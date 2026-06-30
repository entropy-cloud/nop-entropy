package io.nop.ai.agent.message;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 271 Phase 1 reliability tests for {@link DBMessageService} (audit
 * finding 14-02): markConsumed/releaseClaim must not silently swallow
 * {@link SQLException}s, and stale CLAIMED messages must be swept back to
 * PENDING so at-least-once delivery is preserved.
 *
 * <p>Each test uses a real H2 in-memory database (no mocks) for the SQL paths,
 * plus a failing DataSource wrapper for the SQLException-propagation tests.
 */
public class TestDBMessageServiceReliability {

    private static final Logger LOGG = Logger.getLogger(TestDBMessageServiceReliability.class.getName());

    private DataSource dataSource;
    private String dbUrl;

    @BeforeEach
    void setUp() {
        dbUrl = "jdbc:h2:mem:test-db-msg-rel-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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

    private void initSchema() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentMessageTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentMessageTable.DDL_CREATE_INDEX);
        }
    }

    private int statusOf(String sid) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT " + AiAgentMessageTable.COL_STATUS
                             + " FROM " + AiAgentMessageTable.TABLE_NAME
                             + " WHERE " + AiAgentMessageTable.COL_SID + " = '" + sid + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int countByStatus(int status) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM " + AiAgentMessageTable.TABLE_NAME
                             + " WHERE " + AiAgentMessageTable.COL_STATUS + " = " + status)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * Insert a message row directly into CLAIMED state with a given claimed_at.
     * The body is serialized as an {@link AgentMessageEnvelope} (ASYNC, plain
     * payload) exactly like {@code DBMessageService.sendAsync} does, so the
     * poller's {@code deserializeFromDb} can reconstruct it for delivery.
     */
    private void insertClaimedRow(String sid, String topic, Timestamp claimedAt) throws Exception {
        String body = AgentMessageEnvelopeJson.toJson(
                new AgentMessageEnvelope(null, null, null, AgentMessageKind.ASYNC, "body"));
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO " + AiAgentMessageTable.TABLE_NAME
                             + " (" + AiAgentMessageTable.COL_SID + ", "
                             + AiAgentMessageTable.COL_TOPIC + ", "
                             + AiAgentMessageTable.COL_MESSAGE_BODY + ", "
                             + AiAgentMessageTable.COL_STATUS + ", "
                             + AiAgentMessageTable.COL_CONSUMER_ID + ", "
                             + AiAgentMessageTable.COL_CREATED_AT + ", "
                             + AiAgentMessageTable.COL_CLAIMED_AT
                             + ") VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, sid);
            ps.setString(2, topic);
            ps.setString(3, body);
            ps.setInt(4, AiAgentMessageTable.STATUS_CLAIMED);
            ps.setString(5, "dead-consumer");
            ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(7, claimedAt);
            ps.executeUpdate();
        }
    }

    // ---- markConsumed / releaseClaim SQLException propagation (wiring) ----

    @Test
    void markConsumedThrowsOnSqlExceptionNotSilentlySwallowed() {
        DBMessageService svc = new DBMessageService(new FailingDataSource(), "failing");
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> svc.markConsumed("sid-1"));
        assertTrue(ex.getMessage().contains("sid-1"), "exception should identify the message sid");
        assertTrue(ex.getCause() instanceof SQLException, "original SQLException must be preserved as cause");
    }

    @Test
    void releaseClaimThrowsOnSqlExceptionNotSilentlySwallowed() {
        DBMessageService svc = new DBMessageService(new FailingDataSource(), "failing");
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> svc.releaseClaim("sid-2"));
        assertTrue(ex.getMessage().contains("sid-2"), "exception should identify the message sid");
        assertTrue(ex.getCause() instanceof SQLException, "original SQLException must be preserved as cause");
    }

    // ---- stale CLAIMED sweep ----

    @Test
    void sweepResetsStaleClaimedMessagesToPending() throws Exception {
        initSchema();
        DBMessageService svc = new DBMessageService(dataSource, "sweeper");
        // claimed 10 minutes ago → stale
        Timestamp oldClaim = new Timestamp(System.currentTimeMillis() - 10 * 60 * 1000L);
        insertClaimedRow("stale-1", "topic-a", oldClaim);

        int reset = svc.sweepStaleClaimedMessages(5 * 60 * 1000L);

        assertEquals(1, reset, "one stale CLAIMED message should be reset");
        assertEquals(AiAgentMessageTable.STATUS_PENDING, statusOf("stale-1"),
                "stale message should be back to PENDING after sweep");
    }

    @Test
    void sweepDoesNotResetFreshClaimedMessages() throws Exception {
        initSchema();
        DBMessageService svc = new DBMessageService(dataSource, "sweeper");
        // claimed just now → not stale
        insertClaimedRow("fresh-1", "topic-a", new Timestamp(System.currentTimeMillis()));

        int reset = svc.sweepStaleClaimedMessages(5 * 60 * 1000L);

        assertEquals(0, reset, "a fresh CLAIMED message must not be reset");
        assertEquals(AiAgentMessageTable.STATUS_CLAIMED, statusOf("fresh-1"),
                "fresh message must remain CLAIMED");
    }

    @Test
    void sweepWithNoStaleMessagesDoesNotThrowAndReturnsZero() throws Exception {
        initSchema();
        DBMessageService svc = new DBMessageService(dataSource, "sweeper");
        // No rows at all — sweep must not throw and must return 0 (no silent error).
        int reset = assertThrowsNothingAndReturn(() -> svc.sweepStaleClaimedMessages(60 * 1000L));
        assertEquals(0, reset, "sweep over empty table should return 0 without error");
    }

    @Test
    void sweepRejectsNonPositiveTimeoutFast() throws Exception {
        initSchema();
        DBMessageService svc = new DBMessageService(dataSource, "sweeper");
        assertThrows(NopAiAgentException.class, () -> svc.sweepStaleClaimedMessages(0));
        assertThrows(NopAiAgentException.class, () -> svc.sweepStaleClaimedMessages(-1));
    }

    // ---- end-to-end: a stranded CLAIMED message is redelivered after sweep ----

    @Test
    void strandedClaimedMessageIsRedeliveredAfterSweep() throws Exception {
        initSchema();
        DBMessageService svc = new DBMessageService(dataSource, "redeliver");
        svc.setPollIntervalMs(20);
        svc.start();
        try {
            // Simulate a consumer crash: claim a message (CLAIMED, old timestamp)
            // but never consume it.
            Timestamp oldClaim = new Timestamp(System.currentTimeMillis() - 10 * 60 * 1000L);
            insertClaimedRow("stranded-1", "redeliver.topic", oldClaim);
            assertEquals(AiAgentMessageTable.STATUS_CLAIMED, statusOf("stranded-1"));

            AtomicReference<Object> received = new AtomicReference<>();
            svc.subscribe("redeliver.topic", new IMessageConsumer() {
                @Override
                public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                    received.set(message);
                    return null;
                }
            });

            // Sweep resets the stranded message to PENDING so the poller can
            // claim+deliver it to the now-subscribed consumer.
            int reset = svc.sweepStaleClaimedMessages(5 * 60 * 1000L);
            assertEquals(1, reset);

            waitForCondition(() -> received.get() != null, 15, TimeUnit.SECONDS);
            assertTrue(received.get() != null, "stranded CLAIMED message must be redelivered after sweep");
            assertEquals(AiAgentMessageTable.STATUS_CONSUMED, statusOf("stranded-1"),
                    "redelivered message should reach CONSUMED");
        } finally {
            svc.close();
        }
    }

    @Test
    void scheduledSweepKeepsRunningAndDoesNotBreakPolling() throws Exception {
        initSchema();
        DBMessageService svc = new DBMessageService(dataSource, "scheduled-sweep");
        svc.setPollIntervalMs(20);
        // very short sweep interval + short stale threshold so the scheduled
        // sweep fires within the test window.
        svc.setSweepIntervalMs(50);
        svc.setStaleClaimTimeoutMs(100);
        svc.start();
        try {
            // Insert a message that is already stale (claimed_at well in the past).
            insertClaimedRow("sched-1", "sweep.topic",
                    new Timestamp(System.currentTimeMillis() - 60 * 1000L));

            // The scheduled sweep should reset it to PENDING without any manual
            // sweepStaleClaimedMessages call.
            waitForCondition(() -> {
                try {
                    return statusOf("sched-1") == AiAgentMessageTable.STATUS_PENDING;
                } catch (Exception e) {
                    return false;
                }
            }, 15, TimeUnit.SECONDS);

            assertEquals(AiAgentMessageTable.STATUS_PENDING, statusOf("sched-1"),
                    "scheduled sweep should have reset the stale CLAIMED message to PENDING");
        } finally {
            svc.close();
        }
    }

    // ---- helpers ----

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private <T> T assertThrowsNothingAndReturn(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (AssertionError ae) {
            throw ae;
        } catch (Throwable t) {
            throw new AssertionError("expected no throw, but got: " + t, t);
        }
    }

    private void waitForCondition(java.util.function.Supplier<Boolean> condition,
                                  long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(20);
        }
    }

    /** A DataSource whose getConnection always throws SQLException. */
    private static final class FailingDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("simulated DB failure");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("simulated DB failure");
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return LOGG;
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
