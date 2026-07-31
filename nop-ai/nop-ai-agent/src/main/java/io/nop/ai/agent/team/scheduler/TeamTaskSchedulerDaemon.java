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
import io.nop.ai.agent.team.flow.ITaskMemberRouter;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TeamTaskSchedulerDaemon implements ITeamTaskSchedulerDaemon {
    private static final Logger LOG = LoggerFactory.getLogger(TeamTaskSchedulerDaemon.class);

    public static final long DEFAULT_SCAN_INTERVAL_SEC = 5L;
    public static final String DEFAULT_DAEMON_SESSION_ID = "team-task-scheduler-daemon";
    public static final long DEFAULT_SCAN_LEASE_MS = 30_000L;

    private final IAgentEngine agentEngine;
    private final ITeamTaskStore taskStore;
    private final ITeamManager teamManager;
    private final IScheduledExecutor scheduledExecutor;
    private final long scanIntervalSec;
    private final String daemonSessionId;
    private final Set<String> targetTeamIds;
    private final TaskDispatchCoordinator dispatchCoordinator;

    private IDaemonCoordinator daemonCoordinator = NoOpDaemonCoordinator.noOp();
    private String daemonOwnerId = "scheduler-daemon-" + UUID.randomUUID();
    private long scanLeaseMs = DEFAULT_SCAN_LEASE_MS;
    private volatile Future<?> scheduleHandle;



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
        this.dispatchCoordinator = new TaskDispatchCoordinator(this.agentEngine, this.taskStore, this.daemonSessionId);
        this.dispatchCoordinator.setMemberSpawner(memberSpawner);
    }

    public long getScanIntervalSec() { return scanIntervalSec; }

    public String getDaemonSessionId() { return daemonSessionId; }

    public Set<String> getTargetTeamIds() { return targetTeamIds; }

    public IMemberSpawner getMemberSpawner() { return dispatchCoordinator.getMemberSpawner(); }

    public void setMemberSpawner(IMemberSpawner memberSpawner) { dispatchCoordinator.setMemberSpawner(memberSpawner); }

    public ITaskMemberRouter getTaskMemberRouter() { return dispatchCoordinator.getTaskMemberRouter(); }

    public void setTaskMemberRouter(ITaskMemberRouter taskMemberRouter) { dispatchCoordinator.setTaskMemberRouter(taskMemberRouter); }

    public Executor getSpawnStepExecutor() { return dispatchCoordinator.getSpawnStepExecutor(); }

    public void setSpawnStepExecutor(Executor executor) { dispatchCoordinator.setSpawnStepExecutor(executor); }

    public boolean awaitInFlightDispatches(long timeoutMs) { return dispatchCoordinator.awaitInFlightDispatches(timeoutMs); }

    public IDaemonCoordinator getDaemonCoordinator() { return daemonCoordinator; }

    public void setDaemonCoordinator(IDaemonCoordinator daemonCoordinator) {
        this.daemonCoordinator = daemonCoordinator != null ? daemonCoordinator : NoOpDaemonCoordinator.noOp();
    }

    public String getDaemonOwnerId() { return daemonOwnerId; }

    public void setDaemonOwnerId(String daemonOwnerId) {
        Objects.requireNonNull(daemonOwnerId, "daemonOwnerId");
        if (daemonOwnerId.isBlank()) {
            throw new NopAiAgentException(
                    "TeamTaskSchedulerDaemon: daemonOwnerId must not be blank");
        }
        this.daemonOwnerId = daemonOwnerId;
    }

    public long getScanLeaseMs() { return scanLeaseMs; }

    public void setScanLeaseMs(long scanLeaseMs) {
        if (scanLeaseMs <= 0) {
            throw new NopAiAgentException("scanLeaseMs must be positive: " + scanLeaseMs);
        }
        this.scanLeaseMs = scanLeaseMs;
    }


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
        dispatchCoordinator.shutdownOwnSpawnExecutor();
        LOG.info("TeamTaskSchedulerDaemon: stopped periodic team-task scheduling scan "
                + "(in-progress dispatched tasks, if any, continue until natural completion)");
    }

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
                    // Pass the CLAIMED task (carrying the claim epoch, plan 279
                    // / AR-01) so the dispatcher binds the epoch into the single
                    // completeTask CAS — a stale in-flight dispatcher holding a
                    // pre-reclaim epoch cannot complete a reclaimed+re-claimed
                    // task (closes the shared-daemon-id double-execution window).
                    // + the shared fan-out + reduce + complete chain. The
                    // router's NoOp shipped default produces a singleton
                    // single-member plan → bound priority + spawn fallback,
                    // line-for-line identical to the pre-245 daemon
                    // single-member dispatch (zero regression). A multi-member
                    // router produces an N-target plan fanned out + reduced.
                    TaskDispatchCoordinator.DispatchTally tally = dispatchCoordinator.dispatchClaimedTask(team, task, claimedOpt.get(), capturedTenant);
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
}
