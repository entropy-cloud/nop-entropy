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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.cluster.elector.ILeaderElector;
import io.nop.cluster.elector.ILeaderElectionListener;
import io.nop.cluster.elector.LeaderEpoch;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.JobTerminationMode;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import io.nop.stream.core.execution.plan.DeploymentAssignment;
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
 * <p><strong>Fencing (Stage 39):</strong> A monotonic long fencing epoch is derived
 * on start and on each global recovery / leadership grant. All control messages
 * carry this epoch; TaskManagers reject messages with a stale epoch. The epoch
 * encodes both leadership switch and same-leader recovery into a single long
 * ({@code leaderEpochValue * EPOCH_SCALE + recoveryGen}).
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
    private static final long DEFAULT_TERMINATION_CHECKPOINT_TIMEOUT_MS = 60_000L;
    /** G52: default per-task liveness timeout (a task whose lastProgressTime is older than this is considered stalled). */
    static final long DEFAULT_TASK_TIMEOUT_MS = 60_000L;

    /**
     * Stage 39 fencing unification. The monotonic fencing epoch is encoded as
     * {@code leaderEpochValue * EPOCH_SCALE + recoveryGen}. The scale reserves the
     * low-order digits for the same-leader recovery counter so that:
     * <ul>
     *   <li>leadership switch (leaderEpochValue strictly increases cluster-wide)
     *       always produces a strictly larger fencing epoch than any prior leader's
     *       recoveries — rejects stale-leader control (invariant: stale leader rejected)</li>
     *   <li>same-leader recovery increments recoveryGen, producing a strictly larger
     *       fencing epoch than the prior round — rejects stale same-leader tasks
     *       (invariant: prior-recovery task rejected)</li>
     * </ul>
     * Both invariants hold under a single {@code long} comparison (the data-plane
     * dual-key filter collapses to one long key). Non-HA mode uses leaderEpochValue=0,
     * so fencing epoch == recoveryGen (starts at 0, increments on recovery) — fencing
     * remains effective (Decision 3, zero regression).
     */
    static final long EPOCH_SCALE = 1_000_000L;

    private final String jobId;
    private final String coordinatorId;
    private final DeploymentPlan deploymentPlan;
    private final ClusterRegistry clusterRegistry;
    private final CheckpointCoordinator checkpointCoordinator;
    private final Map<String, IStreamTaskRpcService> taskRpcServices;

    /**
     * The current monotonic fencing epoch for this job execution. Stage 39 unified
     * the legacy composite String fencing token into a single long epoch
     * ({@code leaderEpochValue * EPOCH_SCALE + recoveryGen}). Both fencing
     * invariants (stale-leader rejection + same-leader prior-recovery rejection)
     * hold under a single long comparison.
     */
    private final AtomicLong fencingEpoch;

    /**
     * G24/G25: optional platform leader elector. When non-null the coordinator
     * runs in HA mode (leader-gated lifecycle). When null the coordinator keeps
     * the single-instance behaviour (Stage 39: monotonic long fencing epoch derived
     * with leaderEpoch component 0, always active).
     */
    private ILeaderElector leaderElector;

    /** Handle to the registered election listener, closed on {@link #stop()}. */
    private AutoCloseable electionListenerHandle;

    /**
     * G24/G25: the leadership epoch currently held by this coordinator, or null
     * when in non-HA mode / not yet elected / lost leadership. Drives the
     * leadership component of the composite fencing token.
     */
    private volatile LeaderEpoch currentLeadership;

    /**
     * G24/G25 / Stage 39: recovery generation counter. Incremented on every
     * {@link #globalRecovery()} within the same leadership. Stage 39 folds it into
     * the low-order digits of the single monotonic long fencing epoch
     * ({@code leaderEpochValue * EPOCH_SCALE + recoveryGen}); the data-plane filter
     * is now a single long comparison rather than the legacy composite-String
     * equality check.
     */
    private final AtomicLong recoveryGen = new AtomicLong(0);

    /**
     * G24/G25: whether the control plane is currently permitted on this
     * coordinator. In non-HA mode always true once started. In HA mode true only
     * while this node is the elected leader; flipped to false on leadership loss
     * (standby). Control-plane methods gate on this so a standby coordinator
     * explicitly rejects (never silently executes) control actions.
     */
    private volatile boolean active;

    /** Ordered list of subtask assignments (vertexId → subtaskIndex → assignment) */
    private final Map<String, List<TaskAssignment>> taskAssignmentMap;

    /** Task locations that need to ACK the current checkpoint */
    private final Set<TaskLocation> allTaskLocations;

    /** Failure detection scheduler */
    private final ScheduledExecutorService failureDetector;

    /** Whether the coordinator is running */
    private volatile boolean running;

    /** Timeout for waiting on final checkpoint/savepoint during termination */
    private volatile long terminationCheckpointTimeoutMs = DEFAULT_TERMINATION_CHECKPOINT_TIMEOUT_MS;

    /** AR-6: Guard against double initialization */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * G56: per-subtask attempt counter. Keyed by "{vertexId}/{subtaskIndex}".
     * Incremented on every (re)assignment so that ClusterRegistry preserves a
     * monotonically increasing attempt history. Initialized lazily on first
     * assignTasks(); incremented per-subtask on each globalRecovery().
     */
    private final Map<String, Integer> attemptCounters = new ConcurrentHashMap<>();

    /**
     * G52: per-subtask liveness tracking. Key = "{vertexId}/{subtaskIndex}".
     * Values are the latest known lastProgressTime (updated by
     * {@link #reportNodeTaskLiveness} / {@link #reportTaskStatus}).
     */
    private final Map<String, Long> subtaskLiveness = new ConcurrentHashMap<>();

    /** G52: per-task liveness timeout (configurable). */
    private volatile long taskTimeoutMs = DEFAULT_TASK_TIMEOUT_MS;

    /**
     * G52: when {@code true} (default), a per-task FAILED report triggers
     * {@link #globalRecovery()} automatically. Set to {@code false} for the
     * embedded E2E path (which uses synchronous failure propagation via
     * {@code EmbeddedDistributedExecutor.checkTaskResults} and does not
     * reinstall invokables after recovery).
     */
    private volatile boolean autoRecoverOnFailedReport = true;

    /**
     * G56: job-level terminal status. Transitions {@code CREATED → RUNNING → (FAILED | CANCELED)}
     * Once FAILED, the coordinator stops accepting new assignments / triggers.
     */
    private volatile JobStatus jobStatus = JobStatus.CREATED;

    /**
     * G56: global restart counter. Incremented only inside {@link #globalRecovery()}.
     * When it exceeds {@link #maxRestarts}, the next recovery request calls
     * {@link #failJob(Throwable)} instead.
     *
     * <p>Stage 27 scoped restart (targeted failover) will need its own per-region
     * counter because it does not flow through {@code globalRecovery()} — recorded
     * as a deferred follow-up.
     */
    private final java.util.concurrent.atomic.AtomicInteger restartCount = new java.util.concurrent.atomic.AtomicInteger(0);

    /** G56: max global restarts before the job is marked FAILED (default 3). */
    private volatile int maxRestarts = 3;

    /** G56: cause captured by {@link #failJob(Throwable)}; null until FAILED. */
    private volatile Throwable jobFailureCause;

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
        this.fencingEpoch = new AtomicLong(0L);
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
     * Registers this coordinator in the ClusterRegistry, derives a fencing epoch,
     * and starts the failure detection loop.
     *
     * <p>G24/G25 HA lifecycle:
     * <ul>
     *   <li>Non-HA mode (no {@link ILeaderElector} injected): derives a monotonic
     *       long fencing epoch (leaderEpoch component 0, recoveryGen 0), registers,
     *       marks active immediately. Zero regression for the embedded/local path.</li>
     *   <li>HA mode ({@link ILeaderElector} injected): registers an
     *       {@link ILeaderElectionListener} and returns immediately in STANDBY
     *       (active=false). Activation happens only on the
     *       {@link ILeaderElectionListener#becomeLeader(LeaderEpoch)} callback,
     *       which derives the fencing epoch from the granted {@link LeaderEpoch}.
     *       <strong>{@code whenElectionCompleted()} must NOT be used as an
     *       activation trigger</strong> — it only signals "a result exists",
     *       which may be that another node won (otherwise a follower would
     *       erroneously enter ACTIVE and break invariant #8).</li>
     * </ul>
     */
    public void start() {
        if (!initialized.compareAndSet(false, true)) {
            LOG.warn("JobCoordinator {} already started", coordinatorId);
            return;
        }

        if (leaderElector == null) {
            // Non-HA / embedded-local mode: single-instance behaviour.
            // Stage 39 (Decision 3): non-HA fencing epoch uses leaderEpoch component
            // 0, so epoch == recoveryGen. recoveryGen is seeded to 1 on start so the
            // initial epoch (1) is non-zero and distinct from the 0 "uninitialized"
            // sentinel checked in {@link #collectAck}. globalRecovery() increments
            // recoveryGen so fencing stays effective (stale prior-recovery tasks
            // rejected). Zero regression vs. the legacy random-UUID behaviour for
            // embedded mode (always a fresh in-process coordinator + fresh tasks).
            if (fencingEpoch.get() == 0L) {
                recoveryGen.set(1L);
                fencingEpoch.set(deriveHaFencingEpoch(0L, recoveryGen.get()));
            }
            long epoch = fencingEpoch.get();
            clusterRegistry.registerCoordinator(jobId, coordinatorId, epoch);
            startFailureDetector();
            running = true;
            active = true;
            jobStatus = JobStatus.RUNNING;
            LOG.info("JobCoordinator {} started for job {} with fencing epoch {}",
                    coordinatorId, jobId, epoch);
            return;
        }

        // HA mode: register the election listener and enter STANDBY.
        this.electionListenerHandle = leaderElector.addElectionListener(new CoordinatorElectionListener());
        startFailureDetector();
        running = true;
        active = false;
        jobStatus = JobStatus.RUNNING;
        LOG.info("JobCoordinator {} started in HA STANDBY mode for job {} (hostId={})",
                coordinatorId, jobId, leaderElector.getHostId());

        // Stage 38 Phase 3 integration contract: some platform elector
        // implementations (notably SysDaoLeaderElector) do NOT replay the
        // current leadership state to newly-registered listeners — a listener
        // registered AFTER the elector already granted leadership to this
        // host would otherwise sit in STANDBY forever, waiting for a
        // becomeLeader callback that has already fired. To be robust against
        // this platform quirk, query the current elector state on start: if
        // this host already holds leadership, self-activate synchronously.
        // Future leadership changes still flow through the listener.
        try {
            if (leaderElector.isLeader()) {
                LeaderEpoch currentEpoch = leaderElector.getLeaderEpoch();
                if (currentEpoch != null) {
                    LOG.info("JobCoordinator {} found existing leadership for job {} on start; "
                            + "self-activating (leaderId={}, epoch={})",
                            coordinatorId, jobId, currentEpoch.getLeaderId(), currentEpoch.getEpoch());
                    activateAsLeader(currentEpoch);
                }
            }
        } catch (Exception e) {
            // Best-effort reconciliation — a transient elector read failure
            // must not block coordinator start. The next listener callback
            // (grant/loss) will converge the state.
            LOG.warn("Failed to reconcile initial leadership state for job {}; "
                    + "remaining in STANDBY until the next election callback", jobId, e);
        }
    }

    private void startFailureDetector() {
        failureDetector.scheduleAtFixedRate(
                this::detectFailures,
                DEFAULT_LEASE_CHECK_INTERVAL_MS,
                DEFAULT_LEASE_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Unregisters from the ClusterRegistry and shuts down internal services.
     */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        active = false;

        // G24/G25: stop listening to the elector so callbacks cannot fire into a
        // stopped coordinator. The elector bean itself is IoC-managed and is not
        // shut down here.
        if (electionListenerHandle != null) {
            try {
                electionListenerHandle.close();
            } catch (Exception e) {
                LOG.warn("Failed to unregister election listener for job {}", jobId, e);
            }
            electionListenerHandle = null;
        }

        failureDetector.shutdownNow();

        checkpointCoordinator.shutdown();

        LOG.info("JobCoordinator {} stopped for job {}", coordinatorId, jobId);
    }

    /**
     * G56: marks the job as FAILED and shuts down coordinator-side machinery.
     *
     * <p>Effects:
     * <ul>
     *   <li>Sets {@link #jobStatus} to {@link JobStatus#FAILED}; subsequent
     *       {@link #assignTasks()} calls are rejected.</li>
     *   <li>Stops the failure detector (no more recovery attempts).</li>
     *   <li>Captures the cause for diagnostics.</li>
     * </ul>
     *
     * <p>Idempotent: a second invocation when already FAILED is a no-op.
     */
    public void failJob(Throwable cause) {
        if (jobStatus == JobStatus.FAILED) {
            return;
        }
        this.jobFailureCause = cause;
        this.jobStatus = JobStatus.FAILED;
        this.active = false;
        LOG.error("Job {} FAILED (cause={})", jobId, cause == null ? "unknown" : cause.toString(), cause);
        try {
            failureDetector.shutdownNow();
        } catch (Exception e) {
            LOG.warn("Failed to shut down failure detector during failJob", e);
        }
    }

    // ==================== Task Assignment ====================

    /**
     * Distributes subtasks to TaskManagers.
     *
     * <p>If the {@link DeploymentPlan} carries a materialized
     * {@link io.nop.stream.core.execution.plan.DeploymentAssignment} (generated by the
     * distributed {@code IDeploymentPlanProvider}), this method consumes the pre-computed
     * subtask→node mapping directly. Otherwise it falls back to runtime round-robin
     * assignment over {@link ClusterRegistry#getActiveNodes()} (the legacy LOCAL path).
     *
     * <p>For each assignment:
     * <ol>
     *   <li>Records the assignment in the ClusterRegistry (runtime consistency view)</li>
     *   <li>Sends a {@link TaskAssignment} via the task RPC service</li>
     * </ol>
     */
    public void assignTasks() {
        if (!running) {
            LOG.warn("JobCoordinator not running, cannot assign tasks");
            return;
        }
        // G24/G25: a standby coordinator must never issue assignments.
        if (!active) {
            LOG.warn("JobCoordinator in STANDBY (not leader), cannot assign tasks for job {}", jobId);
            return;
        }
        // G56: once the job is FAILED, no new assignments are permitted
        if (jobStatus == JobStatus.FAILED) {
            LOG.warn("Job {} is FAILED (cause={}); rejecting assignTasks",
                    jobId, jobFailureCause == null ? "unknown" : jobFailureCause.toString());
            return;
        }

        long epoch = fencingEpoch.get();

        DeploymentAssignment assignment = deploymentPlan != null ? deploymentPlan.getAssignment() : null;
        boolean useMaterialized = assignment != null && !assignment.isEmpty();

        List<NodeInfo> activeNodes = useMaterialized ? null : clusterRegistry.getActiveNodes();
        if (!useMaterialized && activeNodes.isEmpty()) {
            LOG.warn("No active nodes available for task assignment");
            return;
        }

        int activeNodeCount = useMaterialized ? -1 : activeNodes.size();
        int runtimeNodeIndex = 0;

        List<TaskLocation> locations = new ArrayList<>();

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
                            System.currentTimeMillis(), attemptNumber);

                    clusterRegistry.assignTask(
                            jobId, vertexId, subtaskIndex,
                            targetNodeId, attemptId, epoch, attemptNumber);

                    IStreamTaskRpcService rpc = taskRpcServices.get(targetNodeId);
                    if (rpc != null) {
                        rpc.receiveAssignment(taskAssignment);
                    } else {
                        throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                                "No RPC service for node " + targetNodeId
                                        + ". All control plane operations require IStreamTaskRpcService.");
                    }

                    vertexAssignments.add(taskAssignment);
                    locations.add(new TaskLocation(jobId, "pipeline-0", vertexId, subtaskIndex));
                }

                taskAssignmentMap.put(vertexId, vertexAssignments);
            }
        }

        allTaskLocations.addAll(locations);
        checkpointCoordinator.setTasksToAcknowledge(locations);

        LOG.info("Assigned {} tasks for job {} (mode={}, source={})",
                locations.size(), jobId,
                useMaterialized ? "materialized" : "runtime-round-robin",
                useMaterialized ? "DeploymentPlan.assignment" : "ClusterRegistry");
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
        // G24/G25: a standby coordinator must never trigger checkpoints.
        if (!active) {
            LOG.warn("JobCoordinator in STANDBY (not leader), cannot trigger checkpoint for job {}", jobId);
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

        long epoch = fencingEpoch.get();

        Set<String> sourceNodeIds = computeSourceNodeIds();

        if (!taskRpcServices.isEmpty()) {
            for (String nodeId : sourceNodeIds) {
                IStreamTaskRpcService rpc = taskRpcServices.get(nodeId);
                if (rpc != null) {
                    try {
                        rpc.triggerCheckpoint(barrier, epoch);
                    } catch (Exception e) {
                        LOG.error("Failed to send checkpoint signal to source node {}", nodeId, e);
                    }
                }
            }
        } else {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
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
        // G24/G25: a standby coordinator must never accept checkpoint ACKs.
        if (!active) {
            LOG.warn("Rejecting checkpoint ACK from {}: coordinator in STANDBY (not leader) for job {}",
                    ack.getTaskLocation(), jobId);
            return false;
        }

        // AR-7: fencingEpoch == 0 means coordinator not initialized (the start()
        // path sets a non-zero epoch; 0 is the pre-init sentinel). Reject all ACKs
        // in that state.
        long epoch = fencingEpoch.get();
        if (epoch == 0L) {
            LOG.warn("Rejecting checkpoint ACK: coordinator fencing epoch not initialized");
            return false;
        }

        // Fencing epoch verification (Stage 39: single long comparison)
        if (epoch != ack.getFencingEpoch()) {
            LOG.warn("Rejecting checkpoint ACK with stale fencing epoch {} (expected {}) from {}",
                    ack.getFencingEpoch(), epoch, ack.getTaskLocation());
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
     * G52: per-task terminal-state report handler.
     *
     * <p>Updates per-subtask liveness on every report. On a FAILED report from a
     * task whose host node is still alive, triggers global recovery (so the
     * coordinator no longer depends solely on node-lease detection to react to
     * a single-task failure). Rejects reports with stale fencing tokens.
     */
    @Override
    public void reportTaskStatus(TaskStatusReport report) {
        if (!running) {
            // G24/G25 (#24): explicit, observable rejection — not a silent debug-log+return.
            LOG.warn("Rejecting task status report: coordinator not running for job {}: {}/{}/{} state={}",
                    jobId, report.getVertexId(), report.getSubtaskIndex(),
                    report.getAttemptNumber(), report.getTerminalState());
            return;
        }
        // G24/G25: a standby coordinator must never process task status (it does
        // not own recovery decisions for this job). Explicit rejection, not silent.
        if (!active) {
            LOG.warn("Rejecting task status report: coordinator in STANDBY (not leader) for job {}: {}/{}/{} state={}",
                    jobId, report.getVertexId(), report.getSubtaskIndex(),
                    report.getAttemptNumber(), report.getTerminalState());
            return;
        }
        // Fencing epoch verification (Stage 39: single long comparison)
        long epoch = fencingEpoch.get();
        if (epoch == 0L || epoch != report.getFencingEpoch()) {
            LOG.warn("Rejecting task status report with stale fencing epoch: {}/{}/{} state={} (expected epoch={})",
                    report.getVertexId(), report.getSubtaskIndex(), report.getAttemptNumber(),
                    report.getTerminalState(), epoch);
            return;
        }

        String livenessKey = report.getVertexId() + "/" + report.getSubtaskIndex();
        long now = System.currentTimeMillis();
        long reportedProgress = report.getLastProgressTime();
        if (reportedProgress > 0) {
            subtaskLiveness.put(livenessKey, reportedProgress);
        } else {
            subtaskLiveness.put(livenessKey, now);
        }

        LOG.info("Task status report: {}/{}/{} attempt={} state={} cause={}",
                report.getVertexId(), report.getSubtaskIndex(), report.getAttemptNumber(),
                report.getTerminalState(),
                report.getTerminalState(),
                report.getErrorCause());

        if (report.getTerminalState() == TaskStatusReport.TerminalState.FAILED) {
            if (autoRecoverOnFailedReport) {
                // G52: per-task failure (node still alive) — trigger recovery rather
                // than waiting for a node-lease timeout. The recovery increments the
                // attempt counter; if the global cap (Phase 3) is hit, failJob runs.
                LOG.warn("Task {}/{}/{} reported FAILED (cause={}); triggering global recovery",
                        report.getVertexId(), report.getSubtaskIndex(), report.getAttemptNumber(),
                        report.getErrorCause());
                try {
                    globalRecovery();
                } catch (Exception e) {
                    LOG.error("globalRecovery triggered by FAILED report threw for job {}", jobId, e);
                }
            } else {
                // Embedded E2E path: synchronous propagation handles the failure;
                // recovery would deadlock the executor (no invokable reinstall).
                LOG.warn("Task {}/{}/{} reported FAILED (cause={}); auto-recovery disabled",
                        report.getVertexId(), report.getSubtaskIndex(), report.getAttemptNumber(),
                        report.getErrorCause());
            }
        }
    }

    /**
     * G52: per-node liveness piggybacked on the heartbeat. Updates the
     * per-subtask liveness map; the {@link #detectFailures()} loop checks for
     * stalls against {@link #taskTimeoutMs}.
     */
    @Override
    public void reportNodeTaskLiveness(String nodeId, List<TaskProgress> progress) {
        if (progress == null || progress.isEmpty()) {
            return;
        }
        if (!running) {
            // G24/G25 (#24): observable rejection, not a silent swallow.
            LOG.warn("Rejecting node task liveness from node {}: coordinator not running for job {}",
                    nodeId, jobId);
            return;
        }
        // G24/G25: a standby coordinator does not own liveness-driven recovery.
        if (!active) {
            LOG.warn("Rejecting node task liveness from node {}: coordinator in STANDBY (not leader) for job {}",
                    nodeId, jobId);
            return;
        }
        for (TaskProgress p : progress) {
            String livenessKey = p.getVertexId() + "/" + p.getSubtaskIndex();
            long current = subtaskLiveness.getOrDefault(livenessKey, 0L);
            // Monotonic: only update if the reported progress is newer
            if (p.getLastProgressTime() > current) {
                subtaskLiveness.put(livenessKey, p.getLastProgressTime());
            }
        }
    }

    /**
     * Returns the current CheckpointCoordinator for inspection.
     */
    public CheckpointCoordinator getCheckpointCoordinator() {
        return checkpointCoordinator;
    }

    // ==================== Failure Detection & Recovery ====================

    /**
     * Checks ClusterRegistry node leases AND per-task liveness (G52). If any assigned
     * node has expired, OR if any task's {@code lastProgressTime} is older than
     * {@link #taskTimeoutMs}, triggers global recovery.
     */
    public void detectFailures() {
        if (!running) {
            return;
        }
        // G24/G25: a standby coordinator does not lead recovery. The detector
        // thread stays alive (for re-election) but performs no work while standby.
        if (!active) {
            return;
        }

        try {
            List<NodeInfo> activeNodes = clusterRegistry.getActiveNodes();
            Set<String> activeNodeIds = new HashSet<>();
            for (NodeInfo node : activeNodes) {
                activeNodeIds.add(node.getNodeId());
            }

            // Check if any assigned node has gone down (node-level lease detection,
            // retained as the bottom-line safety net alongside per-task liveness).
            boolean nodeFailureDetected = false;
            for (List<TaskAssignment> assignments : taskAssignmentMap.values()) {
                for (TaskAssignment assignment : assignments) {
                    if (!activeNodeIds.contains(assignment.getNodeId())) {
                        LOG.warn("Node {} (assigned to {}/{}) has expired lease",
                                assignment.getNodeId(),
                                assignment.getVertexId(),
                                assignment.getSubtaskIndex());
                        nodeFailureDetected = true;
                    }
                }
            }

            // G52: per-task liveness check. A task whose lastProgressTime is older
            // than taskTimeoutMs is considered stalled (node alive but task hung).
            boolean taskStallDetected = false;
            long now = System.currentTimeMillis();
            long cutoff = now - taskTimeoutMs;
            for (List<TaskAssignment> assignments : taskAssignmentMap.values()) {
                for (TaskAssignment assignment : assignments) {
                    String livenessKey = assignment.getVertexId() + "/" + assignment.getSubtaskIndex();
                    Long lastProgress = subtaskLiveness.get(livenessKey);
                    // Only flag stall if we have a recorded liveness timestamp that
                    // is older than the cutoff. A task with no liveness record yet
                    // (just-assigned, before first heartbeat) gets the benefit of
                    // the doubt — node-lease detection will catch a true failure.
                    if (lastProgress != null && lastProgress < cutoff) {
                        LOG.warn("Task {}/{}/{} stalled: lastProgressTime={} (cutoff={})",
                                assignment.getVertexId(), assignment.getSubtaskIndex(),
                                lastProgress, cutoff);
                        taskStallDetected = true;
                    }
                }
            }

            if (nodeFailureDetected || taskStallDetected) {
                LOG.warn("Failures detected (nodeLoss={}, taskStall={}), triggering global recovery for job {}",
                        nodeFailureDetected, taskStallDetected, jobId);
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
     *
     * <p>G56 note: ClusterRegistry attempt history is <strong>preserved</strong>
     * across recoveries — only the in-memory coordinator working set is cleared.
     * Each reassigned subtask bumps its {@code attemptNumber} so
     * {@code ClusterRegistry.getAttemptHistory(...)} retains the full attempt
     * sequence for observability and for Stage 27 targeted failover.
     */
    public void globalRecovery() {
        // G56: global restart strategy. The counter is incremented only here
        // (Stage 27 scoped restart will need its own per-region counter, since
        // scoped restart does not flow through globalRecovery).
        int newCount = restartCount.incrementAndGet();
        if (newCount > maxRestarts) {
            LOG.error("Global restart cap exceeded for job {}: count={} maxRestarts={}",
                    jobId, newCount, maxRestarts);
            failJob(new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "Global restart cap exceeded: count=" + newCount + " maxRestarts=" + maxRestarts));
            return;
        }
        LOG.info("Starting global recovery #{} for job {} (cap={})", newCount, jobId, maxRestarts);

        // G24/G25 / Stage 39 fencing (Decision 1): a single monotonic long epoch
        // encodes both leadership switch and same-leader recovery.
        //  - HA mode: rotate the recoveryGen low-order component, keep the leaderEpoch
        //    component unchanged (same leader). The full long epoch still rotates and
        //    is pushed to all TaskManagers so stale same-leader tasks are fenced. The
        //    leaderEpoch component only rotates on leadership switch.
        //  - Non-HA mode: leaderEpoch component is 0, so fencing epoch == recoveryGen
        //    (Decision 3, zero regression).
        long newEpoch;
        LeaderEpoch leadership = this.currentLeadership;
        long leaderEpochValue = leadership != null ? leadership.getEpoch() : 0L;
        long newGen = recoveryGen.incrementAndGet();
        newEpoch = deriveHaFencingEpoch(leaderEpochValue, newGen);

        rotateFencingEpochAndRestore(newEpoch);
    }

    /**
     * G24/G25 / Stage 39: shared fencing-epoch rotation + control-plane rebuild used
     * by both {@link #globalRecovery()} (same-leader recovery) and
     * {@link #activateAsLeader(LeaderEpoch)} (leadership grant). Rotates the fencing
     * epoch, re-registers the coordinator, pushes the new epoch to all TaskManagers,
     * best-effort restores from the latest checkpoint, and reassigns tasks with the
     * new epoch.
     */
    private void rotateFencingEpochAndRestore(long newEpoch) {
        fencingEpoch.set(newEpoch);

        clusterRegistry.registerCoordinator(jobId, coordinatorId, newEpoch);

        // Clear the in-memory working set only. Do NOT wipe the ClusterRegistry
        // attempt history — G56 requires it to be preserved across recoveries.
        taskAssignmentMap.clear();
        allTaskLocations.clear();

        // Push the rotated fencing epoch to all registered TaskManagers so stale
        // envelopes are rejected at the data plane (Stage 39: RemoteInputChannel /
        // RemoteResultPartition filter on a single long epoch comparison).
        for (IStreamTaskRpcService rpc : taskRpcServices.values()) {
            rpc.updateFencingToken(newEpoch);
        }

        // Restore from latest checkpoint/manifest if available (best-effort).
        try {
            CompletedCheckpoint latest = checkpointCoordinator.getLatestCheckpoint();
            if (latest != null) {
                LOG.info("Recovering from checkpoint {} for job {}", latest.getCheckpointId(), jobId);
            }
        } catch (Exception e) {
            LOG.warn("Failed to restore from checkpoint during recovery", e);
        }

        // Reassign tasks with the new fencing epoch (assignTasks bumps
        // attemptNumber per subtask so the ClusterRegistry history appends a new
        // entry).
        assignTasks();

        LOG.info("Fencing epoch rotated for job {} (epoch={})", jobId, newEpoch);
    }

    /**
     * Stage 39 (Decision 1): derives the single monotonic long fencing epoch from a
     * leadership epoch value and a recovery generation. The leaderEpoch component
     * changes only on leadership switch; recoveryGen changes on each same-leader
     * recovery. Combined via {@code leaderEpochValue * EPOCH_SCALE + recoveryGen}
     * so both fencing invariants hold under a single long comparison.
     *
     * @param leaderEpochValue the platform leader epoch (0 in non-HA mode)
     * @param recoveryGen      the same-leader recovery generation
     * @return the monotonic long fencing epoch
     */
    public static long deriveHaFencingEpoch(long leaderEpochValue, long recoveryGen) {
        return leaderEpochValue * EPOCH_SCALE + recoveryGen;
    }
    /**
     * G24/G25: election-listener callback handler. Activation/deactivation is
     * driven EXCLUSIVELY by {@link #becomeLeader} / {@link #becomeFollower}; the
     * {@link #onException} and {@link #onStop} defaults route to a safe standby
     * degradation.
     */
    private final class CoordinatorElectionListener implements ILeaderElectionListener {
        @Override
        public void becomeLeader(LeaderEpoch leaderEpoch) {
            activateAsLeader(leaderEpoch);
        }

        @Override
        public void becomeFollower(LeaderEpoch leaderEpoch) {
            deactivateToStandby(leaderEpoch);
        }

        @Override
        public void onException(Throwable e) {
            // Safe degradation: an elector error must never leave us acting as
            // leader with a possibly-stale epoch. Drop to standby (explicit, not
            // silent) and let the next election round re-establish leadership.
            LOG.error("Leader elector reported exception for job {}; deactivating to STANDBY", jobId, e);
            deactivateToStandby(null);
        }
    }

    /**
     * G24/G25: leadership-grant activation. Derives a fresh composite fencing
     * token from the granted {@link LeaderEpoch} (recoveryGen reset to 0), marks
     * the coordinator active, and rebuilds the control-plane working set from
     * the latest checkpoint. Idempotent re-entry while already active for the
     * same epoch is a no-op (guards against duplicate callbacks).
     */
    private void activateAsLeader(LeaderEpoch epoch) {
        if (!running) {
            LOG.warn("Ignoring becomeLeader for job {}: coordinator not running", jobId);
            return;
        }
        // Guard against duplicate activation for the same epoch.
        LeaderEpoch current = this.currentLeadership;
        if (active && current != null && current.getLeaderId().equals(epoch.getLeaderId())
                && current.getEpoch() == epoch.getEpoch()) {
            LOG.info("Already active leader for job {} (epoch={}); ignoring duplicate becomeLeader", jobId, epoch.getEpoch());
            return;
        }

        LOG.info("JobCoordinator {} became LEADER for job {} (leaderId={}, epoch={})",
                coordinatorId, jobId, epoch.getLeaderId(), epoch.getEpoch());

        this.currentLeadership = epoch;
        this.recoveryGen.set(0);
        // Mark active BEFORE rebuilding so the internal assignTasks() call passes
        // the active gate.
        this.active = true;

        long token = deriveHaFencingEpoch(epoch.getEpoch(), 0);
        rotateFencingEpochAndRestore(token);
    }

    /**
     * G24/G25: leadership-loss / follower deactivation. Flips the active flag to
     * false so all control-plane methods explicitly reject (never silently
     * execute). Does NOT call {@link #stop()} — the failure detector and election
     * listener remain alive so this node can be re-elected (deactivate is
     * reversible; stop is terminal). A null epoch (elector error / onStop) is
     * treated as a safe standby degradation.
     */
    private void deactivateToStandby(LeaderEpoch epoch) {
        if (!running) {
            return;
        }
        LOG.info("JobCoordinator {} became FOLLOWER/STANDBY for job {} (leaderEpoch={})",
                coordinatorId, jobId, epoch == null ? "null" : (epoch.getLeaderId() + "@" + epoch.getEpoch()));
        this.active = false;
        this.currentLeadership = epoch;
        // In-flight checkpoints will not commit: collectAck is gated on active,
        // so further ACKs are rejected and the pending checkpoint times out /
        // is aborted by the new leader. We intentionally do NOT touch the
        // failure detector here (M2: deactivate != stop).
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
    @Override
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
                throw new StreamException(ERR_STREAM_INVALID_STATE)
                        .param(ARG_DETAIL, "Unknown termination mode: " + mode);
        }
    }

    private void terminateCancel() {
        LOG.info("CANCEL: immediately stopping job {}", jobId);
        // G56 / Stage 28: surface the CANCELED terminal transition explicitly
        // (closes the known gap recorded in JobStatus.java — terminateCancel
        // previously only called stop() and left jobStatus at RUNNING).
        this.jobStatus = JobStatus.CANCELED;
        stop();
    }

    private void terminateDrain() {
        LOG.info("DRAIN: triggering final checkpoint for job {}", jobId);
        try {
            // Stage 28: CheckpointType aligned to checkpoint-design.md §7.3
            // (TERMINAL_SAVEPOINT for DRAIN/SUSPEND). The previous
            // COMPLETED_POINT_TYPE was inconsistent with
            // GraphModelCheckpointExecutor.handleJobTermination (DRAIN branch)
            // and with the authoritative §7.3 table.
            PendingCheckpoint finalCheckpoint = checkpointCoordinator.tryTriggerPendingCheckpoint(
                    CheckpointType.TERMINAL_SAVEPOINT);
            if (finalCheckpoint != null) {
                CheckpointBarrier barrier = new CheckpointBarrier(
                        finalCheckpoint.getCheckpointId(),
                        finalCheckpoint.getTriggerTimestamp(),
                        finalCheckpoint.getCheckpointType());
                sendBarrierToAllTaskManagers(barrier);

                finalCheckpoint.getCompletableFuture()
                        .get(terminationCheckpointTimeoutMs, TimeUnit.MILLISECONDS);
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
                        .get(terminationCheckpointTimeoutMs, TimeUnit.MILLISECONDS);
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
                        .get(terminationCheckpointTimeoutMs, TimeUnit.MILLISECONDS);
                LOG.info("EXPORT_SAVEPOINT: savepoint {} exported for job {}. Job continues running.",
                        savepoint.getCheckpointId(), jobId);
            }
        } catch (Exception e) {
            LOG.error("EXPORT_SAVEPOINT: failed for job {}", jobId, e);
        }
        // Job continues running after EXPORT_SAVEPOINT
    }

    // ==================== Status ====================

    /**
     * G23 / Stage 28: aborts the pending checkpoint identified by {@code epochId}
     * via {@link CheckpointCoordinator#abortPendingCheckpoint}. This triggers the
     * existing abort path which fires the LOCAL abort handler (registered by
     * {@code GraphModelCheckpointExecutor.registerLocalAbortHandler}) to cancel
     * the coordinator-JVM tasks in-process. Recovery strategy is unchanged.
     *
     * <p>If {@code epochId} does not match any currently-pending checkpoint, the
     * call logs a warning and returns — the unmatched case is explicitly
     * observable (no silent swallow, #24).
     */
    @Override
    public void abortCheckpoint(long epochId) {
        if (!running) {
            LOG.debug("Ignoring abortCheckpoint({}) — coordinator not running for job {}", epochId, jobId);
            return;
        }
        PendingCheckpoint pending = checkpointCoordinator.getPendingCheckpoint(epochId);
        if (pending == null) {
            // #24: explicit handling of unmatched epochId (log + return), not a
            // silent no-op. A stale or unknown epoch may arrive from a slow/raced
            // RPC caller; the warning makes it observable.
            LOG.warn("abortCheckpoint({}) for job {}: no pending checkpoint matches this epochId "
                    + "(already completed/aborted/unknown). No-op.", epochId, jobId);
            return;
        }
        checkpointCoordinator.abortPendingCheckpoint(pending, "Coordinator RPC abortCheckpoint(" + epochId + ")");
    }

    /**
     * Stage 39 Phase 3: registers the <b>distributed</b> checkpoint-abort handler on
     * the {@link CheckpointCoordinator}. When a checkpoint is aborted (timeout or
     * explicit abort), the handler fires {@code cancelTask} RPC at every currently
     * assigned remote task via {@link IStreamTaskRpcService#cancelTask} — the abort
     * signal's independent control channel (checkpoint-design §13.2 line 1116: "abort
     * 信号必须有独立于数据流的控制通道").
     *
     * <p>The remote {@code TaskManager.cancelTask} runs {@code RunningTask.cancel()}
     * (mailbox {@code signalCancel} + {@code future.cancel(true)} interrupt), which
     * unblocks a stalled barrier-alignment read (checkpoint-design §13.2 line 1113:
     * "coordinator abort must terminate blocked alignment reads").
     *
     * <p>Relationship to the LOCAL abort path
     * ({@code GraphModelCheckpointExecutor.registerLocalAbortHandler}, Phase 3
     * Decision): the two coexist. The LOCAL path cancels coordinator-JVM-internal
     * tasks (embedded fast-path); this DISTRIBUTED path cancels remote tasks over
     * RPC. A deployment uses one or the other depending on the execution form
     * (embedded vs RPC-distributed). {@code RpcDistributedExecutor} registers this
     * distributed handler; {@code GraphModelCheckpointExecutor} registers its local
     * handler. RPC failure during abort is logged and propagated per-node (not
     * silently swallowed — plan guide #24); the next failure-detection /
     * global-recovery cycle still fences the un-canceled tasks via the rotated epoch.
     */
    public void registerDistributedAbortHandler() {
        checkpointCoordinator.setAbortHandler(checkpointId -> {
            if (!active) {
                // A standby coordinator must not issue cancelTask RPCs.
                LOG.warn("Ignoring distributed abort({}) for job {}: coordinator not active", checkpointId, jobId);
                return;
            }
            LOG.warn("Distributed checkpoint abort {} for job {}: firing cancelTask RPC at all assigned remote tasks",
                    checkpointId, jobId);
            for (Map.Entry<String, List<TaskAssignment>> entry : taskAssignmentMap.entrySet()) {
                for (TaskAssignment ta : entry.getValue()) {
                    IStreamTaskRpcService rpc = taskRpcServices.get(ta.getNodeId());
                    if (rpc == null) {
                        LOG.warn("No task RPC service for node {} during abort of {}/{}/{} — "
                                + "relying on epoch-rotation fencing for the un-canceled task",
                                ta.getNodeId(), ta.getVertexId(), ta.getSubtaskIndex());
                        continue;
                    }
                    try {
                        rpc.cancelTask(ta.getJobId(), ta.getVertexId(), ta.getSubtaskIndex());
                    } catch (Exception e) {
                        // #24: explicit propagation — log per-node failure; the next
                        // recovery cycle fences via the rotated epoch.
                        LOG.error("cancelTask RPC failed for {}/{}/{} (node {}) during abort {}",
                                ta.getJobId(), ta.getVertexId(), ta.getSubtaskIndex(),
                                ta.getNodeId(), checkpointId, e);
                    }
                }
            }
        });
    }

    public String getJobId() {
        return jobId;
    }

    public String getCoordinatorId() {
        return coordinatorId;
    }

    public long getFencingEpoch() {
        return fencingEpoch.get();
    }

    public void setFencingEpoch(long epoch) {
        fencingEpoch.set(epoch);
    }

    /**
     * G24/G25: injects the platform {@link ILeaderElector}. When non-null the
     * coordinator runs in HA (leader-gated) mode; when null it keeps the legacy
     * single-instance behaviour. Must be set BEFORE {@link #start()}. The elector
     * bean is IoC-managed (e.g. {@code SysDaoLeaderElector}); this coordinator
     * only consumes the {@link ILeaderElector} contract.
     */
    public void setLeaderElector(ILeaderElector leaderElector) {
        this.leaderElector = leaderElector;
    }

    public ILeaderElector getLeaderElector() {
        return leaderElector;
    }

    /**
     * G24/G25: whether the control plane is currently active on this coordinator.
     * In non-HA mode always true once started. In HA mode true only while this
     * node is the elected leader.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * G24/G25: the leadership epoch currently held (HA mode), or null when
     * non-HA / not yet elected / lost leadership.
     */
    public LeaderEpoch getCurrentLeadership() {
        return currentLeadership;
    }

    /**
     * G24/G25: current recovery generation (composite-token suffix). Resets to 0
     * on each leadership grant and increments on each same-leader
     * {@link #globalRecovery()}.
     */
    public long getRecoveryGen() {
        return recoveryGen.get();
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * G24/G25: whether the internal failure-detector scheduler is still alive
     * (i.e. not shut down). Used by HA tests to verify the
     * {@code deactivate != stop} contract — leadership-loss must flip
     * {@link #isActive()} to false but must NOT terminate the detector (so the
     * node can be re-elected). Only {@link #stop()} / {@link #failJob(Throwable)}
     * shut down the detector.
     */
    public boolean isFailureDetectorAlive() {
        return !failureDetector.isShutdown();
    }

    /**
     * G24/G25 / G52: number of tracked per-subtask liveness entries. Exposed
     * for diagnostics and for HA tests to verify that a STANDBY coordinator
     * rejects (rather than silently accepts) {@code reportNodeTaskLiveness}
     * calls — the map must stay empty while in STANDBY.
     */
    public int getSubtaskLivenessCount() {
        return subtaskLiveness.size();
    }

    public void setTerminationCheckpointTimeoutMs(long timeoutMs) {
        this.terminationCheckpointTimeoutMs = timeoutMs;
    }

    /**
     * G52: per-task liveness timeout. A task whose {@code lastProgressTime} is
     * older than this is considered stalled and triggers recovery on the next
     * {@link #detectFailures()} tick.
     */
    public void setTaskTimeoutMs(long taskTimeoutMs) {
        this.taskTimeoutMs = taskTimeoutMs;
    }

    public long getTaskTimeoutMs() {
        return taskTimeoutMs;
    }

    /**
     * G52: configures whether a per-task FAILED report triggers automatic
     * {@link #globalRecovery()}. Default {@code true} (production behavior).
     * Embedded E2E paths set this to {@code false} to preserve synchronous
     * failure propagation.
     */
    public void setAutoRecoverOnFailedReport(boolean enabled) {
        this.autoRecoverOnFailedReport = enabled;
    }

    public boolean isAutoRecoverOnFailedReport() {
        return autoRecoverOnFailedReport;
    }

    /**
     * G56: max global restarts before {@link #failJob(Throwable)} fires.
     * Default 3. The counter is incremented only inside {@link #globalRecovery()}.
     */
    public void setMaxRestarts(int maxRestarts) {
        this.maxRestarts = Math.max(0, maxRestarts);
    }

    public int getMaxRestarts() {
        return maxRestarts;
    }

    public int getRestartCount() {
        return restartCount.get();
    }

    /**
     * G23 / Stage 28: returns a serializable snapshot of the current job status
     * plus the captured failure cause. Satisfies
     * {@link IStreamCoordinatorRpcService#getJobStatus()} so that local callers
     * and (Stage 39) cross-JVM callers observe the same contract.
     *
     * @return a {@link JobStatusResponse} carrying the current status (never null)
     */
    @Override
    public JobStatusResponse getJobStatus() {
        String cause = jobFailureCause == null ? null : jobFailureCause.toString();
        return new JobStatusResponse(jobStatus, cause);
    }

    public Throwable getJobFailureCause() {
        return jobFailureCause;
    }

    private void sendBarrierToAllTaskManagers(CheckpointBarrier barrier) {
        long epoch = fencingEpoch.get();
        for (Map.Entry<String, IStreamTaskRpcService> entry : taskRpcServices.entrySet()) {
            try {
                entry.getValue().triggerCheckpoint(barrier, epoch);
            } catch (Exception e) {
                LOG.error("Failed to send barrier signal to node {}", entry.getKey(), e);
            }
        }
    }

    private Set<String> computeSourceNodeIds() {
        Set<String> sourceVertexIds = computeSourceVertexIds();
        Set<String> nodeIds = new HashSet<>();
        for (String vertexId : sourceVertexIds) {
            List<TaskAssignment> assignments = taskAssignmentMap.get(vertexId);
            if (assignments != null) {
                for (TaskAssignment assignment : assignments) {
                    nodeIds.add(assignment.getNodeId());
                }
            }
        }
        return nodeIds;
    }

    private Set<String> computeSourceVertexIds() {
        if (deploymentPlan == null || deploymentPlan.getPartitionedPlan() == null) {
            return Collections.emptySet();
        }

        io.nop.stream.core.execution.plan.PartitionedPlan plan = deploymentPlan.getPartitionedPlan();
        Set<String> vertexIds = plan.getVertexPlans().keySet();
        Set<String> targetVertexIds = new HashSet<>();
        for (io.nop.stream.core.execution.plan.PartitionedPlan.EdgePlan ep : plan.getEdgePlans()) {
            if (ep.getTargetVertexId() != null) {
                targetVertexIds.add(ep.getTargetVertexId());
            }
        }

        Set<String> sourceVertexIds = new HashSet<>(vertexIds);
        sourceVertexIds.removeAll(targetVertexIds);
        return sourceVertexIds;
    }

}
