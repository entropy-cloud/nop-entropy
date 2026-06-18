package io.nop.ai.agent.team.scheduler;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 242 Phase 2 honest-failure tests for the
 * {@link TeamTaskSchedulerDaemon} scan-lease coordinator wiring
 * (Minimum Rules #24 No Silent No-Op).
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #leaseFailSkipsTeamAndCountsNotSilent} — a false acquire is
 *       an explicit coordination signal (counted in skippedCoordinatedTeams,
 *       not silently swallowed).</li>
 *   <li>{@link #dbExceptionFromCoordinatorPropagatesAsNopAiAgentException} —
 *       a coordinator that throws (DB error) propagates as
 *       {@link NopAiAgentException} (never swallowed).</li>
 *   <li>{@link #noOpAlwaysSucceedsZeroRegression} — NoOp coordinator never
 *       fails (zero-regression baseline for honest-failure comparison).</li>
 *   <li>{@link #releaseCasFailLogsWarnAndDoesNotAffectResults} — a false
 *       releaseScanLease return is tolerated (LOG.warn); scan results are
 *       unaffected (claimTask CAS is the correctness floor).</li>
 *   <li>{@link #blankDaemonOwnerIdRejected} — coordinator ownerId validation.</li>
 * </ul>
 */
public class TestSchedulerDaemonCoordinationHonestFailure {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Stub coordinator + engine + scheduler
    // ========================================================================

    /** Coordinator that always fails acquire (returns false). */
    static final class AlwaysFailCoordinator implements IDaemonCoordinator {
        final AtomicInteger acquireCalls = new AtomicInteger(0);

        @Override
        public boolean tryAcquireScanLease(String teamId, String ownerId, long leaseMs) {
            acquireCalls.incrementAndGet();
            return false; // always "another instance holds the lease"
        }

        @Override
        public boolean releaseScanLease(String teamId, String ownerId) {
            return false; // never reached (acquire always fails)
        }

        @Override
        public boolean isScanLeaseActive(String teamId) {
            return true;
        }
    }

    /** Coordinator that throws on acquire (simulates DB failure). */
    static final class ThrowingCoordinator implements IDaemonCoordinator {
        @Override
        public boolean tryAcquireScanLease(String teamId, String ownerId, long leaseMs) {
            throw new NopAiAgentException(
                    "simulated DB failure in tryAcquireScanLease for team " + teamId);
        }

        @Override
        public boolean releaseScanLease(String teamId, String ownerId) {
            return false;
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

    // ========================================================================
    // Helpers
    // ========================================================================

    private static Team createTeamWithBoundMember(InMemoryTeamManager mgr, String memberName) {
        TeamSpec spec = new TeamSpec("honest-team-" + System.nanoTime(), "d", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberName + "-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), memberName,
                memberName + "-session", "actor-" + memberName);
        return team;
    }

    private TeamTaskSchedulerDaemon newDaemon(InMemoryTeamManager mgr,
                                                InMemoryTeamTaskStore store) {
        return new TeamTaskSchedulerDaemon(new StubAgentEngine(), store, mgr, new NoOpScheduler());
    }

    // ========================================================================
    // 1. Lease fail = skip + count (NOT silent)
    // ========================================================================

    @Test
    void leaseFailSkipsTeamAndCountsNotSilent() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team1 = createTeamWithBoundMember(mgr, "w1");
        String a1 = store.createTask(team1.getTeamId(), "A1", "d",
                Collections.emptyList(), "lead").getTaskId();

        AlwaysFailCoordinator coord = new AlwaysFailCoordinator();
        TeamTaskSchedulerDaemon daemon = newDaemon(mgr, store);
        daemon.setDaemonCoordinator(coord);

        SchedulerScanResult r = daemon.scanOnce();

        assertEquals(1, r.getSkippedCoordinatedTeams(),
                "lease fail recorded as skippedCoordinatedTeams (explicit signal, not silent)");
        assertEquals(1, coord.acquireCalls.get(),
                "tryAcquireScanLease was actually invoked (wiring #23)");
        assertEquals(0, r.getCompletedTasks(), "no task dispatched (team skipped pre-topology)");
        assertEquals(TeamTaskStatus.CREATED, store.getTask(a1).orElseThrow().getStatus(),
                "task untouched (no claim/dispatch attempted — skipped before topology build)");
    }

    // ========================================================================
    // 2. DB exception propagates as NopAiAgentException (never swallowed)
    // ========================================================================

    @Test
    void dbExceptionFromCoordinatorPropagatesAsNopAiAgentException() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team1 = createTeamWithBoundMember(mgr, "w1");
        store.createTask(team1.getTeamId(), "A1", "d", Collections.emptyList(), "lead");

        ThrowingCoordinator coord = new ThrowingCoordinator();
        TeamTaskSchedulerDaemon daemon = newDaemon(mgr, store);
        daemon.setDaemonCoordinator(coord);

        // The DB failure propagates — it is NOT silently swallowed into a
        // skip or an all-zero result (Minimum Rules #24). The daemon's
        // scanOnceSafe wrapper catches RuntimeException at the scheduler
        // level (LOG.warn + retry next interval), but scanOnce itself
        // honestly throws so callers and tests can observe the failure.
        assertThrows(NopAiAgentException.class, daemon::scanOnce,
                "DB failure in coordinator must propagate as NopAiAgentException "
                        + "(never swallowed into a silent skip)");
    }

    // ========================================================================
    // 3. NoOp always succeeds (zero-regression baseline)
    // ========================================================================

    @Test
    void noOpAlwaysSucceedsZeroRegression() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team1 = createTeamWithBoundMember(mgr, "w1");
        store.createTask(team1.getTeamId(), "A1", "d", Collections.emptyList(), "lead");

        TeamTaskSchedulerDaemon daemon = newDaemon(mgr, store);
        // No setDaemonCoordinator — NoOp shipped default.
        assertTrue(daemon.getDaemonCoordinator() instanceof NoOpDaemonCoordinator);

        SchedulerScanResult r = daemon.scanOnce();
        assertEquals(0, r.getSkippedCoordinatedTeams(),
                "NoOp coordinator always succeeds — zero-regression baseline "
                        + "(no honest-failure path triggered)");
        assertEquals(1, r.getCompletedTasks());
    }

    // ========================================================================
    // 4. releaseScanLease false return tolerated (LOG.warn, results unaffected)
    // ========================================================================

    @Test
    void releaseCasFailLogsWarnAndDoesNotAffectResults() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team1 = createTeamWithBoundMember(mgr, "w1");
        String a1 = store.createTask(team1.getTeamId(), "A1", "d",
                Collections.emptyList(), "lead").getTaskId();

        // Coordinator that succeeds acquire but fails release (simulates
        // lease preempted mid-scan by TTL expiry).
        IDaemonCoordinator coord = new IDaemonCoordinator() {
            @Override
            public boolean tryAcquireScanLease(String teamId, String ownerId, long leaseMs) {
                return true;
            }

            @Override
            public boolean releaseScanLease(String teamId, String ownerId) {
                return false; // lease no longer ours
            }

            @Override
            public boolean isScanLeaseActive(String teamId) {
                return false;
            }
        };
        TeamTaskSchedulerDaemon daemon = newDaemon(mgr, store);
        daemon.setDaemonCoordinator(coord);

        SchedulerScanResult r = daemon.scanOnce();

        // Scan ran to completion — releaseScanLease false is tolerated.
        assertEquals(0, r.getSkippedCoordinatedTeams());
        assertEquals(1, r.getCompletedTasks(),
                "scan results unaffected by releaseScanLease false return");
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a1).orElseThrow().getStatus(),
                "task completed normally (claimTask CAS is the correctness floor, not the lease)");
    }

    // ========================================================================
    // 5. Blank daemonOwnerId rejected (input contract, No Silent No-Op)
    // ========================================================================

    @Test
    void blankDaemonOwnerIdRejected() {
        TeamTaskSchedulerDaemon daemon = newDaemon(
                new InMemoryTeamManager(), new InMemoryTeamTaskStore());
        assertThrows(NullPointerException.class, () -> daemon.setDaemonOwnerId(null),
                "null ownerId rejected");
        assertThrows(NopAiAgentException.class, () -> daemon.setDaemonOwnerId("  "),
                "blank ownerId rejected (a shared blank ownerId would disable coordination)");
        assertThrows(NopAiAgentException.class, () -> daemon.setScanLeaseMs(0L),
                "scanLeaseMs must be > 0");
        assertThrows(NopAiAgentException.class, () -> daemon.setScanLeaseMs(-1L),
                "scanLeaseMs must be > 0");
    }
}
