/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.JobTerminationMode;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.coordinator.JobStatus;
import io.nop.stream.runtime.coordinator.JobStatusResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Stage 39 Phase 2 wiring-verification (plan guide #23): proves that control-plane
 * calls traverse the REAL platform RPC transport ({@link StreamControlRpcServer} +
 * {@link StreamControlRpcProxyFactory} over {@link IMessageService}) and reach the
 * server-side implementation — not a direct Java reference.
 *
 * <p>Assertions are based on observable side effects (counters / captured args) on the
 * server-side recording impl, which is the strongest evidence the RPC layer was
 * crossed.
 */
class TestStreamControlRpc {

    private LocalMessageService messageService = new LocalMessageService();
    private StreamControlRpcServer taskServer;
    private StreamControlRpcProxyFactory taskProxy;
    private StreamControlRpcServer coordinatorServer;
    private StreamControlRpcProxyFactory coordinatorProxy;

    @AfterEach
    void tearDown() {
        stopQuietly(taskServer);
        stopQuietly(taskProxy);
        stopQuietly(coordinatorServer);
        stopQuietly(coordinatorProxy);
        if (messageService != null) {
            messageService.clearConsumers();
        }
    }

    @Test
    void taskControlCallsTraverseRpcToServerImpl() {
        RecordingTaskRpc serverImpl = new RecordingTaskRpc();
        taskServer = new StreamControlRpcServer("streamTaskRpc@node-0",
                IStreamTaskRpcService.class, serverImpl, messageService,
                StreamControlRpcTopics.taskTopic("node-0"));
        taskServer.start();

        taskProxy = new StreamControlRpcProxyFactory("streamTaskRpc@node-0",
                IStreamTaskRpcService.class, messageService,
                StreamControlRpcTopics.taskTopic("node-0"));
        taskProxy.start();

        IStreamTaskRpcService proxy = taskProxy.getProxy();

        // The proxy is NOT a direct reference to the server impl — it is an RPC boundary.
        assertFalse(proxy instanceof RecordingTaskRpc,
                "coordinator-side IStreamTaskRpcService must be an RPC proxy, not the direct impl");

        // Drive control calls through the proxy.
        TaskAssignment assignment = new TaskAssignment("job-1", "source", 0, "node-0",
                "attempt-1", 7L, System.currentTimeMillis(), 1);
        proxy.receiveAssignment(assignment);
        proxy.triggerCheckpoint(new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT), 7L);
        proxy.updateFencingToken(9L);
        proxy.cancelTask("job-1", "source", 0);

        // Wiring verification: the server-side impl recorded every call — proving the
        // RPC layer was crossed (a direct reference would also record, but combined
        // with the proxy-not-impl assertion above this confirms the RPC path).
        assertEquals(1, serverImpl.receiveAssignmentCount.get(),
                "receiveAssignment must reach the server-side impl over RPC");
        assertEquals(7L, serverImpl.lastAssignmentFencingEpoch.get(),
                "receiveAssignment argument must survive the RPC round-trip");
        assertEquals(1, serverImpl.triggerCheckpointCount.get(),
                "triggerCheckpoint must reach the server-side impl over RPC");
        assertEquals(7L, serverImpl.lastTriggerEpoch.get(),
                "triggerCheckpoint fencingEpoch argument must survive the RPC round-trip");
        assertEquals(9L, serverImpl.lastUpdateFencingEpoch.get(),
                "updateFencingToken argument must survive the RPC round-trip");
        assertEquals(1, serverImpl.cancelTaskCount.get(),
                "cancelTask must reach the server-side impl over RPC");
    }

    @Test
    void coordinatorRequestResponseCallTraversesRpc() {
        RecordingCoordinatorRpc serverImpl = new RecordingCoordinatorRpc();
        coordinatorServer = new StreamControlRpcServer("streamCoordinatorRpc@job-1",
                IStreamCoordinatorRpcService.class, serverImpl, messageService,
                StreamControlRpcTopics.coordinatorTopic("job-1"));
        coordinatorServer.start();

        coordinatorProxy = new StreamControlRpcProxyFactory("streamCoordinatorRpc@job-1",
                IStreamCoordinatorRpcService.class, messageService,
                StreamControlRpcTopics.coordinatorTopic("job-1"));
        coordinatorProxy.start();

        IStreamCoordinatorRpcService proxy = coordinatorProxy.getProxy();

        // getJobStatus is a request-response (non-void) call: the proxy must wait for
        // the server's ApiResponse and deserialize the result.
        JobStatusResponse status = proxy.getJobStatus();
        assertNotNull(status, "request-response getJobStatus must return a non-null value over RPC");
        assertEquals(JobStatus.RUNNING, status.getJobStatus(),
                "getJobStatus result must survive the RPC round-trip");

        // terminate is void → one-way, but must still reach the server impl.
        proxy.terminate(JobTerminationMode.CANCEL);
        assertEquals(JobTerminationMode.CANCEL, serverImpl.lastTerminateMode,
                "terminate argument must survive the RPC round-trip");
    }

    private static void stopQuietly(io.nop.commons.service.LifeCycleSupport c) {
        if (c == null) {
            return;
        }
        try {
            c.stop();
        } catch (Exception ignored) {
            // best-effort teardown
        }
    }

    static final class RecordingTaskRpc implements IStreamTaskRpcService {
        final AtomicLong receiveAssignmentCount = new AtomicLong();
        final AtomicLong triggerCheckpointCount = new AtomicLong();
        final AtomicLong cancelTaskCount = new AtomicLong();
        final AtomicLong lastAssignmentFencingEpoch = new AtomicLong();
        final AtomicLong lastTriggerEpoch = new AtomicLong();
        final AtomicLong lastUpdateFencingEpoch = new AtomicLong();

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
            receiveAssignmentCount.incrementAndGet();
            lastAssignmentFencingEpoch.set(assignment.getFencingEpoch());
        }

        @Override
        public void triggerCheckpoint(CheckpointBarrier barrier, long fencingEpoch) {
            triggerCheckpointCount.incrementAndGet();
            lastTriggerEpoch.set(fencingEpoch);
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
            cancelTaskCount.incrementAndGet();
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
            lastUpdateFencingEpoch.set(fencingEpoch);
        }
    }

    static final class RecordingCoordinatorRpc implements IStreamCoordinatorRpcService {
        volatile JobTerminationMode lastTerminateMode;

        @Override
        public void receiveCheckpointAck(io.nop.stream.runtime.taskmanager.CheckpointAckMessage ack) {
        }

        @Override
        public void reportTaskStatus(io.nop.stream.runtime.coordinator.TaskStatusReport report) {
        }

        @Override
        public void reportNodeTaskLiveness(String nodeId, java.util.List<io.nop.stream.runtime.coordinator.TaskProgress> progress) {
        }

        @Override
        public void terminate(JobTerminationMode mode) {
            this.lastTerminateMode = mode;
        }

        @Override
        public void abortCheckpoint(long epochId) {
        }

        @Override
        public JobStatusResponse getJobStatus() {
            return new JobStatusResponse(JobStatus.RUNNING, null);
        }
    }

    // Sanity: ensure the imported types are used (static-analysis guard).
    @SuppressWarnings("unused")
    private void useTypes(JobStatusResponse r) {
        assertInstanceOf(JobStatusResponse.class, r);
    }
}
