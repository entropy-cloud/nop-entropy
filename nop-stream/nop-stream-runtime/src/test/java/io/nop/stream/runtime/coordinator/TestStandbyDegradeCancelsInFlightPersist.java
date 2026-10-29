/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.coordinator;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.storage.CheckpointStorageException;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2510 C7-011 / C10-009 (plan 2510 裁定⑤): standby degrade must (a) actively
 * abort in-flight pending checkpoints and (b) re-check leader activity at the
 * persist-success entry and before the participant commit fan-out — a zombie
 * standby whose persist completes AFTER the degrade must NOT become a second
 * writer on the checkpoint durability plane (no finishCommit(true) fan-out).
 *
 * <p>Red (pre-fix): deactivateToStandby only flips the active flag; the pending
 * checkpoint stays RUNNING and a persist completing after the degrade still
 * fans out participant commits.
 */
class TestStandbyDegradeCancelsInFlightPersist {

    private static final String JOB_ID = "c7-011-job";
    private static final String PIPELINE_ID = "pipeline-0";
    private static final TaskLocation LOC_SOURCE = new TaskLocation(JOB_ID, PIPELINE_ID, "source", 0);
    private static final TaskLocation LOC_SINK = new TaskLocation(JOB_ID, PIPELINE_ID, "sink", 0);

    @TempDir
    Path tempDir;

    private TestLeaderElector elector;
    private InMemoryClusterRegistry clusterRegistry;
    private JobCoordinator coordinator;
    private BlockingCheckpointStorage storage;

    /** Storage whose storeCheckPoint blocks on a latch — models in-flight persist I/O. */
    static final class BlockingCheckpointStorage extends LocalFileCheckpointStorage {
        private static final long serialVersionUID = 1L;
        final CountDownLatch persistGate = new CountDownLatch(1);
        final AtomicBoolean persistEntered = new AtomicBoolean(false);

        BlockingCheckpointStorage(String baseDir) {
            super(baseDir);
        }

        @Override
        public String storeCheckPoint(CompletedCheckpoint checkpoint) throws CheckpointStorageException {
            persistEntered.set(true);
            try {
                if (!persistGate.await(15, TimeUnit.SECONDS)) {
                    throw new CheckpointStorageException("persist gate timeout", null);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CheckpointStorageException("persist gate interrupted", null);
            }
            return super.storeCheckPoint(checkpoint);
        }
    }

    @BeforeEach
    void setUp() {
        elector = new TestLeaderElector("host-A");
        clusterRegistry = new InMemoryClusterRegistry();
        clusterRegistry.registerNode("node-1", "localhost:8081", 4);
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.stop();
        }
    }

    private JobCoordinator newCoordinator(CheckpointCoordinator checkpointCoordinator) {
        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = new ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("source", "sink",
                io.nop.stream.core.execution.plan.PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, PIPELINE_ID, vertexPlans, edgePlans, null, null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                JOB_ID, PIPELINE_ID, partitionedPlan, "local", "memory", "local", null, null);

        JobCoordinator c = new JobCoordinator(
                JOB_ID, "coord-c711", deploymentPlan,
                clusterRegistry, checkpointCoordinator,
                Map.of("node-1", new CoordinatorTestSupport.RecordingTaskRpcService()));
        c.setLeaderElector(elector);
        c.setTerminationCheckpointTimeoutMs(500L);
        return c;
    }

    private CheckpointCoordinator newCheckpointCoordinator(boolean asyncPersist) {
        storage = new BlockingCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(3600_000L)
                .checkpointTimeout(10_000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
        config.setAsyncSnapshotEnabled(asyncPersist);
        return new CheckpointCoordinator(JOB_ID, PIPELINE_ID, idCounter, storage, config);
    }

    private static CheckpointParticipant commitRecordingParticipant(
            AtomicBoolean commitFanoutSeen) {
        return new CheckpointParticipant() {
            @Override
            public TaskStateSnapshot saveState(long epochId) {
                return TaskStateSnapshot.empty(null);
            }

            @Override
            public void prepareCommit(long epochId) {
            }

            @Override
            public void finishCommit(long epochId, boolean success) {
                if (success) {
                    commitFanoutSeen.set(true);
                }
            }

            @Override
            public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
            }
        };
    }

    private void awaitOrFail(AtomicBoolean flag, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!flag.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(flag.get(), message);
    }

    @Test
    void degradeThenPersistCompletionMustNotFanOutParticipantCommit() throws Exception {
        CheckpointCoordinator checkpointCoordinator = newCheckpointCoordinator(true);
        AtomicBoolean commitFanoutSeen = new AtomicBoolean(false);
        checkpointCoordinator.addParticipant(commitRecordingParticipant(commitFanoutSeen));

        coordinator = newCoordinator(checkpointCoordinator);
        coordinator.start();
        elector.grantLeadership(1L);
        assertTrue(coordinator.isActive());

        PendingCheckpoint pending = coordinator.triggerCheckpoint();
        assertNotNull(pending, "active leader must be able to trigger a checkpoint");
        long checkpointId = pending.getCheckpointId();

        checkpointCoordinator.acknowledgeTask(LOC_SOURCE, checkpointId, TaskStateSnapshot.empty(LOC_SOURCE));
        checkpointCoordinator.acknowledgeTask(LOC_SINK, checkpointId, TaskStateSnapshot.empty(LOC_SINK));

        // Wait until the persist is in-flight (blocked inside storeCheckPoint).
        awaitOrFail(storage.persistEntered, "persist must be in-flight before the degrade");

        // Leader loses leadership WHILE the persist is in flight.
        elector.revokeLeadership();
        assertFalse(coordinator.isActive(), "degrade must flip the coordinator to standby");

        // The in-flight persist now completes.
        storage.persistGate.countDown();

        long deadline = System.currentTimeMillis() + 3_000;
        while (!commitFanoutSeen.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        // C7-011: the zombie standby must NOT become a second writer on the
        // checkpoint durability plane — no participant commit fan-out after degrade.
        assertFalse(commitFanoutSeen.get(),
                "standby degrade must cancel the in-flight checkpoint: persist completing "
                        + "after the degrade must not fan out participant commits (C7-011 second-writer)");
    }

    @Test
    void degradeAbortsInFlightPendingCheckpoint() throws Exception {
        CheckpointCoordinator checkpointCoordinator = newCheckpointCoordinator(false);
        AtomicBoolean commitFanoutSeen = new AtomicBoolean(false);
        checkpointCoordinator.addParticipant(commitRecordingParticipant(commitFanoutSeen));

        coordinator = newCoordinator(checkpointCoordinator);
        coordinator.start();
        elector.grantLeadership(1L);
        assertTrue(coordinator.isActive());

        PendingCheckpoint pending = coordinator.triggerCheckpoint();
        assertNotNull(pending);
        long checkpointId = pending.getCheckpointId();

        elector.revokeLeadership();
        assertFalse(coordinator.isActive());

        long deadline = System.currentTimeMillis() + 5_000;
        while (checkpointCoordinator.getPendingCheckpoint(checkpointId) != null
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertNull(checkpointCoordinator.getPendingCheckpoint(checkpointId),
                "standby degrade must actively abort the in-flight pending checkpoint (C7-011)");
    }
}
