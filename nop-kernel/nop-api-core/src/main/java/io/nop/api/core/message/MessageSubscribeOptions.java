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
     * 如果大于0，表示当缓存中消息个数不到batchReceiveCount时，最多允许等待多少时间，单位为毫秒。
     * 该值的语义映射依后端而定：可能与后端的拉取/长轮询阻塞时长绑定，并进入其活性/踢出预算
     * （如 Kafka 将其用作 poll 阻塞时长并与 max.poll.interval.ms 相互约束）。具体见各后端配置类文档。
     */
    private int batchReceiveTimeout;

    /**
     * 启动多少个Consumer并行消费。Pulsar：SINGLE_ACTIVE 语义（Exclusive/Failover，含
     * 未配置型）下该参数被忽略（warn-once）；LOAD_BALANCED/ORDERED_BY_KEY 语义下照常启动
     * N 个 consumer；均未配置且 concurrency>1 时按 Shared 处理（warn-once）。Kafka：启动
     * N 个独立 consumer（组语义）。SysDao（DB 轮询模型）与多 JVM 部署边界见
     * backend-semantics-design.md 3.11——SysDao 忽略此参数（每 JVM 独立轮询）；Kafka
     * 多 JVM 部署由消费组自动分区分摊。
     */
    private int concurrency;

    private SubscriptionType subscriptionType;

    /**
     * WI27 (D4): broker-neutral subscription semantics — preferred over the deprecated
     * {@code subscriptionType}. {@code null} (default) = not configured. Takes precedence
     * over {@code subscriptionType} when both are set (a conflict is warned once).
     */
    private SubscriptionMode subscriptionMode;
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

    public SubscriptionMode getSubscriptionMode() {
        return subscriptionMode;
    }

    public void setSubscriptionMode(SubscriptionMode subscriptionMode) {
        this.subscriptionMode = subscriptionMode;
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