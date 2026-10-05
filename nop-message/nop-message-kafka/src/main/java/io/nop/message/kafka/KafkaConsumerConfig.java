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
 * Kafka consumer-group id (validated non-empty in {@code KafkaMessageService.init} when
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
}
