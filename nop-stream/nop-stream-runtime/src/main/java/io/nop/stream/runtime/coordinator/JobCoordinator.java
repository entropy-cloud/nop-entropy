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
    /**
     * G52 / AR-01: default per-task aliveness timeout (a task whose recorded
     * liveness signal is older than this is considered stalled).
     */
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

    /**
     * ST-8 (plan 357 Phase 3): assignment-planning collaborator (pure move of the
     * coordinator's assignment private method group — materialization, RPC fan-out,
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
     * G52 / AR-01: per-subtask liveness tracking. Key =
     * "{vertexId}/{subtaskIndex}".
     * Values are the latest known task-aliveness timestamp (updated by
     * {@link #reportNodeTaskLiveness} / {@link #reportTaskStatus}).
     *
     * <p>Semantics (AR-01 fix): the value is the <b>task aliveness</b> signal,
     * decoupled from data progress — MIDDLE/SINK report the task thread's
     * loop activity (fresh while the loop cycles, data or idle), SOURCE/
     * SELF_CONTAINED report the TaskManager wall clock. A COMPLETED report
     * <b>removes</b> the entry so the task is permanently excluded from stall
     * detection ({@link #detectFailures} gives a task with no record the
     * benefit of the doubt).
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

    /**
     * Items 28+31 (D3): stall-triggered recovery counter. liveness-stall
     * recoveries draw from this SEPARATE budget so that stall-induced recovery
     * storms (historically: data-plane jam aging every task's liveness) can
     * never starve the real-failure budget ({@link #restartCount}/
     * {@link #maxRestarts}) — after the jam-era defect, a real node kill must
     * still be recoverable even if stall recoveries already fired. Exceeding
     * the stall cap still fails the job (persistent stall is a terminal
     * defect; bounded retries preserved).
     */
    private final java.util.concurrent.atomic.AtomicInteger stallRestartCount =
            new java.util.concurrent.atomic.AtomicInteger(0);

    /** Items 28+31 (D3): max stall-triggered recoveries before the job is marked FAILED (default 3). */
    private volatile int maxStallRestarts = 3;

    /**
     * Items 28+31 (D3): cooldown window for stall-triggered recoveries. A
     * stall recovery request arriving within this window of the previous
     * stall recovery is skipped with an observable WARN (the periodic
     * failure detector re-fires naturally after the cooldown) — prevents a
     * hot-loop of stall recoveries burning the stall budget in seconds.
     */
    private volatile long stallRecoveryCooldownMs = 30_000L;

    /** Items 28+31 (D3): wall-clock ms of the last stall-triggered recovery (0 = none yet). */
    private volatile long lastStallRecoveryAt = 0L;

    /**
     * P1 hardening: mutual-exclusion monitor for the recovery critical section.
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
     * error mid-fan-out is a transient P2 inconsistency cleaned by the next recovery).
     *
     * <p>Reentrant so {@link #assignTasks()} (which also acquires the lock for the
     * standalone-assignment path) can be reused safely.
     */
    private final ReentrantLock recoveryLock = new ReentrantLock();

    /**
     * P1 hardening: dedup flag for the {@code globalRecovery} trigger path.
     *
     * <p>Two concurrent sources (failure-detector thread + RPC FAILED report) can
     * fire {@link #requestRecovery()} for the <em>same</em> failure event. Without
     * dedup, both would reach {@link #globalRecovery()} and — depending on thread
     * scheduling — both complete the full rotate/clear/assign sequence, bumping
     * {@code restartCount} twice and producing duplicate attemptIds. The previous
     * epoch-snapshot guard (snapshot taken before lock acquisition) was
     * <strong>non-deterministic</strong>: if the loser thread was scheduled such
     * that it snapshotted the fencing epoch AFTER the winner had already completed
     * its full critical section and released the lock, the snapshot matched the
     * current epoch, the guard did not fire, and the loser performed a redundant
     * full recovery.
     *
     * <p>This flag fixes that by serializing callers at the <em>trigger boundary</em>
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

    /** G56: cause captured by {@link #failJob(Throwable)}; null until FAILED. */
    private volatile Throwable jobFailureCause;

    /**
     * Stage 42 Phase 0: remote-deploy mode toggle. When {@code true},
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
     * Stage 42 Phase 0: the {@link JobGraph} used to build
     * {@link TaskDeploymentDescriptor}s in remote-deploy mode. Required when
     * {@link #remoteDeployMode} is {@code true}; ignored otherwise.
     */
    private volatile JobGraph jobGraph;

    /**
     * Item 14 (composite-scenario distributed): serializable pipeline declaration
     * shipped in the deployment descriptors INSTEAD of the (non-serializable)
     * compiled {@link #jobGraph} when the pipeline is XDSL-declared. Each
     * TaskManager rebuilds an identical graph locally (see
     * {@link io.nop.stream.runtime.rpc.RemotePipelineSpec}). When non-null, the
     * descriptors carry the spec; {@link #jobGraph} is still required for the
     * coordinator's own fail-fast/plan consistency.
     */
    private volatile io.nop.stream.runtime.rpc.RemotePipelineSpec pipelineSpec;

    /**
     * Stage 42 Phase 0: shared filesystem path of the
     * {@code LocalFileCheckpointStorage} directory. Passed in every
     * {@link TaskDeploymentDescriptor} so a recovery-deployed TaskManager can
     * restore operator state from the same path. Null for a fresh job.
     */
    private volatile String checkpointStoragePath;

    /** Optional periodic checkpoint driver (item 14: launch-path checkpoint scheduling). */
    private volatile java.util.concurrent.ScheduledExecutorService periodicCheckpointScheduler;

    /** Whether {@link #startPeriodicCheckpoints(long)} has been called and not stopped. */
    private volatile boolean periodicCheckpointsStarted;

    /**
     * Item 16 (P-REQ-2): job-level event bus. Always carries the built-in
     * logging listener; also injected into the {@link CheckpointCoordinator}
     * so checkpoint progress events reach the job's listeners. The engine
     * recovery meter (P-REQ-1 engine layer) is updated from the same real
     * lifecycle paths that fire events.
     */
    private final io.nop.stream.runtime.event.StreamJobEventBus jobEventBus =
            new io.nop.stream.runtime.event.StreamJobEventBus();

    /**
     * Item 16 (P-REQ-7): logical health state machine, driven by this
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
        this.failureDetector = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jc-failure-detector-" + jobId);
            t.setDaemon(true);
            return t;
        });
        this.running = false;

        // Item 16 (P-REQ-2): built-in logging listener + share the bus with the
        // checkpoint coordinator so checkpoint events reach job listeners.
        this.jobEventBus.addListener(new io.nop.stream.runtime.event.LoggingJobEventListener());
        if (this.checkpointCoordinator != null) {
            this.checkpointCoordinator.setJobEventBus(this.jobEventBus);
        }

        // Item 16 (P-REQ-7): health state machine wiring.
        // (a) entering DEGRADED is itself a job event (alert routing input,
        //     cross-JVM log evidence);
        // (b) a durable checkpoint completion (fired by the real
        //     CheckpointCoordinator completion path on this bus) heals
        //     DEGRADED -> RUNNING.
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
     * Item 16 (P-REQ-7): registers a health-state listener (transitions of
     * the logical health machine). Listener failures are logged by the
     * machine and never break the control path.
     */
    public void addHealthListener(io.nop.stream.runtime.health.JobHealthListener listener) {
        health.addListener(listener);
    }

    /** Item 16 (P-REQ-7): current logical health state. */
    public io.nop.stream.runtime.health.StreamJobHealth getHealth() {
        return health.getCurrent();
    }

    /**
     * Item 16 (P-REQ-2): registers a job lifecycle/progress event listener.
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

            // Cross-JVM fencing sync (Stage 42 multi-JVM remediation): every other
            // activation path pushes the fencing epoch to the TaskManagers before any
            // deployTask is issued — rotateFencingEpochCoreLocked does it on
            // recovery/HA-activation, and the in-process executors
            // (RpcDistributedExecutor / EmbeddedDistributedExecutor) pre-sync each TM
            // via tm.updateFencingToken before coordinator.start(). The non-HA start()
            // path is the lone omission: it derived epoch=1 and registered, but never
            // told the TMs. TMs boot at currentFencingEpoch=0, so the very first
            // assignTasks() -> deployTask(descriptor, 1) RPC was rejected with
            // ERR_STREAM_FENCING_TOKEN_MISMATCH (expected=0, actual=1), reported FAILED,
            // and triggered a rapid globalRecovery loop. Mirror the recovery-path push
            // here so TMs are at `epoch` before the caller issues assignTasks().
            // Safe for the in-process executors: they pre-sync TMs to the same epoch, so
            // updateFencingToken is a no-op there (old==new); and they call start()
            // AFTER setFencingEpoch, so `epoch` equals the executor's value.
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

        // Item 14: stop the periodic checkpoint driver before the checkpoint
        // coordinator shuts down (no triggers against a shut-down coordinator).
        stopPeriodicCheckpoints();

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
        if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.CANCELED) {
            // Idempotence guard (documented no-op, observable via WARN): a
            // job already in a terminal state never re-fails. This also keeps
            // the P-REQ-7 health machine free of illegal terminal->FAILED
            // transitions from late failure reports racing a terminate.
            LOG.warn("failJob ignored for job {}: already terminal ({})", jobId, jobStatus);
            return;
        }
        this.jobFailureCause = cause;
        this.jobStatus = JobStatus.FAILED;
        this.active = false;
        LOG.error("Job {} FAILED (cause={})", jobId, cause == null ? "unknown" : cause.toString(), cause);
        health.onFailJob(cause == null ? "unknown" : cause.toString());
        // Item 16 (P-REQ-2): job failure event with cause (alert routing input).
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
     * assignment over {@link ClusterRegistry#getActiveNodes()} (the legacy LOCAL path).
     *
     * <p>For each assignment:
     * <ol>
     *   <li>Records the assignment in the ClusterRegistry (runtime consistency view)</li>
     *   <li>Sends a {@link TaskAssignment} via the task RPC service
     *       ({@link IStreamTaskRpcService#receiveAssignment}) — <em>in-process mode</em>;
     *       OR sends a {@link TaskDeploymentDescriptor} via
     *       {@link IStreamTaskRpcService#deployTask} — <em>Stage 42 remote-deploy mode</em>.
     *       In remote-deploy mode {@code receiveAssignment} is NOT called — the descriptor
     *       is self-contained.</li>
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

        // Stage 42 Phase 0: remote-deploy mode requires the JobGraph to build
        // per-subtask deployment descriptors. Fail-fast (no silent skip) when
        // the mode is active but the JobGraph was not injected.
        if (remoteDeployMode && jobGraph == null) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "remoteDeployMode is active but no JobGraph was injected on JobCoordinator "
                            + coordinatorId + ". Call setJobGraph(...) before assignTasks().");
        }

        // P1 hardening: the in-memory materialization (cluster registry update +
        // taskAssignmentMap put) is performed under the recovery lock so it cannot
        // interleave with a concurrent recovery driver. The RPC fan-out is executed
        // AFTER the lock is released (no blocking IO under the lock).
        List<AssignmentPlanner.AssignmentDispatch> dispatches;
        recoveryLock.lock();
        try {
            dispatches = assignmentPlanner.prepareAssignmentsLocked(
                    remoteDeployMode, jobGraph, pipelineSpec, checkpointStoragePath);
        } finally {
            recoveryLock.unlock();
        }
        assignmentPlanner.executeAssignmentFanOut(dispatches);
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
     * <p>Item 14 (composite-scenario distributed): the barrier RPC is fanned out to
     * <strong>every node that currently hosts an assigned subtask</strong>, not just
     * the nodes hosting source vertices. Reason: the receiving
     * {@code TaskManager.triggerCheckpoint} registers the in-flight epoch on each
     * running task's {@code CheckpointBarrierTracker}; only source-operator tasks
     * additionally inject the barrier (the tracker's head-operator check is
     * source-aware). A task on a node that never receives the trigger RPC has no
     * in-flight epoch registered, so its operators' barrier-driven snapshots are
     * dropped by the tracker ("no matching in-flight epoch") and the checkpoint can
     * never complete — with the pre-item-14 source-only fan-out this was invisible
     * because the remote path never wired trackers at all. Registering the epoch on
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
        // G24/G25: a standby coordinator must never trigger checkpoints.
        if (!active) {
            LOG.warn("JobCoordinator in STANDBY (not leader), cannot trigger checkpoint for job {}", jobId);
            return null;
        }
        // Item 14 (composite-scenario distributed): a recovery is pending or
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
     * <p>Updates per-subtask liveness on every report (G52 / AR-01: a COMPLETED
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
        long now = CoreMetrics.currentTimeMillis();
        TaskStatusReport.TerminalState state = report.getTerminalState();
        if (state == TaskStatusReport.TerminalState.COMPLETED) {
            // G52 / AR-01: a completed task must be excluded from stall
            // detection. Remove its liveness entry so detectFailures'
            // benefit-of-the-doubt branch (no record) never flags it as
            // stalled. The task has already left the TaskManager's
            // runningTasks set (RunningTask.run() finally), so no further
            // heartbeats will re-add it.
            subtaskLiveness.remove(livenessKey);
        } else {
            // G52 / AR-01: any other terminal report (FAILED) is an
            // aliveness event in its own right — record the report arrival
            // time (not the possibly-stale data-progress timestamp) so the
            // heartbeat-gap recovery mechanism (task left runningTasks →
            // no more heartbeats → liveness ages → stall detection) starts
            // from a fresh baseline.
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
                    // P1 hardening: route through requestRecovery() so a concurrent
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
     * G52 / AR-01: per-node liveness piggybacked on the heartbeat. Updates the
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
            // Monotonic max via atomic merge: a getOrDefault→compare→put sequence
            // is not atomic and an interleaved delivery could overwrite a newer
            // timestamp with an older one, sending liveness backwards and causing
            // spurious stall detection.
            subtaskLiveness.merge(livenessKey, p.getLastProgressTime(), Math::max);
        }
    }

    /**
     * Returns the current CheckpointCoordinator for inspection.
     */
    public CheckpointCoordinator getCheckpointCoordinator() {
        return checkpointCoordinator;
    }

    /**
     * Item 14 (composite-scenario distributed): starts the launch-path periodic
     * checkpoint driver. Each tick calls {@link #triggerCheckpoint()}, which both
     * triggers a {@link PendingCheckpoint} on the {@link CheckpointCoordinator}
     * AND fans the barrier RPC out to all assigned nodes — this is the
     * "startCheckpointScheduler 或等价机制" wiring for the RPC-distributed form:
     * {@code CheckpointCoordinator.startCheckpointScheduler()} alone does NOT
     * deliver barriers (it has no RPC view), so the JobCoordinator-level driver
     * is the equivalent mechanism.
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
            periodicCheckpointScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "jc-periodic-checkpoint-" + jobId);
                t.setDaemon(true);
                return t;
            });
            periodicCheckpointScheduler.scheduleWithFixedDelay(() -> {
                try {
                    PendingCheckpoint pending = triggerCheckpoint();
                    if (pending == null) {
                        LOG.debug("Periodic checkpoint trigger skipped/rejected for job {}", jobId);
                    }
                } catch (Exception e) {
                    // Observable failure (not swallowed): the next tick retries; the
                    // CheckpointCoordinator's own consecutive-failure accounting
                    // surfaces sustained failure via its metrics.
                    LOG.warn("Periodic checkpoint trigger failed for job {}", jobId, e);
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
     * Item 14 (composite-scenario distributed): registers the distributed
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
     * Checks ClusterRegistry node leases AND per-task liveness (G52). If any assigned
     * node has expired, OR if any task's recorded aliveness timestamp is
     * older than
     * {@link #taskTimeoutMs}, triggers global recovery.
     *
     * <p>G52 / AR-01: the per-task liveness values are the task-aliveness
     * signal (decoupled from data progress — see {@link #subtaskLiveness}).
     * Healthy idle tasks keep fresh values (MIDDLE/SINK loop activity ticks
     * every idle cycle; SOURCE/SELF_CONTAINED report the TM wall clock every
     * heartbeat), completed tasks have their entry removed by
     * {@link #reportTaskStatus}, so only a genuinely hung task (loop stopped
     * ticking) or a task whose heartbeats have stopped (slot freed without a
     * terminal report) falls behind the cutoff.
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

            // G52 / AR-01: per-task aliveness check. A task whose recorded
            // aliveness timestamp is older than taskTimeoutMs is considered
            // stalled (node alive but task hung / heartbeats stopped). The
            // recorded values are the task-aliveness signal (see
            // subtaskLiveness), so idle/completed tasks never fall behind.
            boolean taskStallDetected = false;
            long now = CoreMetrics.currentTimeMillis();
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
                // P1 hardening: route through requestRecovery() so concurrent triggers
                // from the FAILED-report RPC path are deduped via the recoveryPending CAS.
                //
                // Items 28+31 (D3): the trigger CAUSE selects the recovery budget —
                // node-lease expiry is a REAL failure (draws from maxRestarts);
                // a pure liveness stall (node alive) draws from the separate
                // stall budget with cooldown so stall storms cannot starve
                // real-failure recovery. Both present → classified as real
                // failure (node loss dominates: the stall is a consequence).
                requestRecovery(nodeFailureDetected ? RecoveryCause.NODE_FAILURE : RecoveryCause.TASK_STALL);
            }
        } catch (Exception e) {
            LOG.error("Error during failure detection for job {}", jobId, e);
        }
    }

    /**
     * P1 hardening: trigger entry point for global recovery with concurrent-call
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
     * fail and short-circuit with an observable WARN (No-Silent-No-Op). The flag
     * is cleared at the END of {@code globalRecovery()}'s locked section (in
     * {@code finally}), so the CAS window stays closed for the recovery's entire
     * duration — a mid-recovery redundant trigger cannot squeeze through. A
     * trigger firing AFTER the in-flight recovery completes re-arms the flag and
     * runs a fresh recovery; globalRecovery is global/idempotent so a redundant
     * post-completion trigger is wasteful but not corrupting.
     *
     * <p><strong>Why a CAS flag, not the previous epoch-snapshot guard:</strong>
     * the snapshot guard's correctness depended on the loser thread snapshotting
     * {@code fencingEpoch} BEFORE the winner rotated it inside the critical section.
     * That ordering is NOT enforced by {@code startLatch}-style test harnesses (and
     * is not guaranteed by the JVM scheduler in production either): if the loser
     * was scheduled such that it snapshotted AFTER the winner had already completed
     * and released the lock, the snapshot matched the current epoch, the guard did
     * not fire, and the loser performed a redundant full recovery — bumping
     * {@code restartCount} twice and producing duplicate attemptIds. The CAS flag
     * has no such timing dependency: the winner is determined atomically at the
     * trigger boundary, not at lock-acquisition time.
     */
    public void requestRecovery() {
        requestRecovery(RecoveryCause.OTHER);
    }

    /**
     * Items 28+31 (D3): cause-carrying trigger entry point. The cause selects
     * which recovery budget a recovery draws from:
     * <ul>
     *   <li>{@code NODE_FAILURE} / {@code OTHER} (incl. FAILED reports and the
     *       legacy no-cause entry) → the real-failure budget
     *       ({@link #restartCount}/{@link #maxRestarts}, semantics unchanged);</li>
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
        globalRecovery(cause == RecoveryCause.TASK_STALL);
    }

    /**
     * Items 28+31 (D3): classification of a recovery trigger. Real failures
     * (node lease expiry, FAILED reports, administrative/legacy entries) share
     * the {@link #maxRestarts} budget; liveness-stall detections draw from a
     * separate budget so they can never consume the real-failure recovery
     * capacity (exercise evidence: jam-induced taskStall recoveries exhausted
     * the global cap, after which REAL node kills could never recover).
     */
    public enum RecoveryCause {
        NODE_FAILURE, TASK_STALL, OTHER
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
        globalRecovery(false);
    }

    /**
     * Items 28+31 (D3): budget-split form of {@link #globalRecovery()}. A
     * stall-triggered recovery ({@code stallTriggered=true}) draws from the
     * separate stall budget instead of the real-failure budget; everything
     * else (epoch rotation, pending-checkpoint abort, reassignment, fencing)
     * is IDENTICAL for both causes — the fencing invariant holds regardless
     * of why the recovery fires.
     */
    public void globalRecovery(boolean stallTriggered) {
        // Item 16 (P-REQ-1/2/7): recovery meter + health transition + event at
        // the real recovery path. The sequence number is the TOTAL recovery
        // count (real + stall) so events/health stay monotonic across pools.
        int totalBefore = restartCount.get() + stallRestartCount.get();
        io.nop.stream.runtime.metrics.EngineMetrics.forJob(jobId).recovery();
        health.onRecoveryStarted(totalBefore + 1);
        jobEventBus.fire(StreamJobEvent.simple(jobId,
                io.nop.stream.runtime.event.StreamJobEvent.EventType.RECOVERY_STARTED,
                "restart-" + (totalBefore + 1)));
        List<AssignmentPlanner.AssignmentDispatch> dispatches = Collections.emptyList();
        recoveryLock.lock();
        try {
            // G56: global restart strategy. The counter is incremented only here
            // (Stage 27 scoped restart will need its own per-region counter, since
            // scoped restart does not flow through globalRecovery).
            //
            // Items 28+31 (D3): WHICH counter depends on the trigger cause —
            // stall-triggered recoveries draw from the stall budget and can
            // never consume the real-failure budget (and vice versa).
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

            // G24/G25 / Stage 39 fencing (Decision 1): a single monotonic long epoch
            // encodes both leadership switch and same-leader recovery.
            //  - HA mode: rotate the recoveryGen low-order component, keep the leaderEpoch
            //    component unchanged (same leader). The full long epoch still rotates and
            //    is pushed to all TaskManagers so stale same-leader tasks are fenced. The
            //    leaderEpoch component only rotates on leadership switch.
            //  - Non-HA mode: leaderEpoch component is 0, so fencing epoch == recoveryGen
            //    (Decision 3, zero regression).
            LeaderEpoch leadership = this.currentLeadership;
            long leaderEpochValue = leadership != null ? leadership.getEpoch() : 0L;
            long newGen = recoveryGen.incrementAndGet();
            long newEpoch = deriveHaFencingEpoch(leaderEpochValue, newGen);

            // G32: same-leader recovery does NOT rebuild from storage — the in-memory
            // latestCompletedCheckpoint survives within the same JVM. Only the
            // leadership-grant path (activateAsLeader) rebuilds from storage.
            rotateFencingEpochCoreLocked(newEpoch, false);

            // Item 14 (composite-scenario distributed): abort every checkpoint that
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
        } finally {
            // P1 hardening: clear the dedup flag at the END (still under the lock)
            // so the CAS window in requestRecovery stays closed for the ENTIRE
            // duration of this recovery. Any redundant requestRecovery that arrives
            // while this recovery is in flight observes recoveryPending=true and its
            // CAS(false→true) fails, so it short-circuits. We only re-arm the flag
            // once this recovery is fully done — see the extension note below.
            //
            // Clearing in finally (not at the start) closes the race where a second
            // caller's CAS would succeed between "globalRecovery clears pending" and
            // "globalRecovery finishes", queuing a redundant second recovery. With
            // end-clear, the only way a second requestRecovery proceeds is if it
            // fires AFTER this method returns — which is a legitimate, distinct
            // trigger (globalRecovery is global/idempotent, so a redundant trigger
            // after completion is wasteful but not corrupting, and the failure-
            // detector's periodicity bounds how long a true gap can go unhandled).
            recoveryLock.unlock();
        }

        // Item 14: the dedup/suppression flag stays armed THROUGH the assignment
        // fan-out. A periodic checkpoint trigger that lands between the locked
        // section and the fan-out would insert its triggerCheckpoint RPC row
        // BEFORE the new deployment rows on the task topics — the TaskManager
        // would then register the in-flight epoch on pre-replacement attempts
        // (or none at all), and the new attempts' operator barrier ACKs would be
        // dropped by their trackers ("no matching in-flight epoch"), dooming that
        // checkpoint. Keeping recoveryPending=true until every deployTask RPC row
        // is issued (triggerCheckpoint rejects while it is armed) makes the next
        // fresh trigger land strictly AFTER the deployment rows in topic order.
        try {
            assignmentPlanner.executeAssignmentFanOut(dispatches);
        } finally {
            recoveryPending.set(false);
        }
        // Item 16 (P-REQ-7): a completed recovery always carries its failure
        // trace (restart count > 0) — post-recovery health is DEGRADED until
        // the next durable checkpoint heals it. Items 28+31 (D3): the trace
        // carries the TOTAL recovery count across both budget pools.
        int totalAfter = restartCount.get() + stallRestartCount.get();
        health.onRecoveryCompleted(totalAfter);
        jobEventBus.fire(StreamJobEvent.simple(jobId,
                io.nop.stream.runtime.event.StreamJobEvent.EventType.RECOVERY_COMPLETED,
                "restarts=" + totalAfter));
    }

    /**
     * Item 16 (P-REQ-1 engine layer): registers the cluster-level active-node
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
     * G24/G25 / Stage 39 / P1 hardening: shared fencing-epoch rotation + control-plane
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
     * <p><strong>G32 (Stage 46) failover-safe rebuild</strong>: when {@code restoreFromStorage}
     * is {@code true} (the {@link #activateAsLeader} path) AND the in-memory
     * {@code latestCompletedCheckpoint} is {@code null} (the fresh-coordinator-JVM case),
     * this method calls {@link CheckpointCoordinator#restoreFromCheckpoint()} to reload
     * the latest durable epoch from {@link ICheckpointStorage}. A storage failure during
     * this rebuild is a correctness risk (the new leader cannot safely resume), so it
     * <strong>fails loud</strong> (throws {@link StreamException}) rather than silently
     * continuing (plan guide #24 — no silent no-op).
     *
     * <p>When {@code restoreFromStorage} is {@code false} (the {@link #globalRecovery()}
     * same-leader path), the rebuild is skipped: the in-memory view is already alive and
     * an extra DB round-trip per recovery is unnecessary (the field survives same-leader
     * restarts within one JVM).
     *
     * @param newEpoch           the rotated fencing epoch
     * @param restoreFromStorage {@code true} on leadership grant (rebuild from storage
     *                           when in-memory view is empty); {@code false} on
     *                           same-leader recovery
     */
    private void rotateFencingEpochCoreLocked(long newEpoch, boolean restoreFromStorage) {
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

        if (restoreFromStorage) {
            // G32: failover-safe rebuild. On a fresh coordinator JVM (leadership
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
                    // Fail-loud (guide #24): storage unreachable during failover rebuild
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

        LOG.info("Fencing epoch rotated for job {} (epoch={}, restoreFromStorage={})",
                jobId, newEpoch, restoreFromStorage);
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
        // Mark active BEFORE rebuilding so the internal assignment materialization
        // (prepareAssignmentsLocked) observes the active state.
        this.active = true;

        long token = deriveHaFencingEpoch(epoch.getEpoch(), 0);

        // P1 hardening: leadership-grant rebuild runs under the same recovery lock as
        // globalRecovery, so a concurrent same-leader recovery cannot interleave with
        // the epoch rotation / working-set clear. The RPC fan-out executes after the
        // lock is released (no blocking IO under the lock).
        List<AssignmentPlanner.AssignmentDispatch> dispatches;
        recoveryLock.lock();
        try {
            // G32: on leadership grant, rebuild the latestCompletedCheckpoint view
            // from durable storage (failover-safe). A new coordinator JVM has a null
            // in-memory view; restoreFromCheckpoint() reloads the latest durable epoch.
            rotateFencingEpochCoreLocked(token, true);
            // Item 34 (HA failover takeover): seed the per-subtask attempt counters
            // from the registry's persisted attempt history BEFORE materializing the
            // assignments, so the fresh coordinator's re-issued attempt numbers
            // strictly continue the old leader's persisted history instead of
            // colliding on the (job_id, vertex_id, subtask_index, attempt_number)
            // primary key and aborting the become-leader listener.
            seedAttemptCountersFromRegistryLocked();
            dispatches = assignmentPlanner.prepareAssignmentsLocked(
                    remoteDeployMode, jobGraph, pipelineSpec, checkpointStoragePath);
        } finally {
            recoveryLock.unlock();
        }
        assignmentPlanner.executeAssignmentFanOut(dispatches);
    }

    /**
     * Item 34 (HA failover takeover): seeds the per-subtask attempt counters from the
     * registry's persisted attempt history before leadership activation materializes
     * assignments.
     *
     * <p>Defect being fixed: on a fresh coordinator JVM the in-memory
     * {@link #attemptCounters} start empty, so {@link AssignmentPlanner#prepareAssignmentsLocked}
     * re-issues attempt numbers from 1. When the shared registry still holds the old
     * leader's rows for the same (jobId, vertexId, subtaskIndex) with
     * attempt_number=1, the plain INSERT in {@code ClusterRegistry.assignTask} violates
     * the (job_id, vertex_id, subtask_index, attempt_number) primary key, the
     * become-leader listener aborts (the platform elector swallows the exception with
     * no retry and no degradation), and the job freezes half-activated: lease held,
     * epoch rotated, active=true, but no assignments and no further checkpoints.
     *
     * <p>Seeding rule: for every (vertexId, subtaskIndex) enumerated by the deployment
     * plan, read {@link ClusterRegistry#getAttemptHistory} and raise the in-memory
     * counter to max(current, historyMax). No history rows → counter stays absent →
     * the next assignment uses 1 (byte-identical to the fresh-job behavior); an
     * already-larger in-memory value is never lowered. Reading on EVERY activation
     * (not only when the counter key is missing) is the adjudicated warm-path policy:
     * it also corrects a stale in-memory counter after a leadership ping-pong between
     * distinct coordinator instances sharing one persistent registry, and costs one
     * bounded read per subtask per leadership activation — the same order of registry
     * round-trips the immediately-following assignment INSERTs already perform.
     *
     * <p>Failure semantics (adjudicated): a registry read failure during seeding
     * de-activates the coordinator ({@code active=false}, leadership state cleared)
     * and then rethrows — after the platform elector swallows the listener exception
     * the node is left as an explicit STANDBY, never an unrecorded frozen half-active
     * leader holding {@code active=true} with no assignments (guide #24).
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
            // De-activate BEFORE rethrowing (adjudicated failure semantics): after the
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
        if (!running) {
            LOG.warn("JobCoordinator not running, cannot terminate job {}", jobId);
            return;
        }
        // G24/G25: a standby coordinator must never act on termination requests —
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
        // G56 / Stage 28: surface the CANCELED terminal transition explicitly
        // (closes the known gap recorded in JobStatus.java — terminateCancel
        // previously only called stop() and left jobStatus at RUNNING).
        // Item 16 (P-REQ-7): health transitions FIRST — a stop during an
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
        // Stage 28: CheckpointType aligned to checkpoint-design.md §7.3
        // (TERMINAL_SAVEPOINT for DRAIN/SUSPEND).
        terminateWithTerminalSavepoint("DRAIN", CheckpointType.TERMINAL_SAVEPOINT,
                "final checkpoint", "drain", true);
    }

    private void terminateSuspend() {
        terminateWithTerminalSavepoint("SUSPEND", CheckpointType.TERMINAL_SAVEPOINT,
                "savepoint", "suspend", true);
    }

    private void terminateExportSavepoint() {
        terminateWithTerminalSavepoint("EXPORT_SAVEPOINT", CheckpointType.EXPORTED_SAVEPOINT,
                "export savepoint", null, false);
    }

    /**
     * Phase 3 (Plan 2026-09-03-1951-1, F-B): single parameterized implementation for
     * the former terminateDrain / terminateSuspend / terminateExportSavepoint triplet
     * (~90% literal clone: tryTrigger → barrier → sendBarrierToAllTaskManagers →
     * future.get(terminationCheckpointTimeoutMs) → log → side effects). The complete
     * difference set is the parameter matrix below — behavior is pinned before and
     * after the convergence by {@code TestJobCoordinatorTerminationMatrix} (identical
     * assertions): CheckpointType, log texts (mode label + snapshot noun), JOB_FINISHED
     * event payload, health transition + stop (terminal modes), and the
     * continues-running semantics of EXPORT_SAVEPOINT.
     *
     * @param mode            mode label for logs and health cause ("DRAIN" etc.)
     * @param checkpointType  the terminal checkpoint type to trigger
     * @param snapshotNoun    log noun ("final checkpoint" / "savepoint" / "export savepoint")
     * @param finishedPayload JOB_FINISHED event payload; {@code null} = no event (export)
     * @param terminal        true = health.onFinished + JOB_FINISHED + stop();
     *                        false = job keeps running after the export
     */
    private void terminateWithTerminalSavepoint(String mode, CheckpointType checkpointType,
                                                String snapshotNoun, String finishedPayload,
                                                boolean terminal) {
        LOG.info("{}: triggering {} for job {}", mode, snapshotNoun, jobId);
        try {
            PendingCheckpoint pending = checkpointCoordinator.tryTriggerPendingCheckpoint(checkpointType);
            if (pending != null) {
                CheckpointBarrier barrier = new CheckpointBarrier(
                        pending.getCheckpointId(),
                        pending.getTriggerTimestamp(),
                        pending.getCheckpointType());
                sendBarrierToAllTaskManagers(barrier);

                pending.getCompletableFuture()
                        .get(terminationCheckpointTimeoutMs, TimeUnit.MILLISECONDS);
                if (terminal) {
                    LOG.info("{}: {} {} completed for job {}",
                            mode, snapshotNoun, pending.getCheckpointId(), jobId);
                } else {
                    LOG.info("{}: {} {} exported for job {}. Job continues running.",
                            mode, snapshotNoun, pending.getCheckpointId(), jobId);
                }
            }
        } catch (Exception e) {
            LOG.error("{}: failed to complete {} for job {}", mode, snapshotNoun, jobId, e);
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
        // G24/G25: a standby coordinator must never abort a (potentially shared)
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
        checkpointCoordinator.setAbortHandler((checkpointId, reason) -> {
            if (!active) {
                // A standby coordinator must not issue cancelTask RPCs.
                LOG.warn("Ignoring distributed abort({}) for job {}: coordinator not active", checkpointId, jobId);
                return;
            }
            // Item 14 (composite-scenario distributed): a checkpoint TIMEOUT is a
            // routine back-pressure event — the epoch is discarded, tasks keep
            // running, and the next periodic trigger retries. Cancelling all tasks
            // on a timeout turned every slow recovery window (e.g. the ~15s
            // lease-expiry detection gap after a node kill) into a
            // cancel/recover cascade: each timeout abort cancelled the redeployed
            // tasks, the failure detector saw them die, fired another recovery,
            // and the restart cap was exhausted while prepared sink data was
            // aborted away. Only a SNAPSHOT-FAILURE abort (possibly inconsistent
            // task state — the original design intent of this control channel)
            // cancels the assigned tasks.
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
                        // F-C (roadmap item 27): cancelTask carries this coordinator's
                        // current fencing epoch — a stale coordinator's cancel is
                        // rejected at the TaskManager boundary (typed mismatch).
                        rpc.cancelTask(ta.getJobId(), ta.getVertexId(), ta.getSubtaskIndex(),
                                getFencingEpoch());
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

    /**
     * G52 monotonic-max semantics: the recorded liveness value for a key.
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
     * G52 / AR-01: per-task aliveness timeout. A task whose recorded aliveness
     * timestamp (see {@link #subtaskLiveness}) is older than this is considered
     * stalled and triggers recovery on the next {@link #detectFailures()} tick.
     * The value must stay above the TaskManager heartbeat interval (5s) so a
     * healthy idle task (heartbeat-refreshed) never falls behind the cutoff.
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
     * Items 28+31 (D3): stall-budget tuning — max stall-triggered recoveries
     * before the job is marked FAILED, and the cooldown window between
     * stall-triggered recoveries.
     */
    public void setMaxStallRestarts(int maxStallRestarts) {
        this.maxStallRestarts = Math.max(0, maxStallRestarts);
    }

    public int getMaxStallRestarts() {
        return maxStallRestarts;
    }

    public void setStallRecoveryCooldownMs(long stallRecoveryCooldownMs) {
        this.stallRecoveryCooldownMs = Math.max(0L, stallRecoveryCooldownMs);
    }

    public long getStallRecoveryCooldownMs() {
        return stallRecoveryCooldownMs;
    }

    /** Items 28+31 (D3): stall-triggered recovery count (separate budget). */
    public int getStallRestartCount() {
        return stallRestartCount.get();
    }

    /** Items 28+31 (D3): total recoveries across both budget pools. */
    public int getTotalRecoveryCount() {
        return restartCount.get() + stallRestartCount.get();
    }

    /**
     * Stage 42 Phase 0: toggles remote-deploy mode. When {@code true},
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
     * Stage 42 Phase 0: injects the {@link JobGraph} used to build
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
     * Item 14: sets the serializable pipeline declaration shipped in the
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
     * Stage 42 Phase 0: shared filesystem path of the
     * {@code LocalFileCheckpointStorage} directory. Passed in every
     * {@link TaskDeploymentDescriptor} so a recovery-deployed TaskManager can
     * restore operator state from the same path.
     */
    public void setCheckpointStoragePath(String checkpointStoragePath) {
        this.checkpointStoragePath = checkpointStoragePath;
    }

    public String getCheckpointStoragePath() {
        return checkpointStoragePath;
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
}
