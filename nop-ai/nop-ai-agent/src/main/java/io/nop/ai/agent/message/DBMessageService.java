package io.nop.ai.agent.message;

import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL;
import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;
import io.nop.api.core.time.CoreMetrics;
import io.nop.ai.agent.NopAiAgentException;
import io.nop.ai.agent.security.ITenantResolver;
import io.nop.ai.agent.security.NullTenantResolver;
import io.nop.ai.agent.security.TenantSql;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageCapability;
import io.nop.api.core.message.MessageOptionsValidator;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Database-backed {@link IMessageService} implementation — a sibling of
 * {@code LocalMessageService} (in-memory) and {@code PulsarMessageService}
 * (broker). Messages are persisted to the {@code ai_agent_message} table and
 * delivered to registered consumers via a background polling thread.
 *
 * <p><b>At-least-once delivery:</b> messages are persisted to DB before
 * {@code sendAsync} returns. A JVM crash does not lose pending messages —
 * they are recovered and delivered when polling resumes.
 *
 * <p><b>Competing consumers:</b> multiple {@code DBMessageService} instances
 * sharing the same DB compete for messages via atomic
 * {@code UPDATE ... WHERE STATUS = PENDING} claims. Each message is delivered
 * to exactly one consumer instance.
 *
 * <p><b>Consumer return value handling:</b>
 * <ul>
 *   <li>{@code null} → mark consumed</li>
 *   <li>{@link ConsumeLater} → release back to pending for retry</li>
 *   <li>{@link CompletionStage} → wait, then apply the above rules</li>
 *   <li>other non-null → mark consumed (no ack-topic routing)</li>
 * </ul>
 *
 * <p><b>Lifecycle:</b> the platform {@code IMessageService} interface has no
 * start/stop methods. This class provides explicit {@link #start()} and
 * {@link #close()} for managing the background polling thread.
 *
 * <p><b>Payload constraint:</b> DB-backed transport requires payloads to be
 * JSON-serializable (unlike in-memory transport which passes by reference).
 */

/** * <p><b>store-layer 边界（审计 AI-2/AI-19 裁定）</b>：自建表/本地文件为引擎内部运行时状态，
 * 保留独立 store 层（不注册 ORM）；租户/软删不适用理由与表清单见
 * {@code ai-dev/design/nop-ai-agent/store-layer-contract.md}。
 */
public class DBMessageService implements IMessageService, AutoCloseable {
    private static final Set<MessageCapability> CAPABILITIES = EnumSet.of(
            MessageCapability.SEND, MessageCapability.SUBSCRIBE, MessageCapability.BATCH_CONSUME,
            MessageCapability.ACK_REPLY);

    /** DB queue transport: delay / seek options are warn-once (D1). */
    private static final Set<MessageCapability> WARN_ONCE = EnumSet.of(
            MessageCapability.DELAYED_SEND, MessageCapability.SEND_TIMEOUT, MessageCapability.SEEK);

    @Override
    public Set<MessageCapability> getCapabilities() {
        return CAPABILITIES;
    }


    private static final Logger LOG = LoggerFactory.getLogger(DBMessageService.class);

    private static final int DEFAULT_POLL_INTERVAL_MS = 50;
    private static final int DEFAULT_MAX_BATCH = 10;

    /**
     * Plan 271 (finding 14-02): a CLAIMED message older than this threshold is
     * considered "stale" (the consumer that claimed it crashed or failed to
     * mark it consumed/released) and is reset to PENDING by the sweep task so
     * it can be redelivered. This is the safety net that guarantees
     * at-least-once delivery even when {@code markConsumed}/{@code releaseClaim}
     * themselves fail.
     */
    static final long DEFAULT_STALE_CLAIM_TIMEOUT_MS = 5 * 60 * 1000L;
    static final long DEFAULT_SWEEP_INTERVAL_MS = 60 * 1000L;

    private final DataSource dataSource;
    private final String consumerId;
    private final ITenantResolver tenantResolver;

    /**
     * Plan 2519 (裁定⑨/c): registrations keyed by topic, each carrying the
     * consumer identity ({@code ownerId}). Duplicate registration on the same
     * topic is rejected explicitly — the previous silent replacement (audit
     * C1-019/CL-024: consumer hijack / identity theft) is no longer possible.
     */
    private final Map<String, ConsumerRegistration> consumers = new ConcurrentHashMap<>();

    /**
     * Plan 2519 (裁定⑨/a): dedicated bounded delivery executor. The poller
     * thread only fetches and claims messages; consumer callbacks (a call-agent
     * handler can block for 120s) run here so one slow topic no longer freezes
     * delivery for every other topic (audit CL-040 single-thread starvation).
     */
    private ThreadPoolExecutor deliveryExecutor;

    private ScheduledExecutorService poller;
    private volatile boolean started = false;
    private volatile boolean closed = false;

    // 3.1-MA-12 (plan 2555 裁定 B / C1-022): volatile — the four config setters are public runtime API
    // while the poller/sweep/delivery threads read these fields bare.
    private volatile long pollIntervalMs = DEFAULT_POLL_INTERVAL_MS;
    private volatile int maxBatch = DEFAULT_MAX_BATCH;
    private volatile long staleClaimTimeoutMs = DEFAULT_STALE_CLAIM_TIMEOUT_MS;
    private volatile long sweepIntervalMs = DEFAULT_SWEEP_INTERVAL_MS;
    /**
     * Plan 2519 (裁定⑨/b): per-message redelivery upper bound (poison-message
     * cap, audit C7-025). Counted per claim; a message exceeding the bound is
     * dead-lettered and ack-skipped instead of being requeued forever.
     */
    private volatile int maxRedeliveries = DEFAULT_MAX_REDELIVERIES;
    private volatile int deliveryThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
    private volatile int deliveryQueueCapacity = 100;

    /** Observability counters (design §3.11). */
    private final AtomicLong dlqDropCount = new AtomicLong();
    private final AtomicLong claimFencedRejectCount = new AtomicLong();
    private final AtomicLong deliveryRejectedCount = new AtomicLong();

    /**
     * Plan 2519 (裁定⑨/b): last delivery-failure reason per message sid, so the
     * DLQ row carries the root failure (not just the attempt count). Entries are
     * removed on dead-letter / consume so the map stays bounded.
     */
    private final Map<String, String> lastFailureReasons = new ConcurrentHashMap<>();

    static final int DEFAULT_MAX_REDELIVERIES = 3;

    public DBMessageService(DataSource dataSource) {
        this(dataSource, "db-msg-" + UUID.randomUUID(), NullTenantResolver.INSTANCE);
    }

    public DBMessageService(DataSource dataSource, String consumerId) {
        this(dataSource, consumerId, NullTenantResolver.INSTANCE);
    }

    /**
     * Create a DB-backed message service with a contextual tenant resolver
     * (plan 232 / vision §5.1). When the resolver reports a non-null tenant,
     * INSERT writes {@code TENANT_ID} and all SELECT/UPDATE inject the tenant
     * {@code WHERE} so one tenant's pending messages are invisible to another;
     * when {@code null}, SQL is byte-identical to the original (zero regression).
     *
     * @param dataSource     the JDBC data source; never null
     * @param consumerId     this consumer instance's identity; never null
     * @param tenantResolver the contextual tenant resolver; never null
     */
    public DBMessageService(DataSource dataSource, String consumerId, ITenantResolver tenantResolver) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.consumerId = Objects.requireNonNull(consumerId, "consumerId");
        this.tenantResolver = Objects.requireNonNull(tenantResolver, "tenantResolver");
    }

    private String currentTenant() {
        return tenantResolver.resolveTenantId();
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        if (pollIntervalMs <= 0) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "pollIntervalMs must be positive, got: " + pollIntervalMs);
        }
        this.pollIntervalMs = pollIntervalMs;
    }

    public void setMaxBatch(int maxBatch) {
        if (maxBatch <= 0) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "maxBatch must be positive, got: " + maxBatch);
        }
        this.maxBatch = maxBatch;
    }

    /**
     * Plan 271 (finding 14-02): how long a CLAIMED message may remain
     * un-consumed before the sweep task resets it to PENDING for redelivery.
     * Must be positive.
     */
    public void setStaleClaimTimeoutMs(long staleClaimTimeoutMs) {
        if (staleClaimTimeoutMs <= 0) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "staleClaimTimeoutMs must be positive, got: " + staleClaimTimeoutMs);
        }
        this.staleClaimTimeoutMs = staleClaimTimeoutMs;
    }

    /**
     * Plan 271 (finding 14-02): how often the stale-CLAIMED sweep task runs.
     * Must be positive.
     */
    public void setSweepIntervalMs(long sweepIntervalMs) {
        if (sweepIntervalMs <= 0) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "sweepIntervalMs must be positive, got: " + sweepIntervalMs);
        }
        this.sweepIntervalMs = sweepIntervalMs;
    }

    long getStaleClaimTimeoutMs() {
        return staleClaimTimeoutMs;
    }

    public String getConsumerId() {
        return consumerId;
    }

    /**
     * Plan 2519 (裁定⑨/b): per-message redelivery upper bound. Must be
     * positive. Zero {@code @cfg:} keys are introduced (setter injection only).
     */
    public void setMaxRedeliveries(int maxRedeliveries) {
        if (maxRedeliveries <= 0) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "maxRedeliveries must be positive, got: " + maxRedeliveries);
        }
        this.maxRedeliveries = maxRedeliveries;
    }

    /** Plan 2519 (裁定⑨/a): delivery executor size. Must be positive. */
    public void setDeliveryThreads(int deliveryThreads) {
        if (deliveryThreads <= 0) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "deliveryThreads must be positive, got: " + deliveryThreads);
        }
        this.deliveryThreads = deliveryThreads;
    }

    /** Plan 2519 (裁定⑨/a): delivery queue capacity. Must be positive. */
    public void setDeliveryQueueCapacity(int deliveryQueueCapacity) {
        if (deliveryQueueCapacity <= 0) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "deliveryQueueCapacity must be positive, got: " + deliveryQueueCapacity);
        }
        this.deliveryQueueCapacity = deliveryQueueCapacity;
    }

    long getDlqDropCount() {
        return dlqDropCount.get();
    }

    long getClaimFencedRejectCount() {
        return claimFencedRejectCount.get();
    }

    long getDeliveryRejectedCount() {
        return deliveryRejectedCount.get();
    }

    int maxRedeliveries() {
        return maxRedeliveries;
    }

    // ---- test hooks (package-private, governance tests only) ----
    long claimMessageForTest(String sid) {
        return claimMessage(sid);
    }

    void markConsumedFencedForTest(String sid, long claimGen) {
        markConsumedFenced(sid, claimGen);
    }

    /**
     * Initialize the DB schema and start the background polling thread.
     * Must be called before messages will be delivered to consumers.
     */
    public synchronized void start() {
        if (started) {
            return;
        }
        initSchema();
        // Plan 2519 (裁定⑨/a): dedicated bounded delivery executor with an
        // explicit rejection policy — a full queue is never silently dropped:
        // the rejected delivery is WARNed, counted, and its message is released
        // back to PENDING (the redelivery counter already incremented at claim
        // time, so a rejection storm terminates in the poison-message DLQ bound).
        ThreadFactory deliverFactory = r -> {
            Thread t = new Thread(r, "db-message-deliver-" + consumerId);
            t.setDaemon(true);
            return t;
        };
        deliveryExecutor = new ThreadPoolExecutor(deliveryThreads, deliveryThreads,
                0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(deliveryQueueCapacity),
                deliverFactory, (r, executor) -> {
                    // caller-runs on the poller thread: bounded DB roundtrip only
                    if (r instanceof DeliveryTask) {
                        onDeliveryRejected((DeliveryTask) r);
                    }
                });
        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "db-message-poller-" + consumerId);
            t.setDaemon(true);
            return t;
        });
        poller.scheduleWithFixedDelay(this::pollAllTopics, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
        // messages back to PENDING so a consumer that crashed or failed to
        // mark-consume/release does not permanently strand a message. This is
        // the at-least-once-delivery safety net. (裁定⑨/a: the sweep shares the
        // poller thread but the consumer callbacks no longer run there, so the
        // sweep is no longer starved by a blocking handler — CL-040.)
        poller.scheduleWithFixedDelay(this::sweepStaleClaimedMessagesSafely,
                sweepIntervalMs, sweepIntervalMs, TimeUnit.MILLISECONDS);
        started = true;
        LOG.info("nop.ai.agent.message.db-message-service-started:consumerId={}", consumerId);
    }

    /**
     * Stop the background polling thread and release resources.
     * After close, this instance no longer consumes any topic.
     */
    @Override
    public synchronized void close() {
        closed = true;
        if (poller != null) {
            poller.shutdownNow();
            try {
                poller.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            poller = null;
        }
        if (deliveryExecutor != null) {
            // bounded shutdown: queued deliveries are discarded by shutdownNow;
            // their messages stay CLAIMED and the stale-CLAIMED sweep redelivers
            // them (at-least-once preserved). The discard is observable, not silent.
            deliveryExecutor.shutdown();
            try {
                if (!deliveryExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    int discarded = deliveryExecutor.shutdownNow().size();
                    if (discarded > 0) {
                        LOG.warn("nop.ai.agent.message.db-delivery-discarded-on-close:count={}, consumerId={}",
                                discarded, consumerId);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                deliveryExecutor.shutdownNow();
            }
            deliveryExecutor = null;
        }
        started = false;
        LOG.info("nop.ai.agent.message.db-message-service-stopped:consumerId={}", consumerId);
    }

    private void initSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentMessageTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentMessageTable.DDL_CREATE_INDEX);
            // Plan 2519 (裁定⑨/b): dead-letter table for poison messages.
            stmt.execute(AiAgentMessageTable.DDL_CREATE_DLQ_TABLE);
            // pre-existing deployments: best-effort column upgrade, guarded so a
            // dialect without ADD COLUMN support only warns (fresh tables carry
            // the columns via CREATE TABLE).
            addColumnIfMissing(stmt, AiAgentMessageTable.COL_DELIVERY_ATTEMPTS,
                    "ALTER TABLE " + AiAgentMessageTable.TABLE_NAME + " ADD COLUMN "
                            + AiAgentMessageTable.COL_DELIVERY_ATTEMPTS + " INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(stmt, AiAgentMessageTable.COL_CLAIM_GEN,
                    "ALTER TABLE " + AiAgentMessageTable.TABLE_NAME + " ADD COLUMN "
                            + AiAgentMessageTable.COL_CLAIM_GEN + " INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBMessageService: failed to initialize schema: " + e.getMessage());
        }
    }

    private void addColumnIfMissing(Statement stmt, String column, String ddl) {
        try {
            stmt.execute(ddl);
        } catch (SQLException e) {
            LOG.info("nop.ai.agent.message.db-schema-upgrade-skipped:column={}", column, e);
        }
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        MessageOptionsValidator.validateSend(CAPABILITIES, options, "db-agent", WARN_ONCE);
        Objects.requireNonNull(topic, "topic must not be null");
        Objects.requireNonNull(message, "message must not be null");

        String json = serializeForDb(message);
        String sid = StringHelper.generateUUID();
        String tenant = currentTenant();

        String sql = "INSERT INTO " + AiAgentMessageTable.TABLE_NAME
                + " (" + AiAgentMessageTable.COL_SID + ", "
                + AiAgentMessageTable.COL_TOPIC + ", "
                + AiAgentMessageTable.COL_MESSAGE_BODY + ", "
                + AiAgentMessageTable.COL_STATUS + ", "
                + AiAgentMessageTable.COL_CREATED_AT;
        if (tenant != null) {
            sql += ", " + AiAgentMessageTable.COL_TENANT_ID;
        }
        sql += ") VALUES (?, ?, ?, ?, ?";
        if (tenant != null) {
            sql += ", ?";
        }
        sql += ")";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sid);
            ps.setString(2, topic);
            ps.setString(3, json);
            ps.setInt(4, AiAgentMessageTable.STATUS_PENDING);
            ps.setTimestamp(5, CoreMetrics.currentTimestamp());
            if (tenant != null) {
                ps.setString(6, tenant);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBMessageService: failed to persist message to topic '" + topic + "': " + e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        MessageOptionsValidator.validateSubscribe(CAPABILITIES, options, "db-agent", WARN_ONCE);
        if (topic == null || topic.isEmpty()) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "DBMessageService.subscribe: topic must not be null or empty");
        }
        Objects.requireNonNull(listener, "listener must not be null");

        // Plan 2519 (裁定⑨/c, audit C1-019/CL-024): a duplicate registration on
        // the same topic used to silently replace the previous consumer (identity
        // theft). Replacement is now an explicit typed rejection; a legitimate
        // re-register must cancel the old subscription first (cancel/subscribe
        // symmetry — see AgentCallDelegate's existing pattern).
        ConsumerRegistration registration = new ConsumerRegistration(listener, consumerId);
        ConsumerRegistration previous = consumers.putIfAbsent(topic, registration);
        if (previous != null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL,
                    "DBMessageService.subscribe: topic '" + topic
                            + "' already has a registered consumer (ownerId=" + previous.ownerId
                            + "); duplicate registration would silently replace it and is rejected"
                            + " — cancel the existing subscription before re-registering");
        }
        DbSubscription subscription = new DbSubscription(topic, registration);
        LOG.debug("nop.ai.agent.message.db-subscribe:topic={}, consumerId={}", topic, consumerId);
        return subscription;
    }

    void unregisterConsumer(String topic, ConsumerRegistration expected) {
        // identity-conditional removal: a stale subscription handle must not
        // unregister a newer consumer's registration
        if (expected == null) {
            consumers.remove(topic);
        } else {
            consumers.remove(topic, expected);
        }
        LOG.debug("nop.ai.agent.message.db-unsubscribe:topic={}, consumerId={}", topic, consumerId);
    }

    private void pollAllTopics() {
        if (closed || consumers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, ConsumerRegistration> entry : consumers.entrySet()) {
            if (closed) {
                break;
            }
            // Plan 2519 (裁定⑨/CL-069): the suspend flag used to be a dead flag
            // (set but never read). A suspended subscription now stops delivery.
            if (entry.getValue().suspended) {
                continue;
            }
            String topic = entry.getKey();
            try {
                pollTopic(topic);
            } catch (Throwable e) {
                // MA-07/C3-027 (plan 2496 Phase 4): per-topic guard must cover Throwable.
                // An Error escaping the per-topic catch would kill the single poller
                // thread (JDK suppresses the periodic task after any Throwable) — every
                // topic on this consumer stops being delivered with zero trace. Catching
                // per topic keeps the poller alive for the remaining topics and keeps
                // the failed round observable.
                LOG.error("nop.ai.agent.message.db-poll-error:topic={}, consumerId={}", topic, consumerId, e);
            }
        }
    }

    private void pollTopic(String topic) {
        ConsumerRegistration registration = consumers.get(topic);
        if (registration == null || closed || registration.suspended) {
            return;
        }

        List<MessageRow> pending = findPending(topic, maxBatch);
        for (MessageRow row : pending) {
            if (closed) {
                break;
            }
            // Re-check subscription: cancel() may have removed the consumer
            // since we entered pollTopic or since the previous iteration
            registration = consumers.get(topic);
            if (registration == null) {
                break;
            }
            // Plan 2519 (裁定⑨/b, audit C7-025): poison-message upper bound — a
            // message whose delivery attempts already reached the redelivery cap
            // is dead-lettered and ack-skipped instead of being requeued forever.
            if (row.deliveryAttempts >= maxRedeliveries) {
                deadLetter(row, "delivery attempts exhausted: " + row.deliveryAttempts);
                continue;
            }
            long claimGen = claimMessage(row.sid);
            if (claimGen < 0) {
                continue;
            }
            // Re-check after claiming: cancel() may have occurred during the claim
            registration = consumers.get(topic);
            if (registration == null) {
                releaseClaimFencedQuietly(row.sid, claimGen);
                break;
            }
            // Plan 2519 (裁定⑨/a): the consumer callback runs on the dedicated
            // bounded delivery executor — a blocking handler (call-agent, 120s)
            // no longer freezes polling of every other topic.
            DeliveryTask task = new DeliveryTask(row.sid, topic, row.messageBody, claimGen,
                    row.deliveryAttempts + 1);
            try {
                deliveryExecutor.execute(task);
            } catch (RejectedExecutionException e) {
                // executor already shutdown (race with close): leave the message
                // CLAIMED; the stale-CLAIMED sweep redelivers it.
                LOG.warn("nop.ai.agent.message.db-delivery-not-accepted:sid={}, topic={}", row.sid, topic);
            }
        }
    }

    /**
     * Plan 2519 (裁定⑨/a): delivery task executed on the delivery executor.
     * Carries the claim generation so the consume/release writes are
     * generation-fenced (裁定⑨/d).
     */
    private class DeliveryTask implements Runnable {
        final String sid;
        final String topic;
        final String messageBody;
        final long claimGen;
        final int attempt;

        DeliveryTask(String sid, String topic, String messageBody, long claimGen, int attempt) {
            this.sid = sid;
            this.topic = topic;
            this.messageBody = messageBody;
            this.claimGen = claimGen;
            this.attempt = attempt;
        }

        @Override
        public void run() {
            try {
                runDelivery();
            } catch (Throwable err) {
                // MA-07/C3-027 (plan 2496 Phase 4) same-root guard widening, kept on
                // the delivery leg (裁定⑨/a): an Error escaping the delivery task
                // would kill the delivery worker thread with zero trace. The Error
                // round stays observable under the db-poll-error log family and the
                // message stays CLAIMED (its claim release belongs to the
                // stale-CLAIMED sweep) — at-least-once preserved.
                LOG.error("nop.ai.agent.message.db-poll-error:sid={}, topic={}, consumerId={}",
                        sid, topic, consumerId, err);
            }
        }

        private void runDelivery() {
            if (closed) {
                releaseClaimFencedQuietly(sid, claimGen);
                return;
            }
            ConsumerRegistration registration = consumers.get(topic);
            if (registration == null) {
                releaseClaimFencedQuietly(sid, claimGen);
                return;
            }
            try {
                Object message = deserializeFromDb(messageBody);
                IMessageConsumeContext context = new DbConsumeContext();
                Object result = registration.consumer.onMessage(topic, message, context);
                handleConsumerResult(sid, claimGen, result);
            } catch (Exception e) {
                LOG.error("nop.ai.agent.message.db-deliver-error:sid={}, topic={}, attempt={}",
                        sid, topic, attempt, e);
                // remember the root failure so the eventual DLQ row carries it
                lastFailureReasons.put(sid, String.valueOf(e));
                // release back to PENDING; the attempts counter was already
                // incremented at claim time, so a persistently failing message
                // terminates in the poison-message DLQ bound (裁定⑨/b).
                releaseClaimFencedQuietly(sid, claimGen);
            }
        }
    }

    /**
     * Plan 2519 (裁定⑨/a): explicit rejection policy for a full delivery queue.
     * The message is released back to PENDING with its redelivery counter
     * already incremented at claim time — the rejection and the poison cap are
     * the same fallback chain, so a rejection storm terminates in the DLQ bound.
     * Never silently discarded.
     */
    private void onDeliveryRejected(DeliveryTask task) {
        deliveryRejectedCount.incrementAndGet();
        LOG.warn("nop.ai.message.delivery-rejected:sid={}, topic={}, attempt={}, consumerId={}",
                task.sid, task.topic, task.attempt, consumerId);
        if (task.attempt >= maxRedeliveries) {
            deadLetter(new MessageRow(task.sid, task.topic, task.messageBody, task.attempt - 1),
                    "delivery queue full at attempt " + task.attempt);
        } else {
            releaseClaimFencedQuietly(task.sid, task.claimGen);
        }
    }

    private List<MessageRow> findPending(String topic, int limit) {
        List<MessageRow> rows = new ArrayList<>();
        String tenant = currentTenant();
        String sql = "SELECT " + AiAgentMessageTable.COL_SID + ", "
                + AiAgentMessageTable.COL_MESSAGE_BODY + ", "
                + AiAgentMessageTable.COL_DELIVERY_ATTEMPTS
                + " FROM " + AiAgentMessageTable.TABLE_NAME
                + " WHERE " + AiAgentMessageTable.COL_TOPIC + " = ?"
                + " AND " + AiAgentMessageTable.COL_STATUS + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentMessageTable.COL_TENANT_ID);
        }
        sql += " ORDER BY " + AiAgentMessageTable.COL_CREATED_AT
                + " LIMIT ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, topic);
            ps.setInt(2, AiAgentMessageTable.STATUS_PENDING);
            if (tenant != null) {
                ps.setString(3, tenant);
                ps.setInt(4, limit);
            } else {
                ps.setInt(3, limit);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new MessageRow(rs.getString(1), topic, rs.getString(2), rs.getInt(3)));
                }
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBMessageService: failed to query pending messages for topic '" + topic + "': " + e.getMessage());
        }
        return rows;
    }

    /**
     * Plan 2519 (裁定⑨/d, audit CL-069): claim with generation fencing. The
     * conditional UPDATE atomically moves PENDING → CLAIMED and increments the
     * claim generation; the returned generation is carried by the delivery task
     * so consume/release writes are conditioned on it — a stale (old-generation)
     * consumer write is rejected instead of clobbering the newer claim.
     *
     * @return the new claim generation, or -1 if the message was claimed by
     *         another consumer in the meantime
     */
    private long claimMessage(String sid) {
        String tenant = currentTenant();
        String sql = "UPDATE " + AiAgentMessageTable.TABLE_NAME
                + " SET " + AiAgentMessageTable.COL_STATUS + " = ?, "
                + AiAgentMessageTable.COL_CONSUMER_ID + " = ?, "
                + AiAgentMessageTable.COL_CLAIMED_AT + " = ?, "
                + AiAgentMessageTable.COL_CLAIM_GEN + " = " + AiAgentMessageTable.COL_CLAIM_GEN + " + 1, "
                + AiAgentMessageTable.COL_DELIVERY_ATTEMPTS + " = " + AiAgentMessageTable.COL_DELIVERY_ATTEMPTS + " + 1"
                + " WHERE " + AiAgentMessageTable.COL_SID + " = ?"
                + " AND " + AiAgentMessageTable.COL_STATUS + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentMessageTable.COL_TENANT_ID);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, AiAgentMessageTable.STATUS_CLAIMED);
            ps.setString(2, consumerId);
            ps.setTimestamp(3, CoreMetrics.currentTimestamp());
            ps.setString(4, sid);
            ps.setInt(5, AiAgentMessageTable.STATUS_PENDING);
            if (tenant != null) {
                ps.setString(6, tenant);
            }
            if (ps.executeUpdate() <= 0) {
                return -1;
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBMessageService: failed to claim message " + sid);
        }
        return readClaimGeneration(sid, tenant);
    }

    private long readClaimGeneration(String sid, String tenant) {
        String sql = "SELECT " + AiAgentMessageTable.COL_CLAIM_GEN
                + " FROM " + AiAgentMessageTable.TABLE_NAME
                + " WHERE " + AiAgentMessageTable.COL_SID + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentMessageTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sid);
            if (tenant != null) {
                ps.setString(2, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBMessageService: failed to read claim generation for message " + sid);
        }
    }

    /**
     * Reset a claimed message back to PENDING (release the claim) so it can be
     * redelivered.
     *
     * <p>Plan 271 (finding 14-02): a {@link SQLException} here is no longer
     * silently swallowed. It is wrapped in a {@link NopAiAgentException} and
     * propagated so the failure is visible. If this reset fails, the message
     * stays CLAIMED; the periodic stale-CLAIMED sweep
     * ({@link #sweepStaleClaimedMessages(long)}) is the safety net that
     * eventually redelivers it, preserving at-least-once delivery rather than
     * permanently stranding it.
     */
    void releaseClaim(String sid) {
        String tenant = currentTenant();
        String sql = "UPDATE " + AiAgentMessageTable.TABLE_NAME
                + " SET " + AiAgentMessageTable.COL_STATUS + " = ?, "
                + AiAgentMessageTable.COL_CONSUMER_ID + " = NULL, "
                + AiAgentMessageTable.COL_CLAIMED_AT + " = NULL"
                + " WHERE " + AiAgentMessageTable.COL_SID + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentMessageTable.COL_TENANT_ID);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, AiAgentMessageTable.STATUS_PENDING);
            ps.setString(2, sid);
            if (tenant != null) {
                ps.setString(3, tenant);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBMessageService: failed to release claim for message " + sid + ": " + e.getMessage());
        }
    }

    /**
     * Best-effort {@link #releaseClaim} that never throws — used from recovery
     * paths (e.g. the async consume callback) where a throw would be lost. A
     * failure is logged; the message stays CLAIMED and is recovered by the
     * stale-CLAIMED sweep.
     */
    private void releaseClaimQuietly(String sid) {
        try {
            releaseClaim(sid);
        } catch (RuntimeException e) {
            LOG.error("nop.ai.agent.message.db-release-claim-error:sid={}", sid, e);
        }
    }

    /**
     * Plan 2519 (裁定⑨/d): generation-fenced release — resets the message to
     * PENDING only when the message is still CLAIMED under the given claim
     * generation. A stale generation write is rejected (WARN +
     * {@code nop.ai.message.claim-fenced-reject} counter).
     */
    private void releaseClaimFenced(String sid, long claimGen) {
        String tenant = currentTenant();
        String sql = "UPDATE " + AiAgentMessageTable.TABLE_NAME
                + " SET " + AiAgentMessageTable.COL_STATUS + " = ?, "
                + AiAgentMessageTable.COL_CONSUMER_ID + " = NULL, "
                + AiAgentMessageTable.COL_CLAIMED_AT + " = NULL"
                + " WHERE " + AiAgentMessageTable.COL_SID + " = ?"
                + " AND " + AiAgentMessageTable.COL_STATUS + " = ?"
                + " AND " + AiAgentMessageTable.COL_CLAIM_GEN + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentMessageTable.COL_TENANT_ID);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, AiAgentMessageTable.STATUS_PENDING);
            ps.setString(2, sid);
            ps.setInt(3, AiAgentMessageTable.STATUS_CLAIMED);
            ps.setLong(4, claimGen);
            if (tenant != null) {
                ps.setString(5, tenant);
            }
            if (ps.executeUpdate() == 0) {
                claimFencedRejectCount.incrementAndGet();
                LOG.warn("nop.ai.message.claim-fenced-reject:op=release, sid={}, claimGen={}, consumerId={}",
                        sid, claimGen, consumerId);
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBMessageService: failed to release claim (fenced) for message " + sid + ": " + e.getMessage());
        }
    }

    private void releaseClaimFencedQuietly(String sid, long claimGen) {
        try {
            releaseClaimFenced(sid, claimGen);
        } catch (RuntimeException e) {
            LOG.error("nop.ai.agent.message.db-release-claim-error:sid={}", sid, e);
        }
    }

    /**
     * Plan 2519 (裁定⑨/d): generation-fenced consume — marks the message
     * consumed only when it is still CLAIMED under the given generation.
     */
    private void markConsumedFenced(String sid, long claimGen) {
        String tenant = currentTenant();
        String sql = "UPDATE " + AiAgentMessageTable.TABLE_NAME
                + " SET " + AiAgentMessageTable.COL_STATUS + " = ?, "
                + AiAgentMessageTable.COL_CONSUMED_AT + " = ?"
                + " WHERE " + AiAgentMessageTable.COL_SID + " = ?"
                + " AND " + AiAgentMessageTable.COL_STATUS + " = ?"
                + " AND " + AiAgentMessageTable.COL_CLAIM_GEN + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentMessageTable.COL_TENANT_ID);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, AiAgentMessageTable.STATUS_CONSUMED);
            ps.setTimestamp(2, CoreMetrics.currentTimestamp());
            ps.setString(3, sid);
            ps.setInt(4, AiAgentMessageTable.STATUS_CLAIMED);
            ps.setLong(5, claimGen);
            if (tenant != null) {
                ps.setString(6, tenant);
            }
            if (ps.executeUpdate() > 0) {
                lastFailureReasons.remove(sid);
            } else {
                claimFencedRejectCount.incrementAndGet();
                LOG.warn("nop.ai.message.claim-fenced-reject:op=consume, sid={}, claimGen={}, consumerId={}",
                        sid, claimGen, consumerId);
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBMessageService: failed to mark message consumed (fenced): sid=" + sid + ": " + e.getMessage());
        }
    }

    /**
     * Plan 2519 (裁定⑨/b): move a poison message to the dead-letter table
     * (self-built JDBC table, zero ORM source-model touch) and ack-skip the
     * original row. The dead-lettering is explicitly observable
     * ({@code nop.ai.message.dlq-drop} WARN + counter) — the queue never stalls
     * on a poison message again.
     */
    private void deadLetter(MessageRow row, String reason) {
        String tenant = currentTenant();
        String rootReason = lastFailureReasons.remove(row.sid);
        String effectiveReason = rootReason != null
                ? rootReason + " (dead-lettered: " + reason + ")"
                : reason;
        String insertSql = "INSERT INTO " + AiAgentMessageTable.DLQ_TABLE_NAME
                + " (" + AiAgentMessageTable.COL_DLQ_SID + ", "
                + AiAgentMessageTable.COL_DLQ_TOPIC + ", "
                + AiAgentMessageTable.COL_DLQ_MESSAGE_BODY + ", "
                + AiAgentMessageTable.COL_DLQ_FAIL_REASON + ", "
                + AiAgentMessageTable.COL_DLQ_ATTEMPTS + ", "
                + AiAgentMessageTable.COL_DLQ_CREATED_AT;
        String updateSql = "UPDATE " + AiAgentMessageTable.TABLE_NAME
                + " SET " + AiAgentMessageTable.COL_STATUS + " = ?, "
                + AiAgentMessageTable.COL_CONSUMER_ID + " = NULL, "
                + AiAgentMessageTable.COL_CLAIMED_AT + " = NULL"
                + " WHERE " + AiAgentMessageTable.COL_SID + " = ?";
        if (tenant != null) {
            insertSql += ", " + AiAgentMessageTable.COL_DLQ_TENANT_ID;
            updateSql += TenantSql.whereTenant(AiAgentMessageTable.COL_TENANT_ID);
        }
        insertSql += ") VALUES (?, ?, ?, ?, ?, ?";
        if (tenant != null) {
            insertSql += ", ?";
        }
        insertSql += ")";

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, row.sid);
                ps.setString(2, row.topic != null ? row.topic : "unknown");
                ps.setString(3, row.messageBody);
                ps.setString(4, truncateReason(effectiveReason));
                ps.setInt(5, row.deliveryAttempts);
                ps.setTimestamp(6, CoreMetrics.currentTimestamp());
                if (tenant != null) {
                    ps.setString(7, tenant);
                }
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, AiAgentMessageTable.STATUS_DEAD_LETTER);
                ps.setString(2, row.sid);
                if (tenant != null) {
                    ps.setString(3, tenant);
                }
                ps.executeUpdate();
            }
            dlqDropCount.incrementAndGet();
            LOG.warn("nop.ai.message.dlq-drop:sid={}, attempts={}, reason={}, consumerId={}",
                    row.sid, row.deliveryAttempts, effectiveReason, consumerId);
        } catch (SQLException e) {
            // DLQ insert failed: leave the message PENDING so the normal
            // redelivery path (and the cap re-check) retries dead-lettering —
            // never silently lost.
            LOG.error("nop.ai.agent.message.db-dlq-error:sid={}", row.sid, e);
        }
    }

    private static String truncateReason(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }

    /**
     * Mark a claimed message as consumed (terminal).
     *
     * <p>Plan 271 (finding 14-02): a {@link SQLException} here is no longer
     * silently swallowed (the original code only logged it, leaving the
     * message permanently stranded in CLAIMED with no redelivery). It is now
     * wrapped in a {@link NopAiAgentException} and propagated. In the
     * synchronous delivery path the caller ({@code pollTopic}) catches it and
     * calls {@link #releaseClaim} to reset the message to PENDING for
     * redelivery; in the asynchronous path the message stays CLAIMED until the
     * stale-CLAIMED sweep ({@link #sweepStaleClaimedMessages(long)}) resets
     * it. Either way the message is never permanently lost.
     */
    void markConsumed(String sid) {
        String tenant = currentTenant();
        String sql = "UPDATE " + AiAgentMessageTable.TABLE_NAME
                + " SET " + AiAgentMessageTable.COL_STATUS + " = ?, "
                + AiAgentMessageTable.COL_CONSUMED_AT + " = ?"
                + " WHERE " + AiAgentMessageTable.COL_SID + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentMessageTable.COL_TENANT_ID);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, AiAgentMessageTable.STATUS_CONSUMED);
            ps.setTimestamp(2, CoreMetrics.currentTimestamp());
            ps.setString(3, sid);
            if (tenant != null) {
                ps.setString(4, tenant);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBMessageService: failed to mark message consumed: sid=" + sid + ": " + e.getMessage());
        }
    }

    /**
     * Plan 271 (finding 14-02): reset stale CLAIMED messages back to PENDING so
     * they can be redelivered. A message is "stale" when it has been in
     * CLAIMED status longer than {@code staleTimeoutMs} (its
     * {@code CLAIMED_AT} timestamp predates {@code now - staleTimeoutMs}).
     *
     * <p>This is the safety net that guarantees at-least-once delivery when a
     * consumer crashes or when {@code markConsumed}/{@code releaseClaim}
     * themselves fail: rather than leaving the message permanently stranded in
     * CLAIMED, it is returned to PENDING for any competing consumer (including
     * a restarted instance) to pick up.
     *
     * @param staleTimeoutMs the age (in milliseconds) after which a CLAIMED
     *                       message is considered stale; must be positive
     * @return the number of messages reset from CLAIMED to PENDING
     */
    int sweepStaleClaimedMessages(long staleTimeoutMs) {
        if (staleTimeoutMs <= 0) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "staleTimeoutMs must be positive, got: " + staleTimeoutMs);
        }
        String tenant = currentTenant();
        String sql = "UPDATE " + AiAgentMessageTable.TABLE_NAME
                + " SET " + AiAgentMessageTable.COL_STATUS + " = ?, "
                + AiAgentMessageTable.COL_CONSUMER_ID + " = NULL, "
                + AiAgentMessageTable.COL_CLAIMED_AT + " = NULL"
                + " WHERE " + AiAgentMessageTable.COL_STATUS + " = ?"
                + " AND " + AiAgentMessageTable.COL_CLAIMED_AT + " < ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentMessageTable.COL_TENANT_ID);
        }

        Timestamp cutoff = new Timestamp(CoreMetrics.currentTimeMillis() - staleTimeoutMs);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, AiAgentMessageTable.STATUS_PENDING);
            ps.setInt(2, AiAgentMessageTable.STATUS_CLAIMED);
            ps.setTimestamp(3, cutoff);
            if (tenant != null) {
                ps.setString(4, tenant);
            }
            int reset = ps.executeUpdate();
            if (reset > 0) {
                LOG.info("nop.ai.agent.message.db-sweep-stale-claimed:reset={}, consumerId={}, staleTimeoutMs={}",
                        reset, consumerId, staleTimeoutMs);
            }
            return reset;
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBMessageService: failed to sweep stale claimed messages: " + e.getMessage());
        }
    }

    /**
     * Sweep wrapper used by the scheduled task: never throws (a throw would
     * suppress subsequent scheduled executions). Logs and continues.
     */
    private void sweepStaleClaimedMessagesSafely() {
        if (closed) {
            return;
        }
        try {
            sweepStaleClaimedMessages(staleClaimTimeoutMs);
        } catch (Throwable e) {
            // MA-07/C7-029 (plan 2496 Phase 4): same-root guard widening as pollAllTopics —
            // an Error escaping this wrapper would permanently kill the sweep task on the
            // shared poller thread (the at-least-once safety net dies silently).
            LOG.error("nop.ai.agent.message.db-sweep-stale-claimed-error:consumerId={}", consumerId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleConsumerResult(String sid, long claimGen, Object result) {
        if (result instanceof CompletionStage) {
            ((CompletionStage<Object>) result).whenComplete((r, e) -> {
                // Plan 2519 (裁定⑨/b): guarded failure callback — the callback
                // itself must never throw (4.1-C8-002 contract alignment,
                // attempt=0 base counting is carried by DELIVERY_ATTEMPTS which
                // starts at 0 for a fresh message and counts each claim).
                if (e != null) {
                    LOG.error("nop.ai.agent.message.db-async-consume-error:sid={}", sid, e);
                    releaseClaimFencedQuietly(sid, claimGen);
                } else {
                    // are on the async completion thread with no enclosing
                    // try/catch, so fall back to releasing the claim (reset to
                    // PENDING for redelivery). releaseClaimFencedQuietly never
                    // throws; if it also fails the stale-CLAIMED sweep is the
                    // final safety net.
                    try {
                        handleConsumerResult(sid, claimGen, r);
                    } catch (RuntimeException ex) {
                        LOG.error("nop.ai.agent.message.db-async-mark-consumed-error:sid={}", sid, ex);
                        releaseClaimFencedQuietly(sid, claimGen);
                    }
                }
            });
        } else if (result instanceof ConsumeLater) {
            releaseClaimFencedQuietly(sid, claimGen);
        } else {
            markConsumedFenced(sid, claimGen);
        }
    }

    private String serializeForDb(Object message) {
        if (message instanceof AgentMessageEnvelope) {
            return AgentMessageEnvelopeJson.toJson((AgentMessageEnvelope) message);
        }
        return AgentMessageEnvelopeJson.toJson(
                new AgentMessageEnvelope(null, null, null, AgentMessageKind.ASYNC, message));
    }

    private static Object deserializeFromDb(String json) {
        AgentMessageEnvelope env = AgentMessageEnvelopeJson.fromJson(json);
        if (env.getKind() == AgentMessageKind.ASYNC
                && env.getSenderId() == null
                && env.getTargetTopic() == null
                && env.getCorrelationId() == null) {
            return env.getPayload();
        }
        return env;
    }

    private static class MessageRow {
        final String sid;
        final String topic;
        final String messageBody;
        final int deliveryAttempts;

        MessageRow(String sid, String topic, String messageBody, int deliveryAttempts) {
            this.sid = sid;
            this.topic = topic;
            this.messageBody = messageBody;
            this.deliveryAttempts = deliveryAttempts;
        }
    }

    /**
     * Plan 2519 (裁定⑨/c): consumer registration carrying the owner identity.
     * The suspend flag used to live on the subscription only (audit CL-069
     * dead-flag face) and was never consulted — it now gates delivery.
     */
    private static final class ConsumerRegistration {
        final IMessageConsumer consumer;
        final String ownerId;
        volatile boolean suspended;

        ConsumerRegistration(IMessageConsumer consumer, String ownerId) {
            this.consumer = consumer;
            this.ownerId = ownerId;
        }
    }

    private class DbSubscription implements IMessageSubscription {
        private final String topic;
        /** The registration captured at subscribe time (identity anchor, 裁定⑨/c). */
        private final ConsumerRegistration registration;
        private volatile boolean cancelled = false;

        DbSubscription(String topic, ConsumerRegistration registration) {
            this.topic = topic;
            this.registration = registration;
        }

        @Override
        public void cancel() {
            cancelled = true;
            // identity-conditional: only the registration this handle belongs
            // to is removed (cancel/subscribe symmetry, 裁定⑨/c)
            unregisterConsumer(topic, registration);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isSuspended() {
            return registration.suspended;
        }

        @Override
        public void suspend() {
            registration.suspended = true;
        }

        @Override
        public void resume() {
            registration.suspended = false;
        }
    }

    private class DbConsumeContext implements IMessageConsumeContext {
        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return DBMessageService.this.sendAsync(topic, message, options);
        }
    }
}
