/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.ApiConstants.API_STATUS_UNAUTHORIZED;
import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface AuthCoreErrors {
    String ARG_ALG = "alg";
    String ARG_TOKEN = "token";

    String ARG_MIN_LENGTH = "minLength";
    String ARG_LENGTH = "length";

    String ARG_MIN_COUNT = "minCount";
    String ARG_CLAIMS = "claims";

    String ARG_LOCALE = "locale";

    ErrorCode ERR_JWT_INVALID_ALGORITHM = define(API_STATUS_UNAUTHORIZED, "nop.err.auth.jwt.invalid-algorithm",
            "JWT使用了不支持的算法:{alg}", ARG_ALG);

    ErrorCode ERR_JWT_INVALID_TOKEN = define(API_STATUS_UNAUTHORIZED, "nop.err.auth.jwt.invalid-token",
            "JWT Token解析失败");

    ErrorCode ERR_JWT_TOKEN_EXPIRED = define(API_STATUS_UNAUTHORIZED, "nop.err.auth.jwt.token-expired", "访问令牌已失效");

    ErrorCode ERR_AUTH_USER_NOT_LOGIN =
            define("nop.err.auth.user-not-login", "用户尚未登录或者登录凭证已过期");

    ErrorCode ERR_AUTH_LOGIN_NOT_CURRENT_SESSION =
            define("nop.err.auth.not-current-session", "账号已经在别处登录，当前会话已失效");

    ErrorCode ERR_AUTH_OLD_PASSWORD_NOT_MATCH =
            define("nop.err.auth.password-not-match", "旧密码不匹配");

    ErrorCode ERR_PASSWORD_LENGTH_TOO_SHORT =
            define("nop.err.auth.password-length-too-short",
                    "密码长度太短，要求最少{minLength}位", ARG_MIN_LENGTH, ARG_LENGTH);

    ErrorCode ERR_PASSWORD_TOO_FEW_UPPER_CASE =
            define("nop.err.auth.password-contains-too-few-upper-case",
                    "密码必须包含至少{minCount}个大写字母", ARG_MIN_COUNT);

    ErrorCode ERR_PASSWORD_TOO_FEW_LOWER_CASE =
            define("nop.err.auth.password-contains-too-few-lower-case",
                    "密码必须包含至少包含{minCount}个小写字母", ARG_MIN_COUNT);

    ErrorCode ERR_PASSWORD_TOO_FEW_SPECIAL_CHAR =
            define("nop.err.auth.password-contains-too-few-special-char",
                    "密码必须包含至少{minCount}特殊字符", ARG_MIN_COUNT);

    ErrorCode ERR_PASSWORD_TOO_FEW_DIGITS =
            define("nop.err.auth.password-contains-too-few-digits",
                    "密码必须包含至少{minCount}数字", ARG_MIN_COUNT);

    ErrorCode ERR_PASSWORD_MUST_NOT_BE_SAME_AS_USER_NAME =
            define("nop.err.auth.password-must-not-be-same-as-user-name",
                    "密码不能与用户名重复");

    ErrorCode ERR_AUTH_NOT_AUTHORIZED =
            define("nop.err.auth.not-authorized",
                    "用户未登录或者会话已过期");
}
