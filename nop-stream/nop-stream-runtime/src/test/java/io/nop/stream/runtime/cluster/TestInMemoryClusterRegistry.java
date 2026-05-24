package io.nop.stream.runtime.cluster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestInMemoryClusterRegistry {

    private InMemoryClusterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryClusterRegistry();
    }

    @Test
    void testRegisterAndGetCoordinator() {
        registry.registerCoordinator("job-1", "coord-1", "token-abc");
        CoordinatorInfo info = registry.getActiveCoordinator("job-1");
        assertNotNull(info);
        assertEquals("coord-1", info.getCoordinatorId());
        assertEquals("token-abc", info.getFencingToken());
    }

    @Test
    void testRegisterAndGetNodes() {
        registry.registerNode("node-1", "host1:8080", 4);
        registry.registerNode("node-2", "host2:8080", 2);

        List<NodeInfo> nodes = registry.getActiveNodes();
        assertEquals(2, nodes.size());
    }

    @Test
    void testRenewLease() {
        registry.registerNode("node-1", "host1:8080", 4);
        assertTrue(registry.renewLease("node-1", 15000));
        assertFalse(registry.renewLease("unknown-node", 15000));
    }

    @Test
    void testAssignAndGetTask() {
        registry.assignTask("job-1", "vertex-1", 0, "node-1", "attempt-1", "token-1");
        TaskAssignment assignment = registry.getTaskAssignment("job-1", "vertex-1", 0);
        assertNotNull(assignment);
        assertEquals("node-1", assignment.getNodeId());
        assertEquals("vertex-1", assignment.getVertexId());
        assertEquals(0, assignment.getSubtaskIndex());
    }

    @Test
    void testRemoveTaskAssignment() {
        registry.assignTask("job-1", "vertex-1", 0, "node-1", "attempt-1", "token-1");
        registry.removeTaskAssignment("job-1", "vertex-1", 0);
        assertNull(registry.getTaskAssignment("job-1", "vertex-1", 0));
    }

    @Test
    void testGetActiveNodesFiltersExpiredLeases() {
        registry.registerNode("node-1", "host1:8080", 4);
        registry.registerNode("node-2", "host2:8080", 2);

        long expiredTime = System.currentTimeMillis() - InMemoryClusterRegistry.LEASE_TIMEOUT_MS - 1000;
        registry.setLeaseTimestampForTest("node-1", expiredTime);

        List<NodeInfo> active = registry.getActiveNodes();
        assertEquals(1, active.size());
        assertEquals("node-2", active.get(0).getNodeId());
    }

    @Test
    void testGetActiveNodesEmptyWhenAllExpired() {
        registry.registerNode("node-1", "host1:8080", 4);
        registry.registerNode("node-2", "host2:8080", 2);

        long expiredTime = System.currentTimeMillis() - InMemoryClusterRegistry.LEASE_TIMEOUT_MS - 1000;
        registry.setLeaseTimestampForTest("node-1", expiredTime);
        registry.setLeaseTimestampForTest("node-2", expiredTime);

        List<NodeInfo> active = registry.getActiveNodes();
        assertTrue(active.isEmpty());
    }
}
