package io.nop.stream.cep.nfa.sharedbuffer;

import io.nop.stream.cep.Event;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.DeweyNumber;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSharedBufferExtended {

    private SharedBuffer<Event> createBuffer() {
        return new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
    }

    @Test
    void testSharedBufferFullLifecycle() throws Exception {
        SharedBuffer<Event> buffer = createBuffer();
        int numberEvents = 8;
        Event[] events = new Event[numberEvents];
        EventId[] eventIds = new EventId[numberEvents];
        final long timestamp = 1L;

        for (int i = 0; i < numberEvents; i++) {
            events[i] = new Event(i + 1, "e" + (i + 1));
            try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
                eventIds[i] = accessor.registerEvent(events[i], timestamp);
            }
        }

        assertNotNull(eventIds[0]);

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            NodeId a10 = accessor.put("a1", eventIds[0], null, DeweyNumber.fromString("1"));
            NodeId aLoop0 = accessor.put("a[]", eventIds[1], a10, DeweyNumber.fromString("1.0"));
            NodeId a11 = accessor.put("a1", eventIds[2], null, DeweyNumber.fromString("2"));
            NodeId aLoop1 = accessor.put("a[]", eventIds[2], aLoop0, DeweyNumber.fromString("1.0"));
            NodeId aLoop2 = accessor.put("a[]", eventIds[3], aLoop1, DeweyNumber.fromString("1.0"));
            NodeId aSecondLoop0 = accessor.put("a[]", eventIds[3], a11, DeweyNumber.fromString("2.0"));
            NodeId aLoop3 = accessor.put("a[]", eventIds[4], aLoop2, DeweyNumber.fromString("1.0"));

            DeweyNumber b0Version = DeweyNumber.fromString("1.0.0");
            NodeId b0 = accessor.put("b", eventIds[5], aLoop3, b0Version);
            NodeId aLoop4 = accessor.put("a[]", eventIds[5], aLoop3, DeweyNumber.fromString("1.1"));
            DeweyNumber b1Version = DeweyNumber.fromString("2.0.0");
            NodeId b1 = accessor.put("b", eventIds[5], aSecondLoop0, b1Version);
            NodeId aLoop5 = accessor.put("a[]", eventIds[6], aLoop4, DeweyNumber.fromString("1.1"));
            DeweyNumber b3Version = DeweyNumber.fromString("1.1.0");
            NodeId b3 = accessor.put("b", eventIds[7], aLoop5, b3Version);

            accessor.lockNode(b0, b0Version);
            accessor.lockNode(b1, b1Version);
            accessor.lockNode(b3, b3Version);

            List<Map<String, List<EventId>>> patterns3 = accessor.extractPatterns(b3, b3Version);
            assertEquals(1, patterns3.size());

            Map<String, List<Event>> materialized3 = accessor.materializeMatch(patterns3.get(0));
            assertTrue(materialized3.containsKey("a1"));
            assertTrue(materialized3.containsKey("a[]"));
            assertTrue(materialized3.containsKey("b"));
            assertEquals(events[0], materialized3.get("a1").get(0));

            accessor.releaseNode(b3, b3Version);

            List<Map<String, List<EventId>>> patterns4 = accessor.extractPatterns(b3, b3Version);
            assertEquals(0, patterns4.size());

            List<Map<String, List<EventId>>> patterns1 = accessor.extractPatterns(b1, b1Version);
            assertEquals(1, patterns1.size());
            accessor.releaseNode(b1, b1Version);

            List<Map<String, List<EventId>>> patterns2 = accessor.extractPatterns(b0, b0Version);
            assertEquals(1, patterns2.size());
            accessor.releaseNode(b0, b0Version);

            for (EventId eventId : eventIds) {
                accessor.releaseEvent(eventId);
            }
        }

        assertTrue(buffer.isEmpty());
    }

    @Test
    void testSharedBufferExtractOrder() throws Exception {
        SharedBuffer<Event> buffer = createBuffer();
        int numberEvents = 5;
        Event[] events = new Event[numberEvents];
        EventId[] eventIds = new EventId[numberEvents];
        final long timestamp = 1L;

        for (int i = 0; i < numberEvents; i++) {
            events[i] = new Event(i + 1, "e" + (i + 1));
            try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
                eventIds[i] = accessor.registerEvent(events[i], timestamp);
            }
        }

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            NodeId a = accessor.put("a", eventIds[0], null, DeweyNumber.fromString("1"));
            NodeId b = accessor.put("b", eventIds[1], a, DeweyNumber.fromString("1.0"));
            NodeId aa = accessor.put("aa", eventIds[2], b, DeweyNumber.fromString("1.0.0"));
            NodeId bb = accessor.put("bb", eventIds[3], aa, DeweyNumber.fromString("1.0.0.0"));
            NodeId c = accessor.put("c", eventIds[4], bb, DeweyNumber.fromString("1.0.0.0.0"));

            Map<String, List<Event>> patternsResult =
                    accessor.materializeMatch(
                            accessor.extractPatterns(c, DeweyNumber.fromString("1.0.0.0.0")).get(0));

            List<String> expectedOrder = List.of("a", "b", "aa", "bb", "c");
            List<String> resultOrder = new ArrayList<>(patternsResult.keySet());
            assertEquals(expectedOrder, resultOrder);

            for (EventId eventId : eventIds) {
                accessor.releaseEvent(eventId);
            }
        }
    }

    @Test
    void testClearingSharedBufferWithMultipleEdgesBetweenEntries() throws Exception {
        SharedBuffer<Event> buffer = createBuffer();
        int numberEvents = 8;
        Event[] events = new Event[numberEvents];
        EventId[] eventIds = new EventId[numberEvents];
        final long timestamp = 1L;

        for (int i = 0; i < numberEvents; i++) {
            events[i] = new Event(i + 1, "e" + (i + 1));
            try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
                eventIds[i] = accessor.registerEvent(events[i], timestamp);
            }
        }

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            NodeId start = accessor.put("start", eventIds[1], null, DeweyNumber.fromString("1"));
            NodeId b0 = accessor.put("branching", eventIds[2], start, DeweyNumber.fromString("1.0"));
            NodeId b1 = accessor.put("branching", eventIds[3], start, DeweyNumber.fromString("1.1"));
            NodeId b00 = accessor.put("branching", eventIds[3], b0, DeweyNumber.fromString("1.0.0"));
            accessor.put("branching", eventIds[4], b00, DeweyNumber.fromString("1.0.0.0"));
            NodeId b10 = accessor.put("branching", eventIds[4], b1, DeweyNumber.fromString("1.1.0"));

            accessor.lockNode(b0, DeweyNumber.fromString("1.0"));

            accessor.releaseNode(b10, DeweyNumber.fromString("1.1.0"));

            for (EventId eventId : eventIds) {
                accessor.releaseEvent(eventId);
            }
        }

        assertFalse(buffer.isEmpty());
    }

    @Test
    void testSharedBufferCountersClearing() throws Exception {
        SharedBuffer<Event> buffer = createBuffer();
        int numberEvents = 4;
        Event[] events = new Event[numberEvents];

        for (int i = 0; i < numberEvents; i++) {
            events[i] = new Event(i + 1, "e" + (i + 1));
            try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
                accessor.registerEvent(events[i], i);
            }
        }

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            accessor.advanceTime(3);
        }

        assertFalse(buffer.isEmpty());
    }

    @Test
    void testReleaseNodesWithLongPath() throws Exception {
        SharedBuffer<Event> buffer = createBuffer();
        final int numberEvents = 1000;
        Event[] events = new Event[numberEvents];
        EventId[] eventIds = new EventId[numberEvents];
        NodeId[] nodeIds = new NodeId[numberEvents];
        final long timestamp = 1L;

        for (int i = 0; i < numberEvents; i++) {
            events[i] = new Event(i + 1, "e" + (i + 1));
            try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
                eventIds[i] = accessor.registerEvent(events[i], timestamp);
            }
        }

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            for (int i = 0; i < numberEvents; i++) {
                NodeId prevId = i == 0 ? null : nodeIds[i - 1];
                nodeIds[i] = accessor.put("n" + i, eventIds[i], prevId, DeweyNumber.fromString("1.0"));
            }

            NodeId lastNode = nodeIds[numberEvents - 1];
            accessor.releaseNode(lastNode, DeweyNumber.fromString("1.0"));

            for (int i = 0; i < numberEvents; i++) {
                accessor.releaseEvent(eventIds[i]);
            }
        }

        assertTrue(buffer.isEmpty());
    }

    @Test
    void testSharedBufferAccessorCacheBehavior() throws Exception {
        SharedBuffer<Event> buffer = createBuffer();
        int numberEvents = 8;
        Event[] events = new Event[numberEvents];
        EventId[] eventIds = new EventId[numberEvents];
        final long timestamp = 1L;

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            for (int i = 0; i < numberEvents; i++) {
                events[i] = new Event(i + 1, "e" + (i + 1));
                eventIds[i] = accessor.registerEvent(events[i], timestamp);
            }

            assertTrue(buffer.getEventsBufferCacheSize() > 0);
            assertEquals(0, buffer.getSharedBufferNodeCacheSize());

            NodeId start = accessor.put("start", eventIds[1], null, DeweyNumber.fromString("1"));
            NodeId b0 = accessor.put("branching", eventIds[2], start, DeweyNumber.fromString("1.0"));
            NodeId b1 = accessor.put("branching", eventIds[3], start, DeweyNumber.fromString("1.1"));
            NodeId b00 = accessor.put("branching", eventIds[3], b0, DeweyNumber.fromString("1.0.0"));
            accessor.put("branching", eventIds[4], b00, DeweyNumber.fromString("1.0.0.0"));

            assertTrue(buffer.getSharedBufferNodeCacheSize() > 0);

            accessor.lockNode(b0, DeweyNumber.fromString("1.0.0"));

            for (EventId eventId : eventIds) {
                accessor.releaseEvent(eventId);
            }
        }
    }
}
