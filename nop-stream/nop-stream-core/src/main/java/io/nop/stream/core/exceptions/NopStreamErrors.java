/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.exceptions;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopStreamErrors {
    String ARG_ARG_NAME = "argName";
    String ARG_DETAIL = "detail";
    String ARG_OPERATOR_NAME = "operatorName";
    String ARG_STATE_NAME = "stateName";
    String ARG_CLASS_NAME = "className";
    String ARG_CONFIG_KEY = "configKey";
    String ARG_OPERATION = "operation";

    ErrorCode ERR_STREAM_NULL_ARG =
            define("nop.err.stream.null-arg", "Argument {argName} must not be null", ARG_ARG_NAME);

    ErrorCode ERR_STREAM_INVALID_STATE =
            define("nop.err.stream.invalid-state", "Invalid stream state: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_CONFIG_ERROR =
            define("nop.err.stream.config-error", "Stream configuration error: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_UNSUPPORTED =
            define("nop.err.stream.unsupported", "Unsupported operation: {operation}", ARG_OPERATION);

    ErrorCode ERR_STREAM_SERIALIZATION =
            define("nop.err.stream.serialization", "Serialization failed: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_OPERATOR_ERROR =
            define("nop.err.stream.operator-error", "Operator {operatorName} execution error: {detail}",
                    ARG_OPERATOR_NAME, ARG_DETAIL);

    ErrorCode ERR_STREAM_CHECKPOINT_ERROR =
            define("nop.err.stream.checkpoint-error", "Checkpoint error: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_STATE_ERROR =
            define("nop.err.stream.state-error", "State management error: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_INVALID_ARG =
            define("nop.err.stream.invalid-arg", "Invalid value for argument {argName}: {detail}",
                    ARG_ARG_NAME, ARG_DETAIL);

    ErrorCode ERR_STREAM_INIT_ERROR =
            define("nop.err.stream.init-error", "Initialization failed: {detail}", ARG_DETAIL);
}
