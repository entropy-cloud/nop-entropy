/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointPlan;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.Subtask;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-7: savepoint/checkpoint vertex-set differential. The forward direction
 * (current vertex absent from checkpoint) was already rejected at
 * {@link GraphModelCheckpointExecutor} via the {@code stateLookup} throwing
 * on missing state. The reverse direction (checkpoint has vertex not in
 * current graph) was silently dropped — the restore loop only walked current
 * vertices. This file hardens both directions as regression guards and
 * asserts the new reverse-direction reject.
 *
 * <p>Lives in the {@code execution} package so it can drive package-private
 * test helpers ({@code restoreTaskStatesFromSourceForTest},
 * {@code validateReverseVertexDifferential}) directly.
 *
 * <p>Per checkpoint-design.md §8.6 "delete stateful vertex = default reject".
 * operatorId-level differential and §8.6's nuanced state-aware
 * classification (distinguish stateful vs stateless new vertex, initial-state
 * fallback) are deferred to the roadmap successor (see plan Deferred But
 * Adjudicated).
 */
public class TestSavepointVertexSetDifferential {

    private static final String JOB = "diff-job";
    private static final String PIPELINE = "diff-pipeline";

    // ==================== Forward direction regression guards ====================
    // These protect the forward-direction reject (current vertex absent from
    // checkpoint). The throw is hardened as a pre-check inside
    // validateReverseVertexDifferential, so removing it (or weakening to a
    // LOG.warn) breaks these tests — anti-hollow.

    @Test
    void forward_missingStateForCurrentVertexRejectsViaLookupThrow() {
        // Current graph has {v1, v2}; checkpoint only has {v1}. v2 is missing
        // from the checkpoint — forward differential must reject.
        GraphExecutionPlan execPlan = planForVertices("v1", "v2");
        CheckpointPlan checkpointPlan = checkpointPlanFor("v1", "v2");
        Set<TaskLocation> checkpointLocations = locationsFor("v1");

        StreamException thrown = assertThrows(StreamException.class, () ->
                GraphModelCheckpointExecutor.validateReverseVertexDifferential(
                        execPlan, checkpointPlan, checkpointLocations));
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED.getErrorCode(),
                thrown.getErrorCode(),
                "forward differential (current vertex missing from checkpoint) must throw RESTORE_FAILED");
    }

    @Test
    void forward_newStatefulVertexInCurrentGraphRejectsRestore() {
        // The new-stateful scenario: current graph adds a vertex the checkpoint
        // never saw.
        GraphExecutionPlan execPlan = planForVertices("existing", "new-stateful");
        CheckpointPlan checkpointPlan = checkpointPlanFor("existing", "new-stateful");
        Set<TaskLocation> checkpointLocations = locationsFor("existing");

        StreamException thrown = assertThrows(StreamException.class, () ->
                GraphModelCheckpointExecutor.validateReverseVertexDifferential(
                        execPlan, checkpointPlan, checkpointLocations));
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED.getErrorCode(),
                thrown.getErrorCode());
    }

    @Test
    void forward_currentGraphSupersetOfCheckpointRejectsRestore() {
        // Superset case: current graph = {a, b, c, d}, checkpoint = {a}.
        GraphExecutionPlan execPlan = planForVertices("a", "b", "c", "d");
        CheckpointPlan checkpointPlan = checkpointPlanFor("a", "b", "c", "d");
        Set<TaskLocation> checkpointLocations = locationsFor("a");

        StreamException thrown = assertThrows(StreamException.class, () ->
                GraphModelCheckpointExecutor.validateReverseVertexDifferential(
                        execPlan, checkpointPlan, checkpointLocations));
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED.getErrorCode(),
                thrown.getErrorCode());
    }

    // ==================== Reverse direction (the actual P0-7 fix) ====================

    @Test
    void reverse_deletedVertexInCheckpointRejectsRestore() {
        // Checkpoint has {v1, v2}; current graph only has {v1} — v2 was
        // deleted. Reverse-direction reject must fire on the differential.
        GraphExecutionPlan execPlan = planForVertices("v1");
        CheckpointPlan checkpointPlan = checkpointPlanFor("v1");
        Set<TaskLocation> checkpointLocations = locationsFor("v1", "v2-removed");

        StreamException thrown = assertThrows(StreamException.class, () ->
                GraphModelCheckpointExecutor.validateReverseVertexDifferential(
                        execPlan, checkpointPlan, checkpointLocations));
        assertEquals(NopStreamErrors.ERR_STREAM_SAVEPOINT_VERTEX_DIFFERENTIAL.getErrorCode(),
                thrown.getErrorCode(),
                "reverse differential (deleted stateful vertex) must throw VERTEX_DIFFERENTIAL");
    }

    @Test
    void reverse_checkpointSubsetOfCurrentGraphRejectsRestore() {
        // Symmetric case: current graph is a strict subset of the checkpoint
        // (checkpoint has b, c that current graph lacks).
        GraphExecutionPlan execPlan = planForVertices("a");
        CheckpointPlan checkpointPlan = checkpointPlanFor("a");
        Set<TaskLocation> checkpointLocations = locationsFor("a", "b", "c");

        StreamException thrown = assertThrows(StreamException.class, () ->
                GraphModelCheckpointExecutor.validateReverseVertexDifferential(
                        execPlan, checkpointPlan, checkpointLocations));
        assertEquals(NopStreamErrors.ERR_STREAM_SAVEPOINT_VERTEX_DIFFERENTIAL.getErrorCode(),
                thrown.getErrorCode());
    }

    // ==================== Baseline: same vertex set succeeds ====================

    @Test
    void sameVertexSetRestoreSucceeds() {
        GraphExecutionPlan execPlan = planForVertices("v1", "v2");
        CheckpointPlan checkpointPlan = checkpointPlanFor("v1", "v2");
        Set<TaskLocation> checkpointLocations = locationsFor("v1", "v2");

        // Reverse check passes (no checkpoint-only vertices).
        assertDoesNotThrow(() ->
                GraphModelCheckpointExecutor.validateReverseVertexDifferential(
                        execPlan, checkpointPlan, checkpointLocations));
    }

    // ==================== helpers ====================

    private static GraphExecutionPlan planForVertices(String... vertexIds) {
        List<String> sorted = Arrays.asList(vertexIds);
        Map<String, io.nop.stream.core.jobgraph.JobVertex> executionVertices = Collections.emptyMap();
        Map<String, List<Subtask>> subtasks = new LinkedHashMap<>();
        for (String v : vertexIds) {
            Subtask s = new Subtask(v, 0, new TaskLocation(JOB, PIPELINE, v, 0), null);
            subtasks.put(v, Collections.singletonList(s));
        }
        Map<String, io.nop.stream.core.execution.StreamTaskInvokable> invokables = Collections.emptyMap();
        return GraphExecutionPlan.create(sorted, executionVertices, invokables, subtasks);
    }

    private static CheckpointPlan checkpointPlanFor(String... vertexIds) {
        // allTasks / stateMappings keys must line up with the TaskLocations
        // that findTaskLocationInPlan resolves for the production restore path.
        // For validateReverseVertexDifferential itself the checkpointPlan is
        // not consumed (only execPlan + checkpointLocations are); for the
        // forward-restore test we mirror the production contract here.
        java.util.List<TaskLocation> all = new java.util.ArrayList<>();
        Map<TaskLocation, java.util.List<io.nop.stream.core.checkpoint.OperatorStateMapping>> mappings =
                new LinkedHashMap<>();
        for (String v : vertexIds) {
            TaskLocation loc = new TaskLocation(JOB, PIPELINE, v, 0);
            all.add(loc);
            mappings.put(loc, Collections.emptyList());
        }
        return new CheckpointPlan(JOB, PIPELINE, all, all, mappings);
    }

    private static Set<TaskLocation> locationsFor(String... vertexIds) {
        Set<TaskLocation> set = new HashSet<>();
        for (String v : vertexIds) {
            set.add(new TaskLocation(JOB, PIPELINE, v, 0));
        }
        return set;
    }
}
