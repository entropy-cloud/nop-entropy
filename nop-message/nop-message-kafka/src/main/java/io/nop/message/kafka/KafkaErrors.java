/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.kafka;

import io.nop.api.core.exceptions.ErrorCode;

/**
 * Error codes for {@code KafkaMessageService} (mirrors {@code PulsarErrors}).
 */
public interface KafkaErrors {
    String PREFIX = "nop.message.kafka.";

    ErrorCode ERR_BOOTSTRAP_SERVERS_NOT_CONFIGURED =
            ErrorCode.define(PREFIX + "bootstrap-servers-not-configured",
                    "Kafka bootstrapServers is not configured");

    ErrorCode ERR_GROUP_ID_NOT_CONFIGURED =
            ErrorCode.define(PREFIX + "group-id-not-configured",
                    "Kafka groupId is not configured");

    ErrorCode ERR_KAFKA_PRODUCER_CREATE_FAILED =
            ErrorCode.define(PREFIX + "producer-create-failed",
                    "Failed to create Kafka producer");

    ErrorCode ERR_KAFKA_CONSUMER_CREATE_FAILED =
            ErrorCode.define(PREFIX + "consumer-create-failed",
                    "Failed to create Kafka consumer");

    ErrorCode ERR_KAFKA_SERIALIZATION_FAILED =
            ErrorCode.define(PREFIX + "serialization-failed",
                    "Kafka serialization failed");

    ErrorCode ERR_KAFKA_SEEK_NOT_SUPPORTED =
            ErrorCode.define(PREFIX + "seek-not-supported",
                    "Kafka seek by message id is not supported");

    ErrorCode ERR_KAFKA_SEND_FAILED =
            ErrorCode.define(PREFIX + "send-failed",
                    "Kafka producer send failed");
}
