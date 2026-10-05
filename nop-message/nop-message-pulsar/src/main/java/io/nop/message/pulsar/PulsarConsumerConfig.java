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
}
