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

import static org.junit.jupiter.api.Assertions.*;

class TestMemoryOperatorStateBackend {

    @Test
    void testRedistributionModeEnumExists() {
        assertNotNull(RedistributionMode.NONE);
        assertNotNull(RedistributionMode.UNION);
        assertNotNull(RedistributionMode.BROADCAST);
        assertNotNull(RedistributionMode.SPLIT_DISTRIBUTE);
    }

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

    @Test
    void testBroadcastRedistribution() throws Exception {
        OperatorSnapshotResult old1 = new OperatorSnapshotResult();
        old1.putOperatorState("x", "from1");
        old1.setCheckpointParallelism(3);

        OperatorSnapshotResult old2 = new OperatorSnapshotResult();
        old2.putOperatorState("x", "from2");
        old2.setCheckpointParallelism(3);

        MemoryOperatorStateBackend task0 = new MemoryOperatorStateBackend();
        task0.restoreState(Arrays.asList(old1, old2), 3, RedistributionMode.BROADCAST, 0, 2);

        MemoryOperatorStateBackend task1 = new MemoryOperatorStateBackend();
        task1.restoreState(Arrays.asList(old1, old2), 3, RedistributionMode.BROADCAST, 1, 2);

        assertEquals("from1", task0.snapshotState(1).getOperatorState("x"));
        assertEquals("from1", task1.snapshotState(1).getOperatorState("x"));
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
}
