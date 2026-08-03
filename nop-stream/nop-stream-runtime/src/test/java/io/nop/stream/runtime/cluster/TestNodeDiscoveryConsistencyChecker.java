package io.nop.stream.runtime.cluster;

import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 41 D7 (Option B) — tests for {@link NodeDiscoveryConsistencyChecker},
 * the genuine consumer of the platform discovery <strong>read</strong> direction
 * ({@link IDiscoveryClient#getInstances}).
 *
 * <p>Covers:
 * <ul>
 *   <li>consistency when both views agree;</li>
 *   <li>drift detection (instances only in discovery / nodes only in registry);</li>
 *   <li>{@code assertConsistent()} fail-loud on drift (plan guide #24 — no silent
 *       no-op);</li>
 *   <li>wiring verification (plan guide #23): {@code IDiscoveryClient.getInstances}
 *       is actually invoked (call-count assertion), proving the read path is not a
 *       dead contract surface;</li>
 *   <li>empty views report consistent (no false drift on cold start).</li>
 * </ul>
 */
class TestNodeDiscoveryConsistencyChecker {

    @Test
    void testConsistentWhenBothViewsAgree() {
        RecordingDiscovery discovery = new RecordingDiscovery(Arrays.asList("node-0", "node-1"));
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        registry.registerNode("node-0", "host:9000", 4);
        registry.registerNode("node-1", "host:9001", 4);

        NodeDiscoveryConsistencyChecker checker = new NodeDiscoveryConsistencyChecker(discovery, registry);

        NodeDiscoveryConsistencyChecker.ConsistencyReport report = checker.check();

        assertTrue(report.isConsistent(), "views agree → consistent: " + report);
        assertTrue(report.getOnlyInDiscovery().isEmpty());
        assertTrue(report.getOnlyInRegistry().isEmpty());
        assertEquals(set("node-0", "node-1"), report.getDiscoveryInstanceIds());
        assertEquals(set("node-0", "node-1"), report.getRegistryNodeIds());
    }

    @Test
    void testWiringDiscoveryReadPathActuallyInvoked() {
        // Wiring test (#23): getInstances must be called — the read direction is
        // genuinely consumed, not a dead contract surface.
        RecordingDiscovery discovery = new RecordingDiscovery(Collections.emptyList());
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();

        NodeDiscoveryConsistencyChecker checker = new NodeDiscoveryConsistencyChecker(discovery, registry);

        checker.check();

        assertEquals(1, discovery.getInstancesCount.get(),
                "IDiscoveryClient.getInstances must be invoked by check() (read direction consumed)");
        assertEquals(StreamNodeAutoRegistration.SERVICE_NAME, discovery.lastServiceName,
                "checker must query the nop-stream service name");
    }

    @Test
    void testDriftInstanceOnlyInDiscovery() {
        RecordingDiscovery discovery = new RecordingDiscovery(Arrays.asList("node-0", "ghost"));
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        registry.registerNode("node-0", "host:9000", 4);

        NodeDiscoveryConsistencyChecker checker = new NodeDiscoveryConsistencyChecker(discovery, registry);

        NodeDiscoveryConsistencyChecker.ConsistencyReport report = checker.check();

        assertFalse(report.isConsistent(), "ghost instance in discovery → drift");
        assertEquals(set("ghost"), report.getOnlyInDiscovery(),
                "ghost should be flagged as only-in-discovery");
        assertTrue(report.getOnlyInRegistry().isEmpty());
    }

    @Test
    void testDriftNodeOnlyInRegistry() {
        RecordingDiscovery discovery = new RecordingDiscovery(Collections.singletonList("node-0"));
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        registry.registerNode("node-0", "host:9000", 4);
        registry.registerNode("node-1", "host:9001", 4); // not in discovery

        NodeDiscoveryConsistencyChecker checker = new NodeDiscoveryConsistencyChecker(discovery, registry);

        NodeDiscoveryConsistencyChecker.ConsistencyReport report = checker.check();

        assertFalse(report.isConsistent(), "node missing from discovery → drift");
        assertEquals(set("node-1"), report.getOnlyInRegistry(),
                "unregistered node should be flagged as only-in-registry");
        assertTrue(report.getOnlyInDiscovery().isEmpty());
    }

    @Test
    void testAssertConsistentThrowsOnDrift() {
        // No-silent-skip test (#24): drift must fail loud, never silently return.
        RecordingDiscovery discovery = new RecordingDiscovery(Arrays.asList("node-0", "leak"));
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        registry.registerNode("node-0", "host:9000", 4);
        // "leak" is in discovery but not in registry → drift

        NodeDiscoveryConsistencyChecker checker = new NodeDiscoveryConsistencyChecker(discovery, registry);

        StreamException ex = assertThrows(StreamException.class, checker::assertConsistent,
                "assertConsistent must fail loud on drift");
        assertTrue(ex.getMessage().contains("drift") || ex.getMessage().contains("leak"),
                "error message should describe the drift: " + ex.getMessage());
    }

    @Test
    void testAssertConsistentPassesWhenConsistent() {
        RecordingDiscovery discovery = new RecordingDiscovery(Arrays.asList("node-0", "node-1"));
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        registry.registerNode("node-0", "host:9000", 4);
        registry.registerNode("node-1", "host:9001", 4);

        NodeDiscoveryConsistencyChecker checker = new NodeDiscoveryConsistencyChecker(discovery, registry);

        assertDoesNotThrow(checker::assertConsistent,
                "assertConsistent must not throw when views agree");
    }

    @Test
    void testEmptyViewsReportConsistent() {
        // Cold start: no nodes registered anywhere should not be reported as drift.
        RecordingDiscovery discovery = new RecordingDiscovery(Collections.emptyList());
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();

        NodeDiscoveryConsistencyChecker checker = new NodeDiscoveryConsistencyChecker(discovery, registry);

        NodeDiscoveryConsistencyChecker.ConsistencyReport report = checker.check();

        assertTrue(report.isConsistent(), "empty/empty should be consistent (no false drift)");
    }

    @Test
    void testBidirectionalDriftReportedTogether() {
        RecordingDiscovery discovery = new RecordingDiscovery(Arrays.asList("node-0", "extra-d"));
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        registry.registerNode("node-0", "host:9000", 4);
        registry.registerNode("extra-r", "host:9001", 4);

        NodeDiscoveryConsistencyChecker checker = new NodeDiscoveryConsistencyChecker(discovery, registry);

        NodeDiscoveryConsistencyChecker.ConsistencyReport report = checker.check();

        assertFalse(report.isConsistent());
        assertEquals(set("extra-d"), report.getOnlyInDiscovery());
        assertEquals(set("extra-r"), report.getOnlyInRegistry());
    }

    private static Set<String> set(String... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    // ==================== Mocks ====================

    static class RecordingDiscovery implements IDiscoveryClient {
        final java.util.concurrent.atomic.AtomicInteger getInstancesCount = new java.util.concurrent.atomic.AtomicInteger();
        volatile String lastServiceName;
        private final List<ServiceInstance> instances;

        RecordingDiscovery(List<String> instanceIds) {
            this.instances = new ArrayList<>();
            for (String id : instanceIds) {
                ServiceInstance svc = new ServiceInstance();
                svc.setInstanceId(id);
                svc.setServiceName(StreamNodeAutoRegistration.SERVICE_NAME);
                this.instances.add(svc);
            }
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceName) {
            getInstancesCount.incrementAndGet();
            lastServiceName = serviceName;
            return new ArrayList<>(instances);
        }

        @Override
        public List<String> getServices() {
            return Collections.singletonList(StreamNodeAutoRegistration.SERVICE_NAME);
        }
    }
}
