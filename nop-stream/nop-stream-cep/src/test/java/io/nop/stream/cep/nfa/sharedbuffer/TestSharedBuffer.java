package io.nop.stream.cep.nfa.sharedbuffer;

import io.nop.stream.cep.Event;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.DeweyNumber;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    public void testRetrieveByCondition() throws Exception {
        SharedBuffer<Event> sharedBuffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        try (SharedBufferAccessor<Event> accessor = sharedBuffer.getAccessor()) {
            EventId eventId1 = accessor.registerEvent(new Event(1, "a"), 1L);
            EventId eventId2 = accessor.registerEvent(new Event(2, "b"), 2L);

            NodeId nodeId1 = accessor.put("stateA", eventId1, null, new DeweyNumber(1));
            NodeId nodeId2 = accessor.put("stateB", eventId2, nodeId1, new DeweyNumber(1).addStage());

            assertNotNull(nodeId1);
            assertNotNull(nodeId2);

            List<Map<String, List<EventId>>> patterns = accessor.extractPatterns(nodeId2, new DeweyNumber(1).addStage());
            assertFalse(patterns.isEmpty());

            Map<String, List<EventId>> pattern = patterns.get(0);
            assertTrue(pattern.containsKey("stateA") || pattern.containsKey("stateB"));
        }
    }

    @Test
    public void testLockUnlockSemantics() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId eventId = accessor.registerEvent(new Event(1, "a"), 1L);
            NodeId nodeId = accessor.put("state1", eventId, null, new DeweyNumber(1));

            accessor.lockNode(nodeId, new DeweyNumber(1));
            Lockable<SharedBufferNode> lockedNode = buffer.getEntry(nodeId);
            assertNotNull(lockedNode);

            accessor.releaseNode(nodeId, new DeweyNumber(1));
        }
    }

    @Test
    public void testEmptyBufferOperations() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        assertTrue(buffer.isEmpty());
    }

    @Test
    public void testDuplicateRegistrationSameState() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId eventId1 = accessor.registerEvent(new Event(1, "a"), 1L);

            NodeId nodeId1 = accessor.put("state1", eventId1, null, new DeweyNumber(1));
            NodeId nodeId2 = accessor.put("state1", eventId1, null, new DeweyNumber(2));

            assertEquals(nodeId1, nodeId2);

            Lockable<SharedBufferNode> node = buffer.getEntry(nodeId1);
            assertNotNull(node);
            assertEquals(2, node.getElement().getEdges().size());
        }
    }

    @Test
    public void testCacheEvictionUnderPressure() throws Exception {
        int smallCacheSize = 5;
        SharedBufferCacheConfig smallConfig = new SharedBufferCacheConfig(smallCacheSize, smallCacheSize,
                java.time.Duration.ofMinutes(1));
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, smallConfig);

        List<EventId> eventIds = new ArrayList<>();
        List<NodeId> nodeIds = new ArrayList<>();

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            for (int i = 0; i < 20; i++) {
                EventId eventId = accessor.registerEvent(new Event(i, "e" + i), i + 1);
                eventIds.add(eventId);
                NodeId nodeId = accessor.put("state" + i, eventId, null, new DeweyNumber(1));
                nodeIds.add(nodeId);
            }

            for (int i = 0; i < 20; i++) {
                Lockable<SharedBufferNode> node = buffer.getEntry(nodeIds.get(i));
                assertNotNull(node);
            }

            for (int i = 0; i < 20; i++) {
                Lockable<Event> event = buffer.getEvent(eventIds.get(i));
                assertNotNull(event);
                assertEquals(i, event.getElement().getId());
            }
        }
    }

    @Test
    public void testMaterializeMatchFromBuffer() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId eventId1 = accessor.registerEvent(new Event(1, "a"), 1L);
            EventId eventId2 = accessor.registerEvent(new Event(2, "b"), 2L);

            NodeId nodeId1 = accessor.put("start", eventId1, null, new DeweyNumber(1));
            NodeId nodeId2 = accessor.put("end", eventId2, nodeId1, new DeweyNumber(1).addStage());

            List<Map<String, List<EventId>>> patterns = accessor.extractPatterns(nodeId2, new DeweyNumber(1).addStage());
            assertFalse(patterns.isEmpty());

            Map<String, List<EventId>> rawPattern = patterns.get(0);
            Map<String, List<Event>> materialized = accessor.materializeMatch(rawPattern);

            assertTrue(materialized.containsKey("start") || materialized.containsKey("end"));
            if (materialized.containsKey("start")) {
                assertEquals(1, materialized.get("start").get(0).getId());
            }
            if (materialized.containsKey("end")) {
                assertEquals(2, materialized.get("end").get(0).getId());
            }
        }
    }

    @Test
    public void testAdvanceTimeCleansOldCounters() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            accessor.registerEvent(new Event(1, "a"), 1L);
            accessor.registerEvent(new Event(2, "b"), 2L);
            accessor.registerEvent(new Event(3, "c"), 3L);
        }

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            accessor.advanceTime(3L);
        }

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId newEvent = accessor.registerEvent(new Event(4, "d"), 3L);
            assertNotNull(newEvent);
        }
    }

    @Test
    public void testAdvanceTimePreventsEventIdCollision() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        EventId oldEvent;
        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            oldEvent = accessor.registerEvent(new Event(1, "a"), 1L);
            assertNotNull(oldEvent);
            assertEquals(0, oldEvent.getId());
        }

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            accessor.advanceTime(2L);
        }

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId newEvent = accessor.registerEvent(new Event(3, "c"), 1L);
            assertNotNull(newEvent);
            assert !newEvent.equals(oldEvent) :
                    "New EventId must not collide with old EventId still in eventsBuffer";
        }

        Lockable<Event> oldEntry = buffer.getEvent(oldEvent);
        assertNotNull(oldEntry, "Old entry should still be accessible");
        assertEquals(1, oldEntry.getElement().getId());
    }

    @Test
    public void testAdvanceTimeThenRegisterEventCompleteChain() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        EventId event10, event20, event30;
        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            event10 = accessor.registerEvent(new Event(1, "a"), 10L);
            event20 = accessor.registerEvent(new Event(2, "b"), 20L);
            event30 = accessor.registerEvent(new Event(3, "c"), 30L);
        }

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            accessor.advanceTime(25L);
        }

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            assertNotNull(buffer.getEvent(event10));
            assertNotNull(buffer.getEvent(event20));
            assertNotNull(buffer.getEvent(event30));

            EventId reused = accessor.registerEvent(new Event(4, "d"), 10L);
            assertNotNull(reused);
            assert !reused.equals(event10) :
                    "New EventId at reused timestamp must not collide";
        }

        Lockable<Event> old10 = buffer.getEvent(event10);
        assertNotNull(old10);
        assertEquals(1, old10.getElement().getId());
    }

    @Test
    public void testAdvanceTimeCountersResetAndNoCollision() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            accessor.registerEvent(new Event(1, "a"), 1L);
            accessor.registerEvent(new Event(2, "b"), 1L);
        }

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            accessor.advanceTime(2L);
        }

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId e0 = accessor.registerEvent(new Event(3, "c"), 1L);
            EventId e1 = accessor.registerEvent(new Event(4, "d"), 1L);

            assertNotNull(e0);
            assertNotNull(e1);
            assert !e0.equals(e1) : "Sequential EventIds must be unique";

            assertEquals(2, e0.getId(), "Should skip past existing id=0 in eventsBuffer");
            assertEquals(3, e1.getId(), "Should continue from last assigned id");
        }
    }

    @Test
    public void testFlushCacheAfterAdvanceTime() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            accessor.registerEvent(new Event(1, "a"), 10L);
            accessor.registerEvent(new Event(2, "b"), 20L);
        }

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            accessor.advanceTime(15L);
        }

        buffer.flushCache();

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId reused = accessor.registerEvent(new Event(3, "c"), 10L);
            assertNotNull(reused);
            assert reused.getId() != 0 :
                    "Should not produce EventId(0,10) since it exists in eventsBuffer";
        }
    }
}
