/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.pulsar;

import io.nop.api.core.exceptions.ErrorCode;

public interface PulsarErrors {
    String PREFIX = "nop.message.pulsar.";

    ErrorCode ERR_SERVICE_URL_NOT_CONFIGURED =
            ErrorCode.define(PREFIX + "service-url-not-configured", "Pulsar serviceUrl is not configured");

    ErrorCode ERR_PULSAR_CLIENT_CREATE_FAILED =
            ErrorCode.define(PREFIX + "client-create-failed", "Failed to create Pulsar client");

    ErrorCode ERR_PULSAR_PRODUCER_CREATE_FAILED =
            ErrorCode.define(PREFIX + "producer-create-failed", "Failed to create Pulsar producer");

    ErrorCode ERR_PULSAR_CONSUMER_CREATE_FAILED =
            ErrorCode.define(PREFIX + "consumer-create-failed", "Failed to create Pulsar consumer");
}
