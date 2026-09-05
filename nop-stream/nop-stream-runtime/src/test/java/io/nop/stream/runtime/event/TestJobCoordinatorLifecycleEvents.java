/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.event;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.JobTerminationMode;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.coordinator.JobCoordinator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (P-REQ-2 wiring): asserts that REAL coordinator lifecycle methods
 * fire job events — listeners registered on the coordinator's bus are called
 * back on start / failJob / terminate(CANCEL) / checkpoint completion paths.
 */
class TestJobCoordinatorLifecycleEvents {

    private static final String JOB_ID = "events-job-1";

    @TempDir
    Path tempDir;

    private ClusterRegistry clusterRegistry;
    private CheckpointCoordinator checkpointCoordinator;
    private JobCoordinator coordinator;
    private List<StreamJobEvent> received;

    @BeforeEach
    void setUp() {
        clusterRegistry = new InMemoryClusterRegistry();
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(5000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(2)
                .build();
        checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", new CheckpointIDCounter(), storage, config);

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, "pipeline-0", vertexPlans, Collections.emptyList(), null, null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan, "local", "memory", "local", null, null);

        coordinator = new JobCoordinator(JOB_ID, "coordinator-1", deploymentPlan,
                clusterRegistry, checkpointCoordinator, Collections.emptyMap());

        received = Collections.synchronizedList(new ArrayList<>());
        coordinator.addJobEventListener(received::add);
    }

    @Test
    void testStartFiresJobStarted() {
        coordinator.start();
        try {
            assertEquals(1, received.size());
            assertEquals(StreamJobEvent.EventType.JOB_STARTED, received.get(0).getType());
            assertEquals(JOB_ID, received.get(0).getJobId());
        } finally {
            coordinator.stop();
        }
    }

    @Test
    void testFailJobFiresJobFailedWithCause() {
        coordinator.start();
        try {
            received.clear();
            coordinator.failJob(new IllegalStateException("cap exceeded"));
            assertEquals(1, received.size());
            assertEquals(StreamJobEvent.EventType.JOB_FAILED, received.get(0).getType());
            assertTrue(received.get(0).getCause().contains("cap exceeded"));
        } finally {
            coordinator.stop();
        }
    }

    @Test
    void testTerminateCancelFiresJobCanceled() {
        coordinator.start();
        received.clear();
        coordinator.terminate(JobTerminationMode.CANCEL);
        assertEquals(1, received.size());
        assertEquals(StreamJobEvent.EventType.JOB_CANCELED, received.get(0).getType());
    }

    @Test
    void testCheckpointCoordinatorSharesCoordinatorBus() {
        // The coordinator constructor injects its bus into the checkpoint
        // coordinator, so checkpoint-level events reach job listeners.
        assertSameBus(coordinator.getJobEventBus(), checkpointCoordinator.getJobEventBus());

        checkpointCoordinator.getJobEventBus().fire(
                StreamJobEvent.simple(JOB_ID, StreamJobEvent.EventType.CHECKPOINT_COMPLETED, null));
        assertEquals(1, received.size());
        assertEquals(StreamJobEvent.EventType.CHECKPOINT_COMPLETED, received.get(0).getType());
    }

    @Test
    void testBuiltInLoggingListenerRegisteredByDefault() {
        // P-REQ-2: the coordinator bus always carries the built-in logging
        // listener (1) plus the test listener registered in setUp (1) plus the
        // P-REQ-7 health router (CHECKPOINT_COMPLETED -> DEGRADED heal, 1).
        assertEquals(3, coordinator.getJobEventBus().getListenerCount());
    }

    private void assertSameBus(StreamJobEventBus expected, StreamJobEventBus actual) {
        assertEquals(System.identityHashCode(expected), System.identityHashCode(actual),
                "checkpoint coordinator must share the job coordinator's event bus");
    }
}
