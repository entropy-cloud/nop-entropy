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
}
