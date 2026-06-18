package io.nop.ai.agent.team.scheduler;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.runtime.coordination.IDaemonCoordinator;
import io.nop.ai.agent.runtime.coordination.NoOpDaemonCoordinator;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.NoOpMemberSpawner;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMember;
import io.nop.ai.agent.team.TeamStatus;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.ai.agent.team.flow.TeamTaskTopology;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
        int skippedCoordinated = 0;
        List<String> completedIds = new ArrayList<>();
        List<String> abandonedIds = new ArrayList<>();

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

                // Resolve one bound member for the whole team (daemon delegates
                // each ready task to a bound member; multi-member routing is a
                // successor). Resolved once per team per scan — the bound roster
                // does not change within a scan.
                ResolvedMember member = resolveBoundMember(team);

                for (TeamTask task : ready) {
                    // design 裁定 4 关键安全约束: skip CLAIMED tasks (another
                    // member is executing them) — never claim, never abandon.
                    if (task.getStatus() != TeamTaskStatus.CREATED) {
                        continue;
                    }
                    readyCreated++;

                    String taskId = task.getTaskId();

                    // CAS claim (idempotent). Empty = lost the race to another
                    // claimer — legitimate concurrency, not an error: skip silently.
                    Optional<TeamTask> claimedOpt = taskStore.claimTask(taskId, daemonSessionId);
                    if (claimedOpt.isEmpty()) {
                        claimLost++;
                        continue;
                    }
                    claimed++;

                    // From here on we OWN this task (CREATED → CLAIMED by us).
                    // Any dispatch failure is handled by abandoning — only tasks
                    // we CAS-claimed are ever abandoned (design 裁定 4).
                    DispatchOutcome outcome = dispatchClaimedTask(team, task, member);
                    switch (outcome) {
                        case COMPLETED:
                            completed++;
                            completedIds.add(taskId);
                            dispatched++;
                            break;
                        case DISPATCH_FAILED:
                            abandoned++;
                            abandonedIds.add(taskId);
                            dispatched++;
                            break;
                        case UNBOUND_MEMBER:
                            abandoned++;
                            abandonedIds.add(taskId);
                            // execute was NOT invoked — do not count as dispatched.
                            break;
                        default:
                            throw new IllegalStateException("unhandled dispatch outcome: " + outcome);
                    }
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
                dispatched, completed, abandoned, skippedCoordinated,
                completedIds, abandonedIds, scannedAt, scanDurationMs);
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
     * Resolve one bound member of the team to delegate ready tasks to (plan
     * 236 裁定 4 — consume already-bound members; plan 237 adds the
     * spawned-member fallback on the dispatch path when this returns null).
     * Strategy: prefer the first bound MEMBER-role member; fall back to the
     * first bound member of any role; return {@code null} if no member is
     * bound.
     *
     * <p>This is the daemon-side equivalent of
     * {@code TeamTaskFlowOrchestrator.resolveMember}, simplified to a single
     * resolved member per team per scan (the daemon does not check
     * {@code task.getClaimedBy()} because it only processes CREATED tasks,
     * whose {@code claimedBy} is null). When this returns {@code null} the
     * dispatch path consults the wired {@link IMemberSpawner} (plan 237).
     */
    private ResolvedMember resolveBoundMember(Team team) {
        TeamMember fallback = null;
        for (TeamMember m : team.getMembers().values()) {
            if (!m.isBound()) {
                continue;
            }
            if (m.getRole() == MemberRole.MEMBER) {
                return new ResolvedMember(m.getMemberName(), m.getSessionId(),
                        agentModelOf(team, m.getMemberName()));
            }
            if (fallback == null) {
                fallback = m;
            }
        }
        if (fallback != null) {
            return new ResolvedMember(fallback.getMemberName(), fallback.getSessionId(),
                    agentModelOf(team, fallback.getMemberName()));
        }
        return null;
    }

    private String agentModelOf(Team team, String memberName) {
        if (team.getSpec() == null) {
            return null;
        }
        for (io.nop.ai.agent.team.TeamMemberSpec spec : team.getSpec().getMemberSpecs()) {
            if (spec.getMemberName().equals(memberName)) {
                return spec.getAgentModel();
            }
        }
        return null;
    }

    /**
     * Dispatch a task this daemon has just CAS-claimed. Synchronous join
     * (design 裁定 1 — same delegation mechanism as plan 233
     * {@code MemberAgentTaskStep}). Honesty contract (design 裁定 4):
     * <ul>
     *   <li>Bound member present ({@code member != null}) → dispatch to bound
     *       member agent; success COMPLETED, any failure abandon.</li>
     *   <li>No bound member + functional {@link IMemberSpawner} (plan 237):
     *     <ul>
     *       <li>{@link SpawnMemberResult.Status#DISPATCHED} → dispatch to the
     *           spawned agent (the spawner already executed the agent; here we
     *           just interpret the wrapped {@link AgentExecutionResult} for
     *           complete/abandon — design 裁定 4: spawned path shares the
     *           bound-member complete/abandon logic).</li>
     *       <li>{@link SpawnMemberResult.Status#NO_SPAWN} → abandon
     *           ({@code UNBOUND_MEMBER} — same as NoOp spawner, zero
     *           regression for the "spawner honestly declined" case).</li>
     *       <li>{@link SpawnMemberResult.Status#SPAWN_FAILED} → abandon
     *           ({@code DISPATCH_FAILED} — same as a bound-member dispatch
     *           failure).</li>
     *     </ul>
     *   </li>
     *   <li>No bound member + NoOp spawner shipped default → abandon
     *       ({@code UNBOUND_MEMBER}, zero regression — pre-spawn behaviour).</li>
     *   <li>{@code completeTask} CAS lost → abandon.</li>
     * </ul>
     * Per-task isolation: any failure here is contained — the caller continues
     * to the next ready task.
     */
    private DispatchOutcome dispatchClaimedTask(Team team, TeamTask task, ResolvedMember member) {
        String taskId = task.getTaskId();

        // --- Bound-member priority (design 裁定 3): if a bound member was
        // resolved, dispatch to it directly (the spawner is NEVER consulted
        // for teams with a bound member — zero behaviour change for the
        // bound-member path).
        if (member != null) {
            return dispatchToBoundMember(task, member);
        }

        // --- No bound member: consult the spawner (plan 237 / design 裁定 5).
        // NoOp shipped default returns NO_SPAWN, so this falls through to the
        // pre-spawn UNBOUND_MEMBER abandon (zero regression). A functional
        // spawner (DefaultMemberSpawner) attempts to spawn based on the team's
        // declarative memberSpec.agentModel.
        SpawnMemberRequest spawnReq = new SpawnMemberRequest(team, task, daemonSessionId);
        SpawnMemberResult spawnResult;
        try {
            spawnResult = memberSpawner.spawnMember(spawnReq);
        } catch (RuntimeException e) {
            // A spawner that throws (rather than returning SPAWN_FAILED) is
            // a contract violation, but we still handle it honestly: the
            // task is abandoned (not silently swallowed).
            LOG.warn("TeamTaskSchedulerDaemon: memberSpawner threw for taskId={}, teamId={} — abandoning",
                    taskId, team.getTeamId(), e);
            abandonClaimed(taskId, "memberSpawner threw: " + e.toString());
            return DispatchOutcome.DISPATCH_FAILED;
        }
        if (spawnResult == null) {
            // Defensive: a well-behaved spawner never returns null (Minimum
            // Rules #24), but treat a null as honest NO_SPAWN-equivalent
            // abandon rather than NPE.
            LOG.warn("TeamTaskSchedulerDaemon: memberSpawner returned null for taskId={}, teamId={} — "
                    + "abandoning (spawner contract violation)", taskId, team.getTeamId());
            abandonClaimed(taskId, "memberSpawner returned null");
            return DispatchOutcome.UNBOUND_MEMBER;
        }

        switch (spawnResult.getStatus()) {
            case NO_SPAWN:
                LOG.warn("TeamTaskSchedulerDaemon: no bound member for team teamId={} and spawner "
                                + "declined to spawn (reason={}) — abandoning claimed task taskId={}",
                        team.getTeamId(), spawnResult.getReason(), taskId);
                abandonClaimed(taskId, "no spawn: " + spawnResult.getReason());
                return DispatchOutcome.UNBOUND_MEMBER;
            case SPAWN_FAILED:
                LOG.warn("TeamTaskSchedulerDaemon: spawner attempted spawn but failed for taskId={}, "
                                + "teamId={} (reason={}) — abandoning",
                        taskId, team.getTeamId(), spawnResult.getReason());
                abandonClaimed(taskId, "spawn failed: " + spawnResult.getReason());
                return DispatchOutcome.DISPATCH_FAILED;
            case DISPATCHED:
                // Spawner executed the agent; interpret the wrapped result
                // for complete/abandon (design 裁定 4 — shared with the
                // bound-member path).
                return completeOrAbandonAfterExecution(task, spawnResult.getExecutionResult(),
                        spawnResult.getSpawnedSessionId(), /*spawned=*/true);
            default:
                throw new IllegalStateException("unhandled spawn result status: "
                        + spawnResult.getStatus());
        }
    }

    /**
     * Dispatch a claimed task to an already-resolved bound member (design
     * 裁定 1 — synchronous join via {@code IAgentEngine.execute}). The
     * spawner is NOT consulted on this path (bound-member priority,
     * design 裁定 3).
     */
    private DispatchOutcome dispatchToBoundMember(TeamTask task, ResolvedMember member) {
        String taskId = task.getTaskId();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("teamTaskId", taskId);
        metadata.put("teamId", task.getTeamId());
        AgentMessageRequest request = new AgentMessageRequest(
                member.agentName, buildPrompt(task), member.sessionId, metadata);

        AgentExecutionResult result;
        try {
            CompletableFuture<AgentExecutionResult> future = agentEngine.execute(request);
            result = future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            LOG.warn("TeamTaskSchedulerDaemon: member agent threw for taskId={}, sessionId={} — abandoning",
                    taskId, member.sessionId, cause);
            abandonClaimed(taskId, "member agent threw: " + cause.toString());
            return DispatchOutcome.DISPATCH_FAILED;
        }

        return completeOrAbandonAfterExecution(task, result, member.sessionId, /*spawned=*/false);
    }

    /**
     * Shared complete/abandon logic for the bound-member and spawned-member
     * dispatch paths (design 裁定 4 — unified post-execution handling).
     * Inspects the {@link AgentExecutionResult} status to decide
     * {@code completeTask} (success) vs. {@code abandonTask} (any non-completed
     * terminal status), then handles the {@code completeTask} CAS-loss case.
     *
     * @param task           the claimed task being dispatched
     * @param result         the execution result from the agent (non-null)
     * @param sessionLabel   the session id (bound) or spawned session id, used
     *                       for logging/audit
     * @param spawned        {@code true} if this is the spawned-member path
     *                       (for log clarity); {@code false} for bound-member
     */
    private DispatchOutcome completeOrAbandonAfterExecution(TeamTask task,
                                                             AgentExecutionResult result,
                                                             String sessionLabel,
                                                             boolean spawned) {
        String taskId = task.getTaskId();

        if (result == null) {
            // Defensive: engine returned null from a completed future. Treat
            // as dispatch failure rather than NPE.
            LOG.warn("TeamTaskSchedulerDaemon: {} agent returned null result for taskId={}, "
                            + "sessionId={} — abandoning",
                    spawned ? "spawned" : "bound", taskId, sessionLabel);
            abandonClaimed(taskId, (spawned ? "spawned" : "bound")
                    + " agent returned null result");
            return DispatchOutcome.DISPATCH_FAILED;
        }

        if (result.getStatus() != AgentExecStatus.completed) {
            LOG.warn("TeamTaskSchedulerDaemon: {} agent did not complete for taskId={}, "
                            + "sessionId={}, status={}{} — abandoning",
                    spawned ? "spawned" : "bound", taskId, sessionLabel, result.getStatus(),
                    result.getError() != null ? ", error=" + result.getError() : "");
            abandonClaimed(taskId, (spawned ? "spawned" : "bound")
                    + " agent status=" + result.getStatus());
            return DispatchOutcome.DISPATCH_FAILED;
        }

        Optional<TeamTask> completedOpt = taskStore.completeTask(taskId, daemonSessionId);
        if (completedOpt.isEmpty()) {
            LOG.warn("TeamTaskSchedulerDaemon: completeTask CAS lost for taskId={} "
                            + "(not in CLAIMED status — possible concurrent transition) — abandoning",
                    taskId);
            abandonClaimed(taskId, "completeTask CAS lost");
            return DispatchOutcome.DISPATCH_FAILED;
        }

        return DispatchOutcome.COMPLETED;
    }

    /**
     * Transition a task this daemon has claimed (CLAIMED) to ABANDONED. If
     * the abandon CAS loses (e.g. the task was concurrently completed by
     * another path), the failure is LOG.warn'd — the task is left in its
     * current state and the caller still records it as "abandoned" in the
     * scan result (the dispatch <em>did</em> fail; the honest signal is
     * preserved even if the state transition raced).
     */
    private void abandonClaimed(String taskId, String reason) {
        try {
            Optional<TeamTask> abandoned = taskStore.abandonTask(taskId, daemonSessionId);
            if (abandoned.isEmpty()) {
                LOG.warn("TeamTaskSchedulerDaemon: abandonTask returned empty for taskId={} "
                        + "(already terminal) — dispatch failure still recorded: {}", taskId, reason);
            }
        } catch (RuntimeException e) {
            // Never let an abandon failure abort the scan.
            LOG.warn("TeamTaskSchedulerDaemon: abandonTask threw for taskId={} — dispatch failure "
                    + "still recorded ({}): {}", taskId, reason, e.toString());
        }
    }

    private String buildPrompt(TeamTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("Execute team task: ").append(task.getSubject());
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            sb.append("\n").append(task.getDescription());
        }
        return sb.toString();
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private static final class ResolvedMember {
        final String memberName;
        final String sessionId;
        final String agentName;

        ResolvedMember(String memberName, String sessionId, String agentName) {
            this.memberName = memberName;
            this.sessionId = sessionId;
            this.agentName = agentName;
        }
    }

    /**
     * Internal per-task dispatch outcome. Used only inside {@link #scanOnce}
     * to fold outcomes into the {@link SchedulerScanResult} counters.
     * <ul>
     *   <li>{@link #COMPLETED} — dispatched (execute invoked) and
     *       {@code completeTask} succeeded. Increments completed + dispatched.</li>
     *   <li>{@link #DISPATCH_FAILED} — dispatched (execute invoked) but the
     *       member agent threw / returned non-completed / {@code completeTask}
     *       CAS lost; the task was abandoned. Increments abandoned +
     *       dispatched.</li>
     *   <li>{@link #UNBOUND_MEMBER} — no bound member so execute was NOT
     *       invoked; the task was abandoned. Increments abandoned only.</li>
     * </ul>
     */
    private enum DispatchOutcome {
        COMPLETED,
        DISPATCH_FAILED,
        UNBOUND_MEMBER
    }
}
