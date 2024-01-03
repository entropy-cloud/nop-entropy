/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.sso.web;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.auth.api.messages.LogoutRequest;
import io.nop.auth.core.login.ILoginService;
import jakarta.inject.Inject;

import java.util.concurrent.CompletionStage;

import static io.nop.auth.api.AuthApiConstants.LOGOUT_TYPE_SSO_CALLBACK;

@BizModel("LoginApi")
public class SsoLoginWebService {
    @Inject
    ILoginService loginService;

    @BizMutation
    public CompletionStage<Void> ssoLogoutAsync(@Name("accessToken") String logoutToken) {
        LogoutRequest req = new LogoutRequest();
        req.setAccessToken(logoutToken);
        return loginService.logoutAsync(LOGOUT_TYPE_SSO_CALLBACK, req);
    }
}