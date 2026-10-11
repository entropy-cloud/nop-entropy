/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CC-06 (R5, plan 2435): the periodic trigger thread must not block on N×
 * per-node barrier RPC — one slow node must not stall later nodes' barriers
 * NOR the next periodic tick. Before the fix, {@code JobCoordinator#
 * triggerCheckpoint} sent every node's barrier synchronously on the
 * scheduler thread; a blocked send held the whole trigger loop.
 *
 * <p>Mirrors the TestCommitFanoutOffMonitor2326 shape (slow participant does
 * not block the monitor): node tm-0's {@code triggerCheckpoint} blocks on a
 * latch; two consecutive triggers must still deliver tm-1's barriers while
 * tm-0 is blocked; releasing the latch delivers both of tm-0's.
 */
class TestCheckpointBarrierFanoutAsync {

    private static final String JOB_ID = "barrier-fanout-async-job";
    private static final String PIPELINE_ID = "pipeline-0";

    @TempDir
    Path tempDir;

    private JobCoordinator coordinator;

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            try {
                coordinator.stop();
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    @Test
    void slowNodeDoesNotStallTriggerThreadOrOtherNodes() throws Exception {
        CountDownLatch blockNode0 = new CountDownLatch(1);
        BlockingRpcService rpc0 = new BlockingRpcService(blockNode0);
        RecordingFastRpcService rpc1 = new RecordingFastRpcService();

        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, PIPELINE_ID, new CheckpointIDCounter(),
                new LocalFileCheckpointStorage(tempDir.toString()),
                CheckpointConfig.builder().checkpointEnabled(true)
                        .checkpointInterval(1000L).checkpointTimeout(30_000L)
                        .maxConcurrentCheckpoints(5).build());

        TestLeaderElector elector = new TestLeaderElector("host-fanout");
        coordinator = buildCoordinator(checkpointCoordinator, elector,
                Map.of("tm-0", rpc0, "tm-1", rpc1));
        coordinator.start();
        elector.grantLeadership(1L);

        // Two triggers back-to-back while tm-0's FIRST send is still blocked.
        coordinator.triggerCheckpoint();
        long deadline = System.currentTimeMillis() + 10_000L;
        while (rpc1.triggers.get() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }
        assertTrue(rpc1.triggers.get() >= 1,
                "tm-1 must receive its barrier while tm-0 is still blocked (fan-out is off the trigger thread)");

        coordinator.triggerCheckpoint();
        deadline = System.currentTimeMillis() + 10_000L;
        while (rpc1.triggers.get() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }
        assertTrue(rpc1.triggers.get() >= 2,
                "the SECOND trigger must proceed (trigger thread not stalled by the blocked send)");

        // Release node 0: both of its barriers deliver.
        blockNode0.countDown();
        deadline = System.currentTimeMillis() + 10_000L;
        while (rpc0.delivered.get() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }
        assertTrue(rpc0.delivered.get() >= 2,
                "blocked node's barriers deliver after release (no loss)");
    }

    // ==================== assembly (mirrors the failover-test HA wiring) ====================

    private JobCoordinator buildCoordinator(CheckpointCoordinator checkpointCoordinator,
                                            TestLeaderElector elector,
                                            Map<String, IStreamTaskRpcService> rpcs) {
        TestJobCoordinatorLeaderElection.MockClusterRegistry registry = new TestJobCoordinatorLeaderElection.MockClusterRegistry();
        registry.registerNode("tm-0", "localhost:9001", 4);
        registry.registerNode("tm-1", "localhost:9002", 4);
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
        java.util.List<PartitionedPlan.EdgePlan> edgePlans = new java.util.ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("source", "sink",
                io.nop.stream.core.execution.plan.PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, PIPELINE_ID, vertexPlans, edgePlans, null, null);
        return new DeploymentPlan(JOB_ID, PIPELINE_ID, partitionedPlan,
                "local", "memory", "local", null, null);
    }

    // ==================== fixture RPC services ====================

    /** Node whose triggerCheckpoint blocks until released — records delivery after release. */
    static final class BlockingRpcService implements IStreamTaskRpcService {
        private final CountDownLatch block;
        final AtomicLong delivered = new AtomicLong();

        BlockingRpcService(CountDownLatch block) {
            this.block = block;
        }

        @Override
        public void triggerCheckpoint(io.nop.stream.core.checkpoint.CheckpointBarrier barrier,
                                      long fencingEpoch) {
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

    /** Fast node recording trigger deliveries. */
    static final class RecordingFastRpcService implements IStreamTaskRpcService {
        final AtomicLong triggers = new AtomicLong();

        @Override
        public void triggerCheckpoint(io.nop.stream.core.checkpoint.CheckpointBarrier barrier,
                                      long fencingEpoch) {
            triggers.incrementAndGet();
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
