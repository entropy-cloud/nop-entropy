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
import io.nop.credential.crypto.CredentialCipher;
import io.nop.credential.crypto.CredentialErrors;
import io.nop.credential.dao.entity.NopCredential;
import io.nop.credential.dao.entity.NopCredentialAuth;
import io.nop.credential.service.registry.DefaultCredentialTypeRegistry;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W11 Part B Phase 1：{@link CredentialProviderImpl} 明文出口 RBAC 授权收紧矩阵
 * （设计 §6.3 第 4/5/6 行，归属校验之后、解密之前）的端到端 AutoTest。
 *
 * <p>矩阵（scope=system 含存量 NULL 时）：无授权记录 = 一期行为不变（第 4 行）；
 * 有记录 + 无用户上下文 = 放行（第 5 行，服务级信任）；有记录 + 有用户上下文 =
 * 角色求交非空放行 / 空集拒绝（第 6 行，admin 不自动豁免）。user 级不叠加角色授权
 * （归属矩阵回归）；delFlag 先序；mask/testCredential 不做凭证级授权；
 * 引擎内部通道豁免（plan 2026-08-16-2321-1 Phase 1 Decision）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestCredentialProviderRbacAuth extends JunitBaseTestCase {

    private static final String KEY_ID = "testKey";
    private static final String PASSPHRASE = "test-passphrase-for-autotest";

    private static final String OWNER = "user-owner-1";
    private static final String OTHER = "user-other-2";
    private static final String ADMIN = "user-admin-3";

    private static final String ROLE_GRANTED = "role-op-x";
    private static final String ROLE_OTHER = "role-op-y";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    ITransactionTemplate txnTemplate;

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

        static RoleUserContext withRoles(String userId, String... roleIds) {
            return new RoleUserContext(userId, Set.of(roleIds));
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

    private CredentialProviderImpl newProvider() {
        CredentialProviderImpl provider = new CredentialProviderImpl();
        provider.setDaoProvider(daoProvider);
        provider.setCredentialCipher(testCipher());
        provider.setCredentialTypeRegistry(testRegistry());
        provider.setOrmTemplate(ormTemplate);
        provider.setTxnTemplate(txnTemplate);
        return provider;
    }

    private NopCredential saveCredential(String id, String scope, String ownerId, boolean deleted) {
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        NopCredential entity = dao.newEntity();
        entity.setCredentialId(id);
        entity.setName(id);
        entity.setTypeName("generic-secret");
        entity.setStatus("enabled");
        entity.setDelFlag(deleted ? (byte) 1 : (byte) 0);
        entity.setVersion(1);
        entity.setScope(scope);
        entity.setOwnerId(ownerId);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("secretValue", "sk-" + id);
        entity.setData(testCipher().encrypt(JSON.stringify(fields), KEY_ID));

        dao.saveEntityDirectly(entity);
        return entity;
    }

    /** seed 授权行经 dao 直插（plan Phase 1 Proof 约定的播种方式）。 */
    private void seedAuthRows(String credentialId, String... roleIds) {
        IEntityDao<NopCredentialAuth> dao = daoProvider.daoFor(NopCredentialAuth.class);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        for (String roleId : roleIds) {
            NopCredentialAuth auth = dao.newEntity();
            auth.setAuthId(io.nop.commons.util.StringHelper.generateUUID());
            auth.setCredentialId(credentialId);
            auth.setRoleId(roleId);
            auth.setCreateTime(now);
            auth.setCreatedBy(ADMIN);
            dao.saveEntityDirectly(auth);
        }
    }

    // ==================== 矩阵第 4 行：无授权记录 = 一期行为（增量兼容基线） ====================

    @Test
    public void row4NoAuthRecordUserContextStillAllowed() {
        saveCredential("cred-rbac-r4", "system", null, false);
        seedAuthRows("other-credential-not-this-one", ROLE_GRANTED); // 其他凭证的记录不影响本凭证

        IUserContext.set(RoleUserContext.plain(OTHER));
        ICredentialProvider provider = newProvider();
        CredentialData data = provider.getCredential("cred-rbac-r4");
        assertEquals("sk-cred-rbac-r4", data.getField("secretValue"),
                "no auth record for THIS credential keeps phase-1 behavior (row 4)");
        assertEquals("sk-cred-rbac-r4", provider.getCredentialData("cred-rbac-r4", "secretValue"));
    }

    // ==================== 矩阵第 5 行：有记录 + 无用户上下文 = 放行（服务级信任） ====================

    @Test
    public void row5AuthRecordNoUserContextAllowed() {
        saveCredential("cred-rbac-r5", "system", null, false);
        seedAuthRows("cred-rbac-r5", ROLE_GRANTED);

        IUserContext.set(null);
        ICredentialProvider provider = newProvider();
        assertEquals("sk-cred-rbac-r5", provider.getCredential("cred-rbac-r5").getField("secretValue"),
                "service-level trust: no user context bypasses the tightening (row 5)");
    }

    // ==================== 矩阵第 6 行：有记录 + 有用户上下文 = 角色求交 ====================

    @Test
    public void row6GrantedRoleHitAllowed() {
        saveCredential("cred-rbac-r6a", "system", null, false);
        seedAuthRows("cred-rbac-r6a", ROLE_OTHER, ROLE_GRANTED); // 多角色授权，命中其一即可

        IUserContext.set(RoleUserContext.withRoles(OTHER, ROLE_GRANTED));
        ICredentialProvider provider = newProvider();
        assertEquals("sk-cred-rbac-r6a", provider.getCredential("cred-rbac-r6a").getField("secretValue"),
                "user holding a granted role passes (row 6, non-empty intersection)");
    }

    @Test
    public void row6RoleMissRejectedFailClosed() {
        saveCredential("cred-rbac-r6b", "system", null, false);
        seedAuthRows("cred-rbac-r6b", ROLE_GRANTED);

        IUserContext.set(RoleUserContext.withRoles(OTHER, ROLE_OTHER));
        ICredentialProvider provider = newProvider();
        NopException ex = assertThrows(NopException.class, () -> provider.getCredential("cred-rbac-r6b"));
        assertEquals("nop.err.credential.role-not-granted", ex.getErrorCode(),
                "empty intersection must fail closed (row 6)");
        assertEquals("cred-rbac-r6b", ex.getParam("credentialId"));
        assertEquals(ROLE_GRANTED, ex.getParam("roleIds"),
                "rejection must carry the granted role set for the administrator to act on");

        NopException ex2 = assertThrows(NopException.class,
                () -> provider.getCredentialData("cred-rbac-r6b", "secretValue"));
        assertEquals("nop.err.credential.role-not-granted", ex2.getErrorCode(),
                "getCredentialData shares the same plaintext-exit check");
    }

    @Test
    public void row6AdminNotAutoExempt() {
        saveCredential("cred-rbac-r6c", "system", null, false);
        seedAuthRows("cred-rbac-r6c", ROLE_GRANTED);

        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin", "nop-admin"));
        ICredentialProvider provider = newProvider();
        NopException ex = assertThrows(NopException.class, () -> provider.getCredential("cred-rbac-r6c"));
        assertEquals("nop.err.credential.role-not-granted", ex.getErrorCode(),
                "admin role must NOT be auto-exempt from the tightening (admin needs an explicit grant)");
    }

    @Test
    public void row6AdminWithGrantedRolePasses() {
        saveCredential("cred-rbac-r6d", "system", null, false);
        seedAuthRows("cred-rbac-r6d", ROLE_GRANTED, "admin");

        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        ICredentialProvider provider = newProvider();
        assertEquals("sk-cred-rbac-r6d", provider.getCredential("cred-rbac-r6d").getField("secretValue"),
                "admin granted explicitly (grant own role) passes");
    }

    // ==================== user 级凭证不受授权记录影响（归属矩阵回归） ====================

    @Test
    public void userScopeUnaffectedByAuthRecords() {
        saveCredential("cred-rbac-user", "user", OWNER, false);
        seedAuthRows("cred-rbac-user", ROLE_GRANTED); // user 级不叠加角色授权：记录存在也不生效

        ICredentialProvider provider = newProvider();
        IUserContext.set(RoleUserContext.plain(OWNER));
        assertEquals("sk-cred-rbac-user", provider.getCredential("cred-rbac-user").getField("secretValue"),
                "owner passes by ownership alone; auth records are not consulted for user-scope");

        IUserContext.set(RoleUserContext.withRoles(OTHER, ROLE_GRANTED));
        NopException ex = assertThrows(NopException.class, () -> provider.getCredential("cred-rbac-user"));
        assertEquals("nop.err.credential.owner-only", ex.getErrorCode(),
                "non-owner rejected by ownership matrix even when holding a granted role");
    }

    // ==================== delFlag 先序（已删凭证不进授权判定） ====================

    @Test
    public void deletedReportsDeletedBeforeAuthJudgment() {
        saveCredential("cred-rbac-del", "system", null, true);
        seedAuthRows("cred-rbac-del", ROLE_GRANTED);

        IUserContext.set(RoleUserContext.withRoles(OTHER, ROLE_OTHER));
        ICredentialProvider provider = newProvider();
        NopException ex = assertThrows(NopException.class, () -> provider.getCredential("cred-rbac-del"));
        assertEquals("nop.err.credential.deleted", ex.getErrorCode(),
                "deleted check precedes the RBAC judgment (fail-closed, no auth-record query semantics leaked)");
    }

    // ==================== mask/testCredential 不做凭证级授权（§6.3） ====================

    @Test
    public void maskAndTestSkipCredentialLevelAuth() {
        saveCredential("cred-rbac-mask", "system", null, false);
        seedAuthRows("cred-rbac-mask", ROLE_GRANTED);

        IUserContext.set(RoleUserContext.withRoles(OTHER, ROLE_OTHER)); // 未命中授权角色
        ICredentialProvider provider = newProvider();

        MaskedCredential masked = provider.mask("cred-rbac-mask");
        assertNotNull(masked, "mask is a management-plane action: no credential-level auth check");

        TestResult result = provider.testCredential("cred-rbac-mask");
        assertNotNull(result, "testCredential is a management-plane action: no credential-level auth check");
    }

    // ==================== 引擎内部通道豁免（Phase 1 Decision 裁定覆盖对象） ====================

    @Test
    public void engineGetDecryptedFieldsExemptFromAuthCheck() {
        saveCredential("cred-rbac-eng1", "system", null, false);
        seedAuthRows("cred-rbac-eng1", ROLE_GRANTED);

        // 用户上下文在线程上（beginOAuthFlow 可达路径同构）且未命中授权角色：引擎读通道仍放行
        IUserContext.set(RoleUserContext.withRoles(OTHER, ROLE_OTHER));
        CredentialProviderImpl provider = newProvider();
        Map<String, Object> fields = provider.engineGetDecryptedFields("cred-rbac-eng1");
        assertEquals("sk-cred-rbac-eng1", fields.get("secretValue"),
                "engine read channel is exempt from the RBAC tightening (adjudicated: no plaintext egress)");
    }

    @Test
    public void engineUpdateChannelsExemptFromAuthCheck() {
        saveCredential("cred-rbac-eng2", "system", null, false);
        seedAuthRows("cred-rbac-eng2", ROLE_GRANTED);

        IUserContext.set(RoleUserContext.withRoles(OTHER, ROLE_OTHER));
        CredentialProviderImpl provider = newProvider();

        // engineUpdateTokenFields（保留字段合并写）
        Map<String, Object> tokenFields = new LinkedHashMap<>();
        tokenFields.put("accessToken", "at-engine");
        Map<String, Object> afterToken = provider.engineUpdateTokenFields("cred-rbac-eng2", tokenFields);
        assertEquals("at-engine", afterToken.get("accessToken"));

        // engineUpdateInLock（通用行锁写通道，saveCredential 分组写共用入口）
        Map<String, Object> afterUpdate = provider.engineUpdateInLock("cred-rbac-eng2",
                current -> {
                    Map<String, Object> merged = new LinkedHashMap<>(current);
                    merged.put("secretValue", "sk-updated-in-lock");
                    return merged;
                });
        assertEquals("sk-updated-in-lock", afterUpdate.get("secretValue"),
                "engine lock-write channel is exempt (entry actions are ownership/admin gated)");

        // 引擎通道豁免不改变明文出口收紧：getCredential 对未命中用户仍拒绝
        NopException ex = assertThrows(NopException.class, () -> provider.getCredential("cred-rbac-eng2"));
        assertEquals("nop.err.credential.role-not-granted", ex.getErrorCode());
    }
}
