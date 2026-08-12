/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.api;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.messages.AccessCodeRequest;
import io.nop.auth.api.messages.AccessTokenRequest;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.api.messages.LoginUserInfo;
import io.nop.auth.api.messages.LogoutRequest;
import io.nop.auth.api.messages.MfaVerifyRequest;
import io.nop.auth.api.messages.RefreshTokenRequest;

import java.util.concurrent.CompletionStage;

@BizModel("LoginApi")
public interface LoginApi {

    /**
     * 根据用户名、密码登录
     *
     * @param request
     * @return
     */
    @BizMutation
    ApiResponse<LoginResult> login(ApiRequest<LoginRequest> request);

    /**
     * 发送短信验证码（登录用，公开访问）。含手机号/IP 双维度限流 + 防枚举统一响应（设计 §3.3 / §3.6）。
     *
     * @param phone 手机号
     */
    @BizMutation
    ApiResponse<Void> sendSmsCode(ApiRequest<java.util.Map<String, Object>> request);

    /**
     * MFA 第二因子短信验证码重发（公开访问）。凭 challengeToken 服务端取号，
     * challenge 已消费/作废则返回 {@code ERR_AUTH_MFA_CHALLENGE_EXPIRED}（设计 §3.6）。
     *
     * @param challengeToken 第一因子通过后返回的 challenge token
     */
    @BizMutation
    ApiResponse<Void> sendMfaCode(ApiRequest<java.util.Map<String, Object>> request);

    /**
     * 第二因子验证（公开访问）。成功返回 {@link LoginResult}：
     * 密码类 loginType 签发 accessToken；信道类 loginType 签发 accessCode（设计 §3.2 / §3.6）。
     *
     * @param request {@code {challengeToken, code, recoveryCode?}}
     */
    @BizMutation
    ApiResponse<LoginResult> mfaVerify(ApiRequest<MfaVerifyRequest> request);

    @BizMutation
    ApiResponse<Void> logout(ApiRequest<LogoutRequest> request);

    /**
     * 根据一次性的accessCode得到accessToken和refreshToken
     *
     * @param request
     */
    @BizQuery
    ApiResponse<LoginResult> getLoginResult(ApiRequest<AccessCodeRequest> request);

    /**
     * 根据accessToken获取到当前登录用户的详细信息
     *
     * @param request accessToken信息
     */
    @BizQuery
    ApiResponse<LoginUserInfo> getLoginUserInfo(ApiRequest<AccessTokenRequest> request);

    /**
     * accessToken失效的时候可以用refreshToken去重新获取
     *
     * @param request 传入refreshToken
     */
    @BizMutation
    ApiResponse<LoginResult> refreshToken(ApiRequest<RefreshTokenRequest> request);

    @BizMutation
    default CompletionStage<ApiResponse<LoginResult>> loginAsync(ApiRequest<LoginRequest> request) {
        return FutureHelper.futureCall(() -> login(request));
    }

    @BizMutation
    default CompletionStage<ApiResponse<Void>> logoutAsync(ApiRequest<LogoutRequest> request) {
        return FutureHelper.futureCall(() -> logout(request));
    }
}