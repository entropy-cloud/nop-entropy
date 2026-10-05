/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5 CC-01 regression pin: {@code failJob} is terminal — it must run the
 * cancel fan-out over every assigned remote task (the symmetric stop()/
 * terminateCancel paths already did; failJob skipped it, orphaning running
 * sources / occupied slot permits / 2PC state forever) and close the
 * deployment window ({@code running=false}) so a concurrent
 * {@code executeAssignmentFanOut} cannot re-deploy after the cancel.
 */
class TestFailJobCancelsAssignedTasks {

    private static final String JOB_ID = "r5-cc01-job";
    private static final String COORDINATOR_ID = "coordinator-cc01";

    @TempDir
    Path tempDir;

    private CheckpointCoordinator checkpointCoordinator;
    private Map<String, IStreamTaskRpcService> taskRpcServices;
    private CoordinatorTestSupport.RecordingTaskRpcService rpc;
    private DeploymentPlan deploymentPlan;
    private final List<JobCoordinator> coordinators = new ArrayList<>();

    @BeforeEach
    void setUp() {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(10000L)
                .maxConcurrentCheckpoints(2)
                .build();
        checkpointCoordinator = new CheckpointCoordinator(JOB_ID, "pipeline-0", idCounter, storage, config);

        rpc = new CoordinatorTestSupport.RecordingTaskRpcService();
        taskRpcServices = new LinkedHashMap<>();
        taskRpcServices.put("node-1", rpc);

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = new ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("source", "sink",
                io.nop.stream.core.execution.plan.PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, "pipeline-0", vertexPlans, edgePlans, null, null);
        deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);
    }

    @AfterEach
    void tearDown() {
        for (JobCoordinator c : coordinators) {
            try {
                c.stop();
            } catch (Exception ignored) {
                // best-effort cleanup — the test proper already asserted the outcomes
            }
        }
    }

    private JobCoordinator newCoordinator() {
        io.nop.stream.runtime.cluster.InMemoryClusterRegistry registry =
                new io.nop.stream.runtime.cluster.InMemoryClusterRegistry();
        registry.registerNode("node-1", "localhost:8081", 4);
        JobCoordinator c = new JobCoordinator(
                JOB_ID, COORDINATOR_ID, deploymentPlan,
                registry, checkpointCoordinator, taskRpcServices);
        coordinators.add(c);
        return c;
    }

    @Test
    public void failJobCancelsAssignedTasksAndClosesDeploymentWindow() {
        JobCoordinator coordinator = newCoordinator();
        coordinator.start();
        coordinator.assignTasks();
        assertFalse(rpc.assignments.isEmpty(),
                "assignments were fanned out");
        int assigned = rpc.assignments.size();
        assertFalse(coordinator.getTaskAssignments().isEmpty(),
                "assignments must be materialized before the failure (got "
                        + coordinator.getTaskAssignments() + ", running=" + coordinator.isRunning() + ")");

        coordinator.failJob(new RuntimeException("task exploded"));

        assertEquals(JobStatus.FAILED, coordinator.getJobStatus().getJobStatus(),
                "failJob must mark the job FAILED");
        assertFalse(coordinator.isRunning(),
                "failJob must close the deployment window (running=false, R5 CC-01)");
        assertEquals(assigned, rpc.cancelTaskKeys.size(),
                "every assigned task must receive the cancel fan-out on job failure (R5 CC-01)");
        assertEquals(assigned, rpc.cancelTaskCount.get(),
                "cancel RPC count matches the assignment count");
        // no late re-deploy after the cancel: the assignment window is closed
        coordinator.assignTasks();
        assertEquals(assigned, rpc.assignments.size(),
                "no new deployment may be fanned out after failJob (running=false)");
    }
}
