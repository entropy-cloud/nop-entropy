/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

import io.nop.api.core.annotations.config.ConfigBean;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
@ConfigBean
public class MessageSubscribeOptions {
    /**
     * 对应于pulsar的subscribeName
     */
    private String subscribeName;

    /**
     * @deprecated D11 (WI14): transaction semantics are removed from the message SPI —
     * consumption follows the at-least-once baseline and the field is ignored (one-time
     * warn on use). The field is kept for YAML compatibility during the D8 deprecation
     * window. A future transaction capability would be declared per backend (D1).
     */
    @Deprecated
    private boolean transactional;

    /**
     * @deprecated D11 (WI14): ignored together with {@link #transactional}.
     */
    @Deprecated
    private long transactionTimeout;

    /**
     * 如果大于0，表示支持批量消费，指定一次性最多返回多少条消息
     */
    private int batchReceiveCount;

    /**
     * 如果大于0，表示当缓存中消息个数不到batchReceiveCount时，最多允许等待多少时间，单位为毫秒
     */
    private int batchReceiveTimeout;

    /**
     * 启动多少个Consumer并行消费。exclusive模式下会忽略这个参数
     */
    private int concurrency;

    private SubscriptionType subscriptionType;
    private SeekMode seekMode;
    private String seekToMessage;
    private long seekToTime;

    public String getSubscribeName() {
        return subscribeName;
    }

    public void setSubscribeName(String subscribeName) {
        this.subscribeName = subscribeName;
    }

    /**
     * @deprecated D11 (WI14): ignored (one-time warn on use). See field javadoc.
     */
    @Deprecated
    public boolean isTransactional() {
        return transactional;
    }

    /** @deprecated D11 (WI14): ignored. */
    @Deprecated
    public void setTransactional(boolean transactional) {
        this.transactional = transactional;
    }

    /** @deprecated D11 (WI14): ignored. */
    @Deprecated
    public long getTransactionTimeout() {
        return transactionTimeout;
    }

    /** @deprecated D11 (WI14): ignored. */
    @Deprecated
    public void setTransactionTimeout(long transactionTimeout) {
        this.transactionTimeout = transactionTimeout;
    }

    public SubscriptionType getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(SubscriptionType subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public SeekMode getSeekMode() {
        return seekMode;
    }

    public void setSeekMode(SeekMode seekMode) {
        this.seekMode = seekMode;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public String getSeekToMessage() {
        return seekToMessage;
    }

    public void setSeekToMessage(String seekToMessage) {
        this.seekToMessage = seekToMessage;
    }

    public long getSeekToTime() {
        return seekToTime;
    }

    public void setSeekToTime(long seekToTime) {
        this.seekToTime = seekToTime;
    }

    public boolean allowBatchConsume() {
        return batchReceiveCount > 0;
    }

    public int getBatchReceiveCount() {
        return batchReceiveCount;
    }

    public void setBatchReceiveCount(int batchReceiveCount) {
        this.batchReceiveCount = batchReceiveCount;
    }

    public int getBatchReceiveTimeout() {
        return batchReceiveTimeout;
    }

    public void setBatchReceiveTimeout(int batchReceiveTimeout) {
        this.batchReceiveTimeout = batchReceiveTimeout;
    }
}