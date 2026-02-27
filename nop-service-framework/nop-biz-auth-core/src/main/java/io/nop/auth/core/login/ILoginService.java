/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.login;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.auth.IUserContextExtractor;
import io.nop.api.core.exceptions.NopLoginException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginUserInfo;
import io.nop.auth.api.messages.LogoutRequest;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 用户登录后产生UserContext，保存在全局缓存中。
 */
public interface ILoginService extends IUserContextExtractor {

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

    CompletionStage<IUserContext> getUserContextAsync(AuthToken accessToken, Map<String, Object> headers);

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

    /**
     * 从请求头中提取认证信息并构建用户上下文。
     * <p>
     * 默认实现从 headers 中获取 Authorization 头，解析 token 后调用 getUserContextAsync 获取用户上下文。
     *
     * @param headers 请求头信息
     * @return 用户上下文对象，如果没有认证信息则返回 null
     * @throws NopLoginException 如果认证信息存在但验证失败
     */
    @Override
    default IUserContext extractFromHeaders(Map<String, Object> headers) throws NopLoginException {
        if (headers == null)
            return null;

        Object authHeader = headers.get("Authorization");
        if (authHeader == null) {
            authHeader = headers.get("authorization");
        }
        if (authHeader == null)
            return null;

        String authStr = authHeader.toString();
        if (authStr.isEmpty())
            return null;

        // 支持 Bearer token 格式
        String accessToken;
        if (authStr.startsWith("Bearer ") || authStr.startsWith("bearer ")) {
            accessToken = authStr.substring(7);
        } else {
            accessToken = authStr;
        }

        if (accessToken.isEmpty())
            return null;

        AuthToken token = parseAuthToken(accessToken);
        if (token == null)
            return null;


        return getUserContext(token, headers);
    }

    default IUserContext getUserContext(AuthToken authToken, Map<String, Object> headers) {
        return FutureHelper.syncGet(getUserContextAsync(authToken, headers));
    }
}
