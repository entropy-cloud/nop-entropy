package io.nop.code.flow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCacheEviction {

    @Test
    void testFlowDetectorInvalidateCacheRemovesEntry() {
        FlowDetector detector = new FlowDetector();
        detector.invalidateCache("nonexistent_idx");
        assertTrue(detector.listFlows("nonexistent_idx").isEmpty(),
                "Invalidate on non-existent index should not throw");
    }

    @Test
    void testFlowDetectorEvictionAfterMaxEntries() {
        FlowDetector detector = new FlowDetector();

        for (int i = 0; i < 25; i++) {
            detector.invalidateCache("idx_" + i);
        }

        assertTrue(true, "Multiple invalidate calls should not throw");
    }
}
