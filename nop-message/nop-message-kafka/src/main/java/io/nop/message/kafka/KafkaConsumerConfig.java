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

    public int getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(int heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }
}
