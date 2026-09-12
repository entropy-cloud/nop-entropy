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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private static final Logger LOG = LoggerFactory.getLogger(TaskManager.class);

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
    private final long heartbeatIntervalMs;
    private final long leaseTimeoutMs;

    private final Semaphore capacitySemaphore;

    private final ExecutorService taskExecutor;
    private final ScheduledExecutorService heartbeatExecutor;

    /** taskKey (jobId/vertexId/subtaskIndex) → RunningTask */
    private final ConcurrentHashMap<String, RunningTask> runningTasks;

    /** taskKey → TaskResult for completed tasks (bounded to MAX_COMPLETED_TASKS) */
    private static final int MAX_COMPLETED_TASKS = 1000;
    private final ConcurrentHashMap<String, TaskResult> completedTasks;

    /** The currently active fencing epoch for this node (updated on global recovery). */
    private final AtomicLong currentFencingEpoch;

    /** Control topic for sending ACKs via message service (fallback when no RPC service) */
    private final String controlTopic;

    /** RPC service for sending ACKs directly to coordinator */
    private volatile IStreamCoordinatorRpcService coordinatorRpcService;

    private volatile boolean running;

    /** Item 16 (P-REQ-1 task layer): per-node task meters. */
    private final io.nop.stream.runtime.metrics.TaskNodeMetrics nodeMetrics;

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
     * (06-30 audit: intervals were hardcoded with no injection point — failover
     * detection window tuning requires both to be configurable). Non-positive
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
        this.capacitySemaphore = new Semaphore(Math.max(1, capacity));
        this.taskExecutor = Executors.newFixedThreadPool(Math.max(1, capacity), r -> {
            Thread t = new Thread(r, "tm-task-" + nodeId);
            t.setDaemon(true);
            return t;
        });
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tm-heartbeat-" + nodeId);
            t.setDaemon(true);
            return t;
        });
        this.runningTasks = new ConcurrentHashMap<>();
        this.completedTasks = new ConcurrentHashMap<>();
        this.currentFencingEpoch = new AtomicLong(0L);
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.leaseTimeoutMs = leaseTimeoutMs;
        this.running = false;
        // Item 16 (P-REQ-1 task layer): per-node meters on the real
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

        // Item 16 (P-REQ-1 task layer): running-task gauge (same counting
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

        LOG.info("TaskManager {} stopped", nodeId);
    }

    // ==================== Heartbeat ====================

    /**
     * Renews the lease for this node in the ClusterRegistry and (G52) piggybacks
     * per-task liveness to the coordinator on the existing heartbeat cadence.
     *
     * <p>No new task-level heartbeat thread is introduced: this method reads each
     * RunningTask's invokable liveness signal (null-checking the invokable
     * since it is set lazily by {@link RunningTask#setInvokable} after a 30s
     * {@code waitForInvokable} window) and reports a {@link TaskProgress} batch to
     * the coordinator via {@link IStreamCoordinatorRpcService#reportNodeTaskLiveness}.
     *
     * <p>G52 / AR-01 liveness semantics: the reported value is the <b>task
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
        try {
            boolean renewed = clusterRegistry.renewLease(nodeId, leaseTimeoutMs);
            if (!renewed) {
                LOG.warn("Failed to renew lease for node {}. Re-registering.", nodeId);
                clusterRegistry.registerNode(nodeId, endpoint, capacity);
            }
        } catch (Exception e) {
            LOG.error("Heartbeat failed for node {}", nodeId, e);
        }

        // G52: piggyback per-task liveness on the node heartbeat
        IStreamCoordinatorRpcService rpc = this.coordinatorRpcService;
        if (rpc == null || runningTasks.isEmpty()) {
            return;
        }
        List<TaskProgress> progress = new ArrayList<>();
        for (RunningTask task : runningTasks.values()) {
            StreamTaskInvokable inv = task.invokable;
            // null-check defense: invokable is volatile, lazily set by setInvokable()
            // (30s waitForInvokable window). Skip liveness for tasks whose invokable
            // is not yet installed — consistent with Phase 3 cancel null-check.
            if (inv == null) {
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
                // #24 — no silent skip: log and continue. A transient RPC failure
                // does not tear down the heartbeat loop; the next beat retries.
                LOG.warn("reportNodeTaskLiveness failed for node {} ({} tasks)", nodeId, progress.size(), e);
            }
        }
    }

    /**
     * G52 / AR-01: computes the per-task liveness value reported to the
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

        // Fencing epoch check
        // P0-6: harden stale-epoch handling to throw StreamException. The prior
        // implementation only LOG.warn'd and returned, silently swallowing the
        // operation despite the documented contract (TaskManager Javadoc: "rejects
        // any operation carrying an old fencing token"). Cross-JVM fencing is
        // owned by Stage 39 — this hardens the in-process check.
        long activeEpoch = currentFencingEpoch.get();
        if (activeEpoch != assignment.getFencingEpoch()) {
            NopException ex = new StreamException(ERR_STREAM_FENCING_TOKEN_MISMATCH)
                    .param(ARG_EXPECTED_TOKEN, activeEpoch)
                    .param(ARG_ACTUAL_TOKEN, assignment.getFencingEpoch());
            reportAssignmentFailure(assignment, assignment.getFencingEpoch(), ex);
            throw ex;
        }

        // AR-9: Use semaphore for capacity control instead of race-prone size check
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
        RunningTask runningTask = new RunningTask(
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
        // Item 16 (P-REQ-1 operator/io layers): inject per-task data-plane
        // metrics on the real in-process install path.
        invokable.setTaskMetrics(new io.nop.stream.core.metrics.MicrometerStreamTaskMetrics(
                io.nop.stream.core.metrics.StreamMetricsRegistries.registry(),
                jobId, vertexId, subtaskIndex));
        runningTask.setInvokable(invokable);
    }

    // ==================== Remote Deploy (Stage 42 Phase 0) ====================

    /**
     * Stage 42 Phase 0: deploys task logic to this TaskManager as a serializable
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
     * <p><strong>No silent skip</strong> (#24): if deployment fails (build error,
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
            NopException ex = new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "TaskManager " + nodeId + " not running, rejecting deployTask");
            reportDeployFailure(descriptor, fencingEpoch, ex);
            throw ex;
        }
        if (descriptor == null) {
            NopException ex = new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "descriptor");
            reportDeployFailure(null, fencingEpoch, ex);
            throw ex;
        }
        if (descriptor.getJobGraph() == null && descriptor.getPipelineSpec() == null) {
            // Item 14: the pipeline may arrive as a serializable DECLARATION spec
            // (XDSL) instead of a pre-built graph — see RemotePipelineSpec.
            NopException ex = new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "TaskDeploymentDescriptor carries neither a JobGraph nor a pipeline spec; cannot "
                            + "deployTask for " + descriptor.getVertexId() + "/" + descriptor.getSubtaskIndex());
            reportDeployFailure(descriptor, fencingEpoch, ex);
            throw ex;
        }

        long activeEpoch = currentFencingEpoch.get();
        if (activeEpoch != fencingEpoch) {
            NopException ex = new StreamException(ERR_STREAM_FENCING_TOKEN_MISMATCH)
                    .param(ARG_EXPECTED_TOKEN, activeEpoch)
                    .param(ARG_ACTUAL_TOKEN, fencingEpoch);
            reportDeployFailure(descriptor, fencingEpoch, ex);
            throw ex;
        }

        if (descriptor.getNodeId() != null && !descriptor.getNodeId().equals(nodeId)) {
            NopException ex = new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "deployTask target mismatch: descriptor nodeId=" + descriptor.getNodeId()
                            + " but this TaskManager is " + nodeId);
            reportDeployFailure(descriptor, fencingEpoch, ex);
            throw ex;
        }

        // The RPC parameter and the descriptor's embedded epoch must agree: the
        // RunningTask is keyed by the descriptor's field, so a divergent value
        // would run the task under an epoch that was never validated above.
        if (descriptor.getFencingEpoch() != fencingEpoch) {
            NopException ex = new StreamException(ERR_STREAM_FENCING_TOKEN_MISMATCH)
                    .param(ARG_EXPECTED_TOKEN, fencingEpoch)
                    .param(ARG_ACTUAL_TOKEN, descriptor.getFencingEpoch());
            reportDeployFailure(descriptor, fencingEpoch, ex);
            throw ex;
        }

        if (!capacitySemaphore.tryAcquire()) {
            NopException ex = new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "Node " + nodeId + " at capacity (" + (capacity - capacitySemaphore.availablePermits())
                            + "/" + capacity + "), rejecting deployTask for "
                            + descriptor.getVertexId() + "/" + descriptor.getSubtaskIndex());
            reportDeployFailure(descriptor, fencingEpoch, ex);
            throw ex;
        }

        String taskKey = taskKey(descriptor.getJobId(), descriptor.getVertexId(), descriptor.getSubtaskIndex());

        // Recovery may redeploy to the same slot before the old task is GC'd.
        // Fence the old slot out and reclaim its permit into this deployment.
        //
        // P1 hardening (permit conservation): the new deployment's permit was
        // acquired above (tryAcquire at the method entry). Releasing the old
        // slot's permit below balances the old task — net permit change for a
        // redeploy is therefore 0 (one task leaves, one task enters). The
        // legacy code re-acquired a permit here (acquireUninterruptibly), which
        // produced a net -1 per redeploy and wedged the node after `capacity`
        // recoveries. That extra acquire is removed; the entry acquire + this
        // release are the only permit touches for the redeploy path.
        //
        // W-5 (roadmap item 27): slot replacement is a SINGLE atomic map
        // operation — {@code put} returns the displaced entry, so there is no
        // get→remove→put window in which a concurrent same-key deploy/cancel
        // can observe the slot transiently empty (the legacy 3-step sequence
        // could leak a displaced task that kept running but became invisible
        // to the map). Every displaced task is handed to exactly one displacer
        // for cancel + permit release.
        RunningTask runningTask = new RunningTask(
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
            // Item 14: wire the TM-side checkpoint pipeline (state backends +
            // barrier tracker with RPC ACK + restore-on-deploy) BEFORE the
            // invokable is handed to the running task thread.
            io.nop.stream.runtime.deploy.RemoteTaskDeploySupport.wireDeployedSubtask(
                    this, descriptor, deployed.getJobGraph(), deployed.getPlan(), deployed.getInvokable());
            invokable = deployed.getInvokable();
        } catch (Throwable t) {
            LOG.error("Failed to build invokable from deployTask descriptor for {} on {}", taskKey, nodeId, t);
            // W-5: conditional remove — an interleaved replacement deploy may
            // already have displaced this entry; an unconditional remove would
            // delete the successor's mapping (same hazard class as the Item 14
            // RunningTask-exit fix).
            runningTasks.remove(taskKey, runningTask);
            runningTask.cancel();
            if (runningTask.semaphoreReleased.compareAndSet(false, true)) {
                capacitySemaphore.release();
            }
            NopException ex = new StreamException(ERR_STREAM_INVALID_STATE, t).param(ARG_DETAIL,
                    "Failed to build invokable from deployTask descriptor for " + taskKey
                            + " on TaskManager " + nodeId + ": " + t);
            reportDeployFailure(descriptor, fencingEpoch, ex);
            throw ex;
        }

        runningTask.setInvokable(invokable);
        // Item 16 (P-REQ-1 operator/io layers): inject per-task data-plane
        // metrics on the real remote-deploy path.
        invokable.setTaskMetrics(new io.nop.stream.core.metrics.MicrometerStreamTaskMetrics(
                io.nop.stream.core.metrics.StreamMetricsRegistries.registry(),
                descriptor.getJobId(), descriptor.getVertexId(), descriptor.getSubtaskIndex()));
        LOG.info("TaskManager {} deployed task {} via deployTask RPC (attempt={}, checkpointRestorePath={})",
                nodeId, taskKey, descriptor.getAttemptId(), descriptor.getCheckpointRestorePath());
    }

    /**
     * Stage 42 Phase 0: reports a deployTask failure to the coordinator as a
     * FAILED {@link TaskStatusReport} so the failure is observable and triggers
     * recovery. Best-effort — failure to report is logged (not swallowed).
     */
    private void reportDeployFailure(TaskDeploymentDescriptor descriptor, long fencingEpoch, Throwable cause) {
        nodeMetrics.taskFailed();
        IStreamCoordinatorRpcService rpc = this.coordinatorRpcService;
        if (rpc == null) {
            return;
        }
        String dJobId = descriptor != null ? descriptor.getJobId() : null;
        String dVertexId = descriptor != null ? descriptor.getVertexId() : null;
        int dSubtaskIndex = descriptor != null ? descriptor.getSubtaskIndex() : -1;
        int dAttemptNumber = descriptor != null ? descriptor.getAttemptNumber() : 0;
        TaskStatusReport report = new TaskStatusReport(
                dJobId, dVertexId, dSubtaskIndex, dAttemptNumber,
                TaskStatusReport.TerminalState.FAILED,
                cause == null ? "deployTask failure" : cause.toString(),
                -1L, fencingEpoch, CoreMetrics.currentTimeMillis());
        try {
            rpc.reportTaskStatus(report);
        } catch (Exception e) {
            LOG.warn("Failed to report deployTask failure to coordinator for {}/{}/{}",
                    dJobId, dVertexId, dSubtaskIndex, e);
        }
    }

    /**
     * Reports a receiveAssignment rejection to the coordinator as a FAILED
     * {@link TaskStatusReport}. The receiveAssignment RPC is one-way, so a
     * warn-and-return (or even a thrown exception) never reaches the
     * coordinator — mirroring {@link #reportDeployFailure}, the FAILED report
     * makes the rejection observable and lets recovery act on it instead of
     * waiting for supervision to detect the stalled slot. Best-effort — failure
     * to report is logged (not swallowed).
     */
    private void reportAssignmentFailure(TaskAssignment assignment, long fencingEpoch, Throwable cause) {
        nodeMetrics.taskFailed();
        IStreamCoordinatorRpcService rpc = this.coordinatorRpcService;
        if (rpc == null) {
            return;
        }
        String aJobId = assignment != null ? assignment.getJobId() : null;
        String aVertexId = assignment != null ? assignment.getVertexId() : null;
        int aSubtaskIndex = assignment != null ? assignment.getSubtaskIndex() : -1;
        int aAttemptNumber = assignment != null ? assignment.getAttemptNumber() : 0;
        TaskStatusReport report = new TaskStatusReport(
                aJobId, aVertexId, aSubtaskIndex, aAttemptNumber,
                TaskStatusReport.TerminalState.FAILED,
                cause == null ? "receiveAssignment rejection" : cause.toString(),
                -1L, fencingEpoch, CoreMetrics.currentTimeMillis());
        try {
            rpc.reportTaskStatus(report);
        } catch (Exception e) {
            LOG.warn("Failed to report receiveAssignment rejection to coordinator for {}/{}/{}",
                    aJobId, aVertexId, aSubtaskIndex, e);
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
        // P0-6: harden stale-epoch handling to throw StreamException. The prior
        // implementation only LOG.warn'd and returned, silently dropping the
        // barrier — which let a stale coordinator's checkpoint succeed against
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
        // F-C (roadmap item 27): cancelTask was the last mutating control-plane
        // entry without a fencing epoch — a stale coordinator could cancel an
        // active generation's task at any time. Same fail-fast contract as
        // deployTask/triggerCheckpoint/notifyCheckpointComplete: typed mismatch
        // rejection (D2 adjudication: WARN + typed throw; no FAILED
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
     * Item 14 (composite-scenario distributed): checkpoint-completion notification
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
            task.notifyCheckpointComplete(checkpointId);
        }
    }

    /**
     * Sends a checkpoint ACK to the coordinator via the control topic.
     *
     * @param checkpointId the checkpoint ID
     * @param snapshot     the task state snapshot
     */
    public void sendCheckpointAck(long checkpointId, TaskStateSnapshot snapshot) {
        CheckpointAckMessage ack = new CheckpointAckMessage(
                snapshot.getTaskLocation(),
                checkpointId,
                snapshot,
                currentFencingEpoch.get());

        try {
            if (coordinatorRpcService != null) {
                coordinatorRpcService.receiveCheckpointAck(ack);
            } else {
                throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                        "No coordinator RPC service available. "
                        + "All checkpoint ACKs require IStreamCoordinatorRpcService.");
            }
            LOG.debug("Sent checkpoint ACK for checkpoint {} from {}",
                    checkpointId, snapshot.getTaskLocation());
        } catch (Exception e) {
            LOG.error("Failed to send checkpoint ACK for checkpoint {}", checkpointId, e);
        }
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
     * A successfully completed task retains its registry entry (Item 14
     * bounded-run tail commits: the finished 2PC sink must stay reachable for
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

    private String taskKey(String jobId, String vertexId, int subtaskIndex) {
        return jobId + "/" + vertexId + "/" + subtaskIndex;
    }

    // ==================== Inner Classes ====================

    /**
     * A running task tracked by the TaskManager.
     */
    public class RunningTask implements Runnable {
        private final String jobId;
        private final String vertexId;
        private final int subtaskIndex;
        private final long fencingEpoch;
        private final String attemptId;
        /**
         * G56: per-subtask attempt number (mirrors {@link TaskAssignment#getAttemptNumber()}).
         * Carried in {@link TaskStatusReport} so the coordinator can correlate reports
         * with the right attempt.
         */
        private final int attemptNumber;
        private final TaskLocation taskLocation;
        private final CountDownLatch invokableLatch;

        private volatile StreamTaskInvokable invokable;
        private volatile Future<?> future;
        private volatile boolean canceled;
        private volatile Throwable error;
        /**
         * Item 14: set when the task thread reached its terminal state. A
         * SUCCESSFULLY completed task RETAINS its registry entry (bounded-run tail
         * commits — see the finally block), so {@link #getRunningTaskCount()} must
         * exclude finished entries or completion detectors
         * ({@code EmbeddedDistributedExecutor}/{@code RpcDistributedExecutor})
         * would wait forever on retained entries.
         */
        private volatile boolean finished;
        private final AtomicBoolean semaphoreReleased = new AtomicBoolean(false);

        public RunningTask(String jobId, String vertexId, int subtaskIndex,
                           long fencingEpoch, String attemptId) {
            this(jobId, vertexId, subtaskIndex, fencingEpoch, attemptId, 1);
        }

        public RunningTask(String jobId, String vertexId, int subtaskIndex,
                           long fencingEpoch, String attemptId, int attemptNumber) {
            this.jobId = jobId;
            this.vertexId = vertexId;
            this.subtaskIndex = subtaskIndex;
            this.fencingEpoch = fencingEpoch;
            this.attemptId = attemptId;
            this.attemptNumber = attemptNumber;
            this.taskLocation = new TaskLocation(jobId, "pipeline-0", vertexId, subtaskIndex);
            this.invokableLatch = new CountDownLatch(1);
        }

        @Override
        public void run() {
            if (canceled) {
                LOG.info("Task {}/{}/{} was canceled before execution", jobId, vertexId, subtaskIndex);
                return;
            }

            LOG.info("Running task {}/{}/{} (attempt={})", jobId, vertexId, subtaskIndex, attemptId);

            try {
                // Wait for invokable to be installed if not yet available
                StreamTaskInvokable inv = waitForInvokable();
                if (canceled) {
                    LOG.info("Task {}/{}/{} canceled while waiting for invokable", jobId, vertexId, subtaskIndex);
                    return;
                }
                if (inv == null) {
                    // Invokable-install timeout: NOT a success and NOT a cancel. A
                    // timeout means the coordinator died (or stalled) between the
                    // assignment and the install — the finally block must report
                    // FAILED so failover kicks in; reporting COMPLETED here would
                    // silently mark a never-run subtask as successful.
                    this.error = new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                            "Timed out after " + invokableWaitTimeoutMs
                                    + "ms waiting for invokable installation for "
                                    + jobId + "/" + vertexId + "/" + subtaskIndex);
                    LOG.error("Task {}/{}/{} failed: invokable installation timed out",
                            jobId, vertexId, subtaskIndex);
                    return;
                }

                inv.invoke();

                if (!canceled) {
                    LOG.info("Task {}/{}/{} completed successfully", jobId, vertexId, subtaskIndex);
                }
            } catch (Throwable t) {
                if (!canceled) {
                    this.error = t;
                    LOG.error("Task {}/{}/{} failed", jobId, vertexId, subtaskIndex, t);
                }
            } finally {
                String key = taskKey(jobId, vertexId, subtaskIndex);
                boolean success = error == null && !canceled;
                finished = true;
                completedTasks.put(key, new TaskResult(jobId, vertexId, subtaskIndex,
                        success, canceled, error));
                if (completedTasks.size() > MAX_COMPLETED_TASKS) {
                    Iterator<String> it = completedTasks.keySet().iterator();
                    if (it.hasNext()) {
                        it.next();
                        it.remove();
                    }
                }
                // Item 14 (composite-scenario distributed defect fix): remove the
                // registry entry ONLY if this task still owns it. A stale attempt's
                // thread can outlive its replacement's deployment (recovery cancels
                // the old attempt, but the thread winds down asynchronously; the
                // fresh deployTask puts the new RunningTask under the same key
                // immediately). The old finally's unconditional remove(key) then
                // deleted the REPLACEMENT's entry — the new task kept running but
                // became invisible to triggerCheckpoint (barrier never registered
                // on its tracker, checkpoint ACKs dropped with "no matching
                // in-flight epoch") and to cancelTask ("No running task to
                // cancel"). Conditional remove closes the window.
                //
                // Item 14 (bounded-run tail commits): a NATURALLY COMPLETED task
                // (success, not canceled) RETAINS its registry entry. The last
                // data of a bounded run reaches the 2PC sinks exactly at
                // EOS-MAX_WATERMARK — after the task finished but BEFORE the
                // coordinator's checkpoint for it completes. If the finished task
                // deregistered, the commit notification
                // (notifyCheckpointComplete → sink finishCommit) found no task and
                // the epoch's buffered output was lost forever (no later recovery
                // exists to re-commit durable-but-uncommitted transactions).
                // Retaining the entry lets the finished sink commit its last
                // epochs; the entry is replaced by any redeployment and cleared
                // on stop(). Canceled/failed tasks still remove themselves.
                if (success) {
                    LOG.debug("Task {}/{}/{} finished; retaining registry entry for post-finish commit notifications",
                            jobId, vertexId, subtaskIndex);
                } else {
                    boolean stillOwner = runningTasks.remove(key, this);
                    if (!stillOwner) {
                        LOG.debug("Task {}/{} attempt {} exit: registry entry already owned by a newer attempt",
                                jobId, vertexId, attemptId);
                    }
                }
                if (semaphoreReleased.compareAndSet(false, true)) {
                    capacitySemaphore.release();
                }

                // G52: per-task terminal-state report to the coordinator. Only
                // COMPLETED / FAILED are reported (CANCELED is initiated by the
                // coordinator itself, no need to echo back). #24 — failure to
                // report is logged (no silent swallow).
                if (!canceled) {
                    reportTerminalStatus(success);
                }
            }
        }

        /**
         * G52: reports this task's terminal state to the coordinator. Failures
         * are logged but do not tear down the run loop (#24 — explicit handling,
         * not silent).
         */
        private void reportTerminalStatus(boolean success) {
            IStreamCoordinatorRpcService rpc = TaskManager.this.coordinatorRpcService;
            if (rpc == null) {
                // No coordinator wired (e.g. unit-test fixture); skip silently.
                return;
            }
            long lastProgress = -1L;
            StreamTaskInvokable inv = this.invokable;
            if (inv != null) {
                lastProgress = inv.getLastProgressTime();
            }
            TaskStatusReport.TerminalState state = success
                    ? TaskStatusReport.TerminalState.COMPLETED
                    : TaskStatusReport.TerminalState.FAILED;
            if (!success) {
                // Item 16 (P-REQ-1 task layer): count real task failures.
                nodeMetrics.taskFailed();
            }
            String cause = error != null ? error.toString() : null;
            TaskStatusReport report = new TaskStatusReport(
                    jobId, vertexId, subtaskIndex, attemptNumber,
                    state, cause, lastProgress, fencingEpoch,
                    CoreMetrics.currentTimeMillis());
            try {
                rpc.reportTaskStatus(report);
            } catch (Exception e) {
                LOG.warn("Failed to report terminal status for {}/{}/{} (state={})",
                        jobId, vertexId, subtaskIndex, state, e);
            }
        }

        private StreamTaskInvokable waitForInvokable() throws InterruptedException {
            if (!invokableLatch.await(invokableWaitTimeoutMs, TimeUnit.MILLISECONDS)) {
                LOG.warn("Timed out waiting for invokable for {}/{}/{}", jobId, vertexId, subtaskIndex);
                return null;
            }
            return invokable;
        }

        public void setInvokable(StreamTaskInvokable invokable) {
            this.invokable = invokable;
            invokableLatch.countDown();
        }

        public void setFuture(Future<?> future) {
            this.future = future;
        }

        public void cancel() {
            canceled = true;
            invokableLatch.countDown();
            // G58: cooperative mailbox cancel first, then interrupt. Mirrors the
            // LOCAL path (GraphModelCheckpointExecutor.java:683) — the mailbox
            // signalCancel() raises the cancel flag and queues a cancel marker so
            // the task thread observes cancellation at its next mailbox drain even
            // if it is not in a blocking-interruptible section.
            //
            // null-check defense: invokable is volatile, lazily set by
            // setInvokable() (30s waitForInvokable window). If cancel arrives
            // before the invokable is installed, skip the mailbox call (no NPE);
            // the state-transition + future.cancel(true) + latch countdown still
            // apply so the task exits cleanly once the invokable arrives (or
            // waitForInvokable times out).
            StreamTaskInvokable inv = this.invokable;
            if (inv != null) {
                try {
                    inv.getMailboxExecutor().signalCancel();
                } catch (Exception e) {
                    LOG.warn("signalCancel failed for {}/{}/{} (falling back to interrupt-only)",
                            jobId, vertexId, subtaskIndex, e);
                }
            }
            if (future != null) {
                future.cancel(true);
            }
        }

        public void triggerCheckpoint(CheckpointBarrier barrier) {
            StreamTaskInvokable inv = this.invokable;
            if (inv == null) {
                LOG.debug("Cannot trigger checkpoint: invokable not yet installed for {}/{}/{}",
                        jobId, vertexId, subtaskIndex);
                return;
            }
            CheckpointBarrierTracker tracker = inv.getBarrierTracker();
            if (tracker == null) {
                LOG.debug("No barrier tracker for {}/{}/{}", jobId, vertexId, subtaskIndex);
                return;
            }
            try {
                tracker.triggerCheckpoint(barrier.getId(), barrier.getTimestamp(), barrier.getCheckpointType());
            } catch (Exception e) {
                LOG.error("Failed to trigger checkpoint on {}/{}/{}", jobId, vertexId, subtaskIndex, e);
            }
        }

        /**
         * Item 14: forwards a durable-checkpoint notification to this task's
         * operator chain's {@link io.nop.stream.core.checkpoint.participant.CheckpointParticipant}s
         * (the 2PC sink UDFs). Mirrors the LOCAL coordinator-side participant
         * notification — the commit only happens after the coordinator persisted
         * the epoch manifest (2PC invariant: commit after durable).
         */
        public void notifyCheckpointComplete(long checkpointId) {
            StreamTaskInvokable inv = this.invokable;
            if (inv == null || inv.getOperatorChain() == null) {
                LOG.debug("Cannot notify checkpoint completion: invokable not installed for {}/{}/{}",
                        jobId, vertexId, subtaskIndex);
                return;
            }
            for (io.nop.stream.core.operators.StreamOperator<?> op : inv.getOperatorChain().getOperators()) {
                try {
                    if (op instanceof io.nop.stream.core.checkpoint.participant.CheckpointParticipant) {
                        ((io.nop.stream.core.checkpoint.participant.CheckpointParticipant) op)
                                .finishCommit(checkpointId, true);
                    } else if (op instanceof io.nop.stream.core.operators.AbstractUdfStreamOperator) {
                        Object udf = ((io.nop.stream.core.operators.AbstractUdfStreamOperator<?, ?>) op).getUserFunction();
                        if (udf instanceof io.nop.stream.core.checkpoint.participant.CheckpointParticipant && udf != op) {
                            ((io.nop.stream.core.checkpoint.participant.CheckpointParticipant) udf)
                                    .finishCommit(checkpointId, true);
                        }
                    }
                } catch (Exception e) {
                    // Observable failure, not a silent swallow: the coordinator-side
                    // forwarder retries failed commits and the subsuming commit of
                    // the next epoch re-covers this one (ledger/manifest idempotency
                    // guards make the retry safe).
                    LOG.error("finishCommit({}) failed for an operator of {}/{}/{} — "
                            + "subsuming commit / retry will re-cover this epoch",
                            checkpointId, jobId, vertexId, subtaskIndex, e);
                }
            }
        }

        public long getFencingEpoch() {
            return fencingEpoch;
        }

        /** Item 14: whether the task thread reached its terminal state. */
        public boolean isFinished() {
            return finished;
        }

        public String getJobId() { return jobId; }
        public String getVertexId() { return vertexId; }
        public int getSubtaskIndex() { return subtaskIndex; }
        public TaskLocation getTaskLocation() { return taskLocation; }
    }

    public static class TaskResult {
        private final String jobId;
        private final String vertexId;
        private final int subtaskIndex;
        private final boolean success;
        private final boolean canceled;
        private final Throwable error;

        public TaskResult(String jobId, String vertexId, int subtaskIndex,
                          boolean success, boolean canceled, Throwable error) {
            this.jobId = jobId;
            this.vertexId = vertexId;
            this.subtaskIndex = subtaskIndex;
            this.success = success;
            this.canceled = canceled;
            this.error = error;
        }

        public String getJobId() { return jobId; }
        public String getVertexId() { return vertexId; }
        public int getSubtaskIndex() { return subtaskIndex; }
        public boolean isSuccess() { return success; }
        public boolean isCanceled() { return canceled; }
        public Throwable getError() { return error; }
    }
}
