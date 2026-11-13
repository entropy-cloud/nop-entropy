package io.nop.ai.agent.message;

import io.nop.ai.agent.NopAiAgentException;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2519 (裁定⑨ / 3.1-C3-021, 红⑨): DBMessageService governance — the four
 * remediation axes, one focused test per axis:
 *
 * <ul>
 *   <li>(a) 投递/poll 分离: a long-blocking handler on one topic must not
 *       freeze delivery of other topics (audit CL-040 single-thread
 *       starvation; the pre-fix poller ran consumer callbacks inline).</li>
 *   <li>(b) 毒消息处置: a handler that always throws must terminate in the
 *       dead-letter table within the redelivery bound — the queue must never
 *       stall forever (audit C7-025; pre-fix state redelivered infinitely with
 *       no DLQ).</li>
 *   <li>(c) consumers 注册治理: duplicate registration on the same topic is an
 *       explicit typed rejection — the pre-fix silent replacement (identity
 *       theft, audit C1-019/CL-024) is reversed.</li>
 *   <li>(d) claim 世代 fencing: a stale-generation consume write is rejected by
 *       the conditional UPDATE (audit CL-069).</li>
 * </ul>
 *
 * <p>Red evidence: pre-fix defect state (inline delivery, no DLQ, silent
 * replacement, unconditional consume write) — snapshots
 * {@code _tmp/red-green-2519-red9.log} / {@code -green9.log}.
 */
public class TestDBMessageServiceGovernance {

    private javax.sql.DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = newDb();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource instanceof AutoCloseable) {
            ((AutoCloseable) dataSource).close();
        }
    }

    private javax.sql.DataSource newDb() {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:governance-" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        return ds;
    }

    private boolean dlqHasRow(String sid) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM " + AiAgentMessageTable.DLQ_TABLE_NAME
                             + " WHERE " + AiAgentMessageTable.COL_DLQ_SID + " = ?")) {
            ps.setString(1, sid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private int pendingCount(String topic) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM " + AiAgentMessageTable.TABLE_NAME
                             + " WHERE TOPIC = ? AND STATUS = ?")) {
            ps.setString(1, topic);
            ps.setInt(2, AiAgentMessageTable.STATUS_PENDING);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * 红⑨(a): while the topic-A handler is blocked, topic-B messages must still
     * be delivered. Pre-fix (inline delivery on the single poller thread) the
     * whole service froze for the duration of the blocking callback — red.
     */
    @Test
    void testBlockingHandlerDoesNotFreezeOtherTopics() throws Exception {
        DBMessageService svc = new DBMessageService(dataSource, "gov-deliver");
        svc.setPollIntervalMs(20);
        svc.start();
        CountDownLatch blockA = new CountDownLatch(1);
        try {
            AtomicInteger aReceived = new AtomicInteger();
            List<Object> bReceived = new CopyOnWriteArrayList<>();

            svc.subscribe("gov.a.topic", (topic, message, context) -> {
                aReceived.incrementAndGet();
                try {
                    blockA.await(30, TimeUnit.SECONDS); // long-blocking call-agent-like handler
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
            svc.subscribe("gov.b.topic", (topic, message, context) -> {
                bReceived.add(message);
                return null;
            });

            svc.send("gov.a.topic", new AgentMessageEnvelope(
                    "S", "gov.a.topic", "c1", AgentMessageKind.ASYNC, "block"));
            // wait until the A handler is actually inside its blocking callback
            long deadline = System.currentTimeMillis() + 10_000;
            while (aReceived.get() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(1, aReceived.get(), "A handler must be blocked in its callback now");

            // while A is blocked, B must still be delivered
            svc.send("gov.b.topic", new AgentMessageEnvelope(
                    "S", "gov.b.topic", "c2", AgentMessageKind.ASYNC, "urgent"));
            deadline = System.currentTimeMillis() + 10_000;
            while (bReceived.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertNotNull(bReceived.isEmpty() ? null : bReceived.get(0),
                    "裁定⑨/a: topic B delivery must not be frozen by the blocking topic A handler");

            assertTrue(svc.getDeliveryRejectedCount() == 0,
                    "no delivery rejections expected in this scenario");
        } finally {
            blockA.countDown();
            svc.close();
        }
    }

    /**
     * 红⑨(b): a handler that always throws must be dead-lettered within the
     * redelivery bound and stop being redelivered. Pre-fix: infinite
     * redelivery with no DLQ — red.
     */
    @Test
    void testPoisonMessageDeadLetteredWithinBound() throws Exception {
        DBMessageService svc = new DBMessageService(dataSource, "gov-poison");
        svc.setPollIntervalMs(20);
        svc.setMaxRedeliveries(3);
        svc.start();
        String sid = "poison-sid-1";
        try {
            AtomicInteger attempts = new AtomicInteger();
            svc.subscribe("gov.poison.topic", (topic, message, context) -> {
                attempts.incrementAndGet();
                throw new NopAiAgentException("poison payload, deterministic failure");
            });
            svc.send("gov.poison.topic", new AgentMessageEnvelope(
                    "S", "gov.poison.topic", "c1", AgentMessageKind.ASYNC, "poison"));

            // capture the sid of the sent row (the send API does not return it)
            String actualSid = findOnlySid("gov.poison.topic");
            assertNotNull(actualSid, "poison row must exist in the message table");

            long deadline = System.currentTimeMillis() + 20_000;
            while (!dlqHasRow(actualSid) && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(dlqHasRow(actualSid),
                    "裁定⑨/b: poison message must be dead-lettered within the redelivery bound");
            assertTrue(attempts.get() <= 4 /* cap 3 + dead-letter check delivery */,
                    "delivery attempts must be bounded by the redelivery cap, got: "
                            + attempts.get());
            assertTrue(svc.getDlqDropCount() >= 1, "dlq-drop counter must be observable");

            // DLQ row carries the failure reason and attempt count
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT FAIL_REASON, ATTEMPTS FROM " + AiAgentMessageTable.DLQ_TABLE_NAME
                                 + " WHERE SID = ?")) {
                ps.setString(1, actualSid);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertTrue(rs.getString(1) != null && rs.getString(1).contains("deterministic"),
                            "DLQ row must carry the failure reason");
                    assertTrue(rs.getInt(2) >= 1, "DLQ row must carry the attempt count");
                }
            }
            // the original row is ack-skipped (terminal dead-letter status)
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT STATUS FROM " + AiAgentMessageTable.TABLE_NAME + " WHERE SID = ?")) {
                ps.setString(1, actualSid);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(AiAgentMessageTable.STATUS_DEAD_LETTER, rs.getInt(1));
                }
            }
            assertEquals(0, pendingCount("gov.poison.topic"),
                    "the poison message must not keep being redelivered after dead-lettering");
        } finally {
            svc.close();
        }
    }

    private String findOnlySid(String topic) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT SID FROM " + AiAgentMessageTable.TABLE_NAME + " WHERE TOPIC = ?")) {
            ps.setString(1, topic);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * 红⑨(c): duplicate registration on the same topic is an explicit typed
     * rejection. Pre-fix: silent replacement (the second consumer hijacked the
     * topic) — red.
     */
    @Test
    void testDuplicateConsumerRegistrationRejected() throws Exception {
        DBMessageService svc = new DBMessageService(dataSource, "gov-sub");
        svc.start();
        try {
            svc.subscribe("gov.dup.topic", (topic, message, context) -> null);

            NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                    () -> svc.subscribe("gov.dup.topic", (topic, message, context) -> null),
                    "裁定⑨/c: duplicate registration on the same topic must be rejected");
            // the rejection must carry the identity of the existing owner
            assertTrue(String.valueOf(ex.getMessage()).contains("ownerId=gov-sub"),
                    "rejection must name the existing consumer identity, got: " + ex.getMessage());
        } finally {
            svc.close();
        }
    }

    /**
     * 红⑨(c) cancel symmetry: after cancel, the topic can be re-registered.
     */
    @Test
    void testCancelThenResubscribeIsSymmetric() {
        DBMessageService svc = new DBMessageService(dataSource, "gov-resub");
        svc.start();
        try {
            var sub = svc.subscribe("gov.resub.topic", (topic, message, context) -> null);
            sub.cancel();
            // must not throw: cancel/subscribe are symmetric
            svc.subscribe("gov.resub.topic", (topic, message, context) -> null);
        } finally {
            svc.close();
        }
    }

    /**
     * 红⑨(d): a stale-generation consume write must be rejected by the
     * conditional UPDATE (claim fencing, audit CL-069).
     */
    @Test
    void testStaleGenerationConsumeWriteRejected() throws Exception {
        DBMessageService svc = new DBMessageService(dataSource, "gov-fence");
        svc.start();
        try {
            svc.subscribe("gov.fence.topic", (topic, message, context) -> null);
            svc.send("gov.fence.topic", new AgentMessageEnvelope(
                    "S", "gov.fence.topic", "c1", AgentMessageKind.ASYNC, "m"));
            String sid = findOnlySid("gov.fence.topic");
            assertNotNull(sid);

            // claim through the internal path (generation 1)
            long gen1 = svc.claimMessageForTest(sid);
            assertTrue(gen1 >= 1, "claim must allocate a positive generation, got " + gen1);

            // simulate the instance crashing and a newer claim by another
            // instance: bump the generation directly
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("UPDATE " + AiAgentMessageTable.TABLE_NAME
                        + " SET " + AiAgentMessageTable.COL_CLAIM_GEN + " = "
                        + AiAgentMessageTable.COL_CLAIM_GEN + " + 1 WHERE SID = '" + sid + "'");
            }

            // the OLD generation consume write must be rejected (fenced)
            svc.markConsumedFencedForTest(sid, gen1);
            assertTrue(svc.getClaimFencedRejectCount() >= 1,
                    "裁定⑨/d: stale-generation consume write must be rejected and counted");

            // the row must NOT be marked consumed by the stale write
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT STATUS FROM " + AiAgentMessageTable.TABLE_NAME + " WHERE SID = ?")) {
                ps.setString(1, sid);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(AiAgentMessageTable.STATUS_CLAIMED, rs.getInt(1),
                            "stale-generation consume must not clobber the newer claim");
                }
            }
        } finally {
            svc.close();
        }
    }

    /**
     * 红⑨/CL-069 dead-flag face: a suspended subscription stops delivery.
     */
    @Test
    void testSuspendedSubscriptionStopsDelivery() throws Exception {
        DBMessageService svc = new DBMessageService(dataSource, "gov-suspend");
        svc.setPollIntervalMs(20);
        svc.start();
        try {
            AtomicInteger received = new AtomicInteger();
            var sub = svc.subscribe("gov.susp.topic", (topic, message, context) -> {
                received.incrementAndGet();
                return null;
            });
            sub.suspend();
            svc.send("gov.susp.topic", new AgentMessageEnvelope(
                    "S", "gov.susp.topic", "c1", AgentMessageKind.ASYNC, "held"));
            Thread.sleep(300);
            assertEquals(0, received.get(),
                    "suspended subscription must not receive deliveries (dead flag fixed)");
            assertTrue(sub.isSuspended());
            sub.resume();
            long deadline = System.currentTimeMillis() + 10_000;
            while (received.get() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(1, received.get(), "resume must restore delivery");
        } finally {
            svc.close();
        }
    }
}
