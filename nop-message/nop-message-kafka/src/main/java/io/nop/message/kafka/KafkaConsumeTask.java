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
import io.nop.api.core.message.SeekMode;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * <p><strong>Seek policy (not a stub).</strong> Unlike {@code PulsarConsumeTask.seekToPosition}
 * (which is a TODO stub), this task honors {@link SeekMode} by mapping it to
 * {@link Consumer#seekToBeginning} / {@link Consumer#seekToEnd} /
 * {@link Consumer#seek(TopicPartition, long)} on every partition of the subscribed
 * topic, executed once before the poll loop starts. {@code seekToMessage} (offset-by-id)
 * is not supported and throws {@code ERR_KAFKA_SEEK_NOT_SUPPORTED} rather than being
 * silently skipped (plan guide #24 — no silent skip).
 */
public class KafkaConsumeTask {
    static final Logger LOG = LoggerFactory.getLogger(KafkaConsumeTask.class);

    static final long DEFAULT_CONSUME_ERROR_BACKOFF_MS = 1000L;
    private static final long DEFAULT_POLL_TIMEOUT_MS = 1000L;

    private final KafkaMessageService service;
    private final ExecutorService executor;
    final Consumer<String, String> consumer;
    private final MessageSubscriptionConfig config;
    private final long consumeErrorBackoffMs;
    private final long pollTimeoutMs;

    private volatile boolean active = false;

    /**
     * P1-4.4-M01（plan 2396）：期望暂停态——suspend/resume 只置此标志（线程安全），
     * 消费循环每轮应用、rebalance listener 对新分配分区补应用。此前在调用线程直操
     * consumer 且 suspended 与 poll 循环脱钩——rebalance 后 pause 静默失效。
     */
    private volatile boolean desiredPaused = false;

    /** P1-4.4-M03（plan 2396）：INIT 类 seek 仅在首次分区分配时执行。 */
    private volatile boolean initSeekDone = false;

    public KafkaConsumeTask(KafkaMessageService service, ExecutorService executor,
                            Consumer<String, String> consumer,
                            MessageSubscriptionConfig config) {
        this(service, executor, consumer, config,
                DEFAULT_CONSUME_ERROR_BACKOFF_MS, DEFAULT_POLL_TIMEOUT_MS);
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

    public void start() {
        active = true;
        // P1-4.4-M03（plan 2396）：seek 迁移至 ConsumerRebalanceListener.onPartitionsAssigned
        // （doSubscribe 装配）——真实 KafkaConsumer 首次 poll 前 assignment 恒空，启动期直调
        // seekToPosition 全部静默 no-op。
        executor.execute(this::runTask);
    }

    public void setDesiredPaused(boolean paused) {
        this.desiredPaused = paused;
    }

    public boolean isDesiredPaused() {
        return desiredPaused;
    }

    /**
     * P1-4.4-M01（plan 2396）：按期望态对当前分配的分区应用 pause/resume。消费循环每轮
     * 调用（rebalance 后新分区也由此覆盖），成本可忽略（对已暂停分区的重复 pause 幂等）。
     */
    void applyDesiredPause() {
        Collection<TopicPartition> partitions = consumer.assignment();
        if (partitions.isEmpty()) {
            return;
        }
        if (desiredPaused) {
            consumer.pause(partitions);
        } else {
            consumer.resume(partitions);
        }
    }

    /**
     * Honors {@link SeekMode} (NOT a stub). P1-4.4-M03（plan 2396）：由
     * ConsumerRebalanceListener.onPartitionsAssigned 以实际分配的分区驱动——启动期直调
     * 时 assignment 恒空，全部 seek 静默 no-op。ALWAYS_SEEK_TO_END 每次 assigned 执行；
     * INIT_SEEK_TO_BEGIN/INIT_SEEK_TO_END/seekToTime 仅首次 assigned 执行（7.7 裁决②）。
     * seekToMessage (offset-by-message-id) unsupported regardless of mode; fail loud
     * rather than silently skip (plan guide #24).
     */
    void seekOnAssigned(Collection<TopicPartition> partitions) {
        io.nop.api.core.message.MessageSubscribeOptions options = config.getOptions();
        if (options == null) {
            return;
        }

        // seekToMessage (offset-by-message-id) is unsupported regardless of mode; fail
        // loud rather than silently skip (plan guide #24). Checked before the mode
        // early-return so DEFAULT mode still rejects it.
        if (options.getSeekToMessage() != null) {
            throw service.error(KafkaErrors.ERR_KAFKA_SEEK_NOT_SUPPORTED,
                    "seekToMessage is not supported by the Kafka backend");
        }

        SeekMode mode = options.getSeekMode();
        boolean initMode = mode == SeekMode.INIT_SEEK_TO_BEGIN || mode == SeekMode.INIT_SEEK_TO_END;
        if (initMode && initSeekDone) {
            return;
        }

        if (mode == null || mode == SeekMode.DEFAULT) {
            // seekToTime without an explicit mode still applies (INIT semantics).
            if (options.getSeekToTime() > 0) {
                seekByTime(options.getSeekToTime(), partitions);
            }
            initSeekDone = true;
            return;
        }

        switch (mode) {
            case INIT_SEEK_TO_BEGIN:
                consumer.seekToBeginning(partitions);
                initSeekDone = true;
                break;
            case INIT_SEEK_TO_END:
                consumer.seekToEnd(partitions);
                initSeekDone = true;
                break;
            case ALWAYS_SEEK_TO_END:
                consumer.seekToEnd(partitions);
                break;
            default:
                // Future SeekMode values: warn rather than silently skip (plan guide #24).
                LOG.warn("nop.message.kafka.unsupported-seek-mode:mode={}", mode);
                break;
        }

        if (options.getSeekToTime() > 0) {
            seekByTime(options.getSeekToTime(), partitions);
        }
    }

    private void seekByTime(long timestamp, Collection<TopicPartition> partitions) {
        Map<TopicPartition, Long> timestamps = new HashMap<>();
        for (TopicPartition partition : partitions) {
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
                applyDesiredPause();
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
            for (int i = 0; i < responses.size() && i < recordList.size(); i++) {
                Object response = FutureHelper.getResult(responses.get(i));
                ConsumerRecord<String, String> record = recordList.get(i);
                if (response instanceof ConsumeLater) {
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
