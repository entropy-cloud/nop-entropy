package io.nop.auth.core.filter;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.AppConfig;
import io.nop.auth.core.AuthCoreConfigs;
import io.nop.auth.core.AuthCoreConstants;
import io.nop.http.api.server.IHttpServerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 servicePublic / SYS 合成 / 租户头契约（H-3，DR-1b）。
 * 纯单元测试，使用默认 SimpleConfigProvider 操控配置，无需 IoC 容器或数据库。
 */
public class TestSysUserContextContract {

    @AfterEach
    public void resetTrust() {
        AppConfig.getConfigProvider().updateConfigValue(AuthCoreConfigs.CFG_AUTH_TRUST_FORWARDED_TENANT, false);
    }

    @Test
    public void testHeaderTenantNotTrustedByDefault() {
        assertEquals(Boolean.FALSE, AuthCoreConfigs.CFG_AUTH_TRUST_FORWARDED_TENANT.getDefaultValue());

        AuthHttpServerFilter filter = newFilter();
        Map<String, String> headers = new HashMap<>();
        headers.put(ApiConstants.HEADER_TENANT, "tenant-from-client");

        IUserContext sys = filter.newSysUserContext(contextWithHeaders(headers));

        // 客户端 HEADER_TENANT 被忽略，租户为 null（无租户注入）
        assertNull(sys.getTenantId());
        assertEquals(AuthCoreConstants.USER_ID_SYS, sys.getUserId());
    }

    @Test
    public void testForwardedTenantTrustedOnlyWhenEnabled() {
        AuthHttpServerFilter filter = newFilter();
        Map<String, String> headers = new HashMap<>();
        headers.put(ApiConstants.HEADER_TENANT, "ignored");
        headers.put(AuthCoreConstants.HEADER_X_FORWARDED_TENANT, "trusted-tenant");

        // 默认不信任
        assertNull(filter.newSysUserContext(contextWithHeaders(headers)).getTenantId());

        // 启用可信代理转发后采纳 X-Forwarded-Tenant
        AppConfig.getConfigProvider().updateConfigValue(AuthCoreConfigs.CFG_AUTH_TRUST_FORWARDED_TENANT, true);
        assertEquals("trusted-tenant",
                filter.newSysUserContext(contextWithHeaders(headers)).getTenantId());
    }

    @Test
    public void testSysIsAnonymousPrincipal() {
        AuthHttpServerFilter filter = newFilter();
        IUserContext sys = filter.newSysUserContext(contextWithHeaders(new HashMap<>()));

        // 匿名主体：无管理员角色，配合 Phase 2（admin-skip 默认关闭）无法绕过权限检查
        assertFalse(sys.isUserInRole(AuthCoreConstants.ROLE_ADMIN));
        assertFalse(sys.isUserInRole(AuthCoreConstants.ROLE_NOP_ADMIN));
        assertTrue(sys.getRoles() == null || sys.getRoles().isEmpty());
    }

    @Test
    public void testServicePublicDefaultsToFalse() {
        // 无显式配置时 servicePublic 默认 false：未登录服务路径不合成 SYS，而是被拒绝（非静默放行）
        assertFalse(new AuthFilterConfig().isServicePublic());
    }

    private AuthHttpServerFilter newFilter() {
        AuthHttpServerFilter filter = new AuthHttpServerFilter();
        filter.setConfig(new AuthFilterConfig());
        return filter;
    }

    private IHttpServerContext contextWithHeaders(Map<String, String> headers) {
        return (IHttpServerContext) Proxy.newProxyInstance(
                IHttpServerContext.class.getClassLoader(),
                new Class[]{IHttpServerContext.class},
                (proxy, method, args) -> {
                    if ("getRequestStringHeader".equals(method.getName())) {
                        return headers.get((String) args[0]);
                    }
                    return method.getDefaultValue();
                });
    }
}
