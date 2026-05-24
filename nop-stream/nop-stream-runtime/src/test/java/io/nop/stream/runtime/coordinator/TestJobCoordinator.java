/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.api.core.message.*;
import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.taskmanager.CheckpointAckMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JobCoordinator:
 * - start → register coordinator → assign tasks
 * - triggerCheckpoint → barrier signal sent → collect ACKs → complete
 * - Termination modes
 * - Global recovery
 */
class TestJobCoordinator {

    private static final String JOB_ID = "test-job-1";
    private static final String COORDINATOR_ID = "coordinator-1";
    private static final String CONTROL_TOPIC = "test-control-topic";

    @TempDir
    Path tempDir;

    private JobCoordinator coordinator;
    private MockClusterRegistry clusterRegistry;
    private MockMessageService messageService;
    private CheckpointCoordinator checkpointCoordinator;

    @BeforeEach
    void setUp() {
        clusterRegistry = new MockClusterRegistry();
        messageService = new MockMessageService();

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

        // Build a simple DeploymentPlan with 2 vertices, each parallelism 1
        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));

        List<PartitionedPlan.EdgePlan> edgePlans = new ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("source", "sink",
                io.nop.stream.core.execution.plan.PartitionPolicy.FORWARD));

        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, "pipeline-0", vertexPlans, edgePlans, null, null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);

        coordinator = new JobCoordinator(
                JOB_ID, COORDINATOR_ID, deploymentPlan,
                clusterRegistry, messageService, checkpointCoordinator,
                null, CONTROL_TOPIC);
    }

    @AfterEach
    void tearDown() {
        coordinator.stop();
    }

    @Test
    void testStartRegistersCoordinator() {
        coordinator.start();
        assertTrue(coordinator.isRunning());
        assertNotNull(coordinator.getFencingToken());
    }

    @Test
    void testAssignTasksDistributesToNodes() {
        // Register a node first
        clusterRegistry.registerNode("node-1", "localhost:9090", 4);

        coordinator.start();
        coordinator.assignTasks();

        Map<String, List<TaskAssignment>> assignments = coordinator.getTaskAssignments();
        assertFalse(assignments.isEmpty());

        // Should have assignments for both vertices
        assertTrue(assignments.containsKey("source"));
        assertTrue(assignments.containsKey("sink"));

        // Each vertex has 1 subtask (parallelism=1)
        assertEquals(1, assignments.get("source").size());
        assertEquals(1, assignments.get("sink").size());

        // All assigned to node-1
        assertEquals("node-1", assignments.get("source").get(0).getNodeId());
        assertEquals("node-1", assignments.get("sink").get(0).getNodeId());

        // TaskAssignmentMessages sent via message service
        assertFalse(messageService.sentMessages.isEmpty());
    }

    @Test
    void testAssignTasksWithMultipleNodes() {
        clusterRegistry.registerNode("node-1", "localhost:9090", 4);
        clusterRegistry.registerNode("node-2", "localhost:9091", 4);

        coordinator.start();
        coordinator.assignTasks();

        Map<String, List<TaskAssignment>> assignments = coordinator.getTaskAssignments();
        // Round-robin: source goes to node-1, sink goes to node-2
        assertEquals("node-1", assignments.get("source").get(0).getNodeId());
        assertEquals("node-2", assignments.get("sink").get(0).getNodeId());
    }

    @Test
    void testAssignTasksNoActiveNodes() {
        coordinator.start();
        // No nodes registered
        coordinator.assignTasks();

        Map<String, List<TaskAssignment>> assignments = coordinator.getTaskAssignments();
        assertTrue(assignments.isEmpty());
    }

    @Test
    void testTriggerCheckpointSendsBarrier() {
        clusterRegistry.registerNode("node-1", "localhost:9090", 4);
        coordinator.start();
        coordinator.assignTasks();

        PendingCheckpoint pending = coordinator.triggerCheckpoint();
        assertNotNull(pending);

        // Check that barrier signal was sent
        boolean hasBarrierSignal = messageService.sentMessages.stream()
                .anyMatch(m -> m instanceof CheckpointBarrierSignal);
        assertTrue(hasBarrierSignal);
    }

    @Test
    void testCollectAckAcceptsValidMessage() {
        clusterRegistry.registerNode("node-1", "localhost:9090", 4);
        coordinator.start();
        coordinator.assignTasks();

        PendingCheckpoint pending = coordinator.triggerCheckpoint();
        assertNotNull(pending);

        // Simulate ACK from source task
        TaskLocation sourceLoc = new TaskLocation(JOB_ID, "pipeline-0", "source", 0);
        TaskStateSnapshot snapshot = TaskStateSnapshot.builder(sourceLoc)
                .checkpointId(pending.getCheckpointId())
                .putOperatorState("state1", "data1")
                .build();

        CheckpointAckMessage ack = new CheckpointAckMessage(
                sourceLoc, pending.getCheckpointId(), snapshot,
                coordinator.getFencingToken());

        boolean accepted = coordinator.collectAck(ack);
        assertTrue(accepted);
    }

    @Test
    void testCollectAckRejectsStaleToken() {
        clusterRegistry.registerNode("node-1", "localhost:9090", 4);
        coordinator.start();
        coordinator.assignTasks();

        PendingCheckpoint pending = coordinator.triggerCheckpoint();
        assertNotNull(pending);

        TaskLocation sourceLoc = new TaskLocation(JOB_ID, "pipeline-0", "source", 0);
        TaskStateSnapshot snapshot = TaskStateSnapshot.builder(sourceLoc)
                .checkpointId(pending.getCheckpointId())
                .build();

        CheckpointAckMessage ack = new CheckpointAckMessage(
                sourceLoc, pending.getCheckpointId(), snapshot,
                "stale-token");

        boolean accepted = coordinator.collectAck(ack);
        assertFalse(accepted);
    }

    @Test
    void testFullCheckpointFlow() throws Exception {
        clusterRegistry.registerNode("node-1", "localhost:9090", 4);
        coordinator.start();
        coordinator.assignTasks();

        PendingCheckpoint pending = coordinator.triggerCheckpoint();
        assertNotNull(pending);

        String token = coordinator.getFencingToken();

        // ACK from source
        TaskLocation sourceLoc = new TaskLocation(JOB_ID, "pipeline-0", "source", 0);
        TaskStateSnapshot sourceSnapshot = TaskStateSnapshot.builder(sourceLoc)
                .checkpointId(pending.getCheckpointId())
                .putOperatorState("sourceState", "sourceData")
                .build();

        coordinator.collectAck(new CheckpointAckMessage(
                sourceLoc, pending.getCheckpointId(), sourceSnapshot, token));

        // ACK from sink
        TaskLocation sinkLoc = new TaskLocation(JOB_ID, "pipeline-0", "sink", 0);
        TaskStateSnapshot sinkSnapshot = TaskStateSnapshot.builder(sinkLoc)
                .checkpointId(pending.getCheckpointId())
                .putOperatorState("sinkState", "sinkData")
                .build();

        coordinator.collectAck(new CheckpointAckMessage(
                sinkLoc, pending.getCheckpointId(), sinkSnapshot, token));

        // Wait for checkpoint completion
        CompletedCheckpoint completed = pending.getCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertNotNull(completed);
        assertEquals(pending.getCheckpointId(), completed.getCheckpointId());
        assertEquals(2, completed.getTaskStates().size());
    }

    @Test
    void testTerminateCancel() {
        coordinator.start();
        coordinator.terminate(JobTerminationMode.CANCEL);
        assertFalse(coordinator.isRunning());
    }

    @Test
    void testGlobalRecoveryGeneratesNewToken() {
        clusterRegistry.registerNode("node-1", "localhost:9090", 4);
        coordinator.start();
        coordinator.assignTasks();

        String oldToken = coordinator.getFencingToken();
        coordinator.globalRecovery();

        String newToken = coordinator.getFencingToken();
        assertNotEquals(oldToken, newToken);
        assertTrue(coordinator.isRunning());
    }

    @Test
    void testStopCleansUp() {
        coordinator.start();
        assertTrue(coordinator.isRunning());
        coordinator.stop();
        assertFalse(coordinator.isRunning());
    }

    // ==================== Mocks ====================

    static class MockClusterRegistry implements ClusterRegistry {
        final Map<String, NodeInfo> nodes = new ConcurrentHashMap<>();
        final Map<String, io.nop.stream.runtime.cluster.CoordinatorInfo> coordinators = new ConcurrentHashMap<>();

        @Override
        public void registerCoordinator(String jobId, String coordinatorId, String fencingToken) {
            coordinators.put(jobId, new io.nop.stream.runtime.cluster.CoordinatorInfo(
                    jobId, coordinatorId, fencingToken, System.currentTimeMillis()));
        }

        @Override
        public io.nop.stream.runtime.cluster.CoordinatorInfo getActiveCoordinator(String jobId) {
            return coordinators.get(jobId);
        }

        @Override
        public void registerNode(String nodeId, String endpoint, int capacity) {
            nodes.put(nodeId, new NodeInfo(nodeId, endpoint, capacity,
                    System.currentTimeMillis(), System.currentTimeMillis()));
        }

        @Override
        public boolean renewLease(String nodeId, long leaseTimeoutMs) {
            return nodes.containsKey(nodeId);
        }

        @Override
        public io.nop.stream.runtime.cluster.LeaseInfo getNodeLease(String nodeId) {
            return null;
        }

        @Override
        public List<NodeInfo> getActiveNodes() {
            return new ArrayList<>(nodes.values());
        }

        @Override
        public void assignTask(String jobId, String vertexId, int subtaskIndex,
                               String nodeId, String attemptId, String fencingToken) {
            // no-op in mock
        }

        @Override
        public TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
            return null;
        }

        @Override
        public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
            // no-op in mock
        }
    }

    static class MockMessageService implements IMessageService {
        final List<Object> sentMessages = new CopyOnWriteArrayList<>();

        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
            return new IMessageSubscription() {
                @Override public void cancel() {}
                @Override public boolean isSuspended() { return false; }
                @Override public boolean isCancelled() { return false; }
                @Override public void suspend() {}
                @Override public void resume() {}
            };
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            sentMessages.add(message);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
