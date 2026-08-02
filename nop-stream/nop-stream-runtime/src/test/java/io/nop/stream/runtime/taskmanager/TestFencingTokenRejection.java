/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-6: dedicated fencing-token rejection tests. The prior implementation
 * warned and returned (silent no-op); the hardened contract throws a
 * {@link StreamException} carrying the
 * {@link NopStreamErrors#ERR_STREAM_FENCING_TOKEN_MISMATCH} error code for
 * both the assignment and checkpoint-trigger RPC entry points.
 *
 * <p>Anti-hollow: every assertion below fails if the throw is reverted to a
 * LOG.warn + return. Cross-JVM fencing (Stage 39) is out of scope — these
 * tests only harden the in-process check.
 */
public class TestFencingTokenRejection {

    private static final String NODE_ID = "fencing-node";
    private static final String ENDPOINT = "localhost:9191";

    private TaskManager taskManager;
    private RecordingClusterRegistry clusterRegistry;
    private RecordingMessageService messageService;

    @BeforeEach
    void setUp() {
        clusterRegistry = new RecordingClusterRegistry();
        messageService = new RecordingMessageService();
        taskManager = new TaskManager(NODE_ID, ENDPOINT, 4,
                messageService, clusterRegistry, "control-topic");
        taskManager.start();
    }

    @AfterEach
    void tearDown() {
        taskManager.stop();
    }

    @Test
    void staleTokenAssignmentThrowsFencingMismatch() {
        long activeEpoch = 5L;
        taskManager.updateFencingToken(activeEpoch);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                NODE_ID, "attempt-1", 999L,
                System.currentTimeMillis());

        StreamException thrown = assertThrows(StreamException.class,
                () -> taskManager.receiveAssignment(assignment));
        assertEquals(NopStreamErrors.ERR_STREAM_FENCING_TOKEN_MISMATCH.getErrorCode(), thrown.getErrorCode());
        assertEquals(0, taskManager.getRunningTaskCount(),
                "no task slot should be created for a stale-token assignment");
    }

    @Test
    void staleTokenCheckpointTriggerThrowsFencingMismatch() {
        long activeEpoch = 5L;
        taskManager.updateFencingToken(activeEpoch);

        CheckpointBarrier barrier = new CheckpointBarrier(
                17L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        StreamException thrown = assertThrows(StreamException.class,
                () -> taskManager.triggerCheckpoint(barrier, 999L));
        assertEquals(NopStreamErrors.ERR_STREAM_FENCING_TOKEN_MISMATCH.getErrorCode(), thrown.getErrorCode());
    }

    @Test
    void activeTokenAssignmentStillSucceeds() {
        long activeEpoch = 5L;
        taskManager.updateFencingToken(activeEpoch);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                NODE_ID, "attempt-1", activeEpoch,
                System.currentTimeMillis());

        assertDoesNotThrow(() -> taskManager.receiveAssignment(assignment));
    }

    @Test
    void activeTokenCheckpointTriggerDoesNotThrow() {
        long activeEpoch = 5L;
        taskManager.updateFencingToken(activeEpoch);

        CheckpointBarrier barrier = new CheckpointBarrier(
                19L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        assertDoesNotThrow(() -> taskManager.triggerCheckpoint(barrier, activeEpoch));
    }

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
                    NODE_ID, ENDPOINT, 4, System.currentTimeMillis(), System.currentTimeMillis()));
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
        final List<Object> sent = new CopyOnWriteArrayList<>();
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
            sent.add(message);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
