package io.nop.stream.cep.nfa.sharedbuffer;

import io.nop.stream.cep.Event;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestSharedBufferFlushCache {

    private SharedBuffer<Event> buffer;

    @BeforeEach
    void setUp() {
        buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
    }

    @Test
    void testFlushCachePreservesDataAfterPutAll() throws Exception {
        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId id1 = accessor.registerEvent(new Event(1, "a"), 100L);
            EventId id2 = accessor.registerEvent(new Event(2, "b"), 200L);
            assertNotNull(id1);
            assertNotNull(id2);
        }

        assertTrue(buffer.getEventsBufferCacheSize() > 0, "Cache should have entries before flush");
        buffer.flushCache();
        assertEquals(0, buffer.getEventsBufferCacheSize(), "Cache should be cleared after flush");

        assertEquals(2, buffer.getEventsBufferSize(), "State should contain all events after flush");
    }

    @Test
    void testFlushCacheRemovesOnlyFlushedEntries() throws Exception {
        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            accessor.registerEvent(new Event(1, "a"), 100L);
            accessor.registerEvent(new Event(2, "b"), 200L);
        }

        buffer.flushCache();

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            accessor.registerEvent(new Event(3, "c"), 300L);
        }

        assertEquals(1, buffer.getEventsBufferCacheSize(), "Only new event should be in cache");
        assertEquals(3, buffer.getEventsBufferSize(), "All 3 events should be in state");
    }

    @Test
    void testDoubleFlushIdempotent() throws Exception {
        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            accessor.registerEvent(new Event(1, "a"), 100L);
        }

        buffer.flushCache();
        buffer.flushCache();

        assertEquals(1, buffer.getEventsBufferSize(), "Event should not be duplicated");
        assertEquals(0, buffer.getEventsBufferCacheSize());
    }

    @Test
    void testEntryCacheFlushedToState() throws Exception {
        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId eventId = accessor.registerEvent(new Event(1, "a"), 100L);
            NodeId nodeId = accessor.put("state1", eventId, null, new io.nop.stream.cep.nfa.DeweyNumber(1));
            assertNotNull(nodeId);
        }

        int cacheBefore = buffer.getSharedBufferNodeCacheSize();
        assertTrue(cacheBefore > 0, "Entry cache should have nodes before flush");

        buffer.flushCache();

        assertEquals(0, buffer.getSharedBufferNodeCacheSize(), "Entry cache should be cleared after flush");
        assertEquals(1, buffer.getSharedBufferNodeSize(), "State should contain node after flush");
    }
}
