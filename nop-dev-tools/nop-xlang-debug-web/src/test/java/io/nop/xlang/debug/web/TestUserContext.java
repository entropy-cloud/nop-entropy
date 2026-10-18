/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.api.core.auth.IUserContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 测试用户上下文桩（固定字段，仅 userId 有区分度）。 */
public class TestUserContext implements IUserContext {
    private final String userId;
    private final Set<String> roles = new HashSet<>();

    public TestUserContext(String userId) {
        this.userId = userId;
    }

    @Override
    public String getSessionId() {
        return "test-session";
    }

    @Override
    public String getTenantId() {
        return "";
    }

    @Override
    public String getLocale() {
        return "zh-CN";
    }

    @Override
    public String getTimeZone() {
        return "Asia/Shanghai";
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getDeptId() {
        return "";
    }

    @Override
    public String getDeptName() {
        return "";
    }

    @Override
    public String getUserName() {
        return userId;
    }

    @Override
    public String getOpenId() {
        return "";
    }

    @Override
    public String getNickName() {
        return userId;
    }

    @Override
    public String getPrimaryRole() {
        return roles.isEmpty() ? null : roles.iterator().next();
    }

    @Override
    public boolean isUserInRole(String roleId) {
        return roles.contains(roleId);
    }

    @Override
    public boolean isUserInAnyRole(Collection<String> roleIds) {
        return roleIds != null && roleIds.stream().anyMatch(roles::contains);
    }

    @Override
    public Set<String> getRoles() {
        return roles;
    }

    @Override
    public void addRole(String roleId) {
        roles.add(roleId);
    }

    @Override
    public void removeRole(String roleId) {
        roles.remove(roleId);
    }

    @Override
    public String getAccessToken() {
        return "";
    }

    @Override
    public String getRefreshToken() {
        return "";
    }

    @Override
    public void setAccessToken(String accessToken) {
    }

    @Override
    public void setRefreshToken(String refreshToken) {
    }

    private final Map<String, Object> attrs = new HashMap<>();
    private volatile long lastAccessTime = System.currentTimeMillis();

    @Override
    public long getLastAccessTime() {
        return lastAccessTime;
    }

    @Override
    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    @Override
    public Map<String, Object> getAttrs() {
        return attrs;
    }

    @Override
    public Object getAttr(String name) {
        return attrs.get(name);
    }

    @Override
    public void setAttr(String name, Object value) {
        attrs.put(name, value);
    }

    @Override
    public boolean dirty() {
        return false;
    }

    @Override
    public void markDirty() {
        // 测试桩：无持久化，无需脏标记
    }

    @Override
    public void clearDirty() {
        // 测试桩：无持久化，无需脏标记
    }
}
