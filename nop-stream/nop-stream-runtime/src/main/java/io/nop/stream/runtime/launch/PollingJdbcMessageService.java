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
import io.nop.message.core.channel.ChannelBacklogProbe;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.runtime.ledger.LedgerRetentionConfig;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;

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
public class PollingJdbcMessageService implements IMessageService, AutoCloseable, ChannelBacklogProbe {

    private static final Logger LOG = LoggerFactory.getLogger(PollingJdbcMessageService.class);

    /** 裁定⑥⑨共享参数：单 poll 有界批量（LIMIT），缺省 500，&le;0 装配期 fail-fast。 */
    public static final String POLL_BATCH_SIZE_KEY = "nop.stream.launch.poll-batch-size";
    public static final int DEFAULT_POLL_BATCH_SIZE = 500;

    private final IJdbcTemplate jdbcTemplate;
    private final long pollIntervalMs;
    private final int pollBatchSize;
    /** C8-011 裁定⑥：数据面 poll/派发线程池（topic 前缀 {@code nop-stream.data.}）。 */
    private final ScheduledExecutorService dataPoller;
    /** C8-011 裁定⑥：控制面调度线程池（部署/取消/协调指令），与数据面分离。 */
    private final ScheduledExecutorService controlPoller;
    /** C8-003 裁定⑧：msg_queue 形态 A 周期清理调度器（retention-days>0 时创建）。 */
    private volatile ScheduledExecutorService retentionSweeper;
    private volatile java.util.concurrent.ScheduledFuture<?> retentionTask;
    private final AtomicLong retentionCleanupInvocations = new AtomicLong();
    private final LedgerRetentionConfig retentionConfig;
    private final Map<String, List<Subscription>> subscriptions = new ConcurrentHashMap<>();
    private final String queueTable;
    private volatile boolean initialized = false;

    public PollingJdbcMessageService(IJdbcTemplate jdbcTemplate, long pollIntervalMs) {
        this(jdbcTemplate, pollIntervalMs, "nop_stream_msg_queue");
    }

    public PollingJdbcMessageService(IJdbcTemplate jdbcTemplate, long pollIntervalMs, String queueTable) {
        this(jdbcTemplate, pollIntervalMs, queueTable, defaultPollBatchSize(),
                LedgerRetentionConfig.msgqueueFace());
    }

    /**
     * Full constructor. {@code pollBatchSize} is the single-poll LIMIT shared by
     * the faceted poller (裁定⑥) and the bounded read path (裁定⑨); values &le; 0
     * fail fast at assembly time. {@code retentionConfig} carries the R01 SSOT
     * msgqueue face parameters ({@code nop.stream.ledger.msgqueue.*}).
     */
    public PollingJdbcMessageService(IJdbcTemplate jdbcTemplate, long pollIntervalMs, String queueTable,
                                     int pollBatchSize, LedgerRetentionConfig retentionConfig) {
        if (pollBatchSize <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL,
                    POLL_BATCH_SIZE_KEY + " must be > 0 (single-poll batch limit): " + pollBatchSize);
        }
        this.jdbcTemplate = jdbcTemplate;
        this.pollIntervalMs = pollIntervalMs;
        this.pollBatchSize = pollBatchSize;
        this.queueTable = queueTable;
        this.retentionConfig = retentionConfig;
        this.dataPoller = Executors.newScheduledThreadPool(2, namedFactory("polling-jdbc-msg-data"));
        this.controlPoller = Executors.newScheduledThreadPool(2, namedFactory("polling-jdbc-msg-control"));
    }

    private static int defaultPollBatchSize() {
        String raw = System.getProperty(POLL_BATCH_SIZE_KEY);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_POLL_BATCH_SIZE;
        }
        return Integer.parseInt(raw.trim());
    }

    private static java.util.concurrent.ThreadFactory namedFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
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
        LOG.info("PollingJdbcMessageService initialized (table={}, pollIntervalMs={}, pollBatchSize={})",
                queueTable, pollIntervalMs, pollBatchSize);
        scheduleRetentionCleanup();
    }

    /**
     * C8-003 (plan 2510 裁定⑧, R01 形态 A): schedule the msg_queue retention
     * cleanup on the service's own lifecycle hook ({@link #initialize()} is the
     * production-reachable entry — every send/subscribe path and the launch
     * mains call it). Disabled by {@code nop.stream.ledger.msgqueue.retention-days=0}.
     */
    private void scheduleRetentionCleanup() {
        if (retentionConfig.getRetentionDays() <= 0) {
            LOG.info("msg_queue retention cleanup disabled (retention-days=0, face={})",
                    retentionConfig.getFace());
            return;
        }
        if (retentionTask != null) {
            return;
        }
        synchronized (this) {
            if (retentionTask != null) {
                return;
            }
            ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(
                    namedFactory("polling-jdbc-msg-retention"));
            retentionSweeper = sweeper;
            retentionTask = sweeper.scheduleWithFixedDelay(this::runRetentionCleanupGuarded,
                    retentionConfig.getCleanupIntervalMs(), retentionConfig.getCleanupIntervalMs(),
                    TimeUnit.MILLISECONDS);
        }
        LOG.info("msg_queue retention cleanup scheduled (table={}, retentionDays={}, intervalMs={}, deleteBatchSize={})",
                queueTable, retentionConfig.getRetentionDays(), retentionConfig.getCleanupIntervalMs(),
                retentionConfig.getDeleteBatchSize());
    }

    private void runRetentionCleanupGuarded() {
        try {
            int deleted = runRetentionCleanupOnce();
            if (deleted > 0) {
                LOG.info("msg_queue retention cleanup deleted {} dispatched rows (table={})", deleted, queueTable);
            }
        } catch (Exception e) {
            // R01 §3.1 第5项: cleanup failure = explicit ERROR (nop.<module>.ledger.cleanup-fail
            // shape) + visible counter; never an empty catch; single failure must not break the chain.
            retentionCleanupFailedCount++;
            LOG.error("nop.stream.ledger.msgqueue cleanup-fail: retention cleanup failed for table {} "
                    + "(failures={}) — schedule stays armed", queueTable, retentionCleanupFailedCount, e);
        }
    }

    private int retentionCleanupFailedCount = 0;

    /**
     * One retention pass (also invoked directly by tests): deletes rows that are
     * BOTH at or behind the per-topic dispatched position AND older than the
     * retention TTL (双谓词取交，裁定⑧). Topics without a dispatched position
     * (no subscription / restarted process before cursor rebuild) are
     * conservatively skipped — retention never deletes undispatched rows.
     *
     * @return the number of rows deleted
     */
    public int runRetentionCleanupOnce() {
        retentionCleanupInvocations.incrementAndGet();
        int retentionDays = retentionConfig.getRetentionDays();
        if (retentionDays <= 0) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);
        int batchSize = retentionConfig.getDeleteBatchSize();
        int deleted = 0;
        for (Map.Entry<String, List<Subscription>> entry : subscriptions.entrySet()) {
            String topic = entry.getKey();
            Long position = dispatchedPosition(entry.getValue());
            if (position == null) {
                continue;
            }
            while (true) {
                List<Long> ids = jdbcTemplate.executeQuery(SQL.begin()
                        .sql("SELECT id FROM " + queueTable
                                + " WHERE topic = ? AND id <= ? AND created_at_ms < ? ORDER BY id ASC LIMIT ?",
                                topic, position, cutoff, batchSize)
                        .end(), PollingJdbcMessageService::readIds);
                if (ids.isEmpty()) {
                    break;
                }
                jdbcTemplate.executeUpdate(SQL.begin()
                        .sql("DELETE FROM " + queueTable + " WHERE id IN (" + idList(ids) + ")")
                        .end());
                deleted += ids.size();
                if (ids.size() < batchSize) {
                    break;
                }
            }
        }
        return deleted;
    }

    /**
     * The per-topic dispatched position: the MINIMUM {@code lastSeenId} across
     * the topic's live subscriptions (every subscriber must have consumed past a
     * row before it is deletable). Null = no live subscription — the restart
     * conservative-pause window of 裁定⑧.
     */
    private Long dispatchedPosition(List<Subscription> subs) {
        Long min = null;
        for (Subscription sub : subs) {
            if (sub.isCancelled()) {
                continue;
            }
            long cursor = sub.lastSeenId();
            if (min == null || cursor < min) {
                min = cursor;
            }
        }
        return min;
    }

    private static String idList(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    private static List<Long> readIds(IDataSet dataSet) {
        List<Long> ids = new ArrayList<>();
        while (dataSet.hasNext()) {
            io.nop.dataset.IDataRow r = dataSet.next();
            ids.add(r.getLong(0));
        }
        return ids;
    }

    /**
     * C8-003 (裁定⑧ b-half): WI6 producer gate capability — the backlog is the
     * number of UNCONSUMED rows for the topic (id &gt; dispatched cursor; no live
     * subscription cursor yet = all rows count as unconsumed, the conservative
     * backpressure direction).
     */
    @Override
    public long backlog(String topic) {
        List<Subscription> subs = subscriptions.get(topic);
        Long position = subs == null ? null : dispatchedPosition(subs);
        long cursor = position == null ? 0L : position;
        return jdbcTemplate.executeQuery(SQL.begin()
                .sql("SELECT COUNT(*) FROM " + queueTable + " WHERE topic = ? AND id > ?", topic, cursor)
                .end(), dataSet -> {
            if (dataSet.hasNext()) {
                return dataSet.next().getLong(0);
            }
            return 0L;
        });
    }

    /** Rule-23 接线证据: the retention schedule is armed on the lifecycle hook. */
    public boolean isRetentionCleanupScheduled() {
        return retentionTask != null && !retentionTask.isCancelled();
    }

    /** Rule-23 接线证据: the scheduled task really invokes the cleanup body. */
    public long retentionCleanupInvocations() {
        return retentionCleanupInvocations.get();
    }

    /** Rule-25 观测面: cleanup failures are counted, never swallowed. */
    public int retentionCleanupFailures() {
        return retentionCleanupFailedCount;
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
        shutdownExecutor(dataPoller, "data poller");
        shutdownExecutor(controlPoller, "control poller");
        ScheduledExecutorService sweeper = retentionSweeper;
        if (sweeper != null) {
            shutdownExecutor(sweeper, "retention sweeper");
        }
        subscriptions.clear();
    }

    private static void shutdownExecutor(ScheduledExecutorService executor, String name) {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                LOG.warn("PollingJdbcMessageService {} did not terminate cleanly", name);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
            // Item 14 (composite-scenario distributed): DATA-PLANE topics AND
            // per-node TASK control topics deliver from the topic START (cursor 0),
            // not from the current MAX(id):
            //  - Data-plane topics are deterministic per (jobId, edgeId, src, tgt)
            //    and per-run (the harness drops the queue on startup) — rows before
            //    a subscriber's registration are in-flight records of the CURRENT
            //    run. With the MAX(id) bootstrap, a source that starts emitting
            //    before a slower-deployed downstream consumer subscribed silently
            //    lost those records (exactly-once violation).
            //  - Task control topics carry the node's deployment history
            //    (updateFencingToken pushes strictly precede their matching
            //    deployTask rows, and both replay in id order). A REPLACEMENT
            //    TaskManager bootstrapping at MAX(id) never sees the deploy rows
            //    posted while its predecessor was dead / it was booting — its
            //    subtasks stall until the 60s liveness detector forces another
            //    recovery cascade. Replaying from the start converges the node to
            //    the latest assignment (each epoch push precedes its deploys).
            // The COORDINATOR uplink topic keeps the MAX(id) no-backlog bootstrap:
            //    replaying stale task status reports into a fresh coordinator is
            //    pure noise.
            long initialCursor = 0L;
            if (isNoBacklogTopic(topic)) {
                Long maxId = jdbcTemplate.executeQuery(SQL.begin()
                        .sql("SELECT MAX(id) FROM " + queueTable + " WHERE topic = ?", topic)
                        .end(), PollingJdbcMessageService::firstLongOrNull);
                initialCursor = maxId == null ? 0L : maxId;
            }
            lastSeenId.set(initialCursor);
            LOG.info("Subscription started for topic={} at cursor={}", topic, lastSeenId.get());

            this.task = pollExecutorFor(topic).scheduleWithFixedDelay(this::poll,
                    pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
        }

        /**
         * C8-011 (plan 2510 裁定⑥): data-plane topics ({@code nop-stream.data.})
         * poll/dispatch on the dedicated data-plane pool; every other topic
         * (deploy/cancel/coordination RPC/control topics) runs on the separate
         * control-plane pool — slow data-plane consumption can no longer starve
         * the control plane.
         */
        private ScheduledExecutorService pollExecutorFor(String topicName) {
            return topicName.startsWith("nop-stream.data.") ? dataPoller : controlPoller;
        }

        /**
         * Item 14: only the coordinator uplink topic keeps the no-backlog bootstrap;
         * every other {@code nop-stream.*} topic is a data-plane channel or a task
         * deployment history of the current run (see {@link #start()}).
         */
        private static boolean isNoBacklogTopic(String topic) {
            return topic.startsWith("nop-stream.rpc.coordinator.");
        }

        void poll() {
            if (cancelled || suspended) {
                return;
            }
            try {
                long cursor = lastSeenId.get();
                ConsumeContext ctx = new ConsumeContext(topic);
                // C5-010 (plan 2510 裁定⑨): bounded pick - a single poll query
                // materializes at most pollBatchSize rows (LIMIT shared with 裁定⑥);
                // a larger backlog drains across successive poll cycles via the
                // persisted cursor (分批推进至清空, C10-006).
                List<Row> rows = jdbcTemplate.executeQuery(SQL.begin()
                        .sql("SELECT id, payload FROM " + queueTable
                                + " WHERE topic = ? AND id > ? ORDER BY id ASC LIMIT ?", topic, cursor, pollBatchSize)
                        .end(), PollingJdbcMessageService::readRows);
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

        long lastSeenId() {
            return lastSeenId.get();
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
