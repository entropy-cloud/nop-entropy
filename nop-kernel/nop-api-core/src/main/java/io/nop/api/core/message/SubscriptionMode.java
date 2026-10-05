/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

/**
 * WI27 (D4): broker-neutral subscription semantics. Set
 * {@link MessageSubscribeOptions#setSubscriptionMode(SubscriptionMode)} instead of the
 * legacy Pulsar-vocabulary {@code subscriptionType} (which is in its deprecation window —
 * using it logs a one-time warning pointing here).
 *
 * <p>Mapping to the legacy values: SINGLE_ACTIVE ↔ Exclusive (and Failover — failover's
 * leader-election nuance is only reachable via the legacy type), LOAD_BALANCED ↔ Shared,
 * ORDERED_BY_KEY ↔ Key_Shared. Note: this enum shares its simple name with Pulsar's
 * {@code SubscriptionMode} (Durable/NonDurable — a different concept); use qualified names
 * if both are ever referenced in one file.
 */
public enum SubscriptionMode {
    /**
     * Only one active consumer per subscription name at a time (Pulsar Exclusive/Failover).
     */
    SINGLE_ACTIVE,

    /**
     * Multiple consumers share the subscription, messages distributed without an ordering
     * guarantee (Pulsar Shared). Kafka consumer groups approximate this semantics.
     */
    LOAD_BALANCED,

    /**
     * Messages with the same ordering key are routed to the same consumer (Pulsar
     * Key_Shared). No Kafka equivalent.
     */
    ORDERED_BY_KEY
}
