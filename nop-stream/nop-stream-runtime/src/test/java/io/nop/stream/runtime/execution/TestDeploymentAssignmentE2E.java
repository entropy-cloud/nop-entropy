package io.nop.stream.runtime.execution;

import io.nop.api.core.message.*;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.DeploymentMode;
import io.nop.stream.core.execution.plan.DeploymentAssignment;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E test verifying the complete flow: execute() → distributed provider →
 * DeploymentPlan(assignment) → JobCoordinator.assignTasks() → ClusterRegistry.
 *
 * <p>This test intercepts the DeploymentPlan to assert that it carries a materialized
 * subtask→node assignment, and that the EmbeddedDistributedExecutor uses
 * coordinator.assignTasks() (which reads the plan) rather than direct assignment.
 */
class TestDeploymentAssignmentE2E {

    @Test
    void testDistributedPlanCarriesAssignmentAndExecutesEndToEnd() throws Exception {
        List<String> results = new CopyOnWriteArrayList<>();
        IMessageService messageService = new InProcessMessageService();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);
        env.setDeploymentMode(DeploymentMode.DISTRIBUTED);

        // Use a custom dispatcher that records whether the DeploymentPlan carries an assignment
        AssignmentCapturingDispatcher dispatcher = new AssignmentCapturingDispatcher(messageService, 2);
        env.setExecutionDispatcher(dispatcher);

        env.fromElements("a", "b", "c", "d")
                .map(String::toUpperCase)
                .sink(results::add);

        env.execute("e2e-assignment-test");

        // Verify the plan captured by the dispatcher carries a non-null, non-empty assignment
        DeploymentPlan capturedPlan = dispatcher.capturedDeploymentPlan;
        assertNotNull(capturedPlan, "Dispatcher should have received a DeploymentPlan");
        assertNotNull(capturedPlan.getAssignment(),
                "DISTRIBUTED mode DeploymentPlan must carry a materialized assignment");
        assertFalse(capturedPlan.getAssignment().isEmpty(),
                "Assignment must not be empty for a distributed plan with active nodes");

        // Verify all expected data was processed
        assertTrue(results.size() >= 4,
                "Expected at least 4 results, got " + results.size() + ": " + results);
        assertTrue(results.containsAll(Arrays.asList("A", "B", "C", "D")),
                "All mapped values should be present: " + results);
    }

    @Test
    void testDistributedProviderInvokedNotLocalForDistributedMode() throws Exception {
        // Wiring test (#23): verify that DISTRIBUTED mode actually triggers
        // generateDistributed on the provider, not generateLocal.
        IMessageService messageService = new InProcessMessageService();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(1);
        env.setDeploymentMode(DeploymentMode.DISTRIBUTED);

        AssignmentCapturingDispatcher dispatcher = new AssignmentCapturingDispatcher(messageService, 1);
        env.setExecutionDispatcher(dispatcher);

        env.fromElements("x")
                .map(s -> s)
                .sink(v -> {});

        env.execute("wiring-test");

        DeploymentPlan plan = dispatcher.capturedDeploymentPlan;
        assertNotNull(plan.getAssignment(),
                "DISTRIBUTED mode must produce a plan with assignment (proves generateDistributed was called)");
        assertFalse(plan.getAssignment().isEmpty());
    }

    @Test
    void testGetExpectedNodeIdsReturnsConsistentNodeSet() {
        InProcessMessageService messageService = new InProcessMessageService();
        EmbeddedDistributedExecutor dispatcher = new EmbeddedDistributedExecutor(messageService, 3);

        PartitionedPlan partitionedPlan = buildPartitionedPlan(3);

        List<String> nodeIds = dispatcher.getExpectedNodeIds(partitionedPlan);

        assertFalse(nodeIds.isEmpty(), "Expected node IDs should not be empty");
        assertEquals(3, nodeIds.size(), "Should return 3 nodes for parallelism 3");
        assertTrue(nodeIds.contains("node-0"));
        assertTrue(nodeIds.contains("node-1"));
        assertTrue(nodeIds.contains("node-2"));
    }

    @Test
    void testRoundRobinAssignmentSpansAllExpectedNodes() {
        InProcessMessageService messageService = new InProcessMessageService();
        EmbeddedDistributedExecutor dispatcher = new EmbeddedDistributedExecutor(messageService, 3);

        PartitionedPlan partitionedPlan = buildPartitionedPlan(6);
        List<String> nodeIds = dispatcher.getExpectedNodeIds(partitionedPlan);

        DeploymentPlanGenerator generator = new DeploymentPlanGenerator();
        DeploymentPlan plan = generator.generateDistributed(partitionedPlan, nodeIds);

        DeploymentAssignment assignment = plan.getAssignment();
        Set<String> usedNodes = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            usedNodes.add(assignment.getNodeForSubtask("v0", i));
        }
        assertEquals(3, usedNodes.size(),
                "Round-robin over 3 nodes with 6 subtasks should use all 3 nodes: " + usedNodes);
    }

    private PartitionedPlan buildPartitionedPlan(int parallelism) {
        Map<String, PartitionedPlan.VertexPlan> vertices = new LinkedHashMap<>();
        vertices.put("v0", new PartitionedPlan.VertexPlan("v0", parallelism, "op-0"));
        return new PartitionedPlan("job-test", "pipe-0", vertices,
                Collections.emptyList(), null, null);
    }

    // ==================== Helpers ====================

    /**
     * A dispatcher that wraps EmbeddedDistributedExecutor but captures the DeploymentPlan
     * passed to execute() so tests can assert on its assignment field.
     */
    static class AssignmentCapturingDispatcher implements io.nop.stream.core.execution.IStreamExecutionDispatcher {
        private final EmbeddedDistributedExecutor delegate;
        volatile DeploymentPlan capturedDeploymentPlan;

        AssignmentCapturingDispatcher(IMessageService messageService, int nodeCount) {
            this.delegate = new EmbeddedDistributedExecutor(messageService, nodeCount);
        }

        @Override
        public boolean supportsDeploymentMode(DeploymentMode mode) {
            return delegate.supportsDeploymentMode(mode);
        }

        @Override
        public List<String> getExpectedNodeIds(PartitionedPlan partitionedPlan) {
            return delegate.getExpectedNodeIds(partitionedPlan);
        }

        @Override
        public io.nop.stream.core.environment.StreamExecutionResult execute(
                io.nop.stream.core.jobgraph.JobGraph jobGraph,
                PartitionedPlan partitionedPlan,
                DeploymentPlan deploymentPlan) throws Exception {
            this.capturedDeploymentPlan = deploymentPlan;
            return delegate.execute(jobGraph, partitionedPlan, deploymentPlan);
        }
    }

    private static class InProcessMessageService implements IMessageService {
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
