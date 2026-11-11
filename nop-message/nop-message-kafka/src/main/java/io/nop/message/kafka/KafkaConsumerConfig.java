/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.kafka;

import io.nop.api.core.annotations.data.DataBean;

/**
 * Kafka consumer tuning (mirrors {@code PulsarConsumerConfig}). {@code groupId} is the
 * Kafka consumer-group id (validated non-empty in {@code KafkaMessageService.doSubscribe} when
 * subscription is intended). Other fields map to standard {@code KafkaConsumer} config
 * keys.
 */
@DataBean
public class KafkaConsumerConfig {
    /** {@code group.id}. Required for subscription. */
    private String groupId;

    /**
     * {@code auto.offset.reset}: earliest / latest / none. Default earliest so a fresh
     * group consumes from the head of the topic (matches INIT_SEEK_TO_BEGIN semantics).
     */
    private String autoOffsetReset = "earliest";

    /** {@code enable.auto.commit}. Disabled — {@code KafkaConsumeTask} commits manually. */
    private boolean enableAutoCommit = false;

    /** {@code max.poll.records}. */
    private int maxPollRecords = 500;

    /** {@code fetch.min.bytes}. */
    private int fetchMinBytes = 1;

    /** {@code fetch.max.wait.ms}. */
    private int fetchMaxWaitMs = 500;

    /** {@code session.timeout.ms}. */
    private int sessionTimeoutMs = 45000;

    /** {@code heartbeat.interval.ms}. */
    private int heartbeatIntervalMs = 3000;

    /**
     * {@code max.poll.interval.ms}. Must exceed the worst-case processing time of one
     * polled batch (bounded by {@code max.poll.records} and the handler itself, plus any
     * ConsumeLater defer sleep — see {@code KafkaConsumeTask.deferAfterBatch}). If the
     * interval is exceeded the group rebalances (affecting all members) and the next
     * commit fails — at-least-once redelivery, never loss.
     */
    private int maxPollIntervalMs = 300000;

    /**
     * WI21 (D6 layer two): optional application-level dead-letter topic. When set, a
     * {@code TerminateConsume} failure endpoint republishes the message here (synchronously,
     * before the commit) instead of dropping it after the WARN. {@code null} (default)
     * keeps the drop-and-prove-by-WARN semantics. Note: each per-record sync DLQ publish
     * counts against the {@link #maxPollIntervalMs} budget.
     */
    private String deadLetterTopic;

    /**
     * WI22: base backoff for the consume error loop (first error sleeps this long; see
     * {@code KafkaConsumeTask#computeBackoffMs} for the escalating form).
     */
    private long consumeErrorBackoffMs = 1000L;

    /** WI22: upper bound of the escalating consume error backoff. */
    private long consumeErrorBackoffMaxMs = 30000L;

    /**
     * WI22: retry budget per message location before the failure endpoint fires (the
     * endpoint triggers on failure number {@code maxConsumeAttempts + 1} — this counts
     * RETRIES). The endpoint republishes to the {@link #deadLetterTopic} when configured,
     * otherwise DROPS the message with a loud WARN: a poison message no longer spins
     * forever, but a persistent outage longer than the retry budget loses uncommitted
     * messages by default (the spring-kafka DefaultErrorHandler "bounded retries then skip"
     * trade-off). Set {@code 0} for unlimited redelivery (the pre-WI22 behavior, zero
     * loss). The counter is per consume-task instance and resets on restart; on Pulsar the
     * broker redelivery count augments it (see backend-semantics-design.md 3.3.2).
     */
    private int maxConsumeAttempts = 10;

    /**
     * 4.1-MA-01（plan 2558 裁定 A）：bounded wait on the handler-returned future at the
     * consume await points ({@code KafkaConsumeTask} single/batch paths). A handler whose
     * future never completes no longer wedges the poll thread past
     * {@code max.poll.interval.ms} invisibly: on timeout the record is seeked back for
     * redelivery with a WARN — a dedicated branch that does NOT participate in the
     * {@link #maxConsumeAttempts} poison budget (a hung handler redelivers indefinitely;
     * that observable face is explicitly accepted — DLQ budget is reserved for real
     * handler failures). The wait object must be a {@code CompletableFuture}; any other
     * {@code CompletionStage} form is a typed error
     * ({@code ERR_KAFKA_CONSUME_AWAIT_NOT_ASYNC}), never a silent unbounded wait.
     *
     * <p>Default reference value: keep well below {@link #maxPollIntervalMs} (default
     * 300000) so one timed-out wait + redelivery seek still fits the poll-interval budget;
     * tune the three parameters together — see {@code max.block.ms} on the producer side
     * (MA-07 三参数联动 declaration, {@code KafkaProducerConfig.maxBlockMs}).
     */
    private long consumeAwaitTimeoutMs = 60000L;

    // 4.1-C8-005（plan 2558 裁定 K）：ConsumeLater 预算闭环 —— delay 下限与连续 defer 预算。
    /**
     * Lower bound applied to a handler-requested {@code ConsumeLater} delay (previously
     * {@code max(delayMs, 0)}): a zero/negative requested delay would spin the poll loop
     * on the same record with no broker breathing room. Default 1000ms.
     */
    private long consumeLaterMinDelayMs = 1000L;

    /**
     * Budget of consecutive {@code ConsumeLater} rounds in one consume task. Over budget:
     * loud WARN and the redelivery loop exits back to the poll main path (the delay sleep
     * is skipped) so a handler that defers forever cannot hold the partition in a private
     * sleep loop. The counter resets on any round that finalizes a record without
     * deferring.
     */
    private int consumeLaterMaxDefers = 10;

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getAutoOffsetReset() {
        return autoOffsetReset;
    }

    public void setAutoOffsetReset(String autoOffsetReset) {
        this.autoOffsetReset = autoOffsetReset;
    }

    public boolean isEnableAutoCommit() {
        return enableAutoCommit;
    }

    public void setEnableAutoCommit(boolean enableAutoCommit) {
        this.enableAutoCommit = enableAutoCommit;
    }

    public int getMaxPollRecords() {
        return maxPollRecords;
    }

    public void setMaxPollRecords(int maxPollRecords) {
        this.maxPollRecords = maxPollRecords;
    }

    public int getFetchMinBytes() {
        return fetchMinBytes;
    }

    public void setFetchMinBytes(int fetchMinBytes) {
        this.fetchMinBytes = fetchMinBytes;
    }

    public int getFetchMaxWaitMs() {
        return fetchMaxWaitMs;
    }

    public void setFetchMaxWaitMs(int fetchMaxWaitMs) {
        this.fetchMaxWaitMs = fetchMaxWaitMs;
    }

    public int getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    public void setSessionTimeoutMs(int sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
    }

    public int getMaxPollIntervalMs() {
        return maxPollIntervalMs;
    }

    public void setMaxPollIntervalMs(int maxPollIntervalMs) {
        this.maxPollIntervalMs = maxPollIntervalMs;
    }

    public String getDeadLetterTopic() {
        return deadLetterTopic;
    }

    public void setDeadLetterTopic(String deadLetterTopic) {
        this.deadLetterTopic = deadLetterTopic;
    }

    public long getConsumeErrorBackoffMs() {
        return consumeErrorBackoffMs;
    }

    public void setConsumeErrorBackoffMs(long consumeErrorBackoffMs) {
        this.consumeErrorBackoffMs = consumeErrorBackoffMs;
    }

    public long getConsumeErrorBackoffMaxMs() {
        return consumeErrorBackoffMaxMs;
    }

    public void setConsumeErrorBackoffMaxMs(long consumeErrorBackoffMaxMs) {
        this.consumeErrorBackoffMaxMs = consumeErrorBackoffMaxMs;
    }

    public int getMaxConsumeAttempts() {
        return maxConsumeAttempts;
    }

    public void setMaxConsumeAttempts(int maxConsumeAttempts) {
        this.maxConsumeAttempts = maxConsumeAttempts;
    }

    public int getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(int heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public long getConsumeAwaitTimeoutMs() {
        return consumeAwaitTimeoutMs;
    }

    public void setConsumeAwaitTimeoutMs(long consumeAwaitTimeoutMs) {
        this.consumeAwaitTimeoutMs = consumeAwaitTimeoutMs;
    }

    public long getConsumeLaterMinDelayMs() {
        return consumeLaterMinDelayMs;
    }

    public void setConsumeLaterMinDelayMs(long consumeLaterMinDelayMs) {
        this.consumeLaterMinDelayMs = consumeLaterMinDelayMs;
    }

    public int getConsumeLaterMaxDefers() {
        return consumeLaterMaxDefers;
    }

    public void setConsumeLaterMaxDefers(int consumeLaterMaxDefers) {
        this.consumeLaterMaxDefers = consumeLaterMaxDefers;
    }
}
