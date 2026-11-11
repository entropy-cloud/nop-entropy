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
    String PREFIX = "nop.err.message.pulsar.";

    ErrorCode ERR_SERVICE_URL_NOT_CONFIGURED =
            ErrorCode.define(PREFIX + "service-url-not-configured", "Pulsar serviceUrl is not configured");

    ErrorCode ERR_PULSAR_CLIENT_CREATE_FAILED =
            ErrorCode.define(PREFIX + "client-create-failed", "Failed to create Pulsar client");

    ErrorCode ERR_PULSAR_PRODUCER_CREATE_FAILED =
            ErrorCode.define(PREFIX + "producer-create-failed", "Failed to create Pulsar producer");

    ErrorCode ERR_PULSAR_CONSUMER_CREATE_FAILED =
            ErrorCode.define(PREFIX + "consumer-create-failed", "Failed to create Pulsar consumer");

    ErrorCode ERR_PULSAR_SERVICE_NOT_ACTIVE =
            ErrorCode.define(PREFIX + "service-not-active", "PulsarMessageService is not initialized (or already destroyed)");

    ErrorCode ERR_PULSAR_SUBSCRIBE_NAME_NOT_CONFIGURED =
            ErrorCode.define(PREFIX + "subscribe-name-not-configured",
                    "MessageSubscribeOptions.subscribeName is required by the Pulsar backend");

    // 4.1-C3-004（plan 2558 裁定 H）：sendPulsarAsync 全段信封错误码 —— 非 NopException
    // 异常包装此码后 completeExceptionally（Kafka 镜像 ERR_KAFKA_SEND_FAILED 形态）。
    ErrorCode ERR_PULSAR_SEND_FAILED =
            ErrorCode.define(PREFIX + "send-failed", "Pulsar producer send failed");

    // 4.1-MA-01（plan 2558 裁定 A）：handler 返回 CompletionStage 且 toCompletableFuture()
    // 非法（非 CompletableFuture 形态）时消费等待点的 typed error —— 不静默降级为无界等待。
    ErrorCode ERR_PULSAR_CONSUME_AWAIT_NOT_ASYNC =
            ErrorCode.define(PREFIX + "consume-await-not-async",
                    "consume handler returned a CompletionStage that is not a CompletableFuture");
}
