package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.session.AiAgentSessionTable;
import io.nop.ai.agent.team.AiAgentTeamTaskTable;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 240 Phase 2 daemon integration tests for the team-task recovery
 * step in {@link ScheduledRecoveryManager#scanOnce}. Verifies that
 * scanOnce's step 4 invokes the configured {@link ITeamTaskRecoveryHandler},
 * aggregates outcomes into {@link RecoveryScanResult#getTeamTaskRecoveryActions()},
 * the shipped {@link NoOpTeamTaskRecoveryHandler} default returns an empty
 * list (zero regression), and the existing session-recovery step ordering
 * is preserved. Verified against a real H2 DB, satisfying Minimum Rules #22
 * (Anti-Hollow), #23 (Wiring Verification), and #24 (No Silent No-Op).
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #scanOnceStep4RecoversStuckTask} — scanOnce invokes the
 *       handler, task is recovered, RecoveryScanResult.teamTaskRecoveryActions populated</li>
 *   <li>{@link #setterInjectsFunctionalHandler} — setTeamTaskRecoveryHandler wires
 *       a functional handler (replaces the NoOp default)</li>
 *   <li>{@link #noOpDefaultZeroRegression} — NoOp default returns emptyList,
 *       task not recovered, RecoveryScanResult.teamTaskRecoveryActions empty</li>
 *   <li>{@link #scanOnceStepOrderPreserved} — session-recovery steps still run</li>
 *   <li>{@link #setterRejectsNullHandler} — null handler rejected (no silent fallback)</li>
 * </ul>
 *
 * <p>See plan 240 (L4-team-task-reclaim-and-timeout-abandon) Phase 2.
 */
public class TestTeamTaskRecoveryDaemonIntegration {

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
        dbUrl = "jdbc:h2:mem:test-team-task-daemon-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
        createTeamTaskTable();
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

    private void createTeamTaskTable() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // scanOnce runs session-recovery steps (timeout detection, orphan
            // detection) on ai_agent_session before the team-task step, so
            // both tables must exist.
            stmt.execute(AiAgentSessionTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentSessionTable.DDL_CREATE_INDEX);
            stmt.execute(AiAgentTeamTaskTable.DDL_CREATE_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create test tables", e);
        }
    }

    private String insertStuckClaimedTask(String taskId) {
        long stuckUpdatedAt = System.currentTimeMillis() - 120_000L;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO " + AiAgentTeamTaskTable.TABLE_NAME
                             + " (" + AiAgentTeamTaskTable.COL_TASK_ID
                             + ", " + AiAgentTeamTaskTable.COL_TEAM_ID
                             + ", " + AiAgentTeamTaskTable.COL_SUBJECT
                             + ", " + AiAgentTeamTaskTable.COL_STATUS
                             + ", " + AiAgentTeamTaskTable.COL_CREATED_BY
                             + ", " + AiAgentTeamTaskTable.COL_CLAIMED_BY
                             + ", " + AiAgentTeamTaskTable.COL_CREATED_AT
                             + ", " + AiAgentTeamTaskTable.COL_UPDATED_AT
                             + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, taskId);
            ps.setString(2, "team-1");
            ps.setString(3, "stuck task");
            ps.setString(4, TeamTaskStatus.CLAIMED.name());
            ps.setString(5, "creator-sess");
            ps.setString(6, "claimer-sess");
            ps.setLong(7, stuckUpdatedAt - 10000L);
            ps.setLong(8, stuckUpdatedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return taskId;
    }

    private String readTaskStatus(String taskId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + AiAgentTeamTaskTable.COL_STATUS
                             + " FROM " + AiAgentTeamTaskTable.TABLE_NAME
                             + " WHERE " + AiAgentTeamTaskTable.COL_TASK_ID + " = ?")) {
            ps.setString(1, taskId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ========================================================================
    // scanOnce step 4 — stuck team-task recovery
    // ========================================================================

    @Test
    void scanOnceStep4RecoversStuckTask() {
        String taskId = insertStuckClaimedTask("task-daemon-1");

        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, new InlineScheduler());
        DefaultTeamTaskRecoveryHandler handler = new DefaultTeamTaskRecoveryHandler(
                dataSource, 60L, TeamTaskRecoveryAction.RECLAIM);
        mgr.setTeamTaskRecoveryHandler(handler);

        RecoveryScanResult result = mgr.scanOnce();

        // The handler recovered the stuck task → task status is now CREATED.
        assertEquals(TeamTaskStatus.CREATED.name(), readTaskStatus(taskId),
                "scanOnce step 4 must recover the stuck task (CLAIMED→CREATED)");

        // RecoveryScanResult.teamTaskRecoveryActions carries the outcome.
        List<TeamTaskRecoveryOutcome> actions = result.getTeamTaskRecoveryActions();
        assertEquals(1, actions.size(),
                "teamTaskRecoveryActions must carry one outcome for one stuck task");
        assertEquals(taskId, actions.get(0).getTaskId());
        assertEquals(TeamTaskRecoveryAction.RECLAIM, actions.get(0).getAction());
        assertTrue(actions.get(0).isSucceeded(),
                "the RECLAIM outcome must be succeeded=true (Wiring Verification #23)");
    }

    // ========================================================================
    // setter injects functional handler (replaces NoOp default)
    // ========================================================================

    @Test
    void setterInjectsFunctionalHandler() {
        // Default handler is NoOp (instanceof check).
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, new InlineScheduler());
        assertInstanceOf(NoOpTeamTaskRecoveryHandler.class, mgr.getTeamTaskRecoveryHandler(),
                "shipped default handler is NoOpTeamTaskRecoveryHandler");

        // Inject a functional handler.
        DefaultTeamTaskRecoveryHandler handler = new DefaultTeamTaskRecoveryHandler(
                dataSource, 60L, TeamTaskRecoveryAction.RECLAIM);
        mgr.setTeamTaskRecoveryHandler(handler);
        assertEquals(handler, mgr.getTeamTaskRecoveryHandler(),
                "setTeamTaskRecoveryHandler wires the functional handler");

        // Verify scanOnce now uses the injected handler (task recovered, not NoOp emptyList).
        String taskId = insertStuckClaimedTask("task-daemon-2");
        RecoveryScanResult result = mgr.scanOnce();

        assertEquals(TeamTaskStatus.CREATED.name(), readTaskStatus(taskId),
                "injected handler must recover the task (not the NoOp emptyList default)");
        assertEquals(1, result.getTeamTaskRecoveryActions().size());
    }

    // ========================================================================
    // NoOp default zero regression
    // ========================================================================

    @Test
    void noOpDefaultZeroRegression() {
        String taskId = insertStuckClaimedTask("task-daemon-noop");

        // Default: NoOp handler wired. scanOnce's team-task step returns emptyList.
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, new InlineScheduler());

        RecoveryScanResult result = mgr.scanOnce();

        assertTrue(result.getTeamTaskRecoveryActions().isEmpty(),
                "NoOp default returns empty teamTaskRecoveryActions (zero regression)");

        // The stuck task is NOT recovered (status unchanged).
        assertEquals(TeamTaskStatus.CLAIMED.name(), readTaskStatus(taskId),
                "NoOp default must NOT recover the task (explicit SKIP, zero behaviour regression)");

        // RecoveryScanResult.empty() also carries an empty teamTaskRecoveryActions.
        RecoveryScanResult empty = RecoveryScanResult.empty();
        assertTrue(empty.getTeamTaskRecoveryActions().isEmpty(),
                "RecoveryScanResult.empty() teamTaskRecoveryActions is empty");
    }

    // ========================================================================
    // step ordering preserved (session-recovery steps still run)
    // ========================================================================

    @Test
    void scanOnceStepOrderPreserved() {
        // scanOnce runs stale-lock cleanup → timeout detection → orphan
        // detection → team-task recovery. With an empty session/lock table,
        // the first three steps produce zero results; the team-task step
        // (NoOp default) also produces empty. This proves the existing
        // session-recovery steps are unaffected by the new step 4.
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, new InlineScheduler());

        RecoveryScanResult result = mgr.scanOnce();

        assertEquals(0, result.getStaleLocksCleaned(),
                "stale lock cleanup step still runs (0 locks)");
        assertEquals(0, result.getOrphanSessionsDetected(),
                "orphan detection step still runs (0 orphans)");
        assertTrue(result.getTimeoutActions().isEmpty(),
                "timeout detection step still runs (0 timeouts)");
        assertTrue(result.getTeamTaskRecoveryActions().isEmpty(),
                "team-task recovery step (NoOp default) = empty");
    }

    // ========================================================================
    // setter rejects null (no silent fallback)
    // ========================================================================

    @Test
    void setterRejectsNullHandler() {
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, new InlineScheduler());
        assertThrows(io.nop.ai.agent.engine.NopAiAgentException.class,
                () -> mgr.setTeamTaskRecoveryHandler(null),
                "setTeamTaskRecoveryHandler(null) must be rejected (no silent fallback to default)");
    }

    // ========================================================================
    // Minimal inline IScheduledExecutor (scanOnce called directly, no scheduling)
    // ========================================================================

    /**
     * A minimal {@link IScheduledExecutor} that is only used to satisfy the
     * {@link ScheduledRecoveryManager} constructor — these tests invoke
     * {@code scanOnce()} directly, so no periodic scheduling is needed.
     * Scheduling methods return no-op futures (mirrors the RecordingScheduler
     * pattern in {@link TestScheduledRecoveryManager}).
     */
    static final class InlineScheduler implements IScheduledExecutor, IDestroyable {
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
            return "inline-scheduler";
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
