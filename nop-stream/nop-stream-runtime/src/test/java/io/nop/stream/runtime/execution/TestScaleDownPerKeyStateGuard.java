/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointPlan;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5 WIN-2/WIN-4 Phase 1 regression pin: scale-down of a vertex whose
 * checkpoint carries per-key auxiliary operator state (window timers / pane
 * tracking / trigger accumulators / CEP ledger / reduce values) must fail fast
 * — 1:1 copy would split that state from the key-group-redistributed window
 * contents and silently drop fired windows.
 */
class TestScaleDownPerKeyStateGuard {

    private static final String JOB = "job-win21";
    private static final String PIPELINE = "pipeline-0";
    private static final String VERTEX = "keyed-vertex";

    private static TaskLocation loc(int taskIndex) {
        return new TaskLocation(JOB, PIPELINE, VERTEX, taskIndex);
    }

    private static CheckpointPlan planOf(TaskLocation... locations) {
        return new CheckpointPlan(JOB, PIPELINE,
                java.util.Arrays.asList(locations), java.util.Arrays.asList(locations), Collections.emptyMap());
    }

    private static GraphModelCheckpointExecutor.TaskStateLookup lookupOf(Map<TaskLocation, TaskStateSnapshot> states) {
        return states::get;
    }

    private static TaskStateSnapshot snapshotWithWindowTimers(long checkpointId, int taskIndex) {
        TaskStateSnapshot snap = new TaskStateSnapshot(loc(taskIndex), checkpointId);
        snap.putOperatorState("internal-timers", "timer-entries-of-subtask-" + taskIndex);
        return snap;
    }

    @Test
    void scaleDownWithWindowTimersFailsFast() {
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        states.put(loc(0), snapshotWithWindowTimers(7L, 0));
        states.put(loc(1), snapshotWithWindowTimers(7L, 1));

        io.nop.stream.core.common.state.shard.KeyGroupRange newRange =
                io.nop.stream.core.common.state.shard.KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(2, 1, 0);
        StreamException e = assertThrows(StreamException.class, () ->
                RescaleStateAssembler.buildRescaledTaskState(VERTEX, 0, newRange,
                        java.util.Arrays.asList(loc(0), loc(1)), 1, 2, 4, lookupOf(states), planOf(loc(0), loc(1))));
        assertTrue(String.valueOf(e.getParam("detail")).contains("R5 WIN-2/WIN-4"),
                () -> "scale-down guard must carry WIN guidance: " + e.getParam("detail"));
    }

    // ------------------------------------------------------------------
    // R6 KR-09 (plan 2324): production-shape pins. The ONLY production writer
    // of operator-state keys is CheckpointBarrierTracker, which namespaces
    // every key as "operator-{i}-<name>". The bare-name equals previously
    // never matched a production snapshot — the guard was dead code on live
    // data while these fixtures fed bare keys (hollow green). Reverting
    // RescaleStateAssembler.isPerKeyAuxiliaryOperatorState to bare equals (or
    // dropping the three keys added in 2324) turns these tests red.
    // ------------------------------------------------------------------

    private static TaskStateSnapshot snapshotWithOperatorStates(long checkpointId, int taskIndex,
                                                                String... stateKeys) {
        TaskStateSnapshot snap = new TaskStateSnapshot(loc(taskIndex), checkpointId);
        for (String key : stateKeys) {
            snap.putOperatorState(key, "payload-of-subtask-" + taskIndex);
        }
        return snap;
    }

    private static void assertScaleDownRejected(Map<TaskLocation, TaskStateSnapshot> states,
                                                String expectedFragment) {
        io.nop.stream.core.common.state.shard.KeyGroupRange newRange =
                io.nop.stream.core.common.state.shard.KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(2, 1, 0);
        StreamException e = assertThrows(StreamException.class, () ->
                RescaleStateAssembler.buildRescaledTaskState(VERTEX, 0, newRange,
                        java.util.Arrays.asList(loc(0), loc(1)), 1, 2, 4, lookupOf(states),
                        planOf(loc(0), loc(1))));
        assertTrue(String.valueOf(e.getParam("detail")).contains(expectedFragment),
                () -> "scale-down guard must fire on production-shape keys: " + e.getParam("detail"));
    }

    @Test
    void productionPrefixedWindowTimersFailFast() {
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        states.put(loc(0), snapshotWithOperatorStates(7L, 0, "operator-0-internal-timers"));
        states.put(loc(1), snapshotWithOperatorStates(7L, 1, "operator-1-internal-timers"));
        assertScaleDownRejected(states, "R5 WIN-2/WIN-4");
    }

    @Test
    void processInternalTimersKeyFailsFast() {
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        // 2312 added this key AFTER the R5 guard list was written — the original
        // KR-09/REG-05 miss.
        states.put(loc(0), snapshotWithOperatorStates(7L, 0, "operator-0-process-internal-timers"));
        states.put(loc(1), snapshotWithOperatorStates(7L, 1, "operator-1-process-internal-timers"));
        assertScaleDownRejected(states, "R5 WIN-2/WIN-4");
    }

    @Test
    void equiJoinAndOverWindowBufferKeysFailFast() {
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        states.put(loc(0), snapshotWithOperatorStates(7L, 0,
                "operator-0-equi-join-buffer", "operator-0-over-window-buffer"));
        states.put(loc(1), snapshotWithOperatorStates(7L, 1,
                "operator-1-equi-join-buffer", "operator-1-over-window-buffer"));
        assertScaleDownRejected(states, "R5 WIN-2/WIN-4");
    }

    @Test
    void nonAuxiliaryPrefixedStateDoesNotTripGuard() throws Exception {
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        states.put(loc(0), snapshotWithOperatorStates(7L, 0, "operator-0-window-contents"));
        states.put(loc(1), snapshotWithOperatorStates(7L, 1, "operator-1-window-contents"));
        // must NOT throw: non-auxiliary operator state on scale-down keeps the
        // trimmed-subtask semantics (guard is scoped to per-key auxiliary state)
        io.nop.stream.core.common.state.shard.KeyGroupRange newRange =
                io.nop.stream.core.common.state.shard.KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(2, 1, 0);
        RescaleStateAssembler.buildRescaledTaskState(VERTEX, 0, newRange,
                java.util.Arrays.asList(loc(0), loc(1)), 1, 2, 4, lookupOf(states),
                planOf(loc(0), loc(1)));
    }
}
