/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.CoordinatorInfo;
import io.nop.stream.runtime.cluster.LeaseInfo;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.taskmanager.TaskManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link TaskManager}'s OWN thread factories produce daemon threads.
 *
 * <p>The prior version constructed a TaskManager with {@code null} clusterRegistry and
 * never called {@code start()}, so neither {@code tm-heartbeat-*} (created by
 * {@code start()}'s {@code scheduleAtFixedRate}) nor {@code tm-task-*} (lazily created
 * on first {@code submit}) ever existed — the assertions lived in a dead {@code if}
 * branch and {@code foundDaemonTaskThread} was set but never asserted. Removing the
 * daemon markers could not be caught. This rewrite provides non-null minimal mocks so
 * {@code start()} does not NPE, actually starts the executors, submits a dummy task to
 * force {@code tm-task-*} creation, then asserts both daemon threads exist (regression
 * power: drop {@code setDaemon(true)} from either factory and these tests FAIL).
 */
class TestTaskManagerDaemon {

    /** Minimal no-op ClusterRegistry so {@link TaskManager#start()} can registerNode without NPE. */
    private static ClusterRegistry noopClusterRegistry() {
        return new ClusterRegistry() {
            @Override
            public void registerCoordinator(String jobId, String coordinatorId, long fencingEpoch) {
            }

            @Override
            public CoordinatorInfo getActiveCoordinator(String jobId) {
                return null;
            }

            @Override
            public void registerNode(String nodeId, String endpoint, int capacity) {
            }

            @Override
            public boolean renewLease(String nodeId, long leaseTimeoutMs) {
                return true;
            }

            @Override
            public LeaseInfo getNodeLease(String nodeId) {
                return null;
            }

            @Override
            public List<NodeInfo> getActiveNodes() {
                return List.of();
            }

            @Override
            public void assignTask(String jobId, String vertexId, int subtaskIndex, String nodeId,
                                   String attemptId, long fencingEpoch, int attemptNumber) {
            }

            @Override
            public TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
                return null;
            }

            @Override
            public List<TaskAssignment> getAttemptHistory(String jobId, String vertexId, int subtaskIndex) {
                return List.of();
            }

            @Override
            public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
            }
        };
    }

    /**
     * Minimal no-op IMessageService. Not touched by {@code start()}/{@code heartbeat()}/
     * {@code receiveAssignment()} (verified against live code), but provided non-null so
     * the TaskManager fixture matches a realistic wiring and stays robust to future changes.
     */
    private static IMessageService noopMessageService() {
        return new IMessageService() {
            @Override
            public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public IMessageSubscription subscribe(String topic, IMessageConsumer listener,
                                                  MessageSubscribeOptions options) {
                return new IMessageSubscription() {
                    @Override
                    public void cancel() {
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }

                    @Override
                    public boolean isSuspended() {
                        return false;
                    }

                    @Override
                    public void suspend() {
                    }

                    @Override
                    public void resume() {
                    }
                };
            }
        };
    }

    private static boolean threadExists(String namePrefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> t.getName().startsWith(namePrefix));
    }

    private static void waitUntil(BooleanSupplier condition, long timeoutMs, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    @Test
    void testHeartbeatThreadIsDaemon() throws Exception {
        String nodeId = "node-hb-daemon";
        TaskManager tm = new TaskManager(nodeId, "localhost:0", 1,
                noopMessageService(), noopClusterRegistry(), "ctrl");
        try {
            tm.start(); // scheduleAtFixedRate creates the tm-heartbeat-* thread
            waitUntil(() -> threadExists("tm-heartbeat-" + nodeId), 5000,
                    "tm-heartbeat-* thread must exist after start(); without it the test exercises nothing");

            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (t.getName().startsWith("tm-heartbeat-" + nodeId)) {
                    assertTrue(t.isDaemon(),
                            "Heartbeat thread should be daemon: " + t.getName());
                }
            }
        } finally {
            tm.stop();
        }
    }

    @Test
    void testTaskExecutorThreadsAreDaemon() throws Exception {
        String nodeId = "node-task-daemon";
        TaskManager tm = new TaskManager(nodeId, "localhost:0", 2,
                noopMessageService(), noopClusterRegistry(), "ctrl");
        try {
            tm.start(); // sets running=true (required by receiveAssignment)

            // Submit a dummy assignment so taskExecutor lazily creates the tm-task-* thread.
            // fencingEpoch 0 matches TaskManager's initial currentFencingEpoch (starts at 0).
            // The RunningTask blocks on its invokable latch (30s) — long enough to observe
            // the thread, then stop() cancels it cleanly.
            tm.receiveAssignment(new TaskAssignment("job-daemon", "source", 0, nodeId,
                    "attempt-0", 0L, System.currentTimeMillis(), 1));

            waitUntil(() -> threadExists("tm-task-" + nodeId), 5000,
                    "tm-task-* thread must exist after submit(); without it the test exercises nothing");

            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (t.getName().startsWith("tm-task-" + nodeId)) {
                    assertTrue(t.isDaemon(),
                            "Task thread should be daemon: " + t.getName());
                }
            }
        } finally {
            tm.stop();
        }
    }
}
