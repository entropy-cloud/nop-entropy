/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.OperatorStateMapping;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * R6 KR-07 regression pin (plan 2324): two chained keyed operators (process ->
 * process shape — canChain has no keyed constraint, so the chain is reachable
 * through ordinary API) used to snapshot their keyed state under the IDENTICAL
 * raw key "keyed-state"; the tracker's putKeyedState let the last writer erase
 * the first operator's snapshot, and restore cross-fed heterogeneous keyed
 * state. The tracker now namespaces each operator's keyed snapshot under
 * {@code operator-{i}-keyed-<rawKey>}, and the restore side reads that prefix
 * first, falling back to the raw key for old epochs. Reverting the tracker-side
 * namespace derivation (or the restore-side prefix read) turns these tests red.
 */
class TestChainedKeyedOperatorsCheckpointIsolation {

    private static final TaskLocation LOC =
            new TaskLocation("job-kr07", "pipeline-0", "kr07-vertex", 0);

    private static OperatorSnapshotResult keyedSnapshot(long checkpointId, String marker) {
        OperatorSnapshotResult r = OperatorSnapshotResult.builder()
                .putKeyedState("keyed-state", marker)
                .build();
        r.setCheckpointId(checkpointId);
        return r;
    }

    private static java.util.List<OperatorStateMapping> twoOperatorMappings() {
        // hasKeyedState=false — the production shape: the marker derives from
        // getKeyedStateBackend(), which is always null at plan-build time.
        return Arrays.asList(
                new OperatorStateMapping(0, "operator-0", null, false),
                new OperatorStateMapping(1, "operator-1", null, false));
    }

    @Test
    void chainedKeyedOperatorsKeyedSnapshotsDoNotOverwriteOnAck() throws Exception {
        AtomicReference<TaskStateSnapshot> merged = new AtomicReference<>();
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
                LOC,
                Arrays.asList(new io.nop.stream.core.operators.StreamSinkOperator<>(v -> {
                }),
                        new io.nop.stream.core.operators.StreamSinkOperator<>(v -> {
                        })),
                twoOperatorMappings(),
                merged::set);

        assertTrue(tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT),
                "checkpoint epoch must be armed before ACKs");
        tracker.acknowledgeOperator(0, keyedSnapshot(1L, "SNAPSHOT-OP0"));
        tracker.acknowledgeOperator(1, keyedSnapshot(1L, "SNAPSHOT-OP1"));
        org.junit.jupiter.api.Assertions.assertNotNull(merged.get(),
                "both operators ACKed — the epoch must be complete");

        assertEquals("SNAPSHOT-OP0", merged.get().getKeyedState("operator-0-keyed-keyed-state"),
                "operator 0's keyed snapshot must survive under its own namespace");
        assertEquals("SNAPSHOT-OP1", merged.get().getKeyedState("operator-1-keyed-keyed-state"),
                "operator 1's keyed snapshot must survive under its own namespace");
        assertNull(merged.get().getKeyedState("keyed-state"),
                "no bare-key entry may remain (the overwrite vector)");
    }

    @Test
    void restoreSideReadsPerOperatorPrefixAndFallsBackToLegacyRawKey() {
        // new format: both operators namespaced
        TaskStateSnapshot newState = new TaskStateSnapshot(LOC, 1L);
        newState.putKeyedState("operator-0-keyed-keyed-state", "SNAPSHOT-OP0");
        newState.putKeyedState("operator-1-keyed-keyed-state", "SNAPSHOT-OP1");
        newState.putOperatorState("operator-0-internal-timers", "timers-0");
        newState.putOperatorState("operator-1-internal-timers", "timers-1");

        OperatorSnapshotResult forOp0 = GraphModelCheckpointExecutor.buildSnapshotFromTaskState(
                newState, 0, twoOperatorMappings());
        OperatorSnapshotResult forOp1 = GraphModelCheckpointExecutor.buildSnapshotFromTaskState(
                newState, 1, twoOperatorMappings());
        assertEquals("SNAPSHOT-OP0", forOp0.getKeyedState("keyed-state"),
                "operator 0 must read back exactly its own keyed snapshot");
        assertEquals("SNAPSHOT-OP1", forOp1.getKeyedState("keyed-state"),
                "operator 1 must read back exactly its own keyed snapshot");
        assertEquals("timers-0", forOp0.getOperatorState("internal-timers"));

        // legacy epoch: single raw keyed entry, no namespaces
        TaskStateSnapshot legacyState = new TaskStateSnapshot(LOC, 2L);
        legacyState.putKeyedState("keyed-state", "LEGACY-SNAPSHOT");
        OperatorSnapshotResult legacyForOp1 = GraphModelCheckpointExecutor.buildSnapshotFromTaskState(
                legacyState, 1, twoOperatorMappings());
        assertEquals("LEGACY-SNAPSHOT", legacyForOp1.getKeyedState("keyed-state"),
                "old epochs written before the namespace must keep restoring");
    }
}
