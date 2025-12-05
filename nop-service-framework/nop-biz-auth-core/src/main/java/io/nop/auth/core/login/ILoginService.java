/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.login;

import io.nop.api.core.auth.IUserContext;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginUserInfo;
import io.nop.auth.api.messages.LogoutRequest;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 用户登录后产生UserContext，保存在全局缓存中。
 */
public interface ILoginService {

    CompletionStage<IUserContext> loginAsync(LoginRequest request, Map<String, Object> headers);

    CompletionStage<Void> logoutAsync(int logoutType, LogoutRequest request);

    /**
     * 强制结束某个用户的当前会话
     */
    CompletionStage<Void> killLoginAsync(String userName);

    /**
     * 将对IUserContext的修改更新回全局缓存
     */
    CompletionStage<Void> flushUserContextAsync(IUserContext userContext);

    CompletionStage<IUserContext> getUserContextAsync(AuthToken accessToken, Map<String,Object> headers);

    /**
     * 得到指定用户当前的登录上下文
     *
     * @param userName 指定userName
     * @return 登录用户上下文。如果用户没有处于登录状态，则返回null
     */
    CompletionStage<IUserContext> getLoginUserContextAsync(String userName);

    /**
     * 返回userNick,avatar等用于显示的用户信息
     *
     * @param userContext 当前登录用户
     * @return 用户信息
     */
    LoginUserInfo getUserInfo(IUserContext userContext);

    String generateVerifyCode(String verifySecret);

    AuthToken parseAuthToken(String accessToken);

    String refreshToken(IUserContext userContext, AuthToken authToken);

}