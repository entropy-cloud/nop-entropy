package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.session.AiAgentSessionTable;
import io.nop.ai.agent.team.AiAgentTeamTaskTable;
import io.nop.ai.agent.team.DbTeamTaskStore;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import io.nop.commons.lang.IDestroyable;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 240 Phase 3 end-to-end tests for team-task RE-CLAIM + 超时自动 ABANDON.
 * Verifies the full self-healing path: a stuck CLAIMED task (member crashed)
 * → daemon scanOnce → handler detects + recovers → task reset to CREATED
 * and re-claimable (RECLAIM) or marked terminal (ABORT), and a stuck task
 * blocking a DAG unblocks after recovery. Satisfies Minimum Rules #22
 * (Anti-Hollow), #23 (Wiring Verification), and #24 (No Silent No-Op).
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #reclaimPathStuckTaskResetAndReClaimable} — full RECLAIM path</li>
 *   <li>{@link #abortPathStuckTaskMarkedTerminal} — full ABORT path + terminal irreversibility</li>
 *   <li>{@link #dagSelfHealingUnblocksBlockedSuccessor} — DAG self-heal</li>
 *   <li>{@link #noOpDefaultZeroRegressionE2E} — NoOp default = no recovery</li>
 * </ul>
 *
 * <p>See plan 240 (L4-team-task-reclaim-and-timeout-abandon) Phase 3.
 */
public class TestTeamTaskRecoveryEndToEnd {

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
        dbUrl = "jdbc:h2:mem:test-team-task-e2e-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
        createTables();
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

    private void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentSessionTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentSessionTable.DDL_CREATE_INDEX);
            stmt.execute(AiAgentTeamTaskTable.DDL_CREATE_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create test tables", e);
        }
    }

    /**
     * Force a CLAIMED task's UPDATED_AT to a past timestamp, simulating a
     * stuck task whose claimer crashed long ago.
     */
    private void forceStuckUpdatedAt(String taskId, long pastMillis) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE " + AiAgentTeamTaskTable.TABLE_NAME
                             + " SET " + AiAgentTeamTaskTable.COL_UPDATED_AT + " = ? "
                             + "WHERE " + AiAgentTeamTaskTable.COL_TASK_ID + " = ?")) {
            ps.setLong(1, pastMillis);
            ps.setString(2, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ========================================================================
    // E2E RECLAIM path
    // ========================================================================

    @Test
    void reclaimPathStuckTaskResetAndReClaimable() {
        // Construct a team with two members (LEAD + MEMBER).
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(new TeamSpec("team-e2e", "desc", "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                0));
        mgr.bindMemberSession(team.getTeamId(), "worker", "member-1-sess", "actor-1");

        // Create a task and have member-1 claim it, then simulate a crash:
        // the task is CLAIMED but its UPDATED_AT is far in the past (stuck).
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        TeamTask task = store.createTask(team.getTeamId(), "Do work", "desc",
                Collections.emptyList(), "lead-sess");
        store.claimTask(task.getTaskId(), "member-1-sess");
        // Simulate crash: member-1 disappeared, task stuck in CLAIMED.
        forceStuckUpdatedAt(task.getTaskId(), System.currentTimeMillis() - 120_000L);

        // Wire the recovery daemon with a RECLAIM handler.
        ScheduledRecoveryManager daemon = new ScheduledRecoveryManager(dataSource, new NoScheduleExecutor());
        DefaultTeamTaskRecoveryHandler handler = new DefaultTeamTaskRecoveryHandler(
                dataSource, 60L, TeamTaskRecoveryAction.RECLAIM);
        daemon.setTeamTaskRecoveryHandler(handler);

        // Run the recovery scan.
        RecoveryScanResult result = daemon.scanOnce();

        // The stuck task was recovered (CLAIMED → CREATED, claimedBy cleared).
        TeamTask recovered = store.getTask(task.getTaskId()).orElseThrow();
        assertEquals(TeamTaskStatus.CREATED, recovered.getStatus(),
                "E2E RECLAIM: stuck CLAIMED task must be reset to CREATED by the daemon");
        assertNull(recovered.getClaimedBy(),
                "E2E RECLAIM: claimedBy must be cleared (re-claimable)");

        // RecoveryScanResult carries the successful RECLAIM outcome.
        List<TeamTaskRecoveryOutcome> actions = result.getTeamTaskRecoveryActions();
        assertEquals(1, actions.size());
        TeamTaskRecoveryOutcome outcome = actions.get(0);
        assertEquals(task.getTaskId(), outcome.getTaskId());
        assertEquals(TeamTaskRecoveryAction.RECLAIM, outcome.getAction());
        assertTrue(outcome.isSucceeded(),
                "E2E RECLAIM: outcome must be succeeded=true (Anti-Hollow)");

        // Anti-Hollow: another member can now claim the reclaimed task.
        // Bind member-2 and claim.
        mgr.bindMemberSession(team.getTeamId(), "worker", "member-2-sess", "actor-2");
        Optional<TeamTask> reclaimed = store.claimTask(task.getTaskId(), "member-2-sess");
        assertTrue(reclaimed.isPresent(),
                "E2E RECLAIM: reclaimed task must be re-claimable by another member (Anti-Hollow)");
        assertEquals(TeamTaskStatus.CLAIMED, reclaimed.get().getStatus());
        assertEquals("member-2-sess", reclaimed.get().getClaimedBy());

        // And member-2 can complete it — full self-heal verified.
        Optional<TeamTask> completed = store.completeTask(task.getTaskId(), "member-2-sess");
        assertTrue(completed.isPresent());
        assertEquals(TeamTaskStatus.COMPLETED, completed.get().getStatus());
    }

    // ========================================================================
    // E2E ABORT path
    // ========================================================================

    @Test
    void abortPathStuckTaskMarkedTerminal() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        TeamTask task = store.createTask("team-abort", "Do work", "desc",
                Collections.emptyList(), "lead-sess");
        store.claimTask(task.getTaskId(), "member-1-sess");
        forceStuckUpdatedAt(task.getTaskId(), System.currentTimeMillis() - 120_000L);

        ScheduledRecoveryManager daemon = new ScheduledRecoveryManager(dataSource, new NoScheduleExecutor());
        DefaultTeamTaskRecoveryHandler handler = new DefaultTeamTaskRecoveryHandler(
                dataSource, 60L, TeamTaskRecoveryAction.ABORT);
        daemon.setTeamTaskRecoveryHandler(handler);

        RecoveryScanResult result = daemon.scanOnce();

        // The stuck task was marked ABANDONED (terminal).
        TeamTask abandoned = store.getTask(task.getTaskId()).orElseThrow();
        assertEquals(TeamTaskStatus.ABANDONED, abandoned.getStatus(),
                "E2E ABORT: stuck CLAIMED task must be marked ABANDONED (terminal)");

        List<TeamTaskRecoveryOutcome> actions = result.getTeamTaskRecoveryActions();
        assertEquals(1, actions.size());
        assertEquals(TeamTaskRecoveryAction.ABORT, actions.get(0).getAction());
        assertTrue(actions.get(0).isSucceeded());

        // Terminal irreversibility: reclaimTask on an ABANDONED task returns empty.
        Optional<TeamTask> reclaimAttempt = store.reclaimTask(task.getTaskId(), "reclaimer-sess");
        assertTrue(reclaimAttempt.isEmpty(),
                "E2E ABORT: terminal (ABANDONED) task cannot be reclaimed — terminal states are irreversible");
        assertEquals(TeamTaskStatus.ABANDONED, store.getTask(task.getTaskId()).orElseThrow().getStatus());
    }

    // ========================================================================
    // E2E DAG self-healing
    // ========================================================================

    @Test
    void dagSelfHealingUnblocksBlockedSuccessor() {
        // Construct a 2-task DAG: t2 blockedBy t1. Member-1 claims t1 then
        // crashes (t1 stuck CLAIMED). The daemon RECLAIMs t1 → t1 back to
        // CREATED → member-2 claims + completes t1 → t2 is now ready
        // (its only dependency t1 is COMPLETED). This closes the "stuck
        // CLAIMED permanently blocks the DAG" gap.
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        TeamTask t1 = store.createTask("team-dag", "t1", "desc",
                Collections.emptyList(), "lead-sess");
        TeamTask t2 = store.createTask("team-dag", "t2", "desc",
                Collections.singletonList(t1.getTaskId()), "lead-sess");

        // Member-1 claims t1 then crashes (stuck).
        store.claimTask(t1.getTaskId(), "member-1-sess");
        forceStuckUpdatedAt(t1.getTaskId(), System.currentTimeMillis() - 120_000L);

        // Before recovery: t1 is CLAIMED (stuck), t2 is CREATED but blocked
        // (its dependency t1 is not COMPLETED). The DAG is stuck.
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(t1.getTaskId()).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.CREATED, store.getTask(t2.getTaskId()).orElseThrow().getStatus());

        // Daemon RECLAIMs the stuck t1.
        ScheduledRecoveryManager daemon = new ScheduledRecoveryManager(dataSource, new NoScheduleExecutor());
        daemon.setTeamTaskRecoveryHandler(new DefaultTeamTaskRecoveryHandler(
                dataSource, 60L, TeamTaskRecoveryAction.RECLAIM));
        daemon.scanOnce();

        // t1 is now CREATED (re-claimable).
        assertEquals(TeamTaskStatus.CREATED, store.getTask(t1.getTaskId()).orElseThrow().getStatus(),
                "DAG self-heal: stuck t1 reclaimed to CREATED");

        // Member-2 claims + completes t1 → the DAG can now advance to t2.
        store.claimTask(t1.getTaskId(), "member-2-sess");
        store.completeTask(t1.getTaskId(), "member-2-sess");
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(t1.getTaskId()).orElseThrow().getStatus(),
                "DAG self-heal: member-2 completed t1 after reclaim");

        // t2's only dependency (t1) is now COMPLETED → t2 is ready to be claimed.
        Optional<TeamTask> t2Claim = store.claimTask(t2.getTaskId(), "member-2-sess");
        assertTrue(t2Claim.isPresent(),
                "DAG self-heal: t2 is now claimable after t1 was recovered + completed (DAG unblocked)");
        store.completeTask(t2.getTaskId(), "member-2-sess");
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(t2.getTaskId()).orElseThrow().getStatus(),
                "DAG self-heal: t2 completed — full DAG advanced after self-heal");
    }

    // ========================================================================
    // E2E NoOp default zero regression
    // ========================================================================

    @Test
    void noOpDefaultZeroRegressionE2E() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        TeamTask task = store.createTask("team-noop", "Do work", "desc",
                Collections.emptyList(), "lead-sess");
        store.claimTask(task.getTaskId(), "member-1-sess");
        forceStuckUpdatedAt(task.getTaskId(), System.currentTimeMillis() - 120_000L);

        // Default daemon: NoOp handler wired (shipped default).
        ScheduledRecoveryManager daemon = new ScheduledRecoveryManager(dataSource, new NoScheduleExecutor());

        RecoveryScanResult result = daemon.scanOnce();

        // The stuck task is NOT recovered (status unchanged) — NoOp default
        // is an explicit SKIP, not a pretend-recovery.
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(task.getTaskId()).orElseThrow().getStatus(),
                "E2E NoOp: stuck task must NOT be recovered under the shipped NoOp default (zero regression)");
        assertTrue(result.getTeamTaskRecoveryActions().isEmpty(),
                "E2E NoOp: teamTaskRecoveryActions is empty (explicit SKIP)");
    }

    // ========================================================================
    // Minimal IScheduledExecutor (scanOnce called directly, no scheduling)
    // ========================================================================

    static final class NoScheduleExecutor implements IScheduledExecutor, IDestroyable {
        private boolean destroyed;

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public void execute(Runnable command) {
        }

        @Override
        public void destroy() {
            destroyed = true;
        }

        @Override
        public boolean isDestroyed() {
            return destroyed;
        }

        @Override
        public String getName() {
            return "no-schedule";
        }

        @Override
        public ThreadPoolConfig getConfig() {
            return null;
        }

        @Override
        public ThreadPoolStats stats() {
            return null;
        }

        @Override
        public <V> CompletableFuture<V> submit(Callable<V> callable) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public void refreshConfig() {
        }
    }
}
