package io.nop.ai.agent.message;

import io.nop.ai.agent.engine.NopAiAgentException;
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

    private final DataSource dataSource;
    private final String consumerId;

    private final Map<String, IMessageConsumer> consumers = new ConcurrentHashMap<>();

    private ScheduledExecutorService poller;
    private volatile boolean started = false;
    private volatile boolean closed = false;

    private long pollIntervalMs = DEFAULT_POLL_INTERVAL_MS;
    private int maxBatch = DEFAULT_MAX_BATCH;

    public DBMessageService(DataSource dataSource) {
        this(dataSource, "db-msg-" + UUID.randomUUID());
    }

    public DBMessageService(DataSource dataSource, String consumerId) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.consumerId = Objects.requireNonNull(consumerId, "consumerId");
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

        String sql = "INSERT INTO " + AiAgentMessageTable.TABLE_NAME
                + " (" + AiAgentMessageTable.COL_SID + ", "
                + AiAgentMessageTable.COL_TOPIC + ", "
                + AiAgentMessageTable.COL_MESSAGE_BODY + ", "
                + AiAgentMessageTable.COL_STATUS + ", "
                + AiAgentMessageTable.COL_CREATED_AT
                + ") VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sid);
            ps.setString(2, topic);
            ps.setString(3, json);
            ps.setInt(4, AiAgentMessageTable.STATUS_PENDING);
            ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
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
        String sql = "SELECT " + AiAgentMessageTable.COL_SID + ", "
                + AiAgentMessageTable.COL_MESSAGE_BODY
                + " FROM " + AiAgentMessageTable.TABLE_NAME
                + " WHERE " + AiAgentMessageTable.COL_TOPIC + " = ?"
                + " AND " + AiAgentMessageTable.COL_STATUS + " = ?"
                + " ORDER BY " + AiAgentMessageTable.COL_CREATED_AT
                + " LIMIT ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, topic);
            ps.setInt(2, AiAgentMessageTable.STATUS_PENDING);
            ps.setInt(3, limit);
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
        String sql = "UPDATE " + AiAgentMessageTable.TABLE_NAME
                + " SET " + AiAgentMessageTable.COL_STATUS + " = ?, "
                + AiAgentMessageTable.COL_CONSUMER_ID + " = ?, "
                + AiAgentMessageTable.COL_CLAIMED_AT + " = ?"
                + " WHERE " + AiAgentMessageTable.COL_SID + " = ?"
                + " AND " + AiAgentMessageTable.COL_STATUS + " = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, AiAgentMessageTable.STATUS_CLAIMED);
            ps.setString(2, consumerId);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.setString(4, sid);
            ps.setInt(5, AiAgentMessageTable.STATUS_PENDING);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new NopAiAgentException("DBMessageService: failed to claim message " + sid, e);
        }
    }

    private void releaseClaim(String sid) {
        String sql = "UPDATE " + AiAgentMessageTable.TABLE_NAME
                + " SET " + AiAgentMessageTable.COL_STATUS + " = ?, "
                + AiAgentMessageTable.COL_CONSUMER_ID + " = NULL, "
                + AiAgentMessageTable.COL_CLAIMED_AT + " = NULL"
                + " WHERE " + AiAgentMessageTable.COL_SID + " = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, AiAgentMessageTable.STATUS_PENDING);
            ps.setString(2, sid);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("nop.ai.agent.message.db-release-claim-error:sid={}", sid, e);
        }
    }

    private void markConsumed(String sid) {
        String sql = "UPDATE " + AiAgentMessageTable.TABLE_NAME
                + " SET " + AiAgentMessageTable.COL_STATUS + " = ?, "
                + AiAgentMessageTable.COL_CONSUMED_AT + " = ?"
                + " WHERE " + AiAgentMessageTable.COL_SID + " = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, AiAgentMessageTable.STATUS_CONSUMED);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setString(3, sid);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("nop.ai.agent.message.db-mark-consumed-error:sid={}", sid, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleConsumerResult(String sid, Object result) {
        if (result instanceof CompletionStage) {
            ((CompletionStage<Object>) result).whenComplete((r, e) -> {
                if (e != null) {
                    LOG.error("nop.ai.agent.message.db-async-consume-error:sid={}", sid, e);
                    releaseClaim(sid);
                } else {
                    handleConsumerResult(sid, r);
                }
            });
        } else if (result instanceof ConsumeLater) {
            releaseClaim(sid);
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
