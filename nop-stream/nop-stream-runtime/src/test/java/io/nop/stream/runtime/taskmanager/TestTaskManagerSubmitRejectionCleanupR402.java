/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.protocol.TaskDeploymentDescriptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.42 R4-02 W-06 (1.1-C8-009, C10-019, 红④): submit-rejection zombie
 * cleanup + taskDeployed post-positioning. When the task executor rejects the
 * {@code submit} (shutdown race, driven here by the existing
 * {@code forceTaskExecutorShutdownForTest} hook), the pre-fix paths returned /
 * threw WITHOUT cleaning up: the {@code runningTasks} entry stayed (a zombie —
 * {@code RunningTask.run} never executes, so the terminal release is
 * unreachable), one capacity permit stayed pinned, and the pre-submit
 * {@code nodeMetrics.taskDeployed()} counted a task that never ran (observation
 * distortion). Post-fix (adjudication A.3): both submit-rejection catches run
 * the same three-step cleanup as the existing build-failure branch (conditional
 * {@code remove(key, runningTask)} + cancel + CAS-guarded permit release) and
 * {@code taskDeployed()} only counts tasks whose submit succeeded.
 *
 * <p>Zero-touch face (8.41 boundary): the duplicate-reporting half of C8-009 is
 * covered by 8.41's W-02 compute generation dispatch — this pin does NOT re-pin
 * that face; {@code TestTaskManager.testDuplicateAssignmentIgnored} and the
 * {@code TestConcurrencyWindows2326} 8.41 red① assertions remain the untouched
 * owners of it.
 */
class TestTaskManagerSubmitRejectionCleanupR402 {

    private static final String CONTROL_TOPIC = "r402-control";

    private RecordingClusterRegistry clusterRegistry;
    private RecordingMessageService messageService;
    private TaskManager taskManager;

    @BeforeEach
    void setUp() {
        clusterRegistry = new RecordingClusterRegistry();
        messageService = new RecordingMessageService();
        taskManager = new TaskManager("r402-zombie-node", "localhost:9402", 4,
                messageService, clusterRegistry, CONTROL_TOPIC);
        taskManager.start();
    }

    @AfterEach
    void tearDown() {
        taskManager.stop();
    }

    private double deployedCounter() {
        return io.nop.stream.core.metrics.StreamMetricsRegistries.registry()
                .get(io.nop.stream.runtime.metrics.TaskNodeMetrics.METRIC_TASKS_DEPLOYED)
                .tag(io.nop.stream.runtime.metrics.TaskNodeMetrics.TAG_NODE_ID, "r402-zombie-node")
                .counter().count();
    }

    /** Face ①: receiveAssignment submit-rejection — in-process path, no displacement self-heal. */
    @Test
    void receiveAssignmentSubmitRejectionLeavesNoZombie() {
        taskManager.updateFencingToken(1L);
        double deployedBefore = deployedCounter();

        // Executor already terminated (running=true) → submit hits
        // RejectedExecutionException after the slot insert + permit acquire.
        taskManager.forceTaskExecutorShutdownForTest();

        taskManager.receiveAssignment(new TaskAssignment(
                "job-r402a", "vertex-a", 0, "r402-zombie-node", "attempt-a", 1L,
                System.currentTimeMillis()));

        // Post-fix: the rejection catch removes the entry (conditional), cancels
        // the never-run task, and returns the permit — the node is back to the
        // pre-submit state.
        assertEquals(0, taskManager.getRunningTaskCount(),
                "no running-task entry may survive a submit rejection");
        assertFalse(taskManager.hasRegistryEntry("job-r402a", "vertex-a", 0),
                "no registry entry (zombie) may survive a submit rejection");
        assertEquals(4, taskManager.availablePermits(),
                "the pinned capacity permit must be returned (accounting back to pre-submit)");
        assertEquals(deployedBefore, deployedCounter(),
                "a task that never ran (zombie) must not be counted as deployed");
    }

    /** Face ②: deployTask submit-rejection — remote path, typed rejectDeploy propagation kept. */
    @Test
    void deployTaskSubmitRejectionLeavesNoZombieAndThrowsTyped() {
        taskManager.updateFencingToken(1L);
        double deployedBefore = deployedCounter();

        taskManager.forceTaskExecutorShutdownForTest();

        JobGraph emptyGraph = new JobGraph("job-r402b");
        TaskDeploymentDescriptor descriptor = new TaskDeploymentDescriptor(
                "job-r402b", "vertex-b", 0, null,
                "attempt-b", 1, 1L,
                emptyGraph, null, null);

        // The typed rejectDeploy channel (CC-17) is preserved — cleanup happens
        // BEFORE the rethrow, not instead of it.
        StreamException ex = assertThrows(StreamException.class,
                () -> taskManager.deployTask(descriptor, 1L),
                "deployTask submit rejection must keep the typed rejectDeploy channel");
        assertTrue(String.valueOf(ex).contains("rejected deployment")
                        || String.valueOf(ex).contains("executor shutdown race"),
                "rejection names the submit-rejection window: " + ex);

        assertEquals(0, taskManager.getRunningTaskCount(),
                "no running-task entry may survive a deployTask submit rejection");
        assertFalse(taskManager.hasRegistryEntry("job-r402b", "vertex-b", 0),
                "no registry entry (zombie) may survive a deployTask submit rejection");
        assertEquals(4, taskManager.availablePermits(),
                "the pinned capacity permit must be returned (accounting back to pre-submit)");
        assertEquals(deployedBefore, deployedCounter(),
                "a task that never ran (zombie) must not be counted as deployed");
    }

    // ---------------- helpers (same shape as TestConcurrencyWindows2326) ----------------

    private static final class RecordingClusterRegistry implements ClusterRegistry {
        final Map<String, Object> registeredNodes = new ConcurrentHashMap<>();

        @Override public void registerCoordinator(String jobId, String coordinatorId, long fencingEpoch) {}
        @Override public io.nop.stream.runtime.cluster.CoordinatorInfo getActiveCoordinator(String jobId) { return null; }
        @Override public void registerNode(String nodeId, String endpoint, int capacity) {
            registeredNodes.put(nodeId, new Object());
        }
        @Override public boolean renewLease(String nodeId, long leaseTimeoutMs) { return true; }
        @Override public io.nop.stream.runtime.cluster.LeaseInfo getNodeLease(String nodeId) { return null; }
        @Override public List<io.nop.stream.runtime.cluster.NodeInfo> getActiveNodes() {
            return List.of(new io.nop.stream.runtime.cluster.NodeInfo(
                    "r402-zombie-node", "localhost:9402", 4,
                    System.currentTimeMillis(), System.currentTimeMillis()));
        }
        @Override public void assignTask(String jobId, String vertexId, int subtaskIndex,
                                          String nodeId, String attemptId, long fencingEpoch,
                                          int attemptNumber) {}
        @Override public TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex) { return null; }
        @Override public List<TaskAssignment> getAttemptHistory(String jobId, String vertexId, int subtaskIndex) {
            return List.of();
        }
        @Override public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {}
    }

    private static final class RecordingMessageService implements IMessageService {
        @Override public io.nop.api.core.message.IMessageSubscription subscribe(String topic,
                                                                               io.nop.api.core.message.IMessageConsumer consumer,
                                                                               io.nop.api.core.message.MessageSubscribeOptions options) {
            return new io.nop.api.core.message.IMessageSubscription() {
                @Override public void cancel() {}
                @Override public boolean isSuspended() { return false; }
                @Override public boolean isCancelled() { return false; }
                @Override public void suspend() {}
                @Override public void resume() {}
            };
        }
        @Override public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message,
                                                                              io.nop.api.core.message.MessageSendOptions options) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
