package io.nop.stream.core.execution.plan;

import io.nop.stream.core.model.StreamModelFingerprint;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

class TestDeploymentAssignment {

    @Test
    void testGetNodeForSubtask() {
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        mapping.put("source", Arrays.asList("node-0", "node-1"));
        mapping.put("sink", Arrays.asList("node-1", "node-0"));

        DeploymentAssignment assignment = new DeploymentAssignment(mapping);

        assertEquals("node-0", assignment.getNodeForSubtask("source", 0));
        assertEquals("node-1", assignment.getNodeForSubtask("source", 1));
        assertEquals("node-1", assignment.getNodeForSubtask("sink", 0));
        assertEquals("node-0", assignment.getNodeForSubtask("sink", 1));
    }

    @Test
    void testNullForUnknownVertexOrIndex() {
        DeploymentAssignment assignment = new DeploymentAssignment(
                Collections.singletonMap("v1", Collections.singletonList("node-0")));

        assertNull(assignment.getNodeForSubtask("unknown", 0));
        assertNull(assignment.getNodeForSubtask("v1", -1));
        assertNull(assignment.getNodeForSubtask("v1", 99));
    }

    @Test
    void testIsEmpty() {
        assertTrue(new DeploymentAssignment().isEmpty());
        assertTrue(new DeploymentAssignment(null).isEmpty());
        assertTrue(new DeploymentAssignment(Collections.emptyMap()).isEmpty());

        assertFalse(new DeploymentAssignment(
                Collections.singletonMap("v1", Collections.singletonList("node-0"))).isEmpty());
    }

    @Test
    void testSerializationRoundTrip() throws Exception {
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        mapping.put("source", Arrays.asList("node-0", "node-1", "node-2"));
        mapping.put("sink", Arrays.asList("node-2", "node-1", "node-0"));

        DeploymentAssignment original = new DeploymentAssignment(mapping);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        DeploymentAssignment deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = (DeploymentAssignment) ois.readObject();
        }

        assertEquals(original.getVertexAssignments(), deserialized.getVertexAssignments());
        assertEquals("node-0", deserialized.getNodeForSubtask("source", 0));
        assertEquals("node-2", deserialized.getNodeForSubtask("source", 2));
        assertEquals("node-0", deserialized.getNodeForSubtask("sink", 2));
    }

    @Test
    void testImmutability() {
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        mapping.put("v1", new ArrayList<>(Arrays.asList("node-0")));

        DeploymentAssignment assignment = new DeploymentAssignment(mapping);

        // Mutating the source map should not affect the assignment
        mapping.put("v2", Arrays.asList("node-1"));
        assertNull(assignment.getNodeForSubtask("v2", 0));

        // The internal list should be unmodifiable
        assertThrows(UnsupportedOperationException.class, () ->
                assignment.getVertexAssignments().put("v3", Collections.emptyList()));
    }
}

class TestDeploymentPlan {

    @Test
    void testDefaultIsLocal() {
        DeploymentPlan plan = new DeploymentPlan();
        assertEquals("local", plan.getTransportBackend());
        assertEquals("memory", plan.getStateBackendBinding());
        assertEquals("local", plan.getCheckpointStorage());
        assertNull(plan.getAssignment());
    }

    @Test
    void testWithAssignment() {
        DeploymentAssignment assignment = new DeploymentAssignment(
                Collections.singletonMap("source", Arrays.asList("node-0", "node-1")));

        DeploymentPlan plan = new DeploymentPlan(
                "job-1", "pipe-1", null, "remote", "memory", "local",
                null, null, assignment);

        assertNotNull(plan.getAssignment());
        assertEquals("node-0", plan.getAssignment().getNodeForSubtask("source", 0));
        assertEquals("node-1", plan.getAssignment().getNodeForSubtask("source", 1));
    }

    @Test
    void testSerializationRoundTripWithAssignment() throws Exception {
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        mapping.put("source", Arrays.asList("node-0", "node-1"));
        mapping.put("sink", Arrays.asList("node-0", "node-1"));

        DeploymentAssignment assignment = new DeploymentAssignment(mapping);

        Map<String, PartitionedPlan.VertexPlan> vertices = new LinkedHashMap<>();
        vertices.put("source", new PartitionedPlan.VertexPlan("source", 2, "src-op"));
        vertices.put("sink", new PartitionedPlan.VertexPlan("sink", 2, "sink-op"));

        List<PartitionedPlan.EdgePlan> edges = new ArrayList<>();
        edges.add(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD));

        PartitionedPlan partitionedPlan = new PartitionedPlan("job-1", "pipe-1", vertices, edges, null, null);

        DeploymentPlan original = new DeploymentPlan(
                "job-1", "pipe-1", partitionedPlan, "remote", "memory", "local",
                null, null, assignment);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        DeploymentPlan deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = (DeploymentPlan) ois.readObject();
        }

        assertEquals("job-1", deserialized.getJobId());
        assertEquals("remote", deserialized.getTransportBackend());
        assertNotNull(deserialized.getAssignment());
        assertEquals("node-0", deserialized.getAssignment().getNodeForSubtask("source", 0));
        assertEquals("node-1", deserialized.getAssignment().getNodeForSubtask("sink", 1));
    }

    @Test
    void testSerializationRoundTripWithoutAssignment() throws Exception {
        DeploymentPlan original = new DeploymentPlan(
                "job-2", "pipe-2", null, "local", "memory", "local", null, null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        DeploymentPlan deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = (DeploymentPlan) ois.readObject();
        }

        assertEquals("job-2", deserialized.getJobId());
        assertEquals("local", deserialized.getTransportBackend());
        assertNull(deserialized.getAssignment());
    }
}
