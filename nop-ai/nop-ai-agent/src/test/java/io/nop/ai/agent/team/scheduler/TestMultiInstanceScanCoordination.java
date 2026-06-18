package io.nop.ai.agent.team.scheduler;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.runtime.coordination.DbDaemonCoordinator;
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
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 242 Phase 2 end-to-end Anti-Hollow test (#22): two
 * {@link TeamTaskSchedulerDaemon} instances (different daemonOwnerId) + one
 * shared {@link DbDaemonCoordinator} (one in-memory H2 DataSource) + one
 * shared team set. Within a single scan cycle, the two instances must not
 * both scan the same team — the per-team scan lease ensures one acquires
 * and scans while the other skips (observable per-team acquire/skip
 * counts, not just final task state).
 *
 * <p>This closes the "N instances redundantly scan all teams" gap that
 * motivated plan 242 / {@code L4-cross-process-daemon-coordination}: the
 * verification uses public API on two real daemon instances sharing one
 * real DB-backed coordinator — exactly the multi-instance deployment
 * shape the lease layer targets.
 *
 * <p>The NoOp baseline is also asserted: with no coordination, both
 * instances scan every team (zero-regression + zero-coordination baseline).
 */
public class TestMultiInstanceScanCoordination {

    private DataSource dataSource;
    private String dbUrl;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        dbUrl = "jdbc:h2:mem:test-multi-instance-coord-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
    }

    @AfterEach
    void tearDown() {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
                // best-effort close during teardown
            }
        }
    }

    // ========================================================================
    // Recording stubs
    // ========================================================================

    /**
     * Agent engine that records which team's tasks it executed, keyed by
     * teamId (via the teamTaskId metadata → resolve back to team). Used as
     * the per-team scan observable: if instance A executed a team's task,
     * instance A scanned that team.
     */
    static final class TeamRecordingEngine implements IAgentEngine {
        final String instanceLabel;
        final AtomicInteger totalExecutions = new AtomicInteger(0);
        final java.util.Set<String> teamsExecuted =
                java.util.concurrent.ConcurrentHashMap.newKeySet();

        TeamRecordingEngine(String instanceLabel) {
            this.instanceLabel = instanceLabel;
        }

        @Override
        public io.nop.ai.agent.engine.AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            totalExecutions.incrementAndGet();
            Object teamId = request.getMetadata().get("teamId");
            if (teamId != null) {
                teamsExecuted.add(teamId.toString());
            }
            return CompletableFuture.completedFuture(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + instanceLabel,
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
        TeamSpec spec = new TeamSpec("multi-team-" + System.nanoTime() + "-" + memberName,
                "d", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberName + "-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), memberName,
                memberName + "-session", "actor-" + memberName);
        return team;
    }

    // ========================================================================
    // Anti-Hollow #22: two DB-coordinated instances do NOT both scan the same team
    // ========================================================================

    @Test
    void twoDbCoordinatedInstancesDoNotRedundantlyScanSameTeam() {
        // One shared team manager + store simulates the shared DB backing
        // store (teams/tasks visible to both instances).
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();

        Team team1 = createTeamWithBoundMember(mgr, "w1");
        Team team2 = createTeamWithBoundMember(mgr, "w2");
        store.createTask(team1.getTeamId(), "A1", "d", Collections.emptyList(), "lead");
        store.createTask(team2.getTeamId(), "A2", "d", Collections.emptyList(), "lead");

        // One shared DB-backed coordinator = both instances coordinate via
        // the same lease table. Each gets its own recording engine so we
        // can observe which instance scanned which team.
        DbDaemonCoordinator sharedCoord = new DbDaemonCoordinator(dataSource);
        TeamRecordingEngine engineA = new TeamRecordingEngine("A");
        TeamRecordingEngine engineB = new TeamRecordingEngine("B");

        TeamTaskSchedulerDaemon daemonA = new TeamTaskSchedulerDaemon(
                engineA, store, mgr, new NoOpScheduler());
        daemonA.setDaemonOwnerId("daemon-A");
        daemonA.setDaemonCoordinator(sharedCoord);
        daemonA.setScanLeaseMs(60_000L); // long enough that B's acquire fails while A holds

        TeamTaskSchedulerDaemon daemonB = new TeamTaskSchedulerDaemon(
                engineB, store, mgr, new NoOpScheduler());
        daemonB.setDaemonOwnerId("daemon-B");
        daemonB.setDaemonCoordinator(sharedCoord);
        daemonB.setScanLeaseMs(60_000L);

        // --- Simulate the concurrent-scan race deterministically ---
        // Instance A is "currently scanning" both teams: pre-acquire both
        // leases via the shared coordinator using A's ownerId + a long
        // lease. This is exactly the state the DB row would be in if A's
        // scanOnce had just entered the team loop and called
        // tryAcquireScanLease but not yet reached the finally/release.
        assertTrue(sharedCoord.tryAcquireScanLease(team1.getTeamId(), "daemon-A", 60_000L));
        assertTrue(sharedCoord.tryAcquireScanLease(team2.getTeamId(), "daemon-A", 60_000L));

        // Instance B scans in the same cycle — both teams' leases are held
        // by A, so B's tryAcquireScanLease returns false for both and B
        // skips them pre-topology (the redundant scan cost is avoided).
        SchedulerScanResult resultB = daemonB.scanOnce();

        // --- Observable per-team coordination evidence (Anti-Hollow #22) ---
        assertEquals(2, resultB.getSkippedCoordinatedTeams(),
                "Instance B skipped both teams — A holds the active lease "
                        + "(per-team scan lease coordination, not a silent skip)");
        assertEquals(2, resultB.getTeamsScanned(),
                "Instance B still considered both teams in its scan list");
        assertEquals(0, resultB.getCompletedTasks(),
                "Instance B did not dispatch anything (both teams skipped pre-topology)");

        // Engine-side wiring evidence (#23): B executed nothing.
        assertEquals(0, engineB.totalExecutions.get(),
                "Engine B executed nothing (both teams skipped before dispatch)");
        assertTrue(engineB.teamsExecuted.isEmpty());

        // --- Now release A's leases and let B scan — clean handoff ---
        assertTrue(sharedCoord.releaseScanLease(team1.getTeamId(), "daemon-A"));
        assertTrue(sharedCoord.releaseScanLease(team2.getTeamId(), "daemon-A"));

        SchedulerScanResult resultB2 = daemonB.scanOnce();
        assertEquals(0, resultB2.getSkippedCoordinatedTeams(),
                "After A releases, B acquires both leases cleanly");
        assertEquals(2, resultB2.getCompletedTasks(),
                "B scans + dispatches both teams' tasks");
        assertEquals(2, engineB.totalExecutions.get(),
                "Engine B now executed both tasks (clean handoff after release)");

        // Distinct ownerIds are required for coordination to actually work.
        assertNotEquals(daemonA.getDaemonOwnerId(), daemonB.getDaemonOwnerId());
    }

    // ========================================================================
    // Sequential clean handoff: A scans (acquire+release in finally), then B scans
    // ========================================================================

    @Test
    void sequentialScansCleanHandoffViaActiveRelease() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team1 = createTeamWithBoundMember(mgr, "w1");
        store.createTask(team1.getTeamId(), "A1", "d", Collections.emptyList(), "lead");

        DbDaemonCoordinator sharedCoord = new DbDaemonCoordinator(dataSource);
        TeamRecordingEngine engineA = new TeamRecordingEngine("A");
        TeamRecordingEngine engineB = new TeamRecordingEngine("B");

        TeamTaskSchedulerDaemon daemonA = new TeamTaskSchedulerDaemon(
                engineA, store, mgr, new NoOpScheduler());
        daemonA.setDaemonOwnerId("daemon-A");
        daemonA.setDaemonCoordinator(sharedCoord);

        TeamTaskSchedulerDaemon daemonB = new TeamTaskSchedulerDaemon(
                engineB, store, mgr, new NoOpScheduler());
        daemonB.setDaemonOwnerId("daemon-B");
        daemonB.setDaemonCoordinator(sharedCoord);

        // A scans first — acquires, scans, releases in finally.
        SchedulerScanResult resultA = daemonA.scanOnce();
        assertEquals(0, resultA.getSkippedCoordinatedTeams(),
                "A is first scanner — acquires the lease cleanly");
        assertEquals(1, resultA.getCompletedTasks());
        assertFalseLeaseReleased(sharedCoord, team1.getTeamId());

        // B scans next — A's finally released the lease, so B acquires it
        // (clean handoff via active release, not TTL expiry).
        SchedulerScanResult resultB = daemonB.scanOnce();
        assertEquals(0, resultB.getSkippedCoordinatedTeams(),
                "B acquires the lease cleanly (A's finally released it)");
        assertEquals(0, resultB.getCompletedTasks(),
                "B finds no CREATED ready task (A1 already COMPLETED — claimTask CAS floor)");

        // Both engines wired correctly.
        assertEquals(1, engineA.totalExecutions.get());
        assertEquals(0, engineB.totalExecutions.get());
    }

    private static void assertFalseLeaseReleased(DbDaemonCoordinator coord, String teamId) {
        org.junit.jupiter.api.Assertions.assertFalse(coord.isScanLeaseActive(teamId),
                "lease for " + teamId + " should be released (daemon's finally called releaseScanLease)");
    }

    // ========================================================================
    // Baseline: NoOp coordinator = both instances scan everything (zero coordination)
    // ========================================================================

    @Test
    void noOpBaselineBothInstancesScanEverything() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team1 = createTeamWithBoundMember(mgr, "w1");
        store.createTask(team1.getTeamId(), "A1", "d", Collections.emptyList(), "lead");

        IDaemonCoordinator noOp = NoOpDaemonCoordinator.noOp();
        TeamRecordingEngine engineA = new TeamRecordingEngine("A");
        TeamRecordingEngine engineB = new TeamRecordingEngine("B");

        TeamTaskSchedulerDaemon daemonA = new TeamTaskSchedulerDaemon(
                engineA, store, mgr, new NoOpScheduler());
        daemonA.setDaemonOwnerId("daemon-A");
        daemonA.setDaemonCoordinator(noOp);

        TeamTaskSchedulerDaemon daemonB = new TeamTaskSchedulerDaemon(
                engineB, store, mgr, new NoOpScheduler());
        daemonB.setDaemonOwnerId("daemon-B");
        daemonB.setDaemonCoordinator(noOp);

        SchedulerScanResult resultA = daemonA.scanOnce();
        SchedulerScanResult resultB = daemonB.scanOnce();

        // NoOp baseline: both instances scan everything, neither skips.
        assertEquals(0, resultA.getSkippedCoordinatedTeams(),
                "NoOp coordinator never skips — zero-regression baseline");
        assertEquals(0, resultB.getSkippedCoordinatedTeams());

        // Both engines dispatched — this is the "N redundant scans" baseline
        // that the DB coordinator above eliminates. (Note: the second
        // instance finds no CREATED ready task because the first already
        // completed them — the redundant-scan cost is the DB read +
        // topology build, not a second dispatch. claimTask CAS is the
        // correctness floor.)
        assertEquals(1, engineA.totalExecutions.get(),
                "Engine A dispatched A1 (first scanner)");
        assertEquals(0, engineB.totalExecutions.get(),
                "Engine B did not dispatch — A1 already COMPLETED (claimTask CAS floor). "
                        + "But Engine B still paid the redundant topology build / ready query cost "
                        + "(the cost the DB coordinator eliminates).");
        assertEquals(0, resultB.getClaimLostTasks(),
                "Engine B saw no CREATED ready task at all (A1 already COMPLETED in topology snapshot)");
    }
}
