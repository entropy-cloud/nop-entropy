/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import io.nop.api.core.time.CoreMetrics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.util.NopStreamThreadFactory;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_TOKEN;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_TOKEN;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_FENCING_TOKEN_MISMATCH;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.coordinator.TaskProgress;
import io.nop.stream.runtime.coordinator.TaskStatusReport;
import io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.rpc.TaskDeploymentDescriptor;
import io.nop.stream.runtime.transport.RemoteInputChannel;
import io.nop.stream.runtime.transport.RemoteResultPartition;
import io.nop.stream.runtime.transport.SubtaskPlanBuilder;

/**
 * TaskManager is the distributed runtime component on each worker node.
 *
 * <p>It is responsible for:
 * <ul>
 *   <li>Registering with the {@link ClusterRegistry} and sending periodic heartbeats</li>
 *   <li>Receiving task assignments and creating {@link StreamTaskInvokable} instances</li>
 *   <li>Running tasks in a local thread pool with {@link RemoteResultPartition}/{@link RemoteInputChannel}</li>
 *   <li>Handling checkpoint barrier signals from the coordinator</li>
 *   <li>Sending checkpoint ACKs back to the coordinator via control topic</li>
 *   <li>Enforcing fencing tokens to reject stale operations</li>
 * </ul>
 *
 * <p><strong>Fencing:</strong> Each job execution epoch has a fencing token. When the
 * coordinator performs global recovery, a new fencing token is issued. The TaskManager
 * rejects any operation carrying an old fencing token.
 */
@Internal
public class TaskManager implements IStreamTaskRpcService {

    // Logger is package-private: the top-level RunningTask / CheckpointAckSender
    // collaborators log under the same TaskManager logger name.
    static final Logger LOG = LoggerFactory.getLogger(TaskManager.class);

    private static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 5000L;
    private static final long DEFAULT_LEASE_TIMEOUT_MS = 15000L;

    /**
     * Wait budget for the coordinator to install a task's invokable after the
     * assignment slot is created. Package-private and volatile so focused tests
     * can shorten it (the production default stays 30s).
     */
    static volatile long invokableWaitTimeoutMs = 30_000L;

    private final String nodeId;
    private final String endpoint;
    private final int capacity;
    private final IMessageService messageService;
    private final ClusterRegistry clusterRegistry;
    private final CheckpointAckSender ackSender;

    private final long heartbeatIntervalMs;
    private final long leaseTimeoutMs;

    // Package-private below: shared state of the top-level RunningTask /
    // CheckpointAckSender collaborators (same package, no accessor indirection).
    final Semaphore capacitySemaphore;

    private final ExecutorService taskExecutor;
    private final ScheduledExecutorService heartbeatExecutor;
    private final ExecutorService commitExecutor;

    /** taskKey (jobId/vertexId/subtaskIndex) → RunningTask */
    final ConcurrentHashMap<String, RunningTask> runningTasks;

    /** taskKey → TaskResult for completed tasks (bounded to MAX_COMPLETED_TASKS) */
    static final int MAX_COMPLETED_TASKS = 1000;
    final ConcurrentHashMap<String, TaskResult> completedTasks;

    /** The currently active fencing epoch for this node (updated on global recovery). */
    final AtomicLong currentFencingEpoch;

    /** Control topic for sending ACKs via message service (fallback when no RPC service) */
    private final String controlTopic;

    /** RPC service for sending ACKs directly to coordinator */
    volatile IStreamCoordinatorRpcService coordinatorRpcService;

    private volatile boolean running;

    /** Per-node task meters. */
    final io.nop.stream.runtime.metrics.TaskNodeMetrics nodeMetrics;

    public TaskManager(String nodeId,
                       String endpoint,
                       int capacity,
                       IMessageService messageService,
                       ClusterRegistry clusterRegistry,
                       String controlTopic) {
        this(nodeId, endpoint, capacity, messageService, clusterRegistry, controlTopic,
                DEFAULT_HEARTBEAT_INTERVAL_MS, DEFAULT_LEASE_TIMEOUT_MS);
    }

    /**
     * Full constructor with ops-tunable heartbeat cadence and lease timeout
     * (both must be configurable so the failover
     * detection window can be tuned). Non-positive
     * values fail fast.
     */
    public TaskManager(String nodeId,
                       String endpoint,
                       int capacity,
                       IMessageService messageService,
                       ClusterRegistry clusterRegistry,
                       String controlTopic,
                       long heartbeatIntervalMs,
                       long leaseTimeoutMs) {
        if (heartbeatIntervalMs <= 0 || leaseTimeoutMs <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "heartbeatIntervalMs and leaseTimeoutMs must be positive (got "
                            + heartbeatIntervalMs + "/" + leaseTimeoutMs + ")");
        }
        this.nodeId = nodeId;
        this.endpoint = endpoint;
        this.capacity = capacity;
        this.messageService = messageService;
        this.clusterRegistry = clusterRegistry;
        this.controlTopic = controlTopic;
        this.ackSender = new CheckpointAckSender(this);
        this.capacitySemaphore = new Semaphore(Math.max(1, capacity));
        this.taskExecutor = Executors.newFixedThreadPool(Math.max(1, capacity),
                NopStreamThreadFactory.named("tm-task-" + nodeId));
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
                NopStreamThreadFactory.named("tm-heartbeat-" + nodeId));
        // Dedicated single-thread executor for 2PC checkpoint
        // commits. finishCommit performs blocking JDBC/file commits which must
        // never occupy the message-service dispatch thread (a slow commit there
        // stalls heartbeat, assignment and ACK dispatch for the whole node).
        // Single thread keeps commit ordering per task (subsuming semantics make
        // order benign, but serial execution preserves it anyway).
        this.commitExecutor = Executors.newSingleThreadExecutor(
                NopStreamThreadFactory.named("tm-commit-" + nodeId));
        this.runningTasks = new ConcurrentHashMap<>();
        this.completedTasks = new ConcurrentHashMap<>();
        this.currentFencingEpoch = new AtomicLong(0L);
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.leaseTimeoutMs = leaseTimeoutMs;
        this.running = false;
        // Per-node meters on the real
        // deploy/cancel/failure paths.
        this.nodeMetrics = io.nop.stream.runtime.metrics.TaskNodeMetrics.forNode(nodeId);
    }

    // ==================== Lifecycle ====================

    /**
     * Registers this node in the ClusterRegistry and starts the heartbeat loop.
     */
    public void start() {
        if (running) {
            LOG.warn("TaskManager {} already started", nodeId);
            return;
        }

        clusterRegistry.registerNode(nodeId, endpoint, capacity);
        running = true;

        // Running-task gauge (same counting
        // semantics as getRunningTaskCount — excludes finished tasks).
        io.nop.stream.runtime.metrics.TaskNodeMetrics.registerRunningGauge(
                io.nop.stream.core.metrics.StreamMetricsRegistries.registry(),
                nodeId, this::getRunningTaskCount);

        heartbeatExecutor.scheduleAtFixedRate(
                this::heartbeat,
                heartbeatIntervalMs,
                heartbeatIntervalMs,
                TimeUnit.MILLISECONDS);

        LOG.info("TaskManager {} started at endpoint {} with capacity {} (heartbeat={}ms, leaseTimeout={}ms)",
                nodeId, endpoint, capacity, heartbeatIntervalMs, leaseTimeoutMs);
    }

    /**
     * Shuts down the thread pool and cancels heartbeats. The node's registry
     * lease is NOT explicitly unregistered ({@link ClusterRegistry} has no
     * unregister API); the node disappears from {@code getActiveNodes()} once
     * its lease lapses (within {@code leaseTimeoutMs} of the last renewal).
     */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        heartbeatExecutor.shutdownNow();
        taskExecutor.shutdownNow();
        commitExecutor.shutdownNow();

        try {
            if (!taskExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("TaskExecutor did not terminate within 5 seconds for node {}", nodeId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while waiting for TaskExecutor termination for node {}", nodeId);
        }

        for (Map.Entry<String, RunningTask> entry : runningTasks.entrySet()) {
            entry.getValue().cancel();
        }
        runningTasks.clear();
        completedTasks.clear();

        // Release this node's meters and running-gauge so a
        // restarted node re-registers a fresh gauge and a dead node pins nothing.
        io.nop.stream.runtime.metrics.TaskNodeMetrics.releaseNode(nodeId);

        LOG.info("TaskManager {} stopped", nodeId);
    }

    // ==================== Heartbeat ====================

    /**
     * Renews the lease for this node in the ClusterRegistry and piggybacks
     * per-task liveness to the coordinator on the existing heartbeat cadence.
     *
     * <p>No new task-level heartbeat thread is introduced: this method reads each
     * RunningTask's invokable liveness signal (null-checking the invokable
     * since it is set lazily by {@link RunningTask#setInvokable} after a 30s
     * {@code waitForInvokable} window) and reports a {@link TaskProgress} batch to
     * the coordinator via {@link IStreamCoordinatorRpcService#reportNodeTaskLiveness}.
     *
     * <p>Liveness semantics: the reported value is the <b>task
     * aliveness</b> signal, decoupled from data progress. MIDDLE/SINK roles
     * report the task thread's loop-activity timestamp
     * ({@link StreamTaskInvokable#getLastActivityTime()}) — fresh while the
     * loop cycles (data or idle), aging only when the thread is genuinely
     * stuck. SOURCE/SELF_CONTAINED roles report the TM-side wall clock: their
     * run loop may legitimately block for the whole source lifetime (e.g. a
     * polling source with no data), so an idle source must never age out of
     * the coordinator's liveness window.
     */
    public void heartbeat() {
        if (!running) {
            return;
        }
        // The WHOLE iteration is guarded: per ScheduledExecutorService semantics
        // a single uncaught exception from scheduleAtFixedRate permanently kills
        // this loop — liveness reporting would freeze silently and the
        // coordinator's stall detector would misread it as dead tasks and trigger
        // a recovery storm. Any failure is contained here; the next beat retries.
        try {
            try {
                boolean renewed = clusterRegistry.renewLease(nodeId, leaseTimeoutMs);
                if (!renewed) {
                    LOG.warn("Failed to renew lease for node {}. Re-registering.", nodeId);
                    clusterRegistry.registerNode(nodeId, endpoint, capacity);
                }
            } catch (Exception e) {
                LOG.error("Heartbeat lease renewal failed for node {}", nodeId, e);
            }

            // Piggyback per-task liveness on the node heartbeat
            IStreamCoordinatorRpcService rpc = this.coordinatorRpcService;
            if (rpc == null || runningTasks.isEmpty()) {
                return;
            }
            List<TaskProgress> progress = new ArrayList<>();
            for (RunningTask task : runningTasks.values()) {
                StreamTaskInvokable inv = task.invokable;
                // null-check defense: invokable is volatile, lazily set by setInvokable()
                // (30s waitForInvokable window). Skip liveness for tasks whose invokable
                // is not yet installed — the same null-check the cancel
                // path applies.
                if (inv == null) {
                    continue;
                }
                // Skip naturally COMPLETED tasks: a success-finished task RETAINS its
                // registry entry (bounded-run tail commits need the entry to receive
                // commit notifications), but its activity clock is frozen. Reporting
                // the frozen value would re-insert the stale timestamp into the
                // coordinator's liveness map after reportTaskStatus(COMPLETED) removed
                // it, and the coordinator would flag the healthy task TASK_STALL after
                // taskTimeoutMs — triggering a global recovery that cancels the very
                // tail-commit window the retained entry exists to protect.
                if (task.isFinished()) {
                    continue;
                }
                progress.add(new TaskProgress(
                        task.vertexId,
                        task.subtaskIndex,
                        task.attemptNumber,
                        livenessValue(inv)));
            }
            if (!progress.isEmpty()) {
                try {
                    rpc.reportNodeTaskLiveness(nodeId, progress);
                } catch (Exception e) {
                    // No silent skip: log and continue. A transient RPC failure
                    // does not tear down the heartbeat loop; the next beat retries.
                    LOG.warn("reportNodeTaskLiveness failed for node {} ({} tasks)", nodeId, progress.size(), e);
                }
            }
        } catch (Exception e) {
            // No silent skip: the failure is logged; the scheduled loop
            // stays alive so the next beat retries.
            LOG.error("Heartbeat iteration failed unexpectedly for node {} - keeping the heartbeat loop alive", nodeId, e);
        }
    }

    /**
     * Computes the per-task liveness value reported to the
     * coordinator (see {@link #heartbeat()} javadoc for the semantics split).
     */
    private long livenessValue(StreamTaskInvokable inv) {
        StreamTaskInvokable.TaskRole role = inv.getRole();
        if (role == StreamTaskInvokable.TaskRole.SOURCE
                || role == StreamTaskInvokable.TaskRole.SELF_CONTAINED) {
            return CoreMetrics.currentTimeMillis();
        }
        return inv.getLastActivityTime();
    }

    // ==================== Task Assignment ====================

    /**
     * Receives a task assignment, creates the invokable, and starts execution.
     *
     * <p>The assignment must carry the current fencing token; otherwise it is rejected.
     *
     * @param assignment the task assignment from the coordinator
     */
    @Override
    public void receiveAssignment(TaskAssignment assignment) {
        if (!running) {
            LOG.warn("TaskManager {} not running, rejecting assignment", nodeId);
            reportAssignmentFailure(assignment, currentFencingEpoch.get(),
                    new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                            "TaskManager " + nodeId + " not running"));
            return;
        }

        // Fencing epoch check: a stale epoch must fail fast with a typed
        // StreamException instead of a LOG-and-return — silently swallowing
        // the operation would violate the documented contract ("rejects
        // any operation carrying an old fencing token"). Cross-JVM fencing
        // is enforced by the coordinator; this guards the in-process check.
        long activeEpoch = currentFencingEpoch.get();
        if (activeEpoch != assignment.getFencingEpoch()) {
            NopException ex = new StreamException(ERR_STREAM_FENCING_TOKEN_MISMATCH)
                    .param(ARG_EXPECTED_TOKEN, activeEpoch)
                    .param(ARG_ACTUAL_TOKEN, assignment.getFencingEpoch());
            reportAssignmentFailure(assignment, assignment.getFencingEpoch(), ex);
            throw ex;
        }

        // Capacity control via semaphore — a size check would race with
        // concurrent deploys/cancels
        if (!capacitySemaphore.tryAcquire()) {
            LOG.warn("Node {} at capacity ({}/{}), rejecting assignment for {}/{}",
                    nodeId, capacity - capacitySemaphore.availablePermits(), capacity,
                    assignment.getVertexId(), assignment.getSubtaskIndex());
            reportAssignmentFailure(assignment, assignment.getFencingEpoch(),
                    new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                            "Node " + nodeId + " at capacity (" + capacitySemaphore.availablePermits() + "/"
                                    + capacity + ")"));
            return;
        }

        String taskKey = taskKey(assignment);
        if (runningTasks.containsKey(taskKey)) {
            LOG.warn("Task {} already running, ignoring duplicate assignment", taskKey);
            capacitySemaphore.release();
            return;
        }

        // Two-phase assignment: receiveAssignment creates the RunningTask slot (its
        // invokable is not yet installed); the coordinator then populates the
        // invokable via a separate installInvokable() call (see EmbeddedDistributedExecutor
        // / RpcDistributedExecutor). RunningTask.run() blocks on invokableLatch until
        // the invokable arrives (or times out).
        RunningTask runningTask = new RunningTask(this,
                assignment.getJobId(),
                assignment.getVertexId(),
                assignment.getSubtaskIndex(),
                assignment.getFencingEpoch(),
                assignment.getAttemptId(),
                assignment.getAttemptNumber());

        RunningTask existing = runningTasks.putIfAbsent(taskKey, runningTask);
        if (existing != null) {
            capacitySemaphore.release();
            return;
        }

        nodeMetrics.taskDeployed();
        Future<?> future = taskExecutor.submit(runningTask);
        runningTask.setFuture(future);

        LOG.info("TaskManager {} accepted assignment for {}/{} (attempt={})",
                nodeId, assignment.getVertexId(), assignment.getSubtaskIndex(),
                assignment.getAttemptId());
    }

    /**
     * Receive and install a fully-built {@link StreamTaskInvokable} for a previously assigned task slot.
     *
     * <p>This is called after {@link #receiveAssignment} when the coordinator sends the
     * serialized operator chain and deployment plan.
     */
    public void installInvokable(String jobId, String vertexId, int subtaskIndex,
                                 StreamTaskInvokable invokable) {
        String taskKey = taskKey(jobId, vertexId, subtaskIndex);
        RunningTask runningTask = runningTasks.get(taskKey);
        if (runningTask == null) {
            LOG.warn("No running task slot for {}/{}/{}", jobId, vertexId, subtaskIndex);
            return;
        }
        // Inject per-task data-plane
        // metrics on the real in-process install path.
        invokable.setTaskMetrics(new io.nop.stream.core.metrics.MicrometerStreamTaskMetrics(
                io.nop.stream.core.metrics.StreamMetricsRegistries.registry(),
                jobId, vertexId, subtaskIndex));
        runningTask.setInvokable(invokable);
    }

    // ==================== Remote Deploy ====================

    /**
     * Deploys task logic to this TaskManager as a serializable
     * {@link TaskDeploymentDescriptor}. The TaskManager reconstructs its own
     * {@link StreamTaskInvokable} locally from the descriptor's
     * {@link io.nop.stream.core.jobgraph.JobGraph} + edge config (via
     * {@link SubtaskPlanBuilder}), installs it, and starts running the task.
     *
     * <p>This is the cross-JVM replacement for the in-process direct-Java
     * {@link #installInvokable} call. In remote-deploy mode the coordinator calls
     * this instead of {@link #receiveAssignment} + a separate direct install — the
     * descriptor is self-contained (carries {@link TaskAssignment} metadata).
     *
     * <p><strong>No silent skip</strong>: if deployment fails (build error,
     * fencing mismatch, target-node mismatch, capacity exhausted), this method
     * throws a {@link StreamException} AND reports a FAILED
     * {@link io.nop.stream.runtime.coordinator.TaskStatusReport} to the coordinator
     * so the failure is observable and triggers recovery. Throwing alone is
     * insufficient because the {@code deployTask} RPC is one-way (fire-and-forget,
     * see {@code StreamControlRpcTransformer}) — the exception does not propagate
     * back to the coordinator over RPC.
     *
     * @param descriptor   the serializable deployment descriptor
     * @param fencingEpoch the monotonic fencing epoch the deployment is valid under
     */
    @Override
    public void deployTask(TaskDeploymentDescriptor descriptor, long fencingEpoch) {
        if (!running) {
            throw rejectDeploy(descriptor, fencingEpoch, new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "TaskManager " + nodeId + " not running, rejecting deployTask"));
        }
        if (descriptor == null) {
            throw rejectDeploy(null, fencingEpoch,
                    new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "descriptor"));
        }
        if (descriptor.getJobGraph() == null && descriptor.getPipelineSpec() == null) {
            // The pipeline may arrive as a serializable DECLARATION spec
            // (XDSL) instead of a pre-built graph — see RemotePipelineSpec.
            throw rejectDeploy(descriptor, fencingEpoch, new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL,
                            "TaskDeploymentDescriptor carries neither a JobGraph nor a pipeline spec; cannot "
                                    + "deployTask for " + descriptor.getVertexId() + "/"
                                    + descriptor.getSubtaskIndex()));
        }

        long activeEpoch = currentFencingEpoch.get();
        if (activeEpoch != fencingEpoch) {
            throw rejectDeploy(descriptor, fencingEpoch, new StreamException(ERR_STREAM_FENCING_TOKEN_MISMATCH)
                    .param(ARG_EXPECTED_TOKEN, activeEpoch)
                    .param(ARG_ACTUAL_TOKEN, fencingEpoch));
        }

        if (descriptor.getNodeId() != null && !descriptor.getNodeId().equals(nodeId)) {
            throw rejectDeploy(descriptor, fencingEpoch, new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL,
                            "deployTask target mismatch: descriptor nodeId=" + descriptor.getNodeId()
                                    + " but this TaskManager is " + nodeId));
        }

        // The RPC parameter and the descriptor's embedded epoch must agree: the
        // RunningTask is keyed by the descriptor's field, so a divergent value
        // would run the task under an epoch that was never validated above.
        if (descriptor.getFencingEpoch() != fencingEpoch) {
            throw rejectDeploy(descriptor, fencingEpoch, new StreamException(ERR_STREAM_FENCING_TOKEN_MISMATCH)
                    .param(ARG_EXPECTED_TOKEN, fencingEpoch)
                    .param(ARG_ACTUAL_TOKEN, descriptor.getFencingEpoch()));
        }

        if (!capacitySemaphore.tryAcquire()) {
            throw rejectDeploy(descriptor, fencingEpoch, new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL,
                            "Node " + nodeId + " at capacity (" + (capacity - capacitySemaphore.availablePermits())
                                    + "/" + capacity + "), rejecting deployTask for "
                                    + descriptor.getVertexId() + "/" + descriptor.getSubtaskIndex()));
        }

        String taskKey = taskKey(descriptor.getJobId(), descriptor.getVertexId(), descriptor.getSubtaskIndex());

        // Recovery may redeploy to the same slot before the old task is GC'd.
        // Fence the old slot out and reclaim its permit into this deployment.
        //
        // Permit conservation: this deployment acquired exactly one permit at
        // the method entry, and the displaced slot's permit release below
        // balances the displaced task — a redeploy's net permit change is 0
        // (one task leaves, one task enters). The entry acquire plus this
        // release are the only permit touches on the redeploy path, so
        // repeated recoveries cannot drain the node's capacity.
        //
        // Slot replacement is a SINGLE atomic map operation: {@code put}
        // returns the displaced entry, so there is no get→remove→put window
        // in which a concurrent same-key deploy/cancel can observe the slot
        // transiently empty or lose a displaced task. Every displaced task is
        // handed to exactly one displacer for cancel + permit release.
        RunningTask runningTask = new RunningTask(this,
                descriptor.getJobId(),
                descriptor.getVertexId(),
                descriptor.getSubtaskIndex(),
                descriptor.getFencingEpoch(),
                descriptor.getAttemptId(),
                descriptor.getAttemptNumber());
        RunningTask existing = runningTasks.put(taskKey, runningTask);
        if (existing != null) {
            LOG.warn("Slot {} already occupied (attempt={}); fencing old before redeploy",
                    taskKey, existing.attemptId);
            existing.cancel();
            if (existing.semaphoreReleased.compareAndSet(false, true)) {
                capacitySemaphore.release();
            }
        }
        nodeMetrics.taskDeployed();
        Future<?> future = taskExecutor.submit(runningTask);
        runningTask.setFuture(future);

        // Build the invokable locally from the descriptor. SubtaskPlanBuilder
        // rebuilds the subtask using this TaskManager's IMessageService, which
        // connects to the same deterministic data-plane topics the coordinator
        // would have wired — so cross-JVM data exchange works.
        StreamTaskInvokable invokable;
        try {
            SubtaskPlanBuilder builder = new SubtaskPlanBuilder(messageService, null);
            SubtaskPlanBuilder.DeployedSubtaskPlan deployed = builder.buildSubtaskPlan(descriptor);
            // Wire the TM-side checkpoint pipeline (state backends +
            // barrier tracker with RPC ACK + restore-on-deploy) BEFORE the
            // invokable is handed to the running task thread.
            io.nop.stream.runtime.deploy.RemoteTaskDeploySupport.wireDeployedSubtask(
                    this, descriptor, deployed.getJobGraph(), deployed.getPlan(), deployed.getInvokable());
            invokable = deployed.getInvokable();
        } catch (Throwable t) {
            LOG.error("Failed to build invokable from deployTask descriptor for {} on {}", taskKey, nodeId, t);
            // Conditional remove — an interleaved replacement deploy may
            // already have displaced this entry; an unconditional remove would
            // delete the successor's mapping (same hazard class as an
            // unconditional remove from a finished task's exit path).
            runningTasks.remove(taskKey, runningTask);
            runningTask.cancel();
            if (runningTask.semaphoreReleased.compareAndSet(false, true)) {
                capacitySemaphore.release();
            }
            throw rejectDeploy(descriptor, fencingEpoch, new StreamException(ERR_STREAM_INVALID_STATE, t)
                    .param(ARG_DETAIL,
                            "Failed to build invokable from deployTask descriptor for " + taskKey
                                    + " on TaskManager " + nodeId + ": " + t));
        }

        runningTask.setInvokable(invokable);
        // Inject per-task data-plane
        // metrics on the real remote-deploy path.
        invokable.setTaskMetrics(new io.nop.stream.core.metrics.MicrometerStreamTaskMetrics(
                io.nop.stream.core.metrics.StreamMetricsRegistries.registry(),
                descriptor.getJobId(), descriptor.getVertexId(), descriptor.getSubtaskIndex()));
        LOG.info("TaskManager {} deployed task {} via deployTask RPC (attempt={}, checkpointRestorePath={})",
                nodeId, taskKey, descriptor.getAttemptId(), descriptor.getCheckpointRestorePath());
    }

    /**
     * Guard helper for {@link #deployTask}: reports the rejection to the
     * coordinator as a best-effort FAILED {@link TaskStatusReport}, then
     * rethrows the typed exception. Always throws — the return type exists so
     * call sites can write {@code throw rejectDeploy(...)} and keep the
     * compiler's flow analysis aware the path does not continue.
     */
    private NopException rejectDeploy(TaskDeploymentDescriptor descriptor, long fencingEpoch,
                                      NopException cause) {
        reportDeployFailure(descriptor, fencingEpoch, cause);
        throw cause;
    }

    /**
     * Reports a deployTask failure to the coordinator as a
     * FAILED {@link TaskStatusReport} so the failure is observable and triggers
     * recovery. Best-effort — failure to report is logged (not swallowed).
     */
    private void reportDeployFailure(TaskDeploymentDescriptor descriptor, long fencingEpoch, Throwable cause) {
        reportTaskFailure("deployTask failure",
                descriptor != null ? descriptor.getJobId() : null,
                descriptor != null ? descriptor.getVertexId() : null,
                descriptor != null ? descriptor.getSubtaskIndex() : -1,
                descriptor != null ? descriptor.getAttemptNumber() : 0,
                fencingEpoch, cause);
    }

    /**
     * Reports a receiveAssignment rejection to the coordinator as a FAILED
     * {@link TaskStatusReport}. The receiveAssignment RPC is one-way, so a
     * warn-and-return (or even a thrown exception) never reaches the
     * coordinator — the FAILED report makes the rejection observable and lets
     * recovery act on it instead of waiting for supervision to detect the
     * stalled slot. Best-effort — failure to report is logged (not swallowed).
     */
    private void reportAssignmentFailure(TaskAssignment assignment, long fencingEpoch, Throwable cause) {
        reportTaskFailure("receiveAssignment rejection",
                assignment != null ? assignment.getJobId() : null,
                assignment != null ? assignment.getVertexId() : null,
                assignment != null ? assignment.getSubtaskIndex() : -1,
                assignment != null ? assignment.getAttemptNumber() : 0,
                fencingEpoch, cause);
    }

    /**
     * Shared body of the deploy/assignment failure reporters: counts the node
     * failure meter, then best-effort sends a FAILED {@link TaskStatusReport}
     * to the coordinator so a one-way RPC rejection is observable and recovery
     * can act on it. Failure to report is logged, not swallowed.
     *
     * @param failureKind verbatim fallback detail (when {@code cause} is null)
     *                    and log label — "deployTask failure" or
     *                    "receiveAssignment rejection"
     */
    private void reportTaskFailure(String failureKind, String jobId, String vertexId,
                                   int subtaskIndex, int attemptNumber, long fencingEpoch, Throwable cause) {
        nodeMetrics.taskFailed();
        IStreamCoordinatorRpcService rpc = this.coordinatorRpcService;
        if (rpc == null) {
            return;
        }
        TaskStatusReport report = new TaskStatusReport(
                jobId, vertexId, subtaskIndex, attemptNumber,
                TaskStatusReport.TerminalState.FAILED,
                cause == null ? failureKind : cause.toString(),
                -1L, fencingEpoch, CoreMetrics.currentTimeMillis());
        try {
            rpc.reportTaskStatus(report);
        } catch (Exception e) {
            LOG.warn("Failed to report {} to coordinator for {}/{}/{}",
                    failureKind, jobId, vertexId, subtaskIndex, e);
        }
    }

    // ==================== Checkpoint ====================

    /**
     * Handles a checkpoint barrier signal from the coordinator.
     *
     * <p>Injects the barrier into the source operator's pending barrier queue
     * (via {@link CheckpointBarrierTracker}).
     *
     * @param barrier       the checkpoint barrier
     * @param fencingToken  the fencing token of the current epoch
     */
    @Override
    public void triggerCheckpoint(CheckpointBarrier barrier, long fencingEpoch) {
        // Stale-epoch handling must fail fast with a typed
        // StreamException instead of a LOG-and-return: a silently dropped
        // barrier would let a stale coordinator's checkpoint succeed against
        // the active epoch's state, breaking fencing semantics.
        long activeEpoch = currentFencingEpoch.get();
        if (activeEpoch != fencingEpoch) {
            throw new StreamException(ERR_STREAM_FENCING_TOKEN_MISMATCH)
                    .param(ARG_EXPECTED_TOKEN, activeEpoch)
                    .param(ARG_ACTUAL_TOKEN, fencingEpoch);
        }

        for (RunningTask task : runningTasks.values()) {
            if (task.getFencingEpoch() == fencingEpoch) {
                task.triggerCheckpoint(barrier);
            }
        }
    }

    @Override
    public void cancelTask(String jobId, String vertexId, int subtaskIndex, long fencingEpoch) {
        // cancelTask is fenced like every other mutating control-plane entry:
        // a stale coordinator must not be able to cancel an active
        // generation's task. Same fail-fast contract as
        // deployTask/triggerCheckpoint/notifyCheckpointComplete: typed mismatch
        // rejection (WARN + typed throw; no FAILED
        // TaskStatusReport — the task is healthy under the active epoch and the
        // stale coordinator is the anomaly, so triggering coordinator-side
        // recovery would punish the healthy generation).
        long activeEpoch = currentFencingEpoch.get();
        if (activeEpoch != fencingEpoch) {
            LOG.warn("Rejecting stale-epoch cancelTask for {}/{}/{} on {}: expected epoch {} but got {}",
                    jobId, vertexId, subtaskIndex, nodeId, activeEpoch, fencingEpoch);
            throw new StreamException(ERR_STREAM_FENCING_TOKEN_MISMATCH)
                    .param(ARG_EXPECTED_TOKEN, activeEpoch)
                    .param(ARG_ACTUAL_TOKEN, fencingEpoch);
        }

        String taskKey = taskKey(jobId, vertexId, subtaskIndex);
        RunningTask task = runningTasks.remove(taskKey);
        if (task != null) {
            task.cancel();
            nodeMetrics.taskCancelled();
            if (task.semaphoreReleased.compareAndSet(false, true)) {
                capacitySemaphore.release();
            }
            LOG.info("Canceled task {}/{}/{}", jobId, vertexId, subtaskIndex);
        } else {
            LOG.warn("No running task to cancel for {}/{}/{}", jobId, vertexId, subtaskIndex);
        }
    }

    /**
     * Checkpoint-completion notification
     * from the coordinator's distributed commit forwarder. Drives the local 2PC
     * sink participants' {@code CheckpointParticipant.finishCommit(checkpointId, true)}
     * — the TM-side analog of the LOCAL path registering the sink UDFs directly on
     * the coordinator. Stale fencing epochs are rejected fail-fast (same contract
     * as {@link #triggerCheckpoint}).
     *
     * <p>A {@code null} invokable (task still waiting for install) is skipped at
     * DEBUG — the subsuming commit of the NEXT completed epoch covers the missed
     * notification ({@code finishCommit(M)} commits every {@code eid <= M}), and
     * the sink restore path re-commits durable-but-uncommitted transactions on
     * recovery redeploy, so exactly-once holds under lost notifications.
     */
    @Override
    public void notifyCheckpointComplete(long checkpointId, long fencingEpoch) {
        long activeEpoch = currentFencingEpoch.get();
        if (activeEpoch != fencingEpoch) {
            throw new StreamException(ERR_STREAM_FENCING_TOKEN_MISMATCH)
                    .param(ARG_EXPECTED_TOKEN, activeEpoch)
                    .param(ARG_ACTUAL_TOKEN, fencingEpoch);
        }

        for (RunningTask task : runningTasks.values()) {
            if (task.getFencingEpoch() != fencingEpoch) {
                continue;
            }
            // Run the commit off the caller's dispatch thread.
            // The RPC entry (message-service dispatch) / coordinator forwarder
            // thread must stay responsive; finishCommit blocks on JDBC/file I/O.
            commitExecutor.execute(() -> task.notifyCheckpointComplete(checkpointId));
        }
    }

    /**
     * Sends a checkpoint ACK to the coordinator via the control topic, with a
     * bounded retry for transient failures. The ACK is the
     * completion-critical message of the checkpoint protocol: losing it to a
     * transient backend hiccup previously forced the coordinator to wait for
     * the full checkpoint timeout and abort the epoch. Retries are attempted
     * inline with a short backoff; when the budget is exhausted the failure is
     * logged and counted (observable) and the coordinator-side checkpoint
     * timeout remains the subsuming safety net.
     *
     * <p>Delegates to {@link CheckpointAckSender}; kept on the host because it
     * is the public RPC-surface entry point called by
     * {@link io.nop.stream.runtime.deploy.RemoteTaskDeploySupport} and tests.
     *
     * @param checkpointId the checkpoint ID
     * @param snapshot     the task state snapshot
     */
    public void sendCheckpointAck(long checkpointId, TaskStateSnapshot snapshot) {
        ackSender.send(checkpointId, snapshot);
    }

    // ==================== Fencing ====================

    /**
     * Updates the fencing epoch. Tasks with the old epoch are canceled.
     *
     * <p>Fencing epochs are monotonic by construction ({@code deriveHaFencingEpoch}
     * guarantees a zombie coordinator derives a strictly smaller epoch than the
     * active leader). A rollback attempt is therefore always a stale caller and
     * is rejected fail-fast: accepting it would cancel the entire active
     * generation and mismatch-reject every subsequent active-epoch call until
     * the next rotation.
     *
     * @param fencingEpoch the new monotonic fencing epoch
     */
    public void updateFencingToken(long fencingEpoch) {
        long current = currentFencingEpoch.get();
        if (fencingEpoch < current) {
            throw new StreamException(ERR_STREAM_FENCING_TOKEN_MISMATCH)
                    .param(ARG_EXPECTED_TOKEN, current)
                    .param(ARG_ACTUAL_TOKEN, fencingEpoch);
        }
        long oldEpoch = currentFencingEpoch.getAndSet(fencingEpoch);
        if (oldEpoch != fencingEpoch) {
            LOG.info("Fencing epoch updated from {} to {}. Canceling old tasks.", oldEpoch, fencingEpoch);
            runningTasks.entrySet().removeIf(entry -> {
                if (entry.getValue().getFencingEpoch() == oldEpoch) {
                    entry.getValue().cancel();
                    if (entry.getValue().semaphoreReleased.compareAndSet(false, true)) {
                        capacitySemaphore.release();
                    }
                    return true;
                }
                return false;
            });
        }
    }

    // ==================== Status ====================

    public String getNodeId() {
        return nodeId;
    }

    public void setCoordinatorRpcService(IStreamCoordinatorRpcService coordinatorRpcService) {
        this.coordinatorRpcService = coordinatorRpcService;
    }

    /**
     * Counts tasks whose execution thread has NOT yet reached a terminal state.
     * A successfully completed task retains its registry entry
     * (bounded-run tail commits: the finished 2PC sink must stay reachable for
     * {@code notifyCheckpointComplete}), but it is NOT running — completion
     * detectors ({@code EmbeddedDistributedExecutor} et al.) rely on this count
     * reaching zero when every task finished.
     */
    public int getRunningTaskCount() {
        int count = 0;
        for (RunningTask task : runningTasks.values()) {
            if (!task.isFinished()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Package-private test accessor: whether the registry still holds an entry
     * for the task key (a success-finished task retains its entry even though
     * {@link #getRunningTaskCount()} excludes it).
     */
    boolean hasRegistryEntry(String jobId, String vertexId, int subtaskIndex) {
        return runningTasks.containsKey(taskKey(jobId, vertexId, subtaskIndex));
    }

    int availablePermits() {
        return capacitySemaphore.availablePermits();
    }

    public Map<String, TaskResult> getCompletedTaskResults() {
        return Collections.unmodifiableMap(completedTasks);
    }

    public boolean isRunning() {
        return running;
    }

    // ==================== Helpers ====================

    private String taskKey(TaskAssignment assignment) {
        return taskKey(assignment.getJobId(), assignment.getVertexId(), assignment.getSubtaskIndex());
    }

    static String taskKey(String jobId, String vertexId, int subtaskIndex) {
        return jobId + "/" + vertexId + "/" + subtaskIndex;
    }
}
