package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.runtime.lock.AiAgentSessionLockTable;
import io.nop.ai.agent.session.AiAgentSessionTable;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared H2-backed fixtures for the Plan 222/226/229
 * {@link ScheduledRecoveryManager} test classes (MA4.2-06 split): in-memory
 * H2 data source lifecycle, direct-row insert helpers, and the recording
 * doubles (scheduler / orphan handler / timeout handler / engine). No
 * {@code @Test} methods — concrete scenarios live in the
 * {@code TestScheduledRecoveryManager*ScanOnce|Scheduling|OrphanHandler|Timeout}
 * subclasses.
 */
public abstract class AbstractScheduledRecoveryManagerTest {

    protected DataSource dataSource;
    protected String dbUrl;

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
        dbUrl = "jdbc:h2:mem:test-recovery-mgr-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
        createSessionTable();
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

    protected void createSessionTable() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentSessionTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentSessionTable.DDL_CREATE_INDEX);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create ai_agent_session table", e);
        }
    }

    // Helper: insert a session row with a given status.
    protected void insertSession(String sessionId, String status) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("MERGE INTO " + AiAgentSessionTable.TABLE_NAME
                    + " (" + AiAgentSessionTable.COL_SESSION_ID
                    + ", " + AiAgentSessionTable.COL_AGENT_NAME
                    + ", " + AiAgentSessionTable.COL_STATUS
                    + ", " + AiAgentSessionTable.COL_SESSION_DATA
                    + ", " + AiAgentSessionTable.COL_CREATED_AT
                    + ", " + AiAgentSessionTable.COL_UPDATED_AT
                    + ") KEY (" + AiAgentSessionTable.COL_SESSION_ID
                    + ") VALUES ('" + sessionId + "', 'test-agent', '" + status
                    + "', '{}', 0, 0)");
        }
    }

    // Helper: insert a session row with a specific UPDATED_AT timestamp.
    protected void insertSessionWithUpdatedAt(String sessionId, String status, long updatedAt) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("MERGE INTO " + AiAgentSessionTable.TABLE_NAME
                    + " (" + AiAgentSessionTable.COL_SESSION_ID
                    + ", " + AiAgentSessionTable.COL_AGENT_NAME
                    + ", " + AiAgentSessionTable.COL_STATUS
                    + ", " + AiAgentSessionTable.COL_SESSION_DATA
                    + ", " + AiAgentSessionTable.COL_CREATED_AT
                    + ", " + AiAgentSessionTable.COL_UPDATED_AT
                    + ") KEY (" + AiAgentSessionTable.COL_SESSION_ID
                    + ") VALUES ('" + sessionId + "', 'test-agent', '" + status
                    + "', '{}', 0, " + updatedAt + ")");
        }
    }

    // Helper: insert a lock row directly with explicit expiry.
    protected void insertLockRow(String sessionId, String owner, long expiresAt) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO " + AiAgentSessionLockTable.TABLE_NAME
                    + " (" + AiAgentSessionLockTable.COL_SESSION_ID
                    + ", " + AiAgentSessionLockTable.COL_LOCK_OWNER
                    + ", " + AiAgentSessionLockTable.COL_LOCK_ACQUIRED_AT
                    + ", " + AiAgentSessionLockTable.COL_LOCK_EXPIRES_AT
                    + ") VALUES ('" + sessionId + "', '" + owner + "', 0, " + expiresAt + ")");
        }
    }

    protected int countAllLockRows() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM " + AiAgentSessionLockTable.TABLE_NAME)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    protected String getSessionStatus(String sessionId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT " + AiAgentSessionTable.COL_STATUS
                             + " FROM " + AiAgentSessionTable.TABLE_NAME
                             + " WHERE " + AiAgentSessionTable.COL_SESSION_ID
                             + " = '" + sessionId + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }

    protected ScheduledRecoveryManager newManager() {
        return new ScheduledRecoveryManager(dataSource, new RecordingScheduler());
    }

    // ========================================================================
    // Minimal recording ISessionTimeoutHandler for setter-injection tests.
    // Records handleTimeout calls so wiring can be verified (#23).
    // ========================================================================
    static final class RecordingTimeoutHandler implements ISessionTimeoutHandler {
        final AtomicInteger callCount = new AtomicInteger(0);
        final AtomicReference<String> lastSessionId = new AtomicReference<>();

        @Override
        public TimeoutOutcome handleTimeout(String sessionId) {
            callCount.incrementAndGet();
            lastSessionId.set(sessionId);
            return new TimeoutOutcome(sessionId, TimeoutAction.SKIPPED, true, "recording-timeout-handler");
        }
    }

    // ========================================================================
    // Minimal stub IAgentEngine for daemon-integration tests that exercise
    // the LOCAL_CANCELLED branch via DefaultSessionTimeoutHandler. Records
    // cancelSession invocations; all other methods throw UOE.
    // ========================================================================
    static final class StubEngine implements IAgentEngine {
        static final String INSTANCE_ID = "test-instance-id";
        final AtomicInteger cancelCount = new AtomicInteger(0);
        final AtomicReference<String> lastCancelSessionId = new AtomicReference<>();
        final AtomicReference<String> lastCancelReason = new AtomicReference<>();
        final AtomicReference<Boolean> lastCancelForced = new AtomicReference<>();

        @Override
        public CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
            cancelCount.incrementAndGet();
            lastCancelSessionId.set(sessionId);
            lastCancelReason.set(reason);
            lastCancelForced.set(forced);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException("not used in timeout daemon tests");
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            throw new UnsupportedOperationException("not used in timeout daemon tests");
        }
    }

    // ========================================================================
    // Minimal recording IOrphanRecoveryHandler for setter-injection tests.
    // Records handleOrphan calls so wiring can be verified (#23).
    // ========================================================================
    static final class RecordingHandler implements IOrphanRecoveryHandler {
        final AtomicInteger callCount = new AtomicInteger(0);
        final AtomicReference<String> lastSessionId = new AtomicReference<>();

        @Override
        public RecoveryOutcome handleOrphan(String sessionId) {
            callCount.incrementAndGet();
            lastSessionId.set(sessionId);
            return new RecoveryOutcome(sessionId, RecoveryMode.SKIP, true, "recording-handler");
        }
    }

    // ========================================================================
    // Minimal recording IScheduledExecutor stub for scheduling-wiring tests.
    // Records the scheduleWithFixedDelay arguments and exposes a
    // cancel-recording Future. Does NOT actually schedule anything — tests
    // invoke the recorded Runnable manually.
    // ========================================================================
    static final class RecordingScheduler implements IScheduledExecutor, IDestroyable {
        final AtomicInteger scheduleCount = new AtomicInteger(0);
        final AtomicReference<Runnable> lastCommand = new AtomicReference<>();
        final AtomicReference<Long> lastInitialDelay = new AtomicReference<>();
        final AtomicReference<Long> lastDelay = new AtomicReference<>();
        final AtomicReference<TimeUnit> lastUnit = new AtomicReference<>();
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        boolean mayInterruptIfRunning;
        final AtomicBoolean destroyed = new AtomicBoolean(false);

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            scheduleCount.incrementAndGet();
            lastCommand.set(command);
            lastInitialDelay.set(initialDelay);
            lastDelay.set(delay);
            lastUnit.set(unit);
            // Return a cancel-recording Future so stop()'s cancel(false) is
            // observable (CompletableFuture.cancel does not expose whether
            // it was called by the caller vs completed).
            return new Future<Object>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    cancelled.set(true);
                    RecordingScheduler.this.mayInterruptIfRunning = mayInterruptIfRunning;
                    return true;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled.get();
                }

                @Override
                public boolean isDone() {
                    return cancelled.get();
                }

                @Override
                public Object get() {
                    return null;
                }

                @Override
                public Object get(long timeout, TimeUnit u) {
                    return null;
                }
            };
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
            destroyed.set(true);
        }

        @Override
        public boolean isDestroyed() {
            return destroyed.get();
        }

        @Override
        public String getName() {
            return "recording-scheduler";
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
