/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.AccessCodeRequest;
import io.nop.auth.api.messages.AccessTokenRequest;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.api.messages.LoginUserInfo;
import io.nop.auth.api.messages.LogoutRequest;
import io.nop.auth.api.messages.RefreshTokenRequest;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.ILoginService;
import io.nop.auth.core.spi.ILoginSpi;
import io.nop.auth.service.NopAuthErrors;
import io.nop.core.context.IServiceContext;
import io.nop.core.unittest.VarCollector;
import jakarta.inject.Inject;

import java.util.concurrent.CompletionStage;

/**
 * 两种访问方式: 1. GraphQL : LoginApi__login 2. REST: /r/LoginApi__login
 */
@BizModel("LoginApi")
public class LoginApiBizModel implements ILoginSpi {

    @Inject
    ILoginService loginService;

    @BizMutation("login")
    @Auth(noAuth = true)
    public CompletionStage<LoginResult> loginAsync(@RequestBean LoginRequest request, IServiceContext context) {
        return loginService.loginAsync(request, context.getRequestHeaders()).thenApply(this::buildLoginResult);
    }

    @BizMutation
    @Override
    public CompletionStage<Void> logoutAsync(@RequestBean LogoutRequest request, IServiceContext context) {
        return loginService.logoutAsync(AuthApiConstants.LOGOUT_TYPE_MANUAL, request);
    }

    @BizQuery
    @Override
    public CompletionStage<LoginResult> getLoginResultAsync(@RequestBean AccessCodeRequest request,
                                                            IServiceContext context) {
        AuthToken authToken = loginService.parseAuthToken(request.getAccessCode());
        return loginService.getUserContextAsync(authToken, context.getRequestHeaders()).thenApply(this::buildLoginResult);
    }

    @BizQuery
    @Override
    public CompletionStage<LoginUserInfo> getLoginUserInfoAsync(@RequestBean AccessTokenRequest request,
                                                                IServiceContext context) {
        AuthToken authToken = loginService.parseAuthToken(request.getAccessToken());
        return loginService.getUserContextAsync(authToken, context.getRequestHeaders()).thenApply(loginService::getUserInfo);
    }

    @BizMutation
    @Override
    public CompletionStage<LoginResult> refreshTokenAsync(@RequestBean RefreshTokenRequest request,
                                                          IServiceContext context) {
        AuthToken token = loginService.parseAuthToken(request.getRefreshToken());
        return loginService.getUserContextAsync(token, context.getRequestHeaders()).thenApply(this::buildLoginResult);
    }

    @BizQuery
    public String generateVerifyCode(@Name("verifySecret") String verifySecret) {
        return loginService.generateVerifyCode(verifySecret);
    }

    protected LoginResult buildLoginResult(IUserContext userContext) {
        if (userContext == null)
            throw new NopException(NopAuthErrors.ERR_AUTH_SESSION_EXPIRED);

        LoginResult result = new LoginResult();
        String accessToken = userContext.getAccessToken();
        result.setAccessToken(accessToken);

        VarCollector.instance().collectVar("accessToken", accessToken);

        String refreshToken = userContext.getRefreshToken();
        result.setRefreshToken(refreshToken);

        AuthToken authToken = loginService.parseAuthToken(accessToken);
        // 返回时间为秒
        result.setExpiresIn(authToken.getExpireSeconds());

        result.setUserInfo(loginService.getUserInfo(userContext));

        VarCollector.instance().collectVar("refreshToken", refreshToken);

        return result;
    }
}