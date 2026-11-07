/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.test.time.ManualClock;
import io.nop.stream.runtime.protocol.TaskProgress;
import io.nop.stream.runtime.protocol.JobStatus;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
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
 *
 * <p>时间确定化（plan 2510）：InMemoryClusterRegistry 与 JobCoordinator 的活性/租约
 * 判定读取均经 {@code CoreMetrics}（JobCoordinator 另有包内可注入 clock），测试注册
 * {@link ManualClock} 显式推进虚拟时间越过租约到期点；liveness 戳一律取自虚拟时钟线
 * （时间戳陷阱）。时钟为 JVM 全局量：逐测试注册、{@code @AfterEach} 恢复默认时钟。
 */
class TestJobCoordinatorStallRecoveryBudget {

    private static final String JOB_ID = "stall-budget-job";

    @TempDir
    Path tempDir;

    private InMemoryClusterRegistry clusterRegistry;
    private JobCoordinator coordinator;
    private CoordinatorTestSupport.RecordingTaskRpcService taskRpc;
    private ManualClock clock;

    @BeforeEach
    void setUp() {
        clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
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

        taskRpc = new CoordinatorTestSupport.RecordingTaskRpcService();
        coordinator = new JobCoordinator(
                JOB_ID, "coord-1", deploymentPlan,
                clusterRegistry, checkpointCoordinator,
                Collections.singletonMap("node-1", taskRpc));
        // detectFailures 的 now 与 registry 的租约判定共用同一条虚拟时间线。
        coordinator.setClock(clock::peekMillis);
        coordinator.setTerminationCheckpointTimeoutMs(500L);
        // Deterministic tests: disable the stall cooldown unless a test sets it.
        coordinator.setStallRecoveryCooldownMs(0L);
    }

    @AfterEach
    void tearDown() {
        coordinator.stop();
        // 时钟是 JVM 全局量：逐测试恢复默认时钟，避免冻结同 fork 后续所有类。
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
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
        // liveness 戳取自虚拟时钟线（与 detectFailures 注入的同一时钟）。
        long stale = clock.peekMillis() - 120_000L;
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
                shortLeaseRegistry, cc, Collections.singletonMap("node-1", new CoordinatorTestSupport.RecordingTaskRpcService()));
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
            // liveness 戳取自虚拟时钟线（与 detectFailures / registry 判定同一条时间线）。
            coordinatorB.reportNodeTaskLiveness("node-1", Collections.singletonList(
                    new TaskProgress("source", 0, 1, clock.peekMillis() - 120_000L)));

            // 推进虚拟时间越过租约到期点（renewLease 500ms → expireAt = 虚拟 now + 500）：
            // getActiveNodes 的租约判定读取 CoreMetrics，推进后 node-1 立即失活 ——
            // 替代旧的固定 600ms 墙钟等待。
            clock.advance(600L);
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

}
