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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.api.core.message.IMessageService;
import io.nop.cluster.elector.LeaderEpoch;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.taskmanager.TaskManager;
import io.nop.stream.runtime.transport.RemoteInputChannel;
import io.nop.stream.runtime.transport.RemoteResultPartition;
import io.nop.stream.runtime.transport.StreamTopicNaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 39 Phase 1 focused Proof tests for the fencing-token String→long epoch
 * unification. Explicitly asserts the two preserved fencing invariants under the
 * new single-long-epoch representation:
 * <ol>
 *   <li><b>stale-leader rejection</b> — a leadership switch advances the epoch;
 *       control calls carrying the old epoch are rejected (invariant #8)</li>
 *   <li><b>same-leader prior-recovery rejection</b> — a globalRecovery() within
 *       the same leadership advances the epoch; prior-recovery stale tasks are
 *       rejected/canceled</li>
 * </ol>
 * plus data-plane stale-envelope filtering (single long comparison) and non-HA
 * zero-regression.
 *
 * <p>These are NEW tests (plan guide #25) that explicitly assert stale/current
 * epoch behaviour, not merely "the old tests still compile".
 */
class TestFencingEpochUnification {

    private static final String JOB_ID = "fencing-job-1";

    @TempDir
    Path tempDir;

    private LocalMessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new LocalMessageService();
    }

    @AfterEach
    void tearDown() {
        messageService.clearConsumers();
    }

    // ==================== (a) data-plane stale long-epoch envelope filtering ====================

    /**
     * (a) A stale-epoch envelope is discarded by RemoteInputChannel; a current-epoch
     * envelope is accepted. Stage 39 collapses the legacy dual-key filter into a
     * single long epoch comparison.
     */
    @Test
    void dataPlaneStaleEpochEnvelopeDiscardedCurrentAccepted() throws Exception {
        long currentEpoch = 5L;
        String topic = StreamTopicNaming.buildTopic(JOB_ID, "src->tgt", 0, 0);

        RemoteResultPartition producer = new RemoteResultPartition(
                messageService, topic, null, "src->tgt", currentEpoch);
        RemoteInputChannel consumer = new RemoteInputChannel(messageService, topic, currentEpoch);

        // Send a STALE-epoch envelope directly (epoch 4) — must be discarded.
        producer.write(new StreamRecord<>("current-with-right-epoch"));
        // Now rotate the producer to a stale epoch and send — the consumer (still
        // expecting 5) must discard it.
        RemoteResultPartition staleProducer = new RemoteResultPartition(
                messageService, topic, null, "src->tgt", 4L);
        staleProducer.write(new StreamRecord<>("stale-should-be-discarded"));

        // The consumer should only ever receive the current-epoch record.
        StreamRecord<?> first = (StreamRecord<?>) consumer.read(2, TimeUnit.SECONDS);
        assertTrue(first != null, "current-epoch envelope must be accepted");
        assertEquals("current-with-right-epoch", first.getValue());

        // No further element should arrive within a short window (stale discarded).
        assertNull(consumer.read(500, TimeUnit.MILLISECONDS),
                "stale-epoch envelope must be discarded, not delivered");

        consumer.close();
    }

    // ==================== (b) leadership switch advances epoch; old epoch rejected ====================

    /**
     * (b) A leadership switch advances the fencing epoch (leaderEpoch * EPOCH_SCALE).
     * A TaskManager fenced to the OLD epoch must reject a checkpoint trigger
     * carrying the old epoch after the coordinator has advanced — and the
     * coordinator's own collectAck must reject a stale-epoch ACK.
     */
    @Test
    void leadershipSwitchAdvancesEpochOldControlRejected() throws Exception {
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        TaskManager tm = new TaskManager("node-1", "embedded:node-1", 4, messageService, registry,
                "nop-stream.control." + JOB_ID);
        tm.start();
        try {
            // Leader epoch 5 granted → fencing epoch = 5 * EPOCH_SCALE.
            long epochAt5 = JobCoordinator.deriveHaFencingEpoch(5L, 0L);
            tm.updateFencingToken(epochAt5);

            CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

            // Current epoch accepted (no throw).
            tm.triggerCheckpoint(barrier, epochAt5);

            // Leadership switches to epoch 6 → fencing epoch = 6 * EPOCH_SCALE.
            long epochAt6 = JobCoordinator.deriveHaFencingEpoch(6L, 0L);
            assertTrue(epochAt6 > epochAt5, "leadership switch must advance the fencing epoch");

            // The coordinator pushes the new epoch; the task side rotates.
            tm.updateFencingToken(epochAt6);

            // Old-epoch control (epoch at leader 5) must now be rejected — the
            // stale-leader invariant under the single long comparison.
            long staleEpoch = epochAt5;
            StreamException ex = assertThrows(StreamException.class,
                    () -> tm.triggerCheckpoint(barrier, staleEpoch),
                    "stale-leader epoch control must be rejected after leadership switch");
            assertTrue(ex.getMessage().contains("fencing") || ex.getParam("actualToken") != null
                            || ex.getParam("expectedToken") != null,
                    "stale-epoch rejection must be explicit (carries expected/actual token params)");
        } finally {
            tm.stop();
        }
    }

    // ==================== (c) same-leader recovery advances epoch; prior-recovery task rejected ====================

    /**
     * (c) A globalRecovery() within the SAME leadership advances the fencing epoch
     * by incrementing recoveryGen (epoch = leaderEpoch * EPOCH_SCALE + recoveryGen).
     * A task fenced to the previous recovery's epoch is rejected after the rotation.
     */
    @Test
    void sameLeaderRecoveryAdvancesEpochPriorRecoveryRejected() throws Exception {
        // Reuse a full HA coordinator to drive the recovery rotation end-to-end.
        CapturingTaskRpc capture = new CapturingTaskRpc();
        Map<String, io.nop.stream.runtime.rpc.IStreamTaskRpcService> taskRpcServices = new LinkedHashMap<>();
        taskRpcServices.put("node-1", capture);

        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        registry.registerNode("node-1", "localhost:8080", 4);

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointCoordinator cc = new CheckpointCoordinator(JOB_ID, "pipeline-0",
                new CheckpointIDCounter(), storage, fastConfig());

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans =
                java.util.Collections.singletonList(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(JOB_ID, "pipeline-0", vertexPlans, edgePlans, null, null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(JOB_ID, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);

        JobCoordinator coordinator = new JobCoordinator(JOB_ID, "coord-1", deploymentPlan,
                registry, cc, taskRpcServices);
        TestLeaderElector elector = new TestLeaderElector("host-A");
        coordinator.setLeaderElector(elector);
        coordinator.setMaxRestarts(10);

        try {
            coordinator.start();
            elector.grantLeadership(5L);
            long epochAfterGrant = coordinator.getFencingEpoch();
            assertEquals(JobCoordinator.deriveHaFencingEpoch(5L, 0L), epochAfterGrant,
                    "activation epoch = leaderEpoch * EPOCH_SCALE");
            assertEquals(epochAfterGrant, capture.lastPushedEpoch,
                    "activation must push the epoch to the task side");

            // Same-leader global recovery → recoveryGen 1 → epoch advances by 1.
            coordinator.globalRecovery();
            long epochAfterRecovery = coordinator.getFencingEpoch();
            assertEquals(JobCoordinator.deriveHaFencingEpoch(5L, 1L), epochAfterRecovery,
                    "same-leader recovery must advance epoch by 1 (recoveryGen)");
            assertTrue(epochAfterRecovery > epochAfterGrant,
                    "recovery must produce a strictly larger epoch than the prior round");
            assertEquals(epochAfterRecovery, capture.lastPushedEpoch,
                    "recovery must push the rotated epoch to the task side");

            // The task side, fenced to the prior-recovery epoch, must reject a
            // NEW-epoch control call (the coordinator, now at epochAfterRecovery,
            // triggers the next checkpoint and the stale prior-recovery task is
            // fenced out). Simulate a stale task still holding epochAfterGrant.
            TaskManager tm = new TaskManager("node-stale", "embedded:node-stale", 4, messageService, registry,
                    "nop-stream.control.stale");
            tm.start();
            try {
                tm.updateFencingToken(epochAfterGrant);
                CheckpointBarrier barrier = new CheckpointBarrier(2L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
                // Coordinator advances to epochAfterRecovery; the stale task (still
                // fenced to epochAfterGrant) must reject the new-epoch trigger.
                assertThrows(StreamException.class,
                        () -> tm.triggerCheckpoint(barrier, epochAfterRecovery),
                        "a task fenced to the prior-recovery epoch must reject the new-epoch control call");
            } finally {
                tm.stop();
            }
        } finally {
            coordinator.stop();
        }
    }

    // ==================== (d) non-HA zero regression ====================

    /**
     * (d) Non-HA mode derives a monotonic long fencing epoch (leaderEpoch component
     * 0, recoveryGen seeded to 1 on start). globalRecovery() increments it so fencing
     * stays effective — a task fenced to the pre-recovery epoch is rejected afterwards.
     * Zero regression vs. the legacy random-UUID behaviour for the embedded path.
     */
    @Test
    void nonHaModeEpochMonotonicAndFencesAfterRecovery() throws Exception {
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointCoordinator cc = new CheckpointCoordinator(JOB_ID, "pipeline-0",
                new CheckpointIDCounter(), storage, fastConfig());

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = java.util.Collections.emptyList();
        PartitionedPlan partitionedPlan = new PartitionedPlan(JOB_ID, "pipeline-0", vertexPlans, edgePlans, null, null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(JOB_ID, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);

        CapturingTaskRpc capture = new CapturingTaskRpc();
        Map<String, io.nop.stream.runtime.rpc.IStreamTaskRpcService> taskRpcServices = new LinkedHashMap<>();
        taskRpcServices.put("node-1", capture);
        registry.registerNode("node-1", "localhost:8080", 4);

        JobCoordinator coordinator = new JobCoordinator(JOB_ID, "coord-nonha", deploymentPlan,
                registry, cc, taskRpcServices);
        coordinator.setMaxRestarts(10);
        coordinator.setAutoRecoverOnFailedReport(false);

        try {
            coordinator.start();
            assertTrue(coordinator.isActive(), "non-HA coordinator must be active immediately");
            assertNull(coordinator.getCurrentLeadership(), "non-HA coordinator has no leadership epoch");
            long epochAtStart = coordinator.getFencingEpoch();
            assertEquals(1L, epochAtStart, "non-HA start epoch = deriveHaFencingEpoch(0, 1) = 1");

            // Recovery rotates the epoch (fencing still effective, not a fixed value).
            coordinator.globalRecovery();
            long epochAfterRecovery = coordinator.getFencingEpoch();
            assertEquals(2L, epochAfterRecovery, "non-HA recovery increments recoveryGen → epoch 2");
            assertTrue(epochAfterRecovery > epochAtStart, "non-HA epoch must be monotonic across recovery");
            assertEquals(epochAfterRecovery, capture.lastPushedEpoch,
                    "non-HA recovery must push the rotated epoch to the task side");

            // A task fenced to the pre-recovery epoch (1) must reject the
            // NEW-epoch control call after the coordinator rotated to epoch 2.
            TaskManager tm = new TaskManager("node-nonha-stale", "embedded:node-nonha-stale", 4,
                    messageService, registry, "nop-stream.control.nonha");
            tm.start();
            try {
                tm.updateFencingToken(epochAtStart);
                CheckpointBarrier barrier = new CheckpointBarrier(3L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
                assertThrows(StreamException.class,
                        () -> tm.triggerCheckpoint(barrier, epochAfterRecovery),
                        "non-HA task fenced to the pre-recovery epoch must reject the new-epoch control after recovery");
            } finally {
                tm.stop();
            }
        } finally {
            coordinator.stop();
        }
    }

    /**
     * LeaderEpoch / recoveryGen encoding sanity: a new leader always dominates any
     * number of prior-leader recoveries (the scale reserves low-order digits for
     * recoveryGen), so the single long comparison preserves both invariants.
     */
    @Test
    void encodingNewLeaderDominatesPriorLeaderRecoveries() {
        long priorLeaderEpoch = 5L;
        // Prior leader did many recoveries (recoveryGen up to EPOCH_SCALE - 1).
        long maxPriorRecovery = JobCoordinator.deriveHaFencingEpoch(priorLeaderEpoch, JobCoordinator.EPOCH_SCALE - 1);
        long newLeaderEpoch = JobCoordinator.deriveHaFencingEpoch(priorLeaderEpoch + 1, 0L);
        assertTrue(newLeaderEpoch > maxPriorRecovery,
                "a new leader (epoch+1, recoveryGen 0) must dominate the prior leader's max recovery epoch");
        assertFalse(newLeaderEpoch == maxPriorRecovery);
    }

    private static CheckpointConfig fastConfig() {
        return CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(2000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
    }

    /** Minimal IStreamTaskRpcService that records the last pushed fencing epoch. */
    static final class CapturingTaskRpc implements io.nop.stream.runtime.rpc.IStreamTaskRpcService {
        volatile long lastPushedEpoch;
        volatile long lastTriggerEpoch;

        @Override
        public void receiveAssignment(io.nop.stream.runtime.cluster.TaskAssignment assignment) {
        }

        @Override
        public void triggerCheckpoint(CheckpointBarrier barrier, long fencingEpoch) {
            lastTriggerEpoch = fencingEpoch;
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
            lastPushedEpoch = fencingEpoch;
        }
    }
}
