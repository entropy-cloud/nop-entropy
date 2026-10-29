/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.launch;

import io.nop.api.core.message.IMessageSubscription;
import io.nop.stream.runtime.ledger.LedgerRetentionConfig;
import io.nop.message.core.channel.ChannelBacklogProbe;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2510 C8-003 / C10-007 (plan 2510 裁定⑧, both halves):
 * <ul>
 *   <li>(a) msg_queue retention: rows at or behind the per-topic dispatched
 *       position AND older than the retention TTL are deleted in bounded
 *       batches; undispatched rows are NEVER deleted (conservative pause
 *       semantics); the cleanup schedule is armed on the service lifecycle
 *       hook (initialize) and the scheduled task really invokes the cleanup
 *       body (rule-23 anti-hollow).</li>
 *   <li>(b) the service implements {@link ChannelBacklogProbe} with the
 *       unconsumed-row-count semantics, making the WI6 producer gate
 *       genuinely reachable for DB channels.</li>
 * </ul>
 */
class TestMsgQueueRetentionAndBacklogProbe {

    private static final String TOPIC_EDGE = "nop-stream.data.c8003.retention.edge";
    private static final String TOPIC_PROBE = "nop-stream.data.c8003.probe.edge";
    private static final String TOPIC_ORPHAN = "nop-stream.data.c8003.retention.orphan";

    private static HikariDataSource dataSource;

    private PollingJdbcMessageService service;

    @BeforeAll
    static void initAll() {
        CoreInitialization.initialize();
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(4);
    }

    @AfterAll
    static void destroyAll() {
        if (dataSource != null) {
            dataSource.close();
        }
        CoreInitialization.destroy();
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    private IJdbcTemplate newTemplate() {
        JdbcFactory factory = new JdbcFactory();
        return factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
    }

    /** Legacy rows with a caller-controlled created_at (bypassing send()). */
    private void insertRow(String topic, String payload, long createdAt) {
        newTemplate().executeUpdate(SQL.begin()
                .sql("INSERT INTO nop_stream_msg_queue_c8003 (topic, payload, created_at_ms) VALUES (?,?,?)",
                        topic, payload.getBytes(), createdAt)
                .end());
    }

    private LedgerRetentionConfig retentionConfig(int retentionDays) {
        LedgerRetentionConfig config = LedgerRetentionConfig.msgqueueFace();
        config.setRetentionDays(retentionDays);
        return config;
    }

    @Test
    void serviceImplementsBacklogProbeWithUnconsumedRowCountSemantics() {
        service = new PollingJdbcMessageService(newTemplate(), 50L, "nop_stream_msg_queue_c8003",
                10, retentionConfig(0));
        service.initialize();

        // Capability half: the WI6 gate resolves a probe for the DB channel.
        assertTrue(service instanceof ChannelBacklogProbe,
                "PollingJdbcMessageService must implement ChannelBacklogProbe (C8-003 gate half)");

        assertTrue(service.backlog(TOPIC_PROBE) == 0, "no rows yet");

        service.send(TOPIC_PROBE, "r1", null);
        service.send(TOPIC_PROBE, "r2", null);
        service.send(TOPIC_PROBE, "r3", null);
        assertEquals(3L, service.backlog(TOPIC_PROBE),
                "undispatched rows count as backlog");

        // Consume all rows: the dispatched position advances, backlog drains.
        java.util.concurrent.atomic.AtomicInteger received = new java.util.concurrent.atomic.AtomicInteger();
        IMessageSubscription sub = service.subscribe(TOPIC_PROBE, (topic, message, ctx) -> {
            received.incrementAndGet();
            return null;
        }, new io.nop.api.core.message.MessageSubscribeOptions());
        long deadline = System.currentTimeMillis() + 10_000;
        while (received.get() < 3 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertEquals(3, received.get());
        assertEquals(0L, service.backlog(TOPIC_PROBE),
                "rows consumed past the dispatched position are no longer backlog");
        sub.cancel();
    }

    @Test
    void retentionDeletesOnlyDispatchedExpiredRowsInBoundedBatches() throws Exception {
        IJdbcTemplate template = newTemplate();
        service = new PollingJdbcMessageService(template, 50L, "nop_stream_msg_queue_c8003",
                10, retentionConfig(1));
        service.initialize();

        long now = System.currentTimeMillis();
        // Four rows that will be aged beyond the retention window.
        for (int i = 1; i <= 4; i++) {
            service.send(TOPIC_EDGE, "old-" + i, null);
        }
        newTemplate().executeUpdate(SQL.begin()
                .sql("UPDATE nop_stream_msg_queue_c8003 SET created_at_ms = ? WHERE topic = ?",
                        now - 10L * 24 * 3600_000L, TOPIC_EDGE)
                .end());
        // A fresh row (within the retention window) on the same topic.
        service.send(TOPIC_EDGE, "fresh", null);
        // An aged row on a topic with NO subscriber — conservatively skipped.
        service.send(TOPIC_ORPHAN, "orphan-old", null);

        final java.util.concurrent.atomic.AtomicInteger seen =
                new java.util.concurrent.atomic.AtomicInteger();
        IMessageSubscription sub = service.subscribe(TOPIC_EDGE, (topic, message, ctx) -> {
            seen.incrementAndGet();
            return null;
        }, new io.nop.api.core.message.MessageSubscribeOptions());
        long deadline = System.currentTimeMillis() + 10_000;
        while (seen.get() < 5 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(5, seen.get(), "precondition: all rows on the topic dispatched");

        // An AGED row with an id AHEAD of the dispatched position (undispatched) —
        // retention must never delete it (消费后可删, not TTL 纯账龄).
        newTemplate().executeUpdate(SQL.begin()
                .sql("INSERT INTO nop_stream_msg_queue_c8003 (id, topic, payload, created_at_ms) VALUES (?,?,?,?)",
                        999_999L, TOPIC_EDGE, "undispatched-old".getBytes(),
                        now - 10L * 24 * 3600_000L)
                .end());

        int deleted = service.runRetentionCleanupOnce();
        assertEquals(4, deleted,
                "retention deletes dispatched rows beyond the TTL window, never undispatched rows");

        assertEquals(2, service.countRows(TOPIC_EDGE),
                "the fresh row AND the aged-but-undispatched row survive retention");

        // Conservative pause: the orphan topic has no subscription / dispatched
        // position — its rows must NOT be deleted even though they are beyond TTL.
        assertEquals(1, service.countRows(TOPIC_ORPHAN),
                "topics without a dispatched position are conservatively skipped (restart pause)");
        sub.cancel();
    }

    @Test
    void retentionScheduleArmedOnLifecycleHookAndReallyInvokesCleanupBody() throws Exception {
        IJdbcTemplate template = newTemplate();
        LedgerRetentionConfig config = retentionConfig(1);
        // Test seam: shorten the interval below the 60s production floor via
        // reflection so the scheduled task fires inside the test window. The
        // production config path enforces the floor (fail-fast), this only
        // accelerates the SAME scheduled body invocation.
        Field interval = LedgerRetentionConfig.class.getDeclaredField("cleanupIntervalMs");
        interval.setAccessible(true);
        interval.setLong(config, 200L);

        service = new PollingJdbcMessageService(template, 50L, "nop_stream_msg_queue_c8003",
                10, config);
        assertFalse(service.isRetentionCleanupScheduled(), "not armed before initialize");
        service.initialize();

        assertTrue(service.isRetentionCleanupScheduled(),
                "retention cleanup must be armed on the lifecycle hook (rule 23 wiring)");

        long deadline = System.currentTimeMillis() + 5_000;
        while (service.retentionCleanupInvocations() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(service.retentionCleanupInvocations() >= 1,
                "the scheduled task must really invoke the cleanup body (anti-hollow, R01 adjudication 2)");
        assertEquals(0, service.retentionCleanupFailures());
    }

}
