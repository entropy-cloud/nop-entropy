/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.login;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.auth.api.messages.LoginRequest;

public interface IUserContextHook {
    void onLoginSuccess(IUserContext context, LoginRequest request);

    void onLoginFail(LoginRequest request, ErrorCode errorCode, String userName, int failCount);

    void onLogout(String userName, String sessionId, int logoutType);

    void onAccess(IUserContext context);

    void onUpdate(IUserContext context);
}