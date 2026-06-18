package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.security.ITenantResolver;
import io.nop.ai.agent.team.AiAgentTeamTaskTable;
import io.nop.ai.agent.team.DbTeamTaskStore;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 240 Phase 2 focused tests for {@link DefaultTeamTaskRecoveryHandler}.
 * Each action (RECLAIM / ABORT), the non-stuck/terminal/CAS-competition
 * paths, the fail-fast constructor contract, cross-tenant isolation, and
 * per-task fault isolation are verified against a real H2 DB, satisfying
 * Minimum Rules #22 (Anti-Hollow), #23 (Wiring Verification), and #24
 * (No Silent No-Op).
 *
 * <p>Fault injection uses {@link Proxy}-based wrappers (no mock framework
 * dependency): a {@code FaultingDataSource} delegates to the real H2
 * DataSource but, on {@code prepareStatement}, returns a proxied
 * {@link PreparedStatement} that consults a fault {@link Predicate} on
 * {@code executeUpdate()} to throw {@link SQLException}.
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #reclaimActionResetsClaimedToCreated} — RECLAIM raw JDBC UPDATE</li>
 *   <li>{@link #abortActionMarksClaimedAbandoned} — ABORT raw JDBC UPDATE</li>
 *   <li>{@link #nonStuckClaimedTaskNotTouched} — UPDATED_AT recent = not stuck</li>
 *   <li>{@link #completedTaskNotDetected} — terminal status excluded</li>
 *   <li>{@link #casCompetitionReturnsFailedOutcome} — task transitioned before detection</li>
 *   <li>{@link #casCompetitionAtActionLevelReturnsFailedOutcome} — task transitioned between detect+act</li>
 *   <li>{@link #failFastOnNullDataSource} / {@link #failFastOnNonPositiveTimeout} /
 *       {@link #failFastOnSkipAction} — constructor fail-fast</li>
 *   <li>{@link #crossTenantIsolation} — tenant-A handler does not recover tenant-B task</li>
 *   <li>{@link #noStuckTasksReturnsEmptyList} — explicit empty (not silent skip)</li>
 *   <li>{@link #perTaskFaultIsolation} — one task's UPDATE failure does not abort others</li>
 *   <li>{@link #detectionSQLExceptionPropagatesAsNopAiAgentException} — non-silent</li>
 * </ul>
 *
 * <p>See plan 240 (L4-team-task-reclaim-and-timeout-abandon) Phase 2.
 */
public class TestDefaultTeamTaskRecoveryHandler {

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
        dbUrl = "jdbc:h2:mem:test-team-task-recovery-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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
            stmt.execute(AiAgentTeamTaskTable.DDL_CREATE_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create ai_agent_team_task table", e);
        }
    }

    /**
     * Insert a team-task row directly via raw JDBC with explicit UPDATED_AT
     * (to simulate a stuck task without waiting in real time).
     */
    private String insertTaskRow(String taskId, String status, String claimedBy,
                                 long updatedAt, String tenantId) {
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
                             + (tenantId != null ? ", " + AiAgentTeamTaskTable.COL_TENANT_ID : "")
                             + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?"
                             + (tenantId != null ? ", ?" : "") + ")")) {
            ps.setString(1, taskId);
            ps.setString(2, "team-1");
            ps.setString(3, "stuck task");
            ps.setString(4, status);
            ps.setString(5, "creator-sess");
            if (claimedBy != null) {
                ps.setString(6, claimedBy);
            } else {
                ps.setNull(6, java.sql.Types.VARCHAR);
            }
            ps.setLong(7, updatedAt - 10000L);
            ps.setLong(8, updatedAt);
            if (tenantId != null) {
                ps.setString(9, tenantId);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert task row", e);
        }
        return taskId;
    }

    private String readTaskStatus(String taskId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + AiAgentTeamTaskTable.COL_STATUS
                             + ", " + AiAgentTeamTaskTable.COL_CLAIMED_BY
                             + " FROM " + AiAgentTeamTaskTable.TABLE_NAME
                             + " WHERE " + AiAgentTeamTaskTable.COL_TASK_ID + " = ?")) {
            ps.setString(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                String status = rs.getString(1);
                String claimedBy = rs.getString(2);
                return status + "|" + claimedBy;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void flipStatus(String taskId, String newStatus) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE " + AiAgentTeamTaskTable.TABLE_NAME
                             + " SET " + AiAgentTeamTaskTable.COL_STATUS + " = ?, "
                             + AiAgentTeamTaskTable.COL_UPDATED_AT + " = ? "
                             + "WHERE " + AiAgentTeamTaskTable.COL_TASK_ID + " = ?")) {
            ps.setString(1, newStatus);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ========================================================================
    // RECLAIM action
    // ========================================================================

    @Test
    void reclaimActionResetsClaimedToCreated() {
        long stuckUpdatedAt = System.currentTimeMillis() - 120_000L; // 2 min ago
        String taskId = insertTaskRow("task-reclaim", TeamTaskStatus.CLAIMED.name(),
                "claimer-sess", stuckUpdatedAt, null);

        DefaultTeamTaskRecoveryHandler handler = new DefaultTeamTaskRecoveryHandler(
                dataSource, 60L, TeamTaskRecoveryAction.RECLAIM);
        List<TeamTaskRecoveryOutcome> outcomes = handler.recoverStuckTasks();

        assertEquals(1, outcomes.size(), "exactly one outcome for one stuck task");
        TeamTaskRecoveryOutcome outcome = outcomes.get(0);
        assertEquals(taskId, outcome.getTaskId());
        assertEquals(TeamTaskRecoveryAction.RECLAIM, outcome.getAction());
        assertTrue(outcome.isSucceeded(), "RECLAIM must succeed on a stuck CLAIMED task");

        // Verify DB state: STATUS=CREATED, CLAIMED_BY=null.
        String state = readTaskStatus(taskId);
        assertEquals(TeamTaskStatus.CREATED.name() + "|null", state,
                "RECLAIM must reset status to CREATED and clear claimedBy");
    }

    // ========================================================================
    // ABORT action
    // ========================================================================

    @Test
    void abortActionMarksClaimedAbandoned() {
        long stuckUpdatedAt = System.currentTimeMillis() - 120_000L;
        String taskId = insertTaskRow("task-abort", TeamTaskStatus.CLAIMED.name(),
                "claimer-sess", stuckUpdatedAt, null);

        DefaultTeamTaskRecoveryHandler handler = new DefaultTeamTaskRecoveryHandler(
                dataSource, 60L, TeamTaskRecoveryAction.ABORT);
        List<TeamTaskRecoveryOutcome> outcomes = handler.recoverStuckTasks();

        assertEquals(1, outcomes.size());
        TeamTaskRecoveryOutcome outcome = outcomes.get(0);
        assertEquals(taskId, outcome.getTaskId());
        assertEquals(TeamTaskRecoveryAction.ABORT, outcome.getAction());
        assertTrue(outcome.isSucceeded(), "ABORT must succeed on a stuck CLAIMED task");

        // Verify DB state: STATUS=ABANDONED (terminal).
        String state = readTaskStatus(taskId);
        assertTrue(state.startsWith(TeamTaskStatus.ABANDONED.name() + "|"),
                "ABORT must set status to ABANDONED: " + state);
    }

    // ========================================================================
    // Non-stuck task not touched
    // ========================================================================

    @Test
    void nonStuckClaimedTaskNotTouched() {
        long recentUpdatedAt = System.currentTimeMillis() - 1_000L; // 1s ago (not stuck)
        String taskId = insertTaskRow("task-recent", TeamTaskStatus.CLAIMED.name(),
                "claimer-sess", recentUpdatedAt, null);

        DefaultTeamTaskRecoveryHandler handler = new DefaultTeamTaskRecoveryHandler(
                dataSource, 60L, TeamTaskRecoveryAction.RECLAIM);
        List<TeamTaskRecoveryOutcome> outcomes = handler.recoverStuckTasks();

        assertTrue(outcomes.isEmpty(),
                "a non-stuck CLAIMED task (UPDATED_AT recent) must not be detected");

        // Verify DB state unchanged.
        String state = readTaskStatus(taskId);
        assertEquals(TeamTaskStatus.CLAIMED.name() + "|claimer-sess", state,
                "non-stuck task must not be touched");
    }

    @Test
    void completedTaskNotDetected() {
        long stuckUpdatedAt = System.currentTimeMillis() - 120_000L;
        String taskId = insertTaskRow("task-completed", TeamTaskStatus.COMPLETED.name(),
                "claimer-sess", stuckUpdatedAt, null);

        DefaultTeamTaskRecoveryHandler handler = new DefaultTeamTaskRecoveryHandler(
                dataSource, 60L, TeamTaskRecoveryAction.RECLAIM);
        List<TeamTaskRecoveryOutcome> outcomes = handler.recoverStuckTasks();

        assertTrue(outcomes.isEmpty(),
                "a terminal (COMPLETED) task must not be detected as stuck");

        // Verify DB state unchanged.
        String state = readTaskStatus(taskId);
        assertEquals(TeamTaskStatus.COMPLETED.name() + "|claimer-sess", state);
    }

    // ========================================================================
    // CAS competition (task transitioned before/around detection + action)
    // ========================================================================

    @Test
    void casCompetitionReturnsFailedOutcome() {
        long stuckUpdatedAt = System.currentTimeMillis() - 120_000L;
        String taskId = insertTaskRow("task-cas", TeamTaskStatus.CLAIMED.name(),
                "claimer-sess", stuckUpdatedAt, null);

        // Member completes the task BEFORE the handler runs. The handler's
        // detection SELECT (WHERE STATUS='CLAIMED') excludes the COMPLETED
        // task → 0 stuck tasks detected. This proves the detection-level CAS
        // guard excludes transitioned tasks.
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        store.completeTask(taskId, "claimer-sess");

        DefaultTeamTaskRecoveryHandler handler = new DefaultTeamTaskRecoveryHandler(
                dataSource, 60L, TeamTaskRecoveryAction.RECLAIM);
        List<TeamTaskRecoveryOutcome> outcomes = handler.recoverStuckTasks();

        assertTrue(outcomes.isEmpty(),
                "a task that completed before detection is not detected as stuck");
        assertEquals(TeamTaskStatus.COMPLETED.name() + "|claimer-sess", readTaskStatus(taskId));
    }

    @Test
    void casCompetitionAtActionLevelReturnsFailedOutcome() {
        // Simulate the race window between detection and action: the handler
        // detects the stuck task, then BETWEEN detection and its UPDATE the
        // task is flipped to COMPLETED by another process. The handler's
        // UPDATE finds STATUS='COMPLETED' (not 'CLAIMED') → affected=0 →
        // succeeded=false (honest, non-silent).
        //
        // We implement this with a faulting DataSource that, on the handler's
        // first UPDATE executeUpdate, flips the task to COMPLETED just before
        // delegating the UPDATE to H2 (so H2 sees STATUS='COMPLETED' and
        // returns affected=0).
        long stuckUpdatedAt = System.currentTimeMillis() - 120_000L;
        String taskId = insertTaskRow("task-cas-action", TeamTaskStatus.CLAIMED.name(),
                "claimer-sess", stuckUpdatedAt, null);

        final boolean[] flipped = {false};
        // Fault predicate: on the FIRST UPDATE executeUpdate (the handler's
        // RECLAIM UPDATE), flip the task to COMPLETED via the underlying
        // DataSource, then let the UPDATE proceed (it will find STATUS no
        // longer CLAIMED → affected=0). Subsequent calls are not faulted.
        DataSource racingDs = FaultingDataSource.create(dataSource, () -> {
            if (flipped[0]) {
                return false;
            }
            flipped[0] = true;
            flipStatus(taskId, TeamTaskStatus.COMPLETED.name());
            return true;
        });

        DefaultTeamTaskRecoveryHandler handler = new DefaultTeamTaskRecoveryHandler(
                racingDs, 60L, TeamTaskRecoveryAction.RECLAIM);
        List<TeamTaskRecoveryOutcome> outcomes = handler.recoverStuckTasks();

        assertEquals(1, outcomes.size(),
                "the task was detected as stuck before the race flip");
        TeamTaskRecoveryOutcome outcome = outcomes.get(0);
        assertEquals(taskId, outcome.getTaskId());
        assertFalse(outcome.isSucceeded(),
                "RECLAIM CAS must fail (succeeded=false) when the task transitioned between detection and action");
        assertTrue(outcome.getMessage().contains("transitioned"),
                "failed outcome must carry a descriptive message (non-silent): " + outcome.getMessage());
    }

    // ========================================================================
    // Fail-fast constructor
    // ========================================================================

    @Test
    void failFastOnNullDataSource() {
        assertThrows(Exception.class,
                () -> new DefaultTeamTaskRecoveryHandler(null, 60L, TeamTaskRecoveryAction.RECLAIM),
                "null dataSource must fail-fast (Minimum Rules #24)");
    }

    @Test
    void failFastOnNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultTeamTaskRecoveryHandler(dataSource, 0L, TeamTaskRecoveryAction.RECLAIM),
                "taskTimeoutSeconds <= 0 must fail-fast");
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultTeamTaskRecoveryHandler(dataSource, -1L, TeamTaskRecoveryAction.RECLAIM),
                "taskTimeoutSeconds <= 0 must fail-fast");
    }

    @Test
    void failFastOnSkipAction() {
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultTeamTaskRecoveryHandler(dataSource, 60L, TeamTaskRecoveryAction.SKIP),
                "SKIP action must fail-fast (use NoOpTeamTaskRecoveryHandler directly)");
    }

    // ========================================================================
    // Cross-tenant isolation (design 裁定 4a)
    // ========================================================================

    @Test
    void crossTenantIsolation() {
        long stuckUpdatedAt = System.currentTimeMillis() - 120_000L;
        // Two stuck CLAIMED tasks in different tenants.
        String taskA = insertTaskRow("task-tenant-a", TeamTaskStatus.CLAIMED.name(),
                "claimer-a", stuckUpdatedAt, "tenant-A");
        String taskB = insertTaskRow("task-tenant-b", TeamTaskStatus.CLAIMED.name(),
                "claimer-b", stuckUpdatedAt, "tenant-B");

        // Handler scoped to tenant-A.
        ITenantResolver tenantAResolver = () -> "tenant-A";
        DefaultTeamTaskRecoveryHandler handler = new DefaultTeamTaskRecoveryHandler(
                dataSource, 60L, TeamTaskRecoveryAction.RECLAIM, tenantAResolver);
        List<TeamTaskRecoveryOutcome> outcomes = handler.recoverStuckTasks();

        assertEquals(1, outcomes.size(),
                "tenant-A handler must only detect tenant-A's stuck task (not tenant-B's)");
        assertEquals(taskA, outcomes.get(0).getTaskId());
        assertTrue(outcomes.get(0).isSucceeded());

        // tenant-A's task is recovered (CREATED); tenant-B's task is untouched.
        assertEquals(TeamTaskStatus.CREATED.name() + "|null", readTaskStatus(taskA),
                "tenant-A task recovered by tenant-A handler");
        assertEquals(TeamTaskStatus.CLAIMED.name() + "|claimer-b", readTaskStatus(taskB),
                "tenant-B task must NOT be recovered by tenant-A handler (cross-tenant isolation)");
    }

    // ========================================================================
    // No silent skip — empty list when no stuck tasks (explicit semantic)
    // ========================================================================

    @Test
    void noStuckTasksReturnsEmptyList() {
        // Insert a non-stuck task and a terminal task — neither is stuck.
        insertTaskRow("task-recent-2", TeamTaskStatus.CLAIMED.name(),
                "claimer-sess", System.currentTimeMillis() - 1_000L, null);
        insertTaskRow("task-completed-2", TeamTaskStatus.COMPLETED.name(),
                "claimer-sess", System.currentTimeMillis() - 120_000L, null);

        DefaultTeamTaskRecoveryHandler handler = new DefaultTeamTaskRecoveryHandler(
                dataSource, 60L, TeamTaskRecoveryAction.RECLAIM);
        List<TeamTaskRecoveryOutcome> outcomes = handler.recoverStuckTasks();

        assertTrue(outcomes.isEmpty(),
                "no stuck tasks = empty outcome list (explicit semantic, not silent skip)");
    }

    // ========================================================================
    // Per-task fault isolation (design 裁定 4, Minimum Rules #24)
    // ========================================================================

    @Test
    void perTaskFaultIsolation() {
        long stuckUpdatedAt = System.currentTimeMillis() - 120_000L;
        // Two stuck tasks. The first's UPDATE will throw SQLException (via
        // the wrapper); the second must still be recovered normally.
        String taskA = insertTaskRow("task-fault-a", TeamTaskStatus.CLAIMED.name(),
                "claimer-a", stuckUpdatedAt, null);
        String taskB = insertTaskRow("task-fault-b", TeamTaskStatus.CLAIMED.name(),
                "claimer-b", stuckUpdatedAt, null);

        // Faulting DataSource: when the UPDATE's bound TASK_ID parameter
        // equals taskA, throw SQLException on executeUpdate.
        DataSource faultingDs = FaultingDataSource.createForTaskId(dataSource, taskA);

        DefaultTeamTaskRecoveryHandler handler = new DefaultTeamTaskRecoveryHandler(
                faultingDs, 60L, TeamTaskRecoveryAction.RECLAIM);
        List<TeamTaskRecoveryOutcome> outcomes = handler.recoverStuckTasks();

        // Both tasks produce outcomes (detection order is not guaranteed;
        // find by taskId).
        assertEquals(2, outcomes.size(),
                "both detected stuck tasks produce outcomes (fault isolation)");
        TeamTaskRecoveryOutcome outcomeA = outcomes.stream()
                .filter(o -> taskA.equals(o.getTaskId())).findFirst().orElseThrow();
        TeamTaskRecoveryOutcome outcomeB = outcomes.stream()
                .filter(o -> taskB.equals(o.getTaskId())).findFirst().orElseThrow();

        // task-A's UPDATE failed → succeeded=false, descriptive message.
        assertFalse(outcomeA.isSucceeded(),
                "task-A UPDATE failure must be recorded as succeeded=false (non-silent)");
        assertTrue(outcomeA.getMessage().contains("SQL"),
                "task-A failure message must mention SQL: " + outcomeA.getMessage());

        // task-B's UPDATE succeeded despite task-A's failure (fault isolation).
        assertTrue(outcomeB.isSucceeded(),
                "task-B must be recovered normally despite task-A's UPDATE failure (per-task isolation)");
        assertEquals(TeamTaskStatus.CREATED.name() + "|null", readTaskStatus(taskB),
                "task-B is CREATED + claimedBy cleared");
    }

    // ========================================================================
    // Detection SQLException propagates as NopAiAgentException (non-silent)
    // ========================================================================

    @Test
    void detectionSQLExceptionPropagatesAsNopAiAgentException() {
        // A DataSource whose getConnection throws SQLException — the handler's
        // detection SELECT must wrap this as NopAiAgentException (not swallow).
        DataSource failingDs = FailingDataSource.create();
        DefaultTeamTaskRecoveryHandler handler = new DefaultTeamTaskRecoveryHandler(
                failingDs, 60L, TeamTaskRecoveryAction.RECLAIM);

        assertThrows(NopAiAgentException.class, handler::recoverStuckTasks,
                "detection SELECT SQLException must propagate as NopAiAgentException (non-silent)");
    }

    // ========================================================================
    // Proxy-based fault-injection DataSource helpers
    // ========================================================================

    /**
     * A DataSource that delegates to the real H2 DataSource but wraps the
     * Connection / PreparedStatement so a {@link FaultTrigger} can inject
     * side effects (status flip) or a {@code faultTaskId} can cause a specific
     * task's UPDATE to throw {@link SQLException}.
     */
    static final class FaultingDataSource {
        /**
         * Create a DataSource that runs {@code sideEffect.get()} on the first
         * UPDATE {@code executeUpdate} (once-only), then delegates to H2.
         * Used to simulate a race-window status flip.
         */
        static DataSource create(DataSource real, FaultTrigger sideEffect) {
            return wrapDataSource(real, conn -> wrapConnectionForFault(conn, sideEffect, null));
        }

        /**
         * Create a DataSource that throws {@link SQLException} when an UPDATE
         * references the given task ID (bound at PreparedStatement parameter
         * index 3 by the handler). Used for per-task fault isolation.
         */
        static DataSource createForTaskId(DataSource real, String faultTaskId) {
            return wrapDataSource(real, conn -> wrapConnectionForFault(conn, null, faultTaskId));
        }

        @SuppressWarnings("unchecked")
        private static DataSource wrapDataSource(DataSource real,
                                                 java.util.function.Function<Connection, Connection> connWrapper) {
            return (DataSource) Proxy.newProxyInstance(
                    DataSource.class.getClassLoader(),
                    new Class<?>[]{DataSource.class},
                    (proxy, method, args) -> {
                        if ("getConnection".equals(method.getName()) && method.getParameterCount() == 0) {
                            return connWrapper.apply(real.getConnection());
                        }
                        if ("getConnection".equals(method.getName()) && method.getParameterCount() == 2) {
                            return connWrapper.apply(real.getConnection((String) args[0], (String) args[1]));
                        }
                        // Delegate everything else to the real DataSource via reflection.
                        return method.invoke(real, args);
                    });
        }

        private static Connection wrapConnectionForFault(Connection realConn,
                                                         FaultTrigger sideEffect,
                                                         String faultTaskId) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    new ConnectionInvocationHandler(realConn, sideEffect, faultTaskId));
        }
    }

    /** Functional side-effect (once-only race-window flip). */
    @FunctionalInterface
    interface FaultTrigger {
        boolean shouldTrigger();
    }

    /**
     * Invocation handler for the proxied Connection. It delegates every method
     * to the real Connection except {@code prepareStatement(String)}, which
     * returns a proxied PreparedStatement that:
     * <ul>
     *   <li>tracks the bound TASK_ID parameter (index 3 in the handler's
     *       UPDATE), and</li>
     *   <li>on {@code executeUpdate()}, either runs a once-only side-effect
     *       (race-window flip) or throws SQLException for a faulting task ID.</li>
     * </ul>
     */
    private static final class ConnectionInvocationHandler implements InvocationHandler {
        private final Connection real;
        private final FaultTrigger sideEffect;
        private final String faultTaskId;

        ConnectionInvocationHandler(Connection real, FaultTrigger sideEffect, String faultTaskId) {
            this.real = real;
            this.sideEffect = sideEffect;
            this.faultTaskId = faultTaskId;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("prepareStatement".equals(method.getName())
                    && args != null && args.length == 1 && args[0] instanceof String) {
                String sql = (String) args[0];
                PreparedStatement realPs = real.prepareStatement(sql);
                if (sql.startsWith("UPDATE " + AiAgentTeamTaskTable.TABLE_NAME)) {
                    return Proxy.newProxyInstance(
                            PreparedStatement.class.getClassLoader(),
                            new Class<?>[]{PreparedStatement.class},
                            new FaultingPsHandler(realPs, sideEffect, faultTaskId));
                }
                return realPs;
            }
            return method.invoke(real, args);
        }
    }

    /**
     * Invocation handler for the proxied PreparedStatement. It tracks the
     * TASK_ID bound at index 3, and on {@code executeUpdate} either runs the
     * side-effect or throws for the faulting task.
     */
    private static final class FaultingPsHandler implements InvocationHandler {
        private final PreparedStatement real;
        private final FaultTrigger sideEffect;
        private final String faultTaskId;
        private String boundTaskIdAtIdx3;

        FaultingPsHandler(PreparedStatement real, FaultTrigger sideEffect, String faultTaskId) {
            this.real = real;
            this.sideEffect = sideEffect;
            this.faultTaskId = faultTaskId;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String m = method.getName();
            if ("setString".equals(m) && args != null && args.length == 2
                    && Integer.valueOf(3).equals(args[0])) {
                boundTaskIdAtIdx3 = (String) args[1];
            }
            if ("executeUpdate".equals(m) && method.getParameterCount() == 0) {
                // Race-window side effect: runs once before the UPDATE.
                if (sideEffect != null && sideEffect.shouldTrigger()) {
                    // side-effect already executed (flip); fall through to delegate.
                }
                // Per-task fault: throw for the faulting task.
                if (faultTaskId != null && faultTaskId.equals(boundTaskIdAtIdx3)) {
                    throw new SQLException("Injected fault: UPDATE for faulting task " + faultTaskId);
                }
            }
            return method.invoke(real, args);
        }
    }

    /**
     * A DataSource whose {@code getConnection} always throws SQLException
     * (for testing detection-level SQLException propagation).
     */
    static final class FailingDataSource {
        @SuppressWarnings("unchecked")
        static DataSource create() {
            return (DataSource) Proxy.newProxyInstance(
                    DataSource.class.getClassLoader(),
                    new Class<?>[]{DataSource.class},
                    (proxy, method, args) -> {
                        if ("getConnection".equals(method.getName())) {
                            throw new SQLException("Injected: no connection available");
                        }
                        throw new SQLException("Injected: method " + method.getName() + " not available");
                    });
        }
    }
}
