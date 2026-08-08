/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.auth;

import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.IVariableScope;
import io.nop.auth.core.AuthCoreConstants;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.service.NopAuthConstants;
import io.nop.auth.service.sitemap.SiteMapProviderImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SKIP_CHECK_FOR_ADMIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 admin-skip 默认值一致性（H-2）：
 * {@code nop.auth.skip-check-for-admin} 只有唯一默认值 false（来自 IConfigReference），
 * 管理员默认不再绕过权限检查。
 * 纯单元测试，使用默认 SimpleConfigProvider 操控配置，无需 IoC 容器或数据库。
 */
public class TestDefaultActionAuthChecker {

    @AfterEach
    public void resetConfig() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_AUTH_SKIP_CHECK_FOR_ADMIN, false);
    }

    @Test
    public void testConfigDefaultIsFalse() {
        // IConfigReference 是唯一来源，默认 false
        assertEquals(Boolean.FALSE, CFG_AUTH_SKIP_CHECK_FOR_ADMIN.getDefaultValue());
        assertEquals(false, CFG_AUTH_SKIP_CHECK_FOR_ADMIN.get());
    }

    @Test
    public void testAdminNotSkippedByDefault() {
        DefaultActionAuthChecker checker = newChecker();
        ISecurityContext adminCtx = securityContext(adminUser());

        // 默认 skip=false：管理员不绕过，委托给 siteMap（此处返回 false）
        assertEquals(false, CFG_AUTH_SKIP_CHECK_FOR_ADMIN.get());
        assertFalse(checker.isPermitted("some-perm", adminCtx));
    }

    @Test
    public void testAdminSkippedWhenExplicitlyEnabled() {
        DefaultActionAuthChecker checker = newChecker();
        ISecurityContext adminCtx = securityContext(adminUser());

        AppConfig.getConfigProvider().updateConfigValue(CFG_AUTH_SKIP_CHECK_FOR_ADMIN, true);
        assertTrue(CFG_AUTH_SKIP_CHECK_FOR_ADMIN.get());
        // 显式开启后管理员绕过权限检查
        assertTrue(checker.isPermitted("some-perm", adminCtx));

        // 非管理员即使开启也不绕过（委托 siteMap 返回 false）
        assertFalse(checker.isPermitted("some-perm", securityContext(normalUser())));
    }

    private DefaultActionAuthChecker newChecker() {
        DefaultActionAuthChecker checker = new DefaultActionAuthChecker();
        // stub：始终返回 false，以区分“被跳过”与“委托”
        checker.setSiteMapProvider(new SiteMapProviderImpl() {
            @Override
            public boolean isPermitted(String permission, ISecurityContext context) {
                return false;
            }
        });
        return checker;
    }

    private UserContextImpl adminUser() {
        UserContextImpl user = new UserContextImpl();
        user.setRoles(Collections.singleton(NopAuthConstants.ROLE_ADMIN));
        return user;
    }

    private UserContextImpl normalUser() {
        UserContextImpl user = new UserContextImpl();
        user.setRoles(Collections.singleton(AuthCoreConstants.ROLE_USER));
        return user;
    }

    private ISecurityContext securityContext(IUserContext userContext) {
        return new ISecurityContext() {
            @Override
            public IVariableScope getEvalScope() {
                return null;
            }

            @Override
            public IUserContext getUserContext() {
                return userContext;
            }
        };
    }
}
