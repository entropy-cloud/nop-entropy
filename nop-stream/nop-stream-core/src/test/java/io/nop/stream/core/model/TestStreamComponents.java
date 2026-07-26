package io.nop.stream.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.graph.StreamEdge;
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.core.windowing.WindowingStrategy;

import java.util.List;

class TestStreamComponents {

    private static Transformation<?> createTransform() {
        return new Transformation<Integer>("test", null, 1) {
            @Override
            public List<Transformation<?>> getInputs() {
                return java.util.Collections.emptyList();
            }
        };
    }

    @Test
    void testRegisterAndQueryTransform() {
        StreamComponents components = new StreamComponents();
        Transformation<?> transform = createTransform();
        components.registerTransform("transform-1", transform);
        assertSame(transform, components.getTransform("transform-1"));
        assertNull(components.getTransform("nonexistent"));
    }

    @Test
    void testRegisterAndQueryStream() {
        StreamComponents components = new StreamComponents();
        StreamEdge stream = new StreamEdge(1, 2);
        components.registerStream("stream-1", stream);
        assertSame(stream, components.getStream("stream-1"));
    }

    @Test
    void testRegisterAndQueryWindowingStrategy() {
        StreamComponents components = new StreamComponents();
        WindowingStrategy strategy = new WindowingStrategy("ws-1", "windowFn-1", null, 0, null);
        components.registerWindowingStrategy("ws-1", strategy);
        assertSame(strategy, components.getWindowingStrategy("ws-1"));
    }

    @Test
    void testRegisterTransformRejectsNullId() {
        StreamComponents components = new StreamComponents();
        assertThrows(StreamException.class, () -> components.registerTransform(null, createTransform()));
        assertThrows(StreamException.class, () -> components.registerTransform("", createTransform()));
    }

    @Test
    void testRegisterRejectsNullValue() {
        StreamComponents components = new StreamComponents();
        assertThrows(StreamException.class, () -> components.registerTransform("t1", (Transformation<?>) null));
        assertThrows(StreamException.class, () -> components.registerStream("s1", (StreamEdge) null));
        assertThrows(StreamException.class, () -> components.registerWindowingStrategy("w1", (WindowingStrategy) null));
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

    @Test
    void testGetBeanReturnsMatchingType() {
        StreamComponents components = new StreamComponents();
        WindowingStrategy strategy = new WindowingStrategy("ws-1", "windowFn-1", null, 0, null);
        components.registerWindowingStrategy("ws-1", strategy);
        WindowingStrategy retrieved = components.getBean("ws-1", WindowingStrategy.class);
        assertSame(strategy, retrieved);
    }

    @Test
    void testGetBeanRejectsTypeMismatch() {
        StreamComponents components = new StreamComponents();
        WindowingStrategy strategy = new WindowingStrategy("ws-1", "windowFn-1", null, 0, null);
        components.registerWindowingStrategy("ws-1", strategy);
        // WindowingStrategy is not a StreamEdge
        assertThrows(StreamException.class, () -> components.getBean("ws-1", StreamEdge.class));
    }

    @Test
    void testGetBeanThrowsOnMissingId() {
        StreamComponents components = new StreamComponents();
        assertThrows(StreamException.class, () -> components.getBean("nonexistent", WindowingStrategy.class));
    }

    @Test
    void testGetBeanRejectsNullArgs() {
        StreamComponents components = new StreamComponents();
        assertThrows(StreamException.class, () -> components.getBean(null, WindowingStrategy.class));
        assertThrows(StreamException.class, () -> components.getBean("ws-1", null));
    }

    @Test
    void testRegisterTransformRejectsWrongTypeAtCompileTime() {
        // This test verifies compile-time type safety: registerTransform only accepts
        // Transformation<?>, not arbitrary objects. The fact that this test compiles
        // proves the type constraint is enforced at compile time.
        StreamComponents components = new StreamComponents();
        Transformation<?> transform = createTransform();
        components.registerTransform("t1", transform);
        assertNotNull(components.getTransform("t1"));
    }
}
