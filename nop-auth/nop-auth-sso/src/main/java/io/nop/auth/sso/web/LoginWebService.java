/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.sso.web;

import io.nop.auth.api.messages.LogoutRequest;
import io.nop.auth.core.login.ILoginService;

import jakarta.inject.Inject;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.util.concurrent.CompletionStage;

import static io.nop.auth.api.AuthApiConstants.LOGOUT_TYPE_SSO_CALLBACK;

public class LoginWebService {
    @Inject
    ILoginService loginService;

    @POST
    @Path("/sso-logout")
    public CompletionStage<Void> ssoLogoutAsync(@FormParam("logout_token") String logoutToken) {
        LogoutRequest req = new LogoutRequest();
        req.setAccessToken(logoutToken);
        return loginService.logoutAsync(LOGOUT_TYPE_SSO_CALLBACK, req);
    }
}