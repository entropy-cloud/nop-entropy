/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.message;

/**
 * 与Pulsar消息队列的SubscriptionType相对应
 */
public enum SubscriptionType {
    /**
     * There can be only 1 consumer on the same topic with the same subscription name.
     */
    Exclusive,

    /**
     * Multiple consumer will be able to use the same subscription name and the messages will be dispatched
     * according to a round-robin rotation between the connected consumers.
     *
     * <p>In this mode, the consumption order is not guaranteed.
     */
    Shared,

    /**
     * Multiple consumer will be able to use the same subscription name but only 1 consumer will receive the messages.
     * If that consumer disconnects, one of the other connected consumers will start receiving messages.
     *
     * <p>In failover mode, the consumption ordering is guaranteed.
     *
     * <p>In case of partitioned topics, the ordering is guaranteed on a per-partition basis.
     * The partitions assignments will be split across the available consumers. On each partition,
     * at most one consumer will be active at a given point in time.
     */
    Failover,

    /**
     * Multiple consumer will be able to use the same subscription and all messages with the same key
     * will be dispatched to only one consumer.
     *
     * <p>Use ordering_key to overwrite the message key for message ordering.
     */
    Key_Shared
}