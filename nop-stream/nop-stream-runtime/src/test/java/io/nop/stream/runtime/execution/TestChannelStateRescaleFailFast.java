/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.ChannelState;
import io.nop.stream.core.checkpoint.TaskEpochSnapshot;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stage 47, Phase 2: focused unit tests for the channel-state rescale fail-fast
 * ({@code GraphModelCheckpointExecutor.assertNoChannelStateOnRescale}).
 *
 * <p>Proves the live defect fix (plan guide #24 No-Silent-No-Op): a rescale
 * restore (parallelism change) that would have to redistribute unaligned
 * channel state now fails fast with {@code ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED}
 * instead of silently dropping the in-flight data via the prior
 * {@code instanceof TaskEpochSnapshot} guard in {@code restoreChannelStateIfPresent}.
 *
 * <p>Lives in the {@code execution} package so it can drive the package-private
 * helper ({@code assertNoChannelStateOnRescale}) and {@code TaskStateLookup}
 * directly (same pattern as {@code TestSavepointVertexSetDifferential} calling
 * {@code validateReverseVertexDifferential}).
 */
public class TestChannelStateRescaleFailFast {

    private static final String JOB = "cs-rescale-job";
    private static final String PIPELINE = "cs-rescale-pipeline";
    private static final String VERTEX = "cs-vertex";

    /** A lookup backed by an explicit map of location -> snapshot. */
    private static GraphModelCheckpointExecutor.TaskStateLookup lookupOf(
            Map<TaskLocation, TaskStateSnapshot> states) {
        return loc -> states.get(loc);
    }

    private static TaskLocation loc(int taskIndex) {
        return new TaskLocation(JOB, PIPELINE, VERTEX, taskIndex);
    }

    /** Builds a TaskEpochSnapshot that carries a non-empty ChannelState. */
    private static TaskEpochSnapshot snapshotWithChannelState(long checkpointId, int taskIndex) {
        TaskEpochSnapshot snap = new TaskEpochSnapshot(loc(taskIndex), checkpointId);
        ChannelState cs = new ChannelState();
        cs.putRecords(0, Collections.singletonList(new StreamRecord<>("in-flight-" + taskIndex)));
        snap.setChannelState(cs);
        return snap;
    }

    /** Builds a TaskEpochSnapshot with null channel state (aligned checkpoint). */
    private static TaskEpochSnapshot snapshotWithoutChannelState(long checkpointId, int taskIndex) {
        return new TaskEpochSnapshot(loc(taskIndex), checkpointId);
    }

    // ==================== The fix: rescale + channel state -> fail-fast ====================

    @Test
    void rescaleWithNonEmptyChannelStateFailsFast() {
        // Two old subtasks both carrying unaligned channel state; rescale 2 -> 4.
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        states.put(loc(0), snapshotWithChannelState(7L, 0));
        states.put(loc(1), snapshotWithChannelState(7L, 1));

        StreamException thrown = assertThrows(StreamException.class, () ->
                GraphModelCheckpointExecutor.assertNoChannelStateOnRescale(
                        VERTEX, Arrays.asList(loc(0), loc(1)), 4, 2,
                        lookupOf(states)));
        assertEquals(NopStreamErrors.ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED.getErrorCode(),
                thrown.getErrorCode(),
                "rescale + non-empty channel state must fail-fast (not silently drop)");
        // Anti-Hollow: the error carries the rescale context params.
        assertEquals(VERTEX, thrown.getParam(ARG_VERTEX_ID_NAME));
        assertEquals(2, thrown.getParam(ARG_OLD_PARALLELISM_NAME));
        assertEquals(4, thrown.getParam(ARG_NEW_PARALLELISM_NAME));
    }

    @Test
    void rescaleWithChannelStateOnSomeSubtaskStillFailsFast() {
        // Only one of the old subtasks carries channel state — still must fail-fast
        // (dropping even one subtask's in-flight data breaks exactly-once).
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        states.put(loc(0), snapshotWithoutChannelState(7L, 0));
        states.put(loc(1), snapshotWithChannelState(7L, 1));

        StreamException thrown = assertThrows(StreamException.class, () ->
                GraphModelCheckpointExecutor.assertNoChannelStateOnRescale(
                        VERTEX, Arrays.asList(loc(0), loc(1)), 4, 2,
                        lookupOf(states)));
        assertEquals(NopStreamErrors.ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED.getErrorCode(),
                thrown.getErrorCode());
    }

    // ==================== No regression: aligned / empty channel state -> OK ====================

    @Test
    void rescaleWithEmptyChannelStateSucceeds() {
        // Aligned checkpoint: TaskEpochSnapshot with an explicit but EMPTY channel state.
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        TaskEpochSnapshot s0 = new TaskEpochSnapshot(loc(0), 7L);
        s0.setChannelState(new ChannelState()); // empty
        TaskEpochSnapshot s1 = new TaskEpochSnapshot(loc(1), 7L);
        s1.setChannelState(new ChannelState()); // empty
        states.put(loc(0), s0);
        states.put(loc(1), s1);

        assertDoesNotThrow(() ->
                GraphModelCheckpointExecutor.assertNoChannelStateOnRescale(
                        VERTEX, Arrays.asList(loc(0), loc(1)), 4, 2, lookupOf(states)));
    }

    @Test
    void rescaleWithNullChannelStateSucceeds() {
        // Aligned checkpoint: TaskEpochSnapshot with null channel state (default).
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        states.put(loc(0), snapshotWithoutChannelState(7L, 0));
        states.put(loc(1), snapshotWithoutChannelState(7L, 1));

        assertDoesNotThrow(() ->
                GraphModelCheckpointExecutor.assertNoChannelStateOnRescale(
                        VERTEX, Arrays.asList(loc(0), loc(1)), 4, 2, lookupOf(states)));
    }

    @Test
    void rescaleWithPlainTaskStateSnapshotSucceeds() {
        // A plain (non-epoch) snapshot has no channel state at all — must NOT trip
        // the guard. This is the aligned/legacy snapshot shape.
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        states.put(loc(0), new TaskStateSnapshot(loc(0), 7L));
        states.put(loc(1), new TaskStateSnapshot(loc(1), 7L));

        assertDoesNotThrow(() ->
                GraphModelCheckpointExecutor.assertNoChannelStateOnRescale(
                        VERTEX, Arrays.asList(loc(0), loc(1)), 4, 2, lookupOf(states)));
    }

    private static final String ARG_VERTEX_ID_NAME = "vertexId";
    private static final String ARG_OLD_PARALLELISM_NAME = "oldParallelism";
    private static final String ARG_NEW_PARALLELISM_NAME = "newParallelism";

    @Test
    void emptyOldSubtasksSucceeds() {
        // Defensive: no old subtasks -> nothing to rescale -> no throw.
        assertDoesNotThrow(() ->
                GraphModelCheckpointExecutor.assertNoChannelStateOnRescale(
                        VERTEX, Collections.emptyList(), 4, 0, lookupOf(new LinkedHashMap<>())));
    }

    @Test
    void missingSnapshotForOldSubtaskIsSkippedNotFailed() {
        // A null lookup result (subtask absent from checkpoint) is skipped here —
        // the forward-differential check (validateReverseVertexDifferential) is
        // responsible for rejecting missing state. This guard only targets
        // channel state presence.
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        states.put(loc(0), snapshotWithChannelState(7L, 0));
        // loc(1) absent -> lookup returns null
        List<TaskLocation> oldSubtasks = Arrays.asList(loc(0), loc(1));

        StreamException thrown = assertThrows(StreamException.class, () ->
                GraphModelCheckpointExecutor.assertNoChannelStateOnRescale(
                        VERTEX, oldSubtasks, 4, 2, lookupOf(states)));
        assertEquals(NopStreamErrors.ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED.getErrorCode(),
                thrown.getErrorCode(),
                "subtask 0's channel state must still trigger fail-fast even if subtask 1 is absent");
    }
}
