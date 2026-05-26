package io.nop.stream.core.model;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import io.nop.stream.core.exceptions.StreamException;

class TestStreamComponents {

    @Test
    void testRegisterAndQueryTransform() {
        StreamComponents components = new StreamComponents();
        Object transform = new Object();
        components.registerTransform("transform-1", transform);
        assertSame(transform, components.getTransform("transform-1"));
        assertNull(components.getTransform("nonexistent"));
    }

    @Test
    void testRegisterAndQueryStream() {
        StreamComponents components = new StreamComponents();
        Object stream = new Object();
        components.registerStream("stream-1", stream);
        assertSame(stream, components.getStream("stream-1"));
    }

    @Test
    void testRegisterAndQueryWindowingStrategy() {
        StreamComponents components = new StreamComponents();
        Object strategy = new Object();
        components.registerWindowingStrategy("ws-1", strategy);
        assertSame(strategy, components.getWindowingStrategy("ws-1"));
    }

    @Test
    void testRegisterTransformRejectsNullId() {
        StreamComponents components = new StreamComponents();
        assertThrows(StreamException.class, () -> components.registerTransform(null, new Object()));
        assertThrows(StreamException.class, () -> components.registerTransform("", new Object()));
    }

    @Test
    void testAddRequirementDeduplicates() {
        StreamComponents components = new StreamComponents();
        components.addRequirement(StreamRequirement.STATEFUL_PROCESSING);
        components.addRequirement(StreamRequirement.STATEFUL_PROCESSING);
        assertEquals(1, components.getRequirements().size());
    }

    @Test
    void testAddRequirementRejectsNull() {
        StreamComponents components = new StreamComponents();
        assertThrows(StreamException.class, () -> components.addRequirement(null));
    }

    @Test
    void testCheckpointParticipants() {
        StreamComponents components = new StreamComponents();
        components.addCheckpointParticipant("op-1");
        components.addCheckpointParticipant("op-2");
        components.addCheckpointParticipant("op-1");
        assertEquals(2, components.getCheckpointParticipants().size());
        assertTrue(components.hasCheckpointParticipant("op-1"));
        assertFalse(components.hasCheckpointParticipant("op-3"));
    }

    @Test
    void testUnmodifiableCollections() {
        StreamComponents components = new StreamComponents();
        assertThrows(UnsupportedOperationException.class,
                () -> components.getRequirements().add(StreamRequirement.DISTRIBUTED_EXECUTION));
        assertThrows(UnsupportedOperationException.class,
                () -> components.getCheckpointParticipants().add("op-x"));
    }
}
