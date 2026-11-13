package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.NopAiAgentException;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.runtime.lock.AiAgentSessionLockTable;
import io.nop.ai.agent.session.AiAgentSessionTable;
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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.1-MA-18 (plan 2555 裁定 G) carrier — recovery retry budget:
 * <ul>
 *   <li>a failing RESUME recovery retries at most {@code maxAttempts} times
 *       and the exhausted budget escalates to a typed outcome
 *       ("retry budget exhausted"), never an infinite loop or a silent
 *       skip;</li>
 *   <li>a transient failure recovers on a later attempt (succeeded outcome
 *       observes the attempt number);</li>
 *   <li>the LOCAL_CANCELLED face of {@link DefaultSessionTimeoutHandler}
 *       carries the same budget semantics (attempt cap + typed escalation,
 *       exercised against a real H2 lock/session table).</li>
 * </ul>
 */
public class TestOrphanRecoveryBudget {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // DefaultOrphanRecoveryHandler (RESUME face)
    // ========================================================================

    @Test
    void resumeBudgetCapsAttemptsAndEscalates() {
        // Every restoreSession call fails synchronously (takeover-lock
        // conflict) — attempts must be capped at the budget.
        AtomicInteger calls = new AtomicInteger();
        TestDefaultOrphanRecoveryHandler.RecordingAgentEngine engine =
                new TestDefaultOrphanRecoveryHandler.RecordingAgentEngine(sid -> {
                    calls.incrementAndGet();
                    throw new NopAiAgentException(
                            "takeover lock conflict: session already held by another instance");
                });
        DefaultOrphanRecoveryHandler handler =
                new DefaultOrphanRecoveryHandler(RecoveryMode.RESUME, engine, null);
        handler.setResumeMaxAttempts(3);
        handler.setResumeBackoffBaseMs(1);

        RecoveryOutcome outcome = handler.handleOrphan("budget-sess");

        assertEquals(3, calls.get(), "attempts must be capped at the configured budget");
        assertFalse(outcome.isSucceeded(), "budget-exhausted outcome must be failed");
        assertTrue(outcome.getMessage().contains("retry budget exhausted"),
                "budget exhaustion must escalate with a typed outcome message: "
                        + outcome.getMessage());
        assertTrue(outcome.getMessage().contains("(3 attempts)"),
                "outcome must record the attempt cap: " + outcome.getMessage());
    }

    @Test
    void resumeTransientFailureRecoversWithinBudget() {
        // First call fails, second succeeds — the retry must observe the
        // recovery and mark the attempt number in the outcome.
        AtomicInteger calls = new AtomicInteger();
        TestDefaultOrphanRecoveryHandler.RecordingAgentEngine engine =
                new TestDefaultOrphanRecoveryHandler.RecordingAgentEngine(sid -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new NopAiAgentException("transient conflict");
                    }
                    return CompletableFuture.completedFuture(null);
                });
        DefaultOrphanRecoveryHandler handler =
                new DefaultOrphanRecoveryHandler(RecoveryMode.RESUME, engine, null);
        handler.setResumeMaxAttempts(3);
        handler.setResumeBackoffBaseMs(1);

        RecoveryOutcome outcome = handler.handleOrphan("flaky-sess");

        assertEquals(2, calls.get(), "the second attempt must run after one transient failure");
        assertTrue(outcome.isSucceeded(), "recovery within budget must succeed");
        assertTrue(outcome.getMessage().contains("attempt=2"),
                "a retried success must observe the attempt number: " + outcome.getMessage());
    }

    // ========================================================================
    // DefaultSessionTimeoutHandler (LOCAL_CANCELLED face)
    // ========================================================================

    private DataSource dataSource;

    @BeforeEach
    void setUpDb() {
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:test-recovery-budget-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentSessionTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentSessionTable.DDL_CREATE_INDEX);
            stmt.execute(AiAgentSessionLockTable.DDL_CREATE_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create test tables", e);
        }
    }

    @AfterEach
    void tearDownDb() {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
                // best-effort close during teardown
            }
        }
    }

    private void insertSession(String sessionId, String status) throws SQLException {
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

    private void insertLockRow(String sessionId, String owner, long expiresAt) throws SQLException {
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

    /** Engine double: cancelSession always throws synchronously. */
    static final class AlwaysFailingCancelEngine implements IAgentEngine {
        final AtomicInteger cancelCalls = new AtomicInteger();

        @Override
        public CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
            cancelCalls.incrementAndGet();
            throw new NopAiAgentException("cancelSession failed: session not found");
        }

        @Override
        public io.nop.ai.agent.engine.AgentMessageAck sendMessage(
                io.nop.ai.agent.engine.AgentMessageRequest request) {
            throw new UnsupportedOperationException("not used in recovery tests");
        }

        @Override
        public CompletableFuture<io.nop.ai.agent.engine.AgentExecutionResult> execute(
                io.nop.ai.agent.engine.AgentMessageRequest request) {
            throw new UnsupportedOperationException("not used in recovery tests");
        }
    }

    @Test
    void localCancelledBudgetCapsAttemptsAndEscalates() throws Exception {
        String instanceId = "this-instance";
        AlwaysFailingCancelEngine engine = new AlwaysFailingCancelEngine();
        DefaultSessionTimeoutHandler handler =
                new DefaultSessionTimeoutHandler(60L, engine, dataSource, instanceId);
        handler.setCancelMaxAttempts(2);
        handler.setCancelBackoffBaseMs(1);

        insertSession("budget-cancel", "running");
        insertLockRow("budget-cancel", instanceId, System.currentTimeMillis() + 60_000L);

        TimeoutOutcome outcome = handler.handleTimeout("budget-cancel");

        assertEquals(2, engine.cancelCalls.get(),
                "attempts must be capped at the configured budget");
        assertFalse(outcome.isSucceeded(), "budget-exhausted outcome must be failed");
        assertEquals(TimeoutAction.LOCAL_CANCELLED, outcome.getAction(),
                "the outcome action must stay LOCAL_CANCELLED");
        assertTrue(outcome.getMessage().contains("retry budget exhausted"),
                "budget exhaustion must escalate with a typed outcome message: "
                        + outcome.getMessage());
        assertTrue(outcome.getMessage().contains("(2 attempts)"),
                "outcome must record the attempt cap: " + outcome.getMessage());
    }
}
