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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.rpc.TaskDeploymentDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 42 Phase 0: verifies {@link JobCoordinator#assignTasks()} drives the
 * remote-deploy path correctly:
 * <ul>
 *   <li>When {@code remoteDeployMode=true}, calls {@code deployTask} RPC (not
 *       {@code receiveAssignment}) for each assigned subtask — 接线验证 (plan guide #23).</li>
 *   <li>Recovery path {@link JobCoordinator#globalRecovery()} →
 *       {@code rotateFencingEpochAndRestore} → {@code assignTasks()} re-issues
 *       {@code deployTask} for every reassigned subtask with the rotated epoch.</li>
 *   <li>Default mode (in-process) keeps the legacy {@code receiveAssignment} path
 *       unchanged — zero regression.</li>
 *   <li>Fail-fast (#24) when {@code remoteDeployMode=true} but no JobGraph injected.</li>
 * </ul>
 */
class TestJobCoordinatorRemoteDeploy {

    private static final String JOB_ID = "remote-deploy-job";
    private static final String COORDINATOR_ID = "coordinator-rd";
    private static final String CHECKPOINT_PATH = "/tmp/nop-stream-rd-test";

    @TempDir
    Path tempDir;

    private JobCoordinator coordinator;
    private ClusterRegistry clusterRegistry;
    private RecordingTaskRpcService nodeRpc;
    private RecordingTaskRpcService node2Rpc;
    private Map<String, IStreamTaskRpcService> taskRpcServices;
    private DeploymentPlan deploymentPlan;
    private JobGraph jobGraph;

    @BeforeEach
    void setUp() {
        clusterRegistry = new InMemoryClusterRegistry();
        clusterRegistry.registerNode("node-1", "localhost:9001", 4);
        clusterRegistry.registerNode("node-2", "localhost:9002", 4);

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(10000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", idCounter, storage, config);

        nodeRpc = new RecordingTaskRpcService();
        node2Rpc = new RecordingTaskRpcService();
        taskRpcServices = new LinkedHashMap<>();
        taskRpcServices.put("node-1", nodeRpc);
        taskRpcServices.put("node-2", node2Rpc);

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = new ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, "pipeline-0", vertexPlans, edgePlans, null, null);
        deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);

        jobGraph = new JobGraph(JOB_ID);

        coordinator = new JobCoordinator(
                JOB_ID, COORDINATOR_ID, deploymentPlan,
                clusterRegistry, checkpointCoordinator, taskRpcServices);
        coordinator.setTerminationCheckpointTimeoutMs(500L);
    }

    @AfterEach
    void tearDown() {
        coordinator.stop();
    }

    @Test
    void remoteDeployModeCallsDeployTaskNotReceiveAssignment() {
        coordinator.setRemoteDeployMode(true);
        coordinator.setJobGraph(jobGraph);
        coordinator.setCheckpointStoragePath(CHECKPOINT_PATH);
        coordinator.start();

        coordinator.assignTasks();

        assertEquals(2, nodeRpc.deployDescriptors.size() + node2Rpc.deployDescriptors.size(),
                "deployTask must be called once per assigned subtask (2 vertices x parallelism 1 = 2 calls)");
        // The recording rpc did NOT receive a receiveAssignment call (deployTask
        // is self-contained in remote-deploy mode). receiveAssignment count stays 0.
        assertEquals(0, nodeRpc.assignments.size() + node2Rpc.assignments.size(),
                "receiveAssignment must NOT be called in remote-deploy mode (descriptor is self-contained)");

        // Find the deploy descriptor (lands on node-1 by round-robin for the
        // first subtask) and verify metadata.
        TaskDeploymentDescriptor descriptor = nodeRpc.deployDescriptors.isEmpty()
                ? node2Rpc.deployDescriptors.get(0) : nodeRpc.deployDescriptors.get(0);
        assertEquals(JOB_ID, descriptor.getJobId());
        assertEquals(CHECKPOINT_PATH, descriptor.getCheckpointRestorePath());
        assertEquals(coordinator.getFencingEpoch(), descriptor.getFencingEpoch());
        assertNotNull(descriptor.getJobGraph());
        assertEquals(1, descriptor.getAttemptNumber());
    }

    @Test
    void inProcessModeCallsReceiveAssignmentNotDeployTask() {
        // Default mode (no remoteDeployMode setter call) must preserve legacy path.
        coordinator.start();
        coordinator.assignTasks();

        assertFalse(nodeRpc.assignments.isEmpty(),
                "receiveAssignment must be called in the in-process path");
        assertTrue(nodeRpc.deployDescriptors.isEmpty(),
                "deployTask must NOT be called in the in-process path");
    }

    @Test
    void remoteDeployModeFailsFastWithoutJobGraph() {
        coordinator.setRemoteDeployMode(true);
        // Forget to inject JobGraph — should fail-fast, not silent no-op (#24).
        coordinator.start();
        assertThrows(RuntimeException.class, coordinator::assignTasks,
                "remoteDeployMode without JobGraph must throw (plan guide #24 — no silent skip)");
    }

    @Test
    void recoveryPathReIssuesDeployTaskWithRotatedEpoch() {
        coordinator.setMaxRestarts(5);
        coordinator.setRemoteDeployMode(true);
        coordinator.setJobGraph(jobGraph);
        coordinator.setCheckpointStoragePath(CHECKPOINT_PATH);
        coordinator.start();
        coordinator.assignTasks();

        long initialEpoch = coordinator.getFencingEpoch();
        TaskDeploymentDescriptor initialDescriptor = nodeRpc.deployDescriptors.isEmpty()
                ? node2Rpc.deployDescriptors.get(0) : nodeRpc.deployDescriptors.get(0);
        assertEquals(initialEpoch, initialDescriptor.getFencingEpoch());
        int initialAttempt = initialDescriptor.getAttemptNumber();

        // Trigger recovery — rotates the fencing epoch and reassigns tasks.
        nodeRpc.deployDescriptors.clear();
        node2Rpc.deployDescriptors.clear();
        coordinator.globalRecovery();

        long rotatedEpoch = coordinator.getFencingEpoch();
        assertTrue(rotatedEpoch > initialEpoch,
                "Recovery must rotate the fencing epoch to fence out stale tasks");

        int recoveredCount = nodeRpc.deployDescriptors.size() + node2Rpc.deployDescriptors.size();
        assertTrue(recoveredCount > 0,
                "Recovery assignTasks must re-issue deployTask for reassigned tasks");

        TaskDeploymentDescriptor recoveredDescriptor = nodeRpc.deployDescriptors.isEmpty()
                ? node2Rpc.deployDescriptors.get(0) : nodeRpc.deployDescriptors.get(0);
        assertEquals(rotatedEpoch, recoveredDescriptor.getFencingEpoch(),
                "Recovered descriptor must carry the rotated fencing epoch");
        assertTrue(recoveredDescriptor.getAttemptNumber() > initialAttempt,
                "Recovered descriptor must bump the per-subtask attempt number");
        assertEquals(CHECKPOINT_PATH, recoveredDescriptor.getCheckpointRestorePath(),
                "Recovered descriptor must carry the checkpoint restore path so the "
                        + "replacement TaskManager can restore state");
    }

    @Test
    void deployTaskUsesRoundRobinWhenNoMaterializedAssignment() {
        // Two nodes available; one vertex with parallelism 2 should land on both.
        RecordingTaskRpcService node2Rpc = new RecordingTaskRpcService();
        taskRpcServices.put("node-2", node2Rpc);

        // Re-build plan with parallelism 2 on a single vertex (source only).
        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 2, null));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, "pipeline-0", vertexPlans, Collections.emptyList(), null, null);
        deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true).checkpointInterval(1000L).checkpointTimeout(10000L)
                .maxConcurrentCheckpoints(1).maxRetainedCheckpoints(3).build();
        CheckpointCoordinator cc = new CheckpointCoordinator(JOB_ID, "pipeline-0", idCounter, storage, config);

        coordinator = new JobCoordinator(JOB_ID, COORDINATOR_ID, deploymentPlan,
                clusterRegistry, cc, taskRpcServices);
        coordinator.setRemoteDeployMode(true);
        coordinator.setJobGraph(jobGraph);
        coordinator.setCheckpointStoragePath(CHECKPOINT_PATH);
        coordinator.start();
        coordinator.assignTasks();

        assertEquals(1, nodeRpc.deployDescriptors.size(),
                "one subtask should land on node-1 (round-robin)");
        assertEquals(1, node2Rpc.deployDescriptors.size(),
                "one subtask should land on node-2 (round-robin)");
        assertEquals(CHECKPOINT_PATH, nodeRpc.deployDescriptors.get(0).getCheckpointRestorePath(),
                "checkpoint restore path always carried in descriptor");
    }

    /**
     * Recording test double that captures both legacy {@code receiveAssignment}
     * and Stage 42 {@code deployTask} calls so a test can assert which path the
     * coordinator took.
     */
    static final class RecordingTaskRpcService implements IStreamTaskRpcService {
        final List<TaskAssignment> assignments = new CopyOnWriteArrayList<>();
        final List<TaskDeploymentDescriptor> deployDescriptors = new CopyOnWriteArrayList<>();
        final AtomicLong lastFencingEpoch = new AtomicLong();

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
            assignments.add(assignment);
        }

        @Override
        public void triggerCheckpoint(CheckpointBarrier barrier, long fencingEpoch) {
            lastFencingEpoch.set(fencingEpoch);
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
            lastFencingEpoch.set(fencingEpoch);
        }

        @Override
        public void deployTask(TaskDeploymentDescriptor descriptor, long fencingEpoch) {
            deployDescriptors.add(descriptor);
            lastFencingEpoch.set(fencingEpoch);
        }
    }
}
