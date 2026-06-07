package io.nop.stream.runtime.cluster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestInMemoryClusterRegistryFixes {

    private InMemoryClusterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryClusterRegistry();
    }

    @Test
    void testEvictExpiredNodesRemovesStaleNodes() {
        registry.registerNode("node-1", "host1:8080", 4);
        registry.registerNode("node-2", "host2:8080", 2);

        long expiredTime = System.currentTimeMillis() - InMemoryClusterRegistry.LEASE_TIMEOUT_MS - 1000;
        registry.setLeaseTimestampForTest("node-1", expiredTime);

        registry.evictExpiredNodes();

        List<NodeInfo> active = registry.getActiveNodes();
        assertEquals(1, active.size());
        assertEquals("node-2", active.get(0).getNodeId());
    }

    @Test
    void testEvictExpiredNodesKeepsActiveNodes() {
        registry.registerNode("node-1", "host1:8080", 4);
        registry.evictExpiredNodes();
        List<NodeInfo> active = registry.getActiveNodes();
        assertEquals(1, active.size());
    }

    @Test
    void testGetNodeLeaseActiveForFreshNode() {
        registry.registerNode("node-1", "host1:8080", 4);
        LeaseInfo lease = registry.getNodeLease("node-1");
        assertNotNull(lease);
        assertTrue(lease.isActive());
    }

    @Test
    void testGetNodeLeaseInactiveForExpiredNode() {
        registry.registerNode("node-1", "host1:8080", 4);

        long expiredTime = System.currentTimeMillis() - InMemoryClusterRegistry.LEASE_TIMEOUT_MS - 1000;
        registry.setLeaseTimestampForTest("node-1", expiredTime);

        LeaseInfo lease = registry.getNodeLease("node-1");
        assertNotNull(lease);
        assertFalse(lease.isActive());
    }

    @Test
    void testGetNodeLeaseReturnsNullForUnknownNode() {
        assertNull(registry.getNodeLease("unknown"));
    }

    @Test
    void testRenewLeaseAtomic() throws InterruptedException {
        registry.registerNode("node-1", "host1:8080", 4);

        Thread[] threads = new Thread[10];
        boolean[] results = new boolean[10];

        for (int i = 0; i < threads.length; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                results[idx] = registry.renewLease("node-1", 15000);
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        for (boolean r : results) {
            assertTrue(r, "All renewLease calls should succeed for a registered node");
        }
    }

    @Test
    void testRenewLeaseFailsForUnregisteredNode() {
        assertFalse(registry.renewLease("nonexistent", 15000));
    }

    @Test
    void testCustomLeaseTtl() {
        InMemoryClusterRegistry shortTtl = new InMemoryClusterRegistry(100);
        shortTtl.registerNode("node-1", "host1:8080", 4);

        LeaseInfo lease = shortTtl.getNodeLease("node-1");
        assertNotNull(lease);
        assertTrue(lease.isActive());
    }

    @Test
    void testEvictAllExpiredNodes() {
        registry.registerNode("node-1", "host1:8080", 4);
        registry.registerNode("node-2", "host2:8080", 2);

        long expiredTime = System.currentTimeMillis() - InMemoryClusterRegistry.LEASE_TIMEOUT_MS - 1000;
        registry.setLeaseTimestampForTest("node-1", expiredTime);
        registry.setLeaseTimestampForTest("node-2", expiredTime);

        registry.evictExpiredNodes();

        List<NodeInfo> active = registry.getActiveNodes();
        assertTrue(active.isEmpty());
    }
}
