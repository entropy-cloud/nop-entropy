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

import java.util.List;
import java.util.Map;

public interface ILoginSessionStore {

    SessionInfo getSessionInfoForUser(String userName);

    /**
     * 返回sessionId
     */
    String saveSession(IUserContext userContext, LoginRequest request,
                       Map<String, Object> headers);

    /**
     * 标记session的状态为已退出，并返回session所对应的userName
     *
     * @param sessionId  session的唯一键
     * @param logoutType 退出类型
     * @param logoutUser 执行logout操作的用户。如果是点击退出按钮，则为当前用户。如果是管理员主动kill在线用户，则为管理员的userName
     */
    void logoutSession(String sessionId, int logoutType, String logoutUser);

    /**
     * 得到指定用户当前未关闭的session
     *
     * @param userName 用户名
     */
    List<String> getActionSessions(String userName);
}