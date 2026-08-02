/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.launch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.IDataSet;

/**
 * Stage 42 Phase 1: minimal cross-JVM {@link IMessageService} backed by a shared
 * JDBC database (H2 {@code AUTO_SERVER=TRUE} for this harness, but any JDBC DB
 * works). This is the **test-infrastructure alternative** the plan explicitly
 * allows ("a simple shared file/socket-backed message service for tests");
 * production deployments use {@code SysDaoMessageService} / Pulsar.
 *
 * <p><strong>Design</strong>:
 * <ul>
 *   <li>{@code send(topic, message)}: Java-serializes the message (every stream
 *       control-plane + data-plane message type is {@link java.io.Serializable} —
 *       {@code ApiRequest} via {@code ApiMessage}, {@code StreamMessageEnvelope}
 *       directly) and INSERTs a row into
 *       {@code nop_stream_msg_queue(topic, payload BLOB, created_at_ms)}.</li>
 *   <li>{@code subscribe(topic, consumer)}: registers a polling loop that wakes
 *       every {@code pollIntervalMs}, {@code SELECT}s rows with
 *       {@code id > lastSeenId AND topic = ?} in order, deserializes each, and
 *       dispatches to the consumer. Each subscription tracks its own
 *       {@code lastSeenId} cursor.</li>
 *   <li>Topics are concrete strings (no wildcards) — consistent with the stream
 *       control RPC ({@code nop-stream.rpc.task.{nodeId}}) and the deterministic
 *       data-plane topic naming ({@code nop-stream.data.{jobId}.{edgeId}.{subtaskIndex}}).</li>
 *   <li>Subscribe-time cursor bootstraps at the current MAX(id) so a new
 *       subscriber does NOT receive backlog published before it subscribed
 *       (matches {@link io.nop.message.core.local.LocalMessageService} semantics).</li>
 * </ul>
 *
 * <p><strong>Limitations</strong> (acceptable for test infrastructure):
 * <ul>
 *   <li>At-least-once delivery within a single subscriber — a crash between
 *       dispatch and cursor advance may redeliver. The stream runtime already
 *       handles redelivery via fencing epochs + exactly-once sink contracts.</li>
 *   <li>Topic-pattern subscriptions are not supported (not needed by the
 *       control RPC or the data plane).</li>
 *   <li>Fan-out: multiple subscribers on the same topic each see all messages
 *       (publish-subscribe semantics, matching {@code LocalMessageService}).</li>
 * </ul>
 */
public class PollingJdbcMessageService implements IMessageService, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PollingJdbcMessageService.class);

    private final IJdbcTemplate jdbcTemplate;
    private final long pollIntervalMs;
    private final ScheduledExecutorService poller;
    private final Map<String, List<Subscription>> subscriptions = new ConcurrentHashMap<>();
    private final String queueTable;
    private volatile boolean initialized = false;

    public PollingJdbcMessageService(IJdbcTemplate jdbcTemplate, long pollIntervalMs) {
        this(jdbcTemplate, pollIntervalMs, "nop_stream_msg_queue");
    }

    public PollingJdbcMessageService(IJdbcTemplate jdbcTemplate, long pollIntervalMs, String queueTable) {
        this.jdbcTemplate = jdbcTemplate;
        this.pollIntervalMs = pollIntervalMs;
        this.queueTable = queueTable;
        this.poller = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "polling-jdbc-msg");
            t.setDaemon(true);
            return t;
        });
    }

    /** Idempotently creates the queue table. Must be called once before use. */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("CREATE TABLE IF NOT EXISTS " + queueTable + " ("
                        + " id BIGINT AUTO_INCREMENT"
                        + ", topic VARCHAR(256) NOT NULL"
                        + ", payload BLOB"
                        + ", created_at_ms BIGINT NOT NULL"
                        + ", PRIMARY KEY(id)"
                        + ")")
                .end());
        // CREATE INDEX IF NOT EXISTS is racy across concurrent JVMs sharing the
        // same H2 AUTO_SERVER DB — both see "not exists", both try to create,
        // the loser throws "object already exists". Catch + ignore that specific
        // case so multi-JVM bootstrap is robust.
        try {
            jdbcTemplate.executeUpdate(SQL.begin()
                    .sql("CREATE INDEX IF NOT EXISTS idx_" + queueTable + "_topic_id"
                            + " ON " + queueTable + "(topic, id)")
                    .end());
        } catch (Exception e) {
            if (chainContainsAlreadyExists(e)) {
                LOG.info("Index idx_{}_topic_id already exists (concurrent init); skipping",
                        queueTable);
            } else {
                throw e;
            }
        }
        initialized = true;
        LOG.info("PollingJdbcMessageService initialized (table={}, pollIntervalMs={})",
                queueTable, pollIntervalMs);
    }

    private static boolean chainContainsAlreadyExists(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            String msg = cursor.getMessage();
            if (msg != null && msg.toLowerCase().contains("already exists")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /** Drops all rows for topics starting with {@code prefix}. Test-cleanup helper. */
    public void clearTopics(String prefix) {
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("DELETE FROM " + queueTable + " WHERE topic LIKE ?", prefix + "%")
                .end());
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        if (StringHelper.isEmpty(topic)) {
            throw new IllegalArgumentException("topic must not be empty");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        if (!initialized) {
            initialize();
        }

        Subscription sub = new Subscription(topic, listener);
        subscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(sub);
        sub.start();
        return sub;
    }

    @Override
    public void send(String topic, Object message, MessageSendOptions options) {
        if (!initialized) {
            initialize();
        }
        byte[] payload = javaSerialize(message);
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("INSERT INTO " + queueTable
                        + " (topic, payload, created_at_ms) VALUES(?,?,?)", topic, payload, System.currentTimeMillis())
                .end());
    }

    @Override
    public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        send(topic, message, options);
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        poller.shutdownNow();
        try {
            if (!poller.awaitTermination(2, TimeUnit.SECONDS)) {
                LOG.warn("PollingJdbcMessageService poller did not terminate cleanly");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        subscriptions.clear();
    }

    /**
     * Returns the total count of queued rows for diagnostics and tests.
     */
    public int countRows(String topicPrefix) {
        return jdbcTemplate.executeQuery(SQL.begin()
                .sql("SELECT COUNT(*) FROM " + queueTable + " WHERE topic LIKE ?", topicPrefix + "%")
                .end(), dataSet -> {
            if (dataSet.hasNext()) {
                return dataSet.next().getInt(0);
            }
            return 0;
        });
    }

    private static byte[] javaSerialize(Object message) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(message);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "PollingJdbcMessageService failed to Java-serialize message for topic "
                            + "(type=" + (message == null ? "null" : message.getClass().getName()) + "): " + e,
                    e);
        }
    }

    private static Object javaDeserialize(byte[] bytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return ois.readObject();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "PollingJdbcMessageService failed to Java-deserialize message: " + e, e);
        }
    }

    /**
     * One subscription tracked by the polling loop. Each subscription has its
     * own {@code lastSeenId} cursor (per-topic publish-subscribe semantics —
     * multiple subscribers on the same topic each see all messages).
     */
    public final class Subscription implements IMessageSubscription {
        private final String topic;
        private final IMessageConsumer consumer;
        private final AtomicLong lastSeenId = new AtomicLong(0L);
        private volatile boolean cancelled = false;
        private volatile boolean suspended = false;
        private volatile ScheduledFuture<?> task;

        Subscription(String topic, IMessageConsumer consumer) {
            this.topic = topic;
            this.consumer = consumer;
        }

        void start() {
            // Bootstrap the cursor at the current max id so the new subscriber
            // does NOT redeliver history published before it subscribed. This
            // matches LocalMessageService semantics (no backlog on subscribe).
            Long maxId = jdbcTemplate.executeQuery(SQL.begin()
                    .sql("SELECT MAX(id) FROM " + queueTable + " WHERE topic = ?", topic)
                    .end(), PollingJdbcMessageService::firstLongOrNull);
            lastSeenId.set(maxId == null ? 0L : maxId);
            LOG.info("Subscription started for topic={} at cursor={}", topic, lastSeenId.get());

            this.task = poller.scheduleWithFixedDelay(this::poll, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
        }

        void poll() {
            if (cancelled || suspended) {
                return;
            }
            try {
                long cursor = lastSeenId.get();
                List<Row> rows = jdbcTemplate.executeQuery(SQL.begin()
                        .sql("SELECT id, payload FROM " + queueTable
                                + " WHERE topic = ? AND id > ? ORDER BY id ASC", topic, cursor)
                        .end(), PollingJdbcMessageService::readRows);
                if (rows.isEmpty()) {
                    return;
                }
                ConsumeContext ctx = new ConsumeContext(topic);
                for (Row row : rows) {
                    Object message;
                    try {
                        message = javaDeserialize(row.payload);
                    } catch (Exception e) {
                        LOG.warn("Failed to deserialize payload for topic={} id={}: {}", topic, row.id, e.toString());
                        lastSeenId.set(row.id);
                        continue;
                    }
                    try {
                        consumer.onMessage(topic, message, ctx);
                    } catch (Exception e) {
                        LOG.error("Consumer onMessage failed for topic={} id={}", topic, row.id, e);
                    }
                    lastSeenId.set(row.id);
                }
            } catch (Exception e) {
                LOG.error("Poll failed for topic={}", topic, e);
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
            if (task != null) {
                task.cancel(false);
            }
            List<Subscription> subs = subscriptions.get(topic);
            if (subs != null) {
                subs.remove(this);
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isSuspended() {
            return suspended;
        }

        @Override
        public void suspend() {
            suspended = true;
        }

        @Override
        public void resume() {
            suspended = false;
        }
    }

    private static final class Row {
        final long id;
        final byte[] payload;

        Row(long id, byte[] payload) {
            this.id = id;
            this.payload = payload;
        }
    }

    private static List<Row> readRows(IDataSet dataSet) {
        List<Row> rows = new ArrayList<>();
        while (dataSet.hasNext()) {
            io.nop.dataset.IDataRow r = dataSet.next();
            rows.add(new Row(r.getLong(0), r.getBytes(1)));
        }
        return rows;
    }

    private static Long firstLongOrNull(IDataSet dataSet) {
        if (!dataSet.hasNext()) {
            return null;
        }
        io.nop.dataset.IDataRow r = dataSet.next();
        if (r.isNull(0)) {
            return null;
        }
        return r.getLong(0);
    }

    private static final class ConsumeContext implements IMessageConsumeContext {
        private final String topic;

        ConsumeContext(String topic) {
            this.topic = topic;
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            // The control RPC layer routes replies over its own topics via the
            // enclosing IMessageService; this per-message context is unused.
            throw new UnsupportedOperationException(
                    "ConsumeContext.sendAsync is not supported by PollingJdbcMessageService");
        }
    }
}
