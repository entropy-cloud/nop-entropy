/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
import io.nop.auth.api.messages.MfaVerifyRequest;
import io.nop.auth.api.messages.RefreshTokenRequest;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.ILoginService;
import io.nop.auth.core.spi.ILoginSpi;
import io.nop.auth.service.NopAuthErrors;
import io.nop.auth.service.login.LoginServiceImpl;
import io.nop.core.context.IServiceContext;
import io.nop.core.unittest.VarCollector;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 两种访问方式: 1. GraphQL : LoginApi__login 2. REST: /r/LoginApi__login
 */
@BizModel("LoginApi")
public class LoginApiBizModel implements ILoginSpi {

    @Inject
    ILoginService loginService;

    @BizMutation("login")
    @Auth(publicAccess = true)
    public CompletionStage<LoginResult> loginAsync(@RequestBean LoginRequest request, IServiceContext context) {
        return loginService.loginAsync(request, context.getRequestHeaders()).thenApply(this::buildLoginResult);
    }

    @BizMutation
    @Auth(publicAccess = true)
    @Override
    public CompletionStage<Void> logoutAsync(@RequestBean LogoutRequest request, IServiceContext context) {
        return loginService.logoutAsync(AuthApiConstants.LOGOUT_TYPE_MANUAL, request);
    }

    @BizQuery
    @Auth(publicAccess = true)
    @Override
    public CompletionStage<LoginResult> getLoginResultAsync(@RequestBean AccessCodeRequest request,
                                                            IServiceContext context) {
        AuthToken authToken = loginService.parseAccessCode(request.getAccessCode());
        return loginService.getUserContextAsync(authToken, context.getRequestHeaders()).thenApply(this::buildLoginResult);
    }

    @BizQuery
    @Auth(publicAccess = true)
    @Override
    public CompletionStage<LoginUserInfo> getLoginUserInfoAsync(@RequestBean AccessTokenRequest request,
                                                                IServiceContext context) {
        AuthToken authToken = loginService.parseAuthToken(request.getAccessToken());
        return loginService.getUserContextAsync(authToken, context.getRequestHeaders()).thenApply(loginService::getUserInfo);
    }

    @BizMutation
    @Auth(publicAccess = true)
    @Override
    public CompletionStage<LoginResult> refreshTokenAsync(@RequestBean RefreshTokenRequest request,
                                                          IServiceContext context) {
        AuthToken token = loginService.parseRefreshToken(request.getRefreshToken());
        return loginService.getUserContextAsync(token, context.getRequestHeaders()).thenApply(this::buildLoginResult);
    }

    @BizQuery
    @Auth(publicAccess = true)
    public String generateVerifyCode(@Name("verifySecret") String verifySecret) {
        return loginService.generateVerifyCode(verifySecret);
    }

    // ===================== MFA / SMS 端点（设计 §3.6，W5） =====================

    /**
     * 发送登录短信验证码（公开访问，设计 §3.3 / §3.6）。
     * 含手机号/IP 双维度限流 + 防枚举统一响应。
     */
    @BizMutation
    @Auth(publicAccess = true)
    public void sendSmsCode(@Name("phone") String phone, IServiceContext context) {
        loginService.sendSmsCode(phone, extractClientIp(context));
    }

    /**
     * MFA 第二因子短信验证码重发（公开访问，设计 §3.6）。
     * 凭 challengeToken 服务端取号，challenge 已消费/作废返回 CHALLENGE_EXPIRED。
     */
    @BizMutation
    @Auth(publicAccess = true)
    public void sendMfaCode(@Name("challengeToken") String challengeToken, IServiceContext context) {
        loginService.sendMfaCode(challengeToken, extractClientIp(context));
    }

    /**
     * 第二因子验证（公开访问，设计 §3.2 / §3.6）。成功返回 {@link LoginResult}：
     * 密码类 loginType 签发 accessToken；信道类 loginType 签发 accessCode。
     */
    @BizMutation
    @Auth(publicAccess = true)
    public CompletionStage<LoginResult> mfaVerifyAsync(@RequestBean MfaVerifyRequest request, IServiceContext context) {
        return loginService.mfaVerifyAsync(request, context.getRequestHeaders()).thenApply(ctx -> {
            // 信道类 loginType：accessCode 由 completeMfaLogin stashed 到 attr，只返回 accessCode（不签发 accessToken）
            Object accessCode = ctx.getAttr(LoginServiceImpl.ATTR_MFA_ACCESS_CODE);
            if (accessCode instanceof String) {
                LoginResult result = new LoginResult();
                result.setAccessCode((String) accessCode);
                result.setUserInfo(loginService.getUserInfo(ctx));
                return result;
            }
            // 密码类 loginType：normal path（签发 accessToken）
            return buildLoginResult(ctx);
        });
    }

    /**
     * 从请求头提取客户端 IP（用于短信发送 IP 维度限流）。
     * 优先 X-Forwarded-For / X-Real-IP，取不到返回 null（IP 限流为次要防线）。
     */
    protected String extractClientIp(IServiceContext context) {
        if (context == null || context.getRequestHeaders() == null) {
            return null;
        }
        Map<String, Object> headers = context.getRequestHeaders();
        Object xff = headers.get("X-Forwarded-For");
        if (xff == null) {
            xff = headers.get("x-forwarded-for");
        }
        if (xff != null && !xff.toString().isEmpty()) {
            String ip = xff.toString().split(",")[0].trim();
            return ip.isEmpty() ? null : ip;
        }
        Object xri = headers.get("X-Real-IP");
        if (xri == null) {
            xri = headers.get("x-real-ip");
        }
        return xri == null ? null : xri.toString();
    }

    protected LoginResult buildLoginResult(IUserContext userContext) {
        if (userContext == null)
            throw new NopException(NopAuthErrors.ERR_AUTH_SESSION_EXPIRED);

        LoginResult result = new LoginResult();
        String accessToken = userContext.getAccessToken();
        result.setAccessToken(accessToken);

        // VarCollector 是可选的自测支持设施，AutoTestCase 结束后会将其置空，生产代码必须容忍其缺失
        VarCollector varCollector = VarCollector.instance();
        if (varCollector != null) {
            varCollector.collectVar("accessToken", accessToken);
        }

        String refreshToken = userContext.getRefreshToken();
        result.setRefreshToken(refreshToken);

        AuthToken authToken = loginService.parseAuthToken(accessToken);
        // 返回时间为秒
        result.setExpiresIn(authToken.getExpireSeconds());

        result.setUserInfo(loginService.getUserInfo(userContext));

        if (varCollector != null) {
            varCollector.collectVar("refreshToken", refreshToken);
        }

        return result;
    }
}