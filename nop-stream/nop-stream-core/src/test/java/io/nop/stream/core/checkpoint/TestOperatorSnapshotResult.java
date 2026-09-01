/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestOperatorSnapshotResult {

    private OperatorSnapshotResult result;

    @BeforeEach
    void setUp() {
        result = new OperatorSnapshotResult();
    }

    @Test
    void testEmptyFactory() {
        OperatorSnapshotResult empty = OperatorSnapshotResult.empty();
        assertTrue(empty.isEmpty());
        assertTrue(empty.getOperatorStates().isEmpty());
        assertTrue(empty.getKeyedStates().isEmpty());
        assertTrue(empty.getRawKeyedStates().isEmpty());
    }

    /**
     * S-4 (2026-09-01 core audit): empty() returns an INDEPENDENT instance per
     * call. The former shared singleton was mutable via setCheckpointId /
     * setCheckpointParallelism / setError — one stray mutation poisoned "empty"
     * JVM-wide. Each empty result must stay isolated.
     */
    @Test
    void testEmptyInstancesAreIndependent() {
        OperatorSnapshotResult empty1 = OperatorSnapshotResult.empty();
        OperatorSnapshotResult empty2 = OperatorSnapshotResult.empty();

        assertTrue(empty1.isEmpty());
        assertTrue(empty2.isEmpty());

        // Mutating one empty result must not affect the other or later empties.
        empty1.setCheckpointId(42L);
        empty1.setCheckpointParallelism(2);
        empty1.setError(new RuntimeException("poison"));

        assertEquals(-1L, empty2.getCheckpointId(), "empty2 must not see empty1's checkpointId");
        assertEquals(-1, empty2.getCheckpointParallelism(), "empty2 must not see empty1's parallelism");
        assertFalse(empty2.hasError(), "empty2 must not see empty1's error");

        OperatorSnapshotResult empty3 = OperatorSnapshotResult.empty();
        assertEquals(-1L, empty3.getCheckpointId(), "later empty() must not inherit poisoned state");
        assertTrue(empty3.isEmpty());
    }

    @Test
    void testPutOperatorState() {
        result.putOperatorState("op1", "operator-state");

        assertEquals("operator-state", result.getOperatorState("op1"));
        assertEquals(1, result.getOperatorStates().size());
    }

    @Test
    void testPutKeyedState() {
        result.putKeyedState("key1", "keyed-state");

        assertEquals("keyed-state", result.getKeyedState("key1"));
        assertEquals(1, result.getKeyedStates().size());
    }

    @Test
    void testPutRawKeyedState() {
        result.putRawKeyedState("raw1", "raw-keyed-state");

        assertEquals("raw-keyed-state", result.getRawKeyedState("raw1"));
        assertEquals(1, result.getRawKeyedStates().size());
    }

    @Test
    void testIsEmpty() {
        assertTrue(result.isEmpty());

        result.putOperatorState("op1", "data");
        assertFalse(result.isEmpty());
    }

    @Tag("low-value")
    @Test
    void testGetStateCount() {
        assertEquals(0, result.getStateCount());

        result.putOperatorState("op1", "data1");
        assertEquals(1, result.getStateCount());

        result.putKeyedState("key1", "data2");
        assertEquals(2, result.getStateCount());

        result.putRawKeyedState("raw1", "data3");
        assertEquals(3, result.getStateCount());
    }

    @Test
    void testEstimateSize() {
        assertEquals(0, result.estimateSize());

        result.putOperatorState("op1", "data1");
        assertEquals(1, result.estimateSize());

        result.putKeyedState("key1", "data2");
        assertEquals(2, result.estimateSize());

        result.putRawKeyedState("raw1", "data3");
        assertEquals(3, result.estimateSize());
    }

    @Test
    void testBuilder() {
        OperatorSnapshotResult built = OperatorSnapshotResult.builder()
                .putOperatorState("op1", "operator")
                .putKeyedState("key1", "keyed")
                .putRawKeyedState("raw1", "raw")
                .build();

        assertFalse(built.isEmpty());
        assertEquals("operator", built.getOperatorState("op1"));
        assertEquals("keyed", built.getKeyedState("key1"));
        assertEquals("raw", built.getRawKeyedState("raw1"));
    }

    /**
     * S-4 (2026-09-01 core audit): an empty Builder result is an independent
     * empty snapshot (same semantics as empty()) — no shared singleton identity.
     */
    @Test
    void testBuilderEmptyBuildsIndependentEmpty() {
        OperatorSnapshotResult built = OperatorSnapshotResult.builder().build();
        assertTrue(built.isEmpty());

        OperatorSnapshotResult other = OperatorSnapshotResult.empty();
        built.setCheckpointId(7L);
        assertEquals(-1L, other.getCheckpointId(), "builder-built empty must not share state with empty()");
    }

    @Test
    void testCheckpointParallelismDefault() {
        assertEquals(-1, result.getCheckpointParallelism());
    }

    @Test
    void testCheckpointParallelismSetAndGet() {
        result.setCheckpointParallelism(4);
        assertEquals(4, result.getCheckpointParallelism());
    }

    @Test
    void testIsParallelismChangedUnknown() {
        result.setCheckpointParallelism(-1);
        assertFalse(result.isParallelismChanged(4));
    }

    @Test
    void testIsParallelismChangedSame() {
        result.setCheckpointParallelism(4);
        assertFalse(result.isParallelismChanged(4));
    }

    @Test
    void testIsParallelismChangedDifferent() {
        result.setCheckpointParallelism(2);
        assertTrue(result.isParallelismChanged(4));
    }

    @Test
    void testCheckpointParallelismSerializedThroughSnapshot() {
        result.setCheckpointParallelism(3);
        result.putOperatorState("test-key", "test-value");

        OperatorSnapshotResult copy = new OperatorSnapshotResult(
                result.getOperatorStates(),
                result.getKeyedStates(),
                result.getRawKeyedStates());
        copy.setCheckpointParallelism(result.getCheckpointParallelism());

        assertEquals(3, copy.getCheckpointParallelism());
        assertEquals("test-value", copy.getOperatorState("test-key"));
    }
}
