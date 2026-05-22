package io.nop.stream.cep.nfa.sharedbuffer;

import io.nop.stream.cep.Event;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.DeweyNumber;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSharedBuffer {

    @Test
    public void testRegisterAndRetrieveEvent() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        int cacheSize;

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId id = accessor.registerEvent(new Event(1, "a"), 1L);
            assertNotNull(id);
            cacheSize = buffer.getEventsBufferCacheSize();
        }

        assertEquals(1, cacheSize);
        assertFalse(buffer.isEmpty());
    }

    @Test
    public void testRegisterMultipleEvents() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        int cacheSize;

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            accessor.registerEvent(new Event(1, "a"), 1L);
            accessor.registerEvent(new Event(2, "b"), 2L);
            accessor.registerEvent(new Event(3, "c"), 3L);
            cacheSize = buffer.getEventsBufferCacheSize();
        }

        assertEquals(3, cacheSize);
    }

    @Test
    public void testIsEmpty() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        assertTrue(buffer.isEmpty());
    }

    @Test
    public void testSharedBufferNodeRegistration() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        int nodeCacheSize;

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId eventId = accessor.registerEvent(new Event(1, "a"), 1L);
            NodeId nodeId = accessor.put("state1", eventId, null, new DeweyNumber(1));
            assertNotNull(nodeId);
            nodeCacheSize = buffer.getSharedBufferNodeCacheSize();
        }

        assertTrue(nodeCacheSize > 0);
    }
}
