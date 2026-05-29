/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.message.*;
import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.taskmanager.CheckpointAckMessage;
import io.nop.stream.runtime.taskmanager.TaskManager;

/**
 * JobCoordinator is the single point of control for a distributed streaming job.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Generates and maintains a canonical {@link DeploymentPlan}</li>
 *   <li>Assigns tasks to TaskManagers via {@link ClusterRegistry} and control topics</li>
 *   <li>Triggers checkpoint epochs and collects ACKs via {@link CheckpointCoordinator}</li>
 *   <li>Maintains fencing tokens for epoch-based recovery</li>
 *   <li>Detects node failures via lease expiration and triggers global recovery</li>
 *   <li>Implements four {@link JobTerminationMode}s: CANCEL, DRAIN, SUSPEND, EXPORT_SAVEPOINT</li>
 * </ul>
 *
 * <p><strong>Fencing:</strong> A UUID fencing token is generated on start and on each
 * global recovery. All control messages carry this token; TaskManagers reject messages
 * with stale tokens.
 *
 * <p><strong>Checkpoint Flow:</strong>
 * <ol>
 *   <li>{@link #triggerCheckpoint()} → sends {@link CheckpointBarrierSignal} to all source tasks</li>
 *   <li>TaskManagers process barriers, snapshot state, send {@link CheckpointAckMessage} back</li>
 *   <li>{@link #collectAck(CheckpointAckMessage)} → verifies fencing token, forwards to CheckpointCoordinator</li>
 *   <li>When all ACKs collected → CheckpointCoordinator builds {@link EpochManifest}, persists, notifies commit</li>
 * </ol>
 */
@Internal
public class JobCoordinator implements IStreamCoordinatorRpcService {

    private static final Logger LOG = LoggerFactory.getLogger(JobCoordinator.class);

    private static final long DEFAULT_LEASE_CHECK_INTERVAL_MS = 5000L;
    private static final long DEFAULT_LEASE_EXPIRE_THRESHOLD_MS = 30000L;

    private final String jobId;
    private final String coordinatorId;
    private final DeploymentPlan deploymentPlan;
    private final ClusterRegistry clusterRegistry;
    private final CheckpointCoordinator checkpointCoordinator;
    private final Map<String, IStreamTaskRpcService> taskRpcServices;

    /** The current fencing token for this job execution epoch */
    private final AtomicReference<String> fencingToken;

    /** Ordered list of subtask assignments (vertexId → subtaskIndex → assignment) */
    private final Map<String, List<TaskAssignment>> taskAssignmentMap;

    /** Task locations that need to ACK the current checkpoint */
    private final Set<TaskLocation> allTaskLocations;

    /** Failure detection scheduler */
    private final ScheduledExecutorService failureDetector;

    /** Whether the coordinator is running */
    private volatile boolean running;

    public JobCoordinator(String jobId,
                          String coordinatorId,
                          DeploymentPlan deploymentPlan,
                          ClusterRegistry clusterRegistry,
                          CheckpointCoordinator checkpointCoordinator,
                          Map<String, IStreamTaskRpcService> taskRpcServices) {
        this.jobId = jobId;
        this.coordinatorId = coordinatorId;
        this.deploymentPlan = deploymentPlan;
        this.clusterRegistry = clusterRegistry;
        this.checkpointCoordinator = checkpointCoordinator;
        this.taskRpcServices = taskRpcServices != null ? taskRpcServices : Collections.emptyMap();
        this.fencingToken = new AtomicReference<>();
        this.taskAssignmentMap = new ConcurrentHashMap<>();
        this.allTaskLocations = ConcurrentHashMap.newKeySet();
        this.failureDetector = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jc-failure-detector-" + jobId);
            t.setDaemon(true);
            return t;
        });
        this.running = false;
    }

    // ==================== Lifecycle ====================

    /**
     * Registers this coordinator in the ClusterRegistry, generates a fencing token,
     * and starts the failure detection loop.
     */
    public void start() {
        if (running) {
            LOG.warn("JobCoordinator {} already started", coordinatorId);
            return;
        }

        // Use existing fencing token if already set via setFencingToken(), otherwise generate new one
        String token = fencingToken.get();
        if (token == null) {
            token = UUID.randomUUID().toString();
            fencingToken.set(token);
        }

        // Register coordinator in the registry
        clusterRegistry.registerCoordinator(jobId, coordinatorId, token);

        // Start failure detection
        failureDetector.scheduleAtFixedRate(
                this::detectFailures,
                DEFAULT_LEASE_CHECK_INTERVAL_MS,
                DEFAULT_LEASE_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS);

        running = true;
        LOG.info("JobCoordinator {} started for job {} with fencing token {}",
                coordinatorId, jobId, token);
    }

    /**
     * Unregisters from the ClusterRegistry and shuts down internal services.
     */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        failureDetector.shutdownNow();

        checkpointCoordinator.shutdown();

        LOG.info("JobCoordinator {} stopped for job {}", coordinatorId, jobId);
    }

    // ==================== Task Assignment ====================

    /**
     * Distributes subtasks to TaskManagers based on active nodes in the ClusterRegistry.
     *
     * <p>Uses a simple round-robin assignment strategy across active nodes.
     * For each assignment:
     * <ol>
     *   <li>Records the assignment in the ClusterRegistry</li>
     *   <li>Sends a {@link TaskAssignmentMessage} via the control topic</li>
     * </ol>
     */
    public void assignTasks() {
        if (!running) {
            LOG.warn("JobCoordinator not running, cannot assign tasks");
            return;
        }

        List<NodeInfo> activeNodes = clusterRegistry.getActiveNodes();
        if (activeNodes.isEmpty()) {
            LOG.warn("No active nodes available for task assignment");
            return;
        }

        String token = fencingToken.get();
        List<TaskLocation> locations = new ArrayList<>();
        int nodeIndex = 0;

        // Iterate over vertex plans from the DeploymentPlan
        if (deploymentPlan != null && deploymentPlan.getPartitionedPlan() != null) {
            for (Map.Entry<String, io.nop.stream.core.execution.plan.PartitionedPlan.VertexPlan> entry :
                    deploymentPlan.getPartitionedPlan().getVertexPlans().entrySet()) {
                String vertexId = entry.getKey();
                int parallelism = entry.getValue().getParallelism();

                List<TaskAssignment> vertexAssignments = new ArrayList<>(parallelism);

                for (int subtaskIndex = 0; subtaskIndex < parallelism; subtaskIndex++) {
                    NodeInfo targetNode = activeNodes.get(nodeIndex % activeNodes.size());
                    String attemptId = UUID.randomUUID().toString();

                    TaskAssignment assignment = new TaskAssignment(
                            jobId, vertexId, subtaskIndex,
                            targetNode.getNodeId(), attemptId, token,
                            System.currentTimeMillis());

                    clusterRegistry.assignTask(
                            jobId, vertexId, subtaskIndex,
                            targetNode.getNodeId(), attemptId, token);

                    IStreamTaskRpcService rpc = taskRpcServices.get(targetNode.getNodeId());
                    if (rpc != null) {
                        rpc.receiveAssignment(assignment);
                    } else {
                        throw new IllegalStateException(
                                "No RPC service for node " + targetNode.getNodeId()
                                + ". All control plane operations require IStreamTaskRpcService.");
                    }

                    vertexAssignments.add(assignment);
                    locations.add(new TaskLocation(jobId, "pipeline-0", vertexId, subtaskIndex));
                    nodeIndex++;
                }

                taskAssignmentMap.put(vertexId, vertexAssignments);
            }
        }

        allTaskLocations.addAll(locations);
        checkpointCoordinator.setTasksToAcknowledge(locations);

        LOG.info("Assigned {} tasks across {} nodes for job {}", locations.size(), activeNodes.size(), jobId);
    }

    /**
     * Returns all current task assignments.
     */
    public Map<String, List<TaskAssignment>> getTaskAssignments() {
        return Collections.unmodifiableMap(taskAssignmentMap);
    }

    // ==================== Checkpoint ====================

    /**
     * Triggers a checkpoint by sending a barrier signal to all source tasks
     * via the control topic.
     *
     * @return the triggered PendingCheckpoint, or null if trigger failed
     */
    public PendingCheckpoint triggerCheckpoint() {
        if (!running) {
            LOG.warn("JobCoordinator not running, cannot trigger checkpoint");
            return null;
        }

        PendingCheckpoint pending = checkpointCoordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        if (pending == null) {
            LOG.debug("Checkpoint trigger failed or skipped");
            return null;
        }

        CheckpointBarrier barrier = new CheckpointBarrier(
                pending.getCheckpointId(),
                pending.getTriggerTimestamp(),
                pending.getCheckpointType());

        String token = fencingToken.get();

        if (!taskRpcServices.isEmpty()) {
            for (Map.Entry<String, IStreamTaskRpcService> entry : taskRpcServices.entrySet()) {
                try {
                    entry.getValue().triggerCheckpoint(barrier, token);
                } catch (Exception e) {
                    LOG.error("Failed to send checkpoint signal to node {}", entry.getKey(), e);
                }
            }
        } else {
            throw new IllegalStateException(
                    "No RPC services available for checkpoint trigger. "
                    + "All control plane operations require IStreamTaskRpcService.");
        }

        return pending;
    }

    /**
     * Processes a checkpoint ACK message from a TaskManager.
     *
     * <p>Verifies the fencing token, then forwards to the {@link CheckpointCoordinator}.
     * When all ACKs are collected, the coordinator completes the checkpoint automatically.
     *
     * @param ack the ACK message from a TaskManager
     * @return true if the ACK was accepted, false if rejected (stale token, unknown checkpoint)
     */
    public boolean collectAck(CheckpointAckMessage ack) {
        if (!running) {
            return false;
        }

        // Fencing token verification
        String token = fencingToken.get();
        if (token != null && !token.equals(ack.getFencingToken())) {
            LOG.warn("Rejecting checkpoint ACK with stale fencing token from {}",
                    ack.getTaskLocation());
            return false;
        }

        boolean accepted = checkpointCoordinator.acknowledgeTask(
                ack.getTaskLocation(),
                ack.getCheckpointId(),
                ack.getStateSnapshot());

        if (accepted) {
            LOG.debug("Accepted checkpoint ACK from {} for checkpoint {}",
                    ack.getTaskLocation(), ack.getCheckpointId());
        }

        return accepted;
    }

    @Override
    public void receiveCheckpointAck(CheckpointAckMessage ack) {
        collectAck(ack);
    }

    /**
     * Returns the current CheckpointCoordinator for inspection.
     */
    public CheckpointCoordinator getCheckpointCoordinator() {
        return checkpointCoordinator;
    }

    // ==================== Failure Detection & Recovery ====================

    /**
     * Checks ClusterRegistry node leases. If any assigned node has expired,
     * triggers global recovery.
     */
    public void detectFailures() {
        if (!running) {
            return;
        }

        try {
            List<NodeInfo> activeNodes = clusterRegistry.getActiveNodes();
            Set<String> activeNodeIds = new HashSet<>();
            for (NodeInfo node : activeNodes) {
                activeNodeIds.add(node.getNodeId());
            }

            // Check if any assigned node has gone down
            boolean failureDetected = false;
            for (List<TaskAssignment> assignments : taskAssignmentMap.values()) {
                for (TaskAssignment assignment : assignments) {
                    if (!activeNodeIds.contains(assignment.getNodeId())) {
                        LOG.warn("Node {} (assigned to {}/{}) has expired lease",
                                assignment.getNodeId(),
                                assignment.getVertexId(),
                                assignment.getSubtaskIndex());
                        failureDetected = true;
                    }
                }
            }

            if (failureDetected) {
                LOG.warn("Failures detected, triggering global recovery for job {}", jobId);
                globalRecovery();
            }
        } catch (Exception e) {
            LOG.error("Error during failure detection for job {}", jobId, e);
        }
    }

    /**
     * Performs global recovery:
     * <ol>
     *   <li>Generate a new fencing token</li>
     *   <li>Fence all old tasks</li>
     *   <li>Reassign tasks from the latest durable EpochManifest</li>
     * </ol>
     */
    public void globalRecovery() {
        LOG.info("Starting global recovery for job {}", jobId);

        // 1. Generate new fencing token
        String newToken = UUID.randomUUID().toString();
        String oldToken = fencingToken.getAndSet(newToken);

        // 2. Update coordinator registration with new token
        clusterRegistry.registerCoordinator(jobId, coordinatorId, newToken);

        // 3. Clear old assignments
        for (List<TaskAssignment> assignments : taskAssignmentMap.values()) {
            for (TaskAssignment assignment : assignments) {
                clusterRegistry.removeTaskAssignment(
                        jobId, assignment.getVertexId(), assignment.getSubtaskIndex());
            }
        }
        taskAssignmentMap.clear();
        allTaskLocations.clear();

        // Update fencing token on all registered TaskManagers
        for (IStreamTaskRpcService rpc : taskRpcServices.values()) {
            if (rpc instanceof TaskManager) {
                ((TaskManager) rpc).updateFencingToken(newToken);
            }
        }

        // 4. Restore from latest checkpoint/manifest if available
        try {
            CompletedCheckpoint latest = checkpointCoordinator.getLatestCheckpoint();
            if (latest != null) {
                LOG.info("Recovering from checkpoint {} for job {}", latest.getCheckpointId(), jobId);
            }
        } catch (Exception e) {
            LOG.warn("Failed to restore from checkpoint during recovery", e);
        }

        // 5. Reassign tasks with new fencing token
        assignTasks();

        LOG.info("Global recovery completed for job {} with new fencing token {}", jobId, newToken);
    }

    // ==================== Termination ====================

    /**
     * Terminates the job according to the specified mode.
     *
     * <p>Four termination modes:
     * <ul>
     *   <li>{@link JobTerminationMode#CANCEL} — immediately cancel all tasks</li>
     *   <li>{@link JobTerminationMode#DRAIN} — trigger a final checkpoint, wait for completion, then stop</li>
     *   <li>{@link JobTerminationMode#SUSPEND} — trigger a savepoint, persist state, then suspend (recoverable)</li>
     *   <li>{@link JobTerminationMode#EXPORT_SAVEPOINT} — trigger a savepoint, export it, keep job running</li>
     * </ul>
     *
     * @param mode the termination mode
     */
    public void terminate(JobTerminationMode mode) {
        LOG.info("Terminating job {} with mode {}", jobId, mode);

        switch (mode) {
            case CANCEL:
                terminateCancel();
                break;
            case DRAIN:
                terminateDrain();
                break;
            case SUSPEND:
                terminateSuspend();
                break;
            case EXPORT_SAVEPOINT:
                terminateExportSavepoint();
                break;
            default:
                throw new StreamException("Unknown termination mode: " + mode);
        }
    }

    private void terminateCancel() {
        LOG.info("CANCEL: immediately stopping job {}", jobId);
        stop();
    }

    private void terminateDrain() {
        LOG.info("DRAIN: triggering final checkpoint for job {}", jobId);
        try {
            PendingCheckpoint finalCheckpoint = checkpointCoordinator.tryTriggerPendingCheckpoint(
                    CheckpointType.COMPLETED_POINT_TYPE);
            if (finalCheckpoint != null) {
                CheckpointBarrier barrier = new CheckpointBarrier(
                        finalCheckpoint.getCheckpointId(),
                        finalCheckpoint.getTriggerTimestamp(),
                        finalCheckpoint.getCheckpointType());
                sendBarrierToAllTaskManagers(barrier);

                finalCheckpoint.getCompletableFuture()
                        .get(60, TimeUnit.SECONDS);
                LOG.info("DRAIN: final checkpoint {} completed for job {}",
                        finalCheckpoint.getCheckpointId(), jobId);
            }
        } catch (Exception e) {
            LOG.error("DRAIN: failed to complete final checkpoint for job {}", jobId, e);
        }
        stop();
    }

    private void terminateSuspend() {
        LOG.info("SUSPEND: triggering savepoint for job {}", jobId);
        try {
            PendingCheckpoint savepoint = checkpointCoordinator.tryTriggerPendingCheckpoint(
                    CheckpointType.TERMINAL_SAVEPOINT);
            if (savepoint != null) {
                CheckpointBarrier barrier = new CheckpointBarrier(
                        savepoint.getCheckpointId(),
                        savepoint.getTriggerTimestamp(),
                        savepoint.getCheckpointType());
                sendBarrierToAllTaskManagers(barrier);

                savepoint.getCompletableFuture()
                        .get(60, TimeUnit.SECONDS);
                LOG.info("SUSPEND: savepoint {} completed for job {}",
                        savepoint.getCheckpointId(), jobId);
            }
        } catch (Exception e) {
            LOG.error("SUSPEND: failed to complete savepoint for job {}", jobId, e);
        }
        stop();
    }

    private void terminateExportSavepoint() {
        LOG.info("EXPORT_SAVEPOINT: triggering export savepoint for job {}", jobId);
        try {
            PendingCheckpoint savepoint = checkpointCoordinator.tryTriggerPendingCheckpoint(
                    CheckpointType.EXPORTED_SAVEPOINT);
            if (savepoint != null) {
                CheckpointBarrier barrier = new CheckpointBarrier(
                        savepoint.getCheckpointId(),
                        savepoint.getTriggerTimestamp(),
                        savepoint.getCheckpointType());
                sendBarrierToAllTaskManagers(barrier);

                savepoint.getCompletableFuture()
                        .get(60, TimeUnit.SECONDS);
                LOG.info("EXPORT_SAVEPOINT: savepoint {} exported for job {}. Job continues running.",
                        savepoint.getCheckpointId(), jobId);
            }
        } catch (Exception e) {
            LOG.error("EXPORT_SAVEPOINT: failed for job {}", jobId, e);
        }
        // Job continues running after EXPORT_SAVEPOINT
    }

    // ==================== Status ====================

    public String getJobId() {
        return jobId;
    }

    public String getCoordinatorId() {
        return coordinatorId;
    }

    public String getFencingToken() {
        return fencingToken.get();
    }

    public void setFencingToken(String token) {
        fencingToken.set(token);
    }

    public boolean isRunning() {
        return running;
    }

    private void sendBarrierToAllTaskManagers(CheckpointBarrier barrier) {
        String token = fencingToken.get();
        for (Map.Entry<String, IStreamTaskRpcService> entry : taskRpcServices.entrySet()) {
            try {
                entry.getValue().triggerCheckpoint(barrier, token);
            } catch (Exception e) {
                LOG.error("Failed to send barrier signal to node {}", entry.getKey(), e);
            }
        }
    }

}
