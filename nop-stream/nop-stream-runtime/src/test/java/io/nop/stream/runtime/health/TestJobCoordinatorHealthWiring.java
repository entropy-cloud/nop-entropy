/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.health;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.coordinator.JobCoordinator;
import io.nop.stream.runtime.event.StreamJobEvent;
import io.nop.stream.runtime.event.StreamJobEventListener;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (P-REQ-7) wiring verification (plan guide #23): the health machine
 * is driven by the REAL JobCoordinator lifecycle events — start,
 * globalRecovery (entry + completion), failJob (incl. restart-cap path),
 * terminate(CANCEL) — and healed by the durable-checkpoint completion event
 * routed over the shared job event bus (the same route the real
 * CheckpointCoordinator completion path fires).
 */
class TestJobCoordinatorHealthWiring {

    private static final String JOB_ID = "health-wiring-job";

    @TempDir
    Path tempDir;

    private JobCoordinator coordinator;
    private final List<String> healthTransitions = new ArrayList<>();
    private final List<StreamJobEvent.EventType> eventTypes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        registry.registerNode("node-1", "localhost:8080", 4);

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(10_000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
        CheckpointCoordinator checkpointCoordinator =
                new CheckpointCoordinator(JOB_ID, "pipeline-0", new CheckpointIDCounter(), storage, config);

        java.util.Map<String, PartitionedPlan.VertexPlan> vertexPlans = new java.util.LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        PartitionedPlan partitionedPlan = new PartitionedPlan(JOB_ID, "pipeline-0", vertexPlans,
                List.of(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD)), null, null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan, "local", "memory", "local", null, null);

        coordinator = new JobCoordinator(JOB_ID, "coordinator-" + JOB_ID, deploymentPlan,
                registry, checkpointCoordinator,
                java.util.Map.of("node-1", new NoOpTaskRpcService()));

        coordinator.addHealthListener((jobId, from, to, cause) ->
                healthTransitions.add(from + "->" + to));
        coordinator.addJobEventListener(new StreamJobEventListener() {
            @Override
            public void onEvent(StreamJobEvent event) {
                eventTypes.add(event.getType());
            }
        });
    }

    /** Minimal no-op task RPC double: assignment/recovery paths only need a target to exist. */
    private static final class NoOpTaskRpcService
            implements io.nop.stream.runtime.rpc.IStreamTaskRpcService {
        @Override
        public void receiveAssignment(io.nop.stream.runtime.cluster.TaskAssignment assignment) {
        }

        @Override
        public void triggerCheckpoint(io.nop.stream.core.checkpoint.CheckpointBarrier barrier,
                                      long fencingEpoch) {
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex, long fencingEpoch) {
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
        }
    }

    @AfterEach
    void tearDown() {
        coordinator.stop();
    }

    @Test
    void startRecoveryHealAndFailAreDrivenByRealLifecycle() {
        coordinator.start();
        assertEquals(StreamJobHealth.RUNNING, coordinator.getHealth());

        // real recovery path: RUNNING -> RECOVERING -> DEGRADED (+ JOB_DEGRADED event)
        coordinator.globalRecovery();
        assertEquals(StreamJobHealth.DEGRADED, coordinator.getHealth());
        assertTrue(eventTypes.contains(StreamJobEvent.EventType.JOB_DEGRADED),
                "entering DEGRADED fires JOB_DEGRADED on the bus: " + eventTypes);

        // durable-checkpoint completion (real bus route) heals DEGRADED -> RUNNING
        coordinator.getJobEventBus().fire(new StreamJobEvent(
                JOB_ID, StreamJobEvent.EventType.CHECKPOINT_COMPLETED,
                System.currentTimeMillis(), 42L, 10L, 128L, null));
        assertEquals(StreamJobHealth.RUNNING, coordinator.getHealth());

        // real failure path -> FAILED
        coordinator.failJob(new IllegalStateException("wiring-test"));
        assertEquals(StreamJobHealth.FAILED, coordinator.getHealth());
        assertTrue(eventTypes.contains(StreamJobEvent.EventType.JOB_FAILED));

        assertEquals(List.of(
                "CREATED->RUNNING",
                "RUNNING->RECOVERING",
                "RECOVERING->DEGRADED",
                "DEGRADED->RUNNING",
                "RUNNING->FAILED"), healthTransitions);
    }

    @Test
    void restartCapExhaustionFailsFromRecovering() {
        coordinator.start();
        assertEquals(StreamJobHealth.RUNNING, coordinator.getHealth());

        coordinator.setMaxRestarts(0);
        coordinator.requestRecovery(); // cap=0: first recovery immediately fails the job
        assertEquals(StreamJobHealth.FAILED, coordinator.getHealth());
        assertEquals(List.of("CREATED->RUNNING", "RUNNING->RECOVERING", "RECOVERING->FAILED"),
                healthTransitions);
    }

    @Test
    void terminateCancelTransitionsToCanceled() {
        coordinator.start();
        coordinator.terminate(io.nop.stream.core.checkpoint.JobTerminationMode.CANCEL);
        assertEquals(StreamJobHealth.CANCELED, coordinator.getHealth());
        assertTrue(healthTransitions.contains("RUNNING->CANCELED"));
        // idempotent failJob after terminal state is an observable no-op (WARN),
        // never an illegal health transition
        coordinator.failJob(new IllegalStateException("late"));
        assertEquals(StreamJobHealth.CANCELED, coordinator.getHealth());
    }

    @Test
    void terminateDrainTransitionsToFinished() {
        coordinator.start();
        coordinator.setTerminationCheckpointTimeoutMs(200L);
        coordinator.terminate(io.nop.stream.core.checkpoint.JobTerminationMode.DRAIN);
        assertEquals(StreamJobHealth.FINISHED, coordinator.getHealth());
        assertTrue(healthTransitions.contains("RUNNING->FINISHED"));
    }

    @Test
    void exportSavepointKeepsRunning() {
        coordinator.start();
        coordinator.setTerminationCheckpointTimeoutMs(200L);
        coordinator.terminate(io.nop.stream.core.checkpoint.JobTerminationMode.EXPORT_SAVEPOINT);
        assertEquals(StreamJobHealth.RUNNING, coordinator.getHealth(),
                "EXPORT_SAVEPOINT keeps the job running (no terminal transition)");
    }
}
