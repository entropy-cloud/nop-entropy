/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.stream.cep.nfa.sharedbuffer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.DeweyNumber;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.cep.NopCepErrors.ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;

/**
 * A shared buffer implementation which stores values under according state. Additionally, the
 * values can be versioned such that it is possible to retrieve their predecessor element in the
 * buffer.
 *
 * <p>The idea of the implementation is to have a buffer for incoming events with unique ids
 * assigned to them. This way we do not need to deserialize events during processing and we store
 * only one copy of the event.
 *
 * <p>The entries in {@link SharedBuffer} are {@link SharedBufferNode}. The shared buffer node
 * allows to store relations between different entries. A dewey versioning scheme allows to
 * discriminate between different relations (e.g. preceding element).
 *
 * <p>The implementation is strongly based on the paper "Efficient Pattern Matching over Event
 * Streams".
 *
 * @param <V> Type of the values
 * @see <a href="https://people.cs.umass.edu/~yanlei/publications/sase-sigmod08.pdf">
 * https://people.cs.umass.edu/~yanlei/publications/sase-sigmod08.pdf</a>
 */
public class SharedBuffer<V> {

    private static final Logger LOG = LoggerFactory.getLogger(SharedBuffer.class);

    private static final String LEGACY_ENTRIES_STATE_NAME = "sharedBuffer-entries";
    private static final String ENTRIES_STATE_NAME = "sharedBuffer-entries-with-lockable-edges";
    private static final String EVENTS_STATE_NAME = "sharedBuffer-events";
    private static final String EVENTS_COUNT_STATE_NAME = "sharedBuffer-events-count";

    private final MapState<EventId, Lockable<V>> eventsBuffer;
    /**
     * The number of events seen so far in the stream per timestamp.
     */
    private final MapState<Long, Integer> eventsCount;

    private final MapState<NodeId, Lockable<SharedBufferNode>> entries;

    /**
     * The cache of eventsBuffer State, with LRU eviction backed by Guava {@link Cache}.
     *
     * <p>Guava {@code Cache} provides built-in atomic LRU eviction ({@code maximumSize}),
     * {@code recordStats()} for hit/miss/eviction accounting, and a {@link RemovalListener}
     * that only logs entries evicted by size pressure (manual {@code invalidate}/{@code clear}
     * do not trigger the debug log). This replaces the prior hand-rolled {@code LruCache} that
     * maintained a {@code ConcurrentHashMap} and an access-ordered {@code LinkedHashMap} as two
     * independent structures with a non-atomic put/evict window.
     */
    private final Cache<EventId, Lockable<V>> eventsBufferCache;

    /**
     * The cache of sharedBufferNode, with LRU eviction backed by Guava {@link Cache}.
     */
    private final Cache<NodeId, Lockable<SharedBufferNode>> entryCache;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SharedBuffer(
            KeyedStateStore stateStore,
            TypeSerializer<V> valueSerializer,
            SharedBufferCacheConfig cacheConfig) {
        // raw cast intentional - type erased at runtime
        this.eventsBuffer =
                stateStore.getMapState(
                        new MapStateDescriptor<EventId, Lockable<V>>(
                                EVENTS_STATE_NAME,
                                EventId.class,
                                (Class) Lockable.class));
        // raw cast intentional - type erased at runtime
        this.entries =
                stateStore.getMapState(
                        new MapStateDescriptor<NodeId, Lockable<SharedBufferNode>>(
                                ENTRIES_STATE_NAME,
                                NodeId.class,
                                (Class) Lockable.class));

        this.eventsCount =
                stateStore.getMapState(
                        new MapStateDescriptor<>(
                                EVENTS_COUNT_STATE_NAME,
                                Long.class,
                                Integer.class));

        // set the events buffer cache with atomic LRU eviction (Guava Cache, maximumSize + recordStats).
        // RemovalListener logs only SIZE-evicted entries; manual invalidate/clear is silent.
        this.eventsBufferCache =
                CacheBuilder.newBuilder()
                        .maximumSize(cacheConfig.getEventsBufferCacheSlots())
                        .recordStats()
                        .removalListener(this::onCacheRemoval)
                        .build();

        // set the entry cache with atomic LRU eviction (Guava Cache, maximumSize + recordStats).
        this.entryCache =
                CacheBuilder.newBuilder()
                        .maximumSize(cacheConfig.getEntryCacheSlots())
                        .recordStats()
                        .removalListener(this::onCacheRemoval)
                        .build();
    }

    /**
     * Guava {@link RemovalListener} shared by both caches. Logs at debug level only when the
     * removal cause indicates an eviction (SIZE / COLLECTED / EXPIRED — i.e. equivalent to
     * {@code RemovalCause.wasEvicted()}, which is package-private in this Guava version).
     * Manual {@code invalidate} / {@code invalidateAll} / {@code clear}-equivalent calls
     * produce {@link RemovalCause#EXPLICIT} or {@link RemovalCause#REPLACED} and are
     * intentionally silent — these are part of normal write-through / flushCache clear-on-success
     * semantics and would be noisy if logged.
     */
    private <K, V> void onCacheRemoval(RemovalNotification<K, V> notification) {
        RemovalCause cause = notification.getCause();
        // Equivalent to RemovalCause.wasEvicted() (which is package-private in this Guava
        // version): evictions are SIZE/COLLECTED/EXPIRED. EXPLICIT (invalidate) and REPLACED
        // (put overwriting existing key) are normal write-through / clear-on-success operations
        // and must remain silent.
        boolean evicted = cause != RemovalCause.EXPLICIT && cause != RemovalCause.REPLACED;
        if (evicted) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "SharedBuffer cache evicted entry: cause={}, key={}, value={}",
                        cause,
                        notification.getKey(),
                        notification.getValue());
            }
        }
    }

    private void copyEntries(MapState<NodeId, Lockable<SharedBufferNode>> state) throws Exception {
        state.entries()
                .forEach(
                        e -> {
                            try {
                                entries.put(e.getKey(), e.getValue());
                            } catch (Exception exception) {
                                throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, exception).param(ARG_DETAIL, "copyEntries");
                            }
                        });
    }

    private void lockPredecessorEdges(Map.Entry<NodeId, Lockable<SharedBufferNode>> e) {
        SharedBufferNode oldNode = e.getValue().getElement();
        oldNode.getEdges()
                .forEach(
                        edge -> {
                            SharedBufferEdge oldEdge = edge.getElement();
                            lockEdges(oldEdge.getTarget(), oldEdge.getDeweyNumber());
                        });
    }

    private void lockEdges(NodeId nodeId, DeweyNumber version) {

        if (nodeId == null) {
            return;
        }

        try {
            SharedBufferNode newNode = entries.get(nodeId).getElement();
            newNode.getEdges()
                    .forEach(
                            newEdge -> {
                                if (version.isCompatibleWith(
                                        newEdge.getElement().getDeweyNumber())) {
                                    newEdge.lock();
                                }
                            });
        } catch (Exception exception) {
            throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, exception).param(ARG_DETAIL, "lockEdges");
        }
    }

    /**
     * Construct an accessor to deal with this sharedBuffer.
     *
     * @return an accessor to deal with this sharedBuffer.
     */
    public SharedBufferAccessor<V> getAccessor() {
        return new SharedBufferAccessor<>(this);
    }

    void advanceTime(long timestamp) {
        Iterator<Long> iterator = eventsCount.keys().iterator();
        while (iterator.hasNext()) {
            Long next = iterator.next();
            if (next < timestamp) {
                iterator.remove();
            }
        }
        eventsBufferCache.asMap().keySet().removeIf(eventId ->
                eventId != null && eventId.getTimestamp() < timestamp);
    }

    EventId registerEvent(V value, long timestamp) {
        Integer id = eventsCount.get(timestamp);
        if (id == null) {
            id = 0;
        }
        EventId eventId = new EventId(id, timestamp);
        while (eventsBufferCache.asMap().containsKey(eventId) || hasEventInBuffer(eventId)) {
            id++;
            if (id == Integer.MAX_VALUE) {
                throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED)
                        .param(ARG_DETAIL, "EventId counter overflow for timestamp " + timestamp);
            }
            eventId = new EventId(id, timestamp);
        }
        Lockable<V> lockableValue = new Lockable<>(value, 1);
        eventsCount.put(timestamp, id + 1);
        eventsBufferCache.put(eventId, lockableValue);
        try {
            eventsBuffer.put(eventId, lockableValue);
        } catch (Exception e) {
            eventsBufferCache.invalidate(eventId);
            throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, e).param(ARG_DETAIL, "registerEvent");
        }
        return eventId;
    }

    private boolean hasEventInBuffer(EventId eventId) {
        try {
            return eventsBuffer.get(eventId) != null;
        } catch (Exception e) {
            LOG.error("Failed to check event in buffer for eventId={}", eventId, e);
            throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, e)
                    .param(ARG_DETAIL, "hasEventInBuffer for eventId=" + eventId);
        }
    }

    /**
     * Checks if there is no elements in the buffer.
     *
     * @return true if there is no elements in the buffer
     * @throws Exception Thrown if the system cannot access the state.
     */
    public boolean isEmpty() throws Exception {
        return eventsBufferCache.asMap().isEmpty()
                && !eventsBuffer.keys().iterator().hasNext();
    }

    /**
     * Logs the current cache statistics for both {@code eventsBufferCache} and
     * {@code entryCache} at INFO level.
     *
     * <p>Reads {@link Cache#stats()} (populated because {@code recordStats()} is enabled on
     * both caches) and emits one log line per cache with {@code hitCount}/{@code missCount}/
     * {@code evictionCount}/{@code size}. Called periodically by {@code CepOperator}'s
     * dedicated cache-statistics timer (see {@code CepOperator.onCacheStatisticsTimer}),
     * not by the CEP event-processing timer.
     */
    public void logCacheStatistics() {
        com.google.common.cache.CacheStats eventStats = eventsBufferCache.stats();
        com.google.common.cache.CacheStats entryStats = entryCache.stats();
        LOG.info(
                "SharedBuffer cache statistics: eventsBufferCache{hitCount={}, missCount={}, evictionCount={}, size={}},"
                        + " entryCache{hitCount={}, missCount={}, evictionCount={}, size={}}",
                eventStats.hitCount(), eventStats.missCount(), eventStats.evictionCount(),
                eventsBufferCache.size(),
                entryStats.hitCount(), entryStats.missCount(), entryStats.evictionCount(),
                entryCache.size());
    }

    /**
     * Inserts or updates an event in cache.
     *
     * @param eventId id of the event
     * @param event   event body
     */
    void upsertEvent(EventId eventId, Lockable<V> event) {
        this.eventsBufferCache.put(eventId, event);
        try {
            this.eventsBuffer.put(eventId, event);
        } catch (Exception e) {
            this.eventsBufferCache.invalidate(eventId);
            throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, e).param(ARG_DETAIL, "upsertEvent");
        }
    }

    /**
     * Inserts or updates a shareBufferNode in cache and backing state (write-through).
     *
     * @param nodeId id of the event
     * @param entry  SharedBufferNode
     */
    void upsertEntry(NodeId nodeId, Lockable<SharedBufferNode> entry) {
        this.entryCache.put(nodeId, entry);
        try {
            this.entries.put(nodeId, entry);
        } catch (Exception e) {
            this.entryCache.invalidate(nodeId);
            throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, e).param(ARG_DETAIL, "upsertEntry");
        }
    }

    /**
     * Removes an event from cache and state.
     *
     * @param eventId id of the event
     */
    void removeEvent(EventId eventId) {
        this.eventsBufferCache.invalidate(eventId);
        this.eventsBuffer.remove(eventId);
    }

    /**
     * Removes a ShareBufferNode from cache and state.
     *
     * @param nodeId id of the event
     */
    void removeEntry(NodeId nodeId) {
        this.entryCache.invalidate(nodeId);
        this.entries.remove(nodeId);
    }

    /**
     * It always returns node either from state or cache.
     *
     * @param nodeId id of the node
     * @return SharedBufferNode
     */
    Lockable<SharedBufferNode> getEntry(NodeId nodeId) {
        try {
            Lockable<SharedBufferNode> lockableFromCache = entryCache.getIfPresent(nodeId);
            if (Objects.nonNull(lockableFromCache)) {
                return lockableFromCache;
            } else {
                Lockable<SharedBufferNode> lockableFromState = entries.get(nodeId);
                if (Objects.nonNull(lockableFromState)) {
                    entryCache.put(nodeId, lockableFromState);
                }
                return lockableFromState;
            }
        } catch (Exception ex) {
            throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, ex).param(ARG_DETAIL, "getEntry");
        }
    }

    /**
     * It always returns event either from state or cache.
     *
     * @param eventId id of the event
     * @return event
     */
    Lockable<V> getEvent(EventId eventId) {
        try {
            Lockable<V> lockableFromCache = eventsBufferCache.getIfPresent(eventId);
            if (Objects.nonNull(lockableFromCache)) {
                return lockableFromCache;
            } else {
                Lockable<V> lockableFromState = eventsBuffer.get(eventId);
                if (Objects.nonNull(lockableFromState)) {
                    eventsBufferCache.put(eventId, lockableFromState);
                }
                return lockableFromState;
            }
        } catch (Exception ex) {
            throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, ex).param(ARG_DETAIL, "getEvent");
        }
    }

    /**
     * Flush the event and node from cache to state (write-back flush + clear-on-success).
     *
     * <p>Semantics preserved from the LruCache implementation:
     * <ol>
     *   <li>Snapshot the live cache view into a local {@link HashMap}.</li>
     *   <li>{@code putAll} the snapshot into the backing {@code MapState}.</li>
     *   <li><b>Clear-on-success</b>: on successful {@code putAll}, remove the flushed keys
     *       from the cache (via {@code asMap().keySet().removeAll}).</li>
     *   <li>On failure: re-populate the cache from the snapshot
     *       ({@code asMap().putAll(snapshot)}) before rethrowing.</li>
     * </ol>
     * The Guava {@code Cache.asMap()} view is a live concurrent map; mutations performed on
     * the snapshot happen on a local copy and do not race with subsequent cache reads.
     *
     * @throws Exception Thrown if the system cannot access the state.
     */
    void flushCache() {
        if (!entryCache.asMap().isEmpty()) {
            HashMap<NodeId, Lockable<SharedBufferNode>> snapshot1 = new HashMap<>();
            entryCache.asMap().forEach(snapshot1::put);
            try {
                entries.putAll(snapshot1);
                entryCache.asMap().keySet().removeAll(snapshot1.keySet());
            } catch (Exception e) {
                entryCache.asMap().putAll(snapshot1);
                throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, e).param(ARG_DETAIL, "flushCache-entries");
            }
        }
        if (!eventsBufferCache.asMap().isEmpty()) {
            HashMap<EventId, Lockable<V>> snapshot2 = new HashMap<>();
            eventsBufferCache.asMap().forEach(snapshot2::put);
            try {
                eventsBuffer.putAll(snapshot2);
                eventsBufferCache.asMap().keySet().removeAll(snapshot2.keySet());
            } catch (Exception e) {
                eventsBufferCache.asMap().putAll(snapshot2);
                throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, e).param(ARG_DETAIL, "flushCache-events");
            }
        }
    }

    Iterator<Map.Entry<Long, Integer>> getEventCounters() throws Exception {
        return eventsCount.iterator();
    }

    public int getEventsBufferCacheSize() {
        return (int) eventsBufferCache.size();
    }

    /**
     * Returns the number of entries evicted from {@code eventsBufferCache} due to size pressure
     * or other cache-internal reasons (i.e. removals whose cause is SIZE / COLLECTED / EXPIRED,
     * equivalent to {@code RemovalCause.wasEvicted()} which is package-private here).
     *
     * <p>Manual {@code invalidate}/{@code clear}-like removals (e.g. {@code removeEvent},
     * {@code flushCache} clear-on-success) are <b>not</b> counted. Backed by
     * {@code Cache.stats().evictionCount()} (enabled via {@code recordStats()}).
     */
    public long getEventsBufferEvictionCount() {
        return eventsBufferCache.stats().evictionCount();
    }

    /**
     * Returns the hit count of {@code eventsBufferCache} (cache reads that found the key).
     * Backed by {@code Cache.stats().hitCount()}.
     */
    public long getEventsBufferHitCount() {
        return eventsBufferCache.stats().hitCount();
    }

    /**
     * Returns the miss count of {@code eventsBufferCache} (cache reads that did not find the
     * key and fell through to backing state). Backed by {@code Cache.stats().missCount()}.
     */
    public long getEventsBufferMissCount() {
        return eventsBufferCache.stats().missCount();
    }

    /**
     * Returns the hit count of {@code entryCache}. Backed by {@code Cache.stats().hitCount()}.
     */
    public long getEntryCacheHitCount() {
        return entryCache.stats().hitCount();
    }

    /**
     * Returns the miss count of {@code entryCache}. Backed by {@code Cache.stats().missCount()}.
     */
    public long getEntryCacheMissCount() {
        return entryCache.stats().missCount();
    }

    /**
     * Returns the number of entries evicted from {@code entryCache} due to size pressure
     * or other cache-internal reasons (i.e. removals whose cause is SIZE / COLLECTED / EXPIRED).
     */
    public long getEntryCacheEvictionCount() {
        return entryCache.stats().evictionCount();
    }

    public int getEventsBufferSize() throws Exception {
        int count = 0;
        for (Map.Entry<EventId, Lockable<V>> ignored : eventsBuffer.entries()) {
            count++;
        }
        return count;
    }

    public int getSharedBufferNodeSize() throws Exception {
        int count = 0;
        for (Map.Entry<NodeId, Lockable<SharedBufferNode>> ignored : entries.entries()) {
            count++;
        }
        return count;
    }

    public int getSharedBufferNodeCacheSize() throws Exception {
        return (int) entryCache.size();
    }
}
