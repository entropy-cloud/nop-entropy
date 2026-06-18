package io.nop.ai.agent.team.scheduler;

import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.runtime.coordination.IDaemonCoordinator;
import io.nop.ai.agent.runtime.coordination.NoOpDaemonCoordinator;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.NoOpMemberSpawner;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamStatus;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.ai.agent.team.flow.AllMustSucceedReduction;
import io.nop.ai.agent.team.flow.ITaskMemberRouter;
import io.nop.ai.agent.team.flow.MemberDispatchOutcome;
import io.nop.ai.agent.team.flow.MemberDispatchPlan;
import io.nop.ai.agent.team.flow.MemberFanOutDispatcher;
import io.nop.ai.agent.team.flow.NoOpTaskMemberRouter;
import io.nop.ai.agent.team.flow.TeamTaskTopology;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Functional implementation of {@link ITeamTaskSchedulerDaemon} — a
 * continuously running periodic sweep daemon that auto-claims and
 * auto-dispatches dependency-ready team tasks, closing the "unattended
 * multi-agent orchestration" loop (plan 236 / L4-blockedBy-resolution-engine).
 *
 * <p><b>Scheduling</b>: {@link #start} registers a fixed-delay periodic task
 * on the configured {@link IScheduledExecutor} (default cadence); {@link #stop}
 * cancels it. Both are idempotent. The lifecycle / scheduling infrastructure
 * mirrors plan 222's {@code ScheduledRecoveryManager} (idempotent start/stop,
 * {@code scanOnce} single-scan entry, {@code IScheduledExecutor} periodic
 * scheduling, {@code volatile Future} lifecycle handle, NoOp shipped default
 * — zero regression).
 *
 * <p><b>scanOnce algorithm</b> (design 裁定 1 + 2):
 * <ol>
 *   <li>For each target team (configured team-id set, or
 *       {@link ITeamManager#getActiveTeams()} when none configured): load the
 *       team's tasks from {@link ITeamTaskStore}, build a
 *       {@link TeamTaskTopology}, and query
 *       {@link TeamTaskTopology#getReadyTasks()}.</li>
 *   <li>Filter the ready set to {@link TeamTaskStatus#CREATED}. Tasks returned
 *       by the topology in {@link TeamTaskStatus#CLAIMED} (another member is
 *       executing them) are <b>skipped</b> — never claimed, never abandoned
 *       (兑现 Non-Goal「不强占 CLAIMED 任务」, design 裁定 4 关键安全约束).</li>
 *   <li>Resolve a bound member for the team (prefer MEMBER role, fallback any
 *       bound — same strategy as plan 233 {@code TeamTaskFlowOrchestrator}'s
 *       resolveMember). <b>If no member is bound</b>, consult the wired
 *       {@link IMemberSpawner} (plan 237 / L4-auto-spawn-member-agent): a
 *       functional spawner (e.g. {@code DefaultMemberSpawner}) materialises a
 *       fresh member-agent execution based on the team's declarative
 *       {@code TeamMemberSpec.agentModel} and dispatches identically to a
 *       bound member; the shipped {@link NoOpMemberSpawner} default returns
 *       an explicit {@link SpawnMemberResult.Status#NO_SPAWN} so the daemon
 *       falls back to its pre-spawn behaviour (abandon, zero regression).</li>
 *   <li>For each CREATED ready task: CAS-claim via
 *       {@link ITeamTaskStore#claimTask} with the daemon session id
 *       (idempotent — a CAS loss returns empty Optional and the task is
 *       silently skipped, a legitimate concurrency outcome). On a successful
 *       claim, dispatch via {@link IAgentEngine#execute} on the resolved
 *       member agent (synchronous join, identical delegation mechanism to
 *       plan 233 {@code MemberAgentTaskStep}) — or, when no bound member was
 *       resolved, on a spawned member (plan 237). Success transitions the
 *       task CLAIMED → COMPLETED via {@link ITeamTaskStore#completeTask}; a
 *       dispatch failure (unbound member + NoOp spawner / spawned agent threw
 *       / returned non-completed terminal status / completeTask CAS lost)
 *       transitions it CLAIMED → ABANDONED via {@link ITeamTaskStore#abandonTask}
 *       — <b>only</b> for tasks this daemon CAS-claimed, never another
 *       member's CLAIMED task (design 裁定 4).</li>
 * </ol>
 *
 * <p><b>Dependency order is auto-guaranteed</b> by the ready query (design
 * 裁定 2): a task whose {@code blockedBy} are not all COMPLETED is never in
 * the ready set, so it is never dispatched. When its dependencies complete in
 * a prior scan, the next scan's ready query includes it automatically. No
 * runtime blocking, no deadlock surface.
 *
 * <p><b>Per-task failure isolation</b>: a dispatch failure for one task is
 * recorded (the task is abandoned) and does <b>not</b> abort the rest of the
 * scan — mirrors {@code ScheduledRecoveryManager}'s per-session isolation.
 *
 * <p><b>Graceful stop</b> (design 裁定 3): {@link #stop} cancels the periodic
 * schedule but does not interrupt in-progress dispatched tasks (already
 * CLAIMED and executing). After stop, no new tasks are claimed.
 *
 * <p><b>No Silent No-Op</b> (Minimum Rules #24): empty team / no CREATED
 * ready tasks is a legitimate idle state (empty scan result, not an error);
 * but unbound member + NoOp spawner / spawn failure / dispatch failure /
 * completeTask CAS loss are never silently swallowed — the task is honestly
 * abandoned and counted in the scan result.
 *
 * <p><b>The daemon does NOT call
 * {@link io.nop.ai.agent.team.flow.TeamTaskFlowOrchestrator#execute(String)}</b>:
 * that entry rebuilds the whole-team graph on every invocation and
 * short-circuits when any node's CAS claim loses, making it unsuitable for
 * periodic incremental advancement (design 裁定 1).
 *
 * <p><b>Thread safety</b>: {@code start}/{@code stop} are {@code synchronized}
 * and guard a {@code volatile} {@link Future} handle. {@code scanOnce} is
 * stateless w.r.t. the handle and safe for concurrent invocation; per-task
 * dispatch is sequential within a single scan.
 *
 * <p><b>Spawner wiring</b> (plan 237 design 裁定 5): the spawner is injected
 * into the daemon (the consumer), not into {@code DefaultAgentEngine}. The
 * daemon owns its spawner field (null-safe → NoOp shipped default), mirroring
 * the {@code IResourceGuard}→{@code InMemoryTeamManager} wire-at-consumer
 * convention (plan 234). The spawner is only consulted when
 * {@code resolveBoundMember} returns null (bound-member priority, design
 * 裁定 3): a team with a bound member never reaches the spawner.
 *
 * <p>See plan 236 (L4-blockedBy-resolution-engine) and plan 237
 * (L4-auto-spawn-member-agent).
 */
public class TeamTaskSchedulerDaemon implements ITeamTaskSchedulerDaemon {

    private static final Logger LOG = LoggerFactory.getLogger(TeamTaskSchedulerDaemon.class);

    /**
     * Default scan interval (5 seconds). The daemon's role is to advance a
     * team-task DAG in dependency order; a low single-digit-second cadence
     * gives near-immediate advancement when a dependency completes without
     * excessive polling load. Integrators tune via the constructor.
     */
    public static final long DEFAULT_SCAN_INTERVAL_SEC = 5L;

    /**
     * Default daemon session identity used as the {@code claimedBy} /
     * {@code completedBy} / {@code abandonedBy} argument to
     * {@link ITeamTaskStore} state transitions. Records in the task's audit
     * trail that the daemon (not a human-driven tool) drove the transition.
     * The member agent that actually executes the work is identified in the
     * {@link AgentMessageRequest}'s session id / metadata.
     */
    public static final String DEFAULT_DAEMON_SESSION_ID = "team-task-scheduler-daemon";

    /**
     * Default scan-lease duration in milliseconds (plan 242 /
     * {@code L4-cross-process-daemon-coordination}, design 裁定 8):
     * {@code 6 * default scan interval (5s) = 30s}. The lease must be
     * significantly larger than a single scan cycle so the holder can
     * finish a team's scan + dispatch (topology build + claim CAS + agent
     * execute join + complete) without another instance preempting
     * mid-scan, while keeping failover latency bounded (a crashed holder's
     * lease auto-expires within this window).
     */
    public static final long DEFAULT_SCAN_LEASE_MS = 30_000L;

    private final IAgentEngine agentEngine;
    private final ITeamTaskStore taskStore;
    private final ITeamManager teamManager;
    private final IScheduledExecutor scheduledExecutor;
    private final long scanIntervalSec;
    private final String daemonSessionId;
    private final Set<String> targetTeamIds;

    /**
     * Per-instance cross-process scan-lease coordinator (plan 242 /
     * {@code L4-cross-process-daemon-coordination}). Shipped default is
     * {@link NoOpDaemonCoordinator} — single-process deployments are
     * unchanged (every {@code tryAcquireScanLease} returns {@code true} =
     * full scan, zero regression). Integrators opt into multi-instance
     * scan-load coordination by wiring a functional coordinator (e.g.
     * {@code DbDaemonCoordinator}) via {@link #setDaemonCoordinator}.
     *
     * <p>Mutable via {@link #setDaemonCoordinator} so a daemon can be
     * constructed with the default and re-wired later (mirrors the
     * engine's nullable extension-point setter pattern). Reads in
     * {@code scanOnce} go through the field directly (reassigned atomically
     * by the setter).
     */
    private IDaemonCoordinator daemonCoordinator = NoOpDaemonCoordinator.noOp();

    /**
     * Per-instance daemon owner identity used as the {@code ownerId} for
     * scan-lease acquisition (plan 242, design 裁定 7). Each daemon instance
     * must have a <b>unique</b> ownerId so that lease contention between
     * instances is observable: if two instances shared an ownerId, every
     * {@code tryAcquireScanLease} would match the "same owner renew" branch
     * and the lease would never block anyone (coordination would be hollow).
     *
     * <p>Default is {@code "scheduler-daemon-" + UUID.randomUUID()} (mirrors
     * the {@code DefaultAgentEngine.instanceId} UUID convention, with a
     * prefix for observability). Override via constructor / setter for test
     * determinism (fixed ownerId) or deployment customisation
     * (hostname / pod name).
     *
     * <p><b>Deliberately NOT {@link #daemonSessionId}</b>:
     * {@code DEFAULT_DAEMON_SESSION_ID = "team-task-scheduler-daemon"} is a
     * fixed value shared by all instances (used for the task audit fields
     * {@code claimedBy} / {@code completedBy}); reusing it here would
     * collapse all instances into one ownerId and disable coordination.
     */
    private String daemonOwnerId = "scheduler-daemon-" + UUID.randomUUID();

    /**
     * Scan-lease duration in milliseconds (plan 242, design 裁定 8). Tunable
     * via {@link #setScanLeaseMs}; the daemon passes this to every
     * {@link IDaemonCoordinator#tryAcquireScanLease}.
     */
    private long scanLeaseMs = DEFAULT_SCAN_LEASE_MS;

    /**
     * Pluggable member spawner (plan 237 / L4-auto-spawn-member-agent).
     * Consulted only when {@link #resolveBoundMember} returns {@code null}
     * (bound-member priority, design 裁定 3). Shipped default is
     * {@link NoOpMemberSpawner} (returns explicit
     * {@link SpawnMemberResult.Status#NO_SPAWN}, daemon falls back to its
     * pre-spawn abandon behaviour — zero regression). Integrators opt into
     * auto-spawn via the spawner-aware constructor or {@link #setMemberSpawner}.
     *
     * <p>Mutable via {@link #setMemberSpawner} so a daemon can be constructed
     * with the default and re-wired later (mirrors the engine's other nullable
     * extension-point setter patterns). Reads in {@code scanOnce} should
     * therefore go through the field directly (which is reassigned atomically
     * by the setter).
     */
    private IMemberSpawner memberSpawner = NoOpMemberSpawner.noOp();

    /**
     * Pluggable per-task member router (plan 245 / daemon dispatch parity).
     * Consulted at dispatch time for each claimed task to decide which N
     * member targets the task fans out to (bound +/or spawn) + which reduction
     * strategy combines their results. Shipped default is
     * {@link NoOpTaskMemberRouter} (singleton single-member plan — bound
     * priority + spawn fallback, line-for-line identical to the pre-245 daemon
     * single-member dispatch). Integrators opt into multi-member fan-out via
     * the router-aware setter / constructor.
     *
     * <p>Mutable via {@link #setTaskMemberRouter} so a daemon can be built with
     * the default and re-wired later (mirrors the orchestrator's
     * {@code setTaskMemberRouter} and the other Layer 4 extension-point setter
     * patterns). Reads in {@code scanOnce} go through the field directly.
     */
    private ITaskMemberRouter taskMemberRouter = NoOpTaskMemberRouter.noOp();

    /**
     * Dedicated executor for spawn-target {@code supplyAsync} offload (plan 245,
     * reusing plan 243 design 裁定 3). MUST be independent of the
     * {@code commonPool} so spawn workers that synchronously join an engine
     * future (via {@code DefaultMemberSpawner.spawnMember} →
     * {@code engine.execute(req).join()}) do not stall when concurrent spawn
     * targets ≥ commonPool parallelism.
     *
     * <p>Wire-at-consumer: integrators may inject their own executor via
     * {@link #setSpawnStepExecutor}. When not injected, the daemon lazily
     * creates a dedicated bounded daemon-thread pool
     * ({@link #ownedSpawnExecutor}) sized to the spawn concurrency cap; that
     * owned pool is released by {@link #stop()}.
     */
    private Executor spawnStepExecutor;

    /**
     * Tracks the spawn executor pool created by this daemon (when none was
     * injected) so {@link #stop()} can shut it down. {@code null} when an
     * executor was injected or before any spawn-target dispatch has required
     * it.
     */
    private ExecutorService ownedSpawnExecutor;

    /**
     * In-flight fan-out dispatch futures fired during {@code scanOnce} that
     * had not completed synchronously at scan-return time (plan 245 async
     * per-cycle dispatch). The daemon does NOT block on these inside
     * {@code scanOnce}; it tracks them here so callers (tests, graceful
     * shutdown) can await their resolution via
     * {@link #awaitInFlightDispatches(long)}. A future is removed once it
     * settles (the {@code whenComplete} callback removes it).
     */
    private final ConcurrentLinkedQueue<CompletableFuture<MemberDispatchOutcome>> inFlightDispatches =
            new ConcurrentLinkedQueue<>();

    private volatile Future<?> scheduleHandle;

    /**
     * Create a daemon with the default 5s scan interval, the default daemon
     * session id, and no target-team restriction (scans all active teams).
     *
     * @param agentEngine       the member-agent engine used to dispatch claimed
     *                          tasks (non-null)
     * @param taskStore         the team-task store (non-null)
     * @param teamManager       the team manager (non-null)
     * @param scheduledExecutor the scheduler used to register the periodic
     *                          task (non-null)
     */
    public TeamTaskSchedulerDaemon(IAgentEngine agentEngine, ITeamTaskStore taskStore,
                                    ITeamManager teamManager, IScheduledExecutor scheduledExecutor) {
        this(agentEngine, taskStore, teamManager, scheduledExecutor,
                DEFAULT_SCAN_INTERVAL_SEC, DEFAULT_DAEMON_SESSION_ID, null);
    }

    /**
     * Fully-parameterized constructor.
     *
     * @param agentEngine       the member-agent engine used to dispatch claimed
     *                          tasks (non-null)
     * @param taskStore         the team-task store (non-null)
     * @param teamManager       the team manager (non-null)
     * @param scheduledExecutor the scheduler used to register the periodic
     *                          task (non-null)
     * @param scanIntervalSec   the fixed delay between scans, in seconds;
     *                          must be {@code > 0}
     * @param daemonSessionId   the session id recorded as {@code claimedBy} /
     *                          {@code completedBy} / {@code abandonedBy} on
     *                          state transitions driven by this daemon
     *                          (non-null, non-blank)
     * @param targetTeamIds     optional restriction of the scan to a fixed set
     *                          of team ids; {@code null} or empty means scan
     *                          all {@link ITeamManager#getActiveTeams()}
     */
    public TeamTaskSchedulerDaemon(IAgentEngine agentEngine, ITeamTaskStore taskStore,
                                    ITeamManager teamManager, IScheduledExecutor scheduledExecutor,
                                    long scanIntervalSec, String daemonSessionId,
                                    Collection<String> targetTeamIds) {
        this(agentEngine, taskStore, teamManager, scheduledExecutor,
                scanIntervalSec, daemonSessionId, targetTeamIds, null);
    }

    /**
     * Fully-parameterized constructor with explicit member spawner (plan 237).
     *
     * <p>When {@code memberSpawner} is {@code null}, the daemon uses the
     * shipped {@link NoOpMemberSpawner} default (bound-member-only behaviour,
     * zero regression — design 裁定 5). When a functional spawner (e.g.
     * {@code DefaultMemberSpawner}) is supplied, the daemon consults it on
     * the dispatch path whenever {@link #resolveBoundMember} returns
     * {@code null} (bound-member priority, design 裁定 3).
     *
     * @param agentEngine       the member-agent engine used to dispatch claimed
     *                          tasks (non-null)
     * @param taskStore         the team-task store (non-null)
     * @param teamManager       the team manager (non-null)
     * @param scheduledExecutor the scheduler used to register the periodic
     *                          task (non-null)
     * @param scanIntervalSec   the fixed delay between scans, in seconds;
     *                          must be {@code > 0}
     * @param daemonSessionId   the session id recorded as {@code claimedBy} /
     *                          {@code completedBy} / {@code abandonedBy} on
     *                          state transitions driven by this daemon
     *                          (non-null, non-blank)
     * @param targetTeamIds     optional restriction of the scan to a fixed set
     *                          of team ids; {@code null} or empty means scan
     *                          all {@link ITeamManager#getActiveTeams()}
     * @param memberSpawner     optional member spawner consulted when no bound
     *                          member is resolved; {@code null} falls back to
     *                          the shipped {@link NoOpMemberSpawner} default
     */
    public TeamTaskSchedulerDaemon(IAgentEngine agentEngine, ITeamTaskStore taskStore,
                                    ITeamManager teamManager, IScheduledExecutor scheduledExecutor,
                                    long scanIntervalSec, String daemonSessionId,
                                    Collection<String> targetTeamIds,
                                    IMemberSpawner memberSpawner) {
        this.agentEngine = Objects.requireNonNull(agentEngine, "agentEngine");
        this.taskStore = Objects.requireNonNull(taskStore, "taskStore");
        this.teamManager = Objects.requireNonNull(teamManager, "teamManager");
        this.scheduledExecutor = Objects.requireNonNull(scheduledExecutor, "scheduledExecutor");
        if (scanIntervalSec <= 0) {
            throw new NopAiAgentException(
                    "TeamTaskSchedulerDaemon: scanIntervalSec must be > 0 (got " + scanIntervalSec + ")");
        }
        this.scanIntervalSec = scanIntervalSec;
        Objects.requireNonNull(daemonSessionId, "daemonSessionId");
        if (daemonSessionId.isBlank()) {
            throw new NopAiAgentException(
                    "TeamTaskSchedulerDaemon: daemonSessionId must not be blank");
        }
        this.daemonSessionId = daemonSessionId;
        this.targetTeamIds = targetTeamIds != null && !targetTeamIds.isEmpty()
                ? Collections.unmodifiableSet(new HashSet<>(targetTeamIds))
                : Collections.emptySet();
        this.memberSpawner = memberSpawner != null ? memberSpawner : NoOpMemberSpawner.noOp();
    }

    public long getScanIntervalSec() {
        return scanIntervalSec;
    }

    public String getDaemonSessionId() {
        return daemonSessionId;
    }

    /**
     * @return an unmodifiable view of the explicitly-configured target team
     *         ids; empty means "scan all active teams"
     */
    public Set<String> getTargetTeamIds() {
        return targetTeamIds;
    }

    /**
     * Return the member spawner wired into this daemon (plan 237). Never
     * {@code null}: a daemon constructed without an explicit spawner returns
     * the shipped {@link NoOpMemberSpawner} singleton (zero-regression
     * default).
     *
     * @return the wired member spawner (non-null)
     */
    public IMemberSpawner getMemberSpawner() {
        return memberSpawner;
    }

    /**
     * Wire (or re-wire) the member spawner (plan 237 / L4-auto-spawn-member-agent).
     *
     * <p>The spawner is consulted on the dispatch path whenever
     * {@link #resolveBoundMember} returns {@code null} (bound-member priority,
     * design 裁定 3). Passing {@code null} resets to the shipped
     * {@link NoOpMemberSpawner} default (bound-member-only behaviour, zero
     * regression — design 裁定 5). Passing a functional spawner (e.g.
     * {@code DefaultMemberSpawner}) opts the daemon into auto-spawning
     * unbound-member team tasks.
     *
     * <p>Mutable post-construction so a daemon can be built with the default
     * and re-wired later. The read in {@code scanOnce} goes through the field
     * directly, which is reassigned atomically by this setter.
     *
     * @param memberSpawner the spawner to wire; {@code null} falls back to
     *                      {@link NoOpMemberSpawner#noOp()}
     */
    public void setMemberSpawner(IMemberSpawner memberSpawner) {
        this.memberSpawner = memberSpawner != null ? memberSpawner : NoOpMemberSpawner.noOp();
    }

    // ========================================================================
    // Per-task member router + dedicated spawn executor wiring (plan 245)
    // ========================================================================

    /**
     * @return the per-task member router wired into this daemon (plan 245).
     *         Never {@code null}: a daemon constructed without an explicit
     *         router returns the shipped {@link NoOpTaskMemberRouter}
     *         singleton (single-member plan — zero regression).
     */
    public ITaskMemberRouter getTaskMemberRouter() {
        return taskMemberRouter;
    }

    /**
     * Wire (or re-wire) the per-task member router (plan 245 / daemon dispatch
     * parity). Passing {@code null} resets to the shipped
     * {@link NoOpTaskMemberRouter} single-member default (line-for-line
     * identical to the pre-245 daemon single-member dispatch — zero
     * regression). Passing a multi-member router opts the daemon into N-target
     * fan-out per task, reduced under the plan's strategy (shipped default
     * {@link AllMustSucceedReduction}).
     *
     * <p>This mirrors {@code TeamTaskFlowOrchestrator.setTaskMemberRouter} and
     * the established Layer 4 wire-at-consumer convention: the router's only
     * consumer is the daemon's per-task dispatch loop, so the daemon owns its
     * injection (not the engine).
     *
     * @param taskMemberRouter the router to wire; {@code null} falls back to
     *                         {@link NoOpTaskMemberRouter#noOp()}
     */
    public void setTaskMemberRouter(ITaskMemberRouter taskMemberRouter) {
        this.taskMemberRouter = taskMemberRouter != null ? taskMemberRouter : NoOpTaskMemberRouter.noOp();
    }

    /**
     * @return the dedicated executor wired for spawn-target supplyAsync, or
     *         {@code null} when none has been injected (the daemon will then
     *         lazily create an owned bounded pool on first spawn-target
     *         dispatch — plan 245 reusing plan 243 design 裁定 3).
     */
    public Executor getSpawnStepExecutor() {
        return spawnStepExecutor;
    }

    /**
     * Wire (or re-wire) the dedicated executor used for spawn-target
     * supplyAsync (plan 245 reusing plan 243 design 裁定 3). The supplied
     * executor MUST be independent of the {@code commonPool}. When
     * {@code null}, the daemon lazily creates and owns a dedicated bounded
     * daemon-thread pool (released by {@link #stop()}).
     *
     * @param executor the dedicated executor (independent of the commonPool);
     *                 {@code null} resets to the lazily-created owned pool
     */
    public void setSpawnStepExecutor(Executor executor) {
        this.spawnStepExecutor = executor;
    }

    /**
     * Resolve the executor for spawn-target supplyAsync: use the injected one
     * if present, otherwise lazily create a dedicated bounded daemon-thread
     * pool independent of the commonPool (plan 243 design 裁定 3, reused).
     */
    private Executor resolveSpawnExecutor() {
        if (spawnStepExecutor != null) {
            return spawnStepExecutor;
        }
        if (ownedSpawnExecutor == null) {
            int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
            ThreadFactory factory = new SpawnWorkerThreadFactory();
            ownedSpawnExecutor = Executors.newFixedThreadPool(poolSize, factory);
        }
        return ownedSpawnExecutor;
    }

    /**
     * Daemon thread factory for the owned spawn pool (mirrors the orchestrator
     * naming so tests can assert the spawn worker ran off the calling thread /
     * off the commonPool).
     */
    private static final class SpawnWorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ai-agent-daemon-spawn-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }

    /**
     * Await all in-flight fan-out dispatch futures fired during the last
     * {@code scanOnce} (plan 245 async per-cycle dispatch). The daemon does
     * NOT block on these inside {@code scanOnce}; this method lets callers
     * (tests, graceful shutdown) deterministically observe the final task
     * store state after async dispatches settle.
     *
     * <p>A future that already completed synchronously at scan-return time is
     * not tracked here (it was resolved + counted immediately). Only futures
     * that were still in-flight at scan-return are awaited.
     *
     * @param timeoutMs the maximum total time to wait, in milliseconds
     * @return {@code true} if all in-flight futures settled within the
     *         timeout; {@code false} if the timeout elapsed first (the
     *         futures remain tracked and may still settle later)
     */
    public boolean awaitInFlightDispatches(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        for (CompletableFuture<MemberDispatchOutcome> f : inFlightDispatches) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            try {
                f.get(remaining, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                // the dispatcher never completes exceptionally (it returns
                // a FAILED outcome), so this is a timeout / interruption —
                // continue awaiting the rest up to the deadline.
            }
        }
        return System.currentTimeMillis() <= deadline;
    }

    // ========================================================================
    // Cross-process scan-lease coordinator wiring (plan 242)
    // ========================================================================

    /**
     * Return the scan-lease coordinator wired into this daemon (plan 242).
     * Never {@code null}: a daemon constructed without an explicit
     * coordinator returns the shipped {@link NoOpDaemonCoordinator} default
     * (zero-regression single-instance behaviour).
     *
     * @return the wired scan-lease coordinator (non-null)
     */
    public IDaemonCoordinator getDaemonCoordinator() {
        return daemonCoordinator;
    }

    /**
     * Wire (or re-wire) the scan-lease coordinator (plan 242 /
     * {@code L4-cross-process-daemon-coordination}).
     *
     * <p>Before scanning each team, {@code scanOnce} calls
     * {@link IDaemonCoordinator#tryAcquireScanLease}; if another instance
     * holds the active lease for that team, this daemon skips it
     * (incrementing {@code skippedCoordinatedTeams} — an explicit
     * coordination signal, not a silent skip). Passing {@code null} resets
     * to the shipped {@link NoOpDaemonCoordinator} default (every acquire
     * returns {@code true} = full scan, zero regression — design 裁定 5).
     * Passing a functional coordinator (e.g. {@code DbDaemonCoordinator})
     * opts the daemon into multi-instance scan-load distribution.
     *
     * <p><b>Correctness floor is unaffected</b>: even if the coordinator
     * fails completely, {@code claimTask} CAS still guarantees no
     * double-dispatch (design 裁定 6).
     *
     * @param daemonCoordinator the coordinator to wire; {@code null} falls
     *                          back to {@link NoOpDaemonCoordinator#noOp()}
     */
    public void setDaemonCoordinator(IDaemonCoordinator daemonCoordinator) {
        this.daemonCoordinator = daemonCoordinator != null ? daemonCoordinator : NoOpDaemonCoordinator.noOp();
    }

    /**
     * @return this daemon instance's unique scan-lease owner identity
     *         (plan 242, design 裁定 7). Used as the {@code ownerId} for
     *         {@link IDaemonCoordinator#tryAcquireScanLease}. Unique per
     *         instance by default ({@code "scheduler-daemon-" + UUID}).
     */
    public String getDaemonOwnerId() {
        return daemonOwnerId;
    }

    /**
     * Override the scan-lease owner identity (plan 242, design 裁定 7).
     * Useful for test determinism (fixed ownerId) or deployment customisation
     * (hostname / pod name). The ownerId <b>must</b> be unique per daemon
     * instance — reusing a shared value (e.g. {@link #daemonSessionId})
     * would disable coordination (every acquire would match the same-owner
     * renew branch).
     *
     * @param daemonOwnerId the unique owner identity; non-null, non-blank
     */
    public void setDaemonOwnerId(String daemonOwnerId) {
        Objects.requireNonNull(daemonOwnerId, "daemonOwnerId");
        if (daemonOwnerId.isBlank()) {
            throw new NopAiAgentException(
                    "TeamTaskSchedulerDaemon: daemonOwnerId must not be blank");
        }
        this.daemonOwnerId = daemonOwnerId;
    }

    /**
     * @return the scan-lease duration in milliseconds used for every
     *         {@link IDaemonCoordinator#tryAcquireScanLease} call (plan 242,
     *         design 裁定 8). Default {@link #DEFAULT_SCAN_LEASE_MS}.
     */
    public long getScanLeaseMs() {
        return scanLeaseMs;
    }

    /**
     * Override the scan-lease duration (plan 242, design 裁定 8). Tune for
     * team size / agent execution latency (large teams or slow agents may
     * need a larger lease to avoid mid-scan preemption).
     *
     * @param scanLeaseMs the lease duration; must be {@code > 0}
     */
    public void setScanLeaseMs(long scanLeaseMs) {
        if (scanLeaseMs <= 0) {
            throw new NopAiAgentException(
                    "TeamTaskSchedulerDaemon: scanLeaseMs must be > 0 (got " + scanLeaseMs + ")");
        }
        this.scanLeaseMs = scanLeaseMs;
    }

    // ========================================================================
    // Lifecycle (idempotent start / stop)
    // ========================================================================

    @Override
    public synchronized void start() {
        if (scheduleHandle != null) {
            // Idempotent: already running.
            return;
        }
        scheduleHandle = scheduledExecutor.scheduleWithFixedDelay(
                this::scanOnceSafe, scanIntervalSec, scanIntervalSec, TimeUnit.SECONDS);
        LOG.info("TeamTaskSchedulerDaemon: started periodic team-task scheduling scan "
                + "(intervalSec={}, targetTeams={})",
                scanIntervalSec, targetTeamIds.isEmpty() ? "ALL" : targetTeamIds);
    }

    @Override
    public synchronized void stop() {
        if (scheduleHandle == null) {
            // Idempotent: not running.
            return;
        }
        // Graceful: mayInterruptIfRunning=false. In-progress dispatched tasks
        // (already CLAIMED and executing) are not interrupted; only the
        // periodic schedule is cancelled so no NEW tasks are claimed.
        scheduleHandle.cancel(false);
        scheduleHandle = null;
        // Release the owned spawn executor pool (plan 245). An injected
        // executor is left alone — its owner manages its lifecycle.
        if (ownedSpawnExecutor != null) {
            ownedSpawnExecutor.shutdownNow();
            try {
                ownedSpawnExecutor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ownedSpawnExecutor = null;
        }
        LOG.info("TeamTaskSchedulerDaemon: stopped periodic team-task scheduling scan "
                + "(in-progress dispatched tasks, if any, continue until natural completion)");
    }

    /**
     * Wrapper invoked by the scheduler: runs {@link #scanOnce} and logs any
     * unexpected failure at WARN so the periodic task is never silently
     * killed by an exception. A failed scan is observable, not silent.
     */
    private void scanOnceSafe() {
        try {
            SchedulerScanResult result = scanOnce();
            if (result.getCompletedTasks() > 0 || result.getAbandonedTasks() > 0
                    || result.getClaimLostTasks() > 0) {
                LOG.info("TeamTaskSchedulerDaemon: scan complete: {}", result);
            } else {
                LOG.debug("TeamTaskSchedulerDaemon: scan complete (no tasks claimed / dispatched): {}",
                        result);
            }
        } catch (RuntimeException e) {
            LOG.warn("TeamTaskSchedulerDaemon: periodic scan failed (will retry next interval): {}",
                    e.toString());
        }
    }

    // ========================================================================
    // scanOnce — ready resolution + idempotent claim + dispatch + complete/abandon
    // ========================================================================

    @Override
    public SchedulerScanResult scanOnce() {
        long scannedAt = System.currentTimeMillis();
        long start = scannedAt;

        List<String> teamIdsToScan = resolveTeamIdsToScan();
        int readyCreated = 0;
        int claimed = 0;
        int claimLost = 0;
        int dispatched = 0;
        int completed = 0;
        int abandoned = 0;
        int failed = 0;
        int skippedCoordinated = 0;
        List<String> completedIds = new ArrayList<>();
        List<String> abandonedIds = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();

        // Plan 243 design 裁定 2 (explicit-propagation tenant capture): capture
        // the caller's tenant ONCE here, on the scan thread, so spawn-target
        // supplyAsync workers can re-apply it inside the worker regardless of
        // the dispatch topology. Null = no tenant context (all data visible,
        // backward compatible).
        final String capturedTenant = ThreadLocalTenantResolver.current();

        for (String teamId : teamIdsToScan) {
            Optional<Team> teamOpt = teamManager.getTeam(teamId);
            if (teamOpt.isEmpty() || teamOpt.get().getStatus() == TeamStatus.DISBANDED) {
                LOG.warn("TeamTaskSchedulerDaemon: skipping team that is missing or disbanded: teamId={}",
                        teamId);
                continue;
            }

            // --- Cross-process scan-lease guard (plan 242). Acquire BEFORE
            // the expensive topology build + ready query so a coordinated
            // skip avoids the DB read + topology build + claim CAS
            // contention entirely. NoOp shipped default always returns
            // true → zero regression (full scan, no skip).
            if (!daemonCoordinator.tryAcquireScanLease(teamId, daemonOwnerId, scanLeaseMs)) {
                skippedCoordinated++;
                LOG.debug("TeamTaskSchedulerDaemon: skipping team teamId={} — another daemon instance "
                                + "holds the active scan lease (ownerId={})",
                        teamId, daemonOwnerId);
                continue;
            }
            // From here on we hold the lease for this team for this scan.
            // Release it in a finally so the next instance can take over
            // immediately on completion (rather than waiting for TTL).
            try {
                Team team = teamOpt.get();

                List<TeamTask> tasks = taskStore.getTasksByTeam(teamId);
                if (tasks == null || tasks.isEmpty()) {
                    // Legitimate idle: team has no tasks yet.
                    continue;
                }

                TeamTaskTopology topology = new TeamTaskTopology(tasks);
                List<TeamTask> ready = topology.getReadyTasks();

                for (TeamTask task : ready) {
                    // design 裁定 4 关键安全约束: skip CLAIMED tasks (another
                    // member is executing them) — never claim, never touch.
                    if (task.getStatus() != TeamTaskStatus.CREATED) {
                        continue;
                    }
                    readyCreated++;

                    String taskId = task.getTaskId();

                    // CAS claim (idempotent). Empty = lost the race to another
                    // claimer OR the task is already COMPLETED (idempotent
                    // success — plan 245 preserves this honest signal).
                    Optional<TeamTask> claimedOpt = taskStore.claimTask(taskId, daemonSessionId);
                    if (claimedOpt.isEmpty()) {
                        Optional<TeamTask> current = taskStore.getTask(taskId);
                        if (current.isPresent()
                                && current.get().getStatus() == TeamTaskStatus.COMPLETED) {
                            // Idempotent: a prior partial run already COMPLETED
                            // this task — honest explicit success.
                            completed++;
                            completedIds.add(taskId);
                            continue;
                        }
                        claimLost++;
                        continue;
                    }
                    claimed++;

                    // From here on we OWN this task (CREATED → CLAIMED by us).
                    // Plan 245: dispatch consumes the per-task member router
                    // + the shared fan-out + reduce + complete chain. The
                    // router's NoOp shipped default produces a singleton
                    // single-member plan → bound priority + spawn fallback,
                    // line-for-line identical to the pre-245 daemon
                    // single-member dispatch (zero regression). A multi-member
                    // router produces an N-target plan fanned out + reduced.
                    DispatchTally tally = dispatchClaimedTask(team, task, capturedTenant);
                    completed += tally.completed;
                    failed += tally.failed;
                    abandoned += tally.abandoned;
                    dispatched += tally.dispatched;
                    completedIds.addAll(tally.completedIds);
                    failedIds.addAll(tally.failedIds);
                    abandonedIds.addAll(tally.abandonedIds);
                }
            } finally {
                // Active release = fast failover (the next instance can
                // immediately take the lease rather than waiting for TTL).
                // A false return (lease was preempted mid-scan by TTL
                // expiry) is LOG.warn'd — it does NOT affect scan results
                // (the scan ran, and claimTask CAS is the correctness floor).
                if (!daemonCoordinator.releaseScanLease(teamId, daemonOwnerId)) {
                    LOG.warn("TeamTaskSchedulerDaemon: releaseScanLease returned false for teamId={} "
                                    + "(ownerId={}) — lease was no longer held (expired and preempted, "
                                    + "or NoOp coordinator); scan results are unaffected",
                            teamId, daemonOwnerId);
                }
            }
        }

        long scanDurationMs = System.currentTimeMillis() - start;
        return new SchedulerScanResult(
                teamIdsToScan.size(), readyCreated, claimed, claimLost,
                dispatched, completed, abandoned, failed, skippedCoordinated,
                completedIds, abandonedIds, failedIds, scannedAt, scanDurationMs);
    }

    private List<String> resolveTeamIdsToScan() {
        if (!targetTeamIds.isEmpty()) {
            return new ArrayList<>(targetTeamIds);
        }
        Collection<Team> active = teamManager.getActiveTeams();
        if (active == null || active.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>(active.size());
        for (Team t : active) {
            ids.add(t.getTeamId());
        }
        return ids;
    }

    /**
     * Dispatch a task this daemon has just CAS-claimed, consuming the per-task
     * member router + the shared fan-out + reduce + complete chain (plan 245
     * / daemon dispatch parity).
     *
     * <p><b>Async per-cycle dispatch (plan 245 design 裁定 2)</b>: the fan-out
     * future is fired via {@link MemberFanOutDispatcher#dispatch}. The daemon
     * thread does NOT block on {@code engine.execute().join()} for a single
     * task. When every underlying member future was already complete at
     * construction time (e.g. a fast test engine returning completed futures,
     * or a bound-member plan whose engine returned synchronously), the
     * dispatcher's whole chain — including the single {@code completeTask}
     * CAS — runs synchronously, so the outcome is observed and counted inside
     * this method (zero regression for the pre-245 synchronous happy path).
     * When any underlying future is genuinely async (real engine, or a
     * spawn-target {@code supplyAsync}), the outcome is NOT observed here;
     * the future is tracked in {@link #inFlightDispatches} for later
     * resolution (the store transition happens inside the dispatcher's chain
     * regardless of timing).
     *
     * <p><b>Honest failure semantics (plan 245 design 裁定 3)</b>: empty plan
     * / member failure / spawner three-state (NO_SPAWN / SPAWN_FAILED /
     * throws / null) / {@code completeTask} CAS loss → the task is LEFT IN
     * CLAIMED (NOT abandoned — the recovery model is plan 240 reclaim,
     * aligning the daemon failure semantics with the orchestrator line-for-
     * line). An empty plan is a synchronous honest failure (no fan-out fired).
     * No silent skip / no empty body / no swallowed exception (Minimum Rules
     * #24).
     *
     * <p><b>Single-member zero regression</b>: the NoOp shipped router default
     * produces a singleton plan (bound priority + spawn fallback). A singleton
     * BOUND plan with an already-complete engine future completes
     * synchronously → the task is COMPLETED within this method, line-for-line
     * matching the pre-245 daemon bound-member dispatch. A singleton SPAWN
     * plan (NoOp spawner) is an honest failure → task stays CLAIMED (the
     * spawn {@code supplyAsync} is tracked in-flight; the durable store state
     * is CLAIMED either way).
     *
     * @return the tally of resolved outcomes for this one task (the caller
     *         folds it into the scan-wide counters)
     */
    private DispatchTally dispatchClaimedTask(Team team, TeamTask task, String capturedTenant) {
        String taskId = task.getTaskId();

        // Plan 245: route via the per-task member router. The router runs at
        // dispatch time, non-executing (it never calls the engine nor the
        // spawner). NoOp shipped default → singleton plan = bound priority +
        // spawn fallback (line-for-line zero regression).
        MemberDispatchPlan plan;
        try {
            plan = taskMemberRouter.route(team, task);
        } catch (RuntimeException e) {
            // A router that throws is a contract violation (the contract says
            // return an empty plan for the no-member case). Honest failure:
            // task stays CLAIMED, no fan-out fired.
            LOG.warn("TeamTaskSchedulerDaemon: taskMemberRouter threw for taskId={}, teamId={} — "
                    + "task left CLAIMED (honest failure)", taskId, team.getTeamId(), e);
            return DispatchTally.failedNoDispatch(taskId);
        }
        if (plan == null) {
            // Defensive: contract says never null. Treat as honest failure.
            LOG.warn("TeamTaskSchedulerDaemon: taskMemberRouter returned null for taskId={}, teamId={} — "
                    + "task left CLAIMED (router contract violation)", taskId, team.getTeamId());
            return DispatchTally.failedNoDispatch(taskId);
        }

        if (plan.isEmpty()) {
            // Honest failure: empty plan (no dispatchable member). The task
            // stays CLAIMED (recovery via plan 240 reclaim). No fan-out fired.
            LOG.warn("TeamTaskSchedulerDaemon: dispatch plan produced zero targets for taskId={}, "
                            + "teamId={} — task left CLAIMED (no dispatchable member; router={})",
                    taskId, team.getTeamId(), taskMemberRouter.getClass().getName());
            return DispatchTally.failedNoDispatch(taskId);
        }

        // Determine whether any spawn target is present (requires the
        // dedicated spawn executor). Bound-only plans do not need it.
        boolean hasSpawn = false;
        for (io.nop.ai.agent.team.flow.DispatchTarget t : plan.getTargets()) {
            if (t.isSpawn()) {
                hasSpawn = true;
                break;
            }
        }
        Executor spawnExecutor = hasSpawn ? resolveSpawnExecutor() : null;

        // Fire the shared fan-out + reduce + complete chain. The dispatcher
        // never throws — it returns a MemberDispatchOutcome (COMPLETED or
        // FAILED). For already-complete underlying futures the returned
        // future IS DONE at construction time and the chain (including
        // completeTask) has run synchronously.
        CompletableFuture<MemberDispatchOutcome> dispatched = MemberFanOutDispatcher.dispatch(
                task, team, plan.getTargets(), plan.getReductionStrategy(),
                agentEngine, memberSpawner, taskStore, daemonSessionId,
                spawnExecutor, capturedTenant);

        if (dispatched.isDone()) {
            // Synchronous resolution (already-complete futures). Record the
            // outcome immediately. join() is safe — the future is done and
            // the dispatcher never completes exceptionally.
            try {
                MemberDispatchOutcome outcome = dispatched.join();
                if (outcome.isCompleted()) {
                    return DispatchTally.completed(taskId);
                }
                LOG.warn("TeamTaskSchedulerDaemon: fan-out reduction failed for taskId={}, teamId={} — "
                                + "task left CLAIMED (recovery via plan 240 reclaim): {}",
                        taskId, team.getTeamId(), outcome.getCause().toString());
                return DispatchTally.failedAfterDispatch(taskId);
            } catch (RuntimeException e) {
                // Defensive: the dispatcher's exceptionally() guarantees this
                // never happens, but defend against an unexpected propagation.
                LOG.warn("TeamTaskSchedulerDaemon: dispatch future threw unexpectedly for taskId={} — "
                                + "task left CLAIMED",
                        taskId, e);
                return DispatchTally.failedAfterDispatch(taskId);
            }
        }

        // Genuinely async — do NOT block the scan thread. Track in-flight so
        // callers (tests / graceful shutdown) can await. The dispatcher's
        // chain performs the store transition (completeTask on success /
        // leave CLAIMED on failure) regardless of timing. Remove from the
        // in-flight queue once settled to keep the queue bounded across scans.
        inFlightDispatches.add(dispatched);
        dispatched.whenComplete((outcome, ex) -> {
            inFlightDispatches.remove(dispatched);
            if (ex != null) {
                LOG.warn("TeamTaskSchedulerDaemon: in-flight dispatch settled exceptionally for "
                                + "taskId={} — task left CLAIMED",
                        taskId, ex);
            } else if (outcome != null && !outcome.isCompleted()) {
                LOG.warn("TeamTaskSchedulerDaemon: in-flight fan-out reduction failed for taskId={} — "
                                + "task left CLAIMED (recovery via plan 240 reclaim): {}",
                        taskId, outcome.getCause().toString());
            }
        });
        // The task is CLAIMED and dispatched (in-flight). Its final
        // completed/failed outcome will be observed in a later scan's
        // idempotent already-COMPLETED path (on success) or reclaim (on
        // failure). No synchronous completed/failed counter increment here.
        return DispatchTally.inFlight();
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    /**
     * Per-task dispatch tally folded into the scan-wide counters by
     * {@link #scanOnce}. One of:
     * <ul>
     *   <li>{@link #completed} — fan-out succeeded + single completeTask CAS
     *       succeeded (task transitioned CLAIMED → COMPLETED, observed
     *       synchronously). {@code dispatched=1}.</li>
     *   <li>{@link #failedAfterDispatch} — honest failure observed after a
     *       fan-out was fired (sync reduction failure / completeTask CAS loss
     *       on already-complete futures). Task LEFT IN CLAIMED.
     *       {@code dispatched=1}.</li>
     *   <li>{@link #failedNoDispatch} — honest failure with NO fan-out fired
     *       (empty plan / router threw / router returned null). Task LEFT IN
     *       CLAIMED. {@code dispatched=0}.</li>
     *   <li>{@link #inFlight} — dispatched but genuinely async (underlying
     *       futures not complete at scan time). The store transition happens
     *       inside the dispatcher's chain regardless of timing; the final
     *       outcome is observed in a later scan. {@code dispatched=1}.</li>
     * </ul>
     */
    private static final class DispatchTally {
        final int completed;
        final int failed;
        final int abandoned;
        final int dispatched;
        final List<String> completedIds;
        final List<String> failedIds;
        final List<String> abandonedIds;

        private DispatchTally(int completed, int failed, int abandoned, int dispatched,
                              List<String> completedIds, List<String> failedIds,
                              List<String> abandonedIds) {
            this.completed = completed;
            this.failed = failed;
            this.abandoned = abandoned;
            this.dispatched = dispatched;
            this.completedIds = completedIds;
            this.failedIds = failedIds;
            this.abandonedIds = abandonedIds;
        }

        /** Fan-out fired + succeeded (CLAIMED → COMPLETED, observed sync). */
        static DispatchTally completed(String taskId) {
            return new DispatchTally(1, 0, 0, 1,
                    Collections.singletonList(taskId),
                    Collections.emptyList(), Collections.emptyList());
        }

        /** Fan-out fired but reduction failed sync (task LEFT IN CLAIMED). */
        static DispatchTally failedAfterDispatch(String taskId) {
            return new DispatchTally(0, 1, 0, 1,
                    Collections.emptyList(),
                    Collections.singletonList(taskId),
                    Collections.emptyList());
        }

        /** No fan-out fired (empty plan / router threw); task LEFT IN CLAIMED. */
        static DispatchTally failedNoDispatch(String taskId) {
            return new DispatchTally(0, 1, 0, 0,
                    Collections.emptyList(),
                    Collections.singletonList(taskId),
                    Collections.emptyList());
        }

        /** Fan-out fired but genuinely async (outcome observed in a later scan). */
        static DispatchTally inFlight() {
            return new DispatchTally(0, 0, 0, 1,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
    }
}
