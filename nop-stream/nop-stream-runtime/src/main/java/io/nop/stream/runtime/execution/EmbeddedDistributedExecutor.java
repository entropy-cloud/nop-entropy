/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.message.IMessageService;
import io.nop.cluster.naming.INamingService;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import io.nop.stream.core.execution.DeploymentMode;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.IStreamExecutionDispatcher;
import io.nop.stream.core.execution.Subtask;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.cluster.StreamNodeAutoRegistration;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.coordinator.JobCoordinator;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.taskmanager.TaskManager;
import io.nop.stream.runtime.transport.RemoteGraphExecutionPlanBuilder;

public class EmbeddedDistributedExecutor implements IStreamExecutionDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedDistributedExecutor.class);

    private final IMessageService messageService;
    private final int defaultNodeCount;
    private final long completionTimeoutSeconds;
    private final INamingService namingService;

    public EmbeddedDistributedExecutor(IMessageService messageService) {
        this(messageService, 2);
    }

    public EmbeddedDistributedExecutor(IMessageService messageService, int defaultNodeCount) {
        this(messageService, defaultNodeCount, 60);
    }

    public EmbeddedDistributedExecutor(IMessageService messageService, int defaultNodeCount,
                                       long completionTimeoutSeconds) {
        this(messageService, defaultNodeCount, completionTimeoutSeconds, null);
    }

    /**
     * @param namingService optional platform naming service for node discovery registration (G51).
     *                      When non-null, each embedded node is registered with platform discovery
     *                      alongside its ClusterRegistry registration. When null, discovery
     *                      registration is skipped (backward compatible).
     */
    public EmbeddedDistributedExecutor(IMessageService messageService, int defaultNodeCount,
                                       long completionTimeoutSeconds, INamingService namingService) {
        this.messageService = messageService;
        this.defaultNodeCount = defaultNodeCount;
        this.completionTimeoutSeconds = completionTimeoutSeconds;
        this.namingService = namingService;
    }

    @Override
    public boolean supportsDeploymentMode(DeploymentMode mode) {
        return mode == DeploymentMode.DISTRIBUTED;
    }

    @Override
    public List<String> getExpectedNodeIds(PartitionedPlan partitionedPlan) {
        int nodeCount = determineNodeCount(partitionedPlan);
        List<String> nodeIds = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            nodeIds.add("node-" + i);
        }
        return nodeIds;
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
        List<StreamNodeAutoRegistration> discoveryRegistrations = new ArrayList<>(nodeCount);

        for (int i = 0; i < nodeCount; i++) {
            String nodeId = "node-" + i;
            String endpoint = "embedded:" + nodeId;
            String controlTopic = "nop-stream.control." + jobId;
            TaskManager tm = new TaskManager(nodeId, endpoint, 16,
                    messageService, clusterRegistry, controlTopic);
            tm.updateFencingToken(fencingToken);
            taskManagers.add(tm);
            taskRpcServices.put(nodeId, tm);
        }

        for (TaskManager tm : taskManagers) {
            tm.start();
        }

        // Register each node with platform discovery (G51) when a naming service is available.
        // This coexists with ClusterRegistry lease registration — discovery is single-direction
        // (nop-stream → platform), ClusterRegistry remains the runtime source of truth.
        if (namingService != null) {
            for (TaskManager tm : taskManagers) {
                StreamNodeAutoRegistration reg = new StreamNodeAutoRegistration(
                        namingService, tm.getNodeId(), "embedded:" + tm.getNodeId(), 16);
                reg.start();
                discoveryRegistrations.add(reg);
            }
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

            // Start coordinator before assigning tasks
            coordinator.start();

            // Consume the materialized DeploymentPlan assignment (G50) instead of ad-hoc
            // direct assignment. The DeploymentPlan now carries the subtask→node mapping
            // generated by the distributed IDeploymentPlanProvider.
            coordinator.assignTasks();

            // Install invokables on the target nodes based on the coordinator's assignments.
            // assignTasks() has already sent receiveAssignment() to each TaskManager, so the
            // RunningTask slots exist; we now populate them with the invokable logic.
            Map<String, List<TaskAssignment>> assignments = coordinator.getTaskAssignments();
            for (String vertexId : plan.getSortedVertexIds()) {
                List<Subtask> subtasks = plan.getSubtasks(vertexId);
                List<TaskAssignment> vertexAssignments = assignments.get(vertexId);

                for (Subtask subtask : subtasks) {
                    TaskAssignment ta = findAssignment(vertexAssignments, subtask.getTaskIndex());
                    if (ta == null) {
                        throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                                "No assignment found for vertex=" + vertexId
                                        + " subtaskIndex=" + subtask.getTaskIndex()
                                        + " after coordinator.assignTasks()");
                    }

                    TaskManager targetTm = findTaskManager(taskManagers, ta.getNodeId());
                    if (targetTm == null) {
                        throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                                "No TaskManager for nodeId=" + ta.getNodeId()
                                        + " (vertex=" + vertexId + " subtaskIndex=" + subtask.getTaskIndex() + ")");
                    }

                    targetTm.installInvokable(jobId, vertexId, subtask.getTaskIndex(), subtask.getInvokable());

                    LOG.info("Installed subtask {}/{} on node {}", vertexId, subtask.getTaskIndex(), targetTm.getNodeId());
                }
            }

            waitForCompletion(taskManagers, completionTimeoutSeconds);

            long executionTime = System.currentTimeMillis() - startTime;
            LOG.info("Embedded distributed execution completed for job {} in {}ms", jobId, executionTime);

            return new StreamExecutionResult(jobId, executionTime);

        } catch (Exception e) {
            LOG.error("Embedded distributed execution failed for job {}", jobId, e);
            throw e;
        } finally {
            try {
                coordinator.stop();
            } catch (Exception e) {
                LOG.error("Failed to stop coordinator for job {}", jobId, e);
            }
            for (StreamNodeAutoRegistration reg : discoveryRegistrations) {
                try {
                    reg.stop();
                } catch (Exception e) {
                    LOG.error("Failed to unregister node from discovery", e);
                }
            }
            for (TaskManager tm : taskManagers) {
                try {
                    tm.stop();
                } catch (Exception e) {
                    LOG.error("Failed to stop task manager {}", tm.getNodeId(), e);
                }
            }
        }
    }

    private TaskAssignment findAssignment(List<TaskAssignment> assignments, int subtaskIndex) {
        if (assignments == null) {
            return null;
        }
        for (TaskAssignment ta : assignments) {
            if (ta.getSubtaskIndex() == subtaskIndex) {
                return ta;
            }
        }
        return null;
    }

    private TaskManager findTaskManager(List<TaskManager> taskManagers, String nodeId) {
        for (TaskManager tm : taskManagers) {
            if (tm.getNodeId().equals(nodeId)) {
                return tm;
            }
        }
        return null;
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
        throw new StreamException(ERR_STREAM_INVALID_STATE)
                .param(ARG_DETAIL, "Timed out waiting for tasks to complete. Still running: "
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
