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
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G56 wiring verification: confirms {@link JobCoordinator#assignTasks()} actually
 * drives the new {@code attemptNumber} API (not the legacy UUID-only path) and that
 * {@code globalRecovery()} increments the counter so ClusterRegistry preserves a
 * monotonically increasing attempt history.
 *
 * <p>This is the #23 (接线验证) test required by Phase 1 Exit Criteria.
 */
class TestJobCoordinatorAttemptTracking {

    private static final String JOB_ID = "attempt-job-1";

    @TempDir
    Path tempDir;

    private InMemoryClusterRegistry clusterRegistry;
    private CheckpointCoordinator checkpointCoordinator;
    private CapturingTaskRpc taskRpc;
    private DeploymentPlan deploymentPlan;
    private JobCoordinator coordinator;

    @BeforeEach
    void setUp() {
        clusterRegistry = new InMemoryClusterRegistry();
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(10000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
        checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", idCounter, storage, config);

        clusterRegistry.registerNode("node-1", "localhost:9090", 4);

        taskRpc = new CapturingTaskRpc();
        Map<String, IStreamTaskRpcService> taskRpcServices = new HashMap<>();
        taskRpcServices.put("node-1", taskRpc);

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 2, null));
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
                clusterRegistry, checkpointCoordinator, taskRpcServices);
        coordinator.setTerminationCheckpointTimeoutMs(500L);
    }

    @AfterEach
    void tearDown() {
        coordinator.stop();
    }

    @Test
    void firstAssignmentUsesAttemptNumberOne() {
        coordinator.start();
        coordinator.assignTasks();

        // All subtasks should have attemptNumber = 1
        for (TaskAssignment a : taskRpc.assignments) {
            assertEquals(1, a.getAttemptNumber(),
                    "first assignment must carry attemptNumber=1, got " + a.getAttemptNumber());
        }

        // ClusterRegistry history should also reflect attemptNumber=1
        for (String vertex : new String[]{"source", "sink"}) {
            int parallelism = vertex.equals("source") ? 2 : 1;
            for (int i = 0; i < parallelism; i++) {
                List<TaskAssignment> history = clusterRegistry.getAttemptHistory(JOB_ID, vertex, i);
                assertEquals(1, history.size());
                assertEquals(1, history.get(0).getAttemptNumber());
            }
        }
    }

    @Test
    void globalRecoveryIncrementsAttemptNumber() {
        coordinator.start();
        coordinator.assignTasks();

        // Trigger recovery — should bump attemptNumber
        coordinator.globalRecovery();

        for (String vertex : new String[]{"source", "sink"}) {
            int parallelism = vertex.equals("source") ? 2 : 1;
            for (int i = 0; i < parallelism; i++) {
                List<TaskAssignment> history = clusterRegistry.getAttemptHistory(JOB_ID, vertex, i);
                assertEquals(2, history.size(),
                        "history should retain both attempts after recovery (vertex=" + vertex + " subtask=" + i + ")");
                assertEquals(1, history.get(0).getAttemptNumber());
                assertEquals(2, history.get(1).getAttemptNumber());

                TaskAssignment latest = clusterRegistry.getTaskAssignment(JOB_ID, vertex, i);
                assertEquals(2, latest.getAttemptNumber(),
                        "latest assignment after recovery must have attemptNumber=2");
            }
        }
    }

    @Test
    void multipleRecoveriesProduceMonotonicHistory() {
        coordinator.start();
        coordinator.assignTasks();
        coordinator.globalRecovery();
        coordinator.globalRecovery();
        coordinator.globalRecovery();

        // source/0 should have 4 attempts (1 initial + 3 recoveries)
        List<TaskAssignment> history = clusterRegistry.getAttemptHistory(JOB_ID, "source", 0);
        assertEquals(4, history.size());
        for (int i = 0; i < history.size(); i++) {
            assertEquals(i + 1, history.get(i).getAttemptNumber(),
                    "monotonic check: position " + i + " should be attemptNumber " + (i + 1));
        }
    }

    @Test
    void attemptIdAndAttemptNumberAreBothPopulated() {
        coordinator.start();
        coordinator.assignTasks();

        for (TaskAssignment a : taskRpc.assignments) {
            assertNotNull(a.getAttemptId(), "UUID attemptId must still be set");
            assertTrue(a.getAttemptId().length() > 0);
            assertTrue(a.getAttemptNumber() >= 1, "attemptNumber must be >= 1");
        }
    }

    static class CapturingTaskRpc implements IStreamTaskRpcService {
        final List<TaskAssignment> assignments = new CopyOnWriteArrayList<>();

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
            assignments.add(assignment);
        }

        @Override
        public void triggerCheckpoint(io.nop.stream.core.checkpoint.CheckpointBarrier barrier, String fencingToken) {
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
        }

        @Override
        public void updateFencingToken(String newToken) {
        }
    }
}
