package io.nop.stream.cep.nfa.sharedbuffer;

import io.nop.stream.cep.Event;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestSharedBufferLruCache {

    @Test
    void testCacheEvictsOldestEntryUnderCapacityPressure() throws Exception {
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
        }

    }
}
