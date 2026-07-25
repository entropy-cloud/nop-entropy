package io.nop.stream.runtime.execution;

import io.nop.stream.core.execution.plan.DeploymentAssignment;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestDeploymentPlanGenerator {

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
        return new PartitionedPlan("job-1", "pipe-1", vertices, edges, null, null);
    }

    @Test
    void testGenerateLocalHasNoAssignment() {
        DeploymentPlanGenerator generator = new DeploymentPlanGenerator();
        DeploymentPlan plan = generator.generateLocal(buildPlan(1, 1));

        assertNull(plan.getAssignment(),
                "LOCAL plan should have no materialized assignment");
    }

    @Test
    void testGenerateDistributedRoundRobin3Nodes4Subtasks() {
        DeploymentPlanGenerator generator = new DeploymentPlanGenerator();
        PartitionedPlan partitionedPlan = buildPlan(4);

        List<String> nodeIds = Arrays.asList("node-0", "node-1", "node-2");

        DeploymentPlan plan = generator.generateDistributed(partitionedPlan, nodeIds);

        assertNotNull(plan.getAssignment());
        DeploymentAssignment assignment = plan.getAssignment();
        assertFalse(assignment.isEmpty());

        // 4 subtasks round-robin over 3 nodes: 0,1,2,0
        assertEquals("node-0", assignment.getNodeForSubtask("v0", 0));
        assertEquals("node-1", assignment.getNodeForSubtask("v0", 1));
        assertEquals("node-2", assignment.getNodeForSubtask("v0", 2));
        assertEquals("node-0", assignment.getNodeForSubtask("v0", 3));
    }

    @Test
    void testGenerateDistributedRoundRobinMultipleVertices() {
        DeploymentPlanGenerator generator = new DeploymentPlanGenerator();
        PartitionedPlan partitionedPlan = buildPlan(2, 2);

        List<String> nodeIds = Arrays.asList("node-0", "node-1");

        DeploymentPlan plan = generator.generateDistributed(partitionedPlan, nodeIds);

        DeploymentAssignment assignment = plan.getAssignment();
        assertNotNull(assignment);

        // Global round-robin: v0/0->node-0, v0/1->node-1, v1/0->node-0, v1/1->node-1
        assertEquals("node-0", assignment.getNodeForSubtask("v0", 0));
        assertEquals("node-1", assignment.getNodeForSubtask("v0", 1));
        assertEquals("node-0", assignment.getNodeForSubtask("v1", 0));
        assertEquals("node-1", assignment.getNodeForSubtask("v1", 1));
    }

    @Test
    void testGenerateDistributedSingleNodeAllSubtasksToSameNode() {
        DeploymentPlanGenerator generator = new DeploymentPlanGenerator();
        PartitionedPlan partitionedPlan = buildPlan(3);

        DeploymentPlan plan = generator.generateDistributed(partitionedPlan, Collections.singletonList("only-node"));

        DeploymentAssignment assignment = plan.getAssignment();
        assertEquals("only-node", assignment.getNodeForSubtask("v0", 0));
        assertEquals("only-node", assignment.getNodeForSubtask("v0", 1));
        assertEquals("only-node", assignment.getNodeForSubtask("v0", 2));
    }

    @Test
    void testGenerateDistributedFailsOnEmptyNodes() {
        DeploymentPlanGenerator generator = new DeploymentPlanGenerator();
        PartitionedPlan partitionedPlan = buildPlan(1);

        assertThrows(StreamException.class,
                () -> generator.generateDistributed(partitionedPlan, Collections.emptyList()),
                "Distributed generation with no active nodes must fail explicitly");

        assertThrows(StreamException.class,
                () -> generator.generateDistributed(partitionedPlan, null),
                "Distributed generation with null node list must fail explicitly");
    }

    @Test
    void testGenerateDistributedFailsOnNullPartitionedPlan() {
        DeploymentPlanGenerator generator = new DeploymentPlanGenerator();

        assertThrows(StreamException.class,
                () -> generator.generateDistributed(null, Arrays.asList("node-0")),
                "Distributed generation with null partitioned plan must fail explicitly");
    }

    @Test
    void testGenerateDistributedUsesRemoteTransport() {
        DeploymentPlanGenerator generator = new DeploymentPlanGenerator();
        DeploymentPlan plan = generator.generateDistributed(buildPlan(1), Arrays.asList("node-0"));

        assertEquals("remote", plan.getTransportBackend(),
                "Distributed plan should use remote transport backend");
    }

    @Test
    void testEverySubtaskHasAssignment() {
        DeploymentPlanGenerator generator = new DeploymentPlanGenerator();
        int parallelism = 7;
        PartitionedPlan partitionedPlan = buildPlan(parallelism);
        List<String> nodeIds = Arrays.asList("node-0", "node-1", "node-2");

        DeploymentPlan plan = generator.generateDistributed(partitionedPlan, nodeIds);

        DeploymentAssignment assignment = plan.getAssignment();
        for (int i = 0; i < parallelism; i++) {
            String node = assignment.getNodeForSubtask("v0", i);
            assertNotNull(node, "Subtask " + i + " should have an assignment");
            assertTrue(nodeIds.contains(node), "Assigned node should be in the active node set");
        }
    }
}
