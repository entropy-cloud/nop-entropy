/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import io.nop.api.core.message.*;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TaskManager:
 * - receiveAssignment → creates task slot → runs → completes
 * - Heartbeat renewal
 * - Fencing token enforcement
 * - Stop lifecycle
 */
class TestTaskManager {

    private static final String NODE_ID = "test-node-1";
    private static final String ENDPOINT = "localhost:9090";
    private static final int CAPACITY = 4;
    private static final String CONTROL_TOPIC = "test-control";

    private TaskManager taskManager;
    private MockClusterRegistry clusterRegistry;
    private MockMessageService messageService;

    @BeforeEach
    void setUp() {
        clusterRegistry = new MockClusterRegistry();
        messageService = new MockMessageService();
        taskManager = new TaskManager(NODE_ID, ENDPOINT, CAPACITY,
                messageService, clusterRegistry, CONTROL_TOPIC);
    }

    @AfterEach
    void tearDown() {
        taskManager.stop();
    }

    @Test
    void testStartRegistersNode() {
        taskManager.start();
        assertTrue(taskManager.isRunning());
        assertTrue(clusterRegistry.registeredNodes.containsKey(NODE_ID));
    }

    @Test
    void testHeartbeatRenewsLease() {
        taskManager.start();
        taskManager.heartbeat();
        assertTrue(clusterRegistry.leaseRenewed);
    }

    @Test
    void testReceiveAssignmentCreatesTaskSlot() {
        taskManager.start();
        String fencingToken = UUID.randomUUID().toString();
        taskManager.updateFencingToken(fencingToken);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                NODE_ID, "attempt-1", fencingToken,
                System.currentTimeMillis());

        taskManager.receiveAssignment(assignment);

        assertEquals(1, taskManager.getRunningTaskCount());
    }

    @Test
    void testReceiveAssignmentRejectsStaleFencingToken() {
        taskManager.start();
        taskManager.updateFencingToken("current-token");

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                NODE_ID, "attempt-1", "old-token",
                System.currentTimeMillis());

        taskManager.receiveAssignment(assignment);

        assertEquals(0, taskManager.getRunningTaskCount());
    }

    @Test
    void testReceiveAssignmentRejectsWhenNotRunning() {
        // Don't start the task manager
        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                NODE_ID, "attempt-1", "token",
                System.currentTimeMillis());

        taskManager.receiveAssignment(assignment);

        assertEquals(0, taskManager.getRunningTaskCount());
    }

    @Test
    void testStopCleansUp() {
        taskManager.start();
        assertTrue(taskManager.isRunning());

        taskManager.stop();
        assertFalse(taskManager.isRunning());
    }

    @Test
    void testUpdateFencingTokenCancelsOldTasks() {
        taskManager.start();
        String oldToken = UUID.randomUUID().toString();
        taskManager.updateFencingToken(oldToken);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                NODE_ID, "attempt-1", oldToken,
                System.currentTimeMillis());

        taskManager.receiveAssignment(assignment);
        assertEquals(1, taskManager.getRunningTaskCount());

        // Update fencing token — old tasks should be canceled
        String newToken = UUID.randomUUID().toString();
        taskManager.updateFencingToken(newToken);

        // Give time for cancellation to propagate
        assertEquals(0, taskManager.getRunningTaskCount());
    }

    @Test
    void testCapacityEnforcement() {
        taskManager.start();
        String token = UUID.randomUUID().toString();
        taskManager.updateFencingToken(token);

        // Submit more tasks than capacity
        for (int i = 0; i < CAPACITY + 2; i++) {
            TaskAssignment assignment = new TaskAssignment(
                    "job-1", "vertex-1", i,
                    NODE_ID, "attempt-" + i, token,
                    System.currentTimeMillis());
            taskManager.receiveAssignment(assignment);
        }

        // Should not exceed capacity
        assertTrue(taskManager.getRunningTaskCount() <= CAPACITY);
    }

    @Test
    void testDuplicateAssignmentIgnored() {
        taskManager.start();
        String token = UUID.randomUUID().toString();
        taskManager.updateFencingToken(token);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                NODE_ID, "attempt-1", token,
                System.currentTimeMillis());

        taskManager.receiveAssignment(assignment);
        taskManager.receiveAssignment(assignment); // duplicate

        assertEquals(1, taskManager.getRunningTaskCount());
    }

    @Test
    void testCancelTaskDoesNotDoubleReleaseSemaphore() throws Exception {
        TaskManager smallTm = new TaskManager("node", "ep", 2,
                messageService, clusterRegistry, CONTROL_TOPIC);
        smallTm.start();
        String token = UUID.randomUUID().toString();
        smallTm.updateFencingToken(token);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                "node", "attempt-1", token,
                System.currentTimeMillis());

        smallTm.receiveAssignment(assignment);

        Thread.sleep(100);

        smallTm.cancelTask("job-1", "vertex-1", 0);

        Thread.sleep(200);

        Semaphore sem = null;
        int available = smallTm.availablePermits();
        assertTrue(available <= 2,
                "availablePermits (" + available + ") should not exceed capacity (2) after cancel");

        smallTm.stop();
    }

    @Test
    void testUpdateFencingTokenReleasesSemaphore() throws Exception {
        TaskManager smallTm = new TaskManager("node", "ep", 2,
                messageService, clusterRegistry, CONTROL_TOPIC);
        smallTm.start();
        String oldToken = UUID.randomUUID().toString();
        smallTm.updateFencingToken(oldToken);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                "node", "attempt-1", oldToken,
                System.currentTimeMillis());

        smallTm.receiveAssignment(assignment);
        assertEquals(1, smallTm.getRunningTaskCount());

        int permitsBefore = smallTm.availablePermits();

        String newToken = UUID.randomUUID().toString();
        smallTm.updateFencingToken(newToken);

        Thread.sleep(200);

        int permitsAfter = smallTm.availablePermits();
        assertTrue(permitsAfter > permitsBefore,
                "Permits should increase after updateFencingToken cancels old tasks");

        assertTrue(permitsAfter <= 2,
                "availablePermits (" + permitsAfter + ") should not exceed capacity (2)");

        smallTm.stop();
    }

    @Test
    void testMultipleCancelsDoNotExceedCapacity() throws Exception {
        TaskManager smallTm = new TaskManager("node", "ep", 2,
                messageService, clusterRegistry, CONTROL_TOPIC);
        smallTm.start();
        String token = UUID.randomUUID().toString();
        smallTm.updateFencingToken(token);

        for (int i = 0; i < 2; i++) {
            TaskAssignment assignment = new TaskAssignment(
                    "job-1", "vertex-1", i,
                    "node", "attempt-" + i, token,
                    System.currentTimeMillis());
            smallTm.receiveAssignment(assignment);
        }

        Thread.sleep(100);

        smallTm.cancelTask("job-1", "vertex-1", 0);
        smallTm.cancelTask("job-1", "vertex-1", 1);

        Thread.sleep(200);

        int available = smallTm.availablePermits();
        assertTrue(available <= 2,
                "availablePermits (" + available + ") should not exceed capacity (2) after N cancels");

        smallTm.stop();
    }

    // ==================== Mocks ====================

    static class MockClusterRegistry implements ClusterRegistry {
        final Map<String, Object> registeredNodes = new ConcurrentHashMap<>();
        volatile boolean leaseRenewed = false;

        @Override
        public void registerCoordinator(String jobId, String coordinatorId, String fencingToken) {}

        @Override
        public io.nop.stream.runtime.cluster.CoordinatorInfo getActiveCoordinator(String jobId) {
            return null;
        }

        @Override
        public void registerNode(String nodeId, String endpoint, int capacity) {
            registeredNodes.put(nodeId, new Object());
        }

        @Override
        public boolean renewLease(String nodeId, long leaseTimeoutMs) {
            leaseRenewed = true;
            return true;
        }

        @Override
        public io.nop.stream.runtime.cluster.LeaseInfo getNodeLease(String nodeId) {
            return null;
        }

        @Override
        public List<io.nop.stream.runtime.cluster.NodeInfo> getActiveNodes() {
            List<io.nop.stream.runtime.cluster.NodeInfo> nodes = new ArrayList<>();
            for (String nodeId : registeredNodes.keySet()) {
                nodes.add(new io.nop.stream.runtime.cluster.NodeInfo(
                        nodeId, "localhost", 4, System.currentTimeMillis(), System.currentTimeMillis()));
            }
            return nodes;
        }

        @Override
        public void assignTask(String jobId, String vertexId, int subtaskIndex,
                               String nodeId, String attemptId, String fencingToken) {}

        @Override
        public TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
            return null;
        }

        @Override
        public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {}
    }

    static class MockMessageService implements IMessageService {
        final List<Object> sentMessages = new CopyOnWriteArrayList<>();

        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
            return new IMessageSubscription() {
                @Override public void cancel() {}
                @Override public boolean isSuspended() { return false; }
                @Override public boolean isCancelled() { return false; }
                @Override public void suspend() {}
                @Override public void resume() {}
            };
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            sentMessages.add(message);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
