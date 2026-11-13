/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.kafka;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.connector.ConnectivityCheckable;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * WI3 (plan 2335): native Kafka transactional sink — the committed-write half of the
 * exactly-once connector pair ({@link KafkaSourceFunction} is the offset half).
 *
 * <p><strong>Commit-at-barrier epoch transactions</strong>: {@code kafka-clients} public
 * API cannot resume a transaction across producer restarts (Flink needs producer
 * internals for that), so NEITHER classic recovery policy is fully provable:
 * re-sending a possibly-committed epoch duplicates it; skipping an aborted epoch loses
 * it. This sink fuses the epoch commit into the checkpoint barrier —
 * {@link #saveState(long)} runs <em>inside</em> the barrier (after the flushed sends,
 * before the coordinator's commit bookkeeping) and does
 * {@code commitTransaction + beginTransaction(next) + pendingCommits marker}:
 * <ul>
 *   <li><strong>Zero loss (provable)</strong>: a crash before the barrier aborts the
 *       open transaction on recovery ({@code initTransactions()}) — records stay
 *       invisible and the source replays them from the last checkpoint into a fresh
 *       epoch (the source offset snapshot rides the same, aborted barrier). A crash
 *       after the barrier means the records are already visible.</li>
 *   <li><strong>Zero duplicate — with one bounded residual window</strong>: if the
 *       crash lands after this barrier's {@code commitTransaction} but BEFORE the
 *       checkpoint becomes durable, recovery resumes from the previous checkpoint, the
 *       source replays the epoch, and the sink re-sends it — the records become visible
 *       twice. The window spans one broker commit RPC plus the checkpoint persist
 *       (synchronous persist keeps it at the RPC); it cannot be closed without
 *       transaction resume, so it is pinned as a documented residual, not hidden.</li>
 * </ul>
 * The cost is visibility latency: records become visible at the checkpoint boundary,
 * not at invoke time.
 *
 * <p><strong>Typed no-silent-skip</strong>: {@code commit(epoch)} is a no-op only for
 * epochs this sink has barrier-committed (epoch &le; the committed high-water mark); an
 * epoch beyond that mark fails typed — the coordinator asking to commit a never-snapshotted
 * epoch is a contract violation, not a recoverable state. The high-water mark (rather
 * than the mutable pendingCommits map) is the guard so the base class's empty-pending
 * fallback commit for an already-committed epoch stays a harmless no-op.
 *
 * <p><strong>Transactional identity</strong>: the transactional.id is
 * {@code prefix|jobId|vertexId|taskIndex} (via {@code copyForSubtask(TaskLocation)}),
 * stable across restarts of the same subtask so {@code initTransactions()} fences zombie
 * producer generations of the SAME sink. Two different jobs must not share a prefix.
 */
public class KafkaTwoPhaseCommitSink<T> extends TwoPhaseCommitSinkFunction<T>
        implements ConnectivityCheckable, AutoCloseable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(KafkaTwoPhaseCommitSink.class);

    /**
     * Bounded producer shutdown: {@code close()} with no timeout waits on in-flight
     * sends indefinitely, which would trade the resource leak for a shutdown hang.
     */
    private static final Duration PRODUCER_CLOSE_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Plan 2511 C8-006 (adjudication ⑦): explicit default for the Kafka producer's
     * {@code transaction.timeout.ms}. Equals the broker's default
     * {@code transaction.max.timeout.ms} (900s) so it works against zero broker
     * reconfiguration, and gives 15x headroom over the default checkpoint interval
     * (60s) — the pre-fix shape left the producer default (60s) EQUAL to the
     * checkpoint interval, so under sustained load every epoch transaction aged past
     * its own timeout and the job entered a typed-failure/restart/FAIL loop.
     * Above the broker max the broker rejects at initTransactions() — the owner doc
     * declares that bound; this sink does not probe the broker.
     */
    public static final long DEFAULT_TRANSACTION_TIMEOUT_MS = 900000L;

    /**
     * Deployment config key for the transaction timeout ({@code long} ms). The sink
     * itself is a pure constructor-injected object (no global config reads, same
     * pattern as the Avro registry client): the deployment assembly projects this key
     * into {@code producerProps} under the Kafka-native
     * {@code transaction.timeout.ms} name, which is where this sink resolves it.
     */
    public static final String TRANSACTION_TIMEOUT_CONFIG_KEY =
            "nop.stream.connector.kafka.transaction-timeout-ms";

    /**
     * Safety factor for the constructor-time coupling guard: the transaction must
     * outlive at least this many checkpoint intervals (interval + snapshot duration
     * + scheduling slack). {@code interval × factor > timeout} is rejected.
     */
    public static final int TRANSACTION_TIMEOUT_SAFETY_FACTOR = 3;

    private final String bootstrapServers;
    private final String topic;
    private final String transactionalIdPrefix;
    private final Map<String, Object> producerProps;
    private final Function<T, byte[]> valueSerializer;
    private final Function<T, String> keyExtractor;
    /**
     * C8-006: checkpoint interval of the owning deployment (ms), injected at assembly
     * time and constant for the sink's lifetime; drives the constructor-time coupling
     * guard against the effective {@code transaction.timeout.ms}.
     */
    private final long checkpointIntervalMs;
    /**
     * C8-006: effective {@code transaction.timeout.ms} — the user-explicit
     * {@code producerProps} value when present, otherwise
     * {@link #DEFAULT_TRANSACTION_TIMEOUT_MS}. Validated once at construction.
     */
    private final long transactionTimeoutMs;

    private transient volatile Producer<String, byte[]> producer;
    private transient volatile boolean transactionOpen;
    private transient volatile boolean initDone;

    /**
     * High-water mark of epochs committed at the barrier (transient: after a restart
     * every restored pending epoch predates any new commit, so starting at -1 is sound —
     * restoreFromEpoch re-commit requests for those epochs arrive while their markers
     * are still in pendingCommits... the markers ARE the pre-restart record of
     * committed epochs; see {@link #commit(long)}).
     */
    private transient volatile long committedHighWater = -1L;

    private String transactionalId;

    public KafkaTwoPhaseCommitSink(String bootstrapServers, String topic, String transactionalIdPrefix,
                                   Function<T, byte[]> valueSerializer, Function<T, String> keyExtractor) {
        this(bootstrapServers, topic, transactionalIdPrefix, null, valueSerializer, keyExtractor,
                transactionalIdPrefix, CheckpointConfig.DEFAULT_CHECKPOINT_INTERVAL);
    }

    KafkaTwoPhaseCommitSink(String bootstrapServers, String topic, String transactionalIdPrefix,
                            Map<String, Object> producerProps, Function<T, byte[]> valueSerializer,
                            Function<T, String> keyExtractor, String transactionalId) {
        this(bootstrapServers, topic, transactionalIdPrefix, producerProps, valueSerializer,
                keyExtractor, transactionalId, CheckpointConfig.DEFAULT_CHECKPOINT_INTERVAL);
    }

    KafkaTwoPhaseCommitSink(String bootstrapServers, String topic, String transactionalIdPrefix,
                            Map<String, Object> producerProps, Function<T, byte[]> valueSerializer,
                            Function<T, String> keyExtractor, String transactionalId,
                            long checkpointIntervalMs) {
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "bootstrapServers");
        }
        if (topic == null || topic.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "topic");
        }
        if (transactionalIdPrefix == null || transactionalIdPrefix.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "transactionalIdPrefix");
        }
        if (valueSerializer == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "valueSerializer");
        }
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
        this.transactionalIdPrefix = transactionalIdPrefix;
        this.producerProps = producerProps == null ? java.util.Collections.emptyMap() : new LinkedHashMap<>(producerProps);
        this.valueSerializer = valueSerializer;
        this.keyExtractor = keyExtractor;
        this.transactionalId = transactionalId;
        // C8-006: resolve the effective transaction timeout (user-explicit producerProps
        // value wins; the explicit default makes every producer carry one) and fail fast
        // on the zero-margin coupling instead of entering the typed-failure loop at runtime
        this.checkpointIntervalMs = checkpointIntervalMs;
        Object configuredTimeout = this.producerProps.get(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG);
        long effectiveTimeout;
        if (configuredTimeout == null) {
            effectiveTimeout = DEFAULT_TRANSACTION_TIMEOUT_MS;
        } else if (configuredTimeout instanceof Number n) {
            effectiveTimeout = n.longValue();
        } else {
            try {
                effectiveTimeout = Long.parseLong(String.valueOf(configuredTimeout).trim());
            } catch (NumberFormatException e) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ARG_NAME, ProducerConfig.TRANSACTION_TIMEOUT_CONFIG)
                        .param(ARG_DETAIL, ProducerConfig.TRANSACTION_TIMEOUT_CONFIG + " is not a number: "
                                + configuredTimeout + " (config key " + TRANSACTION_TIMEOUT_CONFIG_KEY + ")");
            }
        }
        if (effectiveTimeout <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, ProducerConfig.TRANSACTION_TIMEOUT_CONFIG)
                    .param(ARG_DETAIL, ProducerConfig.TRANSACTION_TIMEOUT_CONFIG + " must be positive, got: "
                            + effectiveTimeout + " (config key " + TRANSACTION_TIMEOUT_CONFIG_KEY + ")");
        }
        // the Kafka client declares transaction.timeout.ms as a 32-bit INT config: a
        // wider value can never reach the broker client and would blow up INSIDE the
        // first KafkaProducer construction (Plan 2511-b E2E finding), so reject it at
        // assembly instead of at first invoke
        if (effectiveTimeout > Integer.MAX_VALUE) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, ProducerConfig.TRANSACTION_TIMEOUT_CONFIG)
                    .param(ARG_DETAIL, ProducerConfig.TRANSACTION_TIMEOUT_CONFIG
                            + " exceeds the Kafka client's 32-bit INT config bound (Integer.MAX_VALUE), got: "
                            + effectiveTimeout + " (config key " + TRANSACTION_TIMEOUT_CONFIG_KEY + "); the broker's"
                            + " transaction.max.timeout.ms is far below this bound");
        }
        if (checkpointIntervalMs <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "checkpointIntervalMs")
                    .param(ARG_DETAIL, "checkpointIntervalMs must be positive, got: " + checkpointIntervalMs);
        }
        if (checkpointIntervalMs * TRANSACTION_TIMEOUT_SAFETY_FACTOR > effectiveTimeout) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, ProducerConfig.TRANSACTION_TIMEOUT_CONFIG)
                    .param(ARG_DETAIL, "zero-margin transaction configuration rejected: checkpointInterval ("
                            + checkpointIntervalMs + "ms) x " + TRANSACTION_TIMEOUT_SAFETY_FACTOR + " > "
                            + ProducerConfig.TRANSACTION_TIMEOUT_CONFIG + " (" + effectiveTimeout + "ms). "
                            + "The epoch transaction must outlive its own checkpoint interval plus snapshot "
                            + "duration, or sustained load trips the broker's transaction timeout and the job "
                            + "enters a typed-failure/restart loop. Lower the checkpoint interval or raise "
                            + transactionTimeoutConfigHint(effectiveTimeout) + " up to the broker's "
                            + "transaction.max.timeout.ms");
        }
        this.transactionTimeoutMs = effectiveTimeout;
    }

    private static String transactionTimeoutConfigHint(long current) {
        return ProducerConfig.TRANSACTION_TIMEOUT_CONFIG + " (currently " + current + "ms; config key "
                + TRANSACTION_TIMEOUT_CONFIG_KEY + ")";
    }

    /**
     * The effective {@code transaction.timeout.ms} (user-explicit value or the
     * {@link #DEFAULT_TRANSACTION_TIMEOUT_MS} default). Primarily for tests and the
     * deployment assembly.
     */
    public long getTransactionTimeoutMs() {
        return transactionTimeoutMs;
    }

    protected Producer<String, byte[]> newProducer() {
        return new KafkaProducer<>(buildProducerProps());
    }

    /**
     * Builds the producer configuration: base identity/serializer settings, the
     * user-explicit {@code producerProps}, then the validated
     * {@code transaction.timeout.ms} (C8-006 — the explicit default or the user value
     * the constructor validated; never the client-library default that equals the
     * checkpoint interval). Package-visible for the module test's white-box assertion.
     */
    Map<String, Object> buildProducerProps() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionalId);
        props.putAll(producerProps);
        // C8-006: the Kafka client types transaction.timeout.ms as a 32-bit INT — the
        // value must be handed over as an Integer or ConfigDef.parseType rejects the
        // whole producer config (Long fails "Expected value to be a 32-bit integer")
                // C8-006: the Kafka client types transaction.timeout.ms as a 32-bit INT — the
        // value must be handed over as an Integer or ConfigDef rejects the
        // whole producer config (Long fails "Expected value to be a 32-bit integer")
        props.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, (int) transactionTimeoutMs);
        if (!transactionalId.equals(props.get(ProducerConfig.TRANSACTIONAL_ID_CONFIG))) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "producerProps")
                    .param(ARG_DETAIL, "transactional.id must not be overridden in producerProps: the stable "
                            + "identity " + transactionalId + " is what makes recovery fencing work");
        }
        return props;
    }

    private void ensureTransactionOpen() throws Exception {
        if (transactionOpen) {
            return;
        }
        if (producer == null) {
            producer = newProducer();
        }
        if (!initDone) {
            // fences zombie generations of this transactional.id and aborts dangling
            // transactions left by a crashed predecessor — the recovery entry point
            producer.initTransactions();
            initDone = true;
        }
        producer.beginTransaction();
        transactionOpen = true;
    }

    @Override
    public void invoke(T value) throws Exception {
        ensureTransactionOpen();
        byte[] bytes = valueSerializer.apply(value);
        String key = keyExtractor == null ? null : keyExtractor.apply(value);
        producer.send(new ProducerRecord<>(topic, key, bytes));
    }

    @Override
    public void beginTransaction() throws Exception {
        ensureTransactionOpen();
    }

    /**
     * Ensure every sent record is acknowledged by the broker before the barrier state is
     * persisted — the flushed records are still invisible (transaction open).
     */
    @Override
    public void preCommit(long checkpointId) throws Exception {
        if (producer != null) {
            producer.flush();
        }
    }

    /**
     * The epoch commit point, fused into the barrier: commit the open transaction (all
     * flushed records become visible atomically), then open the next epoch's
     * transaction, and only after both succeeded mark the epoch in pendingCommits (so
     * recovery treats it as committed-and-visible).
     */
    @Override
    public TaskStateSnapshot saveState(long epochId) throws Exception {
        if (transactionOpen && producer != null) {
            // a failed commitTransaction leaves transactionOpen=true and the producer in
            // a poisoned state: subsequent barriers fail typed until task-level recovery
            // (recover() → rollback) — loud, never silent, and job failure backstops it
            producer.commitTransaction();
            transactionOpen = false;
            producer.beginTransaction();
            transactionOpen = true;
        }
        // marker written only after the commit succeeded: a failed commitTransaction
        // throws out of saveState and fails the checkpoint, so a marked epoch is always
        // a committed epoch (the invariant commit() and restoreFromEpoch rely on)
        committedHighWater = Math.max(committedHighWater, epochId);
        getPendingCommits().put(epochId, Boolean.TRUE);
        return super.saveState(epochId);
    }

    /**
     * Idempotent for marked (i.e. already committed at the barrier) epochs; typed
     * failure for an epoch this sink never durably snapshotted.
     */
    @Override
    public void commit(long checkpointId) throws Exception {
        if (checkpointId <= committedHighWater) {
            LOG.debug("kafka-2pc: epoch {} already committed at the barrier; commit is a no-op", checkpointId);
            return;
        }
        throw new StreamException(ERR_STREAM_STATE_ERROR)
                .param(ARG_DETAIL, "commit requested for epoch " + checkpointId
                        + " beyond the committed high-water mark " + committedHighWater
                        + " — this sink commits at the checkpoint barrier (saveState); committing a "
                        + "never-snapshotted epoch is a contract violation");
    }

    @Override
    public void rollback() throws Exception {
        if (transactionOpen && producer != null) {
            producer.abortTransaction();
        }
        transactionOpen = false;
    }

    /**
     * Non-durable epochs (never inside a completed checkpoint) had their records in a
     * dangling transaction — {@code initTransactions()} on the next
     * {@code beginTransaction()} aborts it; here we only drop the (absent) bookkeeping.
     */
    @Override
    public void abort(long epochId) throws Exception {
        rollback();
    }

    /**
     * Restores the committed high-water mark from the rebuilt pendingCommits map (every
     * marker is a barrier-committed epoch — see {@link #saveState(long)}) BEFORE the
     * base class re-commits pending epochs, so those re-commit requests hit the
     * idempotent no-op branch instead of the typed contract-violation branch.
     */
    @Override
    public void restoreFromEpoch(long epochId, TaskStateSnapshot state) throws Exception {
        Map<Long, Object> pending = getPendingCommits();
        if (pending != null) {
            synchronized (pending) {
                for (Long eid : pending.keySet()) {
                    committedHighWater = Math.max(committedHighWater, eid);
                }
            }
        }
        super.restoreFromEpoch(epochId, state);
    }

    /**
     * Recovery entry: drop the current (possibly dangling) transaction state. The next
     * {@code beginTransaction()} re-creates the producer and runs
     * {@code initTransactions()}, fencing zombies and aborting dangling transactions.
     */
    @Override
    public void recover(long checkpointId) throws Exception {
        rollback();
        transactionOpen = false;
        beginTransaction();
    }

    /**
     * Task-identity variant: the transactional.id gains the job/vertex/task coordinates
     * so concurrent jobs (and restarted subtasks of the same job) fence by identity
     * instead of colliding on the bare prefix.
     */
    @Override
    public TwoPhaseCommitSinkFunction<T> copyForSubtask(TaskLocation location) {
        KafkaTwoPhaseCommitSink<T> copy = new KafkaTwoPhaseCommitSink<>(bootstrapServers, topic,
                transactionalIdPrefix, producerProps, valueSerializer, keyExtractor,
                transactionalIdPrefix + "|" + location.getJobId() + "|" + location.getVertexId()
                        + "|" + location.getTaskIndex(), checkpointIntervalMs);
        return copy;
    }

    @Override
    public SinkConsistencyCapability getSinkConsistency() {
        return SinkConsistencyCapability.TWO_PHASE_COMMIT;
    }

    /**
     * Pre-submit probe: exercises the transactional producer lifecycle (init/begin/abort)
     * without sending any record. Never leaves a committed transaction behind.
     */
    @Override
    public void checkConnection() throws Exception {
        try (Producer<String, byte[]> probe = newProducer()) {
            probe.initTransactions();
            probe.beginTransaction();
            probe.abortTransaction();
        }
    }

    /**
     * Releases the transactional producer on every task termination path. The runtime
     * teardown chain ({@code StreamSinkOperator.close} → {@code AutoCloseable}
     * userFunction) is the only caller contract; without this hook the producer — one
     * daemon network thread, its broker connection and the buffer pool — leaked on
     * every task stop, including the normal DRAIN path.
     *
     * <p>An open epoch transaction is aborted: after a DRAIN barrier this is the empty
     * next-epoch transaction opened by {@link #saveState(long)} (all real records are
     * already committed-and-visible); on any earlier stop the open transaction's records
     * are invisible anyway and the next {@code initTransactions()} aborts the dangling
     * transaction broker-side. Failure to abort is logged, never allowed to skip
     * {@code producer.close}. Idempotent: a second close is a no-op, and a later
     * {@code beginTransaction()} transparently rebuilds a fresh producer generation.
     */
    @Override
    public void close() throws Exception {
        Producer<String, byte[]> p = this.producer;
        boolean open = this.transactionOpen;
        this.producer = null;
        this.transactionOpen = false;
        this.initDone = false;
        if (p == null) {
            return;
        }
        if (open) {
            try {
                p.abortTransaction();
            } catch (Exception e) {
                LOG.warn("kafka-2pc: abort on close failed for {} — records stay invisible, the broker"
                        + " aborts the dangling transaction on the next initTransactions()",
                        transactionalId, e);
            }
        }
        p.close(PRODUCER_CLOSE_TIMEOUT);
    }

    /** Test/ops visibility. */
    public String getTransactionalId() {
        return transactionalId;
    }

    /** Module-test hook: barrier-commit high-water visibility. */
    long committedHighWaterForTest() {
        return committedHighWater;
    }
}
