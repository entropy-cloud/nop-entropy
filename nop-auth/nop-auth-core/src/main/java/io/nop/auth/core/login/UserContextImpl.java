/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core.login;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ExtensibleBean;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class UserContextImpl extends ExtensibleBean implements IUserContext {
    private String sessionId;
    private String tenantId;
    private String locale;
    private String timeZone;
    private String userId;
    private String openId;
    private String userName;
    private String nickName;
    private String primaryRole;
    private String deptId;
    private Set<String> roles;

    private boolean dirty;
    private String accessToken;
    private String refreshToken;

    @Override
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        if (!Objects.equals(sessionId, this.sessionId)) {
            this.sessionId = sessionId;
            dirty = true;
        }
    }

    @Override
    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        if (!Objects.equals(tenantId, this.tenantId)) {
            this.tenantId = tenantId;
            dirty = true;
        }
    }

    @Override
    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        if (!Objects.equals(locale, this.locale)) {
            this.locale = locale;
            dirty = true;
        }
    }

    @Override
    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        if (!Objects.equals(timeZone, this.timeZone)) {
            this.timeZone = timeZone;
            dirty = true;
        }
    }

    @Override
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        if (!Objects.equals(userId, this.userId)) {
            this.userId = userId;
            dirty = true;
        }
    }

    @Override
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        if (!Objects.equals(userName, this.userName)) {
            this.userName = userName;
            dirty = true;
        }
    }

    @Override
    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        if (!Objects.equals(nickName, this.nickName)) {
            this.nickName = nickName;
            dirty = true;
        }
    }

    @Override
    public String getPrimaryRole() {
        return primaryRole;
    }

    public void setPrimaryRole(String primaryRole) {
        if (!Objects.equals(primaryRole, this.primaryRole)) {
            this.primaryRole = primaryRole;
            dirty = true;
        }
    }

    @Override
    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        if (!Objects.equals(deptId, this.deptId)) {
            this.deptId = deptId;
            dirty = true;
        }
    }

    @Override
    public String getOpenId() {
        return openId;
    }

    public void setOpenId(String openId) {
        if (!Objects.equals(openId, this.openId)) {
            this.openId = openId;
            dirty = true;
        }
    }

    @Override
    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        if (!Objects.equals(roles, this.roles)) {
            this.roles = roles;
            dirty = true;
        }
    }

    @Override
    public String getAccessToken() {
        return accessToken;
    }

    @Override
    public void setAccessToken(String accessToken) {
        markDirty();
        this.accessToken = accessToken;
    }

    @Override
    public String getRefreshToken() {
        return refreshToken;
    }

    @Override
    public void setRefreshToken(String refreshToken) {
        markDirty();
        this.refreshToken = refreshToken;
    }

    @Override
    public synchronized void addRole(String roleId) {
        if (roles == null)
            roles = new HashSet<>();
        if (roles.add(roleId)) {
            dirty = true;
        }
    }

    @Override
    public synchronized void removeRole(String roleId) {
        if (roles == null)
            return;
        if (roles.remove(roleId)) {
            dirty = true;
        }
    }

    @Override
    public synchronized void setAttrs(Map<String, Object> attrs) {
        if (!Objects.equals(attrs, this.getAttrs())) {
            super.setAttrs(attrs);
            dirty = true;
        }
    }

    @Override
    public synchronized void setAttr(String name, Object value) {
        if (!Objects.equals(value, getAttr(name))) {
            super.setAttr(name, value);
            dirty = true;
        }
    }

    @Override
    public boolean dirty() {
        return dirty;
    }

    @Override
    public boolean isUserInRole(String roleId) {
        if (Objects.equals(roleId, primaryRole))
            return true;

        return roles != null && roles.contains(roleId);
    }

    @Override
    public boolean isUserInAnyRole(Collection<String> roleIds) {
        if (roleIds == null)
            return true;
        for (String roleId : roleIds) {
            if (isUserInRole(roleId))
                return true;
        }
        return false;
    }

    @Override
    public void clearDirty() {
        dirty = false;
    }

    @Override
    public void markDirty() {
        dirty = true;
    }
}