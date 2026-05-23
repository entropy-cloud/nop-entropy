package io.nop.stream.core.execution.plan;

import io.nop.stream.core.model.StreamModelFingerprint;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestPartitionedPlan {

    @Test
    void testCreation() {
        Map<String, PartitionedPlan.VertexPlan> vertices = new LinkedHashMap<>();
        vertices.put("v1", new PartitionedPlan.VertexPlan("v1", 1, "source-op"));
        vertices.put("v2", new PartitionedPlan.VertexPlan("v2", 1, "sink-op"));

        List<PartitionedPlan.EdgePlan> edges = new ArrayList<>();
        edges.add(new PartitionedPlan.EdgePlan("v1", "v2", PartitionPolicy.FORWARD));

        PartitionedPlan plan = new PartitionedPlan("job1", "pipe1", vertices, edges,
                new LinkedHashSet<>(Arrays.asList("v1", "v2")), null);

        assertEquals(2, plan.getVertexPlans().size());
        assertEquals(1, plan.getEdgePlans().size());
        assertEquals(2, plan.getCheckpointAckSet().size());
    }

    @Test
    void testVertexPlanDefaults() {
        PartitionedPlan.VertexPlan vp = new PartitionedPlan.VertexPlan();
        assertEquals(1, vp.getParallelism());
    }

    @Test
    void testEdgePlanDefaultPolicy() {
        PartitionedPlan.EdgePlan ep = new PartitionedPlan.EdgePlan();
        assertEquals(PartitionPolicy.FORWARD, ep.getPartitionPolicy());
    }
}

class TestDeploymentPlan {

    @Test
    void testDefaultIsLocal() {
        DeploymentPlan plan = new DeploymentPlan();
        assertEquals("local", plan.getTransportBackend());
        assertEquals("memory", plan.getStateBackendBinding());
        assertEquals("local", plan.getCheckpointStorage());
    }
}
