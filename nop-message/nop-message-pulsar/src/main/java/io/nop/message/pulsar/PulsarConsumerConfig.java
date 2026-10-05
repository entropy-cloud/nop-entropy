/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.pulsar;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class PulsarConsumerConfig {
    private int ackTimeout;
    private int negativeAckRedeliveryDelay;
    private int maxTotalReceiverQueueSizeAcrossPartitions;

    /**
     * WI21 (D6 layer two): max redeliveries before the client diverts a message to the
     * dead-letter topic. {@code 0} (default) = dead-letter policy disabled. Pulsar 2.8
     * semantics (verified against the 2.8.0 client): the redelivery count is tracked on the
     * receive side and the diversion happens when the message is finally ACKED — a poison
     * message that is only ever negatively acknowledged stays in the redelivery loop, so
     * the DLQ takes effect together with the retry-limit/terminal-ack semantics (WI22).
     * Also note: only Shared/Key_Shared subscriptions carry a redelivery count in 2.8 —
     * enabling this with another subscription type logs a one-time warning and has no
     * effect. Real-broker diversion behavior is verified by the WI33 gated E2E.
     */
    private int deadLetterMaxRedeliverCount;

    /** WI21: explicit dead-letter topic; {@code null} lets the client derive {@code <topic>-<sub>-DLQ}. */
    private String deadLetterTopic;

    /**
     * WI22: base backoff for the consume error loop (first error sleeps this long; see
     * {@code PulsarConsumeTask#computeBackoffMs} for the escalating form).
     */
    private long consumeErrorBackoffMs = 1000L;

    /** WI22: upper bound of the escalating consume error backoff. */
    private long consumeErrorBackoffMaxMs = 30000L;

    /**
     * WI22: retry budget per message before the failure endpoint fires (the endpoint
     * triggers on failure number {@code maxConsumeAttempts + 1} — this counts RETRIES).
     * On Pulsar the effective attempt is max(local counter, broker redeliveryCount), so
     * for Shared/Key_Shared subscriptions the budget holds across restarts. When the
     * endpoint fires the message is ACKED permanently — the native
     * {@code deadLetterMaxRedeliverCount} policy (if configured) diverts it to the DLQ at
     * that ack; without the policy the message is dropped with a loud WARN. Set {@code 0}
     * for unlimited negative-ack redelivery (the pre-WI22 behavior).
     */
    private int maxConsumeAttempts = 10;

    public int getAckTimeout() {
        return ackTimeout;
    }

    public void setAckTimeout(int ackTimeout) {
        this.ackTimeout = ackTimeout;
    }

    public int getNegativeAckRedeliveryDelay() {
        return negativeAckRedeliveryDelay;
    }

    public void setNegativeAckRedeliveryDelay(int negativeAckRedeliveryDelay) {
        this.negativeAckRedeliveryDelay = negativeAckRedeliveryDelay;
    }

    public int getMaxTotalReceiverQueueSizeAcrossPartitions() {
        return maxTotalReceiverQueueSizeAcrossPartitions;
    }

    public void setMaxTotalReceiverQueueSizeAcrossPartitions(int maxTotalReceiverQueueSizeAcrossPartitions) {
        this.maxTotalReceiverQueueSizeAcrossPartitions = maxTotalReceiverQueueSizeAcrossPartitions;
    }

    public int getDeadLetterMaxRedeliverCount() {
        return deadLetterMaxRedeliverCount;
    }

    public void setDeadLetterMaxRedeliverCount(int deadLetterMaxRedeliverCount) {
        this.deadLetterMaxRedeliverCount = deadLetterMaxRedeliverCount;
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
}
