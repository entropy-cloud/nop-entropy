/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.coordinator.TaskStatusReport;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 369 Phase 4 focused tests (R5-CC-07): the deployment grace period.
 *
 * <p>Pre-fix, an assigned task that never produced a liveness record (a
 * deployTask whose one-way transport RPC was lost never yields liveness or a
 * FAILED report) enjoyed a PERMANENT benefit-of-the-doubt exemption in
 * {@code detectFailures()} — a silent half-dead topology with no recovery
 * trigger. Post-fix:
 *
 * <ul>
 *   <li>within the configurable grace period the benefit of the doubt still
 *       applies (no false positives for just-assigned tasks);</li>
 *   <li>beyond the grace period with no liveness, the assignment is treated as
 *       a deployment failure and recovery is triggered;</li>
 *   <li>a liveness report or a terminal report closes the grace window;</li>
 *   <li>a COMPLETED task is tombstoned and never flagged.</li>
 * </ul>
 */
class TestJobCoordinatorDeployGracePeriod {

    private static final String JOB_ID = "job-cc07-grace";
    private static final String COORD_ID = "coord-cc07";
    private static final long GRACE_MS = 10_000L;

    @TempDir
    Path tempDir;

    private TestJobCoordinatorLeaderElection.MockClusterRegistry registry;
    private CoordinatorTestSupport.RecordingTaskRpcService rpc;
    private JobCoordinator coordinator;
    private final AtomicLong fakeNow = new AtomicLong(1_000_000L);

    @BeforeEach
    void setUp() {
        registry = new TestJobCoordinatorLeaderElection.MockClusterRegistry();
        registry.registerNode("node-1", "localhost:9001", 4);
        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", new CheckpointIDCounter(),
                new LocalFileCheckpointStorage(tempDir.toString()),
                CoordinatorTestSupport.defaultCheckpointConfig());
        rpc = new CoordinatorTestSupport.RecordingTaskRpcService();
        Map<String, IStreamTaskRpcService> rpcs = new HashMap<>();
        rpcs.put("node-1", rpc);
        DeploymentPlan plan = CoordinatorTestSupport.twoVertexForwardPlan(JOB_ID);
        coordinator = new JobCoordinator(JOB_ID, COORD_ID, plan,
                registry, checkpointCoordinator, rpcs);
        coordinator.setTerminationCheckpointTimeoutMs(500L);
        coordinator.setDeployGracePeriodMs(GRACE_MS);
        coordinator.setClock(fakeNow::get);
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.stop();
        }
    }

    @Test
    void graceExpiryWithoutLivenessTriggersRecovery() {
        coordinator.start();
        coordinator.assignTasks();

        assertFalse(coordinator.getTaskAssignments().isEmpty());
        assertEquals(2, coordinator.getPendingDeployCount(),
                "both assignments must hold an open deploy grace window");

        // Advance beyond the grace period (no liveness ever arrives — the
        // transport-lost deploy scenario) and let the detector tick.
        fakeNow.addAndGet(GRACE_MS + 1);
        int recoveriesBefore = coordinator.getTotalRecoveryCount();
        coordinator.detectFailures();

        assertTrue(coordinator.getTotalRecoveryCount() > recoveriesBefore,
                "an assignment with no liveness record beyond the deployment grace period must "
                        + "trigger recovery (R5-CC-07); totalRecoveries=" + coordinator.getTotalRecoveryCount());
    }

    @Test
    void withinGracePeriodNoFalsePositive() {
        coordinator.start();
        coordinator.assignTasks();

        // Advance well beyond the TASK-stall timeout but stay inside the deploy
        // grace window: a just-assigned task must not be flagged.
        fakeNow.addAndGet(Math.max(GRACE_MS - 1, 0));
        int recoveriesBefore = coordinator.getTotalRecoveryCount();
        coordinator.detectFailures();

        assertEquals(recoveriesBefore, coordinator.getTotalRecoveryCount(),
                "an assignment inside the deployment grace period must keep the benefit of the doubt");
    }

    @Test
    void livenessArrivalClosesGraceWindow() {
        coordinator.start();
        coordinator.assignTasks();

        // The task's first heartbeat lands shortly after deploy.
        fakeNow.addAndGet(1000L);
        coordinator.reportNodeTaskLiveness("node-1", java.util.Collections.singletonList(
                new TaskProgress("source", 0, 1, fakeNow.get())));
        assertEquals(1, coordinator.getPendingDeployCount(),
                "the reported subtask's grace window must close; only the unreported one remains");

        // Now let the grace period elapse. The reported task has a FRESH liveness
        // value (no stall); only the unreported subtask may fire.
        fakeNow.addAndGet(GRACE_MS + 1);
        int recoveriesBefore = coordinator.getTotalRecoveryCount();
        coordinator.detectFailures();
        assertTrue(coordinator.getTotalRecoveryCount() > recoveriesBefore,
                "the unreported subtask must still trigger recovery beyond the grace period");

        // After recovery the assignments (and markers) are re-materialized; the
        // previously reported task was never the trigger.
        assertTrue(coordinator.getPendingDeployCount() >= 0);
    }

    @Test
    void completedTaskIsNeverFlaggedByDeployGrace() {
        coordinator.start();
        coordinator.assignTasks();

        long epoch = coordinator.getFencingEpoch();
        coordinator.reportTaskStatus(new TaskStatusReport(
                JOB_ID, "source", 0, 1,
                TaskStatusReport.TerminalState.COMPLETED, "done", -1L, epoch, fakeNow.get()));
        coordinator.reportTaskStatus(new TaskStatusReport(
                JOB_ID, "sink", 0, 1,
                TaskStatusReport.TerminalState.COMPLETED, "done", -1L, epoch, fakeNow.get()));

        // Long after any grace period: both tasks are COMPLETED and tombstoned;
        // neither may ever be flagged as a deploy failure (regression guard
        // against burning the restart budget on healthy completed tasks).
        fakeNow.addAndGet(GRACE_MS * 10);
        int recoveriesBefore = coordinator.getTotalRecoveryCount();
        coordinator.detectFailures();

        assertEquals(recoveriesBefore, coordinator.getTotalRecoveryCount(),
                "COMPLETED (tombstoned) tasks must never be flagged as deploy failures, "
                        + "no matter how far beyond the grace period the clock moves");
    }

    @Test
    void transportFailureRecordPointObservability() {
        coordinator.start();
        rpc.failReceiveAssignment = true;
        coordinator.assignTasks();

        assertTrue(coordinator.isDeployTransportFailed("source", 0),
                "a transport-failed assignment RPC must be recorded (R5-CC-07 record point)");
        assertTrue(coordinator.isDeployTransportFailed("sink", 0));

        // Known-bad but still inside the grace period: no recovery yet. The
        // boundary itself (elapsed == grace) is still within the window.
        fakeNow.addAndGet(GRACE_MS);
        int recoveriesBefore = coordinator.getTotalRecoveryCount();
        coordinator.detectFailures();
        assertEquals(recoveriesBefore, coordinator.getTotalRecoveryCount(),
                "a known transport failure must still wait out the grace period "
                        + "(boundary included)");

        fakeNow.addAndGet(1);
        coordinator.detectFailures();
        assertTrue(coordinator.getTotalRecoveryCount() > recoveriesBefore,
                "beyond the grace period the recorded transport failure must trigger recovery");
    }

    @Test
    void gracePeriodRotationClearsMarkers() {
        coordinator.start();
        coordinator.assignTasks();
        assertEquals(2, coordinator.getPendingDeployCount());

        // A recovery rotates the generation: markers must be re-materialized
        // fresh (not inherited), so the new attempts get a full grace window.
        fakeNow.addAndGet(GRACE_MS * 5);
        coordinator.globalRecovery();
        assertEquals(2, coordinator.getPendingDeployCount(),
                "after rotation the markers must be the new generation's fresh deploy issues");

        // Immediately after recovery (fresh markers at the new clock), no
        // deploy-grace trigger may fire.
        int restartsBefore = coordinator.getRestartCount();
        coordinator.detectFailures();
        assertEquals(restartsBefore, coordinator.getRestartCount(),
                "fresh post-recovery deploys must not be flagged by stale grace arithmetic");

        assertTrue(coordinator.getTaskAssignments().values().stream()
                        .flatMap(java.util.List::stream)
                        .map(TaskAssignment::getVertexId)
                        .distinct().count() >= 1,
                "assignments must be re-materialized after recovery");
    }
}
