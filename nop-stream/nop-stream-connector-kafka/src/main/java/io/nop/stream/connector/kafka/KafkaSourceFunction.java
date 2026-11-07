/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.source.CheckpointedSourceFunction;
import io.nop.stream.core.connector.DrainableSource;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.functions.source.SubtaskShardedSourceFunction;
import io.nop.stream.core.connector.ConnectivityCheckable;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * WI3 (plan 2335): native Kafka source whose offsets live in the stream checkpoint —
 * the REPLAYABLE half of the exactly-once connector pair (the other half is
 * {@link KafkaTwoPhaseCommitSink}).
 *
 * <p><strong>Offset checkpointing (self-managed, Debezium pattern)</strong>: the
 * consumed position is a per-partition offset map, so the single-long
 * {@code ReplayableSourceFunction} cursor does not apply. The map is written to the
 * operator state under {@link #KAFKA_OFFSETS_KEY} in {@code snapshotState} and read
 * back in {@code initializeState}; on restore every assigned partition is seeked to its
 * checkpointed offset before the first poll. Consumer auto-commit is force-disabled —
 * the checkpoint, not the broker group offset, is the single recovery source.
 *
 * <p><strong>Sharding</strong>: topic partitions are distributed round-robin over the
 * deployment parallelism via {@code copyForSubtask(int)} — disjoint exclusive shards,
 * no consumer-group coordination, no rebalance. A shard may be empty when partitions
 * &lt; parallelism; an empty shard emits nothing and checkpoints an empty offset map
 * (documented idle, not a silent skip). Recovery is deterministic: the assignment is a
 * pure function of (partition list, parallelism, subtaskIndex), so a restored offset
 * map always belongs to the same shard.
 *
 * <p><strong>Cold start</strong>: a job without a checkpoint starts from
 * {@code startFrom} = {@code earliest} (default, deterministic replays) or
 * {@code latest}. Restored jobs always resume from the checkpointed offsets.
 *
 * <p>Record values are deserialized by an injected function (default UTF-8 String).
 * Records are emitted with {@code collectWithTimestamp(record.timestamp(), ...)} so
 * downstream event-time windows see the broker timestamp.
 */
public class KafkaSourceFunction<T> implements SubtaskShardedSourceFunction<T>,
        CheckpointedSourceFunction<T>, DrainableSource<T>, ConnectivityCheckable {

    private static final long serialVersionUID = 1L;

    /** Operator-state key for the partition→offset map (JSON-safe String keys). */
    public static final String KAFKA_OFFSETS_KEY = "kafka-offsets";

    public static final String START_EARLIEST = "earliest";
    public static final String START_LATEST = "latest";

    private final String bootstrapServers;
    private final String topic;
    private final Map<String, Object> consumerProps;
    private final Function<byte[], T> valueDeserializer;
    private final String startFrom;

    private volatile boolean running = true;

    /** Optional poll-batch rate limit (WI5 SourceRateLimiter consumption; 0/0 = off). */
    private volatile long rateLimitRowsPerSecond;
    private volatile long rateLimitBytesPerSecond;

    /**
     * Drain mode ({@link DrainableSource#truncateForDrain()}): stop after the in-flight
     * poll batch is emitted, so the source task reaches a natural terminal state and the
     * job finishes (bounded-replay shapes and E2E harnesses use this; the default
     * connector mode is unbounded).
     */
    private volatile boolean drainRequested;

    /**
     * Deployment parallelism, recorded on the template instance by
     * {@link #validateParallelism(int)} (Debezium pattern) so the plan builders'
     * {@code copyForSubtask(int)} creates correctly-routed shards. A template instance
     * never runs directly: the builders always copy per subtask.
     */
    private int totalParallelism = 1;
    private final int subtaskIndex;

    /**
     * CRA 8.44 R4-04 W-01 (C3-005; adjudication A.1): the consumer held by the current
     * {@code run()}, published after creation and cleared in the run's finally. Volatile:
     * single writer (the run thread) + cross-thread reader ({@link #cancel()}) — a stale
     * or null read is harmless by design (wakeup on a closed consumer is a no-op; a null
     * read means the armed self-protection in run() covers the early-cancel window).
     * KafkaConsumer does not respond to thread interruption, so wakeup() is the ONLY
     * cancellation primitive that can break a blocking {@code partitionsFor}/{@code poll}.
     */
    private volatile KafkaConsumer<String, byte[]> consumer;

    /** Partition -> next offset to consume; String keys survive the JSON checkpoint round-trip. */
    private volatile TreeMap<Integer, Long> restoredOffsets;

    public KafkaSourceFunction(String bootstrapServers, String topic, Function<byte[], T> valueDeserializer) {
        this(bootstrapServers, topic, null, valueDeserializer, START_EARLIEST, 1, 0);
    }

    public KafkaSourceFunction(String bootstrapServers, String topic, Map<String, Object> consumerProps,
                               Function<byte[], T> valueDeserializer, String startFrom,
                               int totalParallelism, int subtaskIndex) {
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "bootstrapServers");
        }
        if (topic == null || topic.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "topic");
        }
        if (valueDeserializer == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "valueDeserializer");
        }
        if (!START_EARLIEST.equals(startFrom) && !START_LATEST.equals(startFrom)) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "startFrom")
                    .param(ARG_DETAIL, "must be 'earliest' or 'latest', got: " + startFrom);
        }
        if (totalParallelism < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "totalParallelism")
                    .param(ARG_DETAIL, "must be >= 1, got: " + totalParallelism);
        }
        if (subtaskIndex < 0 || subtaskIndex >= totalParallelism) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "subtaskIndex")
                    .param(ARG_DETAIL, "must be in [0, " + totalParallelism + "), got: " + subtaskIndex);
        }
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
        this.consumerProps = consumerProps == null ? Collections.emptyMap() : new LinkedHashMap<>(consumerProps);
        this.valueDeserializer = valueDeserializer;
        this.startFrom = startFrom;
        this.totalParallelism = totalParallelism;
        this.subtaskIndex = subtaskIndex;
    }

    @Override
    public SubtaskShardedSourceFunction<T> copyForSubtask(int subtaskIndex) {
        if (subtaskIndex < 0 || subtaskIndex >= totalParallelism) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "subtaskIndex")
                    .param(ARG_DETAIL, "must be in [0, " + totalParallelism
                            + ") for the recorded parallelism, got: " + subtaskIndex);
        }
        KafkaSourceFunction<T> copy = new KafkaSourceFunction<>(bootstrapServers, topic, consumerProps,
                valueDeserializer, startFrom, totalParallelism, subtaskIndex);
        copy.rateLimitRowsPerSecond = this.rateLimitRowsPerSecond;
        copy.rateLimitBytesPerSecond = this.rateLimitBytesPerSecond;
        return copy;
    }

    /**
     * Optional poll-batch rate limit (WI5 plan 2330 deferred item, landed here): rows
     * and/or bytes per second; 0 = unlimited. Negative values fail typed. The limit
     * throttles the poll loop (rate limiting IS backpressure) and rides the shard
     * copies.
     */
    public KafkaSourceFunction<T> withRateLimit(long rowsPerSecond, long bytesPerSecond) {
        if (rowsPerSecond < 0 || bytesPerSecond < 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "rateLimit")
                    .param(ARG_DETAIL, "rate limits must not be negative, got rows=" + rowsPerSecond
                            + ", bytes=" + bytesPerSecond);
        }
        this.rateLimitRowsPerSecond = rowsPerSecond;
        this.rateLimitBytesPerSecond = bytesPerSecond;
        return this;
    }

    /**
     * Records the deployment parallelism on this template instance (the shard routing
     * in {@link #copyForSubtask(int)} needs it). Any parallelism is valid: partitions
     * are distributed round-robin and a shard may be empty (an empty shard emits
     * nothing and snapshots an empty offset map — no partitions assigned to it, which
     * is distinct from the watermark-idle mechanism).
     */
    @Override
    public void validateParallelism(int parallelism) {
        if (parallelism < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "parallelism")
                    .param(ARG_DETAIL, "must be >= 1, got: " + parallelism);
        }
        this.totalParallelism = parallelism;
    }

    /**
     * Round-robin shard of the topic's partitions for this subtask. Pure function of
     * (partition list, parallelism, subtaskIndex) — the property that makes offset-map
     * restore deterministic across restarts.
     */
    List<TopicPartition> shardOf(List<PartitionInfo> partitions) {
        List<TopicPartition> shard = new ArrayList<>();
        for (PartitionInfo info : partitions) {
            // route by partition id, not list position: metadata ordering is not a
            // documented contract, and the shard mapping must be order-independent
            if (info.partition() % totalParallelism == subtaskIndex) {
                shard.add(new TopicPartition(info.topic(), info.partition()));
            }
        }
        return shard;
    }

    /**
     * CRA 8.44 R4-04 W-01 (C3-005; adjudication A.1 decision 5): consumer creation seam —
     * protected so the red① probe subclass can inject a no-network consumer (same family
     * as {@code DebeziumCdcSourceFunction.createMessageSource} and the existing
     * package-private {@code *ForTest} hooks). The default delegates to the unchanged
     * private {@link #newConsumer()} factory (auto-commit typed guard etc. untouched).
     */
    protected KafkaConsumer<String, byte[]> createConsumer() {
        return newConsumer();
    }

    private KafkaConsumer<String, byte[]> newConsumer() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        // manual partition assignment: no group coordination, no auto-commit —
        // the stream checkpoint is the only offset authority
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "nop-stream-kafka-source");
        props.putAll(consumerProps);
        // re-check AFTER user props: user config must not re-enable silent offset paths
        if (Boolean.TRUE.equals(props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG))) {
            throw new StreamException(ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH)
                    .param(ARG_DETAIL, "enable.auto.commit must not be true: the checkpointed offset map, "
                            + "not the broker group offset, is the recovery source");
        }
        return new KafkaConsumer<>(props);
    }

    @Override
    public void run(SourceContext<T> ctx) throws Exception {
        this.running = true;
        // W-01 (CRA 8.44 R4-04; adjudication A.1 decision 1): two-step resource binding —
        // the consumer is published on the field BEFORE the try-with-resources takes
        // ownership, so cancel() can reach it; the resource is still TWR-closed.
        KafkaConsumer<String, byte[]> consumer = createConsumer();
        this.consumer = consumer;
        try (consumer) {
            if (!running) {
                // W-01 armed self-protection (adjudication A.1 decision 3): a cancel that
                // landed while the consumer was being created had no target for its
                // wakeup — arm the first blocking call instead of blocking up to
                // max.block.ms.
                consumer.wakeup();
            }
            List<PartitionInfo> partitions = consumer.partitionsFor(topic);
            if (partitions == null || partitions.isEmpty()) {
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_DETAIL, "topic " + topic + " has no partitions (topic missing or unreachable)");
            }
            List<TopicPartition> shard = shardOf(partitions);
            consumer.assign(shard);

            TreeMap<Integer, Long> offsets = restoredOffsets;
            if (offsets != null) {
                seekRestored(consumer, shard, offsets);
            } else if (START_EARLIEST.equals(startFrom)) {
                consumer.seekToBeginning(shard);
            } else {
                consumer.seekToEnd(shard);
            }

            io.nop.stream.core.source.SourceRateLimiter rateLimiter = null;
            if (rateLimitRowsPerSecond > 0 || rateLimitBytesPerSecond > 0) {
                rateLimiter = new io.nop.stream.core.source.SourceRateLimiter(
                        rateLimitRowsPerSecond, rateLimitBytesPerSecond);
            }
            while (running) {
                var records = consumer.poll(Duration.ofMillis(200L));
                if (rateLimiter != null && !records.isEmpty()) {
                    long bytes = 0L;
                    for (ConsumerRecord<String, byte[]> record : records) {
                        bytes += record.serializedValueSize() + record.serializedKeySize();
                    }
                    // rate limiting IS backpressure: bounded-slice sleep, interruptible
                    rateLimiter.acquire(records.count(), bytes);
                }
                for (ConsumerRecord<String, byte[]> record : records) {
                    if (!running) {
                        return;
                    }
                    T value = valueDeserializer.apply(record.value());
                    ctx.collectWithTimestamp(value, record.timestamp());
                    markConsumed(record.partition(), record.offset());
                }
                if (drainRequested) {
                    // in-flight batch fully emitted: reach terminal state so the job
                    // finishes naturally (DrainableSource contract)
                    return;
                }
            }
        } catch (WakeupException e) {
            if (running) {
                // W-01 (adjudication A.1 decision 4): an unexpected wakeup while the
                // source is still running surfaces typed — never silently swallowed.
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_DETAIL, "unexpected Kafka consumer wakeup while the source "
                                + "is still running (no cancel in flight)");
            }
            // cancel-initiated wakeup: graceful stop (cancellation = stop, not failure)
        } finally {
            // the consumer is closed by the try-with-resources; drop the published
            // reference so a late cancel() wakeup hits a null (harmless no-op) instead
            // of a closed consumer
            this.consumer = null;
        }
    }

    @Override
    public void truncateForDrain() {
        this.drainRequested = true;
        this.running = true;
    }

    private void seekRestored(KafkaConsumer<String, byte[]> consumer, List<TopicPartition> shard,
                              TreeMap<Integer, Long> offsets) {
        for (TopicPartition tp : shard) {
            Long offset = offsets.get(tp.partition());
            if (offset != null) {
                consumer.seek(tp, offset);
            } else {
                // partition has no checkpointed position (new partition or previously
                // empty shard growth): fall back to the declared cold-start position
                if (START_EARLIEST.equals(startFrom)) {
                    consumer.seekToBeginning(Collections.singletonList(tp));
                } else {
                    consumer.seekToEnd(Collections.singletonList(tp));
                }
            }
        }
    }

    private void markConsumed(int partition, long offset) {
        if (restoredOffsets == null) {
            restoredOffsets = new TreeMap<>();
        }
        restoredOffsets.put(partition, offset + 1L);
    }

    @Override
    public void cancel() {
        running = false;
        // W-01 (CRA 8.44 R4-04; adjudication A.1 decision 2): single-shot wakeup on the
        // held consumer — KafkaConsumer ignores thread interruption, so wakeup() is the
        // only primitive that breaks a blocking partitionsFor/poll immediately (bounded
        // by wakeup propagation) instead of max.block.ms (default 60s, user-enlargeable).
        // A null/stale read is benign: the armed self-protection in run() covers the
        // early-cancel window, and wakeup on a closed consumer is a no-op.
        KafkaConsumer<String, byte[]> held = this.consumer;
        if (held != null) {
            held.wakeup();
        }
    }

    @Override
    public SourceConsistencyCapability getSourceConsistency() {
        return SourceConsistencyCapability.REPLAYABLE;
    }

    @Override
    public OperatorSnapshotResult snapshotState(long checkpointId) {
        OperatorSnapshotResult result = new OperatorSnapshotResult();
        result.setCheckpointId(checkpointId);
        TreeMap<Integer, Long> offsets = restoredOffsets == null ? new TreeMap<>() : restoredOffsets;
        Map<String, Long> jsonSafe = new TreeMap<>();
        for (Map.Entry<Integer, Long> e : offsets.entrySet()) {
            jsonSafe.put(String.valueOf(e.getKey()), e.getValue());
        }
        result.putOperatorState(KAFKA_OFFSETS_KEY, jsonSafe);
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void initializeState(TaskStateSnapshot state) {
        if (state == null || state.getOperatorState(KAFKA_OFFSETS_KEY) == null) {
            // cold start (no checkpoint): startFrom decides; keep restoredOffsets null
            return;
        }
        Object raw = state.getOperatorState(KAFKA_OFFSETS_KEY);
        if (!(raw instanceof Map)) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "checkpointed state under key " + KAFKA_OFFSETS_KEY
                            + " is not a map, got: " + (raw == null ? "null" : raw.getClass().getName()));
        }
        TreeMap<Integer, Long> offsets = new TreeMap<>();
        for (Map.Entry<?, ?> e : ((Map<Object, Object>) raw).entrySet()) {
            offsets.put(Integer.parseInt(String.valueOf(e.getKey())),
                    ((Number) e.getValue()).longValue());
        }
        this.restoredOffsets = offsets;
    }

    /**
     * Pre-submit connectivity probe: resolves the topic's partition metadata without
     * consuming anything. Missing topic fails typed.
     */
    @Override
    public void checkConnection() throws Exception {
        try (KafkaConsumer<String, byte[]> consumer = newConsumer()) {
            Collection<PartitionInfo> partitions = consumer.partitionsFor(topic);
            if (partitions == null || partitions.isEmpty()) {
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_DETAIL, "connectivity probe: topic " + topic
                                + " does not exist or metadata is unreachable");
            }
        }
    }

    /** Test/ops visibility: the currently consumed position (partition -> next offset). */
    public Map<Integer, Long> currentOffsets() {
        TreeMap<Integer, Long> offsets = restoredOffsets;
        return offsets == null ? Collections.emptyMap() : new TreeMap<>(offsets);
    }

    /** Default value deserializer: UTF-8 String. */
    public static Function<byte[], String> utf8String() {
        return bytes -> new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ==================== module-test hooks (package-private) ====================

    /** Test hook: the consumer-props guard without touching a broker. */
    void checkConnectionQuietlyForTest() {
        newConsumer();
    }

    /** Test hook: seed the restored position as if initializeState had run. */
    void restoreForTest(TreeMap<Integer, Long> offsets) {
        this.restoredOffsets = offsets;
    }

    /** Test hook: drain flag visibility without a broker round-trip. */
    boolean isDrainRequestedForTest() {
        return drainRequested;
    }

    /** Test hook: rate-limit wiring visibility. */
    long[] rateLimitForTest() {
        return new long[]{rateLimitRowsPerSecond, rateLimitBytesPerSecond};
    }
}
