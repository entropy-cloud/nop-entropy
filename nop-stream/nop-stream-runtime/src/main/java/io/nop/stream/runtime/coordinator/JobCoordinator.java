/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.api.core.time.CoreMetrics;
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
import java.util.concurrent.locks.ReentrantLock;

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
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import io.nop.stream.core.execution.plan.DeploymentAssignment;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.util.NopStreamThreadFactory;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.event.StreamJobEvent;
import io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.rpc.TaskDeploymentDescriptor;
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
 * <p><strong>Fencing:</strong> A monotonic long fencing epoch is derived
 * on start and on each global recovery / leadership grant. All control messages
 * carry this epoch; TaskManagers reject messages with a stale epoch. The epoch
 * encodes both leadership switch and same-leader recovery into a single long
 * ({@code leaderEpochValue * EPOCH_SCALE + recoveryGen}).
 *
 * <p><strong>Checkpoint Flow:</strong>
 * <ol>
 *   <li>{@link #triggerCheckpoint()} → opens a PendingCheckpoint via
 *       {@code CheckpointCoordinator.tryTriggerPendingCheckpoint}, then fans the
 *       {@link CheckpointBarrier} RPC out to every node hosting an assigned
 *       subtask; source tasks inject the barrier into the data plane</li>
 *   <li>TaskManagers process barriers, snapshot state, send {@link CheckpointAckMessage} back</li>
 *   <li>{@link #collectAck(CheckpointAckMessage)} → verifies fencing token, forwards to CheckpointCoordinator</li>
 *   <li>When all ACKs collected → CheckpointCoordinator builds {@link EpochManifest}, persists, notifies commit</li>
 * </ol>
 */
@Internal
public class JobCoordinator implements IStreamCoordinatorRpcService {

    private static final Logger LOG = LoggerFactory.getLogger(JobCoordinator.class);

    private static final long DEFAULT_LEASE_CHECK_INTERVAL_MS = 5000L;
    private static final long DEFAULT_TERMINATION_CHECKPOINT_TIMEOUT_MS = 60_000L;
    /**
     * Default per-task aliveness timeout (a task whose recorded liveness signal
     * is older than this is considered stalled).
     */
    static final long DEFAULT_TASK_TIMEOUT_MS = 60_000L;

    /**
     * Default deployment grace period (plan 369 Phase 4, R5-CC-07): how long an
     * assignment with NO liveness record is given the benefit of the doubt
     * after its deploy RPC was issued. Must comfortably exceed the TaskManager
     * heartbeat interval (5s) so a successfully deployed task's first liveness
     * report always lands within the window.
     */
    static final long DEFAULT_DEPLOY_GRACE_PERIOD_MS = 60_000L;

    /**
     * The monotonic fencing epoch is encoded as
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
     * remains effective.
     */
    static final long EPOCH_SCALE = 1_000_000L;

    private final String jobId;
    private final String coordinatorId;
    private final DeploymentPlan deploymentPlan;
    private final ClusterRegistry clusterRegistry;
    private final CheckpointCoordinator checkpointCoordinator;
    private final Map<String, IStreamTaskRpcService> taskRpcServices;

    /**
     * The current monotonic fencing epoch for this job execution, encoded as
     * {@code leaderEpochValue * EPOCH_SCALE + recoveryGen}. Both fencing
     * invariants (stale-leader rejection + same-leader prior-recovery rejection)
     * hold under a single long comparison.
     */
    private final AtomicLong fencingEpoch;

    /**
     * Optional platform leader elector. When non-null the coordinator
     * runs in HA mode (leader-gated lifecycle). When null the coordinator keeps
     * the single-instance behaviour (monotonic long fencing epoch derived
     * with leaderEpoch component 0, always active).
     */
    private ILeaderElector leaderElector;

    /** Handle to the registered election listener, closed on {@link #stop()}. */
    private AutoCloseable electionListenerHandle;

    /**
     * The leadership epoch currently held by this coordinator, or null
     * when in non-HA mode / not yet elected / lost leadership. Drives the
     * leadership component of the composite fencing token.
     */
    private volatile LeaderEpoch currentLeadership;

    /**
     * Recovery generation counter. Incremented on every
     * {@link #globalRecovery()} within the same leadership. Folded into
     * the low-order digits of the single monotonic long fencing epoch
     * ({@code leaderEpochValue * EPOCH_SCALE + recoveryGen}); the data-plane filter
     * is a single long comparison.
     */
    private final AtomicLong recoveryGen = new AtomicLong(0);

    /**
     * Whether the control plane is currently permitted on this
     * coordinator. In non-HA mode always true once started. In HA mode true only
     * while this node is the elected leader; flipped to false on leadership loss
     * (standby). Control-plane methods gate on this so a standby coordinator
     * explicitly rejects (never silently executes) control actions.
     */
    private volatile boolean active;

    /** Ordered list of subtask assignments (vertexId → subtaskIndex → assignment) */
    private final Map<String, List<TaskAssignment>> taskAssignmentMap;

    /**
     * Assignment-planning collaborator (assignment materialization, RPC fan-out,
     * assigned-node computation). Holds the SHARED working-set instances, so its
     * mutations are visible through this coordinator's fields.
     */
    private final AssignmentPlanner assignmentPlanner;

    /** Task locations that need to ACK the current checkpoint */
    private final Set<TaskLocation> allTaskLocations;

    /** Failure detection scheduler */
    private final ScheduledExecutorService failureDetector;

    /** Whether the coordinator is running */
    private volatile boolean running;

    /** Timeout for waiting on final checkpoint/savepoint during termination */
    private volatile long terminationCheckpointTimeoutMs = DEFAULT_TERMINATION_CHECKPOINT_TIMEOUT_MS;

    /** Guard against double initialization */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Per-subtask attempt counter. Keyed by "{vertexId}/{subtaskIndex}".
     * Incremented on every (re)assignment so that ClusterRegistry preserves a
     * monotonically increasing attempt history. Initialized lazily on first
     * assignTasks(); incremented per-subtask on each globalRecovery().
     */
    private final Map<String, Integer> attemptCounters = new ConcurrentHashMap<>();

    /**
     * Per-subtask liveness tracking. Key =
     * "{vertexId}/{subtaskIndex}".
     * Values are the latest known task-aliveness timestamp (updated by
     * {@link #reportNodeTaskLiveness} / {@link #reportTaskStatus}).
     *
     * <p>Semantics: the value is the <b>task aliveness</b> signal,
     * decoupled from data progress — MIDDLE/SINK report the task thread's
     * loop activity (fresh while the loop cycles, data or idle), SOURCE/
     * SELF_CONTAINED report the TaskManager wall clock. A COMPLETED report
     * <b>removes</b> the entry so the task is permanently excluded from stall
     * detection ({@link #detectFailures} gives a task with no record the
     * benefit of the doubt).
     */
    private final Map<String, Long> subtaskLiveness = new ConcurrentHashMap<>();

    /**
     * REG-02 (plan366-regression audit): liveness keys of subtasks whose
     * COMPLETED report has been processed. A finished task is skipped by the
     * TaskManager heartbeat loop, but an in-flight heartbeat batch (built
     * before the task finished, delivered after the COMPLETED report removed
     * the liveness entry) must not resurrect the frozen timestamp via
     * {@code merge}'s insert-on-absent — after taskTimeoutMs the detector
     * would flag the healthy finished task TASK_STALL and burn a recovery.
     * The tombstone is checked inside {@link #livenessLock} by
     * {@link #reportNodeTaskLiveness}, which closes the window; cleared on
     * every fencing rotation (each subtask is re-attempted in a new
     * generation).
     */
    private final Set<String> completedSubtaskKeys = ConcurrentHashMap.newKeySet();

    /**
     * Monitor closing the COMPLETED-remove vs in-flight-heartbeat-merge race
     * (REG-02). Both critical sections are O(1) on the 5s heartbeat cadence.
     */
    private final Object livenessLock = new Object();

    /**
     * CC-01 (R5 audit): whether this coordinator has EVER materialized a
     * non-empty assignment working set. Arms the empty-working-set sentinel in
     * {@link #detectFailures()}: a fresh coordinator that has not assigned yet
     * is legitimately empty and must not self-recover, while an empty working
     * set AFTER a first assignment means a recovery aborted mid-flight.
     */
    private volatile boolean everAssigned;

    /** Per-task liveness timeout (configurable). */
    private volatile long taskTimeoutMs = DEFAULT_TASK_TIMEOUT_MS;

    /**
     * R5-CC-07 (plan 369 Phase 4): per-assignment deploy-issue timestamps,
     * keyed by the liveness key {@code "{vertexId}/{subtaskIndex}"}. Populated
     * right before each assignment RPC fan-out (all three drive sites:
     * {@link #assignTasks()}, {@link #globalRecovery(RecoveryCause)},
     * {@link #activateAsLeader(LeaderEpoch, boolean)}) and removed when the
     * task's first liveness/terminal report arrives. Cleared with the working
     * set on every fencing rotation (generation-scoped, like the liveness map).
     *
     * <p>This is the grace-period clock that closes the permanent
     * benefit-of-the-doubt blind spot: a deploy whose one-way transport RPC was
     * lost produces no liveness record EVER, so pre-fix such an assignment was
     * exempt from detection forever. With the grace period, "no liveness AND
     * deployed longer than {@link #deployGracePeriodMs} ago" is treated as a
     * deployment failure and re-triggers recovery.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> deployIssuedAtMs =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * R5-CC-07: liveness keys whose current-generation deploy RPC FAILED at the
     * transport level (caught by {@code AssignmentPlanner.executeAssignmentFanOut}).
     * Observability + diagnosis aid: these keys are known-bad from issue time;
     * recovery still waits for the grace period so a slow-but-alive transport
     * and a failed one behave identically. Cleared on every fencing rotation.
     */
    private final java.util.Set<String> deployTransportFailures = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * R5-CC-07: deployment grace period in ms. An assignment with no liveness
     * record whose deploy was issued more than this long ago is treated as a
     * deployment failure by {@link #detectFailures()}.
     */
    private volatile long deployGracePeriodMs = DEFAULT_DEPLOY_GRACE_PERIOD_MS;

    /**
     * Injectable wall clock for {@link #detectFailures()} and the deploy-issue
     * timestamps (package-private for focused tests; production reads
     * {@code CoreMetrics}).
     */
    private volatile java.util.function.LongSupplier clock = CoreMetrics::currentTimeMillis;

    /**
     * When {@code true} (default), a per-task FAILED report triggers
     * {@link #globalRecovery()} automatically. Set to {@code false} for the
     * embedded E2E path (which uses synchronous failure propagation via
     * {@code EmbeddedDistributedExecutor.checkTaskResults} and does not
     * reinstall invokables after recovery).
     */
    private volatile boolean autoRecoverOnFailedReport = true;

    /**
     * Job-level terminal status. Transitions {@code CREATED → RUNNING → (FAILED | CANCELED)}
     * Once FAILED, the coordinator stops accepting new assignments / triggers.
     */
    private volatile JobStatus jobStatus = JobStatus.CREATED;

    /**
     * Global restart counter. Incremented only inside {@link #globalRecovery()}.
     * When it exceeds {@link #maxRestarts}, the next recovery request calls
     * {@link #failJob(Throwable)} instead.
     */
    private final java.util.concurrent.atomic.AtomicInteger restartCount = new java.util.concurrent.atomic.AtomicInteger(0);

    /** Max global restarts before the job is marked FAILED (default 3). */
    private volatile int maxRestarts = 3;

    /**
     * Stall-triggered recovery counter. Liveness-stall
     * recoveries draw from this SEPARATE budget so that stall-induced recovery
     * storms can never starve the real-failure budget ({@link #restartCount}/
     * {@link #maxRestarts}) — a real node kill must
     * still be recoverable even if stall recoveries already fired. Exceeding
     * the stall cap still fails the job (persistent stall is a terminal
     * defect; bounded retries preserved).
     */
    private final java.util.concurrent.atomic.AtomicInteger stallRestartCount =
            new java.util.concurrent.atomic.AtomicInteger(0);

    /** Max stall-triggered recoveries before the job is marked FAILED (default 3). */
    private volatile int maxStallRestarts = 3;

    /**
     * Cooldown window for stall-triggered recoveries. A
     * stall recovery request arriving within this window of the previous
     * stall recovery is skipped with an observable WARN (the periodic
     * failure detector re-fires naturally after the cooldown) — prevents a
     * hot-loop of stall recoveries burning the stall budget in seconds.
     */
    private volatile long stallRecoveryCooldownMs = 30_000L;

    /** Wall-clock ms of the last stall-triggered recovery (0 = none yet). */
    private volatile long lastStallRecoveryAt = 0L;

    /**
     * Mutual-exclusion monitor for the recovery critical section.
     * Two concurrent sources reach {@link #globalRecovery()}: the single-threaded
     * {@code failureDetector} (via {@link #detectFailures()}) and the RPC server
     * thread pool (via {@code reportTaskStatus} on a FAILED report with
     * {@code autoRecoverOnFailedReport=true}). This lock serializes them so the
     * rotate-epoch → register-coordinator → update-fencing-token → clear working
     * set → materialize-assignment sequence executes atomically.
     *
     * <p>The RPC fan-out ({@code deployTask}/{@code receiveAssignment}) is executed
     * OUTSIDE the lock: the assignment list is materialized into {@link #taskAssignmentMap}
     * under the lock, the lock is released, and only then are the per-subtask RPCs
     * issued. This avoids holding the lock across N×TaskManager blocking IO (an RPC
     * error mid-fan-out is a transient inconsistency cleaned by the next recovery).
     *
     * <p>Reentrant so {@link #assignTasks()} (which also acquires the lock for the
     * standalone-assignment path) can be reused safely.
     */
    private final ReentrantLock recoveryLock = new ReentrantLock();

    /**
     * Dedup flag for the {@code globalRecovery} trigger path.
     *
     * <p>Two concurrent sources (failure-detector thread + RPC FAILED report) can
     * fire {@link #requestRecovery()} for the <em>same</em> failure event. Without
     * dedup, both would reach {@link #globalRecovery()} and — depending on thread
     * scheduling — both complete the full rotate/clear/assign sequence, bumping
     * {@code restartCount} twice and producing duplicate attemptIds.
     *
     * <p>Callers are serialized at the <em>trigger boundary</em>
     * via a single CAS: {@link #requestRecovery()} does {@code compareAndSet(false,
     * true)}; exactly one caller wins regardless of subsequent scheduling, and the
     * loser short-circuits with an observable WARN. The flag is cleared at the
     * END of {@link #globalRecovery()}'s locked section (in {@code finally}, before
     * {@code unlock}) so the CAS window stays closed for the recovery's ENTIRE
     * duration — a redundant trigger arriving mid-recovery observes
     * {@code recoveryPending=true} and its CAS fails. Any trigger firing AFTER
     * the in-flight recovery completes re-arms the flag and runs a fresh,
     * legitimate recovery; globalRecovery is global/idempotent so a truly
     * redundant post-completion trigger is wasteful but not corrupting, and the
     * failure detector's periodicity bounds any unhandled gap.
     *
     * <p>Memory-model notes: writes to this flag happen-before {@code globalRecovery}'s
     * lock acquisition (program order in the caller) and the lock's release/acquire
     * pair provides the synchronization barrier; {@link AtomicBoolean} is used for
     * the atomic CAS semantics, not for volatile visibility alone.
     */
    private final AtomicBoolean recoveryPending = new AtomicBoolean(false);

    /** Cause captured by {@link #failJob(Throwable)}; null until FAILED. */
    private volatile Throwable jobFailureCause;

    /**
     * Remote-deploy mode toggle. When {@code true},
     * {@link #assignTasks()} builds a {@link TaskDeploymentDescriptor} for each
     * subtask and calls {@link IStreamTaskRpcService#deployTask} over RPC (the
     * TaskManager rebuilds its own invokable locally). When {@code false} (default,
     * in-process path), {@link #assignTasks()} calls
     * {@link IStreamTaskRpcService#receiveAssignment} and the embedding executor
     * installs the invokable via a direct {@code TaskManager.installInvokable}
     * Java call. The recovery path inherits the same mode —
     * {@code globalRecovery()} → {@link #rotateFencingEpochCoreLocked} →
     * {@link AssignmentPlanner#prepareAssignmentsLocked} →
     * {@link AssignmentPlanner#executeAssignmentFanOut}.
     */
    private volatile boolean remoteDeployMode = false;

    /**
     * The {@link JobGraph} used to build
     * {@link TaskDeploymentDescriptor}s in remote-deploy mode. Required when
     * {@link #remoteDeployMode} is {@code true}; ignored otherwise.
     */
    private volatile JobGraph jobGraph;

    /**
     * Serializable pipeline declaration
     * shipped in the deployment descriptors INSTEAD of the (non-serializable)
     * compiled {@link #jobGraph} when the pipeline is XDSL-declared. Each
     * TaskManager rebuilds an identical graph locally (see
     * {@link io.nop.stream.runtime.rpc.RemotePipelineSpec}). When non-null, the
     * descriptors carry the spec; {@link #jobGraph} is still required for the
     * coordinator's own fail-fast/plan consistency.
     */
    private volatile io.nop.stream.runtime.rpc.RemotePipelineSpec pipelineSpec;

    /**
     * Shared filesystem path of the
     * {@code LocalFileCheckpointStorage} directory. Passed in every
     * {@link TaskDeploymentDescriptor} so a recovery-deployed TaskManager can
     * restore operator state from the same path. Null for a fresh job.
     */
    private volatile String checkpointStoragePath;

    /** Optional periodic checkpoint driver for launch-path checkpoint scheduling. */
    private volatile java.util.concurrent.ScheduledExecutorService periodicCheckpointScheduler;

    /** Whether {@link #startPeriodicCheckpoints(long)} has been called and not stopped. */
    private volatile boolean periodicCheckpointsStarted;

    /**
     * Job-level event bus. Always carries the built-in
     * logging listener; also injected into the {@link CheckpointCoordinator}
     * so checkpoint progress events reach the job's listeners. The engine
     * recovery meter is updated from the same real
     * lifecycle paths that fire events.
     */
    private final io.nop.stream.runtime.event.StreamJobEventBus jobEventBus =
            new io.nop.stream.runtime.event.StreamJobEventBus();

    /**
     * Logical health state machine, driven by this
     * coordinator's real lifecycle events (start / globalRecovery / failJob /
     * terminate) and healed by the durable-checkpoint completion callback
     * (routed from the shared event bus below). Never timer-inferred.
     */
    private final io.nop.stream.runtime.health.JobHealthStateMachine health;

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
        this.assignmentPlanner = new AssignmentPlanner(jobId, deploymentPlan, clusterRegistry,
                this.checkpointCoordinator, this.taskRpcServices, this.fencingEpoch,
                this.taskAssignmentMap, this.allTaskLocations, this.attemptCounters);
        this.failureDetector = Executors.newSingleThreadScheduledExecutor(
                NopStreamThreadFactory.named("jc-failure-detector-" + jobId));
        this.running = false;

        // Built-in logging listener + share the bus with the
        // checkpoint coordinator so checkpoint events reach job listeners.
        this.jobEventBus.addListener(new io.nop.stream.runtime.event.LoggingJobEventListener());
        if (this.checkpointCoordinator != null) {
            this.checkpointCoordinator.setJobEventBus(this.jobEventBus);
        }

        // Health state machine wiring:
        // entering DEGRADED is itself a job event (alert routing input,
        // cross-JVM log evidence); a durable checkpoint completion (fired by
        // the real CheckpointCoordinator completion path on this bus) heals
        // DEGRADED -> RUNNING.
        this.health = new io.nop.stream.runtime.health.JobHealthStateMachine(jobId);
        this.health.addListener((jid, from, to, cause) -> {
            if (to == io.nop.stream.runtime.health.StreamJobHealth.DEGRADED) {
                jobEventBus.fire(StreamJobEvent.simple(jobId,
                        io.nop.stream.runtime.event.StreamJobEvent.EventType.JOB_DEGRADED, cause));
            }
        });
        this.jobEventBus.addListener(event -> {
            if (event.getType()
                    == io.nop.stream.runtime.event.StreamJobEvent.EventType.CHECKPOINT_COMPLETED) {
                health.onDurableCheckpoint(
                        event.getCheckpointId() == null ? -1L : event.getCheckpointId());
            }
        });
    }

    /**
     * Registers a health-state listener (transitions of
     * the logical health machine). Listener failures are logged by the
     * machine and never break the control path.
     */
    public void addHealthListener(io.nop.stream.runtime.health.JobHealthListener listener) {
        health.addListener(listener);
    }

    /** Current logical health state. */
    public io.nop.stream.runtime.health.StreamJobHealth getHealth() {
        return health.getCurrent();
    }

    /**
     * Registers a job lifecycle/progress event listener.
     * Listener failures are logged and swallowed by the bus.
     */
    public void addJobEventListener(io.nop.stream.runtime.event.StreamJobEventListener listener) {
        jobEventBus.addListener(listener);
    }

    public io.nop.stream.runtime.event.StreamJobEventBus getJobEventBus() {
        return jobEventBus;
    }

    // ==================== Lifecycle ====================

    /**
     * Registers this coordinator in the ClusterRegistry, derives a fencing epoch,
     * and starts the failure detection loop.
     *
     * <p>HA lifecycle:
     * <ul>
     *   <li>Non-HA mode (no {@link ILeaderElector} injected): derives a monotonic
     *       long fencing epoch (leaderEpoch component 0, recoveryGen 0), registers,
     *       marks active immediately.</li>
     *   <li>HA mode ({@link ILeaderElector} injected): registers an
     *       {@link ILeaderElectionListener} and returns immediately in STANDBY
     *       (active=false). Activation happens only on the
     *       {@link ILeaderElectionListener#becomeLeader(LeaderEpoch)} callback,
     *       which derives the fencing epoch from the granted {@link LeaderEpoch}.
     *       <strong>{@code whenElectionCompleted()} must NOT be used as an
     *       activation trigger</strong> — it only signals "a result exists",
     *       which may be that another node won (otherwise a follower would
     *       erroneously enter ACTIVE).</li>
     * </ul>
     */
    public void start() {
        if (!initialized.compareAndSet(false, true)) {
            LOG.warn("JobCoordinator {} already started", coordinatorId);
            return;
        }

        if (leaderElector == null) {
            // Non-HA / embedded-local mode: single-instance behaviour.
            // Non-HA fencing epoch uses leaderEpoch component
            // 0, so epoch == recoveryGen. recoveryGen is seeded to 1 on start so the
            // initial epoch (1) is non-zero and distinct from the 0 "uninitialized"
            // sentinel checked in {@link #collectAck}. globalRecovery() increments
            // recoveryGen so fencing stays effective (stale prior-recovery tasks
            // rejected).
            if (fencingEpoch.get() == 0L) {
                recoveryGen.set(1L);
                fencingEpoch.set(deriveHaFencingEpoch(0L, recoveryGen.get()));
            } else {
                // fencingEpoch was pre-set via setFencingEpoch (e.g. by JobCoordinatorMain
                // or the in-process executors RpcDistributedExecutor /
                // EmbeddedDistributedExecutor, which derive deriveHaFencingEpoch(0,1)=1
                // before start()). The seed branch above is skipped, so recoveryGen must
                // be synced here from the pre-set epoch: in non-HA mode the leaderEpoch
                // component is 0, so the epoch's low-order component equals recoveryGen.
                // Without this sync the first globalRecovery() would increment 0->1 and
                // derive deriveHaFencingEpoch(0,1)=1 — the SAME epoch as the pre-set value
                // — so the fencing epoch would NOT rotate on recovery (fencing invariant
                // violation: each recovery must produce a strictly greater epoch).
                recoveryGen.set(fencingEpoch.get());
            }
            long epoch = fencingEpoch.get();
            clusterRegistry.registerCoordinator(jobId, coordinatorId, epoch);

            // Cross-JVM fencing sync: every other
            // activation path pushes the fencing epoch to the TaskManagers before any
            // deployTask is issued — rotateFencingEpochCoreLocked does it on
            // recovery/HA-activation, and the in-process executors
            // (RpcDistributedExecutor / EmbeddedDistributedExecutor) pre-sync each TM
            // via tm.updateFencingToken before coordinator.start(). Mirror the
            // recovery-path push here so TMs are at `epoch` before the caller issues
            // assignTasks(). Safe for the in-process executors: they pre-sync TMs to
            // the same epoch, so updateFencingToken is a no-op there (old==new); and
            // they call start() AFTER setFencingEpoch, so `epoch` equals the
            // executor's value.
            for (IStreamTaskRpcService rpc : taskRpcServices.values()) {
                rpc.updateFencingToken(epoch);
            }

            startFailureDetector();
            running = true;
            active = true;
            jobStatus = JobStatus.RUNNING;
            health.onStart();
            registerNodesActiveGauge();
            jobEventBus.fire(StreamJobEvent.simple(jobId,
                    io.nop.stream.runtime.event.StreamJobEvent.EventType.JOB_STARTED, null));
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
        health.onStart();
        registerNodesActiveGauge();
        jobEventBus.fire(StreamJobEvent.simple(jobId,
                io.nop.stream.runtime.event.StreamJobEvent.EventType.JOB_STARTED, "ha-standby"));
        LOG.info("JobCoordinator {} started in HA STANDBY mode for job {} (hostId={})",
                coordinatorId, jobId, leaderElector.getHostId());

        // Integration contract: some platform elector
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

        // CC-03 (R5 audit): a stopping coordinator must tell every TaskManager
        // hosting an assigned subtask to cancel that task. Pre-fix only the
        // coordination side was torn down: remote tasks kept running forever
        // (orphaned source fetches, sink commits, permanently occupied slot
        // permits, heartbeats rejected against the dead coordinator) until a
        // later epoch rotation happened to reclaim them. Best-effort: per-task
        // try/catch, failures logged, never blocking or failing the
        // coordinator-side shutdown.
        cancelAllAssignedTasks("coordinator stop");

        // Stop listening to the elector so callbacks cannot fire into a
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

        // Stop the periodic checkpoint driver before the checkpoint
        // coordinator shuts down (no triggers against a shut-down coordinator).
        stopPeriodicCheckpoints();

        checkpointCoordinator.shutdown();

        // Release this job's engine meters so a coordination
        // process running many short-lived jobs does not accumulate meter sets.
        io.nop.stream.runtime.metrics.EngineMetrics.releaseJob(jobId);

        LOG.info("JobCoordinator {} stopped for job {}", coordinatorId, jobId);
    }

    /**
     * CC-03: fans a {@code cancelTask} RPC out to the node of every currently
     * assigned subtask (best-effort, per-task containment). The RPC carries the
     * coordinator's current fencing epoch, so a stale coordinator's cancel is
     * rejected at the TaskManager boundary exactly as in the checkpoint-abort
     * path. RPC failures are logged and never propagate: the coordinator-side
     * shutdown must complete regardless, and the epoch-rotation fencing of the
     * next leader/recovery reclaims any task this fan-out could not cancel.
     */
    private void cancelAllAssignedTasks(String reason) {
        long epoch = fencingEpoch.get();
        for (Map.Entry<String, List<TaskAssignment>> entry : taskAssignmentMap.entrySet()) {
            for (TaskAssignment ta : entry.getValue()) {
                IStreamTaskRpcService rpc = taskRpcServices.get(ta.getNodeId());
                if (rpc == null) {
                    LOG.warn("No task RPC service for node {} during cancelTask fan-out ({}) for "
                                    + "{}/{}/{} — relying on epoch-rotation fencing / TM restart "
                                    + "for reclamation",
                            ta.getNodeId(), reason, ta.getJobId(), ta.getVertexId(),
                            ta.getSubtaskIndex());
                    continue;
                }
                try {
                    rpc.cancelTask(ta.getJobId(), ta.getVertexId(), ta.getSubtaskIndex(), epoch);
                } catch (Exception e) {
                    LOG.warn("cancelTask RPC failed for {}/{}/{} (node {}) during {} for job {}",
                            ta.getJobId(), ta.getVertexId(), ta.getSubtaskIndex(), ta.getNodeId(),
                            reason, jobId, e);
                }
            }
        }
    }

    /**
     * Marks the job as FAILED and shuts down coordinator-side machinery.
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
        if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.CANCELED) {
            // Idempotence guard (documented no-op, observable via WARN): a
            // job already in a terminal state never re-fails. This also keeps
            // the health machine free of illegal terminal->FAILED
            // transitions from late failure reports racing a terminate.
            LOG.warn("failJob ignored for job {}: already terminal ({})", jobId, jobStatus);
            return;
        }
        this.jobFailureCause = cause;
        this.jobStatus = JobStatus.FAILED;
        this.active = false;
        LOG.error("Job {} FAILED (cause={})", jobId, cause == null ? "unknown" : cause.toString(), cause);
        health.onFailJob(cause == null ? "unknown" : cause.toString());
        // Job failure event with cause (alert routing input).
        jobEventBus.fire(StreamJobEvent.simple(jobId,
                io.nop.stream.runtime.event.StreamJobEvent.EventType.JOB_FAILED,
                cause == null ? "unknown" : cause.toString()));
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
     * assignment over {@link ClusterRegistry#getActiveNodes()} (the LOCAL path).
     *
     * <p>For each assignment:
     * <ol>
     *   <li>Records the assignment in the ClusterRegistry (runtime consistency view)</li>
     *   <li>Sends a {@link TaskAssignment} via the task RPC service
     *       ({@link IStreamTaskRpcService#receiveAssignment}) — <em>in-process mode</em>;
     *       OR sends a {@link TaskDeploymentDescriptor} via
     *       {@link IStreamTaskRpcService#deployTask} — <em>remote-deploy mode</em>.
     *       In remote-deploy mode {@code receiveAssignment} is NOT called — the descriptor
     *       is self-contained.</li>
     * </ol>
     */
    public void assignTasks() {
        if (!running) {
            LOG.warn("JobCoordinator not running, cannot assign tasks");
            return;
        }
        // A standby coordinator must never issue assignments.
        if (!active) {
            LOG.warn("JobCoordinator in STANDBY (not leader), cannot assign tasks for job {}", jobId);
            return;
        }
        // Once the job is FAILED, no new assignments are permitted
        if (jobStatus == JobStatus.FAILED) {
            LOG.warn("Job {} is FAILED (cause={}); rejecting assignTasks",
                    jobId, jobFailureCause == null ? "unknown" : jobFailureCause.toString());
            return;
        }

        // Remote-deploy mode requires the JobGraph to build
        // per-subtask deployment descriptors. Fail-fast (no silent skip) when
        // the mode is active but the JobGraph was not injected.
        if (remoteDeployMode && jobGraph == null) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "remoteDeployMode is active but no JobGraph was injected on JobCoordinator "
                            + coordinatorId + ". Call setJobGraph(...) before assignTasks().");
        }

        // The in-memory materialization (cluster registry update +
        // taskAssignmentMap put) is performed under the recovery lock so it cannot
        // interleave with a concurrent recovery driver. The RPC fan-out is executed
        // AFTER the lock is released (no blocking IO under the lock).
        List<AssignmentPlanner.AssignmentDispatch> dispatches;
        recoveryLock.lock();
        try {
            dispatches = assignmentPlanner.prepareAssignmentsLocked(
                    remoteDeployMode, jobGraph, pipelineSpec, checkpointStoragePath);
            markEverAssigned(dispatches);
        } finally {
            recoveryLock.unlock();
        }
        recordDeploysIssued(dispatches);
        assignmentPlanner.executeAssignmentFanOut(dispatches, this::markDeployTransportFailed);
    }

    /**
     * Returns all current task assignments.
     */
    public Map<String, List<TaskAssignment>> getTaskAssignments() {
        return Collections.unmodifiableMap(taskAssignmentMap);
    }

    // ==================== Checkpoint ====================

    /**
     * Triggers a checkpoint by sending a barrier signal to the source tasks
     * via the control topic.
     *
     * <p>The barrier RPC is fanned out to
     * <strong>every node that currently hosts an assigned subtask</strong>, not just
     * the nodes hosting source vertices. Reason: the receiving
     * {@code TaskManager.triggerCheckpoint} registers the in-flight epoch on each
     * running task's {@code CheckpointBarrierTracker}; only source-operator tasks
     * additionally inject the barrier (the tracker's head-operator check is
     * source-aware). A task on a node that never receives the trigger RPC has no
     * in-flight epoch registered, so its operators' barrier-driven snapshots are
     * dropped by the tracker ("no matching in-flight epoch") and the checkpoint can
     * never complete. Registering the epoch on
     * all tasks is idempotent for non-source tasks (no barrier injection happens
     * there; the barrier still arrives via the data plane from upstream).
     *
     * @return the triggered PendingCheckpoint, or null if trigger failed
     */
    public PendingCheckpoint triggerCheckpoint() {
        if (!running) {
            LOG.warn("JobCoordinator not running, cannot trigger checkpoint");
            return null;
        }
        // A standby coordinator must never trigger checkpoints.
        if (!active) {
            LOG.warn("JobCoordinator in STANDBY (not leader), cannot trigger checkpoint for job {}", jobId);
            return null;
        }
        // A recovery is pending or
        // in-flight (epoch rotation through assignment fan-out). A trigger in
        // that window races the redeployment — its RPC row may precede the new
        // deployTask rows on the task topics, so the in-flight epoch registers on
        // pre-replacement attempts and the new attempts' barrier ACKs get dropped
        // ("no matching in-flight epoch"), dooming the checkpoint. The next
        // periodic tick after the fan-out triggers fresh on the stable task set.
        if (recoveryPending.get()) {
            LOG.debug("Suppressing checkpoint trigger for job {}: recovery pending/in-flight", jobId);
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

        Set<String> barrierNodeIds = assignmentPlanner.computeAssignedNodeIds();

        if (!taskRpcServices.isEmpty()) {
            for (String nodeId : barrierNodeIds) {
                IStreamTaskRpcService rpc = taskRpcServices.get(nodeId);
                if (rpc != null) {
                    try {
                        rpc.triggerCheckpoint(barrier, epoch);
                    } catch (Exception e) {
                        LOG.error("Failed to send checkpoint signal to node {}", nodeId, e);
                    }
                } else {
                    // No silent skip: a source node without an RPC service dooms this
                    // checkpoint to timeout-abort; surface why at WARN so operators
                    // can see it without DEBUG logging.
                    LOG.warn("No RPC service registered for node {} — checkpoint {} barrier "
                            + "cannot be delivered to this source (epoch {})", nodeId, barrier.getId(), epoch);
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
        // A standby coordinator must never accept checkpoint ACKs.
        if (!active) {
            LOG.warn("Rejecting checkpoint ACK from {}: coordinator in STANDBY (not leader) for job {}",
                    ack.getTaskLocation(), jobId);
            return false;
        }

        // fencingEpoch == 0 means coordinator not initialized (the start()
        // path sets a non-zero epoch; 0 is the pre-init sentinel). Reject all ACKs
        // in that state.
        long epoch = fencingEpoch.get();
        if (epoch == 0L) {
            LOG.warn("Rejecting checkpoint ACK: coordinator fencing epoch not initialized");
            return false;
        }

        // Fencing epoch verification (single long comparison)
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
     * Per-task terminal-state report handler.
     *
     * <p>Updates per-subtask liveness on every report (a COMPLETED
     * report <b>removes</b> the liveness entry so the completed task is
     * excluded from stall detection; a FAILED report records the report arrival
     * time as a fresh aliveness baseline). On a FAILED report from a task whose
     * host node is still alive, triggers global recovery (so the coordinator no
     * longer depends solely on node-lease detection to react to a single-task
     * failure). Rejects reports with stale fencing tokens.
     */
    @Override
    public void reportTaskStatus(TaskStatusReport report) {
        if (!running) {
            // Explicit, observable rejection — not a silent debug-log+return.
            LOG.warn("Rejecting task status report: coordinator not running for job {}: {}/{}/{} state={}",
                    jobId, report.getVertexId(), report.getSubtaskIndex(),
                    report.getAttemptNumber(), report.getTerminalState());
            return;
        }
        // A standby coordinator must never process task status (it does
        // not own recovery decisions for this job). Explicit rejection, not silent.
        if (!active) {
            LOG.warn("Rejecting task status report: coordinator in STANDBY (not leader) for job {}: {}/{}/{} state={}",
                    jobId, report.getVertexId(), report.getSubtaskIndex(),
                    report.getAttemptNumber(), report.getTerminalState());
            return;
        }
        // Fencing epoch verification (single long comparison)
        long epoch = fencingEpoch.get();
        if (epoch == 0L || epoch != report.getFencingEpoch()) {
            LOG.warn("Rejecting task status report with stale fencing epoch: {}/{}/{} state={} (expected epoch={})",
                    report.getVertexId(), report.getSubtaskIndex(), report.getAttemptNumber(),
                    report.getTerminalState(), epoch);
            return;
        }

        String livenessKey = report.getVertexId() + "/" + report.getSubtaskIndex();
        long now = CoreMetrics.currentTimeMillis();
        TaskStatusReport.TerminalState state = report.getTerminalState();
        if (state == TaskStatusReport.TerminalState.COMPLETED) {
            // A completed task must be excluded from stall
            // detection. Remove its liveness entry so detectFailures'
            // benefit-of-the-doubt branch (no record) never flags it as
            // stalled. The entry stays removed because heartbeats skip
            // finished tasks (a success-finished task RETAINS its
            // TaskManager registry entry for tail commits, but the heartbeat
            // loop filters on isFinished() and never re-reports its frozen
            // activity clock).
            //
            // REG-02 (plan366-regression audit): this removal races in-flight
            // heartbeats — merge() INSERTS on an absent key, so a heartbeat
            // batch built before the task finished but delivered after this
            // removal used to resurrect the frozen timestamp, and after
            // taskTimeoutMs the detector flagged the healthy finished task
            // TASK_STALL (a spurious recovery that could cut the 2PC tail
            // commit window). The completed-set tombstone, checked inside the
            // same monitor by reportNodeTaskLiveness, closes the window: either
            // the heartbeat's critical section ran first (it merged into the
            // still-present entry, which this remove then deletes) or it
            // observes the tombstone and skips — the frozen value can never be
            // re-inserted.
            synchronized (livenessLock) {
                subtaskLiveness.remove(livenessKey);
                completedSubtaskKeys.add(livenessKey);
                // R5-CC-07: the task is terminal — its deploy grace window is
                // over and must never be evaluated again for this generation.
                deployIssuedAtMs.remove(livenessKey);
            }
            // C3 (plan 369): contract the completed task out of the checkpoint
            // participant set. A completed task can never ACK a later epoch;
            // leaving it registered kept every subsequent checkpoint waiting for
            // its full checkpointTimeout and aborting. The coordinator inherits
            // the task's state from the latest completed checkpoint at trigger
            // time, so subsequent epochs still carry the full task-state set.
            // The TaskLocation uses the same (jobId, "pipeline-0") family the
            // AssignmentPlanner registered the ACK set with.
            try {
                checkpointCoordinator.markTaskCompleted(new TaskLocation(
                        jobId, "pipeline-0", report.getVertexId(), report.getSubtaskIndex()));
            } catch (Exception e) {
                LOG.warn("Failed to contract completed task {}/{} out of the checkpoint ACK set "
                        + "for job {} — the task will remain a checkpoint participant",
                        report.getVertexId(), report.getSubtaskIndex(), jobId, e);
            }
        } else {
            // Any other terminal report (FAILED) is an
            // aliveness event in its own right — record the report arrival
            // time (not the possibly-stale data-progress timestamp) so the
            // heartbeat-gap recovery mechanism (task left runningTasks →
            // no more heartbeats → liveness ages → stall detection) starts
            // from a fresh baseline.
            subtaskLiveness.put(livenessKey, now);
            // R5-CC-07: a terminal report proves the deploy reached a live task
            // slot — its grace window is over.
            deployIssuedAtMs.remove(livenessKey);
        }

        LOG.info("Task status report: {}/{}/{} attempt={} state={} cause={}",
                report.getVertexId(), report.getSubtaskIndex(), report.getAttemptNumber(),
                report.getAttemptNumber(),
                report.getTerminalState(),
                report.getErrorCause());

        if (report.getTerminalState() == TaskStatusReport.TerminalState.FAILED) {
            if (autoRecoverOnFailedReport) {
                // Per-task failure (node still alive) — trigger recovery rather
                // than waiting for a node-lease timeout. The recovery increments the
                // attempt counter; if the global cap is hit, failJob runs.
                LOG.warn("Task {}/{}/{} reported FAILED (cause={}); triggering global recovery",
                        report.getVertexId(), report.getSubtaskIndex(), report.getAttemptNumber(),
                        report.getErrorCause());
                try {
                    // Route through requestRecovery() so a concurrent
                    // failure-detector cycle for the same event is deduped via the
                    // recoveryPending CAS rather than racing into globalRecovery.
                    requestRecovery();
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
     * Per-node liveness piggybacked on the heartbeat. Updates the
     * per-subtask liveness map; the {@link #detectFailures()} loop checks for
     * stalls against {@link #taskTimeoutMs}. The reported values carry the
     * task-aliveness semantics described on {@link #subtaskLiveness}
     * (MIDDLE/SINK loop activity, SOURCE/SELF_CONTAINED TM wall clock).
     */
    @Override
    public void reportNodeTaskLiveness(String nodeId, List<TaskProgress> progress) {
        if (progress == null || progress.isEmpty()) {
            return;
        }
        if (!running) {
            // Observable rejection, not a silent swallow.
            LOG.warn("Rejecting node task liveness from node {}: coordinator not running for job {}",
                    nodeId, jobId);
            return;
        }
        // A standby coordinator does not own liveness-driven recovery.
        if (!active) {
            LOG.warn("Rejecting node task liveness from node {}: coordinator in STANDBY (not leader) for job {}",
                    nodeId, jobId);
            return;
        }
        for (TaskProgress p : progress) {
            String livenessKey = p.getVertexId() + "/" + p.getSubtaskIndex();
            // CC-05 (R5 audit): reject heartbeats from a SUPERSEDED attempt. When a
            // recovery's fencing-token push failed on some node (or raced its
            // heartbeat), the zombie task of the old generation keeps heartbeating
            // under the same liveness key; its fresh timestamps would merge over
            // the new generation's values and permanently mask a genuinely hung
            // new task (stall detection silently bypassed for that subtask). The
            // current attempt number is read from the materialized assignment
            // working set — kept in sync with the ClusterRegistry by
            // prepareAssignmentsLocked — so no registry round-trip per heartbeat
            // is needed. When no current assignment exists for the key the
            // heartbeat is unclassifiable and harmless (detectFailures only reads
            // liveness keys that back a current assignment), so it is accepted.
            Integer currentAttempt = currentAttemptOf(p.getVertexId(), p.getSubtaskIndex());
            if (currentAttempt != null && currentAttempt.intValue() != p.getAttemptNumber()) {
                LOG.warn("Rejecting stale-attempt heartbeat from node {} for {}/{}: attempt={} "
                                + "(current attempt={}) — zombie task of a superseded generation",
                        nodeId, p.getVertexId(), p.getSubtaskIndex(),
                        p.getAttemptNumber(), currentAttempt.intValue());
                continue;
            }
            synchronized (livenessLock) {
                // REG-02: a key tombstoned by a COMPLETED report must not be
                // re-inserted — an in-flight heartbeat batch (built before the
                // task finished) would otherwise resurrect its frozen timestamp.
                if (completedSubtaskKeys.contains(livenessKey)) {
                    continue;
                }
                // Monotonic max via atomic merge: a getOrDefault→compare→put sequence
                // is not atomic and an interleaved delivery could overwrite a newer
                // timestamp with an older one, sending liveness backwards and causing
                // spurious stall detection.
                subtaskLiveness.merge(livenessKey, p.getLastProgressTime(), Math::max);
                // R5-CC-07: the first liveness report proves the deploy reached a
                // live task slot — close its grace window (beyond this point the
                // task is covered by the regular liveness-stall detection).
                deployIssuedAtMs.remove(livenessKey);
            }
        }
    }

    /**
     * Returns the attempt number of the current assignment for the given
     * subtask, or {@code null} when the subtask has no materialized assignment.
     */
    private Integer currentAttemptOf(String vertexId, int subtaskIndex) {
        List<TaskAssignment> assignments = taskAssignmentMap.get(vertexId);
        if (assignments == null) {
            return null;
        }
        for (TaskAssignment ta : assignments) {
            if (ta.getSubtaskIndex() == subtaskIndex) {
                return ta.getAttemptNumber();
            }
        }
        return null;
    }

    /**
     * Returns the current CheckpointCoordinator for inspection.
     */
    public CheckpointCoordinator getCheckpointCoordinator() {
        return checkpointCoordinator;
    }

    /**
     * Starts the launch-path periodic
     * checkpoint driver. Each tick calls {@link #triggerCheckpoint()}, which both
     * triggers a {@link PendingCheckpoint} on the {@link CheckpointCoordinator}
     * AND fans the barrier RPC out to all assigned nodes — the coordinator itself
     * has no RPC view and owns no trigger loop, so this JobCoordinator-level
     * driver is the only periodic-checkpoint mechanism for the RPC-distributed
     * form.
     *
     * <p>Idempotent: a second call while started logs a warning and returns
     * (observable, not silent). Stopped by {@link #stopPeriodicCheckpoints()}
     * or {@link #stop()}.
     *
     * @param intervalMs period between checkpoint triggers; must be positive
     */
    public void startPeriodicCheckpoints(long intervalMs) {
        if (intervalMs <= 0) {
            throw new StreamException(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG)
                    .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
                            "checkpoint interval must be positive (got " + intervalMs + "ms)");
        }
        synchronized (this) {
            if (periodicCheckpointsStarted) {
                LOG.warn("Periodic checkpoints already started for job {}; ignoring duplicate start", jobId);
                return;
            }
            periodicCheckpointScheduler = Executors.newSingleThreadScheduledExecutor(
                    NopStreamThreadFactory.named("jc-periodic-checkpoint-" + jobId));
            periodicCheckpointScheduler.scheduleWithFixedDelay(() -> {
                try {
                    PendingCheckpoint pending = triggerCheckpoint();
                    if (pending == null) {
                        LOG.debug("Periodic checkpoint trigger skipped/rejected for job {}", jobId);
                    }
                } catch (Exception e) {
                    // Observable failure (not swallowed): the next tick retries.
                    // Feed the coordinator's consecutive-failure counter so
                    // sustained trigger failures keep surfacing in metrics —
                    // but only for real exceptions: a null return is a
                    // legitimate skip/reject (standby, throttled, recovery
                    // pending) and must NOT count as a failure.
                    LOG.warn("Periodic checkpoint trigger failed for job {}", jobId, e);
                    checkpointCoordinator.incrementTriggerFailures();
                }
            }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
            periodicCheckpointsStarted = true;
            LOG.info("Periodic checkpoints started for job {} (interval={}ms)", jobId, intervalMs);
        }
    }

    /**
     * Stops the periodic checkpoint driver started by
     * {@link #startPeriodicCheckpoints(long)}. Safe no-op when not started.
     */
    public synchronized void stopPeriodicCheckpoints() {
        if (!periodicCheckpointsStarted || periodicCheckpointScheduler == null) {
            return;
        }
        periodicCheckpointScheduler.shutdownNow();
        try {
            if (!periodicCheckpointScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("Periodic checkpoint scheduler did not terminate in 5s for job {}", jobId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        periodicCheckpointScheduler = null;
        periodicCheckpointsStarted = false;
        LOG.info("Periodic checkpoints stopped for job {}", jobId);
    }

    /**
     * Registers the distributed
     * checkpoint-completion forwarder on the {@link CheckpointCoordinator}. When
     * a checkpoint becomes durable, {@code CheckpointParticipant.finishCommit}
     * fires on the coordinator — but in remote-deploy mode the 2PC sink operators
     * live inside the TaskManager JVMs, so the commit notification must cross the
     * RPC boundary. This bridge participant fans a
     * {@code notifyCheckpointComplete(checkpointId, fencingEpoch)} RPC out to
     * every node that currently hosts an assigned subtask; each TaskManager then
     * drives its local sink participants' {@code finishCommit}.
     *
     * <p>Failure semantics: an RPC failure to one node propagates out of
     * {@code finishCommit} so the CheckpointCoordinator records this participant
     * in its failed-commit set and retries on the next completion
     * ({@code retryFailedCommits}); missed epochs are additionally covered by the
     * sinks' subsuming commit ({@code finishCommit(M)} commits every
     * {@code eid <= M}) and by the ledger/manifest idempotency guards, so
     * exactly-once holds under lost notifications.
     */
    public void registerDistributedCommitForwarder() {
        checkpointCoordinator.addParticipant(new CheckpointParticipant() {
            @Override
            public io.nop.stream.core.checkpoint.TaskStateSnapshot saveState(long epochId) {
                // No coordinator-side state: all task state arrives via checkpoint
                // ACKs from the TaskManagers. Return an empty snapshot.
                return new io.nop.stream.core.checkpoint.TaskStateSnapshot(
                        new TaskLocation(jobId, "pipeline-0", "coordinator-commit-forwarder", 0), epochId);
            }

            @Override
            public void prepareCommit(long epochId) {
                // Commit preparation happens on the TaskManager-side sink operators;
                // the coordinator has no local transaction to prepare.
            }

            @Override
            public void finishCommit(long epochId, boolean success) throws Exception {
                if (!success) {
                    // The checkpoint was aborted/failed: the TM-side sinks keep their
                    // prepared transactions for subsuming (same semantics as the
                    // LOCAL path's notifyParticipantsFinishCommit(false)). Do not
                    // remote-abort — non-durable transactions are aborted by the
                    // sink restore path on the next recovery redeploy.
                    LOG.debug("Distributed commit forwarder: epoch {} not successful for job {}; "
                            + "keeping TM-side prepared transactions for subsuming", epochId, jobId);
                    return;
                }
                long epoch = fencingEpoch.get();
                java.util.List<String> failedNodes = null;
                for (String nodeId : assignmentPlanner.computeAssignedNodeIds()) {
                    IStreamTaskRpcService rpc = taskRpcServices.get(nodeId);
                    if (rpc == null) {
                        LOG.warn("No RPC service for node {} during commit forward of epoch {} — "
                                + "subsuming commit / sink restore will recover it", nodeId, epochId);
                        continue;
                    }
                    try {
                        rpc.notifyCheckpointComplete(epochId, epoch);
                    } catch (Exception e) {
                        // Collect and rethrow after the fan-out so one unreachable node
                        // does not skip the remaining nodes (mirrors the per-dispatch
                        // containment of executeAssignmentFanOut).
                        if (failedNodes == null) {
                            failedNodes = new java.util.ArrayList<>();
                        }
                        failedNodes.add(nodeId + ": " + e);
                        LOG.error("notifyCheckpointComplete RPC failed for node {} (epoch {})",
                                nodeId, epochId, e);
                    }
                }
                if (failedNodes != null) {
                    throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                            "Distributed commit forward failed for epoch " + epochId
                                    + " on nodes: " + failedNodes
                                    + ". The CheckpointCoordinator will retry this commit "
                                    + "(retryFailedCommits); sink idempotency guards make the "
                                    + "retry safe.");
                }
            }

            @Override
            public void restoreFromEpoch(long epochId, io.nop.stream.core.checkpoint.TaskStateSnapshot state) {
                // Nothing to restore coordinator-side: TaskManager-side restore is
                // driven by the deployment descriptor's checkpointRestorePath.
            }
        });
        LOG.info("Distributed commit forwarder registered for job {}", jobId);
    }

    // ==================== Failure Detection & Recovery ====================

    /**
     * Checks ClusterRegistry node leases AND per-task liveness. If any assigned
     * node has expired, OR if any task's recorded aliveness timestamp is
     * older than
     * {@link #taskTimeoutMs}, triggers global recovery.
     *
     * <p>The per-task liveness values are the task-aliveness
     * signal (decoupled from data progress — see {@link #subtaskLiveness}).
     * Healthy idle tasks keep fresh values (MIDDLE/SINK loop activity ticks
     * every idle cycle; SOURCE/SELF_CONTAINED report the TM wall clock every
     * heartbeat), completed tasks have their entry removed by
     * {@link #reportTaskStatus}, so only a genuinely hung task (loop stopped
     * ticking) or a task whose heartbeats have stopped (slot freed without a
     * terminal report) falls behind the cutoff.
     *
     * <p>R5-CC-07 (plan 369 Phase 4): an assignment with NO liveness record is
     * given the benefit of the doubt only within {@link #deployGracePeriodMs}
     * of its deploy-issue timestamp ({@code deployIssuedAtMs}); beyond the
     * grace period it is a deployment failure (typically a deployTask whose
     * one-way transport RPC was lost) and contributes to the recovery trigger.
     */
    public void detectFailures() {
        if (!running) {
            return;
        }
        // A standby coordinator does not lead recovery. The detector
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

            // Per-task aliveness check. A task whose recorded
            // aliveness timestamp is older than taskTimeoutMs is considered
            // stalled (node alive but task hung / heartbeats stopped). The
            // recorded values are the task-aliveness signal (see
            // subtaskLiveness), so idle/completed tasks never fall behind.
            boolean taskStallDetected = false;
            boolean deployFailureDetected = false;
            long now = clock.getAsLong();
            long cutoff = now - taskTimeoutMs;
            for (List<TaskAssignment> assignments : taskAssignmentMap.values()) {
                for (TaskAssignment assignment : assignments) {
                    String livenessKey = assignment.getVertexId() + "/" + assignment.getSubtaskIndex();
                    Long lastProgress = subtaskLiveness.get(livenessKey);
                    if (lastProgress != null && lastProgress < cutoff) {
                        LOG.warn("Task {}/{}/{} stalled: lastProgressTime={} (cutoff={})",
                                assignment.getVertexId(), assignment.getSubtaskIndex(),
                                lastProgress, cutoff);
                        taskStallDetected = true;
                    } else if (lastProgress == null && !completedSubtaskKeys.contains(livenessKey)) {
                        // R5-CC-07 (plan 369 Phase 4): a task with no liveness
                        // record used to get a PERMANENT benefit of the doubt —
                        // a deploy whose one-way transport RPC was lost never
                        // produces a liveness record or a FAILED report, so the
                        // assignment was exempt from detection forever (silent
                        // half-dead topology). Within the grace period the
                        // benefit of the doubt still applies (just-assigned,
                        // first heartbeat in flight); beyond it, the assignment
                        // is a deployment failure and re-triggers recovery.
                        Long issuedAt = deployIssuedAtMs.get(livenessKey);
                        if (issuedAt == null) {
                            // No deploy marker for the current generation
                            // (assignment materialized before this coordinator
                            // learned the key) — keep the legacy benefit of the
                            // doubt; node-lease detection covers a dead node.
                            continue;
                        }
                        long sinceDeploy = now - issuedAt;
                        if (sinceDeploy > deployGracePeriodMs) {
                            LOG.warn("Task {}/{}/{} has NO liveness record {}ms after its deploy was issued "
                                            + "(grace={}ms, transportFailure={}) — treating as deployment failure",
                                    assignment.getVertexId(), assignment.getSubtaskIndex(),
                                    sinceDeploy, deployGracePeriodMs,
                                    deployTransportFailures.contains(livenessKey));
                            deployFailureDetected = true;
                        }
                    }
                }
            }

            // CC-01 bottom line (R5 audit): a recovery that aborts after the
            // working set was cleared (storage rebuild failure on leadership
            // grant, assignment-materialization failure) leaves the job "active
            // but zero assignments" — both detection loops above iterate an
            // empty map and would never fire again (permanent wedge). Once this
            // coordinator HAS materialized assignments, an empty working set is
            // itself the failure: re-trigger recovery so the assignments are
            // re-materialized, or — if the failure persists — the restart budget
            // fails the job loudly instead of hanging silently. A fresh
            // coordinator that has not assigned yet is legitimately empty and
            // must not self-recover (everAssigned gate).
            if (everAssigned && taskAssignmentMap.isEmpty()) {
                LOG.error("Job {} is active but its assignment working set is empty after a "
                        + "previous assignment — the last recovery must have aborted mid-flight; "
                        + "re-triggering recovery to re-materialize assignments", jobId);
                requestRecovery(RecoveryCause.OTHER);
                return;
            }

            if (nodeFailureDetected || taskStallDetected || deployFailureDetected) {
                LOG.warn("Failures detected (nodeLoss={}, taskStall={}, deployGraceExpired={}), "
                                + "triggering global recovery for job {}",
                        nodeFailureDetected, taskStallDetected, deployFailureDetected, jobId);
                // Route through requestRecovery() so concurrent triggers
                // from the FAILED-report RPC path are deduped via the recoveryPending CAS.
                //
                // The trigger CAUSE selects the recovery budget —
                // node-lease expiry is a REAL failure (draws from maxRestarts);
                // a pure liveness stall (node alive) draws from the separate
                // stall budget with cooldown so stall storms cannot starve
                // real-failure recovery. Both present → classified as real
                // failure (node loss dominates: the stall is a consequence).
                // R5-CC-07: a grace-expired deploy draws from the stall budget
                // like a liveness stall (the plan's "正常 stall/恢复判定") — a
                // persistently broken deploy path re-fires after each cooldown
                // until the stall cap fails the job loudly instead of hanging.
                requestRecovery(nodeFailureDetected ? RecoveryCause.NODE_FAILURE : RecoveryCause.TASK_STALL);
            }
        } catch (Exception e) {
            LOG.error("Error during failure detection for job {}", jobId, e);
        }
    }

    /**
     * Trigger entry point for global recovery with concurrent-call
     * deduplication. This is the method production trigger sites (failure detector,
     * FAILED-report RPC handler) MUST call instead of {@link #globalRecovery()}.
     *
     * <p>Two concurrent sources can fire for the <em>same</em> failure event:
     * <ul>
     *   <li>the single-threaded {@code failureDetector} via {@link #detectFailures()}
     *       (node-lease expiration or per-task liveness stall);</li>
     *   <li>the RPC server thread pool via {@link #reportTaskStatus} on a FAILED
     *       report with {@code autoRecoverOnFailedReport=true}.</li>
     * </ul>
     *
 * <p>Dedup is implemented as a single CAS on {@link #recoveryPending}: exactly
 * one caller transitions {@code false → true} and proceeds into
 * {@link #globalRecovery()}; all redundant callers (whether truly overlapping
 * or arriving while the in-flight recovery is still running) observe the CAS
 * fail and short-circuit with an observable WARN (No-Silent-No-Op). The flag is
 * cleared by {@code globalRecovery()}'s OUTER finally — after the locked
 * section, the assignment fan-out, and the health/event callbacks — so the CAS
 * window stays closed for the recovery's entire suppression window (including
 * the fan-out; see {@code globalRecovery}). A trigger firing AFTER the clear
 * re-arms the flag and runs a fresh recovery; globalRecovery is
 * global/idempotent so a redundant post-completion trigger is wasteful but not
 * corrupting. Because the clear sits in a finally, a lock-internal exception
 * (fencing DB write, assignment planning) also un-sticks the flag.
 */
    public void requestRecovery() {
        requestRecovery(RecoveryCause.OTHER);
    }

    /**
     * Cause-carrying trigger entry point. The cause selects
     * which recovery budget a recovery draws from:
     * <ul>
     *   <li>{@code NODE_FAILURE} / {@code OTHER} (incl. FAILED reports and the
     *       no-cause entry) → the real-failure budget
     *       ({@link #restartCount}/{@link #maxRestarts});</li>
     *   <li>{@code TASK_STALL} → the separate stall budget
     *       ({@link #stallRestartCount}/{@link #maxStallRestarts}) with a
     *       cooldown window ({@link #stallRecoveryCooldownMs}) — a request
     *       inside the cooldown is skipped with an observable WARN (the
     *       periodic detector re-fires after the cooldown).</li>
     * </ul>
     * Concurrent-trigger dedup (CAS on {@link #recoveryPending}) applies to
     * every cause identically.
     */
    public void requestRecovery(RecoveryCause cause) {
        if (cause == RecoveryCause.TASK_STALL) {
            long now = CoreMetrics.currentTimeMillis();
            long last = lastStallRecoveryAt;
            if (last > 0 && now - last < stallRecoveryCooldownMs) {
                LOG.warn("Skipping stall-triggered recovery request for job {}: within cooldown window "
                        + "({}ms < {}ms since the last stall recovery); the failure detector re-fires "
                        + "after the cooldown", jobId, now - last, stallRecoveryCooldownMs);
                return;
            }
        }
        if (!recoveryPending.compareAndSet(false, true)) {
            LOG.warn("Short-circuiting redundant recovery request for job {}: another recovery "
                    + "is pending or in-flight (recoveryPending=true); not re-entering globalRecovery", jobId);
            return;
        }
        if (cause == RecoveryCause.TASK_STALL) {
            lastStallRecoveryAt = CoreMetrics.currentTimeMillis();
        }
        globalRecovery(cause);
    }

    /**
     * Classification of a recovery trigger. Real failures
     * (node lease expiry, FAILED reports, administrative entries) share
     * the {@link #maxRestarts} budget; liveness-stall detections draw from a
     * separate budget so they can never consume the real-failure recovery
     * capacity.
     */
    public enum RecoveryCause {
        NODE_FAILURE, TASK_STALL, OTHER
    }

    /**
     * Why the fencing epoch is being rotated. Selects whether the
     * latest-completed-checkpoint view is rebuilt from durable storage:
     * a leadership grant on a fresh coordinator JVM must reload it, while a
     * same-leader recovery keeps the already-alive in-memory view.
     */
    private enum FencingRotationCause {
        SAME_LEADER_RECOVERY, LEADERSHIP_GRANT
    }

    /**
     * What happens to the job after a terminal-savepoint form completes.
     * {@link #TERMINATES_JOB} fires the JOB_FINISHED event, transitions health
     * to finished and stops the job (DRAIN / SUSPEND); {@link #KEEPS_JOB_RUNNING}
     * only exports the savepoint and leaves the job running
     * (EXPORT_SAVEPOINT).
     */
    private enum SavepointScope {
        TERMINATES_JOB, KEEPS_JOB_RUNNING
    }

    /**
     * Performs global recovery:
     * <ol>
     *   <li>Generate a new fencing token</li>
     *   <li>Fence all old tasks</li>
     *   <li>Reassign tasks from the latest durable EpochManifest</li>
     * </ol>
     *
     * <p>ClusterRegistry attempt history is <strong>preserved</strong>
     * across recoveries — only the in-memory coordinator working set is cleared.
     * Each reassigned subtask bumps its {@code attemptNumber} so
     * {@code ClusterRegistry.getAttemptHistory(...)} retains the full attempt
     * sequence for observability.
     *
     * <p><strong>Dedup contract:</strong> concurrent-trigger deduplication is the
     * caller's responsibility and lives in {@link #requestRecovery()} (CAS on
     * {@link #recoveryPending}). Production trigger sites (failure detector,
     * FAILED-report RPC handler) MUST go through {@code requestRecovery()}.
     * Direct callers of this method (tests, {@code activateAsLeader}-style
     * administrative paths) bypass dedup and always perform the full recovery —
     * this preserves the existing contract relied on by recovery-mechanics tests
     * (e.g. {@code TestJobCoordinatorRestartStrategy}, {@code TestFencingEpochUnification}).
     */
    public void globalRecovery() {
        globalRecovery(RecoveryCause.OTHER);
    }

    /**
     * Legacy boolean form of {@link #globalRecovery(RecoveryCause)}.
     * {@code stallTriggered=true} maps to {@link RecoveryCause#TASK_STALL};
     * {@code false} maps to {@link RecoveryCause#OTHER} (real-failure budget).
     * Production callers should prefer the cause-typed overload.
     */
    public void globalRecovery(boolean stallTriggered) {
        globalRecovery(stallTriggered ? RecoveryCause.TASK_STALL : RecoveryCause.OTHER);
    }

    /**
     * Budget-split form of {@link #globalRecovery()}. A
     * stall-triggered recovery ({@link RecoveryCause#TASK_STALL}) draws from the
     * separate stall budget instead of the real-failure budget; everything
     * else (epoch rotation, pending-checkpoint abort, reassignment, fencing)
     * is IDENTICAL for both causes — the fencing invariant holds regardless
     * of why the recovery fires.
     */
    public void globalRecovery(RecoveryCause cause) {
        // The whole body runs under a try/finally that ALWAYS clears the
        // recoveryPending dedup flag — including the budget-cap early returns
        // and any exception from the locked section (fencing rotation DB write,
        // "No RPC service" assignment failures) or the fan-out. Without this,
        // a failed recovery would leave the flag armed forever: every future
        // requestRecovery CAS would fail and every checkpoint trigger would be
        // suppressed — the job would be wedged with no retries and no
        // checkpoints. The finally runs AFTER the fan-out completes, so the
        // ordering contract below (flag stays armed until every deployTask RPC
        // row is issued) is preserved.
        try {
            // Recovery meter + health transition + event at
            // the real recovery path. The sequence number is the TOTAL recovery
            // count (real + stall) so events/health stay monotonic across pools.
            int totalBefore = restartCount.get() + stallRestartCount.get();
            io.nop.stream.runtime.metrics.EngineMetrics.forJob(jobId).recovery();
            notifyRecoveryStartedSafely(totalBefore + 1);
            jobEventBus.fire(StreamJobEvent.simple(jobId,
                    io.nop.stream.runtime.event.StreamJobEvent.EventType.RECOVERY_STARTED,
                    "restart-" + (totalBefore + 1)));
            List<AssignmentPlanner.AssignmentDispatch> dispatches = Collections.emptyList();
            recoveryLock.lock();
            try {
                // Global restart strategy. The counter is incremented only here.
                //
                // WHICH counter depends on the trigger cause —
                // stall-triggered recoveries draw from the stall budget and can
                // never consume the real-failure budget (and vice versa).
                boolean stallTriggered = (cause == RecoveryCause.TASK_STALL);
                int newCount;
                if (stallTriggered) {
                    newCount = stallRestartCount.incrementAndGet();
                    if (newCount > maxStallRestarts) {
                        LOG.error("Stall recovery cap exceeded for job {}: stallCount={} maxStallRestarts={} "
                                + "(real-failure count={} unaffected)", jobId, newCount, maxStallRestarts,
                                restartCount.get());
                        failJob(new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                                "Stall recovery cap exceeded: stallCount=" + newCount
                                        + " maxStallRestarts=" + maxStallRestarts));
                        return;
                    }
                } else {
                    newCount = restartCount.incrementAndGet();
                    if (newCount > maxRestarts) {
                        LOG.error("Global restart cap exceeded for job {}: count={} maxRestarts={}",
                                jobId, newCount, maxRestarts);
                        failJob(new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                                "Global restart cap exceeded: count=" + newCount + " maxRestarts=" + maxRestarts));
                        return;
                    }
                }
                LOG.info("Starting global recovery #{} for job {} (cause={}, realCap={}, stallCount={}, stallCap={})",
                        totalBefore + 1, jobId, stallTriggered ? "TASK_STALL" : "REAL_FAILURE",
                        maxRestarts, stallRestartCount.get(), maxStallRestarts);

                // Fencing: a single monotonic long epoch
                // encodes both leadership switch and same-leader recovery.
                //  - HA mode: rotate the recoveryGen low-order component, keep the leaderEpoch
                //    component unchanged (same leader). The full long epoch still rotates and
                //    is pushed to all TaskManagers so stale same-leader tasks are fenced. The
                //    leaderEpoch component only rotates on leadership switch.
                //  - Non-HA mode: leaderEpoch component is 0, so fencing epoch == recoveryGen.
                LeaderEpoch leadership = this.currentLeadership;
                long leaderEpochValue = leadership != null ? leadership.getEpoch() : 0L;
                long newGen = recoveryGen.incrementAndGet();
                long newEpoch = deriveHaFencingEpoch(leaderEpochValue, newGen);

                // Same-leader recovery does NOT rebuild from storage — the in-memory
                // latestCompletedCheckpoint survives within the same JVM. Only the
                // leadership-grant path (activateAsLeader) rebuilds from storage.
                rotateFencingEpochCoreLocked(newEpoch, FencingRotationCause.SAME_LEADER_RECOVERY);

                // Abort every checkpoint that
                // is still pending under the dead generation. Its barrier/in-flight
                // registrations live on task attempts this recovery is about to replace,
                // so at least some ACKs will never arrive and the pending would stall the
                // checkpoint loop (maxConcurrent=1) for a full timeout after recovery —
                // starving post-recovery commits in bounded runs. Aborted epochs keep
                // their TM-side prepared transactions for subsuming (2PC semantics).
                checkpointCoordinator.abortAllPendingCheckpoints(
                        "global recovery #" + newCount + " (fencing epoch " + newEpoch + ")");

                // Materialize the assignment under the lock; fan-out after release.
                dispatches = assignmentPlanner.prepareAssignmentsLocked(
                        remoteDeployMode, jobGraph, pipelineSpec, checkpointStoragePath);
                markEverAssigned(dispatches);
            } finally {
                // This finally only releases the lock. The dedup flag is NOT
                // cleared here — it stays armed through the assignment fan-out,
                // the health callback and the RECOVERY_COMPLETED event, and is
                // cleared by the OUTER finally (recoveryPending.set(false))
                // after all of those complete. See the notes at the fan-out and
                // at the outer finally for why the suppression window extends
                // past the locked section.
                recoveryLock.unlock();
            }

            // The dedup/suppression flag stays armed THROUGH the assignment
            // fan-out. A periodic checkpoint trigger that lands between the locked
            // section and the fan-out would insert its triggerCheckpoint RPC row
            // BEFORE the new deployment rows on the task topics — the TaskManager
            // would then register the in-flight epoch on pre-replacement attempts
            // (or none at all), and the new attempts' operator barrier ACKs would be
            // dropped by their trackers ("no matching in-flight epoch"), dooming that
            // checkpoint. Keeping recoveryPending=true until every deployTask RPC row
            // is issued (triggerCheckpoint rejects while it is armed) makes the next
            // fresh trigger land strictly AFTER the deployment rows in topic order.
            // (The flag itself is cleared by the outer finally, which runs after
            // this fan-out completes.)
            recordDeploysIssued(dispatches);
            assignmentPlanner.executeAssignmentFanOut(dispatches, this::markDeployTransportFailed);

            // A completed recovery always carries its failure
            // trace (restart count > 0) — post-recovery health is DEGRADED until
            // the next durable checkpoint heals it. The trace
            // carries the TOTAL recovery count across both budget pools.
            int totalAfter = restartCount.get() + stallRestartCount.get();
            notifyRecoveryCompletedSafely(totalAfter);
            jobEventBus.fire(StreamJobEvent.simple(jobId,
                    io.nop.stream.runtime.event.StreamJobEvent.EventType.RECOVERY_COMPLETED,
                    "restarts=" + totalAfter));
        } finally {
            // THE dedup-flag clear point: outer finally, after the locked
            // section, the assignment fan-out, the health callback and the
            // RECOVERY_COMPLETED event. The flag stays armed for the whole
            // suppression window so (a) a redundant requestRecovery CAS fails
            // for the entire in-flight recovery, and (b) a periodic
            // triggerCheckpoint cannot interleave its RPC row before the
            // deployment rows (see the fan-out note above). Any
            // requestRecovery firing after this clear is a legitimate, distinct
            // trigger (globalRecovery is global/idempotent — a redundant
            // post-completion trigger is wasteful, not corrupting). Lock-internal
            // exceptions (fencing DB write, assignment planning) also reach this
            // finally, so a failed recovery can never leave the flag stuck.
            recoveryPending.set(false);
        }
    }

    /**
     * CC-01 follow-on: the health transitions are OBSERVABILITY side effects
     * and must never break the recovery itself. Concretely: a recovery that
     * aborted mid-flight leaves the health machine in RECOVERING (its
     * completion callback never ran); the sentinel-driven retry (see
     * detectFailures) then hits the strict RECOVERING→RECOVERING rejection —
     * which, unguarded, would abort the very recovery that is supposed to heal
     * the wedge. Failures are logged and swallowed; the health machine
     * converges on the recovery's completion callback (RECOVERING→DEGRADED).
     */
    private void notifyRecoveryStartedSafely(int sequence) {
        try {
            health.onRecoveryStarted(sequence);
        } catch (Exception e) {
            LOG.error("Health transition failed at recovery start for job {} (sequence={}); "
                    + "recovery continues", jobId, sequence, e);
        }
    }

    private void notifyRecoveryCompletedSafely(int totalRestarts) {
        try {
            health.onRecoveryCompleted(totalRestarts);
        } catch (Exception e) {
            LOG.error("Health transition failed at recovery completion for job {} (restarts={})",
                    jobId, totalRestarts, e);
        }
    }

    /**
     * CC-01: records that this coordinator materialized a non-empty assignment
     * working set, arming the empty-working-set sentinel in
     * {@link #detectFailures()}. An empty dispatch list (no active nodes yet)
     * does not arm the sentinel — there was never a working set to lose.
     */
    private void markEverAssigned(List<AssignmentPlanner.AssignmentDispatch> dispatches) {
        if (dispatches != null && !dispatches.isEmpty()) {
            everAssigned = true;
        }
    }

    /**
     * R5-CC-07 (plan 369 Phase 4): records the deploy-issue timestamp of every
     * dispatch right before its RPC is issued, starting the deployment grace
     * window that {@link #detectFailures()} evaluates for assignments that never
     * produce a liveness record. Called at all three fan-out drive sites with
     * the SAME injectable clock the detector reads, so the elapsed comparison is
     * consistent.
     */
    private void recordDeploysIssued(List<AssignmentPlanner.AssignmentDispatch> dispatches) {
        if (dispatches == null || dispatches.isEmpty()) {
            return;
        }
        long now = clock.getAsLong();
        for (AssignmentPlanner.AssignmentDispatch d : dispatches) {
            deployIssuedAtMs.put(livenessKeyOf(d.taskAssignment), now);
        }
    }

    /**
     * R5-CC-07 deploy-failure record point: invoked by
     * {@code AssignmentPlanner.executeAssignmentFanOut} when a deploy/assignment
     * RPC failed at the transport level. The key is marked known-bad
     * (observability + detector diagnostics); recovery still waits for the
     * grace period so the failed-transport and lost-transport cases behave
     * identically.
     */
    private void markDeployTransportFailed(String vertexId, int subtaskIndex, Throwable cause) {
        String key = vertexId + "/" + subtaskIndex;
        deployTransportFailures.add(key);
        LOG.warn("Deploy transport failure recorded for {}/{} (job {}) — deployment grace period "
                        + "applies before stall/recovery detection treats it as a deploy failure",
                vertexId, subtaskIndex, jobId, cause);
    }

    private static String livenessKeyOf(io.nop.stream.runtime.cluster.TaskAssignment assignment) {
        return assignment.getVertexId() + "/" + assignment.getSubtaskIndex();
    }

    /**
     * Registers the cluster-level active-node
     * gauge against the process composite registry. Idempotent per gauge id;
     * standalone gauges on the same registry do not duplicate.
     */
    private void registerNodesActiveGauge() {
        if (clusterRegistry != null) {
            io.nop.stream.runtime.metrics.EngineMetrics.registerNodesActiveGauge(
                    io.nop.stream.core.metrics.StreamMetricsRegistries.registry(),
                    () -> clusterRegistry.getActiveNodes().size());
        }
    }

    /**
     * Shared fencing-epoch rotation + control-plane
     * rebuild used by both {@link #globalRecovery()} (same-leader recovery) and
     * {@link #activateAsLeader(LeaderEpoch)} (leadership grant). Rotates the fencing
     * epoch, re-registers the coordinator, pushes the new epoch to all TaskManagers,
     * and optionally rebuilds the latest-completed-checkpoint view from durable storage.
     *
     * <p><strong>Must be called while holding {@link #recoveryLock}.</strong> This method
     * performs the in-memory critical section (epoch rotation + working-set clear +
     * fencing-token push). It does NOT reassign tasks — the caller materializes the
     * assignment via {@link AssignmentPlanner#prepareAssignmentsLocked} (still under the lock) and then
     * performs the RPC fan-out via {@link AssignmentPlanner#executeAssignmentFanOut} after releasing
     * the lock, so blocking IO never occurs inside the recovery critical section.
     *
     * <p><strong>Failover-safe rebuild</strong>: when {@code cause} is
     * {@link FencingRotationCause#LEADERSHIP_GRANT} AND the in-memory
     * {@code latestCompletedCheckpoint} is {@code null} (the fresh-coordinator-JVM case),
     * this method calls {@link CheckpointCoordinator#restoreFromCheckpoint()} to reload
     * the latest durable epoch from {@link ICheckpointStorage}. A storage failure during
     * this rebuild is a correctness risk (the new leader cannot safely resume), so it
     * <strong>fails loud</strong> (throws {@link StreamException}) rather than silently
     * continuing (no silent no-op).
     *
     * <p>When {@code cause} is {@link FencingRotationCause#SAME_LEADER_RECOVERY},
     * the rebuild is skipped: the in-memory view is already alive and
     * an extra DB round-trip per recovery is unnecessary (the field survives same-leader
     * restarts within one JVM).
     *
     * @param newEpoch the rotated fencing epoch
     * @param cause    {@link FencingRotationCause#LEADERSHIP_GRANT} on leadership grant
     *                 (rebuild from storage when in-memory view is empty);
     *                 {@link FencingRotationCause#SAME_LEADER_RECOVERY} on
     *                 same-leader recovery
     */
    private void rotateFencingEpochCoreLocked(long newEpoch, FencingRotationCause cause) {
        fencingEpoch.set(newEpoch);

        clusterRegistry.registerCoordinator(jobId, coordinatorId, newEpoch);

        // Clear the in-memory working set only. Do NOT wipe the ClusterRegistry
        // attempt history — it must be preserved across recoveries.
        taskAssignmentMap.clear();
        allTaskLocations.clear();

        // CC-04 (R5 audit): the previous generation's per-subtask liveness entries
        // must not survive the rotation. A frozen timestamp kept under a
        // "vertexId/subtaskIndex" key that the new assignment reuses would be
        // compared against the NEW assignment before its first heartbeat arrives;
        // once its age crosses taskTimeoutMs the first detector tick flags a
        // phantom TASK_STALL and burns one stall-recovery budget slot (worst
        // case: stall cap exceeded → job failed). Every assignment after a
        // rotation is a new generation, so the entries have no retained value.
        //
        // REG-02 (plan366-regression audit): the completed-subtask tombstones
        // are generation-scoped for the same reason — every subtask is
        // re-attempted under the new assignments.
        subtaskLiveness.clear();
        completedSubtaskKeys.clear();

        // R5-CC-07 (plan 369 Phase 4): the deploy grace markers are generation-
        // scoped for the same reason as the liveness entries — every subtask is
        // re-attempted under the new assignments and its fan-out re-records a
        // fresh issue timestamp.
        deployIssuedAtMs.clear();
        deployTransportFailures.clear();

        // Push the rotated fencing epoch to all registered TaskManagers so stale
        // envelopes are rejected at the data plane (RemoteInputChannel /
        // RemoteResultPartition filter on a single long epoch comparison).
        //
        // CC-01 (R5 audit): the push is isolated PER NODE. Pre-fix a single
        // failing/unreachable TaskManager aborted the whole recovery mid-flight —
        // AFTER the working set above was cleared but BEFORE any redeploy. The
        // job stayed "active but zero assignments": detectFailures iterated an
        // empty map, so nothing was ever detectable again and no recovery could
        // re-trigger (job-level permanent wedge). A failed push is now contained
        // and logged: the target keeps its stale token until the next rotation
        // fences it, and the empty-working-set sentinel in detectFailures (armed
        // by everAssigned) is the bottom line for any residual wedge window.
        for (Map.Entry<String, IStreamTaskRpcService> entry : taskRpcServices.entrySet()) {
            try {
                entry.getValue().updateFencingToken(newEpoch);
            } catch (Exception e) {
                LOG.error("Failed to push rotated fencing epoch {} to node {} for job {} — "
                                + "the node keeps its stale token until the next rotation; "
                                + "recovery continues",
                        newEpoch, entry.getKey(), jobId, e);
            }
        }

        if (cause == FencingRotationCause.LEADERSHIP_GRANT) {
            // Failover-safe rebuild. On a fresh coordinator JVM (leadership
            // grant), the in-memory latestCompletedCheckpoint is null. Reload it
            // from durable storage so the coordinator can resume from the latest
            // durable epoch + 1. Same-leader recovery (globalRecovery) skips this:
            // the field is already alive in the same JVM.
            CompletedCheckpoint inMemory = checkpointCoordinator.getLatestCheckpoint();
            if (inMemory == null) {
                try {
                    CompletedCheckpoint restored = checkpointCoordinator.restoreFromCheckpoint();
                    if (restored != null) {
                        LOG.info("Rebuilt latestCompletedCheckpoint from storage for job {} "
                                        + "(restored epoch={}, next epoch will be >= {})",
                                jobId, restored.getCheckpointId(), restored.getCheckpointId() + 1);
                    } else {
                        LOG.info("No durable checkpoint found in storage for job {}; "
                                + "starting fresh (no restore needed)", jobId);
                    }
                } catch (Exception e) {
                    // Fail-loud: storage unreachable during failover rebuild
                    // is a correctness risk — the new leader cannot safely decide whether
                    // to resume or start fresh. Surface it as a hard failure rather than a
                    // silent warn-and-continue that would mask a split-brain or data loss.
                    throw new StreamException(ERR_STREAM_INVALID_STATE, e).param(ARG_DETAIL,
                            "Failed to rebuild latestCompletedCheckpoint from storage during "
                                    + "leadership activation for job " + jobId
                                    + ". The new leader cannot safely resume. Cause: "
                                    + (e.getMessage() == null ? e.toString() : e.getMessage()));
                }
            } else {
                LOG.info("Recovering from in-memory checkpoint {} for job {} (same-JVM leader switch)",
                        inMemory.getCheckpointId(), jobId);
            }
        } else {
            // Same-leader global recovery: the in-memory view is alive; only log it.
            CompletedCheckpoint inMemory = checkpointCoordinator.getLatestCheckpoint();
            if (inMemory != null) {
                LOG.info("Recovering from in-memory checkpoint {} for job {} (same-leader recovery)",
                        inMemory.getCheckpointId(), jobId);
            }
        }

        LOG.info("Fencing epoch rotated for job {} (epoch={}, cause={})",
                jobId, newEpoch, cause);
    }

    /**
     * Derives the single monotonic long fencing epoch from a
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
     * Election-listener callback handler. Activation/deactivation is
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
     * Leadership-grant activation. Derives a fresh composite fencing
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
        // Mark active BEFORE rebuilding so the internal assignment materialization
        // (prepareAssignmentsLocked) observes the active state.
        this.active = true;

        long token = deriveHaFencingEpoch(epoch.getEpoch(), 0);

        // Leadership-grant rebuild runs under the same recovery lock as
        // globalRecovery, so a concurrent same-leader recovery cannot interleave with
        // the epoch rotation / working-set clear. The RPC fan-out executes after the
        // lock is released (no blocking IO under the lock).
        List<AssignmentPlanner.AssignmentDispatch> dispatches;
        recoveryLock.lock();
        try {
            // On leadership grant, rebuild the latestCompletedCheckpoint view
            // from durable storage (failover-safe). A new coordinator JVM has a null
            // in-memory view; restoreFromCheckpoint() reloads the latest durable epoch.
            rotateFencingEpochCoreLocked(token, FencingRotationCause.LEADERSHIP_GRANT);
            // Seed the per-subtask attempt counters
            // from the registry's persisted attempt history BEFORE materializing the
            // assignments, so the fresh coordinator's re-issued attempt numbers
            // strictly continue the old leader's persisted history instead of
            // colliding on the (job_id, vertex_id, subtask_index, attempt_number)
            // primary key and aborting the become-leader listener.
            seedAttemptCountersFromRegistryLocked();
            dispatches = assignmentPlanner.prepareAssignmentsLocked(
                    remoteDeployMode, jobGraph, pipelineSpec, checkpointStoragePath);
            markEverAssigned(dispatches);
        } finally {
            recoveryLock.unlock();
        }
        recordDeploysIssued(dispatches);
        assignmentPlanner.executeAssignmentFanOut(dispatches, this::markDeployTransportFailed);
    }

    /**
     * Seeds the per-subtask attempt counters from the
     * registry's persisted attempt history before leadership activation materializes
     * assignments.
     *
     * <p>Why seeding is required: on a fresh coordinator JVM the in-memory
     * {@link #attemptCounters} start empty, so {@link AssignmentPlanner#prepareAssignmentsLocked}
     * re-issues attempt numbers from 1. When the shared registry still holds the old
     * leader's rows for the same (jobId, vertexId, subtaskIndex) with
     * attempt_number=1, the plain INSERT in {@code ClusterRegistry.assignTask} violates
     * the (job_id, vertex_id, subtask_index, attempt_number) primary key and the
     * become-leader listener aborts (the platform elector swallows the exception with
     * no retry and no degradation), leaving the job half-activated: lease held,
     * epoch rotated, active=true, but no assignments and no further checkpoints.
     *
     * <p>Seeding rule: for every (vertexId, subtaskIndex) enumerated by the deployment
     * plan, read {@link ClusterRegistry#getAttemptHistory} and raise the in-memory
     * counter to max(current, historyMax). No history rows → counter stays absent →
     * the next assignment uses 1 (byte-identical to the fresh-job behavior); an
     * already-larger in-memory value is never lowered. Reading on EVERY activation
     * (not only when the counter key is missing) is the warm-path policy:
     * it also corrects a stale in-memory counter after a leadership ping-pong between
     * distinct coordinator instances sharing one persistent registry, and costs one
     * bounded read per subtask per leadership activation — the same order of registry
     * round-trips the immediately-following assignment INSERTs already perform.
     *
     * <p>Failure semantics: a registry read failure during seeding
     * de-activates the coordinator ({@code active=false}, leadership state cleared)
     * and then rethrows — after the platform elector swallows the listener exception
     * the node is left as an explicit STANDBY, never an unrecorded frozen half-active
     * leader holding {@code active=true} with no assignments.
     *
     * <p><strong>Must be called while holding {@link #recoveryLock}</strong> (called
     * from {@link #activateAsLeader} between the epoch rotation and the assignment
     * materialization).
     */
    private void seedAttemptCountersFromRegistryLocked() {
        if (deploymentPlan == null || deploymentPlan.getPartitionedPlan() == null) {
            return;
        }
        try {
            for (Map.Entry<String, io.nop.stream.core.execution.plan.PartitionedPlan.VertexPlan> entry :
                    deploymentPlan.getPartitionedPlan().getVertexPlans().entrySet()) {
                String vertexId = entry.getKey();
                int parallelism = entry.getValue().getParallelism();
                for (int subtaskIndex = 0; subtaskIndex < parallelism; subtaskIndex++) {
                    String attemptKey = vertexId + "/" + subtaskIndex;
                    List<TaskAssignment> history =
                            clusterRegistry.getAttemptHistory(jobId, vertexId, subtaskIndex);
                    if (history == null || history.isEmpty()) {
                        // No persisted attempts for this subtask: the counter stays
                        // absent and the next assignment starts at 1 (fresh-job
                        // semantics preserved).
                        continue;
                    }
                    int historyMax = 0;
                    for (TaskAssignment ta : history) {
                        historyMax = Math.max(historyMax, ta.getAttemptNumber());
                    }
                    int previous = attemptCounters.getOrDefault(attemptKey, 0);
                    if (historyMax > previous) {
                        attemptCounters.put(attemptKey, historyMax);
                        LOG.info("Seeded attempt counter for job {} {}/{} from registry history: {} -> {}",
                                jobId, vertexId, subtaskIndex, previous, historyMax);
                    }
                }
            }
        } catch (Exception e) {
            // De-activate BEFORE rethrowing: after the
            // platform elector swallows the listener exception this node must remain an
            // explicit standby, not a frozen half-active leader. Reversible — the next
            // leadership grant re-activates from scratch.
            this.active = false;
            this.currentLeadership = null;
            LOG.error("Failed to seed attempt counters from the cluster registry during "
                    + "leadership activation for job {}; deactivating to STANDBY before rethrow",
                    jobId, e);
            throw new StreamException(ERR_STREAM_INVALID_STATE, e).param(ARG_DETAIL,
                    "Failed to seed attempt counters from the cluster registry during "
                            + "leadership activation for job " + jobId
                            + ". The new leader cannot safely continue takeover. Cause: "
                            + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    /**
     * Leadership-loss / follower deactivation. Flips the active flag to
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
        // failure detector here (deactivate != stop).
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
        if (!running) {
            LOG.warn("JobCoordinator not running, cannot terminate job {}", jobId);
            return;
        }
        // A standby coordinator must never act on termination requests —
        // a standby terminate(CANCEL) would set CANCELED and stop() this instance
        // (and, in the single-JVM HA topology sharing one CheckpointCoordinator,
        // mutate the active leader's coordinator state).
        if (!active) {
            LOG.warn("JobCoordinator in STANDBY (not leader), cannot terminate job {}", jobId);
            return;
        }
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
        // Surface the CANCELED terminal transition explicitly. Health
        // transitions FIRST — a stop during an
        // in-flight RECOVERING window fails fast here (explicit exception,
        // no mutation) instead of silently canceling mid-recovery; the caller
        // (REST layer) maps it to 409 and the client retries once the
        // recovery completes (millisecond-scale window).
        health.onCanceled();
        this.jobStatus = JobStatus.CANCELED;
        jobEventBus.fire(StreamJobEvent.simple(jobId,
                io.nop.stream.runtime.event.StreamJobEvent.EventType.JOB_CANCELED, null));
        stop();
    }

    private void terminateDrain() {
        // CheckpointType per checkpoint-design.md §7.3
        // (TERMINAL_SAVEPOINT for DRAIN/SUSPEND).
        terminateWithTerminalSavepoint("DRAIN", CheckpointType.TERMINAL_SAVEPOINT,
                "final checkpoint", "drain", SavepointScope.TERMINATES_JOB);
    }

    private void terminateSuspend() {
        terminateWithTerminalSavepoint("SUSPEND", CheckpointType.TERMINAL_SAVEPOINT,
                "savepoint", "suspend", SavepointScope.TERMINATES_JOB);
    }

    private void terminateExportSavepoint() {
        terminateWithTerminalSavepoint("EXPORT_SAVEPOINT", CheckpointType.EXPORTED_SAVEPOINT,
                "export savepoint", null, SavepointScope.KEEPS_JOB_RUNNING);
    }

    /**
     * Single parameterized implementation for the terminal-savepoint termination
     * forms (DRAIN / SUSPEND / EXPORT_SAVEPOINT): tryTrigger → barrier →
     * sendBarrierToAllTaskManagers → future.get(terminationCheckpointTimeoutMs) →
     * log → side effects. The parameter matrix below selects CheckpointType, log
     * texts (mode label + snapshot noun), JOB_FINISHED event payload, health
     * transition + stop (terminal modes), and the
     * continues-running semantics of EXPORT_SAVEPOINT.
     *
     * @param mode            mode label for logs and health cause ("DRAIN" etc.)
     * @param checkpointType  the terminal checkpoint type to trigger
     * @param snapshotNoun    log noun ("final checkpoint" / "savepoint" / "export savepoint")
     * @param finishedPayload JOB_FINISHED event payload; {@code null} = no event (export)
     * @param scope           {@link SavepointScope#TERMINATES_JOB} = health.onFinished +
     *                        JOB_FINISHED + stop(); {@link SavepointScope#KEEPS_JOB_RUNNING}
     *                        = job keeps running after the export
     */
    private void terminateWithTerminalSavepoint(String mode, CheckpointType checkpointType,
                                                String snapshotNoun, String finishedPayload,
                                                SavepointScope scope) {
        boolean terminal = (scope == SavepointScope.TERMINATES_JOB);
        LOG.info("{}: triggering {} for job {}", mode, snapshotNoun, jobId);
        // C2 (plan 369): the trigger uses bounded retry with typed-reason
        // disposition. A collision with an in-flight periodic checkpoint
        // (maxConcurrent=1) previously returned null here and the terminal
        // savepoint was silently skipped while the job was declared FINISHED —
        // the "catch 后仍 stop()" hazard. Now: back-pressure rejections are
        // retried within the checkpoint timeout budget; NO_TASKS_TO_ACK
        // short-circuits as success (with the C3 participant contraction an
        // empty ACK set means every task is terminal); a final trigger failure
        // fails the job loud (failJob) for TERMINATES_JOB scope and is logged
        // loud while the job keeps running for EXPORT_SAVEPOINT scope. The
        // InterruptedException path keeps the plan 368 CC-11 recorded
        // interrupt→teardown semantics unchanged.
        try {
            PendingCheckpoint pending = checkpointCoordinator.triggerCheckpointBounded(
                    checkpointType, terminationCheckpointTimeoutMs);
            if (pending != null) {
                // C2×C3 (plan 369): an all-terminal epoch is assembled from
                // inherited states and completed inline by the coordinator —
                // there are no live subtasks left to deliver a barrier to, so
                // skip the RPC fan-out when the trigger already finished the
                // epoch (the future is done; a barrier RPC would only hit
                // dead/absent task slots).
                if (!pending.getCompletableFuture().isDone()) {
                    CheckpointBarrier barrier = new CheckpointBarrier(
                            pending.getCheckpointId(),
                            pending.getTriggerTimestamp(),
                            pending.getCheckpointType());
                    sendBarrierToAllTaskManagers(barrier);
                } else {
                    LOG.info("{}: {} {} assembled from terminal states for job {} "
                            + "(no live participants; barrier fan-out skipped)",
                            mode, snapshotNoun, pending.getCheckpointId(), jobId);
                }

                pending.getCompletableFuture()
                        .get(terminationCheckpointTimeoutMs, TimeUnit.MILLISECONDS);
                if (terminal) {
                    LOG.info("{}: {} {} completed for job {}",
                            mode, snapshotNoun, pending.getCheckpointId(), jobId);
                } else {
                    LOG.info("{}: {} {} exported for job {}. Job continues running.",
                            mode, snapshotNoun, pending.getCheckpointId(), jobId);
                }
            } else {
                LOG.info("{}: {} trigger short-circuited as success for job {} "
                        + "(no tasks to acknowledge — all participants terminal)",
                        mode, snapshotNoun, jobId);
            }
        } catch (InterruptedException e) {
            // CC-11 (R5 audit): restore the interrupt status. The terminal
            // savepoint wait (up to terminationCheckpointTimeoutMs) must stay
            // cancellable by shutdown hooks / REST timeouts — pre-fix the
            // generic catch swallowed the interrupt and the caller's cancel
            // request was lost. The terminal teardown below still runs
            // (stopping the job is the correct action once the wait was
            // interrupted); the caller re-observes the interrupt on its next
            // blocking operation.
            Thread.currentThread().interrupt();
            LOG.warn("{}: interrupted while waiting for {} of job {}; stopping the job",
                    mode, snapshotNoun, jobId);
        } catch (Exception e) {
            // C2 (plan 369): a terminal savepoint that ultimately failed is NOT
            // silently absorbed into a FINISHED teardown.
            if (terminal) {
                LOG.error("{}: failed to complete {} for job {} — failing the job (terminal "
                        + "savepoint is a durability promise, not a best-effort step)",
                        mode, snapshotNoun, jobId, e);
                failJob(new io.nop.stream.core.exceptions.StreamException(
                        io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_FAILED, e)
                        .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_REASON,
                                mode + " terminal savepoint failed"));
                return;
            }
            // EXPORT_SAVEPOINT (KEEPS_JOB_RUNNING): the export failed loudly but
            // the job was never promised to stop — record the failure and keep
            // running (adjudicated in plan 369 C2).
            LOG.error("{}: failed to export {} for job {} — job keeps running",
                    mode, snapshotNoun, jobId, e);
            return;
        }
        if (!terminal) {
            // Job continues running after EXPORT_SAVEPOINT: no health transition,
            // no JOB_FINISHED event, no stop().
            return;
        }
        health.onFinished(mode);
        jobEventBus.fire(StreamJobEvent.simple(jobId,
                io.nop.stream.runtime.event.StreamJobEvent.EventType.JOB_FINISHED, finishedPayload));
        stop();
    }

    // ==================== Status ====================

    /**
     * Aborts the pending checkpoint identified by {@code epochId}
     * via {@link CheckpointCoordinator#abortPendingCheckpoint}. This triggers the
     * abort path which fires the LOCAL abort handler (registered by
     * {@code GraphModelCheckpointExecutor.registerLocalAbortHandler}) to cancel
     * the coordinator-JVM tasks in-process.
     *
     * <p>If {@code epochId} does not match any currently-pending checkpoint, the
     * call logs a warning and returns — the unmatched case is explicitly
     * observable (no silent swallow).
     */
    @Override
    public void abortCheckpoint(long epochId) {
        if (!running) {
            LOG.debug("Ignoring abortCheckpoint({}) — coordinator not running for job {}", epochId, jobId);
            return;
        }
        // A standby coordinator must never abort a (potentially shared)
        // pending checkpoint. In the single-JVM HA topology both coordinators are
        // built on the same CheckpointCoordinator, so an un-gated standby abort
        // would cancel the active leader's pending checkpoint.
        if (!active) {
            LOG.warn("Ignoring abortCheckpoint({}) for job {}: coordinator in STANDBY (not leader)",
                    epochId, jobId);
            return;
        }
        PendingCheckpoint pending = checkpointCoordinator.getPendingCheckpoint(epochId);
        if (pending == null) {
            // Explicit handling of unmatched epochId (log + return), not a
            // silent no-op. A stale or unknown epoch may arrive from a slow/raced
            // RPC caller; the warning makes it observable.
            LOG.warn("abortCheckpoint({}) for job {}: no pending checkpoint matches this epochId "
                    + "(already completed/aborted/unknown). No-op.", epochId, jobId);
            return;
        }
        checkpointCoordinator.abortPendingCheckpoint(pending, "Coordinator RPC abortCheckpoint(" + epochId + ")");
    }

    /**
     * Registers the <b>distributed</b> checkpoint-abort handler on
     * the {@link CheckpointCoordinator}. When a checkpoint is aborted (timeout or
     * explicit abort), the handler fires {@code cancelTask} RPC at every currently
     * assigned remote task via {@link IStreamTaskRpcService#cancelTask} — the abort
     * signal's independent control channel (checkpoint-design §13.2: the abort
     * signal must have a control channel independent of the data flow).
     *
     * <p>The remote {@code TaskManager.cancelTask} runs {@code RunningTask.cancel()}
     * (mailbox {@code signalCancel} + {@code future.cancel(true)} interrupt), which
     * unblocks a stalled barrier-alignment read (checkpoint-design §13.2: a
     * coordinator abort must terminate blocked alignment reads).
     *
     * <p>Relationship to the LOCAL abort path
     * ({@code GraphModelCheckpointExecutor.registerLocalAbortHandler}): the two
     * coexist. The LOCAL path cancels coordinator-JVM-internal
     * tasks (embedded fast-path); this DISTRIBUTED path cancels remote tasks over
     * RPC. A deployment uses one or the other depending on the execution form
     * (embedded vs RPC-distributed). {@code RpcDistributedExecutor} registers this
     * distributed handler; {@code GraphModelCheckpointExecutor} registers its local
     * handler. RPC failure during abort is logged and propagated per-node (not
     * silently swallowed); the next failure-detection /
     * global-recovery cycle still fences the un-canceled tasks via the rotated epoch.
     */
    public void registerDistributedAbortHandler() {
        checkpointCoordinator.setAbortHandler((checkpointId, reason) -> {
            if (!active) {
                // A standby coordinator must not issue cancelTask RPCs.
                LOG.warn("Ignoring distributed abort({}) for job {}: coordinator not active", checkpointId, jobId);
                return;
            }
            // A checkpoint TIMEOUT is a
            // routine back-pressure event — the epoch is discarded, tasks keep
            // running, and the next periodic trigger retries. Only a
            // SNAPSHOT-FAILURE abort (possibly inconsistent task state) cancels
            // the assigned tasks.
            if (reason != null && reason.startsWith("Timeout")) {
                LOG.warn("Distributed checkpoint abort {} for job {}: timeout (reason={}) — "
                                + "discarding the epoch only; tasks keep running",
                        checkpointId, jobId, reason);
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
                        // cancelTask carries this coordinator's
                        // current fencing epoch — a stale coordinator's cancel is
                        // rejected at the TaskManager boundary (typed mismatch).
                        rpc.cancelTask(ta.getJobId(), ta.getVertexId(), ta.getSubtaskIndex(),
                                getFencingEpoch());
                    } catch (Exception e) {
                        // Explicit propagation — log per-node failure; the next
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
     * Injects the platform {@link ILeaderElector}. When non-null the
     * coordinator runs in HA (leader-gated) mode; when null it keeps the
     * single-instance behaviour. Must be set BEFORE {@link #start()}. The elector
     * bean is IoC-managed (e.g. {@code SysDaoLeaderElector}); this coordinator
     * only consumes the {@link ILeaderElector} contract.
     */
    public void setLeaderElector(ILeaderElector leaderElector) {
        this.leaderElector = leaderElector;
    }

    /**
     * Whether the control plane is currently active on this coordinator.
     * In non-HA mode always true once started. In HA mode true only while this
     * node is the elected leader.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * The leadership epoch currently held (HA mode), or null when
     * non-HA / not yet elected / lost leadership.
     */
    public LeaderEpoch getCurrentLeadership() {
        return currentLeadership;
    }

    /**
     * Current recovery generation (composite-token suffix). Resets to 0
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
     * Whether the internal failure-detector scheduler is still alive
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
     * Number of tracked per-subtask liveness entries. Exposed
     * for diagnostics and for HA tests to verify that a STANDBY coordinator
     * rejects (rather than silently accepts) {@code reportNodeTaskLiveness}
     * calls — the map must stay empty while in STANDBY.
     */
    public int getSubtaskLivenessCount() {
        return subtaskLiveness.size();
    }

    /**
     * Monotonic-max semantics: the recorded liveness value for a key.
     * Package-private test accessor (concurrent-delivery tests assert the max
     * survives interleaved older reports).
     */
    long getSubtaskLivenessValue(String key) {
        return subtaskLiveness.getOrDefault(key, -1L);
    }

    public void setTerminationCheckpointTimeoutMs(long timeoutMs) {
        this.terminationCheckpointTimeoutMs = timeoutMs;
    }

    /**
     * Per-task aliveness timeout. A task whose recorded aliveness
     * timestamp (see {@link #subtaskLiveness}) is older than this is considered
     * stalled and triggers recovery on the next {@link #detectFailures()} tick.
     * The value must stay above the TaskManager heartbeat interval (5s) so a
     * healthy idle task (heartbeat-refreshed) never falls behind the cutoff.
     */
    public void setTaskTimeoutMs(long taskTimeoutMs) {
        this.taskTimeoutMs = taskTimeoutMs;
    }

    /**
     * R5-CC-07 (plan 369 Phase 4): deployment grace period. An assignment with
     * no liveness record whose deploy RPC was issued more than this long ago is
     * treated as a deployment failure by {@link #detectFailures()} (before the
     * fix this case was a permanent benefit-of-the-doubt blind spot). Default
     * {@value #DEFAULT_DEPLOY_GRACE_PERIOD_MS}ms — comfortably above the 5s TM
     * heartbeat so a successfully deployed task's first liveness report always
     * lands inside the window; {@code 0} disables the grace (every unreported
     * assignment is a deploy failure on the next detector tick).
     */
    public void setDeployGracePeriodMs(long deployGracePeriodMs) {
        this.deployGracePeriodMs = Math.max(0L, deployGracePeriodMs);
    }

    /** Deployment grace period in ms (R5-CC-07). */
    public long getDeployGracePeriodMs() {
        return deployGracePeriodMs;
    }

    /**
     * Injects the wall clock used by {@link #detectFailures()} and the deploy
     * grace timestamps. Package-private: focused tests advance time without
     * sleeping; production never touches this.
     */
    void setClock(java.util.function.LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Package-private test accessor: number of assignments currently holding an
     * open deploy grace window.
     */
    int getPendingDeployCount() {
        return deployIssuedAtMs.size();
    }

    /**
     * Package-private test accessor: whether the deploy RPC for this subtask
     * failed at the transport level in the current generation.
     */
    boolean isDeployTransportFailed(String vertexId, int subtaskIndex) {
        return deployTransportFailures.contains(vertexId + "/" + subtaskIndex);
    }

    /**
     * Configures whether a per-task FAILED report triggers automatic
     * {@link #globalRecovery()}. Default {@code true} (production behavior).
     * Embedded E2E paths set this to {@code false} to preserve synchronous
     * failure propagation.
     */
    public void setAutoRecoverOnFailedReport(boolean enabled) {
        this.autoRecoverOnFailedReport = enabled;
    }

    /**
     * Max global restarts before {@link #failJob(Throwable)} fires.
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
     * Stall-budget tuning — max stall-triggered recoveries
     * before the job is marked FAILED, and the cooldown window between
     * stall-triggered recoveries.
     */
    public void setMaxStallRestarts(int maxStallRestarts) {
        this.maxStallRestarts = Math.max(0, maxStallRestarts);
    }

    public void setStallRecoveryCooldownMs(long stallRecoveryCooldownMs) {
        this.stallRecoveryCooldownMs = Math.max(0L, stallRecoveryCooldownMs);
    }

    /** Stall-triggered recovery count (separate budget). */
    public int getStallRestartCount() {
        return stallRestartCount.get();
    }

    /** Total recoveries across both budget pools. */
    public int getTotalRecoveryCount() {
        return restartCount.get() + stallRestartCount.get();
    }

    /**
     * Toggles remote-deploy mode. When {@code true},
     * {@link #assignTasks()} sends a {@link TaskDeploymentDescriptor} via
     * {@link IStreamTaskRpcService#deployTask} (cross-JVM path; the TaskManager
     * rebuilds its own invokable locally). When {@code false} (default, in-process
     * path), {@link #assignTasks()} calls {@link IStreamTaskRpcService#receiveAssignment}
     * and the embedding executor installs the invokable via a direct Java call.
     *
     * <p>Recovery inherits the same mode: {@link #globalRecovery()} →
     * {@link #rotateFencingEpochCoreLocked} →
     * {@link AssignmentPlanner#prepareAssignmentsLocked} →
     * {@link AssignmentPlanner#executeAssignmentFanOut}.
     */
    public void setRemoteDeployMode(boolean remoteDeployMode) {
        this.remoteDeployMode = remoteDeployMode;
    }

    public boolean isRemoteDeployMode() {
        return remoteDeployMode;
    }

    /**
     * Injects the {@link JobGraph} used to build
     * {@link TaskDeploymentDescriptor}s in remote-deploy mode. Required when
     * {@link #setRemoteDeployMode(boolean)} is set to {@code true}; ignored
     * otherwise.
     */
    public void setJobGraph(JobGraph jobGraph) {
        this.jobGraph = jobGraph;
    }

    public JobGraph getJobGraph() {
        return jobGraph;
    }

    /**
     * Sets the serializable pipeline declaration shipped in the
     * deployment descriptors (see {@link #pipelineSpec}). Must be set together
     * with {@link #setJobGraph(JobGraph)} — the coordinator still needs its own
     * graph for consistency checks; only the TM-bound copy is replaced by the spec.
     */
    public void setPipelineSpec(io.nop.stream.runtime.rpc.RemotePipelineSpec pipelineSpec) {
        this.pipelineSpec = pipelineSpec;
    }

    public io.nop.stream.runtime.rpc.RemotePipelineSpec getPipelineSpec() {
        return pipelineSpec;
    }

    /**
     * Shared filesystem path of the
     * {@code LocalFileCheckpointStorage} directory. Passed in every
     * {@link TaskDeploymentDescriptor} so a recovery-deployed TaskManager can
     * restore operator state from the same path.
     */
    public void setCheckpointStoragePath(String checkpointStoragePath) {
        this.checkpointStoragePath = checkpointStoragePath;
    }

    /**
     * Returns a serializable snapshot of the current job status
     * plus the captured failure cause. Satisfies
     * {@link IStreamCoordinatorRpcService#getJobStatus()} so that local callers
     * and cross-JVM callers observe the same contract.
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
}
