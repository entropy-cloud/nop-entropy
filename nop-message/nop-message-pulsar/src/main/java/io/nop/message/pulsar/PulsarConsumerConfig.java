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

    /**
     * WI21 (D6 layer two): max redeliveries before the client diverts a message to the
     * dead-letter topic. {@code 0} (default) = dead-letter policy disabled. Pulsar 2.8
     * semantics <em>as verified by the WI33 real-broker E2E (F-DLQ, superseding the WI21
     * static adjudication)</em>: a message received at {@code redeliveryCount >=
     * maxRedeliverCount} is registered client-side, and the diversion fires when that
     * message is NEGATIVELY acknowledged — the client intercepts the redelivery request,
     * publishes the DLQ copy, and acks the original itself. A plain ack never diverts, so
     * the WI22 attempt-cap failure endpoint hands exhausted messages to a negative ack
     * when this policy is configured ( Shared/Key_Shared subscriptions only carry the
     * redelivery count in 2.8 — other subscription types log a one-time warning and the
     * policy has no effect). Keep {@code maxConsumeAttempts == deadLetterMaxRedeliverCount}
     * so the endpoint's first firing coincides with the diversion threshold.
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
     * endpoint fires with the native {@code deadLetterMaxRedeliverCount} policy configured
     * and its threshold met, the message is handed to a negative ack so the client's
     * diversion publishes the DLQ copy (WI33 F-DLQ); otherwise it is ACKED permanently and
     * dropped with a loud WARN. Set {@code 0} for unlimited negative-ack redelivery (the
     * pre-WI22 behavior).
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
