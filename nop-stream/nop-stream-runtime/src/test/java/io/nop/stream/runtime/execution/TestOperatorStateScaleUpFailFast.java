/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointPlan;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 369 Phase 3 NEW-A: a scale-up rescale (newParallelism &gt; oldParallelism)
 * that would have to redistribute operator (non-keyed) state across the new
 * parallelism fails fast with {@code ERR_STREAM_OPERATOR_STATE_SCALE_UP_UNSUPPORTED}
 * instead of silently starting the added subtasks with empty operator state
 * (pre-fix behavior, reproduced pre-fix in {@code _tmp/r6-p3-newa-repro.log}).
 *
 * <p>Symmetric with the AR-03 scale-down guard in
 * {@code MaxParallelismReshardMigration}: scale-down restores keep the 1:1
 * by-index copy for every kept subtask and never trip this guard;
 * same-parallelism restores never reach {@code buildRescaledTaskState} at all.
 *
 * <p>Lives in the {@code execution} package so it can drive the package-private
 * {@code RescaleStateAssembler} helpers directly (same pattern as
 * {@code TestChannelStateRescaleFailFast}).
 */
public class TestOperatorStateScaleUpFailFast {

    private static final String JOB = "newa-job";
    private static final String PIPELINE = "newa-pipeline";
    private static final String VERTEX = "newa-vertex";

    private static TaskLocation loc(int taskIndex) {
        return new TaskLocation(JOB, PIPELINE, VERTEX, taskIndex);
    }

    private static GraphModelCheckpointExecutor.TaskStateLookup lookupOf(
            Map<TaskLocation, TaskStateSnapshot> states) {
        return states::get;
    }

    private static CheckpointPlan planOf(TaskLocation... locations) {
        return new CheckpointPlan(JOB, PIPELINE,
                Arrays.asList(locations), Arrays.asList(locations), Collections.emptyMap());
    }

    private static TaskStateSnapshot snapshotWithOperatorState(long checkpointId, int taskIndex) {
        TaskStateSnapshot snap = new TaskStateSnapshot(loc(taskIndex), checkpointId);
        snap.putOperatorState("split-assignment", "split-table-of-subtask-" + taskIndex);
        return snap;
    }

    // ==================== The fix: scale-up + operator state -> fail-fast ====================

    @Test
    void scaleUpWithNonEmptyOperatorStateFailsFast() {
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        states.put(loc(0), snapshotWithOperatorState(7L, 0));

        KeyGroupRange newRange = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(4, 2, 1);
        StreamException thrown = assertThrows(StreamException.class, () ->
                RescaleStateAssembler.buildRescaledTaskState(VERTEX, 1, newRange,
                        Collections.singletonList(loc(0)), 2, 1, 4, lookupOf(states), planOf(loc(0))));
        assertEquals(NopStreamErrors.ERR_STREAM_OPERATOR_STATE_SCALE_UP_UNSUPPORTED.getErrorCode(),
                thrown.getErrorCode(),
                "scale-up + non-empty operator state must fail fast, not silently empty the added subtask");
        // Anti-Hollow: the error carries the rescale context params.
        assertEquals(VERTEX, thrown.getParam("vertexId"));
        assertEquals(1, thrown.getParam("oldParallelism"));
        assertEquals(2, thrown.getParam("newParallelism"));
    }

    @Test
    void scaleUpTripsGuardWhenAnyOldSubtaskCarriesOperatorState() {
        // Two old subtasks: only subtask 1 carries operator state — still fail-fast
        // (the added subtasks cannot know which parts they should own).
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        TaskStateSnapshot s0 = new TaskStateSnapshot(loc(0), 7L);
        states.put(loc(0), s0);
        states.put(loc(1), snapshotWithOperatorState(7L, 1));

        StreamException thrown = assertThrows(StreamException.class, () ->
                RescaleStateAssembler.assertNoOperatorStateOnScaleUp(
                        VERTEX, Arrays.asList(loc(0), loc(1)), 4, 2, lookupOf(states)));
        assertEquals(NopStreamErrors.ERR_STREAM_OPERATOR_STATE_SCALE_UP_UNSUPPORTED.getErrorCode(),
                thrown.getErrorCode());
        assertTrue(String.valueOf(thrown.getParam("detail")).contains("split-assignment"),
                "the failing check names the offending operator state keys");
    }

    // ==================== Unaffected shapes ====================

    @Test
    void scaleUpWithEmptyOperatorStatesSucceeds() throws Exception {
        // Keyed-state-only vertices (the common shape) rescale as before.
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        states.put(loc(0), new TaskStateSnapshot(loc(0), 7L));

        KeyGroupRange newRange = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(4, 2, 1);
        TaskStateSnapshot merged = RescaleStateAssembler.buildRescaledTaskState(VERTEX, 1, newRange,
                Collections.singletonList(loc(0)), 2, 1, 4, lookupOf(states), planOf(loc(0)));
        assertNull(merged.getOperatorState("split-assignment"));
        assertTrue(merged.getOperatorStates().isEmpty(),
                "no operator state anywhere in the checkpoint -> added subtask has none, no failure");
    }

    @Test
    void assertGuardIsNoOpForScaleDownAndSameParallelism() {
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        states.put(loc(0), snapshotWithOperatorState(7L, 0));
        states.put(loc(1), snapshotWithOperatorState(7L, 1));

        // Scale-down 2 -> 1: the guard is a no-op (AR-03 owns trimmed subtasks,
        // and every kept index still maps 1:1 to an old subtask).
        assertDoesNotThrow(() -> RescaleStateAssembler.assertNoOperatorStateOnScaleUp(
                VERTEX, Arrays.asList(loc(0), loc(1)), 1, 2, lookupOf(states)));
        // Same parallelism: never reaches this method in production (the caller
        // gates on oldP != newP); the guard is equally a no-op.
        assertDoesNotThrow(() -> RescaleStateAssembler.assertNoOperatorStateOnScaleUp(
                VERTEX, Arrays.asList(loc(0), loc(1)), 2, 2, lookupOf(states)));
    }

    @Test
    void scaleDownKeepsOneToOneOperatorStateCopy() throws Exception {
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        states.put(loc(0), snapshotWithOperatorState(7L, 0));
        states.put(loc(1), snapshotWithOperatorState(7L, 1));

        // Scale-down 2 -> 1, kept subtask 0: 1:1 operator state copy unchanged.
        KeyGroupRange newRange = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(4, 1, 0);
        TaskStateSnapshot merged = RescaleStateAssembler.buildRescaledTaskState(VERTEX, 0, newRange,
                Arrays.asList(loc(0), loc(1)), 1, 2, 4, lookupOf(states), planOf(loc(0), loc(1)));
        assertEquals("split-table-of-subtask-0", merged.getOperatorState("split-assignment"),
                "scale-down keeps the 1:1 by-index operator state copy");
    }
}
