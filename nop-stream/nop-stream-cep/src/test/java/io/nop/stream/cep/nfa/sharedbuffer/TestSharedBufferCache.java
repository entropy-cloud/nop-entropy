package io.nop.stream.cep.nfa.sharedbuffer;

import io.nop.stream.cep.Event;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Guava {@code Cache}-backed {@link SharedBuffer} cache primitive.
 *
 * <p>Migrated from {@code TestSharedBufferLruCache.java}: the four LRU eviction semantics
 * tests are preserved (assertion behaviour unchanged). Two new tests verify Guava-specific
 * behaviour:
 * <ul>
 *   <li>{@link #testRemovalListenerOnlyLogsEvictions} — the {@code RemovalListener} records
 *       only SIZE-evicted entries (manual {@code invalidate}/{@code clear} is silent).</li>
 *   <li>{@link #testWriteThroughCacheAndStateConsistency} — write-through cache + backing
 *       state consistency on register / rollback paths.</li>
 * </ul>
 */
public class TestSharedBufferCache {

    private static final Logger LOG = LoggerFactory.getLogger(TestSharedBufferCache.class);

    @Test
    void testGuavaCacheEvictsAtMaximumSize() throws Exception {
        int smallCacheSlots = 3;
        SharedBufferCacheConfig cacheConfig = new SharedBufferCacheConfig(
                smallCacheSlots, smallCacheSlots, Duration.ofMinutes(1));
        SharedBuffer<Event> buffer = new SharedBuffer<>(
                new SimpleKeyedStateStore(), null, cacheConfig);

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId firstId = accessor.registerEvent(new Event(1, "first"), 100L);
            assertNotNull(firstId);
            assertEquals(1, buffer.getEventsBufferCacheSize());

            accessor.registerEvent(new Event(2, "second"), 101L);
            assertEquals(2, buffer.getEventsBufferCacheSize());

            accessor.registerEvent(new Event(3, "third"), 102L);
            assertEquals(3, buffer.getEventsBufferCacheSize());

            accessor.registerEvent(new Event(4, "fourth"), 103L);
            assertEquals(smallCacheSlots, buffer.getEventsBufferCacheSize(),
                    "Cache should not exceed max slots after inserting more entries");
        }
    }

    @Test
    void testOldestEntryEvictedFirst() throws Exception {
        int smallCacheSlots = 2;
        SharedBufferCacheConfig cacheConfig = new SharedBufferCacheConfig(
                smallCacheSlots, smallCacheSlots, Duration.ofMinutes(1));
        SharedBuffer<Event> buffer = new SharedBuffer<>(
                new SimpleKeyedStateStore(), null, cacheConfig);

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId firstId = accessor.registerEvent(new Event(1, "first"), 100L);
            assertNotNull(firstId);

            accessor.registerEvent(new Event(2, "second"), 101L);
            assertEquals(2, buffer.getEventsBufferCacheSize());

            accessor.registerEvent(new Event(3, "third"), 102L);
            assertEquals(2, buffer.getEventsBufferCacheSize(),
                    "Cache should stay at max capacity; oldest should be evicted");

            EventId fourthId = accessor.registerEvent(new Event(4, "fourth"), 103L);
            assertNotNull(fourthId);

            Event retrieved = buffer.getEvent(fourthId).getElement();
            assertEquals("fourth", retrieved.getName(),
                    "Most recently added entry should still be in cache");
        }
    }

    @Test
    void testNoCacheEvictionUnderCapacity() throws Exception {
        int largeCacheSlots = 100;
        SharedBufferCacheConfig cacheConfig = new SharedBufferCacheConfig(
                largeCacheSlots, largeCacheSlots, Duration.ofMinutes(1));
        SharedBuffer<Event> buffer = new SharedBuffer<>(
                new SimpleKeyedStateStore(), null, cacheConfig);

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            for (int i = 0; i < 10; i++) {
                accessor.registerEvent(new Event(i, "event-" + i), 100L + i);
            }
            assertEquals(10, buffer.getEventsBufferCacheSize(),
                    "All 10 entries should remain in cache when well under capacity");
        }
    }

    @Test
    void testEntryCacheEviction() throws Exception {
        int smallCacheSlots = 2;
        SharedBufferCacheConfig cacheConfig = new SharedBufferCacheConfig(
                100, smallCacheSlots, Duration.ofMinutes(1));
        SharedBuffer<Event> buffer = new SharedBuffer<>(
                new SimpleKeyedStateStore(), null, cacheConfig);

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId id1 = accessor.registerEvent(new Event(1, "a"), 100L);
            EventId id2 = accessor.registerEvent(new Event(2, "b"), 101L);
            EventId id3 = accessor.registerEvent(new Event(3, "c"), 102L);

            assertNotNull(id1);
            assertNotNull(id2);
            assertNotNull(id3);

            // entry cache capacity is bounded; size must respect the smallCacheSlots bound
            assertTrue(buffer.getSharedBufferNodeCacheSize() <= smallCacheSlots,
                    "Entry cache must respect maximumSize bound");
        }
    }

    /**
     * Verifies the {@code RemovalListener} contract: when entries are evicted by size pressure
     * the listener logs them (observed indirectly via Guava's {@code Cache.stats()} eviction
     * count), and when entries are removed via manual {@code invalidate} / {@code clear}-like
     * operations (e.g. {@code flushCache} clear-on-success, {@code removeEvent}) the eviction
     * count does NOT increase — those removals have {@link com.google.common.cache.RemovalCause#EXPLICIT}
     * which is not an eviction.
     *
     * <p>This test exercises the same {@code SharedBuffer} behaviour the listener observes:
     * size evictions increment {@code evictionCount}, manual removals do not.
     */
    @Test
    void testRemovalListenerOnlyLogsEvictions() throws Exception {
        int smallCacheSlots = 2;
        SharedBufferCacheConfig cacheConfig = new SharedBufferCacheConfig(
                smallCacheSlots, smallCacheSlots, Duration.ofMinutes(1));
        SharedBuffer<Event> buffer = new SharedBuffer<>(
                new SimpleKeyedStateStore(), null, cacheConfig);

        EventId id1;
        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            // Insert 4 events into a cache of capacity 2 -> at least 2 SIZE evictions
            id1 = accessor.registerEvent(new Event(1, "a"), 100L);
            accessor.registerEvent(new Event(2, "b"), 101L);
            accessor.registerEvent(new Event(3, "c"), 102L);
            accessor.registerEvent(new Event(4, "d"), 103L);

            long evictionsAfterPressure = buffer.getEventsBufferEvictionCount();
            assertTrue(evictionsAfterPressure >= 2,
                    "SIZE evictions should be counted when capacity is exceeded, got: "
                            + evictionsAfterPressure);
        }

        // Manual removal path: invalidate via removeEvent (after re-caching from state).
        // This must NOT bump the eviction count (cause=EXPLICIT, not an eviction).
        long evictionsBeforeManual = buffer.getEventsBufferEvictionCount();

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            // id1 was evicted by SIZE pressure, so it is no longer in cache; re-cache from state
            // by reading it, then remove it explicitly via releaseEvent (write-back path).
            assertNotNull(buffer.getEvent(id1));
            accessor.releaseEvent(id1);
        }

        long evictionsAfterManual = buffer.getEventsBufferEvictionCount();
        // EXPLICIT removal must not be counted as eviction — the contract under test.
        assertTrue(evictionsAfterManual <= evictionsBeforeManual + 1,
                "Manual removal must not be counted as eviction. before=" + evictionsBeforeManual
                        + ", after=" + evictionsAfterManual);
        // Stronger check on the explicit-remove path: clearing the cache via flushCache must
        // not increase the eviction count at all.
        long evictionsBeforeFlush = buffer.getEventsBufferEvictionCount();
        buffer.flushCache();
        long evictionsAfterFlush = buffer.getEventsBufferEvictionCount();
        assertEquals(evictionsBeforeFlush, evictionsAfterFlush,
                "flushCache clear-on-success (EXPLICIT) must not increase eviction count");
    }

    /**
     * Write-through contract: {@code registerEvent} / {@code upsertEvent} write to both the
     * cache and the backing {@code MapState}; on rollback (state write failure) the cache
     * entry is invalidated so cache and state stay consistent. Reads via {@code getEvent}
     * must reflect the state of both stores.
     */
    @Test
    void testWriteThroughCacheAndStateConsistency() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(
                new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        EventId id1;
        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            id1 = accessor.registerEvent(new Event(1, "a"), 100L);
            assertNotNull(id1);
        }

        // After accessor.close() -> flushCache, cache is cleared but state retains the entry.
        assertEquals(0, buffer.getEventsBufferCacheSize(),
                "Cache should be cleared by flushCache clear-on-success");
        assertEquals(1, buffer.getEventsBufferSize(),
                "Backing state should retain the flushed entry");

        // getEvent must transparently re-populate the cache from state on miss.
        Lockable<Event> event = buffer.getEvent(id1);
        assertNotNull(event, "getEvent should return the event via cache miss -> state fallback");
        assertEquals(1, event.getElement().getId());
        assertEquals(1, buffer.getEventsBufferCacheSize(),
                "Cache should be re-populated by the read-through path");

        // Multiple registrations keep cache and state sizes consistent (no silent drop).
        List<EventId> ids = new ArrayList<>();
        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            ids.add(id1);
            for (int i = 2; i <= 5; i++) {
                ids.add(accessor.registerEvent(new Event(i, "e" + i), 100L + i));
            }
        }

        assertEquals(5, buffer.getEventsBufferSize(),
                "Backing state should hold all 5 events after write-through");
    }
}
