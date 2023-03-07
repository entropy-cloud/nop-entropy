/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.ApiConstants.API_STATUS_BAD_REQUEST;
import static io.nop.api.core.ApiConstants.API_STATUS_UNAUTHORIZED;
import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface NopAuthErrors {
    String ARG_USER_NAME = "userName";
    String ARG_SITE_ID = "siteId";

    String ARG_PRINCIPAL_ID = "principalId";

    String ARG_SESSION_ID = "sessionId";
    String ARG_AUTH_TOKEN = "authToken";

    ErrorCode ERR_AUTH_INVALID_LOGIN_REQUEST = define(API_STATUS_BAD_REQUEST, "nop.err.auth.invalid-login-request",
            "登录请求参数不合法");

    ErrorCode ERR_AUTH_LOGIN_CHECK_FAIL = define("nop.err.auth.login-check-fail", "登陆失败，用户名或者密码不匹配");

    ErrorCode ERR_AUTH_LOGIN_WITH_UNKNOWN_USER = define("nop.err.auth.login-with-unknown-user", "登陆使用的用户名不存在");

    ErrorCode ERR_AUTH_LOGIN_CHECK_FAIL_TOO_MANY_TIMES = define("nop.err.auth.login-check-fail-too-many-times",
            "登录失败次数过多，账号已被暂时禁用，请等待一段时间再尝试或者联系管理员");

    ErrorCode ERR_AUTH_USER_NOT_ALLOW_LOGIN = define("nop.err.auth.user-not-allow-login",
            "用户[{principalId}]的账号已经过期或者被禁用，不允许登录", ARG_PRINCIPAL_ID);

    ErrorCode ERR_AUTH_INVALID_VERIFY_CODE = define("nop.err.auth.invalid-verify-code", "验证码不匹配或者已失效");

    ErrorCode ERR_AUTH_UNKNOWN_SITE = define("nop.err.auth.unknown-site", "未知的站点：{siteId}", ARG_SITE_ID);

    ErrorCode ERR_AUTH_SESSION_EXPIRED = define(API_STATUS_UNAUTHORIZED, "nop.err.auth.session-expired", "用户未登录或者会话已过期",
            ARG_SESSION_ID, ARG_AUTH_TOKEN);
}
