package io.nop.ai.agent.team.scheduler;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.runtime.coordination.IDaemonCoordinator;
import io.nop.ai.agent.runtime.coordination.NoOpDaemonCoordinator;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 242 Phase 2 focused tests for the {@link TeamTaskSchedulerDaemon}
 * scan-lease coordinator wiring (Minimum Rules #23 Wiring Verification).
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #noOpCoordinatorScansAllTeamsZeroRegression} — shipped
 *       default keeps single-instance behaviour unchanged (no team skipped).</li>
 *   <li>{@link #coordinatorAcquirePerTeamAndReleaseOnCompletion} — scanOnce
 *       really invokes {@code tryAcquireScanLease} per team and
 *       {@code releaseScanLease} on scan completion (wiring proof).</li>
 *   <li>{@link #coordinatorAcquireFailureSkipsTeamAndCounts} — when acquire
 *       returns false, the team is skipped (not scanned) and counted in
 *       {@code skippedCoordinatedTeams} (No Silent No-Op #24).</li>
 *   <li>{@link #releaseFailureDoesNotAffectScanResults} — a false
 *       releaseScanLease return is tolerated (LOG.warn); the scan's task
 *       results are unaffected (correctness floor = claimTask CAS).</li>
 *   <li>{@link #setDaemonCoordinatorNullFallsBackToNoOp} — null-safe
 *       fallback to the NoOp shipped default.</li>
 *   <li>{@link #daemonOwnerIdDefaultsToUniqueSchedulerPrefix} — ownerId
 *       is a unique per-instance "scheduler-daemon-..." value (design
 *       裁定 7).</li>
 *   <li>{@link #scanLeaseMsDefaultsToThirtySeconds} — design 裁定 8.</li>
 * </ul>
 */
public class TestSchedulerDaemonCoordination {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Recording stubs
    // ========================================================================

    /** Always-succeed coordinator that records every call. */
    static final class RecordingCoordinator implements IDaemonCoordinator {
        final List<String> acquireTeamIds = Collections.synchronizedList(new ArrayList<>());
        final List<String> releaseTeamIds = Collections.synchronizedList(new ArrayList<>());
        final Map<String, String> acquireOwnersByTeam = new ConcurrentHashMap<>();
        final Map<String, Long> acquireLeasesByTeam = new ConcurrentHashMap<>();
        final AtomicInteger releaseFalseReturns = new AtomicInteger(0);
        boolean releaseReturnOverride = true; // true = held by us, false = preempted

        @Override
        public boolean tryAcquireScanLease(String teamId, String ownerId, long leaseMs) {
            acquireTeamIds.add(teamId);
            acquireOwnersByTeam.put(teamId, ownerId);
            acquireLeasesByTeam.put(teamId, leaseMs);
            return true;
        }

        @Override
        public boolean releaseScanLease(String teamId, String ownerId) {
            releaseTeamIds.add(teamId);
            if (!releaseReturnOverride) {
                releaseFalseReturns.incrementAndGet();
            }
            return releaseReturnOverride;
        }

        @Override
        public boolean isScanLeaseActive(String teamId) {
            return false;
        }
    }

    /** Coordinator that fails acquire for specific teams (returns false). */
    static final class SelectiveFailCoordinator implements IDaemonCoordinator {
        final List<String> acquireCalls = Collections.synchronizedList(new ArrayList<>());
        final java.util.Set<String> teamsToFail;

        SelectiveFailCoordinator(java.util.Set<String> teamsToFail) {
            this.teamsToFail = teamsToFail;
        }

        @Override
        public boolean tryAcquireScanLease(String teamId, String ownerId, long leaseMs) {
            acquireCalls.add(teamId);
            return !teamsToFail.contains(teamId);
        }

        @Override
        public boolean releaseScanLease(String teamId, String ownerId) {
            return true;
        }

        @Override
        public boolean isScanLeaseActive(String teamId) {
            return false;
        }
    }

    static final class StubAgentEngine implements IAgentEngine {
        @Override
        public io.nop.ai.agent.engine.AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            return CompletableFuture.completedFuture(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok",
                            Collections.emptyList(), 1, 1L, 1L, null));
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static Team createTeamWithBoundMember(InMemoryTeamManager mgr,
                                                    String memberName, String sessionId) {
        TeamSpec spec = new TeamSpec("daemon-team-" + System.nanoTime(), "d", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberName + "-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), memberName, sessionId, "actor-" + memberName);
        return team;
    }

    private TeamTaskSchedulerDaemon newDaemon(InMemoryTeamManager mgr,
                                                InMemoryTeamTaskStore store,
                                                IScheduledExecutor scheduler) {
        return new TeamTaskSchedulerDaemon(new StubAgentEngine(), store, mgr, scheduler);
    }

    // ========================================================================
    // 1. NoOp default — zero regression (no team skipped, full scan)
    // ========================================================================

    @Test
    void noOpCoordinatorScansAllTeamsZeroRegression() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team1 = createTeamWithBoundMember(mgr, "w1", "s1");
        Team team2 = createTeamWithBoundMember(mgr, "w2", "s2");
        String a1 = store.createTask(team1.getTeamId(), "A1", "d",
                Collections.emptyList(), "lead").getTaskId();
        String a2 = store.createTask(team2.getTeamId(), "A2", "d",
                Collections.emptyList(), "lead").getTaskId();

        TeamTaskSchedulerDaemon daemon = newDaemon(mgr, store, new NoOpScheduler());
        // No setDaemonCoordinator call — relies on the NoOp shipped default.
        assertSame(NoOpDaemonCoordinator.noOp(), daemon.getDaemonCoordinator(),
                "default coordinator is the NoOp singleton");

        SchedulerScanResult r = daemon.scanOnce();

        assertEquals(0, r.getSkippedCoordinatedTeams(),
                "NoOp coordinator never skips — single-instance behaviour unchanged");
        assertEquals(2, r.getTeamsScanned());
        assertEquals(2, r.getCompletedTasks(), "both teams' tasks dispatched + completed");
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a1).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a2).orElseThrow().getStatus());
    }

    // ========================================================================
    // 2. Wiring (#23) — acquire per team + release on completion
    // ========================================================================

    @Test
    void coordinatorAcquirePerTeamAndReleaseOnCompletion() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team1 = createTeamWithBoundMember(mgr, "w1", "s1");
        Team team2 = createTeamWithBoundMember(mgr, "w2", "s2");
        store.createTask(team1.getTeamId(), "A1", "d", Collections.emptyList(), "lead");
        store.createTask(team2.getTeamId(), "A2", "d", Collections.emptyList(), "lead");

        RecordingCoordinator coord = new RecordingCoordinator();
        TeamTaskSchedulerDaemon daemon = newDaemon(mgr, store, new NoOpScheduler());
        daemon.setDaemonOwnerId("daemon-test-A");
        daemon.setScanLeaseMs(12_345L);
        daemon.setDaemonCoordinator(coord);

        daemon.scanOnce();

        // Wiring proof: tryAcquireScanLease called once per team.
        assertEquals(2, coord.acquireTeamIds.size(),
                "tryAcquireScanLease invoked once per scanned team");
        assertTrue(coord.acquireTeamIds.contains(team1.getTeamId()));
        assertTrue(coord.acquireTeamIds.contains(team2.getTeamId()));

        // The ownerId and leaseMs passed to acquire are the daemon's fields.
        assertEquals("daemon-test-A", coord.acquireOwnersByTeam.get(team1.getTeamId()));
        assertEquals(12_345L, coord.acquireLeasesByTeam.get(team1.getTeamId()));

        // Wiring proof: releaseScanLease called once per scanned team (in finally).
        assertEquals(2, coord.releaseTeamIds.size(),
                "releaseScanLease invoked once per scanned team in finally");
        assertTrue(coord.releaseTeamIds.contains(team1.getTeamId()));
        assertTrue(coord.releaseTeamIds.contains(team2.getTeamId()));
        assertEquals(0, coord.releaseFalseReturns.get(),
                "release returned true (no preempt) — no false-return LOG.warn path triggered");
    }

    // ========================================================================
    // 3. Acquire failure skips the team + counts (No Silent No-Op #24)
    // ========================================================================

    @Test
    void coordinatorAcquireFailureSkipsTeamAndCounts() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team1 = createTeamWithBoundMember(mgr, "w1", "s1");
        Team team2 = createTeamWithBoundMember(mgr, "w2", "s2");
        String a1 = store.createTask(team1.getTeamId(), "A1", "d",
                Collections.emptyList(), "lead").getTaskId();
        String a2 = store.createTask(team2.getTeamId(), "A2", "d",
                Collections.emptyList(), "lead").getTaskId();

        // team1 fails acquire (another instance holds it); team2 succeeds.
        SelectiveFailCoordinator coord = new SelectiveFailCoordinator(
                java.util.Collections.singleton(team1.getTeamId()));
        TeamTaskSchedulerDaemon daemon = newDaemon(mgr, store, new NoOpScheduler());
        daemon.setDaemonCoordinator(coord);

        SchedulerScanResult r = daemon.scanOnce();

        assertEquals(1, r.getSkippedCoordinatedTeams(),
                "team1 skipped — explicit coordination signal, not silent");
        assertEquals(2, r.getTeamsScanned(), "both teams are in the scan list (teamsScanned)");

        // team1's task is NOT dispatched (skipped before topology build).
        assertEquals(TeamTaskStatus.CREATED, store.getTask(a1).orElseThrow().getStatus(),
                "team1 task untouched (skipped — no claim/dispatch attempted)");

        // team2 was scanned normally.
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a2).orElseThrow().getStatus());
        assertEquals(1, r.getCompletedTasks());

        // The acquire was attempted for both teams (we don't know which is
        // contended without asking).
        assertTrue(coord.acquireCalls.contains(team1.getTeamId()));
        assertTrue(coord.acquireCalls.contains(team2.getTeamId()));
    }

    // ========================================================================
    // 4. releaseScanLease false return tolerated (correctness floor = claimTask)
    // ========================================================================

    @Test
    void releaseFailureDoesNotAffectScanResults() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team1 = createTeamWithBoundMember(mgr, "w1", "s1");
        String a1 = store.createTask(team1.getTeamId(), "A1", "d",
                Collections.emptyList(), "lead").getTaskId();

        RecordingCoordinator coord = new RecordingCoordinator();
        coord.releaseReturnOverride = false; // simulate lease preempted mid-scan
        TeamTaskSchedulerDaemon daemon = newDaemon(mgr, store, new NoOpScheduler());
        daemon.setDaemonCoordinator(coord);

        SchedulerScanResult r = daemon.scanOnce();

        // The scan ran to completion (claimTask CAS is the correctness floor).
        assertEquals(0, r.getSkippedCoordinatedTeams());
        assertEquals(1, r.getCompletedTasks());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a1).orElseThrow().getStatus(),
                "scan results unaffected by releaseScanLease false return");
        assertEquals(1, coord.releaseFalseReturns.get(),
                "release returned false once (preempted mid-scan) — tolerated, LOG.warn only");
    }

    // ========================================================================
    // 5. null-safe setDaemonCoordinator → NoOp fallback
    // ========================================================================

    @Test
    void setDaemonCoordinatorNullFallsBackToNoOp() {
        TeamTaskSchedulerDaemon daemon = newDaemon(
                new InMemoryTeamManager(), new InMemoryTeamTaskStore(), new NoOpScheduler());

        daemon.setDaemonCoordinator(null);
        assertSame(NoOpDaemonCoordinator.noOp(), daemon.getDaemonCoordinator(),
                "null setter falls back to the NoOp shipped default (zero regression)");
    }

    // ========================================================================
    // 6. daemonOwnerId default + uniqueness (design 裁定 7)
    // ========================================================================

    @Test
    void daemonOwnerIdDefaultsToUniqueSchedulerPrefix() {
        TeamTaskSchedulerDaemon d1 = newDaemon(
                new InMemoryTeamManager(), new InMemoryTeamTaskStore(), new NoOpScheduler());
        TeamTaskSchedulerDaemon d2 = newDaemon(
                new InMemoryTeamManager(), new InMemoryTeamTaskStore(), new NoOpScheduler());

        String id1 = d1.getDaemonOwnerId();
        String id2 = d2.getDaemonOwnerId();

        assertTrue(id1.startsWith("scheduler-daemon-"),
                "default ownerId has the scheduler-daemon- prefix (design 裁定 7)");
        assertTrue(id2.startsWith("scheduler-daemon-"));
        assertFalse(id1.equals(id2),
                "two daemon instances get distinct ownerIds (UUID-based) — required for "
                        + "coordination to actually contend");
        assertNotNull(id1);
    }

    // ========================================================================
    // 7. scanLeaseMs default (design 裁定 8)
    // ========================================================================

    @Test
    void scanLeaseMsDefaultsToThirtySeconds() {
        TeamTaskSchedulerDaemon daemon = newDaemon(
                new InMemoryTeamManager(), new InMemoryTeamTaskStore(), new NoOpScheduler());
        assertEquals(TeamTaskSchedulerDaemon.DEFAULT_SCAN_LEASE_MS, daemon.getScanLeaseMs(),
                "default scanLeaseMs = 30_000ms (6x default 5s scan interval, design 裁定 8)");
        assertEquals(30_000L, TeamTaskSchedulerDaemon.DEFAULT_SCAN_LEASE_MS);
    }

    // ========================================================================
    // Minimal IScheduledExecutor stub. scanOnce doesn't touch the scheduler
    // (only start/stop do), so all methods can be no-ops for these tests.
    // ========================================================================
    static final class NoOpScheduler implements IScheduledExecutor {
        @Override
        public java.util.concurrent.Future<?> scheduleWithFixedDelay(
                Runnable command, long initialDelay, long delay,
                java.util.concurrent.TimeUnit unit) {
            return new java.util.concurrent.CompletableFuture<>();
        }

        @Override
        public <V> java.util.concurrent.CompletableFuture<V> schedule(
                java.util.concurrent.Callable<V> callable, long delay,
                java.util.concurrent.TimeUnit unit) {
            return new java.util.concurrent.CompletableFuture<>();
        }

        @Override
        public java.util.concurrent.Future<?> scheduleAtFixedRate(
                Runnable command, long initialDelay, long period,
                java.util.concurrent.TimeUnit unit) {
            return new java.util.concurrent.CompletableFuture<>();
        }

        @Override
        public void execute(Runnable command) {
        }

        @Override
        public void destroy() {
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }

        @Override
        public String getName() {
            return "no-op-scheduler";
        }

        @Override
        public io.nop.commons.concurrent.executor.ThreadPoolConfig getConfig() {
            return null;
        }

        @Override
        public io.nop.commons.concurrent.executor.ThreadPoolStats stats() {
            return null;
        }

        @Override
        public <V> java.util.concurrent.CompletableFuture<V> submit(
                java.util.concurrent.Callable<V> callable) {
            return new java.util.concurrent.CompletableFuture<>();
        }

        @Override
        public <V> java.util.concurrent.CompletableFuture<V> submit(
                Runnable task, V result) {
            return java.util.concurrent.CompletableFuture.completedFuture(result);
        }

        @Override
        public void refreshConfig() {
        }
    }
}
