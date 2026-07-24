/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import io.nop.stream.core.common.state.backend.RedistributionMode;
import io.nop.stream.core.common.state.backend.memory.MemoryOperatorStateBackend;
import io.nop.stream.core.operators.StreamSourceOperator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestE2EOperatorStateRedistribution {

    @Test
    void testSameParallelismRestorePreservesExactlyOnce() throws Exception {
        OperatorSnapshotResult snap = new OperatorSnapshotResult();
        snap.putOperatorState("list", Arrays.asList("a", "b", "c"));
        snap.setCheckpointParallelism(2);

        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        backend.restoreState(Arrays.asList(snap), 2, RedistributionMode.NONE, 0, 2);

        OperatorSnapshotResult result = backend.snapshotState(1);
        assertEquals(3, ((List<?>) result.getOperatorState("list")).size());
        assertTrue(((List<?>) result.getOperatorState("list")).containsAll(Arrays.asList("a", "b", "c")));
    }

    @Test
    void testScaleUpUnionPreservesExactlyOnce() throws Exception {
        OperatorSnapshotResult old1 = new OperatorSnapshotResult();
        old1.putOperatorState("items", Arrays.asList("a", "b"));
        old1.setCheckpointParallelism(2);

        OperatorSnapshotResult old2 = new OperatorSnapshotResult();
        old2.putOperatorState("items", Arrays.asList("c", "d"));
        old2.setCheckpointParallelism(2);

        List<OperatorSnapshotResult> oldSnapshots = Arrays.asList(old1, old2);

        MemoryOperatorStateBackend[] newTasks = new MemoryOperatorStateBackend[4];
        for (int i = 0; i < 4; i++) {
            newTasks[i] = new MemoryOperatorStateBackend();
            newTasks[i].restoreState(oldSnapshots, 2, RedistributionMode.UNION, i, 4);
        }

        for (int i = 0; i < 4; i++) {
            Map<String, Object> states = newTasks[i].snapshotState(1).getOperatorStates();
            assertTrue(states.containsKey("items"));
            List<?> items = (List<?>) states.get("items");
            assertEquals(4, items.size());
            assertTrue(items.containsAll(Arrays.asList("a", "b", "c", "d")));
        }
    }

    @Test
    void testScaleDownSplitDistributeNoDataLoss() throws Exception {
        OperatorSnapshotResult old1 = new OperatorSnapshotResult();
        old1.putOperatorState("items", Arrays.asList("e0", "e1"));
        old1.setCheckpointParallelism(4);

        OperatorSnapshotResult old2 = new OperatorSnapshotResult();
        old2.putOperatorState("items", Arrays.asList("e2", "e3"));
        old2.setCheckpointParallelism(4);

        OperatorSnapshotResult old3 = new OperatorSnapshotResult();
        old3.putOperatorState("items", Arrays.asList("e4", "e5"));
        old3.setCheckpointParallelism(4);

        OperatorSnapshotResult old4 = new OperatorSnapshotResult();
        old4.putOperatorState("items", Arrays.asList("e6", "e7"));
        old4.setCheckpointParallelism(4);

        List<OperatorSnapshotResult> oldSnapshots = Arrays.asList(old1, old2, old3, old4);

        MemoryOperatorStateBackend[] newTasks = new MemoryOperatorStateBackend[2];
        for (int i = 0; i < 2; i++) {
            newTasks[i] = new MemoryOperatorStateBackend();
            newTasks[i].restoreState(oldSnapshots, 4, RedistributionMode.SPLIT_DISTRIBUTE, i, 2);
        }

        Map<String, Object> states0 = newTasks[0].snapshotState(1).getOperatorStates();
        Map<String, Object> states1 = newTasks[1].snapshotState(1).getOperatorStates();
        List<?> items0 = (List<?>) states0.get("items");
        List<?> items1 = (List<?>) states1.get("items");

        assertEquals(4, items0.size());
        assertEquals(4, items1.size());

        List<String> all = new ArrayList<>();
        all.addAll((List<String>) (List<?>) items0);
        all.addAll((List<String>) (List<?>) items1);
        assertEquals(8, all.size());
        for (int i = 0; i < 8; i++) {
            assertTrue(all.contains("e" + i), "Missing e" + i);
        }
    }

    @Test
    void testLatestCheckpointRestore() throws Exception {
        OperatorSnapshotResult cp1 = new OperatorSnapshotResult();
        cp1.putOperatorState("offset", 10L);
        cp1.setCheckpointParallelism(1);

        OperatorSnapshotResult cp2 = new OperatorSnapshotResult();
        cp2.putOperatorState("offset", 20L);
        cp2.setCheckpointParallelism(1);

        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        backend.restoreState(Arrays.asList(cp2), 1, RedistributionMode.NONE, 0, 1);

        OperatorSnapshotResult restored = backend.snapshotState(1);
        assertEquals(20L, restored.getOperatorState("offset"));
        assertNotEquals(10L, restored.getOperatorState("offset"));
    }

    @Test
    void testSourceOffsetCheckpointAndRestoreEndToEnd() throws Exception {
        OperatorSnapshotResult snap = new OperatorSnapshotResult();
        snap.putOperatorState(StreamSourceOperator.SOURCE_OFFSET_KEY, 7L);
        snap.setCheckpointParallelism(1);

        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        backend.restoreState(Arrays.asList(snap), 1, RedistributionMode.NONE, 0, 1);

        OperatorSnapshotResult restored = backend.snapshotState(1);
        assertEquals(7L, restored.getOperatorState(StreamSourceOperator.SOURCE_OFFSET_KEY));
    }

    @Test
    void testRedistributionAntiHollowCheck() throws Exception {
        OperatorSnapshotResult old1 = new OperatorSnapshotResult();
        old1.putOperatorState("data", Arrays.asList("x", "y", "z"));
        old1.setCheckpointParallelism(2);

        OperatorSnapshotResult old2 = new OperatorSnapshotResult();
        old2.putOperatorState("data", Arrays.asList("1", "2", "3"));
        old2.setCheckpointParallelism(2);

        List<OperatorSnapshotResult> oldSnapshots = Arrays.asList(old1, old2);

        for (int taskIdx = 0; taskIdx < 3; taskIdx++) {
            MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
            backend.restoreState(oldSnapshots, 2, RedistributionMode.SPLIT_DISTRIBUTE, taskIdx, 3);
            OperatorSnapshotResult result = backend.snapshotState(1);
            assertNotNull(result.getOperatorState("data"),
                    "Task " + taskIdx + " should have received data via redistribution");
            List<?> data = (List<?>) result.getOperatorState("data");
            assertFalse(data.isEmpty(),
                    "Task " + taskIdx + " should have non-empty data after SPLIT_DISTRIBUTE");
        }
    }
}
