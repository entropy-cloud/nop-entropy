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

    private static final String ENTRIES_STATE_NAME = "sharedBuffer-entries-with-lockable-edges";
    private static final String EVENTS_STATE_NAME = "sharedBuffer-events";
    private static final String EVENTS_COUNT_STATE_NAME = "sharedBuffer-events-count";

    /**
     * Plan 360 R3: cache-key scope. {@code scope == null} selects the legacy
     * behavior (raw {@link EventId}/{@link NodeId} cache keys shared across
     * stream keys, cleared on accessor close — see {@link #flushCache()}). A
     * non-null scope (the stream key) makes cache entries key-scoped so
     * identically-minted ids of different keys can never collide, which makes
     * the per-close cache clear unnecessary (every mutation is write-through,
     * so cache and backing state stay consistent within a key).
     */
    private static Object scoped(Object scope, Object id) {
        return scope == null ? id : new ScopedId(scope, id);
    }

    private static final class ScopedId {
        private final Object scope;
        private final Object id;
        private final int hash;

        ScopedId(Object scope, Object id) {
            this.scope = scope;
            this.id = id;
            // Plan 360 R3-audit: precomputed — this object is a Guava cache key on
            // the CEP hot path (hash computed on every getIfPresent/put).
            this.hash = 31 * scope.hashCode() + id.hashCode();
        }

        Object id() {
            return id;
        }

        boolean hasScope(Object other) {
            return scope.equals(other);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ScopedId)) return false;
            ScopedId that = (ScopedId) o;
            return scope.equals(that.scope) && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

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
    // Plan 360 R3: cache keys are Object — either the raw EventId/NodeId (legacy
    // unscoped accessors, flush-on-close) or a ScopedId(scope, id) composite
    // (key-scoped accessors, no flush needed). Values unchanged.
    private final Cache<Object, Lockable<V>> eventsBufferCache;

    /**
     * The cache of sharedBufferNode, with LRU eviction backed by Guava {@link Cache}.
     */
    private final Cache<Object, Lockable<SharedBufferNode>> entryCache;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SharedBuffer(
            KeyedStateStore stateStore,
            TypeSerializer<V> valueSerializer,
            SharedBufferCacheConfig cacheConfig) {
        // raw cast intentional - type erased at runtime
        // P2-INV-6 resolution: the Lockable<> value graphs (user events and
        // node/edge structures) are Java-serialized into byte[] and embedded in
        // the JSON snapshot; EventId/NodeId map keys are @DataBean (JSON path).
        MapStateDescriptor<EventId, Lockable<V>> eventsDescriptor =
                new MapStateDescriptor<>(
                        EVENTS_STATE_NAME,
                        EventId.class,
                        (Class) Lockable.class);
        eventsDescriptor.setSerializer(
                io.nop.stream.core.common.typeutils.JavaStreamSerializer.of());
        this.eventsBuffer = stateStore.getMapState(eventsDescriptor);
        // raw cast intentional - type erased at runtime
        MapStateDescriptor<NodeId, Lockable<SharedBufferNode>> entriesDescriptor =
                new MapStateDescriptor<NodeId, Lockable<SharedBufferNode>>(
                        ENTRIES_STATE_NAME,
                        NodeId.class,
                        (Class) Lockable.class);
        entriesDescriptor.setSerializer(
                io.nop.stream.core.common.typeutils.JavaStreamSerializer.of());
        this.entries = stateStore.getMapState(entriesDescriptor);

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
            // Plan 360 R3: per-eviction logging is TRACE-level diagnostic detail —
            // on the CEP hot path a sustained eviction rate (cache at maximumSize)
            // made DEBUG-level logging a measurable share of the cost.
            if (LOG.isTraceEnabled()) {
                LOG.trace(
                        "SharedBuffer cache evicted entry: cause={}, key={}, value={}",
                        cause,
                        notification.getKey(),
                        notification.getValue());
            }
        }
    }

    /**
     * Constructs an accessor bound to this shared buffer. Accessor reads are served
     * cache-first (see {@link #getEntry(NodeId)} and {@link #getEvent(EventId)}) and
     * accessor writes are write-through (see {@link #upsertEntry(NodeId, Lockable)} and
     * {@link #upsertEvent(EventId, Lockable)}); the cache lifecycle per accessor scope
     * is managed via {@link #flushCache()}.
     *
     * @return an accessor bound to this shared buffer.
     */
    public SharedBufferAccessor<V> getAccessor() {
        return getAccessor(null);
    }

    /**
     * Constructs an accessor bound to this shared buffer AND to the given
     * key scope (plan 360 R3). Cache entries are keyed by
     * {@code (scope, id)} so accessors of different keys never share cache
     * entries and closing a scoped accessor does NOT clear the caches
     * (write-through keeps state authoritative; see {@link #flushCache()} for
     * the legacy unscoped contract).
     *
     * @param key the current stream key (non-null selects key-scoped caching)
     * @return an accessor bound to this shared buffer and key scope.
     */
    public SharedBufferAccessor<V> getAccessor(Object key) {
        return new SharedBufferAccessor<>(this, key);
    }

    void advanceTime(long timestamp) {
        advanceTime(timestamp, null);
    }

    void advanceTime(long timestamp, Object scope) {
        Iterator<Long> iterator = eventsCount.keys().iterator();
        while (iterator.hasNext()) {
            Long next = iterator.next();
            if (next < timestamp) {
                iterator.remove();
            }
        }
        eventsBufferCache.asMap().keySet().removeIf(cacheKey -> {
            EventId eventId = matchingId(cacheKey, scope);
            return eventId != null && eventId.getTimestamp() < timestamp;
        });
    }

    /**
     * Returns the raw {@link EventId}/{@link NodeId} carried by a cache key when
     * the key belongs to the given scope (legacy raw keys match the null scope;
     * {@link ScopedId} keys match by their scope component); {@code null} when
     * the cache key belongs to a different scope.
     */
    private static EventId matchingId(Object cacheKey, Object scope) {
        if (cacheKey instanceof ScopedId) {
            ScopedId scoped = (ScopedId) cacheKey;
            return scoped.hasScope(scope) ? (EventId) scoped.id() : null;
        }
        return scope == null ? (EventId) cacheKey : null;
    }

    private static NodeId matchingEntryId(Object cacheKey, Object scope) {
        if (cacheKey instanceof ScopedId) {
            ScopedId scoped = (ScopedId) cacheKey;
            return scoped.hasScope(scope) ? (NodeId) scoped.id() : null;
        }
        return scope == null ? (NodeId) cacheKey : null;
    }

    EventId registerEvent(V value, long timestamp) {
        return registerEvent(value, timestamp, null);
    }

    EventId registerEvent(V value, long timestamp, Object scope) {
        Integer id = eventsCount.get(timestamp);
        if (id == null) {
            id = 0;
        }
        EventId eventId = new EventId(id, timestamp);
        while (eventsBufferCache.asMap().containsKey(scoped(scope, eventId)) || hasEventInBuffer(eventId)) {
            id++;
            if (id == Integer.MAX_VALUE) {
                throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED)
                        .param(ARG_DETAIL, "EventId counter overflow for timestamp " + timestamp);
            }
            eventId = new EventId(id, timestamp);
        }
        if (id == Integer.MAX_VALUE) {
            // The overflow guard inside the collision loop only fires when the loop iterates.
            // If eventsCount already holds Integer.MAX_VALUE for this timestamp and that slot
            // is free, the loop body never runs and the counter write below would silently
            // overflow to Integer.MIN_VALUE, corrupting the next minted EventId.
            throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED)
                    .param(ARG_DETAIL, "EventId counter overflow for timestamp " + timestamp);
        }
        Lockable<V> lockableValue = new Lockable<>(value, 1);
        eventsCount.put(timestamp, id + 1);
        eventsBufferCache.put(scoped(scope, eventId), lockableValue);
        try {
            eventsBuffer.put(eventId, lockableValue);
        } catch (Exception e) {
            eventsBufferCache.invalidate(scoped(scope, eventId));
            throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, e).param(ARG_DETAIL, "registerEvent");
        }
        return eventId;
    }

    private boolean hasEventInBuffer(EventId eventId) {
        try {
            return eventsBuffer.get(eventId) != null;
        } catch (Exception e) {
            // No log-and-throw: the typed StreamException below carries the cause and the full
            // eventId context, and every caller propagates it — logging first would only
            // duplicate the same failure in the output.
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
        upsertEvent(eventId, event, null);
    }

    void upsertEvent(EventId eventId, Lockable<V> event, Object scope) {
        Object cacheKey = scoped(scope, eventId);
        this.eventsBufferCache.put(cacheKey, event);
        try {
            this.eventsBuffer.put(eventId, event);
        } catch (Exception e) {
            this.eventsBufferCache.invalidate(cacheKey);
            throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, e).param(ARG_DETAIL, "upsertEvent");
        }
    }

    /**
     * Inserts or updates a SharedBufferNode in cache and backing state (write-through).
     *
     * @param nodeId id of the node
     * @param entry  SharedBufferNode
     */
    void upsertEntry(NodeId nodeId, Lockable<SharedBufferNode> entry) {
        upsertEntry(nodeId, entry, null);
    }

    void upsertEntry(NodeId nodeId, Lockable<SharedBufferNode> entry, Object scope) {
        Object cacheKey = scoped(scope, nodeId);
        this.entryCache.put(cacheKey, entry);
        try {
            this.entries.put(nodeId, entry);
        } catch (Exception e) {
            this.entryCache.invalidate(cacheKey);
            throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, e).param(ARG_DETAIL, "upsertEntry");
        }
    }

    /**
     * Removes an event from cache and state.
     *
     * @param eventId id of the event
     */
    void removeEvent(EventId eventId) {
        removeEvent(eventId, null);
    }

    void removeEvent(EventId eventId, Object scope) {
        this.eventsBufferCache.invalidate(scoped(scope, eventId));
        this.eventsBuffer.remove(eventId);
    }

    /**
     * Removes a SharedBufferNode from cache and state.
     *
     * @param nodeId id of the node
     */
    void removeEntry(NodeId nodeId) {
        removeEntry(nodeId, null);
    }

    void removeEntry(NodeId nodeId, Object scope) {
        this.entryCache.invalidate(scoped(scope, nodeId));
        this.entries.remove(nodeId);
    }

    /**
     * Returns the {@link SharedBufferNode} for the given id, reading cache-first: a
     * cache hit is returned directly; on a miss the node is loaded from the backing
     * state and written back into the cache before being returned. Returns {@code null}
     * when the node exists in neither cache nor state.
     *
     * @param nodeId id of the node
     * @return the lockable node, or {@code null} if absent from both cache and state
     */
    Lockable<SharedBufferNode> getEntry(NodeId nodeId) {
        return getEntry(nodeId, null);
    }

    Lockable<SharedBufferNode> getEntry(NodeId nodeId, Object scope) {
        return getWithCache(entryCache, entries, scoped(scope, nodeId), nodeId, "getEntry");
    }

    /**
     * Returns the event for the given id, reading cache-first: a cache hit is returned
     * directly; on a miss the event is loaded from the backing state and written back
     * into the cache before being returned. Returns {@code null} when the event exists
     * in neither cache nor state.
     *
     * @param eventId id of the event
     * @return the lockable event, or {@code null} if absent from both cache and state
     */
    Lockable<V> getEvent(EventId eventId) {
        return getEvent(eventId, null);
    }

    Lockable<V> getEvent(EventId eventId, Object scope) {
        return getWithCache(eventsBufferCache, eventsBuffer, scoped(scope, eventId), eventId, "getEvent");
    }

    /**
     * Cache-first read shared by {@link #getEntry(NodeId)} and {@link #getEvent(EventId)}:
     * returns the cached value when present; on a miss loads it from the backing state
     * and back-fills the cache — the backing state is the source of truth and the cache
     * mirrors it. State access failures are rethrown as a {@link StreamException} whose
     * detail carries {@code accessName} (preserving the per-accessor error context of
     * the original methods).
     */
    private <K, W> Lockable<W> getWithCache(
            Cache<Object, Lockable<W>> cache, MapState<K, Lockable<W>> state, Object cacheKey, K stateKey, String accessName) {
        try {
            Lockable<W> lockableFromCache = cache.getIfPresent(cacheKey);
            if (Objects.nonNull(lockableFromCache)) {
                return lockableFromCache;
            } else {
                Lockable<W> lockableFromState = state.get(stateKey);
                if (Objects.nonNull(lockableFromState)) {
                    cache.put(cacheKey, lockableFromState);
                }
                return lockableFromState;
            }
        } catch (Exception ex) {
            throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, ex).param(ARG_DETAIL, accessName);
        }
    }

    /**
     * Clears the per-accessor caches at the end of an accessor scope (clear-on-success).
     *
     * <p>Cache keys ({@link EventId}/{@link NodeId}) are NOT unique across stream keys — the
     * counters that mint them live in keyed state — so retaining cache entries past the accessor
     * scope would serve key A's entries to key B once the key context switches. The clear is
     * therefore <b>load-bearing for cross-key correctness</b>, not just hygiene.
     *
     * <p>No write-back is needed: every mutation is already write-through
     * ({@code upsertEvent}/{@code upsertEntry}/{@code registerEvent} persist to state at once),
     * so by the time an accessor closes, the backing MapStates already hold every cached value.
     *
     * @throws Exception Thrown if the system cannot access the state.
     */
    void flushCache() {
        entryCache.asMap().keySet().clear();
        eventsBufferCache.asMap().keySet().clear();
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
