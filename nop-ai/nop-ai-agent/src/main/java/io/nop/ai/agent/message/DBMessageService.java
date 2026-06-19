package io.nop.ai.agent.message;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.security.ITenantResolver;
import io.nop.ai.agent.security.NullTenantResolver;
import io.nop.ai.agent.security.TenantSql;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
public class DBMessageService implements IMessageService, AutoCloseable {

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

    private final Map<String, IMessageConsumer> consumers = new ConcurrentHashMap<>();

    private ScheduledExecutorService poller;
    private volatile boolean started = false;
    private volatile boolean closed = false;

    private long pollIntervalMs = DEFAULT_POLL_INTERVAL_MS;
    private int maxBatch = DEFAULT_MAX_BATCH;
    private long staleClaimTimeoutMs = DEFAULT_STALE_CLAIM_TIMEOUT_MS;
    private long sweepIntervalMs = DEFAULT_SWEEP_INTERVAL_MS;

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
            throw new NopAiAgentException("pollIntervalMs must be positive, got: " + pollIntervalMs);
        }
        this.pollIntervalMs = pollIntervalMs;
    }

    public void setMaxBatch(int maxBatch) {
        if (maxBatch <= 0) {
            throw new NopAiAgentException("maxBatch must be positive, got: " + maxBatch);
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
            throw new NopAiAgentException("staleClaimTimeoutMs must be positive, got: " + staleClaimTimeoutMs);
        }
        this.staleClaimTimeoutMs = staleClaimTimeoutMs;
    }

    /**
     * Plan 271 (finding 14-02): how often the stale-CLAIMED sweep task runs.
     * Must be positive.
     */
    public void setSweepIntervalMs(long sweepIntervalMs) {
        if (sweepIntervalMs <= 0) {
            throw new NopAiAgentException("sweepIntervalMs must be positive, got: " + sweepIntervalMs);
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
     * Initialize the DB schema and start the background polling thread.
     * Must be called before messages will be delivered to consumers.
     */
    public synchronized void start() {
        if (started) {
            return;
        }
        initSchema();
        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "db-message-poller-" + consumerId);
            t.setDaemon(true);
            return t;
        });
        poller.scheduleWithFixedDelay(this::pollAllTopics, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
        // Plan 271 (finding 14-02): periodic sweep resets stale CLAIMED
        // messages back to PENDING so a consumer that crashed or failed to
        // mark-consume/release does not permanently strand a message. This is
        // the at-least-once-delivery safety net.
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
        started = false;
        LOG.info("nop.ai.agent.message.db-message-service-stopped:consumerId={}", consumerId);
    }

    private void initSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentMessageTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentMessageTable.DDL_CREATE_INDEX);
        } catch (SQLException e) {
            throw new NopAiAgentException("DBMessageService: failed to initialize schema: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
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
            ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            if (tenant != null) {
                ps.setString(6, tenant);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBMessageService: failed to persist message to topic '" + topic + "': " + e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        if (topic == null || topic.isEmpty()) {
            throw new NopAiAgentException("DBMessageService.subscribe: topic must not be null or empty");
        }
        Objects.requireNonNull(listener, "listener must not be null");

        consumers.put(topic, listener);
        DbSubscription subscription = new DbSubscription(topic);
        LOG.debug("nop.ai.agent.message.db-subscribe:topic={}, consumerId={}", topic, consumerId);
        return subscription;
    }

    void unregisterConsumer(String topic) {
        consumers.remove(topic);
        LOG.debug("nop.ai.agent.message.db-unsubscribe:topic={}, consumerId={}", topic, consumerId);
    }

    private void pollAllTopics() {
        if (closed || consumers.isEmpty()) {
            return;
        }
        for (String topic : consumers.keySet()) {
            if (closed) {
                break;
            }
            try {
                pollTopic(topic);
            } catch (Exception e) {
                LOG.error("nop.ai.agent.message.db-poll-error:topic={}, consumerId={}", topic, consumerId, e);
            }
        }
    }

    private void pollTopic(String topic) {
        IMessageConsumer consumer = consumers.get(topic);
        if (consumer == null || closed) {
            return;
        }

        List<MessageRow> pending = findPending(topic, maxBatch);
        for (MessageRow row : pending) {
            if (closed) {
                break;
            }
            // Re-check subscription: cancel() may have removed the consumer
            // since we entered pollTopic or since the previous iteration
            if (!consumers.containsKey(topic)) {
                break;
            }
            if (!claimMessage(row.sid)) {
                continue;
            }
            // Re-check after claiming: cancel() may have occurred during the claim
            if (!consumers.containsKey(topic)) {
                releaseClaim(row.sid);
                break;
            }
            try {
                Object message = deserializeFromDb(row.messageBody);
                IMessageConsumeContext context = new DbConsumeContext();
                Object result = consumer.onMessage(topic, message, context);
                handleConsumerResult(row.sid, result);
            } catch (Exception e) {
                LOG.error("nop.ai.agent.message.db-deliver-error:sid={}, topic={}", row.sid, topic, e);
                releaseClaim(row.sid);
            }
        }
    }

    private List<MessageRow> findPending(String topic, int limit) {
        List<MessageRow> rows = new ArrayList<>();
        String tenant = currentTenant();
        String sql = "SELECT " + AiAgentMessageTable.COL_SID + ", "
                + AiAgentMessageTable.COL_MESSAGE_BODY
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
                    rows.add(new MessageRow(rs.getString(1), rs.getString(2)));
                }
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBMessageService: failed to query pending messages for topic '" + topic + "': " + e.getMessage(), e);
        }
        return rows;
    }

    private boolean claimMessage(String sid) {
        String tenant = currentTenant();
        String sql = "UPDATE " + AiAgentMessageTable.TABLE_NAME
                + " SET " + AiAgentMessageTable.COL_STATUS + " = ?, "
                + AiAgentMessageTable.COL_CONSUMER_ID + " = ?, "
                + AiAgentMessageTable.COL_CLAIMED_AT + " = ?"
                + " WHERE " + AiAgentMessageTable.COL_SID + " = ?"
                + " AND " + AiAgentMessageTable.COL_STATUS + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentMessageTable.COL_TENANT_ID);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, AiAgentMessageTable.STATUS_CLAIMED);
            ps.setString(2, consumerId);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.setString(4, sid);
            ps.setInt(5, AiAgentMessageTable.STATUS_PENDING);
            if (tenant != null) {
                ps.setString(6, tenant);
            }
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new NopAiAgentException("DBMessageService: failed to claim message " + sid, e);
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
            throw new NopAiAgentException(
                    "DBMessageService: failed to release claim for message " + sid + ": " + e.getMessage(), e);
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
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setString(3, sid);
            if (tenant != null) {
                ps.setString(4, tenant);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBMessageService: failed to mark message consumed: sid=" + sid + ": " + e.getMessage(), e);
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
            throw new NopAiAgentException("staleTimeoutMs must be positive, got: " + staleTimeoutMs);
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

        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - staleTimeoutMs);
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
            throw new NopAiAgentException(
                    "DBMessageService: failed to sweep stale claimed messages: " + e.getMessage(), e);
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
        } catch (Exception e) {
            LOG.error("nop.ai.agent.message.db-sweep-stale-claimed-error:consumerId={}", consumerId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleConsumerResult(String sid, Object result) {
        if (result instanceof CompletionStage) {
            ((CompletionStage<Object>) result).whenComplete((r, e) -> {
                if (e != null) {
                    LOG.error("nop.ai.agent.message.db-async-consume-error:sid={}", sid, e);
                    releaseClaimQuietly(sid);
                } else {
                    // Plan 271 (finding 14-02): if mark-consume fails here we
                    // are on the async completion thread with no enclosing
                    // try/catch, so fall back to releasing the claim (reset to
                    // PENDING for redelivery). releaseClaimQuietly never
                    // throws; if it also fails the stale-CLAIMED sweep is the
                    // final safety net.
                    try {
                        handleConsumerResult(sid, r);
                    } catch (RuntimeException ex) {
                        LOG.error("nop.ai.agent.message.db-async-mark-consumed-error:sid={}", sid, ex);
                        releaseClaimQuietly(sid);
                    }
                }
            });
        } else if (result instanceof ConsumeLater) {
            releaseClaimQuietly(sid);
        } else {
            markConsumed(sid);
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
        final String messageBody;

        MessageRow(String sid, String messageBody) {
            this.sid = sid;
            this.messageBody = messageBody;
        }
    }

    private class DbSubscription implements IMessageSubscription {
        private final String topic;
        private volatile boolean cancelled = false;
        private volatile boolean suspended = false;

        DbSubscription(String topic) {
            this.topic = topic;
        }

        @Override
        public void cancel() {
            cancelled = true;
            unregisterConsumer(topic);
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

    private class DbConsumeContext implements IMessageConsumeContext {
        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return DBMessageService.this.sendAsync(topic, message, options);
        }
    }
}
