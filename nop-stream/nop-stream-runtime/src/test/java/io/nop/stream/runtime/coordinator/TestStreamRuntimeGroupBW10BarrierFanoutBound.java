/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA Phase 8.41 R4-01 (plan 2534) 1.1 unit, Group B — E2 ubiquitous pin for
 * W-10 (C4-005): the per-node barrier fan-out executor has a BOUNDED wait
 * queue (capacity 100) with a drop-on-saturation handler — a hung node send can
 * no longer grow the queue without limit, the triggering thread is never
 * blocked by the saturated node, and every drop is counted on
 * {@code nop.stream.engine.barrierFanoutDrops.total}.
 *
 * <p>Deterministic driver (S6 seam = hanging IStreamTaskRpcService send, the
 * TestCheckpointBarrierFanoutAsync stub shape): node tm-0's
 * {@code triggerCheckpoint} parks on a latch (the fan-out thread is pinned
 * inside the first send); 105 barrier triggers follow. Pre-fix
 * ({@code newSingleThreadExecutor}, unbounded queue) every task enqueues and
 * nothing is ever dropped — the drop counter stays 0 (deterministic red: the
 * counter meter does not even exist pre-fix and the bound assertion fails).
 * Post-fix: task 1 executes (parked), tasks 2..101 fill the capacity-100 queue,
 * triggers 102..105 are dropped and counted — exactly 4 drops, the trigger
 * thread returns promptly each time, and the queue stays bounded.
 */
class TestStreamRuntimeGroupBW10BarrierFanoutBound {

    private static final String JOB_ID = "gb-w10-job";
    private static final String PIPELINE_ID = "pipeline-0";
    private static final String METRIC_BARRIER_FANOUT_DROPS = "nop.stream.engine.barrierFanoutDrops.total";
    private static final int QUEUE_CAPACITY = 100;
    private static final int TRIGGERS = QUEUE_CAPACITY + 5;

    @TempDir
    Path tempDir;

    private JobCoordinator coordinator;
    private CountDownLatch blockSend = new CountDownLatch(1);

    @AfterEach
    void tearDown() {
        blockSend.countDown();
        if (coordinator != null) {
            try {
                coordinator.stop();
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    @Test
    void saturatedFanoutQueueDropsAndCountsWhileTriggerThreadStaysFree() throws Exception {
        HungSendRpcService rpc0 = new HungSendRpcService(blockSend);

        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, PIPELINE_ID, new CheckpointIDCounter(),
                new LocalFileCheckpointStorage(tempDir.toString()),
                CheckpointConfig.builder().checkpointEnabled(true)
                        .checkpointInterval(1000L).checkpointTimeout(120_000L)
                        .minPause(0L)
                        .maxConcurrentCheckpoints(1000).build());

        TestLeaderElector elector = new TestLeaderElector("host-gb-w10");
        coordinator = buildCoordinator(checkpointCoordinator, elector, Map.of("tm-0", rpc0));
        coordinator.start();
        elector.grantLeadership(1L);

        // Task 1 occupies the single fan-out thread (parked inside the send)...
        coordinator.triggerCheckpoint();
        long deadline = System.currentTimeMillis() + 10_000L;
        while (rpc0.entered.get() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        assertEquals(1, rpc0.entered.get(), "the first barrier send must be executing (parked)");

        // ...triggers 2..N: each must return promptly (trigger thread never
        // blocked by the hung node — CC-06 semantics preserved).
        for (int i = 1; i < TRIGGERS; i++) {
            final int call = i;
            long start = System.currentTimeMillis();
            coordinator.triggerCheckpoint();
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed < 2000L,
                    () -> "trigger thread must not block on the saturated node (call " + call
                            + " took " + elapsed + "ms)");
        }

        // Drop accounting: 1 executing + 100 queued = 101 delivered-or-held;
        // the remaining TRIGGERS-101 barriers are DROPPED and counted. Pre-fix
        // (unbounded queue) the meter never moves: deterministic red.
        double drops = dropsCounter(JOB_ID);
        assertEquals(TRIGGERS - (QUEUE_CAPACITY + 1), drops,
                () -> "every barrier beyond the bounded queue must be dropped and counted on "
                        + METRIC_BARRIER_FANOUT_DROPS + ", got " + drops);

        // Release the hung send: the queued barriers deliver (no loss for the
        // ones that made it into the queue).
        blockSend.countDown();
        deadline = System.currentTimeMillis() + 20_000L;
        while (rpc0.delivered.get() < QUEUE_CAPACITY + 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }
        assertEquals(QUEUE_CAPACITY + 1, rpc0.delivered.get(),
                "the executing plus the queued barriers all deliver after the send unblocks");
    }

    // ==================== assembly (mirrors TestCheckpointBarrierFanoutAsync) ====================

    private JobCoordinator buildCoordinator(CheckpointCoordinator checkpointCoordinator,
                                            TestLeaderElector elector,
                                            Map<String, IStreamTaskRpcService> rpcs) {
        TestJobCoordinatorLeaderElection.MockClusterRegistry registry =
                new TestJobCoordinatorLeaderElection.MockClusterRegistry();
        registry.registerNode("tm-0", "localhost:9401", 4);
        JobCoordinator c = new JobCoordinator(
                JOB_ID, "coord-" + elector.getHostId(), buildDeploymentPlan(),
                registry, checkpointCoordinator, rpcs);
        c.setLeaderElector(elector);
        c.setTerminationCheckpointTimeoutMs(500L);
        return c;
    }

    private DeploymentPlan buildDeploymentPlan() {
        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = List.of(new PartitionedPlan.EdgePlan(
                "source", "sink", io.nop.stream.core.execution.plan.PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, PIPELINE_ID, vertexPlans, edgePlans, null, null);
        return new DeploymentPlan(JOB_ID, PIPELINE_ID, partitionedPlan,
                "local", "memory", "local", null, null);
    }

    private static double dropsCounter(String jobId) {
        return io.nop.stream.core.metrics.StreamMetricsRegistries.registry()
                .counter(METRIC_BARRIER_FANOUT_DROPS, "jobId", jobId).count();
    }

    // ==================== fixture RPC service (S6 seam) ====================

    /** Node whose barrier send parks until released — records entry and delivery. */
    static final class HungSendRpcService implements IStreamTaskRpcService {
        private final CountDownLatch block;
        final AtomicLong entered = new AtomicLong();
        final AtomicLong delivered = new AtomicLong();

        HungSendRpcService(CountDownLatch block) {
            this.block = block;
        }

        @Override
        public void triggerCheckpoint(io.nop.stream.core.checkpoint.CheckpointBarrier barrier,
                                      long fencingEpoch) {
            entered.incrementAndGet();
            try {
                block.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            delivered.incrementAndGet();
        }

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex, long fencingEpoch) {
        }
    }
}
