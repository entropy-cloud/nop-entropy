/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.spi;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.auth.api.messages.AccessCodeRequest;
import io.nop.auth.api.messages.AccessTokenRequest;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.api.messages.LoginUserInfo;
import io.nop.auth.api.messages.LogoutRequest;
import io.nop.auth.api.messages.RefreshTokenRequest;
import io.nop.core.context.IServiceContext;

import java.util.concurrent.CompletionStage;

public interface ILoginSpi {

    /**
     * 根据用户名、密码登录
     *
     * @param request
     * @return
     */
    @BizMutation
    CompletionStage<LoginResult> loginAsync(LoginRequest request, IServiceContext context);

    @BizMutation
    CompletionStage<Void> logoutAsync(LogoutRequest request, IServiceContext context);

    /**
     * 根据一次性的accessCode得到accessToken和refreshToken
     *
     * @param request
     */
    @BizQuery
    CompletionStage<LoginResult> getLoginResultAsync(AccessCodeRequest request, IServiceContext context);

    /**
     * 根据accessToken获取到当前登录用户的详细信息
     *
     * @param request accessToken信息
     */
    @BizQuery
    CompletionStage<LoginUserInfo> getLoginUserInfoAsync(AccessTokenRequest request, IServiceContext context);

    /**
     * accessToken失效的时候可以用refreshToken去重新获取
     *
     * @param request 传入refreshToken
     */
    @BizMutation
    CompletionStage<LoginResult> refreshTokenAsync(RefreshTokenRequest request, IServiceContext context);
}