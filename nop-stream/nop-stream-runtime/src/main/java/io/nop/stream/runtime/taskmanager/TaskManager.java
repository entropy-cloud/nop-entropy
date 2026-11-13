/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import io.nop.stream.runtime.metrics.RuntimeMetrics;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
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
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_QUERY_TIMEOUT;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TYPE_NAME;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.protocol.TaskProgress;
import io.nop.stream.runtime.protocol.TaskStatusReport;
import io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.protocol.TaskDeploymentDescriptor;
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
     * Default capacity of the dedicated 2PC commit executor's wait queue
     * (plan 369 Phase 4, R5-CC-10). Generous for any realistic task count x
     * checkpoint backlog, but bounded: a pathologically slow commit can no
     * longer grow the queue without limit.
     */
    public static final int DEFAULT_COMMIT_QUEUE_CAPACITY = 1000;

    /**
     * Wait budget for the coordinator to install a task's invokable after the
     * assignment slot is created. Package-private and volatile so focused tests
     * can shorten it (the production default stays 30s).
     */
    static volatile long invokableWaitTimeoutMs = 30_000L;

    /**
     * W-09 (CRA 8.41 R4-01, ruling D.2): bounded wait for the mailbox-handoff
     * keyed-state snapshot behind {@code GET .../state}. Configurable via
     * {@code nop.stream.ops.state-query-timeout-ms} (values &lt;= 0 fail fast
     * typed at resolve time). Package-visible and volatile so focused tests can
     * shorten it (the production default stays 5s).
     */
    static volatile long stateQueryTimeoutMs = 5_000L;
    /** Config key for {@link #stateQueryTimeoutMs} (ruling D.2.3). */
    static final String STATE_QUERY_TIMEOUT_CONFIG = "nop.stream.ops.state-query-timeout-ms";

    private final String nodeId;

    // W-13 site ② (CRA 8.41 R4-01, ruling E.1): per-TM heartbeat lease-renewal
    // failure suppressor (per-site instance — no cross-thread shared state).
    // Initialized in the primary constructor (nodeId is a blank final there).
    final io.nop.stream.runtime.support.PeriodicFailureSuppressor heartbeatFailureSuppressor;

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
    /**
     * CC-17 (R5, plan 2435): single-thread notifier for per-task terminal-state
     * reports. Rejection handler runs the report INLINE even after shutdown —
     * a dying task's terminal report must never be silently dropped (guide #24);
     * stop() closes this executor LAST (after taskExecutor termination and the
     * cancel sweep) so the normal path never hits the handler. Non-final only
     * for {@link #terminalReportExecutorForTest(Runnable)}.
     */
    private ExecutorService terminalReportExecutor;
    private final ScheduledExecutorService heartbeatExecutor;
    private final ThreadPoolExecutor commitExecutor;
    /** Wait-queue capacity of {@link #commitExecutor} (R5-CC-10 upper bound). */
    private final int commitQueueCapacity;

    /** taskKey (jobId/vertexId/subtaskIndex) → RunningTask */
    final ConcurrentHashMap<String, RunningTask> runningTasks;

    /** taskKey → TaskResult for completed tasks (bounded to MAX_COMPLETED_TASKS) */
    static final int MAX_COMPLETED_TASKS = 1000;
    final ConcurrentHashMap<String, TaskResult> completedTasks;

    /**
     * WI14 (plan 2340): read-only keyed-state snapshot for one running subtask —
     * the queryable-state-lite surface (no write-back, no cross-process read-RPC:
     * the TM-hosted ops server reads in-process).
     *
     * <p>W-09 (CRA 8.41 R4-01, ruling D.2): the snapshot is handed off to the
     * TASK THREAD via the control mailbox (Mail.control → TaskMailbox.put →
     * drained at the task's safe points), restoring the same-thread-as-writes
     * snapshot premise the checkpoint path already relies on — a REST read can no
     * longer tear a map the task thread is mutating. The HTTP (caller) thread
     * bounded-waits on a future; a stuck task answers the typed
     * {@link io.nop.stream.core.exceptions.NopStreamErrors#ERR_STREAM_STATE_QUERY_TIMEOUT}
     * after {@link #stateQueryTimeoutMs} instead of serving torn state or hanging.
     *
     * <p>Steady-state filters (ruling D.2.4): an unknown task answers typed
     * TASK_NOT_FOUND; a FINISHED (retained-entry) task answers typed TASK_NOT_FOUND
     * too — its post-finish commit notifications keep the entry, but it can no
     * longer serve live snapshots. A registered task whose task thread is not
     * running (between install and first run, or already wound down) has no
     * concurrent writer: the snapshot is served directly.
     *
     * @return the keyed state snapshot, or an empty snapshot when the task exists but
     *         holds no keyed backend; typed error when the task is not running here
     */
    public io.nop.stream.core.common.state.backend.StateSnapshot keyedStateSnapshot(
            String jobId, String vertexId, int subtaskIndex) {
        String taskKey = taskKey(jobId, vertexId, subtaskIndex);
        RunningTask task = runningTasks.get(taskKey);
        if (task == null || task.invokable == null || task.isFinished()) {
            throw new io.nop.stream.core.exceptions.StreamException(
                    io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_TASK_NOT_FOUND)
                    .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_TYPE_NAME, taskKey);
        }
        io.nop.stream.core.execution.task.StreamTaskInvokable invokable = task.invokable;
        if (!task.hasLiveTaskThread()) {
            // No live task thread → no concurrent task-thread writer exists; serve
            // directly (the registered-but-not-running window, e.g. between install
            // and first run, or an already wound-down attempt).
            return invokable.keyedStateSnapshot();
        }

        long timeoutMs = resolveStateQueryTimeoutMs();
        java.util.concurrent.CompletableFuture<io.nop.stream.core.common.state.backend.StateSnapshot> future =
                new java.util.concurrent.CompletableFuture<>();
        io.nop.stream.core.execution.Mail mail = io.nop.stream.core.execution.Mail.control(() -> {
            try {
                future.complete(invokable.keyedStateSnapshot());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }, "queryable-state-snapshot-" + taskKey);
        invokable.getMailboxExecutor().getMailbox().put(mail);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            // TaskMailbox.put silently drops on a closed mailbox too — the bounded
            // wait is the single funnel for both "task stuck" and "task stopped".
            throw new StreamException(ERR_STREAM_STATE_QUERY_TIMEOUT)
                    .param(ARG_TYPE_NAME, taskKey)
                    .param(ARG_DETAIL, "no snapshot from the task thread within "
                            + timeoutMs + "ms (" + STATE_QUERY_TIMEOUT_CONFIG + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamException(ERR_STREAM_INVALID_STATE, e)
                    .param(ARG_DETAIL, "interrupted while waiting for the keyed-state snapshot of " + taskKey);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof StreamException) {
                throw (StreamException) cause;
            }
            throw new StreamException(ERR_STREAM_INVALID_STATE, cause)
                    .param(ARG_DETAIL, "keyed state snapshot failed: " + cause);
        }
    }

    /**
     * W-09 (ruling D.2.3): resolves the bounded state-query window from
     * {@code nop.stream.ops.state-query-timeout-ms} (falls back to
     * {@link #stateQueryTimeoutMs}); values ≤ 0 fail fast typed (parameter
     * discipline aligned with adjudication ② / ⑥).
     */
    private static long resolveStateQueryTimeoutMs() {
        Long configured = Long.getLong(STATE_QUERY_TIMEOUT_CONFIG);
        long effective = configured != null ? configured : stateQueryTimeoutMs;
        if (effective <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, STATE_QUERY_TIMEOUT_CONFIG)
                    .param(ARG_DETAIL, "must be > 0, got: " + effective);
        }
        return effective;
    }

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
        this(nodeId, endpoint, capacity, messageService, clusterRegistry, controlTopic,
                heartbeatIntervalMs, leaseTimeoutMs, DEFAULT_COMMIT_QUEUE_CAPACITY);
    }

    /**
     * Full constructor with an explicit commit-queue capacity
     * (plan 369 Phase 4, R5-CC-10). A non-positive capacity fails fast.
     *
     * @param commitQueueCapacity upper bound of the 2PC commit executor's wait
     *                            queue; overflow falls back to caller-runs
     *                            (see {@link #notifyCheckpointComplete})
     */
    public TaskManager(String nodeId,
                       String endpoint,
                       int capacity,
                       IMessageService messageService,
                       ClusterRegistry clusterRegistry,
                       String controlTopic,
                       long heartbeatIntervalMs,
                       long leaseTimeoutMs,
                       int commitQueueCapacity) {
        if (heartbeatIntervalMs <= 0 || leaseTimeoutMs <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "heartbeatIntervalMs and leaseTimeoutMs must be positive (got "
                            + heartbeatIntervalMs + "/" + leaseTimeoutMs + ")");
        }
        if (commitQueueCapacity <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "commitQueueCapacity must be positive (got " + commitQueueCapacity + ")");
        }
        this.nodeId = nodeId;
        this.heartbeatFailureSuppressor = new io.nop.stream.runtime.support.PeriodicFailureSuppressor(
                "taskmanager.heartbeat:" + nodeId, 30_000L);
        this.endpoint = endpoint;
        this.capacity = capacity;
        this.messageService = messageService;
        this.clusterRegistry = clusterRegistry;
        this.controlTopic = controlTopic;
        this.ackSender = new CheckpointAckSender(this);
        this.capacitySemaphore = new Semaphore(Math.max(1, capacity));
        // CC-17 (R5, plan 2435): bounded queue + AbortPolicy. Normal operation
        // cannot reach saturation (capacitySemaphore pins accepted tasks to
        // capacity == pool size); the bound exists so a shutdown race or a
        // misconfigured capacity>queue degrades LOUD (rejection is converted to
        // a typed rejectDeploy/reportAssignmentFailure at both submit sites).
        this.taskExecutor = new java.util.concurrent.ThreadPoolExecutor(
                Math.max(1, capacity), Math.max(1, capacity),
                60_000L, java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(1000),
                NopStreamThreadFactory.named("tm-task-" + nodeId),
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        // CC-17 (plan 2435): single-thread terminal-report notifier; rejection
        // handler runs the report INLINE even after shutdown (a dying task's
        // terminal report is never dropped — guide #24). Closed LAST in stop().
        this.terminalReportExecutor = new java.util.concurrent.ThreadPoolExecutor(
                1, 1, 60_000L, java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(64),
                NopStreamThreadFactory.named("tm-terminal-report-" + nodeId),
                // CC-17: ALWAYS inline on rejection — including after shutdown
                // (CallerRunsPolicy would silently drop once isShutdown()). A
                // terminal report is never lost; worst case it runs on the
                // dying task's own thread, exactly like the pre-CC-17 behavior.
                (java.util.concurrent.RejectedExecutionHandler) (r, executor) -> r.run());
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
                NopStreamThreadFactory.named("tm-heartbeat-" + nodeId));
        // Dedicated single-thread executor for 2PC checkpoint
        // commits. finishCommit performs blocking JDBC/file commits which must
        // never occupy the message-service dispatch thread (a slow commit there
        // stalls heartbeat, assignment and ACK dispatch for the whole node).
        // Single thread keeps commit ordering per task (subsuming semantics make
        // order benign, but serial execution preserves it anyway).
        //
        // R5-CC-10 (plan 369 Phase 4): the wait queue is BOUNDED. When the
        // single commit thread cannot keep up, the queue can no longer grow
        // without limit; see the saturation policy on
        // notifyCheckpointComplete for the caller-runs fallback and
        // why it was chosen over aborting the notification.
        this.commitQueueCapacity = commitQueueCapacity;
        this.commitExecutor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(commitQueueCapacity),
                NopStreamThreadFactory.named("tm-commit-" + nodeId),
                this::saturatedCommitQueuePolicy);
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

    /**
     * R5-CC-10 saturation policy for the commit executor: CallerRuns with an
     * observable counter (no dropped commit notifications).
     *
     * <p><strong>Choice record (plan 369 F-16)</strong>: of the two candidates —
     * (a) CallerRuns and (b) Abort + typed exception — CallerRuns was chosen.
     * The commit chain is the exactly-once tail: a dropped
     * {@code notifyCheckpointComplete} is only healed by the NEXT epoch's
     * subsuming commit or a restore-time re-commit, so for the last checkpoint
     * of a bounded job an abort would stall the tail commit indefinitely.
     * CallerRuns applies natural backpressure instead and never loses a
     * notification; after shutdown the same always-inline path delivers the
     * notification on the caller thread (C4-012 correction) — it is never
     * silently dropped there either. The critical-thread concern was checked:
     * the callers of
     * {@link #notifyCheckpointComplete} are the TM-side control-topic dispatch
     * thread / coordinator commit forwarder — NOT the checkpoint monitor; the
     * fallback delays at most ONE commit per saturated notify call on that
     * thread, and only while the commit thread is already saturated. The
     * fallback fires are counted ({@code nop.stream.task.commitCallerRuns.total})
     * so sustained saturation is observable.
     *
     * <p>W-12 (CRA 8.41 R4-01, ruling C.3): the handler is SPLIT-WINDOW. In the
     * saturation window (not shutdown, queue full) the R5-CC-10 CallerRuns
     * semantics is preserved unchanged. In the shutdown window the former
     * {@code if (!executor.isShutdown())} guard silently DROPPED the commit —
     * the stop-race window now always runs it inline (CC-17 precedent), so the
     * TaskManagerMain {@code tm.stop() → messageService.close()} gap can no
     * longer lose a 2PC commit notification. The counter increments only when
     * the commit genuinely runs inline (both windows do), keeping the saturation
     * metric honest (pre-fix it counted a dropped notification as a caller-run).
     */
    private void saturatedCommitQueuePolicy(Runnable r, java.util.concurrent.ThreadPoolExecutor executor) {
        if (executor.isShutdown()) {
            // W-12 (C4-012): shutdown race window — always-inline (CC-17
            // semantics). A dropped notification here is only healed by the next
            // epoch's subsuming commit; running it on the caller thread keeps
            // the bounded-job tail commit deliverable through tm.stop().
            nodeMetrics.commitCallerRun();
            LOG.warn("Commit executor already shut down on node {}; running the commit on the "
                            + "caller thread (shutdown drain: running the commit on the caller "
                            + "thread, C4-012)",
                    nodeId);
            r.run();
            return;
        }
        // R5-CC-10: saturation window — CallerRuns backpressure, never dropped.
        nodeMetrics.commitCallerRun();
        LOG.warn("Commit executor queue saturated on node {} (capacity={}); running the commit "
                        + "on the caller thread (CallerRuns backpressure, R5-CC-10)",
                nodeId, commitQueueCapacity);
        r.run();
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
                nodeId, this::getRunningTaskCount);

        // R5-CC-10 backlog gauge: live depth of the commit executor's wait queue.
        io.nop.stream.runtime.metrics.TaskNodeMetrics.registerCommitQueueDepthGauge(
                nodeId, this::commitQueueDepth);

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

        // CC-17 (plan 2435): terminal-report executor closes LAST — after the
        // task threads are cancelled — so dying tasks' terminal reports still
        // enqueue; the always-inline rejection handler covers any straggler
        // that submits after this point.
        //
        // Plan 2436 (WI12 兜底): shutdown() alone is NOT enough — it lets the
        // queue drain ASYNCHRONOUSLY, racing the shutdown hook's datasource
        // close. A queued terminal-FAILED report that loses that race is lost
        // (the INSERT fails into a closed pool; the per-task catch only logs),
        // and with it the coordinator's fast failover trigger: without the
        // FAILED report the 15s lease must lapse, which a fast-restarting node
        // masks by re-registering — no recovery, no fencing rotation
        // (TestS1MultiJvmE2E kill-recover regression, plan 2435 wave). Drain
        // the notifier BEFORE the hook proceeds past stop().
        terminalReportExecutor.shutdown();
        try {
            if (!terminalReportExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("Terminal-report executor did not drain within 5 seconds for node {}", nodeId);
                terminalReportExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            terminalReportExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

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
                } else if (heartbeatFailureSuppressor.success()
                        == io.nop.stream.runtime.support.PeriodicFailureSuppressor.Action.LOG_CLEARED) {
                    // W-13 site ② recovery: renewal succeeded again
                    LOG.info("Heartbeat lease renewal recovered for node {} (failure window cleared)",
                            nodeId);
                }
            } catch (Exception e) {
                // W-13 site ② (CRA 8.41 R4-01, ruling E.1): three-state suppression
                // on the 5s heartbeat cadence (first full / window aggregate / cleared
                // on the next successful renewal)
                io.nop.stream.runtime.support.PeriodicFailureSuppressor.Action a =
                        heartbeatFailureSuppressor.failure(e);
                if (a == io.nop.stream.runtime.support.PeriodicFailureSuppressor.Action.LOG_FULL) {
                    LOG.error("Heartbeat lease renewal failed for node {}", nodeId, e);
                } else if (a == io.nop.stream.runtime.support.PeriodicFailureSuppressor.Action.LOG_AGGREGATE) {
                    LOG.warn("Heartbeat lease renewal failures for node {} — {} suppressed in the "
                                    + "last {}ms window; last: {}",
                            nodeId, heartbeatFailureSuppressor.getLastWindowSuppressedCount(),
                            heartbeatFailureSuppressor.getWindowMs(),
                            heartbeatFailureSuppressor.getLastSummary());
                }
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
        // W-02 (CRA 8.41 R4-01, ruling B.1): the duplicate-slot branches (the
        // former containsKey pre-check and the putIfAbsent race loser) converge
        // into ONE per-key compute that dispatches by generation:
        // - incumbent.epoch < assignment.epoch → a zombie slot from an older
        //   generation (only reachable in the updateFencingToken in-flight
        //   window) is DISPLACED, CC-02 replacement shape (cancel + permit
        //   release via the semaphoreReleased CAS). Strict inequality by design
        //   (review R1 Major-3): selectDisplacedTask's equal-epoch semantic is
        //   candidate-displaces, which contradicts this branch's reject-on->=
        //   semantics — the decision shape is reused, not the function.
        // - incumbent.epoch >= assignment.epoch → same-generation duplicate or
        //   stale late arrival: REJECT — typed reportAssignmentFailure to the
        //   coordinator (the RPC is one-way; a warn-and-return is invisible),
        //   this assignment's permit released, the healthy incumbent untouched.
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

        RunningTask[] displacedOld = new RunningTask[1];
        RunningTask[] rejectedIncumbent = new RunningTask[1];
        runningTasks.compute(taskKey, (k, incumbent) -> {
            if (incumbent == null) {
                return runningTask;
            }
            if (incumbent.getFencingEpoch() < assignment.getFencingEpoch()) {
                // newer generation displaces the zombie old-generation slot
                displacedOld[0] = incumbent;
                return runningTask;
            }
            // same generation or older-than-incumbent: reject, keep incumbent
            rejectedIncumbent[0] = incumbent;
            return incumbent;
        });
        if (rejectedIncumbent[0] != null) {
            LOG.warn("Task {} already runs fencing epoch {} >= assignment epoch {}; rejecting "
                            + "duplicate/stale assignment (incumbent attempt={})",
                    taskKey, rejectedIncumbent[0].getFencingEpoch(), assignment.getFencingEpoch(),
                    rejectedIncumbent[0].attemptId);
            capacitySemaphore.release();
            reportAssignmentFailure(assignment, assignment.getFencingEpoch(),
                    new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                            "Duplicate/stale assignment for " + taskKey
                                    + ": slot already runs fencing epoch " + rejectedIncumbent[0].getFencingEpoch()
                                    + " (>= assignment epoch " + assignment.getFencingEpoch()
                                    + "); the incumbent is untouched"));
            return;
        }
        if (displacedOld[0] != null) {
            LOG.warn("Slot {} displaced older task (epoch {} -> {}, attempt={}); fencing old before "
                    + "assignment redeploy", taskKey, displacedOld[0].getFencingEpoch(),
                    assignment.getFencingEpoch(), displacedOld[0].attemptId);
            displacedOld[0].cancel();
            if (displacedOld[0].semaphoreReleased.compareAndSet(false, true)) {
                capacitySemaphore.release();
            }
        }

        Future<?> future;
        try {
            future = taskExecutor.submit(runningTask);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // CC-17 (plan 2435): shutdown race — report assignment failure loud
            // (one-way RPC: bare throw is invisible), mirroring the capacity path.
            // W-06 (CRA 8.42 R4-02, ruling A.3): BEFORE reporting, run the same
            // three-step cleanup as the build-failure branch below — a submit
            // rejection leaves the just-inserted slot entry as a zombie (run()
            // never executes, so the terminal release is unreachable) and pins
            // one capacity permit until epoch rotation. Conditional remove (an
            // interleaved replacement deploy may already have displaced this
            // entry) + CAS-guarded release (no double release against the
            // cancelled task's exit path).
            runningTasks.remove(taskKey, runningTask);
            runningTask.cancel();
            if (runningTask.semaphoreReleased.compareAndSet(false, true)) {
                capacitySemaphore.release();
            }
            reportAssignmentFailure(assignment, assignment.getFencingEpoch(),
                    new StreamException(ERR_STREAM_INVALID_STATE, e).param(ARG_DETAIL,
                            "Task executor rejected assignment for " + assignment.getVertexId()
                                    + "/" + assignment.getSubtaskIndex() + " (executor shutdown race)"));
            return;
        }
        runningTask.setFuture(future);
        // W-06 (ruling A.3): the deployed counter is only bumped AFTER the submit
        // succeeded — a rejected (never-run) task must not count as deployed.
        nodeMetrics.taskDeployed();

        LOG.info("TaskManager {} accepted assignment for {}/{} (attempt={})",
                nodeId, assignment.getVertexId(), assignment.getSubtaskIndex(),
                assignment.getAttemptId());
    }

    /**
     * Receive and install a fully-built {@link StreamTaskInvokable} for a previously assigned task slot.
     *
     * <p>This is called after {@link #receiveAssignment} when the coordinator sends the
     * serialized operator chain and deployment plan.
     *
     * <p>W-02 (CRA 8.41 R4-01, ruling B.1.2): the install carries the assignment's
     * fencing epoch and is REJECTED when it does not equal the slot's epoch — a
     * stale install must never mix generations into a live slot. The missing-slot
     * branch keeps its silent-return contract (the two-phase protocol's slot may
     * already have been swept by a fencing rotation).
     *
     * @param fencingEpoch the fencing epoch of the assignment this invokable belongs to
     */
    public void installInvokable(String jobId, String vertexId, int subtaskIndex,
                                 StreamTaskInvokable invokable, long fencingEpoch) {
        String taskKey = taskKey(jobId, vertexId, subtaskIndex);
        RunningTask runningTask = runningTasks.get(taskKey);
        if (runningTask == null) {
            LOG.warn("No running task slot for {}/{}/{}", jobId, vertexId, subtaskIndex);
            return;
        }
        if (runningTask.getFencingEpoch() != fencingEpoch) {
            LOG.warn("Rejecting invokable install for {}/{}/{}: slot fencing epoch {} != install epoch {}",
                    jobId, vertexId, subtaskIndex, runningTask.getFencingEpoch(), fencingEpoch);
            return;
        }
        // Inject per-task data-plane
        // metrics on the real in-process install path.
        invokable.setTaskMetrics(new io.nop.stream.core.metrics.MicrometerStreamTaskMetrics(
                io.nop.stream.runtime.metrics.RuntimeMetrics.registry(),
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
     * {@link io.nop.stream.runtime.protocol.TaskStatusReport} to the coordinator
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
        // R6 CC-02 (plan 2326): the displace decision happens INSIDE the per-key
        // compute, atomically against a concurrent newer-generation deploy of the
        // same slot. The previous put-then-inspect window let a LATE stale-epoch
        // deploy displace a healthy newer task (its cancel was already underway
        // and a post-hoc rollback could not undo it, nor race a third deploy's
        // put). Now: an older-or-equal epoch displaces as before; a NEWER epoch
        // already in the slot wins — this stale deploy is rejected WITHOUT
        // touching the slot, and reports FAILED with its own (old) epoch, which
        // the coordinator's stale-report filter drops.
        // compute returns the NEW mapping (unlike put, which returns the OLD
        // value) — so the displaced incumbent is captured INSIDE the lambda; a
        // stale deploy keeps the incumbent untouched and flags the rejection.
        RunningTask[] displacedOld = new RunningTask[1];
        boolean[] staleRejected = new boolean[1];
        boolean[] fencingRotated = new boolean[1];
        RunningTask incumbentForError = runningTasks.compute(taskKey, (k, current) -> {
            // W-02 (CRA 8.41 R4-01, ruling B.1.3): FINAL fencing re-verification
            // inside the slot-write atomic section — pushes the entry-gate epoch
            // check (:686-696) into the same atomic segment as the map write, so
            // an updateFencingToken that rotated the epoch between the gate and
            // this compute can no longer insert the old-generation task into the
            // map (the audit's getAndSet→removeIf non-atomic window). The
            // selectDisplacedTask decision function itself is untouched.
            if (currentFencingEpoch.get() != descriptor.getFencingEpoch()) {
                fencingRotated[0] = true;
                return current;
            }
            // R6 CC-02 (plan 2326): the decision is delegated to the pinned
            // package-private helper (selectDisplacedTask) so the production
            // path and the unit pin cannot drift apart.
            RunningTask winner = selectDisplacedTask(current, runningTask);
            displacedOld[0] = current != null && winner == runningTask ? current : null;
            staleRejected[0] = current != null && winner == current;
            return winner;
        });
        if (fencingRotated[0]) {
            // the epoch rotated away while this deploy was in flight: the slot is
            // left untouched (old incumbent kept / empty slot stays empty) and the
            // deploy fails loud through the typed rejectDeploy channel
            capacitySemaphore.release();
            throw rejectDeploy(descriptor, fencingEpoch, new StreamException(ERR_STREAM_FENCING_TOKEN_MISMATCH)
                    .param(ARG_EXPECTED_TOKEN, currentFencingEpoch.get())
                    .param(ARG_ACTUAL_TOKEN, fencingEpoch)
                    .param(ARG_DETAIL, "deployTask fencing re-check failed at slot write for " + taskKey
                            + ": the active epoch rotated away from " + fencingEpoch
                            + " before the slot write; the deployment is rejected and the slot is untouched"));
        }
        if (staleRejected[0]) {
            // newer generation owns the slot: release this deployment's permit
            // (same conservation rule as the reject path below) and fail loud
            capacitySemaphore.release();
            throw rejectDeploy(descriptor, fencingEpoch, new StreamException(ERR_STREAM_FENCING_TOKEN_MISMATCH)
                    .param(ARG_EXPECTED_TOKEN, incumbentForError.getFencingEpoch())
                    .param(ARG_ACTUAL_TOKEN, fencingEpoch)
                    .param(ARG_DETAIL, "deployTask stale generation: slot " + taskKey
                            + " already runs fencing epoch " + incumbentForError.getFencingEpoch()
                            + " (newer than this deploy's " + fencingEpoch
                            + "); the stale deploy is rejected and the healthy task is untouched (R6 CC-02)"));
        }
        if (displacedOld[0] != null) {
            LOG.warn("Slot {} displaced older task (attempt={}); fencing old before redeploy",
                    taskKey, displacedOld[0].attemptId);
            displacedOld[0].cancel();
            if (displacedOld[0].semaphoreReleased.compareAndSet(false, true)) {
                capacitySemaphore.release();
            }
        }
        Future<?> future;
        try {
            future = taskExecutor.submit(runningTask);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // CC-17 (plan 2435): submit-after-shutdown race — degrade LOUD and
            // through the rejectDeploy→FAILED channel (one-way RPC contract:
            // a bare throw is invisible to the coordinator), not silently.
            // W-06 (CRA 8.42 R4-02, ruling A.3): before the typed rethrow, the
            // same three-step cleanup as the build-failure branch — conditional
            // slot remove (protects an interleaved successor deploy), cancel of
            // the never-run task, CAS-guarded permit release. The remote path's
            // next-redeploy displacement self-heal no longer has to mask a
            // pinned permit + zombie entry.
            runningTasks.remove(taskKey, runningTask);
            runningTask.cancel();
            if (runningTask.semaphoreReleased.compareAndSet(false, true)) {
                capacitySemaphore.release();
            }
            throw rejectDeploy(descriptor, descriptor.getFencingEpoch(),
                    new StreamException(ERR_STREAM_INVALID_STATE, e).param(ARG_DETAIL,
                            "Task executor rejected deployment for " + descriptor.getVertexId()
                                    + "/" + descriptor.getSubtaskIndex() + " (executor shutdown race)"));
        }
        runningTask.setFuture(future);
        // W-06 (ruling A.3): deployed counts only tasks whose submit succeeded —
        // the build-failure path below still counts (its task WAS accepted by the
        // executor and fails via the invokableLatch timeout), unchanged semantics.
        nodeMetrics.taskDeployed();

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
                io.nop.stream.runtime.metrics.RuntimeMetrics.registry(),
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
     *
     * <p><strong>Queue saturation (R5-CC-10)</strong>: commits are dispatched to
     * the dedicated single-thread commit executor whose wait queue is bounded by
     * {@code commitQueueCapacity}. When the queue is full, the commit falls back
     * to the CALLER's thread (CallerRuns): the notification is never dropped —
     * dropping it would defer the 2PC commit to the next epoch's subsuming
     * commit (or a restore-time re-commit), which stalls the bounded-job tail —
     * and the caller-side execution applies natural backpressure to the
     * coordinator's commit forwarder. Fallbacks are counted on
     * {@code nop.stream.task.commitCallerRuns.total} and logged; see
     * {@link #saturatedCommitQueuePolicy} for the recorded choice.
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
     * Current wait-queue depth of the dedicated 2PC commit executor
     * (R5-CC-10 backlog visibility; also backs the
     * {@code nop.stream.task.commitQueueDepth} gauge).
     */
    int commitQueueDepth() {
        return commitExecutor.getQueue().size();
    }

    /** Configured commit-queue capacity (R5-CC-10 upper bound). */
    int commitQueueCapacity() {
        return commitQueueCapacity;
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
    /** CC-17 (plan 2435): submits a terminal report off the dying task's thread. */
    void submitTerminalReport(Runnable report) {
        try {
            terminalReportExecutor.execute(report);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // always-inline handler covers shutdown; belt-and-braces
            report.run();
        }
    }

    /**
     * CC-17 (plan 2435; running flag added by plan 2436): test hook — simulates
     * the stop() shutdown race: the node is UP ({@code running}, as if
     * {@link #start()} had completed) but its task executor is already
     * terminated, so the next deploy hits the
     * submit→{@code RejectedExecutionException} window. Without the running
     * flag the state guard rejects first and the race branch is unreachable.
     */
    void forceTaskExecutorShutdownForTest() {
        this.running = true;
        taskExecutor.shutdownNow();
    }

    /**
     * CC-17 (plan 2435) test hook, restored by plan 2436 after the accessor was
     * lost between plan 2435's C1 and C2 commits (the committed test never
     * compiled): swaps the terminal-report notifier for a stub whose every
     * submission runs {@code probe} asynchronously in place of the report
     * body — the probe stands in for the slow coordinator RPC
     * (TestCc17TerminalReportAsync).
     */
    void terminalReportExecutorForTest(Runnable probe) {
        ExecutorService real = Executors
                .newSingleThreadExecutor(NopStreamThreadFactory.named("tm-terminal-report-test"));
        this.terminalReportExecutor = new java.util.concurrent.AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                real.execute(probe);
            }

            @Override
            public void shutdown() {
                real.shutdown();
            }

            @Override
            public List<Runnable> shutdownNow() {
                return real.shutdownNow();
            }

            @Override
            public boolean isShutdown() {
                return real.isShutdown();
            }

            @Override
            public boolean isTerminated() {
                return real.isTerminated();
            }

            @Override
            public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit)
                    throws InterruptedException {
                return real.awaitTermination(timeout, unit);
            }
        };
    }

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

    /**
     * R6 CC-02 (plan 2326): the slot-displace decision, extracted package-private
     * for the deterministic unit pin. The candidate displaces the incumbent when
     * the slot is empty or the incumbent's generation is older-or-equal; a NEWER
     * incumbent stays untouched (the stale candidate is rejected by the caller —
     * its cancel would kill a healthy task and a post-hoc rollback could neither
     * undo the cancel nor win the race against a third deploy's put).
     *
     * @return the task that should occupy the slot after this deploy attempt
     */
    static RunningTask selectDisplacedTask(RunningTask incumbent, RunningTask candidate) {
        boolean displace = incumbent == null || incumbent.getFencingEpoch() <= candidate.getFencingEpoch();
        return displace ? candidate : incumbent;
    }


    static String taskKey(String jobId, String vertexId, int subtaskIndex) {
        return jobId + "/" + vertexId + "/" + subtaskIndex;
    }
}
