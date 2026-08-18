/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.crypto.ITextCipher;
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.credential.api.crypto.ICredentialKeyProvider;
import io.nop.credential.api.registry.ICredentialTypeRegistry;
import io.nop.credential.config.CredentialConfigs;
import io.nop.credential.crypto.CredentialCipher;
import io.nop.credential.dao.entity.NopCredential;
import io.nop.credential.service.registry.DefaultCredentialTypeRegistry;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * W11 Phase 1：{@link CredentialProviderImpl} 归属校验矩阵（设计 §5.3 per-method，解密之前、
 * fail-closed、先序 delFlag）的端到端 AutoTest。
 *
 * <p>矩阵（scope=user 时）：getCredential/getCredentialData = owner 唯一（无上下文/非 owner/
 * 管理员一律拒绝）；mask/testCredential = owner+admin；scope=system（含存量 NULL）全放行。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestCredentialProviderOwnership extends JunitBaseTestCase {

    private static final String KEY_ID = "testKey";
    private static final String PASSPHRASE = "test-passphrase-for-autotest";

    private static final String OWNER = "user-owner-1";
    private static final String OTHER = "user-other-2";
    private static final String ADMIN = "user-admin-3";

    @Inject
    IDaoProvider daoProvider;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
    }

    // ==================== 测试基建 ====================

    /** 最小角色感知 IUserContext（nop-credential 不依赖 nop-auth，无法用 UserContextImpl）。 */
    static final class RoleUserContext implements IUserContext {
        private final String userId;
        private final Set<String> roles;

        RoleUserContext(String userId, Set<String> roles) {
            this.userId = userId;
            this.roles = roles;
        }

        static RoleUserContext plain(String userId) {
            return new RoleUserContext(userId, Collections.emptySet());
        }

        static RoleUserContext admin(String userId) {
            return new RoleUserContext(userId, Set.of("admin"));
        }

        @Override
        public String getUserId() {
            return userId;
        }

        @Override
        public String getUserName() {
            return userId;
        }

        @Override
        public boolean isUserInRole(String roleId) {
            return roles.contains(roleId);
        }

        @Override
        public boolean isUserInAnyRole(Collection<String> roleIds) {
            for (String role : roleIds) {
                if (roles.contains(role)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Set<String> getRoles() {
            return roles;
        }

        @Override
        public void addRole(String roleId) {
        }

        @Override
        public void removeRole(String roleId) {
        }

        @Override
        public String getAccessToken() {
            return null;
        }

        @Override
        public String getRefreshToken() {
            return null;
        }

        @Override
        public void setAccessToken(String accessToken) {
        }

        @Override
        public void setRefreshToken(String refreshToken) {
        }

        @Override
        public long getLastAccessTime() {
            return 0;
        }

        @Override
        public void setLastAccessTime(long lastAccessTime) {
        }

        @Override
        public boolean dirty() {
            return false;
        }

        @Override
        public void markDirty() {
        }

        @Override
        public void clearDirty() {
        }

        @Override
        public String getSessionId() {
            return null;
        }

        @Override
        public String getTenantId() {
            return null;
        }

        @Override
        public String getLocale() {
            return null;
        }

        @Override
        public String getTimeZone() {
            return null;
        }

        @Override
        public String getDeptId() {
            return null;
        }

        @Override
        public String getDeptName() {
            return null;
        }

        @Override
        public String getOpenId() {
            return null;
        }

        @Override
        public String getNickName() {
            return null;
        }

        @Override
        public String getPrimaryRole() {
            return null;
        }

        @Override
        public Map<String, Object> getAttrs() {
            return Collections.emptyMap();
        }

        @Override
        public Object getAttr(String name) {
            return null;
        }

        @Override
        public void setAttr(String name, Object value) {
        }
    }

    private ICredentialKeyProvider testKeyProvider() {
        final Map<String, ITextCipher> map = new LinkedHashMap<>();
        map.put(KEY_ID, new AESTextCipher().encKey(PASSPHRASE));
        return new ICredentialKeyProvider() {
            @Override
            public String getActiveKeyId() {
                return KEY_ID;
            }

            @Override
            public ITextCipher getKey(String keyId) {
                return map.get(keyId);
            }

            @Override
            public Set<String> getKeyIds() {
                return Collections.unmodifiableSet(map.keySet());
            }
        };
    }

    private CredentialCipher testCipher() {
        CredentialCipher cipher = new CredentialCipher();
        cipher.setKeyProvider(testKeyProvider());
        return cipher;
    }

    private ICredentialTypeRegistry testRegistry() {
        DefaultCredentialTypeRegistry registry = new DefaultCredentialTypeRegistry();
        registry.init();
        return registry;
    }

    private ICredentialProvider newProvider() {
        CredentialProviderImpl provider = new CredentialProviderImpl();
        provider.setDaoProvider(daoProvider);
        provider.setCredentialCipher(testCipher());
        provider.setCredentialTypeRegistry(testRegistry());
        return provider;
    }

    private NopCredential saveCredential(String id, String scope, String ownerId, boolean deleted) {
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        NopCredential entity = dao.newEntity();
        entity.setCredentialId(id);
        entity.setName(id);
        entity.setTypeName("openai-api-key");
        entity.setStatus("enabled");
        entity.setDelFlag(deleted ? (byte) 1 : (byte) 0);
        entity.setVersion(1);
        entity.setScope(scope);
        entity.setOwnerId(ownerId);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("apiKey", "sk-" + id);
        entity.setData(testCipher().encrypt(JSON.stringify(fields), KEY_ID));

        dao.saveEntityDirectly(entity);
        return entity;
    }

    private static NopException assertOwnershipRejected(Runnable call) {
        NopException ex = assertThrows(NopException.class, call::run);
        return ex;
    }

    // ==================== 明文出口：owner 唯一 ====================

    @Test
    public void userScopeOwnerGetsPlaintext() {
        saveCredential("cred-own-1", "user", OWNER, false);
        IUserContext.set(RoleUserContext.plain(OWNER));

        ICredentialProvider provider = newProvider();
        CredentialData data = provider.getCredential("cred-own-1");
        assertEquals("sk-cred-own-1", data.getField("apiKey"));
        assertEquals("sk-cred-own-1", provider.getCredentialData("cred-own-1", "apiKey"));
    }

    @Test
    public void userScopeNonOwnerRejected() {
        saveCredential("cred-own-2", "user", OWNER, false);
        IUserContext.set(RoleUserContext.plain(OTHER));

        ICredentialProvider provider = newProvider();
        NopException ex = assertOwnershipRejected(() -> provider.getCredential("cred-own-2"));
        assertEquals("nop.err.credential.owner-only", ex.getErrorCode());
    }

    @Test
    public void userScopeAdminNotExemptForPlaintext() {
        saveCredential("cred-own-3", "user", OWNER, false);
        IUserContext.set(RoleUserContext.admin(ADMIN));

        ICredentialProvider provider = newProvider();
        NopException ex = assertOwnershipRejected(() -> provider.getCredential("cred-own-3"));
        assertEquals("nop.err.credential.owner-only", ex.getErrorCode(),
                "admin must NOT be exempt on plaintext exit (owner-only)");
        NopException ex2 = assertOwnershipRejected(() -> provider.getCredentialData("cred-own-3", "apiKey"));
        assertEquals("nop.err.credential.owner-only", ex2.getErrorCode());
    }

    @Test
    public void userScopeNoUserContextRejected() {
        saveCredential("cred-own-4", "user", OWNER, false);
        // 无用户上下文（后台任务/服务间调用）：一律拒绝（owner 消亡同 fail-closed）
        IUserContext.set(null);

        ICredentialProvider provider = newProvider();
        NopException ex = assertOwnershipRejected(() -> provider.getCredential("cred-own-4"));
        assertEquals("nop.err.credential.owner-only", ex.getErrorCode());
    }

    // ==================== system 级（含存量 NULL scope）零变化（一期回归） ====================

    @Test
    public void nullScopeLegacyRowNoContextStillWorks() {
        saveCredential("cred-sys-null", null, null, false);
        IUserContext.set(null);

        ICredentialProvider provider = newProvider();
        assertEquals("sk-cred-sys-null", provider.getCredential("cred-sys-null").getField("apiKey"));
        assertEquals("sk-cred-sys-null", provider.getCredentialData("cred-sys-null", "apiKey"));
    }

    @Test
    public void systemScopeNonAdminUserStillWorks() {
        saveCredential("cred-sys-explicit", "system", null, false);
        IUserContext.set(RoleUserContext.plain(OTHER));

        ICredentialProvider provider = newProvider();
        assertEquals("sk-cred-sys-explicit", provider.getCredential("cred-sys-explicit").getField("apiKey"),
                "scope=system keeps phase-1 behavior for any caller (W7-successor chain unchanged)");
    }

    // ==================== 管理面：mask/test = owner + admin ====================

    @Test
    public void maskAllowsOwnerAndAdmin() {
        saveCredential("cred-mask-own", "user", OWNER, false);

        ICredentialProvider provider = newProvider();
        IUserContext.set(RoleUserContext.plain(OWNER));
        MaskedCredential byOwner = provider.mask("cred-mask-own");
        assertNotNull(byOwner);

        IUserContext.set(RoleUserContext.admin(ADMIN));
        MaskedCredential byAdmin = provider.mask("cred-mask-own");
        assertNotNull(byAdmin);
        assertEquals("****", byAdmin.getFields().get("apiKey"));
    }

    @Test
    public void maskRejectsOthersAndNoContext() {
        saveCredential("cred-mask-other", "user", OWNER, false);

        ICredentialProvider provider = newProvider();
        IUserContext.set(RoleUserContext.plain(OTHER));
        NopException ex = assertOwnershipRejected(() -> provider.mask("cred-mask-other"));
        assertEquals("nop.err.credential.owner-or-admin", ex.getErrorCode());

        IUserContext.set(null);
        NopException ex2 = assertOwnershipRejected(() -> provider.mask("cred-mask-other"));
        assertEquals("nop.err.credential.owner-or-admin", ex2.getErrorCode());
    }

    @Test
    public void testCredentialAllowsOwnerAndAdminRejectsOthers() {
        saveCredential("cred-test-own", "user", OWNER, false);

        ICredentialProvider provider = newProvider();
        IUserContext.set(RoleUserContext.plain(OWNER));
        TestResult byOwner = provider.testCredential("cred-test-own");
        assertNotNull(byOwner);

        IUserContext.set(RoleUserContext.admin(ADMIN));
        assertNotNull(provider.testCredential("cred-test-own"));

        IUserContext.set(RoleUserContext.plain(OTHER));
        NopException ex = assertOwnershipRejected(() -> provider.testCredential("cred-test-own"));
        assertEquals("nop.err.credential.owner-or-admin", ex.getErrorCode());
    }

    // ==================== delFlag 先序（不泄露归属） ====================

    @Test
    public void deletedCredentialReportsDeletedBeforeOwnership() {
        saveCredential("cred-del-first", "user", OWNER, true);

        ICredentialProvider provider = newProvider();
        IUserContext.set(RoleUserContext.plain(OWNER));
        NopException byOwner = assertThrows(NopException.class, () -> provider.getCredential("cred-del-first"));
        assertEquals("nop.err.credential.deleted", byOwner.getErrorCode());

        IUserContext.set(RoleUserContext.plain(OTHER));
        NopException byOther = assertThrows(NopException.class, () -> provider.getCredential("cred-del-first"));
        assertEquals("nop.err.credential.deleted", byOther.getErrorCode(),
                "deleted check precedes ownership: non-owner sees DELETED, not ownership info");
    }

    // ==================== 管理员判定配置 ====================

    @Test
    public void adminRolesConfigHonored() {
        saveCredential("cred-cfg-admin", "user", OWNER, false);

        // 自定义管理员角色名（nop-auth 事实管理员角色名 nop-admin）
        AppConfig.getConfigProvider().updateConfigValue(
                CredentialConfigs.CFG_CREDENTIAL_ADMIN_ROLES, "nop-admin");
        try {
            ICredentialProvider provider = newProvider();
            IUserContext.set(new RoleUserContext(OTHER, Set.of("nop-admin")));
            assertNotNull(provider.mask("cred-cfg-admin"),
                    "custom admin role (nop-admin) must pass mask");

            IUserContext.set(new RoleUserContext(OTHER, Set.of("admin")));
            NopException ex = assertThrows(NopException.class, () -> provider.mask("cred-cfg-admin"));
            assertEquals("nop.err.credential.owner-or-admin", ex.getErrorCode(),
                    "default admin role no longer grants access when config overridden");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(
                    CredentialConfigs.CFG_CREDENTIAL_ADMIN_ROLES, null);
        }
    }

    @Test
    public void contextProviderThreadContextUsed() {
        // IUserContext.get() 经 ContextProvider 线程上下文取当前用户（平台标准身份传播）
        saveCredential("cred-ctx", "user", OWNER, false);
        RoleUserContext ctx = RoleUserContext.plain(OWNER);
        IUserContext.set(ctx);

        assertEquals(ctx, ContextProvider.getContextAttr("userContext"),
                "IUserContext.set must publish to ContextProvider thread context");

        ICredentialProvider provider = newProvider();
        assertEquals("sk-cred-ctx", provider.getCredential("cred-ctx").getField("apiKey"),
                "provider must resolve the user context via IUserContext.get()");
    }
}
