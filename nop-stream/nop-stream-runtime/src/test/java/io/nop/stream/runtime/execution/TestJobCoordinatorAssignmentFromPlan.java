package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.execution.plan.DeploymentAssignment;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.coordinator.JobCoordinator;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.taskmanager.CheckpointAckMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that {@link JobCoordinator#assignTasks()} consumes the materialized
 * subtask→node mapping from the {@link DeploymentPlan}'s {@link DeploymentAssignment}
 * rather than performing its own runtime round-robin.
 */
class TestJobCoordinatorAssignmentFromPlan {

    private static final String JOB_ID = "test-job-plan-assignment";

    @TempDir
    Path tempDir;

    private InMemoryClusterRegistry clusterRegistry;
    private CheckpointCoordinator checkpointCoordinator;
    private RecordingTaskRpc node0Rpc;
    private RecordingTaskRpc node1Rpc;
    private Map<String, IStreamTaskRpcService> taskRpcServices;

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

        clusterRegistry.registerNode("node-0", "localhost:9000", 4);
        clusterRegistry.registerNode("node-1", "localhost:9001", 4);

        node0Rpc = new RecordingTaskRpc();
        node1Rpc = new RecordingTaskRpc();
        taskRpcServices = new LinkedHashMap<>();
        taskRpcServices.put("node-0", node0Rpc);
        taskRpcServices.put("node-1", node1Rpc);
    }

    private PartitionedPlan buildPlan(int... parallelisms) {
        Map<String, PartitionedPlan.VertexPlan> vertices = new LinkedHashMap<>();
        List<PartitionedPlan.EdgePlan> edges = new ArrayList<>();
        String prev = null;
        for (int i = 0; i < parallelisms.length; i++) {
            String vertexId = "v" + i;
            vertices.put(vertexId, new PartitionedPlan.VertexPlan(vertexId, parallelisms[i], "op-" + i));
            if (prev != null) {
                edges.add(new PartitionedPlan.EdgePlan(prev, vertexId, PartitionPolicy.FORWARD));
            }
            prev = vertexId;
        }
        return new PartitionedPlan(JOB_ID, "pipeline-0", vertices, edges, null, null);
    }

    private DeploymentPlan buildPlanWithAssignment(PartitionedPlan partitionedPlan,
                                                    Map<String, List<String>> mapping) {
        return new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan,
                "remote", "memory", "local", null, null,
                new DeploymentAssignment(mapping));
    }

    private DeploymentPlan buildPlanWithoutAssignment(PartitionedPlan partitionedPlan) {
        return new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);
    }

    @Test
    void testAssignTasksConsumesMaterializedAssignment() {
        PartitionedPlan partitionedPlan = buildPlan(2, 2);

        // Deliberately map all subtasks to node-1, which differs from what
        // runtime round-robin would produce (round-robin would split across node-0/node-1).
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        mapping.put("v0", Arrays.asList("node-1", "node-1"));
        mapping.put("v1", Arrays.asList("node-1", "node-1"));

        DeploymentPlan plan = buildPlanWithAssignment(partitionedPlan, mapping);

        JobCoordinator coordinator = new JobCoordinator(
                JOB_ID, "coordinator-1", plan,
                clusterRegistry, checkpointCoordinator, taskRpcServices);

        coordinator.start();
        coordinator.assignTasks();

        // All 4 tasks should be on node-1 (matching the materialized assignment, NOT runtime round-robin)
        assertTrue(node0Rpc.assignments.isEmpty(),
                "node-0 should receive no assignments when the plan maps everything to node-1");
        assertEquals(4, node1Rpc.assignments.size(),
                "node-1 should receive all 4 assignments");

        // Verify the assignments match the materialized mapping
        Map<String, List<TaskAssignment>> assignments = coordinator.getTaskAssignments();
        assertEquals("node-1", assignments.get("v0").get(0).getNodeId());
        assertEquals("node-1", assignments.get("v0").get(1).getNodeId());
        assertEquals("node-1", assignments.get("v1").get(0).getNodeId());
        assertEquals("node-1", assignments.get("v1").get(1).getNodeId());

        // Verify assignments were recorded in ClusterRegistry
        assertEquals("node-1", clusterRegistry.getTaskAssignment(JOB_ID, "v0", 0).getNodeId());
        assertEquals("node-1", clusterRegistry.getTaskAssignment(JOB_ID, "v1", 1).getNodeId());

        coordinator.stop();
    }

    @Test
    void testAssignTasksFallsBackToRuntimeRoundRobinWhenNoAssignment() {
        PartitionedPlan partitionedPlan = buildPlan(2);
        DeploymentPlan plan = buildPlanWithoutAssignment(partitionedPlan);

        JobCoordinator coordinator = new JobCoordinator(
                JOB_ID, "coordinator-1", plan,
                clusterRegistry, checkpointCoordinator, taskRpcServices);

        coordinator.start();
        coordinator.assignTasks();

        // With runtime round-robin over 2 nodes, subtask 0 and 1 go to different nodes
        Map<String, List<TaskAssignment>> assignments = coordinator.getTaskAssignments();
        assertEquals(2, assignments.get("v0").size());
        String node0 = assignments.get("v0").get(0).getNodeId();
        String node1 = assignments.get("v0").get(1).getNodeId();
        assertNotEquals(node0, node1,
                "Runtime round-robin should distribute across different nodes");

        // Both RPC services should have received assignments (one each via round-robin)
        assertEquals(2, node0Rpc.assignments.size() + node1Rpc.assignments.size(),
                "Fallback should send assignments to task managers (2 subtasks → 2 assignments)");
        assertEquals(1, node0Rpc.assignments.size(),
                "Each node should get exactly 1 assignment via round-robin");
        assertEquals(1, node1Rpc.assignments.size());

        coordinator.stop();
    }

    @Test
    void testAssignTasksWiringPlanAssignmentIsConsumed() {
        // Wiring test (#23): verify assignTasks reads the DeploymentPlan mapping
        // by using an assignment that is deliberately incompatible with runtime nodes.
        // If the plan's assignment is NOT consumed, the test would either fail
        // (trying to find an RPC for a node not in taskRpcServices) or produce
        // a different distribution.
        PartitionedPlan partitionedPlan = buildPlan(2);

        // Map to specific nodes that ARE registered, but in a non-round-robin pattern
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        mapping.put("v0", Arrays.asList("node-0", "node-0"));  // both on node-0

        DeploymentPlan plan = buildPlanWithAssignment(partitionedPlan, mapping);

        JobCoordinator coordinator = new JobCoordinator(
                JOB_ID, "coordinator-1", plan,
                clusterRegistry, checkpointCoordinator, taskRpcServices);

        coordinator.start();
        coordinator.assignTasks();

        Map<String, List<TaskAssignment>> assignments = coordinator.getTaskAssignments();
        assertEquals("node-0", assignments.get("v0").get(0).getNodeId());
        assertEquals("node-0", assignments.get("v0").get(1).getNodeId());

        // node-0 RPC received both, node-1 received none
        assertEquals(2, node0Rpc.assignments.size());
        assertEquals(0, node1Rpc.assignments.size());

        coordinator.stop();
    }

    @Test
    void testAssignTasksThrowsOnIncompleteAssignment() {
        PartitionedPlan partitionedPlan = buildPlan(2);

        // Assignment only covers subtask 0, not subtask 1 — should fail explicitly
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        mapping.put("v0", Collections.singletonList("node-0"));

        DeploymentPlan plan = buildPlanWithAssignment(partitionedPlan, mapping);

        JobCoordinator coordinator = new JobCoordinator(
                JOB_ID, "coordinator-1", plan,
                clusterRegistry, checkpointCoordinator, taskRpcServices);

        coordinator.start();

        assertThrows(io.nop.stream.core.exceptions.StreamException.class,
                coordinator::assignTasks,
                "Incomplete assignment must fail explicitly, not silently skip");

        coordinator.stop();
    }

    // ==================== Mocks ====================

    static class RecordingTaskRpc implements IStreamTaskRpcService {
        final List<TaskAssignment> assignments = new CopyOnWriteArrayList<>();
        final AtomicReference<CheckpointBarrier> lastBarrier = new AtomicReference<>();

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
            assignments.add(assignment);
        }

        @Override
        public void triggerCheckpoint(CheckpointBarrier barrier, long fencingEpoch) {
            lastBarrier.set(barrier);
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
        }
    }
}
