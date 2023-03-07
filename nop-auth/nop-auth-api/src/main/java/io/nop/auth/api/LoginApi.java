/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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

    default CompletionStage<ApiResponse<LoginResult>> loginAsync(ApiRequest<LoginRequest> request) {
        return FutureHelper.futureCall(() -> login(request));
    }

    default CompletionStage<ApiResponse<Void>> logoutAsync(ApiRequest<LogoutRequest> request) {
        return FutureHelper.futureCall(() -> logout(request));
    }
}