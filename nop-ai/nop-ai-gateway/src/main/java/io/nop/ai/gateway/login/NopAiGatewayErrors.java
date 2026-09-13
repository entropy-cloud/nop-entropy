package io.nop.ai.gateway.login;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * 扫码登录链路专用错误码（审计 AI-7：替代 ~10 个失败分支复用 ERR_CHECK_INVALID_ARGUMENT）。
 */
public interface NopAiGatewayErrors {
    String ARG_CHANNEL_TYPE = "channelType";
    String ARG_MSG = "msg";

    ErrorCode ERR_CHANNEL_LOGIN_NOT_ENABLED = define("nop.err.ai.channel-login.not-enabled",
            "Scan-login is not enabled in this deployment: {msg}", ARG_MSG);

    ErrorCode ERR_CHANNEL_LOGIN_INVALID_CALLBACK = define("nop.err.ai.channel-login.invalid-callback",
            "Invalid scan callback for channelType={channelType}: {msg}", ARG_CHANNEL_TYPE, ARG_MSG);

    ErrorCode ERR_CHANNEL_LOGIN_NO_SERVER_IDENTITY = define("nop.err.ai.channel-login.no-server-identity",
            "Provider asserted no server-side identity for channelType={channelType}: {msg}", ARG_CHANNEL_TYPE, ARG_MSG);

    ErrorCode ERR_CHANNEL_LOGIN_NO_BINDING = define("nop.err.ai.channel-login.no-binding",
            "No effective channel binding for channelType={channelType}; bind the channel before scan-login",
            ARG_CHANNEL_TYPE);

    ErrorCode ERR_CHANNEL_LOGIN_IDENTITY_MISMATCH = define("nop.err.ai.channel-login.identity-mismatch",
            "Channel identity is bound to a different user than the ticket owner; refusing scan-login "
                    + "(possible forged scan callback) channelType={channelType}", ARG_CHANNEL_TYPE);

    ErrorCode ERR_CHANNEL_LOGIN_SESSION_FAILED = define("nop.err.ai.channel-login.session-failed",
            "Session bootstrap failed for scan-login: {msg}", ARG_MSG);
}
