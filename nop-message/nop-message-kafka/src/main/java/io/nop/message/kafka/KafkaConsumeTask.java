/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.kafka;

import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.SeekMode;
import io.nop.api.core.message.PartialCommit;
import io.nop.api.core.message.TerminateConsume;
import io.nop.api.core.message.TopicMessage;
import io.nop.api.core.util.FutureHelper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;

/**
 * Kafka poll-loop consume task (mirrors {@code PulsarConsumeTask}). Runs on a dedicated
 * single-thread {@link ExecutorService}: polls the {@link Consumer}, builds an
 * {@link ApiMessage} per {@link ConsumerRecord} via {@link KafkaHelper}, invokes
 * {@link IMessageConsumer#onMessage} and dispatches the 5 documented return-value
 * semantics (null → commit, CompletionStage → await, ConsumeLater → seek no-commit,
 * Acknowledge → ack-topic reply + commit, other non-null → reply + commit).
 *
 * <p><strong>Seek policy (adjudication D3).</strong> Seek origins are resolved inside the
 * consumer group's rebalance callback ({@code onPartitionsAssigned}) for the freshly
 * assigned partitions — never before the poll loop, where the assignment is always empty
 * (MSG-F-01). INIT_SEEK modes apply only when the group has no committed offset for the
 * partition (first-ever consumption; a restart resumes from the committed offset), and
 * ALWAYS_SEEK_TO_END re-applies on every assignment. {@code seekToMessage} (offset-by-id)
 * is not supported and is rejected synchronously at subscribe time with
 * {@code ERR_KAFKA_SEEK_NOT_SUPPORTED} — the rejection reaches the caller instead of being
 * swallowed by the consume thread (MSG-F-02 seek facet).
 */
public class KafkaConsumeTask {
    static final Logger LOG = LoggerFactory.getLogger(KafkaConsumeTask.class);

    static final long DEFAULT_CONSUME_ERROR_BACKOFF_MS = 1000L;
    private static final long DEFAULT_POLL_TIMEOUT_MS = 1000L;

    private final KafkaMessageService service;
    private ExecutorService executor;
    final Consumer<String, String> consumer;
    private final MessageSubscriptionConfig config;
    private final long consumeErrorBackoffMs;
    private long pollTimeoutMs;

    private volatile boolean active = false;
    // Rebalance listener runs only on the poll thread; plain boolean suffices.
    private boolean seekToTimeApplied = false;


    /** WI7: created before the executor exists; bindExecutor completes the wiring. */
    KafkaConsumeTask(KafkaMessageService service, Consumer<String, String> consumer,
                     MessageSubscriptionConfig config, long consumeErrorBackoffMs) {
        this.service = service;
        this.consumer = consumer;
        this.config = config;
        this.consumeErrorBackoffMs = consumeErrorBackoffMs;
        this.pollTimeoutMs = DEFAULT_POLL_TIMEOUT_MS;
    }

    public KafkaConsumeTask(KafkaMessageService service, ExecutorService executor,
                            Consumer<String, String> consumer,
                            MessageSubscriptionConfig config,
                            long consumeErrorBackoffMs, long pollTimeoutMs) {
        this.service = service;
        this.executor = executor;
        this.consumer = consumer;
        this.config = config;
        this.consumeErrorBackoffMs = consumeErrorBackoffMs;
        this.pollTimeoutMs = pollTimeoutMs;
    }

    /**
     * Binds the consume executor and poll timeout when the task is constructed before the
     * executor exists (WI7: the task is created ahead of {@code consumer.subscribe} so the
     * rebalance listener can be registered with the subscription).
     */
    void bindExecutor(ExecutorService executor, long pollTimeoutMs) {
        this.executor = executor;
        this.pollTimeoutMs = pollTimeoutMs;
    }

    /**
     * The consumer-group rebalance listener backing the subscription (WI7/D3): seek origins
     * are resolved per assignment. {@code onPartitionsRevoked} is intentionally empty —
     * offsets are committed synchronously after every processed batch, so there is nothing
     * to do on revocation; it is declared explicitly rather than left as a silent stub.
     */
    org.apache.kafka.clients.consumer.ConsumerRebalanceListener rebalanceListener() {
        return new org.apache.kafka.clients.consumer.ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                // Intentional no-op: commits already happened after each processed batch.
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                KafkaConsumeTask.this.onPartitionsAssigned(partitions);
            }
        };
    }

    public void start() {
        active = true;
        executor.execute(this::runTask);
    }

    /** Test seam: drives the poll loop on the caller thread (WI7 poll-first assertions). */
    void runTaskForTest() {
        active = true;
        runTask();
    }

    /**
     * D3 seek-origin resolution, invoked from the consumer group's rebalance callback for
     * the freshly assigned partitions. Runs on the poll (consume executor) thread only.
     *
     * <p>Gate: INIT_SEEK modes apply only when the consumer group has no committed offset
     * for the partition — a restart must resume from the committed position, not rewind
     * the whole log. ALWAYS_SEEK_TO_END re-applies on every assignment. seekToTime applies
     * once per task instance (replaying it on every rebalance would repeatedly rewind to
     * the same timestamp). A seek failure propagates out of {@code poll()} into the
     * consume loop's backoff handling, and the next assignment retries it.
     */
    void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        if (partitions == null || partitions.isEmpty()) {
            return;
        }
        io.nop.api.core.message.MessageSubscribeOptions options = config.getOptions();

        SeekMode mode = options == null ? null : options.getSeekMode();
        if (mode == SeekMode.ALWAYS_SEEK_TO_END) {
            consumer.seekToEnd(partitions);
        } else if (mode == SeekMode.INIT_SEEK_TO_BEGIN || mode == SeekMode.INIT_SEEK_TO_END) {
            // D3 gate: only partitions the group has NEVER committed — a restart resumes
            // from the committed offset instead of rewinding the whole log.
            Set<TopicPartition> uncommitted = new HashSet<>();
            for (TopicPartition tp : partitions) {
                if (consumer.committed(Collections.singleton(tp)).get(tp) == null) {
                    uncommitted.add(tp);
                }
            }
            if (!uncommitted.isEmpty()) {
                if (mode == SeekMode.INIT_SEEK_TO_BEGIN) {
                    consumer.seekToBeginning(uncommitted);
                } else {
                    consumer.seekToEnd(uncommitted);
                }
            }
        }

        if (options != null && options.getSeekToTime() > 0 && !seekToTimeApplied) {
            seekToTimeApplied = true;
            seekByTime(options.getSeekToTime());
        }
    }

    private void seekByTime(long timestamp) {
        Map<TopicPartition, Long> timestamps = new HashMap<>();
        for (TopicPartition partition : consumer.assignment()) {
            timestamps.put(partition, timestamp);
        }
        Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndTimestamp> offsets =
                consumer.offsetsForTimes(timestamps);
        if (offsets == null) {
            return;
        }
        for (Map.Entry<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndTimestamp> entry : offsets.entrySet()) {
            if (entry.getValue() != null) {
                consumer.seek(entry.getKey(), entry.getValue().offset());
            }
        }
    }

    public void stop() {
        active = false;
        consumer.wakeup();
    }

    public boolean isActive() {
        return active;
    }

    private void runTask() {
        while (active) {
            try {
                if (config.getOptions() != null && config.getOptions().allowBatchConsume()) {
                    batchConsume();
                } else {
                    pollAndConsume();
                }
            } catch (WakeupException we) {
                // stop() woke the consumer — exit gracefully.
                break;
            } catch (Exception e) {
                LOG.error("nop.err.message.kafka.consume-fail", e);
                config.getConsumer().onException(e);
                if (active) {
                    try {
                        Thread.sleep(consumeErrorBackoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    /**
     * Single-record consume path: polls, then for each record invokes the consumer and
     * dispatches the 5 return-value semantics individually (commit per record group).
     */
    void pollAndConsume() throws Exception {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollTimeoutMs));
        if (records == null || records.isEmpty()) {
            return;
        }

        IMessageConsumer consumerFn = config.getConsumer();
        boolean anyConsumeLater = false;

        for (ConsumerRecord<String, String> record : records) {
            ApiMessage apiMessage = KafkaHelper.buildApiMessage(record.topic(), record.partition(),
                    record.offset(), record.timestamp(), record.key(), record.value(), record.headers());

            ConsumeContext context = newConsumeContext();
            try {
                Object response = consumerFn.onMessage(record.topic(), apiMessage, context);
                response = FutureHelper.getResult(response);

                if (response instanceof ConsumeLater) {
                    // Seek back to this record's offset so it is re-delivered; do NOT commit.
                    anyConsumeLater = true;
                    consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());
                    ConsumeLater later = (ConsumeLater) response;
                    LOG.debug("nop.message.kafka.consume-later:topic={},offset={},delayMs={}",
                            record.topic(), record.offset(), later.getDelay());
                } else if (response instanceof TerminateConsume) {
                    // D5(b-prime): poison message is acknowledged (committed), never
                    // redelivered, and never dispatched to the ack topic.
                    LOG.warn("nop.message.kafka.terminate-consume:topic={},offset={},reason={}",
                            record.topic(), record.offset(), ((TerminateConsume) response).getReason());
                } else if (response instanceof PartialCommit) {
                    // Single-record dispatch has no batch boundary; treated as completion
                    // (declared in the WI6 plan). Full batch semantics live in batchConsume.
                } else if (response instanceof Acknowledge) {
                    // Acknowledge: send reply to ack topic, then commit.
                    context.send(service.getAckTopic(record.topic()),
                            ((Acknowledge) response).getReplyMessage());
                } else {
                    if (response != null) {
                        context.send(service.getAckTopic(record.topic()), response);
                    }
                }
                context.commit();
            } catch (Exception e) {
                LOG.error("nop.err.message.kafka.consume-record-fail:topic={},offset={}",
                        record.topic(), record.offset(), e);
                context.rollback();
                // Re-seek to the failed record for at-least-once redelivery.
                consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());
                throw e;
            }
        }

        if (!anyConsumeLater) {
            try {
                consumer.commitSync();
            } catch (Exception e) {
                LOG.error("nop.err.message.kafka.commit-fail", e);
            }
        }
    }

    /**
     * Batch consume path (when {@code batchReceiveCount > 0}): polls, builds a
     * {@link TopicMessage} list, invokes {@link IMessageConsumer#onMessageBatch}, then
     * dispatches per-record return semantics (mirrors PulsarConsumeTask.batchConsume).
     */
    void batchConsume() throws Exception {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollTimeoutMs));
        if (records == null || records.isEmpty()) {
            return;
        }

        List<TopicMessage> list = new ArrayList<>(records.count());
        List<ConsumerRecord<String, String>> recordList = new ArrayList<>(records.count());
        for (ConsumerRecord<String, String> record : records) {
            ApiMessage apiMessage = KafkaHelper.buildApiMessage(record.topic(), record.partition(),
                    record.offset(), record.timestamp(), record.key(), record.value(), record.headers());
            list.add(new TopicMessage(record.topic(), apiMessage));
            recordList.add(record);
        }

        IMessageConsumer consumerFn = config.getConsumer();
        ConsumeContext context = newConsumeContext();
        boolean anyConsumeLater = false;
        try {
            List<Object> responses = consumerFn.onMessageBatch(list, context);
            Map<TopicPartition, Long> partialCommitOffsets = null;
            for (int i = 0; i < responses.size() && i < recordList.size(); i++) {
                Object response = FutureHelper.getResult(responses.get(i));
                ConsumerRecord<String, String> record = recordList.get(i);
                if (response instanceof PartialCommit) {
                    // D5(b-prime): commit boundary — records [0..k-1] only. For each
                    // partition, commit "next offset to read" = last committed + 1; the
                    // record carrying the signal and everything after stays uncommitted.
                    int k = ((PartialCommit) response).getCommittedCount();
                    partialCommitOffsets = new HashMap<>();
                    for (int j = 0; j < k && j < recordList.size(); j++) {
                        ConsumerRecord<String, String> committed = recordList.get(j);
                        partialCommitOffsets.merge(new TopicPartition(committed.topic(), committed.partition()),
                                committed.offset() + 1, Math::max);
                    }
                    // commitSync(Map) expects "next offset to read" per partition.
                    break;
                } else if (response instanceof TerminateConsume) {
                    // D5(b-prime): acknowledged, no redelivery, no reply.
                    LOG.warn("nop.message.kafka.terminate-consume:topic={},offset={},reason={}",
                            record.topic(), record.offset(), ((TerminateConsume) response).getReason());
                } else if (response instanceof ConsumeLater) {
                    anyConsumeLater = true;
                    consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());
                } else if (response instanceof Acknowledge) {
                    context.send(service.getAckTopic(record.topic()),
                            ((Acknowledge) response).getReplyMessage());
                } else if (response != null) {
                    context.send(service.getAckTopic(record.topic()), response);
                }
            }
            context.commit();
            if (partialCommitOffsets != null) {
                Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> toCommit =
                        new HashMap<>();
                for (Map.Entry<TopicPartition, Long> entry : partialCommitOffsets.entrySet()) {
                    toCommit.put(entry.getKey(),
                            new org.apache.kafka.clients.consumer.OffsetAndMetadata(entry.getValue()));
                }
                consumer.commitSync(toCommit);
                return;
            }
        } catch (Exception e) {
            LOG.error("nop.err.message.kafka.batch-consume-fail", e);
            context.rollback();
            throw e;
        }

        if (!anyConsumeLater) {
            try {
                consumer.commitSync();
            } catch (Exception e) {
                LOG.error("nop.err.message.kafka.commit-fail", e);
            }
        }
    }

    ConsumeContext newConsumeContext() {
        return new ConsumeContext();
    }

    /**
     * {@link IMessageConsumeContext} implementation. Kafka has no native transactions in
     * the at-least-once first version (Non-Goal), so commit/rollback are no-ops and the
     * context only forwards sends through the enclosing {@link KafkaMessageService}
     * producer.
     */
    protected class ConsumeContext implements IMessageConsumeContext {
        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return service.sendAsync(topic, message, options);
        }

        public void commit() {
            // Per-record semantics handled by the enclosing task; nothing to do here.
        }

        public void rollback() {
            // Per-record semantics handled by the enclosing task; nothing to do here.
        }
    }
}
