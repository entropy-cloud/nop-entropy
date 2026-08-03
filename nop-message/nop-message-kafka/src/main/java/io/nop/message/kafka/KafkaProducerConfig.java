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
 * Kafka producer tuning (mirrors {@code PulsarProducerConfig}). All fields map 1:1 to
 * {@code KafkaProducer} config keys; sensible Kafka defaults apply when unset.
 */
@DataBean
public class KafkaProducerConfig {
    /** {@code acks}: 0 / 1 / all. Default all (at-least-once). */
    private String acks = "all";

    /** {@code retries}. Default 3 (at-least-once, mirrors Pulsar default redelivery). */
    private int retries = 3;

    /** {@code batch.size} in bytes. */
    private int batchSize = 16384;

    /** {@code linger.ms}. */
    private int lingerMs = 1;

    /** {@code buffer.memory} bytes. */
    private long bufferMemory = 33554432L;

    /** {@code max.block.ms} — send back-pressure bound. */
    private long maxBlockMs = 60000L;

    /** Send timeout in ms (applied via producer close timeout). */
    private long sendTimeoutMs = 120000L;

    public String getAcks() {
        return acks;
    }

    public void setAcks(String acks) {
        this.acks = acks;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getLingerMs() {
        return lingerMs;
    }

    public void setLingerMs(int lingerMs) {
        this.lingerMs = lingerMs;
    }

    public long getBufferMemory() {
        return bufferMemory;
    }

    public void setBufferMemory(long bufferMemory) {
        this.bufferMemory = bufferMemory;
    }

    public long getMaxBlockMs() {
        return maxBlockMs;
    }

    public void setMaxBlockMs(long maxBlockMs) {
        this.maxBlockMs = maxBlockMs;
    }

    public long getSendTimeoutMs() {
        return sendTimeoutMs;
    }

    public void setSendTimeoutMs(long sendTimeoutMs) {
        this.sendTimeoutMs = sendTimeoutMs;
    }
}
