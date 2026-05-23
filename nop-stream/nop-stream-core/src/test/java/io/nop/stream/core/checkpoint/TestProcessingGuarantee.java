package io.nop.stream.core.checkpoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestProcessingGuarantee {

    @Test
    void testStrictExactlyOnceBarrierAlignment() {
        assertTrue(ProcessingGuarantee.STRICT_EXACTLY_ONCE.isBarrierAlignment());
        assertTrue(ProcessingGuarantee.STRICT_EXACTLY_ONCE.requiresDurableCheckpoint());
    }

    @Test
    void testAtLeastOnceBarrierAlignment() {
        assertTrue(ProcessingGuarantee.AT_LEAST_ONCE.isBarrierAlignment());
        assertFalse(ProcessingGuarantee.AT_LEAST_ONCE.requiresDurableCheckpoint());
    }

    @Test
    void testEffectivelyOnceNoAlignment() {
        assertFalse(ProcessingGuarantee.EFFECTIVELY_ONCE.isBarrierAlignment());
        assertTrue(ProcessingGuarantee.EFFECTIVELY_ONCE.requiresDurableCheckpoint());
    }

    @Test
    void testBestEffortNoAlignment() {
        assertFalse(ProcessingGuarantee.BEST_EFFORT.isBarrierAlignment());
        assertFalse(ProcessingGuarantee.BEST_EFFORT.requiresDurableCheckpoint());
    }
}
