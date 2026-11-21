package io.nop.ai.agent.message;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2584 Phase 1 (audit nop-ai Major 1 + 2) regression pins:
 *
 * <ul>
 *   <li>deadLetter idempotency: a DLQ row that already exists (the historical
 *       partial state — legacy INSERT succeeded, UPDATE failed) must not
 *       wedge the poison message forever; the main-row status must advance to
 *       DEAD_LETTER (the message leaves PENDING and stops being retried).</li>
 *   <li>deadLetter atomicity: an injected main-table UPDATE failure must roll
 *       back the whole transaction (no DLQ row without the status advance);
       a subsequent retry (injection cleared) completes the dead-lettering.</li>
 *   <li>claim generation atomicity: the returned claim generation is read on
 *       the same connection/transaction as the claim UPDATE, so under
 *       concurrent competing claims exactly one winner gets a generation that
 *       matches the persisted CLAIM_GEN.</li>
 * </ul>
 */
public class TestDBMessageServiceAtomicDeadLetter_2584 {

    private static final String DEAD_LETTER_UPDATE_PREFIX =
            "UPDATE " + AiAgentMessageTable.TABLE_NAME
                    + " SET " + AiAgentMessageTable.COL_STATUS + " = ?, "
                    + AiAgentMessageTable.COL_CONSUMER_ID + " = NULL";

    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = newDb();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource instanceof AutoCloseable) {
            ((AutoCloseable) dataSource).close();
        }
    }

    private DataSource newDb() {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:atomic-dlq-" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        return ds;
    }

    /** DataSource wrapper that fails the dead-letter UPDATE exactly once. */
    private static class OnceFailingDataSource implements DataSource {
        private final DataSource delegate;
        volatile boolean failNextDeadLetterUpdate = false;

        OnceFailingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            final Connection real = delegate.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName()) && args != null
                                && args.length > 0 && args[0] instanceof String
                                && ((String) args[0]).startsWith(DEAD_LETTER_UPDATE_PREFIX)) {
                            final PreparedStatement realPs =
                                    (PreparedStatement) method.invoke(real, args);
                            final String sql = (String) args[0];
                            return Proxy.newProxyInstance(getClass().getClassLoader(),
                                    new Class<?>[]{PreparedStatement.class},
                                    (psProxy, psMethod, psArgs) -> {
                                        if ("executeUpdate".equals(psMethod.getName())
                                                && failNextDeadLetterUpdate) {
                                            failNextDeadLetterUpdate = false;
                                            throw new SQLException(
                                                    "injected dead-letter UPDATE failure (plan 2584 pin)");
                                        }
                                        try {
                                            return psMethod.invoke(realPs, psArgs);
                                        } catch (java.lang.reflect.InvocationTargetException e) {
                                            Throwable cause = e.getCause();
                                            if (cause instanceof RuntimeException) {
                                                throw (RuntimeException) cause;
                                            }
                                            if (cause instanceof SQLException) {
                                                throw (SQLException) cause;
                                            }
                                            throw e;
                                        }
                                    });
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            Throwable cause = e.getCause();
                            if (cause instanceof RuntimeException) {
                                throw (RuntimeException) cause;
                            }
                            if (cause instanceof SQLException) {
                                throw (SQLException) cause;
                            }
                            throw e;
                        }
                    });
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }
        @Override public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }

    private void insertMessageRow(String sid, String topic, int status) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO " + AiAgentMessageTable.TABLE_NAME
                             + " (SID, TOPIC, MESSAGE_BODY, STATUS, CREATED_AT, DELIVERY_ATTEMPTS, CLAIM_GEN)"
                             + " VALUES (?, ?, ?, ?, ?, 0, 0)")) {
            ps.setString(1, sid);
            ps.setString(2, topic);
            ps.setString(3, "{\"payload\":\"pin\"}");
            ps.setInt(4, status);
            ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    private void insertDlqRow(String sid, String topic) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO " + AiAgentMessageTable.DLQ_TABLE_NAME
                             + " (SID, TOPIC, MESSAGE_BODY, FAIL_REASON, ATTEMPTS, CREATED_AT)"
                             + " VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, sid);
            ps.setString(2, topic);
            ps.setString(3, "{\"payload\":\"pin\"}");
            ps.setString(4, "historical partial dead-letter");
            ps.setInt(5, 3);
            ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    private Integer messageStatus(String sid) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT STATUS FROM " + AiAgentMessageTable.TABLE_NAME + " WHERE SID = ?")) {
            ps.setString(1, sid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private boolean dlqHasRow(String sid) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM " + AiAgentMessageTable.DLQ_TABLE_NAME
                             + " WHERE SID = ?")) {
            ps.setString(1, sid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Pin (plan 2584 exit criterion): pre-existing DLQ row (the historical
     * partial INSERT-success/UPDATE-failure state) must not wedge the message
     * in PENDING forever — the duplicate-key dead-letter attempt is idempotent
     * and the main row advances to DEAD_LETTER.
     */
    @Test
    void deadLetterWithPreExistingDlqRowAdvancesMainRow() throws Exception {
        DBMessageService svc = new DBMessageService(dataSource, "pin-dlq-dup");
        svc.start();
        try {
            String sid = "dlq-dup-sid-1";
            insertMessageRow(sid, "pin.topic", AiAgentMessageTable.STATUS_PENDING);
            // historical partial state: DLQ INSERT committed, UPDATE never ran
            insertDlqRow(sid, "pin.topic");

            svc.deadLetterForTest(sid, "pin.topic", 3, "delivery attempts exhausted: 3");

            assertEquals(AiAgentMessageTable.STATUS_DEAD_LETTER, messageStatus(sid),
                    "the poison message must leave PENDING (dup-key dead-letter is idempotent)");
            assertTrue(dlqHasRow(sid));
            assertTrue(svc.getDlqDropCount() >= 1, "dlq-drop counter must stay observable");
        } finally {
            svc.close();
        }
    }

    /**
     * Pin (plan 2584 exit criterion): an injected main-row UPDATE failure must
     * roll back the WHOLE dead-letter transaction — no DLQ row without the
     * status advance — and the retry after the failure clears completes the
     * dead-lettering (the message ends in DEAD_LETTER, not retried forever).
     */
    @Test
    void deadLetterUpdateFailureRollsBackAllThenRetrySucceeds() throws Exception {
        OnceFailingDataSource failingDs = new OnceFailingDataSource(dataSource);
        DBMessageService svc = new DBMessageService(failingDs, "pin-dlq-tx");
        svc.start();
        try {
            String sid = "dlq-tx-sid-1";
            insertMessageRow(sid, "pin.tx", AiAgentMessageTable.STATUS_PENDING);

            // first attempt: UPDATE fails once (injected)
            failingDs.failNextDeadLetterUpdate = true;
            svc.deadLetterForTest(sid, "pin.tx", 3, "delivery attempts exhausted: 3");

            // transactional rollback: no partial state, message stays PENDING
            assertEquals(AiAgentMessageTable.STATUS_PENDING, messageStatus(sid),
                    "UPDATE failure must roll back the whole dead-letter transaction");
            assertFalse(dlqHasRow(sid), "no DLQ row may survive the rolled-back transaction");

            // retry (injection cleared): dead-lettering completes
            svc.deadLetterForTest(sid, "pin.tx", 3, "delivery attempts exhausted: 3");
            assertEquals(AiAgentMessageTable.STATUS_DEAD_LETTER, messageStatus(sid),
                    "the message must finally leave PENDING (bounded retry, no infinite loop)");
            assertTrue(dlqHasRow(sid));
        } finally {
            svc.close();
        }
    }

    /**
     * Pin (plan 2584 exit criterion, single-statement assertion face): under
     * concurrent competing claims on the same message, exactly ONE claim wins,
     * and the winner's returned generation equals the persisted CLAIM_GEN —
     * the generation is read on the claim's own connection/transaction
     * (plan 2584: no cross-connection TOCTOU).
     */
    @Test
    void claimGenerationIsAtomicUnderConcurrentClaims() throws Exception {
        DBMessageService svc = new DBMessageService(dataSource, "pin-claim-gen");
        svc.start();
        try {
            String sid = "claim-gen-sid-1";
            insertMessageRow(sid, "pin.claim", AiAgentMessageTable.STATUS_PENDING);

            int racers = 8;
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger winners = new AtomicInteger();
            AtomicLongRef winnerGen = new AtomicLongRef(-1);
            Thread[] threads = new Thread[racers];
            for (int i = 0; i < racers; i++) {
                threads[i] = new Thread(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    long gen = svc.claimMessageForTest(sid);
                    if (gen >= 0) {
                        winners.incrementAndGet();
                        winnerGen.set(gen);
                    }
                });
                threads[i].start();
            }
            start.countDown();
            for (Thread t : threads) {
                t.join(10_000);
            }

            assertEquals(1, winners.get(),
                    "exactly one competing claim may win the PENDING message");
            Long dbGen = claimGen(sid);
            assertNotNull(dbGen);
            assertEquals(dbGen.longValue(), winnerGen.get(),
                    "the winner's returned generation must equal the persisted CLAIM_GEN "
                            + "(read on the claim's own transaction)");
            assertEquals(AiAgentMessageTable.STATUS_CLAIMED, messageStatus(sid));

            // generation stays monotonic across sequential claims
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE " + AiAgentMessageTable.TABLE_NAME
                                 + " SET STATUS = ? WHERE SID = ?")) {
                ps.setInt(1, AiAgentMessageTable.STATUS_PENDING);
                ps.setString(2, sid);
                ps.executeUpdate();
            }
            long gen2 = svc.claimMessageForTest(sid);
            assertEquals(dbGen + 1, gen2, "successive claims must monotonically bump CLAIM_GEN");
        } finally {
            svc.close();
        }
    }

    private Long claimGen(String sid) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + AiAgentMessageTable.COL_CLAIM_GEN
                             + " FROM " + AiAgentMessageTable.TABLE_NAME + " WHERE SID = ?")) {
            ps.setString(1, sid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** Minimal long holder for the racer threads. */
    private static final class AtomicLongRef {
        private volatile long value;
        AtomicLongRef(long v) { this.value = v; }
        void set(long v) { this.value = v; }
        long get() { return value; }
    }
}
