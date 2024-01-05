/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.auth;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.util.IDirtyFlagSupport;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 用户登录后的上下文对象，存放在全局缓存中
 */
public interface IUserContext extends IDirtyFlagSupport {
    static IUserContext get() {
        return (IUserContext) ContextProvider.getContextAttr("userContext");
    }

    static void set(IUserContext context) {
        ContextProvider.setContextAttr("userContext", context);
    }

    /**
     * 用户当前sessionId
     */
    String getSessionId();

    /**
     * 用户所属租户id
     */
    String getTenantId();

    /**
     * 用户当前选择的语言设置
     */
    String getLocale();

    String getTimeZone();

    /**
     * 用户唯一标识
     */
    String getUserId();

    /**
     * 用户所属部门id
     */
    String getDeptId();

    String getDeptName();

    /**
     * 登录用户名
     */
    String getUserName();

    String getOpenId();

    /**
     * 用户昵称，显示在界面上的用户姓名
     */
    String getNickName();

    /**
     * 用户选择的当前主角色，根据角色设置可能会限制系统具有不同的功能
     */
    String getPrimaryRole();

    boolean isUserInRole(String roleId);

    boolean isUserInAnyRole(Collection<String> roleIds);

    Set<String> getRoles();

    void addRole(String roleId);

    void removeRole(String roleId);

    String getAccessToken();

    String getRefreshToken();

    void setAccessToken(String accessToken);

    void setRefreshToken(String refreshToken);

    long getLastAccessTime();

    void setLastAccessTime(long lastAccessTime);

    /**
     * 附加属性
     */
    Map<String, Object> getAttrs();

    Object getAttr(String name);

    void setAttr(String name, Object value);

    /**
     * 是否已被修改需要被保存
     */
    boolean dirty();
}