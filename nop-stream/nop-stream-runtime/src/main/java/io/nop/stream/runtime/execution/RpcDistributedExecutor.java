/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.exceptions.StreamException;
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
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.coordinator.JobCoordinator;
import io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.rpc.StreamControlRpcProxyFactory;
import io.nop.stream.runtime.rpc.StreamControlRpcServer;
import io.nop.stream.runtime.rpc.StreamControlRpcTopics;
import io.nop.stream.runtime.taskmanager.TaskManager;
import io.nop.stream.runtime.transport.RemoteGraphExecutionPlanBuilder;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;

/**
 * Stage 39 Phase 2: distributed execution dispatcher whose control plane traverses
 * the platform RPC framework ({@link StreamControlRpcServer} +
 * {@link StreamControlRpcProxyFactory} over {@link IMessageService}) rather than direct
 * Java references.
 *
 * <p>Unlike {@link EmbeddedDistributedExecutor} (which injects in-process
 * {@code TaskManager} references directly into the {@link JobCoordinator}), this
 * dispatcher:
 * <ul>
 *   <li>exposes each {@code TaskManager} as a remote {@link IStreamTaskRpcService} on
 *       {@code nop-stream.rpc.task.{nodeId}} via a {@link StreamControlRpcServer};</li>
 *   <li>exposes the {@code JobCoordinator} as a remote
 *       {@link IStreamCoordinatorRpcService} on
 *       {@code nop-stream.rpc.coordinator.{jobId}};</li>
 *   <li>hands the coordinator a {@code Map<String, IStreamTaskRpcService>} of RPC
 *       proxies (one per nodeId), and each TaskManager an RPC proxy to the
 *       coordinator — so every control call crosses the RPC boundary;</li>
 *   <li>keeps the coordinator <b>long-lived</b>: {@link #startJob} returns a
 *       {@link DistributedJobHandle} that owns the coordinator + servers + proxies
 *       and must be {@link DistributedJobHandle#close() closed} to release them.
 *       This honours the {@code IStreamExecutionDispatcher} deferred contract
 *       ("async submit + poll dispatcher deferred to Stage 39").</li>
 * </ul>
 *
 * <p>The data plane stays in-JVM (Stage 40 wires cross-JVM data transport); the RPC
 * control-plane wiring here is what Stage 39 Phase 2 verifies.
 */
public class RpcDistributedExecutor implements IStreamExecutionDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(RpcDistributedExecutor.class);

    private final IMessageService messageService;
    private final int defaultNodeCount;
    private final long completionTimeoutSeconds;

    public RpcDistributedExecutor(IMessageService messageService) {
        this(messageService, 2, 60);
    }

    public RpcDistributedExecutor(IMessageService messageService, int defaultNodeCount, long completionTimeoutSeconds) {
        this.messageService = messageService;
        this.defaultNodeCount = defaultNodeCount;
        this.completionTimeoutSeconds = completionTimeoutSeconds;
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

    /**
     * Synchronous convenience entry that runs a job to completion over the RPC control
     * plane and tears the topology down. For long-lived / control-plane-driven
     * execution use {@link #startJob} and drive the returned handle.
     */
    @Override
    public StreamExecutionResult execute(JobGraph jobGraph, PartitionedPlan partitionedPlan,
                                         DeploymentPlan deploymentPlan) throws Exception {
        long startTime = System.currentTimeMillis();
        String jobId = partitionedPlan.getJobId();
        DistributedJobHandle handle = startJob(jobGraph, partitionedPlan, deploymentPlan);
        try {
            handle.installInvokablesAndRun(completionTimeoutSeconds);
            long executionTime = System.currentTimeMillis() - startTime;
            LOG.info("RPC distributed execution completed for job {} in {}ms", jobId, executionTime);
            return new StreamExecutionResult(jobId, executionTime);
        } finally {
            handle.close();
        }
    }

    /**
     * Builds and starts the RPC control-plane topology for a job. The returned handle
     * owns the long-lived coordinator, the per-node task RPC servers, the coordinator
     * RPC server, and all RPC proxies. The coordinator is ACTIVE and ready for control
     * calls (assignments / checkpoints / fencing rotation) which traverse real RPC.
     */
    public DistributedJobHandle startJob(JobGraph jobGraph, PartitionedPlan partitionedPlan,
                                         DeploymentPlan deploymentPlan) {
        long startTime = System.currentTimeMillis();
        String jobId = partitionedPlan.getJobId();
        long fencingEpoch = JobCoordinator.deriveHaFencingEpoch(0L, 1L);
        LOG.info("Starting RPC distributed topology for job {} (fencing epoch {})", jobId, fencingEpoch);

        int nodeCount = determineNodeCount(partitionedPlan);
        ClusterRegistry clusterRegistry = new InMemoryClusterRegistry();

        List<TaskManager> taskManagers = new ArrayList<>(nodeCount);
        List<StreamControlRpcServer> taskServers = new ArrayList<>(nodeCount);
        List<StreamControlRpcProxyFactory> coordinatorProxies = new ArrayList<>(nodeCount);
        // coordinator → task RPC proxies (one per nodeId)
        Map<String, IStreamTaskRpcService> taskRpcProxies = new LinkedHashMap<>();

        for (int i = 0; i < nodeCount; i++) {
            String nodeId = "node-" + i;
            String endpoint = "rpc:" + nodeId;
            String controlTopic = "nop-stream.control." + jobId;
            TaskManager tm = new TaskManager(nodeId, endpoint, 16, messageService, clusterRegistry, controlTopic);
            tm.updateFencingToken(fencingEpoch);
            tm.start();

            // Task side: expose IStreamTaskRpcService over RPC.
            StreamControlRpcServer taskServer = new StreamControlRpcServer(
                    "streamTaskRpc@" + nodeId, IStreamTaskRpcService.class, tm,
                    messageService, StreamControlRpcTopics.taskTopic(nodeId));
            taskServer.start();

            // Coordinator side: build an RPC proxy to this task node.
            StreamControlRpcProxyFactory taskProxy = new StreamControlRpcProxyFactory(
                    "streamTaskRpc@" + nodeId, IStreamTaskRpcService.class,
                    messageService, StreamControlRpcTopics.taskTopic(nodeId));
            taskProxy.start();

            taskManagers.add(tm);
            taskServers.add(taskServer);
            coordinatorProxies.add(taskProxy);
            taskRpcProxies.put(nodeId, taskProxy.getProxy());
        }

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig checkpointConfig = new CheckpointConfig();
        LocalFileCheckpointStorage checkpointStorage = new LocalFileCheckpointStorage(
                System.getProperty("java.io.tmpdir") + "/nop-stream-checkpoint/" + jobId);
        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                jobId, "pipeline-0", idCounter, checkpointStorage, checkpointConfig);

        JobCoordinator coordinator = new JobCoordinator(
                jobId, "coordinator-" + jobId, deploymentPlan,
                clusterRegistry, checkpointCoordinator, taskRpcProxies);
        coordinator.setFencingEpoch(fencingEpoch);
        coordinator.setAutoRecoverOnFailedReport(false);
        // Stage 39 Phase 3: the RPC-distributed form uses the DISTRIBUTED abort
        // path (coordinator abort handler → cancelTask RPC → remote task). The
        // embedded GraphModelCheckpointExecutor uses its LOCAL abort handler; the
        // two coexist (Phase 3 Decision).
        coordinator.registerDistributedAbortHandler();

        // Coordinator side: expose IStreamCoordinatorRpcService over RPC.
        StreamControlRpcServer coordinatorServer = new StreamControlRpcServer(
                "streamCoordinatorRpc@" + jobId, IStreamCoordinatorRpcService.class, coordinator,
                messageService, StreamControlRpcTopics.coordinatorTopic(jobId));

        // Each task gets an RPC proxy to the coordinator.
        StreamControlRpcProxyFactory coordinatorProxy = new StreamControlRpcProxyFactory(
                "streamCoordinatorRpc@" + jobId, IStreamCoordinatorRpcService.class,
                messageService, StreamControlRpcTopics.coordinatorTopic(jobId));
        IStreamCoordinatorRpcService coordinatorProxyIf = coordinatorProxy.getProxy();
        for (TaskManager tm : taskManagers) {
            tm.setCoordinatorRpcService(coordinatorProxyIf);
        }

        // Build the (in-JVM) data-plane plan keyed on the fencing epoch.
        RemoteGraphExecutionPlanBuilder planBuilder = new RemoteGraphExecutionPlanBuilder(
                messageService, new TypeRegistry(), fencingEpoch);
        GraphExecutionPlan plan = planBuilder.buildRemoteOnly(jobGraph, deploymentPlan, true);

        // Start servers + coordinator (non-HA: derives epoch, goes ACTIVE).
        coordinatorServer.start();
        coordinatorProxy.start();
        coordinator.start();
        coordinator.assignTasks();

        LOG.info("RPC distributed topology ready for job {} (fencing epoch {}) in {}ms",
                jobId, fencingEpoch, System.currentTimeMillis() - startTime);

        return new DistributedJobHandle(jobId, coordinator, taskManagers, taskServers,
                coordinatorServer, coordinatorProxies, coordinatorProxy, plan, startTime);
    }

    private int determineNodeCount(PartitionedPlan partitionedPlan) {
        int maxParallelism = 1;
        for (PartitionedPlan.VertexPlan vp : partitionedPlan.getVertexPlans().values()) {
            maxParallelism = Math.max(maxParallelism, vp.getParallelism());
        }
        return Math.max(1, Math.min(defaultNodeCount, maxParallelism));
    }

    /**
     * Owns the long-lived coordinator + RPC servers + proxies for one job. Control
     * calls on {@link #getCoordinator()} traverse real RPC to the remote task nodes
     * (the coordinator's {@code IStreamTaskRpcService} map holds RPC proxies).
     */
    public static final class DistributedJobHandle implements AutoCloseable {
        private final String jobId;
        private final JobCoordinator coordinator;
        private final List<TaskManager> taskManagers;
        private final List<StreamControlRpcServer> taskServers;
        private final StreamControlRpcServer coordinatorServer;
        private final List<StreamControlRpcProxyFactory> taskProxies;
        private final StreamControlRpcProxyFactory coordinatorProxy;
        private final GraphExecutionPlan plan;
        private final long startTime;

        DistributedJobHandle(String jobId, JobCoordinator coordinator, List<TaskManager> taskManagers,
                             List<StreamControlRpcServer> taskServers, StreamControlRpcServer coordinatorServer,
                             List<StreamControlRpcProxyFactory> taskProxies,
                             StreamControlRpcProxyFactory coordinatorProxy, GraphExecutionPlan plan, long startTime) {
            this.jobId = jobId;
            this.coordinator = coordinator;
            this.taskManagers = taskManagers;
            this.taskServers = taskServers;
            this.coordinatorServer = coordinatorServer;
            this.taskProxies = taskProxies;
            this.coordinatorProxy = coordinatorProxy;
            this.plan = plan;
            this.startTime = startTime;
        }

        public String getJobId() {
            return jobId;
        }

        public JobCoordinator getCoordinator() {
            return coordinator;
        }

        public List<TaskManager> getTaskManagers() {
            return taskManagers;
        }

        public GraphExecutionPlan getPlan() {
            return plan;
        }

        /**
         * Installs the data-plane invokables on the (RPC-reached) task nodes based on
         * the coordinator's assignments, then waits for the pipeline to drain.
         */
        public void installInvokablesAndRun(long timeoutSeconds) throws Exception {
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
                                "No TaskManager for nodeId=" + ta.getNodeId());
                    }
                    targetTm.installInvokable(jobId, vertexId, subtask.getTaskIndex(), subtask.getInvokable());
                }
            }
            waitForCompletion(taskManagers, timeoutSeconds);
        }

        @Override
        public void close() {
            try {
                coordinator.stop();
            } catch (Exception e) {
                LOG.error("Failed to stop coordinator for job {}", jobId, e);
            }
            for (StreamControlRpcServer srv : taskServers) {
                try {
                    srv.stop();
                } catch (Exception e) {
                    LOG.error("Failed to stop task RPC server", e);
                }
            }
            for (StreamControlRpcProxyFactory p : taskProxies) {
                try {
                    p.stop();
                } catch (Exception e) {
                    LOG.error("Failed to stop task RPC proxy", e);
                }
            }
            try {
                coordinatorServer.stop();
            } catch (Exception e) {
                LOG.error("Failed to stop coordinator RPC server", e);
            }
            try {
                coordinatorProxy.stop();
            } catch (Exception e) {
                LOG.error("Failed to stop coordinator RPC proxy", e);
            }
            for (TaskManager tm : taskManagers) {
                try {
                    tm.stop();
                } catch (Exception e) {
                    LOG.error("Failed to stop task manager {}", tm.getNodeId(), e);
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

        private void waitForCompletion(List<TaskManager> taskManagers, long timeoutSeconds) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;
            while (System.currentTimeMillis() < deadline) {
                int totalRunning = 0;
                for (TaskManager tm : taskManagers) {
                    totalRunning += tm.getRunningTaskCount();
                }
                if (totalRunning == 0) {
                    return;
                }
                Thread.sleep(100);
            }
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "Timed out waiting for tasks to complete. Still running: "
                            + taskManagers.stream().mapToInt(TaskManager::getRunningTaskCount).sum());
        }
    }
}
