/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.time.CoreMetrics;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import io.nop.stream.core.execution.plan.DeploymentAssignment;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.rpc.TaskDeploymentDescriptor;

/**
 * ST-8 (plan 357 Phase 3): the assignment-planning collaborator of
 * {@link JobCoordinator} — extracted as a pure move from the coordinator's
 * assignment-related private method group. The coordinator keeps the lifecycle
 * gates (running / standby / FAILED checks, recovery lock ownership, recovery
 * budgets); this class owns the assignment materialization and RPC fan-out
 * mechanics.
 *
 * <p>The planner holds the <b>shared</b> working-set instances (the task
 * assignment map, the all-task-location set, the attempt counters, and the
 * fencing epoch holder) — they are the SAME instances the coordinator owns, so
 * mutations here are immediately visible there and the original interleaving of
 * registry writes and working-set updates is preserved. The remote-deploy
 * configuration ({@code remoteDeployMode} / job graph / pipeline spec /
 * checkpoint storage path) is volatile on the coordinator and is passed as
 * per-call parameters by each call site.
 */
@Internal
class AssignmentPlanner {

    private static final Logger LOG = LoggerFactory.getLogger(AssignmentPlanner.class);

    private final String jobId;
    private final DeploymentPlan deploymentPlan;
    private final ClusterRegistry clusterRegistry;
    private final CheckpointCoordinator checkpointCoordinator;
    private final Map<String, IStreamTaskRpcService> taskRpcServices;
    private final AtomicLong fencingEpoch;

    /** Shared with the coordinator: vertexId → subtaskIndex-ordered assignments. */
    private final Map<String, List<TaskAssignment>> taskAssignmentMap;

    /** Shared with the coordinator: task locations that need to ACK the current checkpoint. */
    private final Set<TaskLocation> allTaskLocations;

    /** Shared with the coordinator: per-subtask attempt counters ("{vertexId}/{subtaskIndex}"). */
    private final Map<String, Integer> attemptCounters;

    AssignmentPlanner(String jobId,
                      DeploymentPlan deploymentPlan,
                      ClusterRegistry clusterRegistry,
                      CheckpointCoordinator checkpointCoordinator,
                      Map<String, IStreamTaskRpcService> taskRpcServices,
                      AtomicLong fencingEpoch,
                      Map<String, List<TaskAssignment>> taskAssignmentMap,
                      Set<TaskLocation> allTaskLocations,
                      Map<String, Integer> attemptCounters) {
        this.jobId = jobId;
        this.deploymentPlan = deploymentPlan;
        this.clusterRegistry = clusterRegistry;
        this.checkpointCoordinator = checkpointCoordinator;
        this.taskRpcServices = taskRpcServices;
        this.fencingEpoch = fencingEpoch;
        this.taskAssignmentMap = taskAssignmentMap;
        this.allTaskLocations = allTaskLocations;
        this.attemptCounters = attemptCounters;
    }

    /**
     * P1 hardening: materializes the full assignment into the in-memory working set
     * ({@link ClusterRegistry#assignTask}, {@code taskAssignmentMap},
     * {@code allTaskLocations}, {@link CheckpointCoordinator#setTasksToAcknowledge})
     * and returns the list of RPC dispatches to execute. MUST be called while holding
     * the coordinator's {@code recoveryLock} so the clear → register → assign sequence
     * is atomic with respect to concurrent recovery drivers.
     *
     * <p>The returned {@link AssignmentDispatch} list captures, per subtask, the RPC
     * target, the {@link TaskAssignment} (in-process path) or {@link TaskDeploymentDescriptor}
     * (remote-deploy path), and the fencing epoch the assignment was materialized under.
     * The caller issues the RPCs after releasing the lock.
     */
    List<AssignmentDispatch> prepareAssignmentsLocked(boolean remoteDeployMode, JobGraph jobGraph,
                                                      io.nop.stream.runtime.rpc.RemotePipelineSpec pipelineSpec,
                                                      String checkpointStoragePath) {
        long epoch = fencingEpoch.get();

        DeploymentAssignment assignment = deploymentPlan != null ? deploymentPlan.getAssignment() : null;
        boolean useMaterialized = assignment != null && !assignment.isEmpty();

        List<NodeInfo> activeNodes = useMaterialized ? null : clusterRegistry.getActiveNodes();
        if (!useMaterialized && activeNodes.isEmpty()) {
            LOG.warn("No active nodes available for task assignment");
            return Collections.emptyList();
        }

        int activeNodeCount = useMaterialized ? -1 : activeNodes.size();
        int runtimeNodeIndex = 0;

        List<TaskLocation> locations = new ArrayList<>();
        List<AssignmentDispatch> dispatches = new ArrayList<>();

        if (deploymentPlan != null && deploymentPlan.getPartitionedPlan() != null) {
            for (Map.Entry<String, io.nop.stream.core.execution.plan.PartitionedPlan.VertexPlan> entry :
                    deploymentPlan.getPartitionedPlan().getVertexPlans().entrySet()) {
                String vertexId = entry.getKey();
                int parallelism = entry.getValue().getParallelism();

                List<TaskAssignment> vertexAssignments = new ArrayList<>(parallelism);

                for (int subtaskIndex = 0; subtaskIndex < parallelism; subtaskIndex++) {
                    String targetNodeId;
                    if (useMaterialized) {
                        targetNodeId = assignment.getNodeForSubtask(vertexId, subtaskIndex);
                        if (targetNodeId == null) {
                            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                                    "DeploymentAssignment has no node mapping for vertex=" + vertexId
                                            + " subtaskIndex=" + subtaskIndex
                                            + ". The assignment is incomplete.");
                        }
                    } else {
                        NodeInfo targetNode = activeNodes.get(runtimeNodeIndex % activeNodeCount);
                        targetNodeId = targetNode.getNodeId();
                        runtimeNodeIndex++;
                    }

                    String attemptId = UUID.randomUUID().toString();
                    // G56: per-subtask monotonically-increasing attempt number
                    String attemptKey = vertexId + "/" + subtaskIndex;
                    int attemptNumber = attemptCounters.computeIfAbsent(attemptKey, k -> 0) + 1;
                    attemptCounters.put(attemptKey, attemptNumber);

                    TaskAssignment taskAssignment = new TaskAssignment(
                            jobId, vertexId, subtaskIndex,
                            targetNodeId, attemptId, epoch,
                            CoreMetrics.currentTimeMillis(), attemptNumber);

                    clusterRegistry.assignTask(
                            jobId, vertexId, subtaskIndex,
                            targetNodeId, attemptId, epoch, attemptNumber);

                    IStreamTaskRpcService rpc = taskRpcServices.get(targetNodeId);
                    if (rpc == null) {
                        throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                                "No RPC service for node " + targetNodeId
                                        + ". All control plane operations require IStreamTaskRpcService.");
                    }

                    TaskDeploymentDescriptor descriptor = null;
                    if (remoteDeployMode) {
                        // Stage 42 Phase 0: send a self-contained deployment
                        // descriptor; the TaskManager rebuilds its own invokable
                        // locally. receiveAssignment is NOT called separately.
                        // The descriptor is captured here and dispatched after the
                        // lock is released (no blocking IO under the recovery lock).
                        // Item 14: when a pipeline spec is set it ships INSTEAD of
                        // the compiled graph (XDSL pipelines are not
                        // Java-serializable; TMs rebuild identical graphs locally).
                        descriptor = new TaskDeploymentDescriptor(
                                jobId, vertexId, subtaskIndex, targetNodeId,
                                attemptId, attemptNumber, epoch,
                                pipelineSpec != null ? null : jobGraph, deploymentPlan, checkpointStoragePath);
                        descriptor.setPipelineSpec(pipelineSpec);
                    }
                    dispatches.add(new AssignmentDispatch(epoch, rpc, taskAssignment, descriptor));

                    vertexAssignments.add(taskAssignment);
                    locations.add(new TaskLocation(jobId, "pipeline-0", vertexId, subtaskIndex));
                }

                taskAssignmentMap.put(vertexId, vertexAssignments);
            }
        }

        allTaskLocations.addAll(locations);
        checkpointCoordinator.setTasksToAcknowledge(locations);

        LOG.info("Assigned {} tasks for job {} (mode={}, deploy={}, source={})",
                locations.size(), jobId,
                useMaterialized ? "materialized" : "runtime-round-robin",
                remoteDeployMode ? "remote" : "in-process",
                useMaterialized ? "DeploymentPlan.assignment" : "ClusterRegistry");

        return dispatches;
    }

    /**
     * P1 hardening: issues the per-subtask assignment RPCs. Executed OUTSIDE
     * the coordinator's {@code recoveryLock} so a slow/unreachable TaskManager cannot
     * block a concurrent recovery driver. Each dispatch carries the exact fencing epoch
     * it was materialized under, so a stale fan-out (the epoch was rotated by a later
     * recovery between unlock and dispatch) is rejected at the data plane.
     */
    void executeAssignmentFanOut(List<AssignmentDispatch> dispatches) {
        for (AssignmentDispatch d : dispatches) {
            // Per-dispatch containment (mirrors triggerCheckpoint/sendBarrierToAllTaskManagers):
            // one unreachable TaskManager must not abort the remaining fan-out and
            // leave a partially-assigned job; the next failure-detection /
            // recovery cycle re-drives whatever this loop could not deliver.
            try {
                if (d.descriptor != null) {
                    d.rpc.deployTask(d.descriptor, d.epoch);
                } else {
                    d.rpc.receiveAssignment(d.taskAssignment);
                }
            } catch (Exception e) {
                LOG.error("Failed to dispatch assignment for {} (epoch {})",
                        d.descriptor != null
                                ? d.descriptor.getVertexId() + "/" + d.descriptor.getSubtaskIndex()
                                : d.taskAssignment.getVertexId() + "/" + d.taskAssignment.getSubtaskIndex(),
                        d.epoch, e);
            }
        }
    }

    /**
     * Item 14: node ids that currently host at least one assigned subtask (the
     * barrier / commit-notification fan-out set).
     */
    Set<String> computeAssignedNodeIds() {
        Set<String> nodeIds = new HashSet<>();
        for (List<TaskAssignment> assignments : taskAssignmentMap.values()) {
            for (TaskAssignment assignment : assignments) {
                nodeIds.add(assignment.getNodeId());
            }
        }
        // Fallback for the window between start() and the first assignTasks():
        // every configured RPC service is a candidate target. Triggering in that
        // window is rejected by the CheckpointCoordinator (no tasks to ack), so
        // this only affects nodes that were configured but never assigned.
        if (nodeIds.isEmpty()) {
            nodeIds.addAll(taskRpcServices.keySet());
        }
        return nodeIds;
    }

    /**
     * P1 hardening: captures a single subtask's assignment RPC so the fan-out can
     * run outside the recovery lock. Built under the lock; executed after release.
     */
    static final class AssignmentDispatch {
        final long epoch;
        final IStreamTaskRpcService rpc;
        final TaskAssignment taskAssignment;
        /** Non-null in remote-deploy mode; null in the in-process receiveAssignment path. */
        final TaskDeploymentDescriptor descriptor;

        AssignmentDispatch(long epoch, IStreamTaskRpcService rpc,
                           TaskAssignment taskAssignment, TaskDeploymentDescriptor descriptor) {
            this.epoch = epoch;
            this.rpc = rpc;
            this.taskAssignment = taskAssignment;
            this.descriptor = descriptor;
        }
    }
}
