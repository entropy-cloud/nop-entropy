package io.nop.ai.toolkit;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/** toolkit 通用校验码（审计 AI-14，plan 356）。 */
public interface NopAiToolkitErrors {
    String ARG_DETAIL = "detail";

    ErrorCode ERR_AI_TOOLKIT_INVALID_ARGUMENT = define("nop.err.ai.toolkit.invalid-argument",
            "Invalid argument: {detail}", ARG_DETAIL);

    ErrorCode ERR_AI_TOOLKIT_INVALID_STATE = define("nop.err.ai.toolkit.invalid-state",
            "Invalid state: {detail}", ARG_DETAIL);

    /** 3.2-MA-07（plan 2557 裁定 A）：per-path 排队积压超上界 fail-loud（QueueOverflowPolicy ERROR 语义） */
    ErrorCode ERR_AI_TOOLKIT_PATH_QUEUE_OVERFLOW = define("nop.err.ai.toolkit.path-queue-overflow",
            "Path queue overflow: {detail}", ARG_DETAIL);
}
