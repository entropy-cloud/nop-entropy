/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.launch;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.stream.runtime.transport.DataPlaneMessageServiceAdapter;
import io.nop.stream.runtime.transport.IdentityWireCodec;
import io.nop.stream.runtime.transport.WireDecodeFailureAware;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.42 R4-02 W-05 (1.1-C8-005, E2 泛在钉 — S6 损坏行注入): a poll row whose
 * payload fails to deserialize must NEVER be silently skipped with the cursor
 * advanced past it (the pre-fix behavior — a permanent, invisible data gap that
 * contradicts the "a data-plane consumer must never silently lose a record"
 * contract). Faces (adjudication C.1):
 *
 * <ul>
 *   <li>(a1) data-plane topic + {@link WireDecodeFailureAware} consumer (direct
 *       subscription): the typed channel failure {@code onWireDecodeFailure}
 *       arrives; the cursor statement is unchanged (the channel is terminal, the
 *       subscription dies with the failed task).</li>
 *   <li>(a2) the SAME corruption composed through the production adapter chain:
 *       the backend hands the failure to the {@code AdaptingConsumer}, which must
 *       DELEGATE it to the inner aware consumer (K5 seam) — without the delegation
 *       the payload-level failure has no reachable typed carrier.</li>
 *   <li>(b) data-plane topic + NON-aware consumer: poison-row fail-loud wedge —
 *       the typed failure propagates, the cursor does NOT advance, the row is
 *       retried (nothing is silently lost); a good row behind the poison row stays
 *       blocked (declared wedge semantics, visible not silent).</li>
 *   <li>(c) boundary: a corrupted CONTROL-plane topic row keeps the legacy
 *       warn+skip+cursor-advance semantics (RPC callers have timeout backstops —
 *       wedging the node control channel would deadlock deploy/cancel/ACK).</li>
 * </ul>
 *
 * <p>Reverting the typed escalation (or the AdaptingConsumer delegation) turns
 * (a1)/(a2)/(b) red; breaking the control-plane boundary turns (c) red.
 */
class TestPollDataPlaneRecordFailureTypedR402 {

    private static HikariDataSource dataSource;

    private PollingJdbcMessageService service;
    private IJdbcTemplate jdbcTemplate;

    /** Recording aware consumer — the typed-channel failure receiver. */
    private static final class RecordingAwareConsumer implements IMessageConsumer,
            WireDecodeFailureAware {
        final List<Object> messages = new CopyOnWriteArrayList<>();
        final AtomicInteger wireFailures = new AtomicInteger();
        final AtomicReference<String> lastFailedTopic = new AtomicReference<>();
        final AtomicReference<Object> lastFailedPayload = new AtomicReference<>();

        @Override
        public Object onMessage(String topic, Object message, io.nop.api.core.message.IMessageConsumeContext ctx) {
            messages.add(message);
            return null;
        }

        @Override
        public void onWireDecodeFailure(String topic, Object message, Throwable cause) {
            wireFailures.incrementAndGet();
            lastFailedTopic.set(topic);
            lastFailedPayload.set(message);
        }
    }

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

    private PollingJdbcMessageService newService() {
        JdbcFactory factory = new JdbcFactory();
        jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
        PollingJdbcMessageService s = new PollingJdbcMessageService(jdbcTemplate, 50L);
        s.initialize();
        return s;
    }

    /** Inserts a row whose payload cannot be java-deserialized (storage corruption). */
    private long insertCorruptRow(String topic) {
        byte[] garbage = new byte[]{0x00, (byte) 0xAC, (byte) 0xED, 0x05, 0x7F, 0x00};
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .sql("INSERT INTO nop_stream_msg_queue (topic, payload, created_at_ms) VALUES(?,?,?)",
                        topic, garbage, System.currentTimeMillis())
                .end());
        Long id = jdbcTemplate.executeQuery(io.nop.core.lang.sql.SQL.begin()
                .sql("SELECT MAX(id) FROM nop_stream_msg_queue WHERE topic = ?", topic)
                .end(), dataSet -> {
            if (!dataSet.hasNext()) {
                return null;
            }
            io.nop.dataset.IDataRow r = dataSet.next();
            return r.isNull(0) ? null : r.getLong(0);
        });
        assertNotNull(id, "poison row must be inserted");
        return id;
    }

    @Test
    void awareDataPlaneConsumerReceivesTypedFailureOnCorruptRow() throws Exception {
        service = newService();
        String topic = "nop-stream.data.r402a.edge1";
        RecordingAwareConsumer consumer = new RecordingAwareConsumer();
        service.subscribe(topic, consumer, new MessageSubscribeOptions());

        // Let the subscription's cursor bootstrap settle (start-at-0 for data topics).
        Thread.sleep(100);

        long poisonId = insertCorruptRow(topic);

        // ~6 poll cycles at 50ms cadence.
        long deadline = System.currentTimeMillis() + 1000;
        while (consumer.wireFailures.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        // (a1) typed failure reached the aware consumer — the record is NOT
        // silently lost.
        assertTrue(consumer.wireFailures.get() >= 1,
                "corrupt data-plane row must reach the aware consumer as a typed channel "
                        + "failure (pre-fix: warn+skip, zero typed calls)");
        assertEquals(topic, consumer.lastFailedTopic.get());
        assertNotNull(consumer.lastFailedPayload.get(), "the raw payload rides the typed failure");
        // The cursor statement is unchanged for the aware face (terminal channel —
        // the subscription dies with the failed task; no consumption consequence).
        PollingJdbcMessageService.Subscription sub = firstSubscription(topic);
        assertTrue(sub.lastSeenId() >= poisonId,
                "aware face keeps the unchanged cursor statement (terminal channel)");
    }

    @Test
    void adapterComposedChainDelegatesTypedFailureToInnerAwareConsumer() throws Exception {
        service = newService();
        String topic = "nop-stream.data.r402b.edge1";
        RecordingAwareConsumer inner = new RecordingAwareConsumer();
        // Production composition: the channel subscribes through the adapter; the
        // adapter's AdaptingConsumer is what the backend actually sees.
        DataPlaneMessageServiceAdapter adapter =
                new DataPlaneMessageServiceAdapter(service, new IdentityWireCodec());
        adapter.subscribe(topic, inner, new MessageSubscribeOptions());

        Thread.sleep(100);
        long poisonId = insertCorruptRow(topic);

        long deadline = System.currentTimeMillis() + 1000;
        while (inner.wireFailures.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        // (a2) K5 delegation: payload-level corruption in the backend reaches the
        // INNER aware consumer through the AdaptingConsumer wrapper.
        assertTrue(inner.wireFailures.get() >= 1,
                "payload-level corruption must reach the inner aware consumer through the "
                        + "AdaptingConsumer delegation (pre-fix: no reachable carrier, silent skip)");
        assertEquals(topic, inner.lastFailedTopic.get());
        assertTrue(subscriptionCursorAtLeast(topic, poisonId),
                "aware face keeps the unchanged cursor statement");
    }

    @Test
    void nonAwareDataPlaneConsumerWedgesInsteadOfSilentSkip() throws Exception {
        service = newService();
        String topic = "nop-stream.data.r402c.edge1";
        List<Object> received = new CopyOnWriteArrayList<>();
        IMessageConsumer plain = (t, m, ctx) -> {
            received.add(m);
            return null;
        };
        service.subscribe(topic, plain, new MessageSubscribeOptions());

        Thread.sleep(100);
        long poisonId = insertCorruptRow(topic);
        // A GOOD row behind the poison row: the wedge keeps it blocked (visible),
        // the pre-fix silent-skip delivered it after cursoring past the poison.
        service.send(topic, "good-row", null);

        // ~6-10 poll cycles: the poison row must keep the cursor pinned.
        Thread.sleep(400);

        PollingJdbcMessageService.Subscription sub = firstSubscription(topic);
        assertTrue(sub.lastSeenId() < poisonId,
                "poison data-plane row must wedge the cursor (fail-loud), not be silently "
                        + "skipped with the cursor advanced (pre-fix behavior)");
        assertTrue(received.isEmpty(),
                "a good row behind the poison row stays blocked by the declared wedge — "
                        + "the failure stays visible instead of a silent gap");
    }

    @Test
    void controlPlaneCorruptRowKeepsWarnSkipCursorAdvance() throws Exception {
        service = newService();
        String topic = "nop-stream.rpc.control.r402d";
        List<Object> received = new CopyOnWriteArrayList<>();
        IMessageConsumer plain = (t, m, ctx) -> {
            received.add(m);
            return null;
        };
        service.subscribe(topic, plain, new MessageSubscribeOptions());

        Thread.sleep(100);
        long poisonId = insertCorruptRow(topic);

        // Control-plane rows keep the legacy semantics: warn + skip + cursor
        // advance (RPC callers have timeout backstops; wedging the control
        // channel would pin deploy/cancel/ACK).
        long deadline = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < deadline) {
            if (subscriptionCursorAtLeast(topic, poisonId)) {
                break;
            }
            Thread.sleep(20);
        }
        assertTrue(subscriptionCursorAtLeast(topic, poisonId),
                "control-plane corrupt row keeps warn+skip+cursor-advance (boundary face)");
        assertTrue(received.isEmpty(), "the corrupt row is never dispatched to the consumer");
    }

    private PollingJdbcMessageService.Subscription firstSubscription(String topic) {
        List<PollingJdbcMessageService.Subscription> subs = service.subscriptionsForTest(topic);
        assertEquals(1, subs.size(), "exactly one subscription on " + topic);
        return subs.get(0);
    }

    private boolean subscriptionCursorAtLeast(String topic, long id) {
        List<PollingJdbcMessageService.Subscription> subs = service.subscriptionsForTest(topic);
        return !subs.isEmpty() && subs.get(0).lastSeenId() >= id;
    }
}
