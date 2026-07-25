/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G56/G58 Phase 3 verification:
 * <ul>
 *   <li>Global restart strategy: cap exceeded → {@link JobCoordinator#failJob(Throwable)}</li>
 *   <li>{@link JobStatus} transitions: CREATED → RUNNING; FAIL → FAILED; FAILED rejects assignTasks</li>
 *   <li>#23 wiring: {@code globalRecovery()} increments the counter; cap blocks recovery</li>
 *   <li>#22 end-to-end: multiple failures → cap exceeded → job FAILED</li>
 *   <li>G58 cancel normalization: Task.cancel() on RUNNING works (covered in
 *       TestTaskStateTransition and TestTaskLifecycle for the underlying state
 *       machine; this class focuses on the restart-strategy side)</li>
 * </ul>
 */
class TestJobCoordinatorRestartStrategy {

    private static final String JOB_ID = "restart-job-1";

    @TempDir
    Path tempDir;

    private InMemoryClusterRegistry clusterRegistry;
    private CheckpointCoordinator checkpointCoordinator;
    private DeploymentPlan deploymentPlan;
    private JobCoordinator coordinator;

    @BeforeEach
    void setUp() {
        clusterRegistry = new InMemoryClusterRegistry();
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true).checkpointInterval(1000L)
                .checkpointTimeout(10000L).maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3).build();
        checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", idCounter, storage, config);

        clusterRegistry.registerNode("node-1", "localhost:9080", 4);

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edges = new ArrayList<>();
        edges.add(new PartitionedPlan.EdgePlan("source", "sink",
                io.nop.stream.core.execution.plan.PartitionPolicy.FORWARD));

        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, "pipeline-0", vertexPlans, edges, null, null);
        deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);

        coordinator = new JobCoordinator(
                JOB_ID, "coord-1", deploymentPlan,
                clusterRegistry, checkpointCoordinator,
                Collections.singletonMap("node-1", new NoopTaskRpc()));
        coordinator.setTerminationCheckpointTimeoutMs(500L);
    }

    @AfterEach
    void tearDown() {
        coordinator.stop();
    }

    @Test
    void jobStatusStartsAsCreatedThenRunning() {
        assertEquals(JobStatus.CREATED, coordinator.getJobStatus());
        coordinator.start();
        assertEquals(JobStatus.RUNNING, coordinator.getJobStatus());
    }

    @Test
    void restartCounterIncrementsPerGlobalRecovery() {
        coordinator.start();
        coordinator.assignTasks();
        assertEquals(0, coordinator.getRestartCount());

        coordinator.globalRecovery();
        assertEquals(1, coordinator.getRestartCount());

        coordinator.globalRecovery();
        assertEquals(2, coordinator.getRestartCount());
    }

    @Test
    void reachingMaxRestartsFailsJob() {
        coordinator.setMaxRestarts(2);
        coordinator.start();
        coordinator.assignTasks();

        assertEquals(0, coordinator.getRestartCount());
        assertEquals(JobStatus.RUNNING, coordinator.getJobStatus());

        coordinator.globalRecovery();
        assertEquals(1, coordinator.getRestartCount());
        assertEquals(JobStatus.RUNNING, coordinator.getJobStatus(),
                "1st recovery (count=1, cap=2) should keep job RUNNING");

        coordinator.globalRecovery();
        assertEquals(2, coordinator.getRestartCount());
        assertEquals(JobStatus.RUNNING, coordinator.getJobStatus(),
                "2nd recovery (count=2, cap=2) should keep job RUNNING");

        // 3rd recovery attempt: counter increments to 3 (3 > cap=2) → failJob.
        // The counter reflects "recovery attempts", so it is 3 (not 2) after failJob.
        coordinator.globalRecovery();
        assertEquals(3, coordinator.getRestartCount(),
                "counter increments before the cap check; failJob fires when count > cap");
        assertEquals(JobStatus.FAILED, coordinator.getJobStatus(),
                "3rd recovery attempt (count > cap=2) must call failJob");
        assertNotNull(coordinator.getJobFailureCause(),
                "failJob must capture a cause");
    }

    @Test
    void failedJobRejectsFurtherAssignTasks() {
        coordinator.setMaxRestarts(0);
        coordinator.start();
        coordinator.assignTasks();
        int assignmentsBefore = coordinator.getTaskAssignments().size();

        // maxRestarts=0 → first globalRecovery immediately fails the job
        coordinator.globalRecovery();
        assertEquals(JobStatus.FAILED, coordinator.getJobStatus());

        coordinator.assignTasks();
        int assignmentsAfter = coordinator.getTaskAssignments().size();
        assertEquals(assignmentsBefore, assignmentsAfter,
                "FAILED job must reject new assignTasks (no new tasks added)");
    }

    @Test
    void failJobIsIdempotent() {
        coordinator.start();
        coordinator.failJob(new RuntimeException("first"));
        assertEquals(JobStatus.FAILED, coordinator.getJobStatus());
        Throwable cause1 = coordinator.getJobFailureCause();

        coordinator.failJob(new RuntimeException("second"));
        assertEquals(JobStatus.FAILED, coordinator.getJobStatus());
        assertSame(cause1, coordinator.getJobFailureCause(),
                "Second failJob must not overwrite the original cause (idempotent)");
    }

    @Test
    void zeroMaxRestartsImmediatelyFailsOnFirstRecovery() {
        coordinator.setMaxRestarts(0);
        coordinator.start();
        coordinator.assignTasks();

        coordinator.globalRecovery();
        assertEquals(JobStatus.FAILED, coordinator.getJobStatus());
        assertEquals(1, coordinator.getRestartCount(),
                "with maxRestarts=0, the first recovery increments the counter to 1 "
                        + "and immediately fails (1 > 0 cap)");
    }

    @Test
    void multipleFailuresViaReportTaskStatusEventuallyFailJob() {
        // End-to-end: per-task FAILED reports (auto-recovery enabled) drive the
        // restart counter; cap exceeded → job FAILED.
        coordinator.setMaxRestarts(2);
        coordinator.start();
        coordinator.assignTasks();

        String token = coordinator.getFencingToken();

        // First FAILED report triggers recovery #1 (count=1, still RUNNING)
        coordinator.reportTaskStatus(new TaskStatusReport(
                JOB_ID, "source", 0, 1,
                TaskStatusReport.TerminalState.FAILED, "fail-1",
                System.currentTimeMillis(), token, System.currentTimeMillis()));
        assertEquals(JobStatus.RUNNING, coordinator.getJobStatus());
        token = coordinator.getFencingToken(); // token changed by recovery

        // Second FAILED → recovery #2 (count=2, still RUNNING)
        coordinator.reportTaskStatus(new TaskStatusReport(
                JOB_ID, "source", 0, 2,
                TaskStatusReport.TerminalState.FAILED, "fail-2",
                System.currentTimeMillis(), token, System.currentTimeMillis()));
        assertEquals(JobStatus.RUNNING, coordinator.getJobStatus());
        token = coordinator.getFencingToken();

        // Third FAILED → recovery #3 attempt → cap exceeded → failJob
        coordinator.reportTaskStatus(new TaskStatusReport(
                JOB_ID, "source", 0, 3,
                TaskStatusReport.TerminalState.FAILED, "fail-3",
                System.currentTimeMillis(), token, System.currentTimeMillis()));
        assertEquals(JobStatus.FAILED, coordinator.getJobStatus(),
                "after 3 FAILED reports with cap=2, job must be FAILED");
    }

    static class NoopTaskRpc implements io.nop.stream.runtime.rpc.IStreamTaskRpcService {
        final AtomicReference<String> lastToken = new AtomicReference<>();
        final CopyOnWriteArrayList<TaskAssignment> assignments = new CopyOnWriteArrayList<>();

        @Override public void receiveAssignment(TaskAssignment a) { assignments.add(a); }
        @Override public void triggerCheckpoint(io.nop.stream.core.checkpoint.CheckpointBarrier b, String t) {}
        @Override public void cancelTask(String j, String v, int s) {}
        @Override public void updateFencingToken(String n) { lastToken.set(n); }
    }
}
