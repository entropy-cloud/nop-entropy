/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;

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
        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", new CheckpointIDCounter(), storage,
                CoordinatorTestSupport.defaultCheckpointConfig());

        clusterRegistry.registerNode("node-1", "localhost:9080", 1);
        clusterRegistry.registerNode("node-2", "localhost:9081", 4);

        coordinator = new JobCoordinator(
                JOB_ID, "coord-1", CoordinatorTestSupport.twoVertexForwardPlan(JOB_ID),
                clusterRegistry, checkpointCoordinator,
                Collections.singletonMap("node-1", new CoordinatorTestSupport.RecordingTaskRpcService()));
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

}
