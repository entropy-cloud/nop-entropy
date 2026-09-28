/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A1 regression (plan 01 quality-perf Phase 2): a recovery that fails inside
 * the locked section (here: {@code prepareAssignmentsLocked} throwing
 * "No RPC service for node X") or exits early through a budget cap must still
 * clear the {@code recoveryPending} dedup flag.
 *
 * <p>Pre-fix, the flag was only cleared in the fan-out block's finally — an
 * exception or early return left it armed forever, so every subsequent
 * {@code requestRecovery()} CAS short-circuited and every checkpoint trigger
 * was suppressed: the job was permanently wedged (no retries, no checkpoints).
 */
class TestJobCoordinatorRecoveryPendingCleanup {

    private static final String JOB_ID = "recovery-pending-job";

    @TempDir
    Path tempDir;

    private JobCoordinator coordinator;

    @BeforeEach
    void setUp() {
        // node-1 has an RPC service but capacity for only ONE slot; node-2 has
        // capacity but NO RPC service. The two-vertex plan therefore requires
        // an assignment on node-2 and prepareAssignmentsLocked throws
        // "No RPC service for node node-2" inside the recovery lock.
        InMemoryClusterRegistry clusterRegistry = new InMemoryClusterRegistry();
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true).checkpointInterval(1000L)
                .checkpointTimeout(10000L).maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3).build();
        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", idCounter, storage, config);

        clusterRegistry.registerNode("node-1", "localhost:9080", 1);
        clusterRegistry.registerNode("node-2", "localhost:9081", 4);

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edges = new ArrayList<>();
        edges.add(new PartitionedPlan.EdgePlan("source", "sink",
                io.nop.stream.core.execution.plan.PartitionPolicy.FORWARD));

        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, "pipeline-0", vertexPlans, edges, null, null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);

        coordinator = new JobCoordinator(
                JOB_ID, "coord-1", deploymentPlan,
                clusterRegistry, checkpointCoordinator,
                Collections.singletonMap("node-1", new LocalNoopTaskRpc()));
        coordinator.setMaxRestarts(5);
    }

    @AfterEach
    void tearDown() {
        coordinator.stop();
    }

    /**
     * The first recovery fails inside the locked section and propagates the
     * error; the flag must be cleared so a SECOND requestRecovery actually
     * re-runs the recovery (pre-fix: the CAS short-circuits forever and the
     * restart counter stays at 1).
     *
     * <p>The budget-cap early-return path exits through the same outer
     * finally; its post-state (job FAILED) legitimately blocks re-entry via
     * the health state machine, so it has no separately observable wedged-flag
     * behavior to pin here.
     */
    @Test
    void recoveryIsReRunnableAfterLockedSectionFailure() {
        coordinator.start();

        StreamException failure = assertThrows(StreamException.class,
                () -> coordinator.requestRecovery(),
                "assignment without an RPC service must fail the recovery");
        assertTrue(String.valueOf(failure.getMessage()).contains("No RPC service"),
                "expected the 'No RPC service' assignment failure, got: " + failure.getMessage());
        assertEquals(1, coordinator.getRestartCount(), "first attempt consumed the budget");

        // Pre-fix this second request observed recoveryPending=true forever and
        // short-circuited SILENTLY (no exception, counter frozen). Post-fix the
        // flag is cleared, so the request re-enters globalRecovery — which throws
        // from the health state machine (the failed first attempt left health in
        // RECOVERING; RECOVERING→RECOVERING is rejected). The exception itself is
        // the observable proof of re-entry: the short-circuit path cannot throw.
        assertThrows(StreamException.class,
                () -> coordinator.requestRecovery(),
                "second request must re-enter globalRecovery (flag cleared), not "
                        + "short-circuit on a wedged CAS");
    }

    /** Minimal no-op task RPC stub (same shape as the sibling coordinator tests). */
    static class LocalNoopTaskRpc implements io.nop.stream.runtime.rpc.IStreamTaskRpcService {
        @Override
        public void receiveAssignment(io.nop.stream.runtime.cluster.TaskAssignment a) {
        }

        @Override
        public void triggerCheckpoint(io.nop.stream.core.checkpoint.CheckpointBarrier b, long fencingEpoch) {
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex, long fencingEpoch) {
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
        }
    }
}
