/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.login;

import io.nop.api.core.auth.IUserContext;

import java.util.concurrent.CompletionStage;

public interface IUserContextCache {
    CompletionStage<IUserContext> getUserContextAsync(String sessionId);

    CompletionStage<Void> saveUserContextAsync(IUserContext userContext);

    CompletionStage<Void> removeUserContextAsync(SessionInfo sessionInfo);

    CompletionStage<String> getUserSessionId(String userName);

    /**
     * 获取用户登录失败次数。如果已经超过次数，则禁止再尝试登录
     */
    int getLoginFailCountForUser(String userName);

    int getLoginFailCountForIp(String ip);

    void setLoginFailCountForUser(String userName, int count);

    void resetLoginFailCountForUser(String userName);

    void resetLoginFailCountForIp(String ip);

    void setLoginFailCountForIp(String ip, int count);

    /**
     * 得到缓存的验证码
     *
     * @param key
     * @return
     */
    String getVerifyCode(String key);

    void setVerifyCode(String key, String code);
}