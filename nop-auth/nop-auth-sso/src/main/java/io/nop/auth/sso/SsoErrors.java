/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.sso;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface SsoErrors {
    String ARG_ERROR = "error";

    ErrorCode ERR_AUTH_SSO_ACCESS_FAIL =
            define("nop.err.auth.sso.access-fail", "单点登录访问失败：{error}", ARG_ERROR);
}
