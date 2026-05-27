/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.*;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.coordinator.JobCoordinator;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.taskmanager.TaskManager;
import io.nop.stream.runtime.transport.RemoteGraphExecutionPlanBuilder;

public class EmbeddedDistributedExecutor implements IStreamExecutionDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedDistributedExecutor.class);

    private final IMessageService messageService;
    private final int defaultNodeCount;

    public EmbeddedDistributedExecutor(IMessageService messageService) {
        this(messageService, 2);
    }

    public EmbeddedDistributedExecutor(IMessageService messageService, int defaultNodeCount) {
        this.messageService = messageService;
        this.defaultNodeCount = defaultNodeCount;
    }

    @Override
    public boolean supportsDeploymentMode(DeploymentMode mode) {
        return mode == DeploymentMode.DISTRIBUTED;
    }

    @Override
    public StreamExecutionResult execute(JobGraph jobGraph, PartitionedPlan partitionedPlan,
                                         DeploymentPlan deploymentPlan) throws Exception {
        long startTime = System.currentTimeMillis();
        String jobId = partitionedPlan.getJobId();
        String fencingToken = UUID.randomUUID().toString();

        LOG.info("Starting embedded distributed execution for job {} with fencing token {}", jobId, fencingToken);

        int nodeCount = determineNodeCount(partitionedPlan);

        ClusterRegistry clusterRegistry = new InMemoryClusterRegistry();

        List<TaskManager> taskManagers = new ArrayList<>(nodeCount);
        Map<String, IStreamTaskRpcService> taskRpcServices = new LinkedHashMap<>();

        for (int i = 0; i < nodeCount; i++) {
            String nodeId = "node-" + i;
            String controlTopic = "nop-stream.control." + jobId;
            TaskManager tm = new TaskManager(nodeId, "embedded:" + nodeId, 16,
                    messageService, clusterRegistry, controlTopic);
            tm.updateFencingToken(fencingToken);
            taskManagers.add(tm);
            taskRpcServices.put(nodeId, tm);
        }

        for (TaskManager tm : taskManagers) {
            tm.start();
        }

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig checkpointConfig = new CheckpointConfig();
        LocalFileCheckpointStorage checkpointStorage = new LocalFileCheckpointStorage(
                System.getProperty("java.io.tmpdir") + "/nop-stream-checkpoint/" + jobId);
        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                jobId, "pipeline-0", idCounter, checkpointStorage, checkpointConfig);

        JobCoordinator coordinator = new JobCoordinator(
                jobId, "coordinator-" + jobId, deploymentPlan,
                clusterRegistry, checkpointCoordinator,
                taskRpcServices);

        coordinator.setFencingToken(fencingToken);

        for (TaskManager tm : taskManagers) {
            tm.setCoordinatorRpcService(coordinator);
        }

        try {
            RemoteGraphExecutionPlanBuilder planBuilder = new RemoteGraphExecutionPlanBuilder(
                    messageService, new TypeRegistry(), fencingToken, 0);
            GraphExecutionPlan plan = planBuilder.buildRemoteOnly(jobGraph, deploymentPlan, true);

            for (String vertexId : plan.getSortedVertexIds()) {
                List<Subtask> subtasks = plan.getSubtasks(vertexId);
                JobVertex vertex = plan.getExecutionVertices().get(vertexId);

                for (Subtask subtask : subtasks) {
                    int nodeIndex = subtask.getTaskIndex() % nodeCount;
                    TaskManager targetTm = taskManagers.get(nodeIndex);
                    String attemptId = UUID.randomUUID().toString();

                    TaskAssignment assignment = new TaskAssignment(
                            jobId, vertexId, subtask.getTaskIndex(),
                            targetTm.getNodeId(), attemptId, fencingToken,
                            System.currentTimeMillis());

                    clusterRegistry.assignTask(jobId, vertexId, subtask.getTaskIndex(),
                            targetTm.getNodeId(), attemptId, fencingToken);

                    targetTm.receiveAssignment(assignment);

                    targetTm.installInvokable(jobId, vertexId, subtask.getTaskIndex(), subtask.getInvokable());

                    LOG.info("Installed subtask {}/{} on node {}", vertexId, subtask.getTaskIndex(), targetTm.getNodeId());
                }
            }

            coordinator.start();

            waitForCompletion(taskManagers, 60);

            long executionTime = System.currentTimeMillis() - startTime;
            LOG.info("Embedded distributed execution completed for job {} in {}ms", jobId, executionTime);

            return new StreamExecutionResult(jobId, executionTime);

        } catch (Exception e) {
            LOG.error("Embedded distributed execution failed for job {}", jobId, e);
            throw e;
        } finally {
            coordinator.stop();
            for (TaskManager tm : taskManagers) {
                tm.stop();
            }
        }
    }

    private int determineNodeCount(PartitionedPlan partitionedPlan) {
        int maxParallelism = 1;
        for (PartitionedPlan.VertexPlan vp : partitionedPlan.getVertexPlans().values()) {
            maxParallelism = Math.max(maxParallelism, vp.getParallelism());
        }
        return Math.max(1, Math.min(defaultNodeCount, maxParallelism));
    }

    private void waitForCompletion(List<TaskManager> taskManagers, long timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;
        while (System.currentTimeMillis() < deadline) {
            int totalRunning = 0;
            for (TaskManager tm : taskManagers) {
                totalRunning += tm.getRunningTaskCount();
            }
            if (totalRunning == 0) {
                checkTaskResults(taskManagers);
                return;
            }
            Thread.sleep(100);
        }
        throw new StreamException("Timed out waiting for tasks to complete. Still running: "
                + taskManagers.stream().mapToInt(TaskManager::getRunningTaskCount).sum());
    }

    private void checkTaskResults(List<TaskManager> taskManagers) {
        List<Throwable> failures = new ArrayList<>();
        for (TaskManager tm : taskManagers) {
            for (TaskManager.TaskResult result : tm.getCompletedTaskResults().values()) {
                if (!result.isSuccess() && !result.isCanceled() && result.getError() != null) {
                    failures.add(result.getError());
                }
            }
        }
        if (!failures.isEmpty()) {
            StreamException ex = new StreamException(
                    failures.size() + " task(s) failed during distributed execution");
            for (Throwable t : failures) {
                ex.addSuppressed(t);
            }
            throw ex;
        }
    }
}
