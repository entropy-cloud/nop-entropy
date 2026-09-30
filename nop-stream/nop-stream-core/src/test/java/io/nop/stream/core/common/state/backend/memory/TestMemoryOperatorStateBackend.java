/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.common.state.backend.IOperatorStateBackend;
import io.nop.stream.core.common.state.backend.RedistributionMode;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestMemoryOperatorStateBackend {

    // R5-TE-16: the previous `testRedistributionModeEnumExists` (assertNotNull on
    // enum constants) was a textbook existence test that could never fail — enum
    // constants exist at compile time. Every RedistributionMode already has a real
    // behavior test in this class: NONE (testNONERestoreSingle), UNION
    // (testUnionRedistribution), BROADCAST (testBroadcastRedistribution),
    // SPLIT_DISTRIBUTE (testSplitDistributeRoundRobin), plus the empty-state and
    // fail-fast guards — the existence test carried no incremental value and was
    // removed.

    @Test
    void testSnapshotAndBasicRestore() throws Exception {
        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        backend.restoreState((OperatorSnapshotResult) null);

        OperatorSnapshotResult first = new OperatorSnapshotResult();
        first.putOperatorState("key1", "value1");
        first.setCheckpointParallelism(1);
        backend.restoreState(first);

        OperatorSnapshotResult snap = backend.snapshotState(1);
        assertEquals("value1", snap.getOperatorState("key1"));
    }

    @Test
    void testBasicRestoreEmpty() throws Exception {
        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        backend.restoreState(new ArrayList<>(), 1, RedistributionMode.NONE, 0, 1);
        OperatorSnapshotResult snap = backend.snapshotState(1);
        assertTrue(snap.getOperatorStates().isEmpty());
    }

    @Test
    void testNONERestoreSingle() throws Exception {
        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        OperatorSnapshotResult snap1 = new OperatorSnapshotResult();
        snap1.putOperatorState("key", "val");
        snap1.setCheckpointParallelism(2);

        backend.restoreState(Arrays.asList(snap1), 2, RedistributionMode.NONE, 0, 1);
        OperatorSnapshotResult result = backend.snapshotState(1);
        assertEquals("val", result.getOperatorState("key"));
    }

    @Test
    void testUnionRedistribution() throws Exception {
        OperatorSnapshotResult old1 = new OperatorSnapshotResult();
        old1.putOperatorState("list", Arrays.asList("a", "b"));
        old1.setCheckpointParallelism(2);

        OperatorSnapshotResult old2 = new OperatorSnapshotResult();
        old2.putOperatorState("list", Arrays.asList("c", "d"));
        old2.setCheckpointParallelism(2);

        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        backend.restoreState(Arrays.asList(old1, old2), 2, RedistributionMode.UNION, 0, 3);

        OperatorSnapshotResult result = backend.snapshotState(1);
        Object state = result.getOperatorState("list");
        assertNotNull(state);
        assertTrue(state instanceof List);
        List<?> list = (List<?>) state;
        assertEquals(4, list.size());
        assertTrue(list.containsAll(Arrays.asList("a", "b", "c", "d")));
    }

    /**
     * NEW-B (plan 369): BROADCAST restore is UNION semantics (aligned with
     * Flink's RoundRobinOperatorStateRepartitioner UNION/BROADCAST path):
     * every non-empty snapshot contributes and the union reaches every new
     * instance. Two old subtasks hold disjoint broadcast keys; after restore
     * BOTH keys must be present on BOTH new subtasks (pre-fix only the first
     * non-empty snapshot was kept, silently dropping subtask 2's state).
     */
    @Test
    void testBroadcastRedistribution() throws Exception {
        OperatorSnapshotResult old1 = new OperatorSnapshotResult();
        old1.putOperatorState("x", "from1");
        old1.setCheckpointParallelism(3);

        OperatorSnapshotResult old2 = new OperatorSnapshotResult();
        old2.putOperatorState("y", "from2");
        old2.setCheckpointParallelism(3);

        MemoryOperatorStateBackend task0 = new MemoryOperatorStateBackend();
        task0.restoreState(Arrays.asList(old1, old2), 3, RedistributionMode.BROADCAST, 0, 2);

        MemoryOperatorStateBackend task1 = new MemoryOperatorStateBackend();
        task1.restoreState(Arrays.asList(old1, old2), 3, RedistributionMode.BROADCAST, 1, 2);

        // Union: both disjoint keys restored on every instance.
        assertEquals("from1", task0.snapshotState(1).getOperatorState("x"));
        assertEquals("from2", task0.snapshotState(1).getOperatorState("y"));
        assertEquals("from1", task1.snapshotState(1).getOperatorState("x"));
        assertEquals("from2", task1.snapshotState(1).getOperatorState("y"));
    }

    /**
     * NEW-B (plan 369): two non-empty snapshots with the SAME state name whose
     * values are lists — the union concatenates both contributions in subtask
     * order on every new instance (the Flink union-broadcast data-preservation
     * shape).
     */
    @Test
    void testBroadcastUnionConcatenatesListValuesAcrossSnapshots() throws Exception {
        OperatorSnapshotResult old1 = new OperatorSnapshotResult();
        old1.putOperatorState("rules", Arrays.asList("r0", "r1"));
        old1.setCheckpointParallelism(2);

        OperatorSnapshotResult old2 = new OperatorSnapshotResult();
        old2.putOperatorState("rules", Arrays.asList("r2", "r3"));
        old2.setCheckpointParallelism(2);

        MemoryOperatorStateBackend task0 = new MemoryOperatorStateBackend();
        task0.restoreState(Arrays.asList(old1, old2), 2, RedistributionMode.BROADCAST, 0, 3);
        MemoryOperatorStateBackend task1 = new MemoryOperatorStateBackend();
        task1.restoreState(Arrays.asList(old1, old2), 2, RedistributionMode.BROADCAST, 1, 3);

        for (MemoryOperatorStateBackend task : Arrays.asList(task0, task1)) {
            Object state = task.snapshotState(1).getOperatorState("rules");
            assertTrue(state instanceof List);
            assertEquals(Arrays.asList("r0", "r1", "r2", "r3"), state,
                    "broadcast union must concatenate same-name list values from all snapshots");
        }
    }

    /**
     * NEW-B (plan 369): same-name MAP values merge entries (later snapshot
     * wins key conflicts); scalar (non-list, non-map) conflicts resolve to the
     * LATER snapshot's value on all instances — scalars carry no merge
     * function, and the union must at least be CONSISTENT across instances
     * (pre-fix it silently kept only the first snapshot's value).
     */
    @Test
    void testBroadcastUnionMergesMapValuesAndConsistentScalarConflict() throws Exception {
        OperatorSnapshotResult old1 = new OperatorSnapshotResult();
        old1.putOperatorState("config", new java.util.HashMap<>(java.util.Map.of("a", 1, "shared", "old")));
        old1.putOperatorState("version", 1);
        old1.setCheckpointParallelism(2);

        OperatorSnapshotResult old2 = new OperatorSnapshotResult();
        old2.putOperatorState("config", new java.util.HashMap<>(java.util.Map.of("b", 2, "shared", "new")));
        old2.putOperatorState("version", 2);
        old2.setCheckpointParallelism(2);

        MemoryOperatorStateBackend task0 = new MemoryOperatorStateBackend();
        task0.restoreState(Arrays.asList(old1, old2), 2, RedistributionMode.BROADCAST, 0, 2);
        MemoryOperatorStateBackend task1 = new MemoryOperatorStateBackend();
        task1.restoreState(Arrays.asList(old1, old2), 2, RedistributionMode.BROADCAST, 1, 2);

        for (MemoryOperatorStateBackend task : Arrays.asList(task0, task1)) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> config = (Map<Object, Object>) task.snapshotState(1).getOperatorState("config");
            assertEquals(1, config.get("a"), "map union keeps subtask-0 entries");
            assertEquals(2, config.get("b"), "map union keeps subtask-1 entries");
            assertEquals("new", config.get("shared"), "later snapshot wins map key conflicts");
            assertEquals(2, task.snapshotState(1).getOperatorState("version"),
                    "scalar conflict resolves to the later snapshot, identically on every instance");
        }
    }

    /**
     * NEW-B (plan 369): null and empty snapshots are ignored by the union —
     * a subtask that checkpointed no broadcast state contributes nothing.
     */
    @Test
    void testBroadcastUnionIgnoresEmptySnapshots() throws Exception {
        OperatorSnapshotResult empty = new OperatorSnapshotResult();
        empty.setCheckpointParallelism(2);

        OperatorSnapshotResult old2 = new OperatorSnapshotResult();
        old2.putOperatorState("x", "from2");
        old2.setCheckpointParallelism(2);

        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        backend.restoreState(Arrays.asList(null, empty, old2), 2, RedistributionMode.BROADCAST, 0, 2);

        assertEquals("from2", backend.snapshotState(1).getOperatorState("x"));
    }

    @Test
    void testSplitDistributeRoundRobin() throws Exception {
        OperatorSnapshotResult old1 = new OperatorSnapshotResult();
        old1.putOperatorState("items", Arrays.asList("e0", "e1", "e2", "e3"));
        old1.setCheckpointParallelism(2);

        OperatorSnapshotResult old2 = new OperatorSnapshotResult();
        old2.putOperatorState("items", Arrays.asList("e4", "e5", "e6", "e7"));
        old2.setCheckpointParallelism(2);

        MemoryOperatorStateBackend task0 = new MemoryOperatorStateBackend();
        task0.restoreState(Arrays.asList(old1, old2), 2, RedistributionMode.SPLIT_DISTRIBUTE, 0, 3);

        MemoryOperatorStateBackend task1 = new MemoryOperatorStateBackend();
        task1.restoreState(Arrays.asList(old1, old2), 2, RedistributionMode.SPLIT_DISTRIBUTE, 1, 3);

        MemoryOperatorStateBackend task2 = new MemoryOperatorStateBackend();
        task2.restoreState(Arrays.asList(old1, old2), 2, RedistributionMode.SPLIT_DISTRIBUTE, 2, 3);

        @SuppressWarnings("unchecked")
        List<String> t0items = (List<String>) task0.snapshotState(1).getOperatorState("items");
        @SuppressWarnings("unchecked")
        List<String> t1items = (List<String>) task1.snapshotState(1).getOperatorState("items");
        @SuppressWarnings("unchecked")
        List<String> t2items = (List<String>) task2.snapshotState(1).getOperatorState("items");

        assertEquals(3, t0items.size());
        assertEquals(3, t1items.size());
        assertEquals(2, t2items.size());

        assertEquals("e0", t0items.get(0));
        assertEquals("e1", t1items.get(0));
        assertEquals("e2", t2items.get(0));
        assertEquals("e3", t0items.get(1));
    }

    @Test
    void testEmptyStateAllModes() throws Exception {
        for (RedistributionMode mode : RedistributionMode.values()) {
            MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
            backend.restoreState(new ArrayList<>(), 2, mode, 0, 3);
            OperatorSnapshotResult result = backend.snapshotState(1);
            assertTrue(result.getOperatorStates().isEmpty(),
                    "Mode " + mode + " should handle empty state gracefully");
        }
    }

    @Test
    void testParallelismUnchangedPassThrough() throws Exception {
        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        OperatorSnapshotResult snap = new OperatorSnapshotResult();
        snap.putOperatorState("key", "value");
        snap.setCheckpointParallelism(2);

        backend.restoreState(Arrays.asList(snap), 2, RedistributionMode.NONE, 0, 2);
        assertEquals("value", backend.snapshotState(1).getOperatorState("key"));
    }

    /**
     * S-9 (2026-09-01 core audit): invalid split-distribute inputs fail fast
     * instead of being silently clamped. A taskIndex >= newParallelism would
     * make this subtask's stride overlap another subtask's (duplicate restore).
     */
    @org.junit.jupiter.api.Test
    void testSplitDistributeInvalidParallelismFailsFast() {
        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        org.junit.jupiter.api.Assertions.assertThrows(io.nop.stream.core.exceptions.StreamException.class,
                () -> backend.restoreState(new ArrayList<>(), 2,
                        RedistributionMode.SPLIT_DISTRIBUTE, 0, 0));
        org.junit.jupiter.api.Assertions.assertThrows(io.nop.stream.core.exceptions.StreamException.class,
                () -> backend.restoreState(new ArrayList<>(), 2,
                        RedistributionMode.SPLIT_DISTRIBUTE, 0, -1));
    }

    @org.junit.jupiter.api.Test
    void testSplitDistributeOutOfRangeTaskIndexFailsFast() {
        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        // taskIndex == newParallelism: overlap stride (would duplicate state).
        org.junit.jupiter.api.Assertions.assertThrows(io.nop.stream.core.exceptions.StreamException.class,
                () -> backend.restoreState(new ArrayList<>(), 4,
                        RedistributionMode.SPLIT_DISTRIBUTE, 2, 2));
        // negative taskIndex: previously silently clamped to 0.
        org.junit.jupiter.api.Assertions.assertThrows(io.nop.stream.core.exceptions.StreamException.class,
                () -> backend.restoreState(new ArrayList<>(), 4,
                        RedistributionMode.SPLIT_DISTRIBUTE, -1, 2));
    }
}
