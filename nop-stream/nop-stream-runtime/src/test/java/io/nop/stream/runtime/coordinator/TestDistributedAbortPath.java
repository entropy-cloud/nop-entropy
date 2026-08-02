/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.rpc.StreamControlRpcProxyFactory;
import io.nop.stream.runtime.rpc.StreamControlRpcServer;
import io.nop.stream.runtime.rpc.StreamControlRpcTopics;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.taskmanager.CheckpointAckMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Stage 39 Phase 3 Proof: the <b>distributed</b> checkpoint-abort path. When a
 * checkpoint is aborted, the coordinator's distributed abort handler fires
 * {@code cancelTask} RPC (the independent control channel, checkpoint-design §13.2
 * line 1116) at every assigned remote task — crossing the RPC boundary established
 * in Phase 2.
 *
 * <p>Asserts both that (a) the abort handler logic routes to all assigned tasks, and
 * (b) the {@code cancelTask} call genuinely traverses the RPC transport (verified
 * via a recording server-side impl reached only through the
 * {@link StreamControlRpcServer}/{@link StreamControlRpcProxyFactory} pair).
 */
class TestDistributedAbortPath {

    private static final String JOB_ID = "abort-job-1";

    @TempDir
    Path tempDir;

    private LocalMessageService messageService;
    private RecordingTaskRpc serverImpl;
    private StreamControlRpcServer taskServer;
    private StreamControlRpcProxyFactory taskProxy;

    @BeforeEach
    void setUp() {
        messageService = new LocalMessageService();
    }

    @AfterEach
    void tearDown() {
        stopQuietly(taskProxy);
        stopQuietly(taskServer);
        if (messageService != null) {
            messageService.clearConsumers();
        }
    }

    @Test
    void checkpointAbortFiresCancelTaskRpcAtAllRemoteTasks() {
        // RPC topology: a recording IStreamTaskRpcService exposed over RPC on node-1.
        serverImpl = new RecordingTaskRpc();
        String topic = StreamControlRpcTopics.taskTopic("node-1");
        taskServer = new StreamControlRpcServer("streamTaskRpc@node-1",
                IStreamTaskRpcService.class, serverImpl, messageService, topic);
        taskServer.start();
        taskProxy = new StreamControlRpcProxyFactory("streamTaskRpc@node-1",
                IStreamTaskRpcService.class, messageService, topic);
        taskProxy.start();

        Map<String, IStreamTaskRpcService> taskRpcServices = new LinkedHashMap<>();
        taskRpcServices.put("node-1", taskProxy.getProxy());

        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        registry.registerNode("node-1", "localhost:8080", 4);

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointCoordinator cc = new CheckpointCoordinator(JOB_ID, "pipeline-0",
                new CheckpointIDCounter(), storage, fastConfig());

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = new ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(JOB_ID, "pipeline-0", vertexPlans, edgePlans, null, null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(JOB_ID, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);

        JobCoordinator coordinator = new JobCoordinator(JOB_ID, "coord-abort", deploymentPlan,
                registry, cc, taskRpcServices);
        coordinator.setMaxRestarts(10);
        coordinator.setAutoRecoverOnFailedReport(false);

        try {
            coordinator.start();
            long epoch = coordinator.getFencingEpoch();
            assertNotEquals(0L, epoch);

            // assignTasks populates taskAssignmentMap AND crosses RPC (server records).
            coordinator.assignTasks();
            assertEquals(2, serverImpl.receiveAssignmentCount.get(),
                    "both source/sink assignments must cross RPC to the server impl");

            // Phase 3: register the distributed abort handler.
            coordinator.registerDistributedAbortHandler();

            // Trigger a pending checkpoint (barrier crosses RPC; pending is created).
            PendingCheckpoint pending = coordinator.triggerCheckpoint();
            assertNotNull(pending, "a pending checkpoint must be triggerable");
            assertEquals(1, serverImpl.triggerCount.get(),
                    "triggerCheckpoint barrier must cross RPC to the server impl");

            // Abort the pending checkpoint → the distributed abort handler must fire
            // cancelTask RPC at every assigned task (source + sink).
            cc.abortPendingCheckpoint(pending, "Phase 3 distributed abort test");

            assertEquals(2, serverImpl.cancelTaskCount.get(),
                    "distributed abort must fire cancelTask RPC at all assigned remote tasks");
            assertEquals(2, serverImpl.cancelTaskKeys.size(),
                    "cancelTask must reach the server impl for both source and sink");
        } finally {
            coordinator.stop();
        }
    }

    private static CheckpointConfig fastConfig() {
        return CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(2000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
    }

    private static void stopQuietly(io.nop.commons.service.LifeCycleSupport c) {
        if (c == null) {
            return;
        }
        try {
            c.stop();
        } catch (Exception ignored) {
            // best-effort
        }
    }

    /**
     * Recording server-side IStreamTaskRpcService. Records every call so the test can
     * assert control calls (and the distributed-abort cancelTask) genuinely crossed
     * the RPC transport.
     */
    static final class RecordingTaskRpc implements IStreamTaskRpcService {
        final AtomicLong receiveAssignmentCount = new AtomicLong();
        final AtomicLong triggerCount = new AtomicLong();
        final AtomicLong cancelTaskCount = new AtomicLong();
        final List<String> cancelTaskKeys = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
            receiveAssignmentCount.incrementAndGet();
        }

        @Override
        public void triggerCheckpoint(io.nop.stream.core.checkpoint.CheckpointBarrier barrier, long fencingEpoch) {
            triggerCount.incrementAndGet();
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
            cancelTaskCount.incrementAndGet();
            cancelTaskKeys.add(vertexId + "/" + subtaskIndex);
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
        }
    }

    // Sentinel usage to keep imports stable if the coordinator-facing side is later
    // extended in this test class (IStreamCoordinatorRpcService / CheckpointAckMessage).
    @SuppressWarnings("unused")
    private void keepImports(IStreamCoordinatorRpcService c, CheckpointAckMessage m,
                             TaskLocation l, TaskStateSnapshot s) {
    }
}
