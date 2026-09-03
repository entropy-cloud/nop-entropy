/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;

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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Items 28+31 (D3, Phase 3 focused tests): stall-recovery budget vs
 * real-failure recovery budget.
 *
 * <p>Exercise evidence (stability drill CHAOS-1, runId 1788383926464-1): a
 * data-plane jam aged every task's liveness → the failure detector fired
 * taskStall global recoveries ×3 → the SHARED cap (maxRestarts=3) was
 * exhausted → subsequent REAL node kills and lease expiries could never
 * recover. These tests pin the D3 mechanism:
 * <ul>
 *   <li>stall-triggered recoveries draw from a SEPARATE budget — the
 *       real-failure budget is untouched;</li>
 *   <li>after a full stall-recovery storm, a real-failure recovery still
 *       proceeds (the CHAOS-1 anchor at coordinator level);</li>
 *   <li>cooldown window skips rapid stall requests (observable, no
 *       budget burn);</li>
 *   <li>stall cap exceeded → job FAILED (bounded retries) with the
 *       real-failure budget still intact;</li>
 *   <li>the REAL failure detector classifies node-lease expiry vs pure
 *       liveness stall into the right budgets (integration through
 *       detectFailures, not just the direct entry points).</li>
 * </ul>
 */
class TestJobCoordinatorStallRecoveryBudget {

    private static final String JOB_ID = "stall-budget-job";

    @TempDir
    Path tempDir;

    private InMemoryClusterRegistry clusterRegistry;
    private JobCoordinator coordinator;
    private NoopTaskRpc taskRpc;

    @BeforeEach
    void setUp() {
        clusterRegistry = new InMemoryClusterRegistry();
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true).checkpointInterval(1000L)
                .checkpointTimeout(10000L).maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3).build();
        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", new CheckpointIDCounter(), storage, config);

        clusterRegistry.registerNode("node-1", "localhost:9080", 4);

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edges = new ArrayList<>();
        edges.add(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD));

        PartitionedPlan partitionedPlan = new PartitionedPlan(JOB_ID, "pipeline-0", vertexPlans, edges, null, null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan, "local", "memory", "local", null, null);

        taskRpc = new NoopTaskRpc();
        coordinator = new JobCoordinator(
                JOB_ID, "coord-1", deploymentPlan,
                clusterRegistry, checkpointCoordinator,
                Collections.singletonMap("node-1", taskRpc));
        coordinator.setTerminationCheckpointTimeoutMs(500L);
        // Deterministic tests: disable the stall cooldown unless a test sets it.
        coordinator.setStallRecoveryCooldownMs(0L);
    }

    @AfterEach
    void tearDown() {
        coordinator.stop();
    }

    @Test
    void stallRecoveriesDrawFromSeparateBudgetAndNeverConsumeRealFailureBudget() {
        coordinator.start();
        coordinator.assignTasks();
        coordinator.setMaxRestarts(3);
        coordinator.setMaxStallRestarts(3);

        for (int i = 0; i < 3; i++) {
            coordinator.requestRecovery(JobCoordinator.RecoveryCause.TASK_STALL);
        }
        assertEquals(3, coordinator.getStallRestartCount(),
                "three stall recoveries must draw from the stall budget");
        assertEquals(0, coordinator.getRestartCount(),
                "stall recoveries must NEVER consume the real-failure budget");
        assertEquals(JobStatus.RUNNING, coordinator.getJobStatus().getJobStatus(),
                "stall budget not exceeded → job stays RUNNING");
        assertEquals(3, coordinator.getTotalRecoveryCount(),
                "total recovery count spans both pools");

        // CHAOS-1 anchor (coordinator level): after a full stall storm, a REAL
        // failure recovery still proceeds within the real budget.
        coordinator.globalRecovery();
        assertEquals(1, coordinator.getRestartCount(), "real-failure recovery must proceed after stall storms");
        assertEquals(JobStatus.RUNNING, coordinator.getJobStatus().getJobStatus(),
                "real-failure recovery capacity survived the stall storm");
    }

    @Test
    void cooldownWindowSkipsRapidStallRequests() {
        coordinator.start();
        coordinator.assignTasks();
        coordinator.setMaxStallRestarts(3);
        coordinator.setStallRecoveryCooldownMs(300_000L);

        long epochBefore = coordinator.getFencingEpoch();
        coordinator.requestRecovery(JobCoordinator.RecoveryCause.TASK_STALL);
        assertEquals(1, coordinator.getStallRestartCount());
        long epochAfterFirst = coordinator.getFencingEpoch();
        assertTrue(epochAfterFirst > epochBefore, "the first stall recovery must rotate fencing");

        // A rapid second stall request inside the cooldown is SKIPPED —
        // observable, no budget burn, no fencing rotation.
        coordinator.requestRecovery(JobCoordinator.RecoveryCause.TASK_STALL);
        assertEquals(1, coordinator.getStallRestartCount(),
                "a stall request inside the cooldown window must be skipped (no budget burn)");
        assertEquals(epochAfterFirst, coordinator.getFencingEpoch(),
                "a skipped stall request must not rotate fencing");
        assertEquals(JobStatus.RUNNING, coordinator.getJobStatus().getJobStatus());
    }

    @Test
    void stallCapExceededFailsJobWhileRealBudgetStaysIntact() {
        coordinator.start();
        coordinator.assignTasks();
        coordinator.setMaxStallRestarts(1);

        coordinator.requestRecovery(JobCoordinator.RecoveryCause.TASK_STALL);
        assertEquals(JobStatus.RUNNING, coordinator.getJobStatus().getJobStatus());

        coordinator.requestRecovery(JobCoordinator.RecoveryCause.TASK_STALL);
        assertEquals(2, coordinator.getStallRestartCount(),
                "counter increments before the stall cap check (attempt semantics)");
        assertEquals(JobStatus.FAILED, coordinator.getJobStatus().getJobStatus(),
                "persistent stall must fail the job after bounded retries (stall cap)");
        assertEquals(0, coordinator.getRestartCount(),
                "the real-failure budget must remain untouched even when the stall cap fires");
    }

    @Test
    void detectFailuresClassifiesPureStallIntoStallBudget() {
        coordinator.start();
        coordinator.assignTasks();

        // Node alive, one task's recorded liveness is stale (> taskTimeoutMs=60s).
        long stale = System.currentTimeMillis() - 120_000L;
        coordinator.reportNodeTaskLiveness("node-1", Collections.singletonList(
                new TaskProgress("source", 0, 1, stale)));

        coordinator.detectFailures();

        assertEquals(1, coordinator.getStallRestartCount(),
                "a pure liveness stall detected by the failure detector must draw from the stall budget");
        assertEquals(0, coordinator.getRestartCount(),
                "a pure stall must NOT consume the real-failure budget");
        assertEquals(JobStatus.RUNNING, coordinator.getJobStatus().getJobStatus());
    }

    @Test
    void detectFailuresClassifiesNodeLeaseExpiryIntoRealBudget() throws Exception {
        // Registry with a short-but-assignable lease TTL: assignments complete
        // while the lease is alive, then the lease expires before the detector
        // runs, so detectFailures sees a REAL node failure.
        InMemoryClusterRegistry shortLeaseRegistry = new InMemoryClusterRegistry(200L);
        shortLeaseRegistry.registerNode("node-1", "localhost:9080", 4);
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.resolve("b").toString());
        CheckpointCoordinator cc = new CheckpointCoordinator(
                JOB_ID + "-b", "pipeline-0", new CheckpointIDCounter(), storage,
                CheckpointConfig.builder().checkpointEnabled(true).checkpointInterval(1000L)
                        .checkpointTimeout(10000L).maxConcurrentCheckpoints(1).maxRetainedCheckpoints(3).build());
        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        PartitionedPlan partitionedPlan = new PartitionedPlan(JOB_ID + "-b", "pipeline-0",
                vertexPlans, new ArrayList<>(), null, null);
        JobCoordinator coordinatorB = new JobCoordinator(
                JOB_ID + "-b", "coord-b",
                new DeploymentPlan(JOB_ID + "-b", "pipeline-0", partitionedPlan,
                        "local", "memory", "local", null, null),
                shortLeaseRegistry, cc, Collections.singletonMap("node-1", new NoopTaskRpc()));
        coordinatorB.setTerminationCheckpointTimeoutMs(500L);
        coordinatorB.setStallRecoveryCooldownMs(0L);
        try {
            coordinatorB.start();
            // Renew so the assignment pass definitely sees a live node.
            shortLeaseRegistry.renewLease("node-1", 500L);
            coordinatorB.assignTasks();
            assertEquals(1, coordinatorB.getTaskAssignments().size(),
                    "precondition: the source subtask must be assigned to node-1");
            // Also age the liveness so BOTH signals are present — node loss must win.
            coordinatorB.reportNodeTaskLiveness("node-1", Collections.singletonList(
                    new TaskProgress("source", 0, 1, System.currentTimeMillis() - 120_000L)));

            Thread.sleep(600L); // let the lease expire
            coordinatorB.detectFailures();

            assertEquals(1, coordinatorB.getRestartCount(),
                    "node-lease expiry must draw from the REAL-failure budget");
            assertEquals(0, coordinatorB.getStallRestartCount(),
                    "node-lease expiry must not touch the stall budget (classification: node loss dominates)");
            assertEquals(JobStatus.RUNNING, coordinatorB.getJobStatus().getJobStatus());
        } finally {
            coordinatorB.stop();
        }
    }

    static class NoopTaskRpc implements io.nop.stream.runtime.rpc.IStreamTaskRpcService {
        final java.util.concurrent.atomic.AtomicLong lastEpoch = new java.util.concurrent.atomic.AtomicLong();
        final CopyOnWriteArrayList<TaskAssignment> assignments = new CopyOnWriteArrayList<>();

        @Override
        public void receiveAssignment(TaskAssignment a) {
            assignments.add(a);
        }

        @Override
        public void triggerCheckpoint(io.nop.stream.core.checkpoint.CheckpointBarrier b, long fencingEpoch) {
        }

        @Override
        public void cancelTask(String j, String v, int s, long fencingEpoch) {
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
            lastEpoch.set(fencingEpoch);
        }
    }
}
