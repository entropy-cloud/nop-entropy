/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium;
    
import io.nop.api.core.exceptions.ErrorCode;

/**
 * Debezium 模块错误码定义
 */
public interface DebeziumErrors {
    String PREFIX = "nop.message.debezium.";

    ErrorCode ERR_DEBEZIUM_ENGINE_START_FAILED =
            ErrorCode.define(PREFIX + "engine-start-failed", "Debezium engine failed to start");

    ErrorCode ERR_DEBEZIUM_ENGINE_STOP_FAILED =
            ErrorCode.define(PREFIX + "engine-stop-failed", "Debezium engine failed to stop");

    ErrorCode ERR_DEBEZIUM_CONFIG_INVALID =
            ErrorCode.define(PREFIX + "config-invalid", "Invalid Debezium configuration");

    ErrorCode ERR_DEBEZIUM_CONNECTOR_NOT_FOUND =
            ErrorCode.define(PREFIX + "connector-not-found", "Debezium connector not found");

    ErrorCode ERR_DEBEZIUM_EVENT_PARSE_FAILED =
            ErrorCode.define(PREFIX + "event-parse-failed", "Failed to parse Debezium change event");

    ErrorCode ERR_DEBEZIUM_UNSUPPORTED_CONNECTOR_TYPE =
            ErrorCode.define(PREFIX + "unsupported-connector-type", "Unsupported connector type");
}
