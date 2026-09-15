package io.nop.ai.service;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopAiErrors {

    String ARG_SESSION_ID = "sessionId";

    ErrorCode ERR_AI_SESSION_ID_REQUIRED =
            define("nop.err.ai.session-id-required", "Session ID must not be empty", ARG_SESSION_ID);
}
