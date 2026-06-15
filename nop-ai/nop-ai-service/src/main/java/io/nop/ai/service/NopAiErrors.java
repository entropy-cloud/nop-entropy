package io.nop.ai.service;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopAiErrors {

    String ARG_SESSION_ID = "sessionId";

    ErrorCode ERR_AI_SESSION_ID_REQUIRED =
            define("nop.err.ai.session-id-required", "会话ID不能为空", ARG_SESSION_ID);
}
