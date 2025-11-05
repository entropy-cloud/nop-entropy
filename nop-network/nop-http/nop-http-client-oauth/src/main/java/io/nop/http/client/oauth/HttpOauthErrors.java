package io.nop.http.client.oauth;

import io.nop.api.core.exceptions.ErrorCode;

public interface HttpOauthErrors {

    String ARG_PROVIDER = "provider";

    ErrorCode ERR_HTTP_OAUTH_NO_USER_CONTEXT = ErrorCode.define("nop.err.http.oauth.no-user-context",
            "http请求要求上下文中存在用户信息");

    ErrorCode ERR_HTTP_OAUTH_UNKNOWN_PROVIDER = ErrorCode.define("nop.err.http.oauth.unknown-provider",
            "未定义的oauth提供者：{provider}", ARG_PROVIDER);
}
