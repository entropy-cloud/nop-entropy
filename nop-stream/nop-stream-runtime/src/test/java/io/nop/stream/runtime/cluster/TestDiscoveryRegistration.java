package io.nop.stream.runtime.cluster;

import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.naming.INamingService;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.DeploymentMode;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.runtime.execution.EmbeddedDistributedExecutor;
import io.nop.api.core.message.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for platform discovery integration (G51):
 * - StreamNodeAutoRegistration registers nodes with INamingService
 * - ServiceInstance field mapping (capacity → weight/metadata)
 * - Wiring: registerInstance called, not IDiscoveryClient.getInstances
 * - Two-view consistency: discovery nodes = ClusterRegistry active nodes
 * - Coexistence: ClusterRegistry-based assignment/failure detection still works
 */
class TestDiscoveryRegistration {

    @Test
    void testStreamNodeAutoRegistrationRegistersWithNamingService() {
        RecordingNamingService namingService = new RecordingNamingService();

        StreamNodeAutoRegistration reg = new StreamNodeAutoRegistration(
                namingService, "node-test", "host1:9090", 16);

        reg.start();

        assertEquals(1, namingService.allRegistered.size(),
                "registerInstance should have been called once");
        assertEquals(0, namingService.unregistered.size(),
                "unregisterInstance should not have been called yet");

        ServiceInstance svc = namingService.allRegistered.get(0);
        assertEquals("node-test", svc.getInstanceId());
        assertEquals(StreamNodeAutoRegistration.SERVICE_NAME, svc.getServiceName());
        assertEquals("host1", svc.getAddr());
        assertEquals(9090, svc.getPort());
        assertEquals(16, svc.getWeight(),
                "capacity should map to weight");
        assertEquals("16", svc.getMetadata().get(StreamNodeAutoRegistration.META_CAPACITY),
                "capacity should be in metadata");

        reg.stop();

        assertEquals(1, namingService.unregistered.size(),
                "unregisterInstance should have been called on stop");
    }

    @Test
    void testWiringUsesRegisterInstanceNotDiscoveryRead() {
        // Wiring test (#23): verify registerInstance is called (INamingService write),
        // NOT getInstances (IDiscoveryClient read).
        RecordingNamingService namingService = new RecordingNamingService();

        StreamNodeAutoRegistration reg = new StreamNodeAutoRegistration(
                namingService, "node-w", "host:1234", 8);

        reg.start();
        reg.stop();

        assertTrue(namingService.registerInstanceCount.get() > 0,
                "registerInstance must be called (INamingService write path)");
        assertEquals(0, namingService.getInstancesCount.get(),
                "IDiscoveryClient.getInstances must NOT be used for registration");
    }

    @Test
    void testRegistrationFailurePropagatesNotSwallowed() {
        // No-silent-skip test (#24): registration failure must throw, not catch{}.
        INamingService failingNamingService = new INamingService() {
            @Override
            public void registerInstance(ServiceInstance instance) {
                throw new StreamException("discovery backend unavailable");
            }

            @Override
            public void unregisterInstance(ServiceInstance instance) {
            }

            @Override
            public List<String> getServices() {
                return Collections.emptyList();
            }

            @Override
            public List<ServiceInstance> getInstances(String serviceName) {
                return Collections.emptyList();
            }
        };

        StreamNodeAutoRegistration reg = new StreamNodeAutoRegistration(
                failingNamingService, "node-fail", "host:1234", 4);

        assertThrows(StreamException.class, reg::start,
                "Registration failure must propagate, not be silently swallowed");
    }

    @Test
    void testEndpointParsing() {
        RecordingNamingService namingService = new RecordingNamingService();

        StreamNodeAutoRegistration reg1 = new StreamNodeAutoRegistration(
                namingService, "n1", "192.168.1.1:8080", 4);
        reg1.start();
        ServiceInstance svc1 = namingService.allRegistered.get(0);
        assertEquals("192.168.1.1", svc1.getAddr());
        assertEquals(8080, svc1.getPort());
        reg1.stop();

        StreamNodeAutoRegistration reg2 = new StreamNodeAutoRegistration(
                namingService, "n2", "embedded:node-0", 4);
        reg2.start();
        ServiceInstance svc2 = namingService.allRegistered.get(1);
        // The endpoint "embedded:node-0" parses host="embedded", port=0 (not a number)
        assertEquals("embedded", svc2.getAddr());
        reg2.stop();
    }

    @Test
    void testE2EDiscoveryRegistrationWithDistributedExecution() throws Exception {
        // E2E: execute() with discovery registration → nodes discoverable via INamingService
        RecordingNamingService namingService = new RecordingNamingService();
        InProcessMessageService messageService = new InProcessMessageService();

        List<String> results = new CopyOnWriteArrayList<>();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);
        env.setDeploymentMode(DeploymentMode.DISTRIBUTED);
        env.setExecutionDispatcher(new EmbeddedDistributedExecutor(messageService, 2, 60, namingService));

        env.fromElements("a", "b", "c", "d")
                .map(String::toUpperCase)
                .sink(results::add);

        env.execute("discovery-e2e-test");

        // After execution, nodes were registered and unregistered
        assertTrue(namingService.registerInstanceCount.get() >= 2,
                "At least 2 nodes should have been registered with discovery");

        // All registered instances should be nop-stream service
        for (ServiceInstance svc : namingService.allRegistered) {
            assertEquals(StreamNodeAutoRegistration.SERVICE_NAME, svc.getServiceName());
        }

        // After execution completes, all should be unregistered
        assertEquals(namingService.registerInstanceCount.get(), namingService.unregistered.size(),
                "All registered nodes should be unregistered after execution");

        // Data was still processed correctly (coexistence)
        assertTrue(results.size() >= 4,
                "All data should be processed even with discovery registration: " + results);
    }

    @Test
    void testTwoViewConsistencyDiscoveryVsClusterRegistry() {
        // Two-view consistency (#23): nodes registered with discovery should match
        // nodes in ClusterRegistry active set during their lifetime.
        RecordingNamingService namingService = new RecordingNamingService();
        InMemoryClusterRegistry clusterRegistry = new InMemoryClusterRegistry();

        // Register a node in ClusterRegistry (simulating TaskManager.start())
        clusterRegistry.registerNode("node-0", "host:9000", 16);
        // Register the same node with discovery
        StreamNodeAutoRegistration reg = new StreamNodeAutoRegistration(
                namingService, "node-0", "host:9000", 16);
        reg.start();

        // Both views should contain the node
        List<String> discoveryNodeIds = extractInstanceIds(namingService.getInstances(StreamNodeAutoRegistration.SERVICE_NAME));
        Set<String> registryNodeIds = new HashSet<>();
        for (NodeInfo ni : clusterRegistry.getActiveNodes()) {
            registryNodeIds.add(ni.getNodeId());
        }

        assertTrue(discoveryNodeIds.contains("node-0"),
                "Discovery should contain the registered node");
        assertTrue(registryNodeIds.contains("node-0"),
                "ClusterRegistry should contain the active node");

        // After unregister, discovery should no longer contain the node
        reg.stop();
        List<String> discoveryAfter = extractInstanceIds(
                namingService.getInstances(StreamNodeAutoRegistration.SERVICE_NAME));
        assertFalse(discoveryAfter.contains("node-0"),
                "Discovery should not contain the node after unregister");
    }

    @Test
    void testCoexistenceClusterRegistryAssignmentStillWorks() throws Exception {
        // Coexistence: existing ClusterRegistry-based assignment path still works
        // when no naming service is provided (backward compatible).
        InProcessMessageService messageService = new InProcessMessageService();

        List<String> results = new CopyOnWriteArrayList<>();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);
        env.setDeploymentMode(DeploymentMode.DISTRIBUTED);
        // No naming service — discovery registration skipped, ClusterRegistry path intact
        env.setExecutionDispatcher(new EmbeddedDistributedExecutor(messageService, 2));

        env.fromElements("x", "y", "z")
                .map(String::toUpperCase)
                .sink(results::add);

        env.execute("coexistence-no-discovery");

        assertTrue(results.containsAll(Arrays.asList("X", "Y", "Z")),
                "ClusterRegistry-based execution should still work without discovery: " + results);
    }

    private List<String> extractInstanceIds(List<ServiceInstance> instances) {
        List<String> ids = new ArrayList<>();
        for (ServiceInstance svc : instances) {
            ids.add(svc.getInstanceId());
        }
        return ids;
    }

    // ==================== Mocks ====================

    static class RecordingNamingService implements INamingService {
        final List<ServiceInstance> allRegistered = new CopyOnWriteArrayList<>();
        final List<ServiceInstance> unregistered = new CopyOnWriteArrayList<>();
        final Set<String> currentlyRegistered = Collections.synchronizedSet(new LinkedHashSet<>());
        final AtomicInteger registerInstanceCount = new AtomicInteger(0);
        final AtomicInteger getInstancesCount = new AtomicInteger(0);

        @Override
        public void registerInstance(ServiceInstance instance) {
            allRegistered.add(instance);
            currentlyRegistered.add(instance.getInstanceId());
            registerInstanceCount.incrementAndGet();
        }

        @Override
        public void unregisterInstance(ServiceInstance instance) {
            unregistered.add(instance);
            currentlyRegistered.remove(instance.getInstanceId());
        }

        @Override
        public List<String> getServices() {
            return Collections.singletonList(StreamNodeAutoRegistration.SERVICE_NAME);
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceName) {
            getInstancesCount.incrementAndGet();
            List<ServiceInstance> result = new ArrayList<>();
            for (ServiceInstance svc : allRegistered) {
                if (currentlyRegistered.contains(svc.getInstanceId())) {
                    result.add(svc);
                }
            }
            result.sort(Comparator.comparing(ServiceInstance::getInstanceId));
            return result;
        }
    }

    static class InProcessMessageService implements IMessageService {
        private final Map<String, List<IMessageConsumer>> subscribers = new HashMap<>();

        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
            subscribers.computeIfAbsent(topic, k -> new ArrayList<>()).add(listener);
            return new IMessageSubscription() {
                @Override public void cancel() {
                    subscribers.getOrDefault(topic, Collections.emptyList()).remove(listener);
                }
                @Override public boolean isSuspended() { return false; }
                @Override public boolean isCancelled() { return false; }
                @Override public void suspend() {}
                @Override public void resume() {}
            };
        }

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            List<IMessageConsumer> consumers = subscribers.get(topic);
            if (consumers != null) {
                for (IMessageConsumer consumer : new ArrayList<>(consumers)) {
                    consumer.onMessage(topic, message, null);
                }
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}
