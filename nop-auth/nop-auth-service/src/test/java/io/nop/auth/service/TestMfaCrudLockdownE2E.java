/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthMfaTrustedDevice;
import io.nop.auth.dao.entity.NopAuthOpLog;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthRoleMfaPolicy;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.entity.NopAuthMfaTrustedDeviceBizModel;
import io.nop.auth.service.mfa.MfaTrustedDeviceManager;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.api.core.util.FutureHelper.syncGet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2-followup-1 Phase 1 E2E：MFA 敏感表（五张 + 同族瞬态码表三张）通用 CRUD 写路径收口——
 * 从 GraphQL mutation 入口断言写动作被<b>显式拒绝</b>（错误码断言，非静默失败）。
 * <ul>
 *   <li>每张收紧表至少一个 mutation（save）经 GraphQL 入口断言
 *       {@code ERR_AUTH_MFA_CRUD_DISABLED}。</li>
 *   <li>代表表（Setting）全继承动作面枚举拒绝：update/delete/batchDelete/batchUpdate/
 *       batchModify/saveOrUpdate/updateByQuery/deleteByQuery/copyForNew/recoverDeleted。</li>
 *   <li>TrustedDevice 管理端 delete carve-out：非 admin 被拒（requireAdmin）；admin 删除 =
 *       manager 物理删除 + revoke 族审计事件落库。</li>
 *   <li>RoleMfaPolicy 专项写路径（saveMfaPolicy/removeMfaPolicy）收紧后行为不变
 *       （requireAdmin/校验/审计面回归）。</li>
 * </ul>
 * 接线验证（登记/豁免/撤销链、bind/confirm/unbind 族专项入口不受收紧影响）由
 * {@code TestTrustedDeviceE2E} 等既有测试零修改回归钉定（全量跑测验证）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestMfaCrudLockdownE2E extends JunitBaseTestCase {

    private static final String TENANT_ID = "0";

    /** 八张收紧表（五敏感表 + 三同族瞬态码表——边界裁定：纳入）。 */
    private static final String[] GUARDED_BIZ_OBJS = {
            "NopAuthMfaSetting", "NopAuthMfaCredential", "NopAuthMfaTrustedDevice",
            "NopAuthRoleMfaPolicy", "NopAuthMfaRecoveryCode",
            "NopAuthMfaChallenge", "NopAuthSmsCode", "NopAuthEmailCode"
    };

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    MfaTrustedDeviceManager trustedDeviceManager;

    @Inject
    io.nop.auth.core.password.IPasswordEncoder passwordEncoder;

    @AfterEach
    void clearUserContext() {
        IUserContext.set(null);
    }

    // ===================== 收紧面：GraphQL mutation 入口显式拒绝 =====================

    @Test
    public void testAllGuardedTablesRejectGenericSave() {
        String userId = "lock-save-user";
        saveUser(userId);
        for (String bizObj : GUARDED_BIZ_OBJS) {
            ApiResponse<?> resp = rpcMutation(bizObj + "__save", saveData(bizObj), adminCtx(userId, "sess-lock"));
            assertFalse(resp.isOk(), bizObj + "__save must be rejected");
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_CRUD_DISABLED.getErrorCode(), resp.getCode(),
                    bizObj + "__save must fail with explicit error code, got: " + resp.getCode());
        }
    }

    @Test
    public void testSettingInheritedMutationSurfaceAllRejected() {
        String userId = "lock-surface-user";
        saveUser(userId);
        UserContextImpl ctx = adminCtx(userId, "sess-surface");

        assertCrudDisabled("NopAuthMfaSetting__update", Map.of("data", Map.of("userId", userId, "mfaType", "sms")), ctx);
        assertCrudDisabled("NopAuthMfaSetting__delete", Map.of("id", userId), ctx);
        assertCrudDisabled("NopAuthMfaSetting__batchDelete", Map.of("ids", Set.of(userId)), ctx);
        assertCrudDisabled("NopAuthMfaSetting__batchUpdate",
                Map.of("ids", Set.of(userId), "data", Map.of("remark", "x")), ctx);
        assertCrudDisabled("NopAuthMfaSetting__batchModify",
                Map.of("data", List.of(Map.of("userId", userId, "remark", "x"))), ctx);
        assertCrudDisabled("NopAuthMfaSetting__saveOrUpdate", Map.of("data", Map.of("userId", userId)), ctx);
        // 查询体仅作占位（守卫在校验前拒绝，不触达 DB）
        assertCrudDisabled("NopAuthMfaSetting__updateByQuery",
                Map.of("query", Map.of(), "data", Map.of("remark", "x")), ctx);
        assertCrudDisabled("NopAuthMfaSetting__deleteByQuery", Map.of("query", Map.of()), ctx);
        assertCrudDisabled("NopAuthMfaSetting__copyForNew", Map.of("data", Map.of("userId", userId)), ctx);
        assertCrudDisabled("NopAuthMfaSetting__recoverDeleted", Map.of("id", userId), ctx);

        // 无静默跳过：拒绝后行未被写入
        assertNull(daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId),
                "rejected save must not persist any row");
    }

    @Test
    public void testTrustedDeviceGenericMutationsRejectedButAdminDeleteCarveOut() {
        String userId = "lock-td-user";
        saveUser(userId);
        // 通用 mutation 拒绝（含伪造豁免行路径 D6-1：save 注入任意 userId/deviceHash/expireAt）
        UserContextImpl admin = adminCtx(userId, "sess-td");
        assertCrudDisabled("NopAuthMfaTrustedDevice__save",
                Map.of("data", Map.of("userId", "victim-user", "deviceHash", "deadbeef",
                        "expireAt", "2030-01-01 00:00:00")), admin);
        assertCrudDisabled("NopAuthMfaTrustedDevice__update",
                Map.of("data", Map.of("sid", "not-exist", "expireAt", "2030-01-01 00:00:00")), admin);

        // carve-out：经 manager 登记一行真实可信设备，admin delete 物理删除 + 审计
        Map<String, Object> headers = new HashMap<>();
        headers.put(MfaTrustedDeviceManager.HEADER_DEVICE_ID, "device-uuid-1");
        headers.put("User-Agent", "JUnit-UA");
        String reason = ContextProvider.runWithTenant(TENANT_ID,
                () -> trustedDeviceManager.register(userId, headers));
        assertNotNull(reason, "register must succeed for carve-out setup");
        NopAuthMfaTrustedDevice row = findTrustedDevice(userId);
        assertNotNull(row, "registered row must exist before admin delete");

        // 非 admin 被拒（requireAdmin——确定性错误码经 BizModel 直调断言）
        IServiceContext svc = new ServiceContextImpl();
        svc.setUserContext(nonAdminCtx(userId, "sess-td-na"));
        NopAuthMfaTrustedDeviceBizModel bizModel = trustedDeviceBizModel();
        NopException denied = assertThrows(NopException.class,
                () -> ormTemplate.runInSession(s -> bizModel.delete(row.getSid(), svc)));
        assertEquals(NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode(), denied.getErrorCode());
        // GraphQL 入口同样拒绝
        ApiResponse<?> rejected = rpcMutation("NopAuthMfaTrustedDevice__delete", Map.of("id", row.getSid()),
                nonAdminCtx(userId, "sess-td-na2"));
        assertFalse(rejected.isOk(), "non-admin delete must be rejected at GraphQL entry");
        assertNotNull(findTrustedDevice(userId), "rejected non-admin delete must not remove the row");

        // admin 删除：物理删除 + revoke 族审计事件
        ApiResponse<?> ok = rpcMutation("NopAuthMfaTrustedDevice__delete", Map.of("id", row.getSid()), admin);
        assertTrue(ok.isOk(), "admin delete carve-out must succeed: " + ok.getErrors());
        assertNull(findTrustedDevice(userId), "admin delete must physically remove the row");
        assertTrue(pollAuditRequests("mfa:trusted-device-revoked").stream()
                        .anyMatch(d -> d.contains("admin-removed")),
                "admin delete must produce revoke audit event with reason=admin-removed");
    }

    @Test
    public void testRoleMfaPolicyDedicatedPathUnchanged() {
        String userId = "lock-policy-user";
        saveUser(userId);
        String roleId = "lock-policy-role";
        ensureRole(roleId);

        // 非 admin 专项路径仍拒绝（requireAdmin 面回归）
        ApiResponse<?> denied = rpcMutation("NopAuthRole__saveMfaPolicy",
                Map.of("roleId", roleId, "minMfaLevel", 2), nonAdminCtx(userId, "sess-policy-na"));
        assertFalse(denied.isOk(), "non-admin saveMfaPolicy must be rejected");

        // admin 专项路径：save（建行）→ 校验拒绝（minMfaLevel 4）→ remove（撤策略）
        ApiResponse<?> saved = rpcMutation("NopAuthRole__saveMfaPolicy",
                Map.of("roleId", roleId, "minMfaLevel", 2), adminCtx(userId, "sess-policy"));
        assertTrue(saved.isOk(), "admin saveMfaPolicy must work after lockdown: " + saved.getErrors());
        NopAuthRoleMfaPolicy policy = daoProvider.daoFor(NopAuthRoleMfaPolicy.class).getEntityById(roleId);
        assertNotNull(policy, "policy row must be created by dedicated path");
        assertEquals(2, policy.getMinMfaLevel());

        ApiResponse<?> invalid = rpcMutation("NopAuthRole__saveMfaPolicy",
                Map.of("roleId", roleId, "minMfaLevel", 4), adminCtx(userId, "sess-policy2"));
        assertFalse(invalid.isOk(), "invalid minMfaLevel must still be rejected");

        ApiResponse<?> removed = rpcMutation("NopAuthRole__removeMfaPolicy", Map.of("roleId", roleId),
                adminCtx(userId, "sess-policy3"));
        assertTrue(removed.isOk(), "admin removeMfaPolicy must work after lockdown: " + removed.getErrors());
        NopAuthRoleMfaPolicy removedRow = ContextProvider.runWithTenant(TENANT_ID,
                () -> daoProvider.daoFor(NopAuthRoleMfaPolicy.class).getEntityById(roleId));
        assertTrue(removedRow == null || (removedRow.getDelFlag() != null && removedRow.getDelFlag() != 0),
                "policy row must be logically deleted by dedicated path");

        // 删除后重建（复活路径）：removeMfaPolicy 后 saveMfaPolicy 仍可复活策略行
        ApiResponse<?> revived = rpcMutation("NopAuthRole__saveMfaPolicy",
                Map.of("roleId", roleId, "minMfaLevel", 3), adminCtx(userId, "sess-policy4"));
        assertTrue(revived.isOk(), "saveMfaPolicy after removal (revive path) must work: " + revived.getErrors());
        NopAuthRoleMfaPolicy revivedRow = ContextProvider.runWithTenant(TENANT_ID,
                () -> daoProvider.daoFor(NopAuthRoleMfaPolicy.class).getEntityById(roleId));
        assertNotNull(revivedRow, "revived policy row must exist");
        assertEquals(3, revivedRow.getMinMfaLevel());
    }

    // ===================== Helpers =====================

    private void assertCrudDisabled(String operation, Map<String, Object> data, UserContextImpl user) {
        ApiResponse<?> resp = rpcMutation(operation, data, user);
        assertFalse(resp.isOk(), operation + " must be rejected");
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CRUD_DISABLED.getErrorCode(), resp.getCode(),
                operation + " must fail with explicit error code, got: " + resp.getCode());
    }

    /** 各表最小 save 数据（含必填列，证明拒绝来自守卫而非字段校验）。 */
    private static Map<String, Object> saveData(String bizObj) {
        Map<String, Object> data = new HashMap<>();
        if ("NopAuthMfaSetting".equals(bizObj)) {
            data.put("userId", "lock-row");
        } else if ("NopAuthMfaCredential".equals(bizObj)) {
            data.put("sid", "lock-row");
            data.put("userId", "lock-row");
            data.put("credentialId", "lock-cred");
            data.put("publicKey", "pk");
        } else if ("NopAuthMfaTrustedDevice".equals(bizObj)) {
            data.put("userId", "lock-row");
            data.put("deviceHash", "deadbeef");
        } else if ("NopAuthRoleMfaPolicy".equals(bizObj)) {
            data.put("roleId", "lock-role");
            data.put("minMfaLevel", 1);
        } else if ("NopAuthMfaRecoveryCode".equals(bizObj)) {
            data.put("sid", "lock-row");
            data.put("userId", "lock-row");
            data.put("codeHash", "salt:hash");
        } else if ("NopAuthMfaChallenge".equals(bizObj)) {
            data.put("challengeToken", "lock-token");
            data.put("userId", "lock-row");
        } else if ("NopAuthSmsCode".equals(bizObj) || "NopAuthEmailCode".equals(bizObj)) {
            data.put("codeKey", "mfa:lock-row");
            data.put("code", "123456");
        } else {
            throw new IllegalArgumentException(bizObj);
        }
        return Map.of("data", data);
    }

    private NopAuthMfaTrustedDeviceBizModel trustedDeviceBizModel() {
        Object bean = io.nop.api.core.ioc.BeanContainer.tryGetBean(
                "io.nop.auth.service.entity.NopAuthMfaTrustedDeviceBizModel");
        if (bean == null)
            throw new IllegalStateException("NopAuthMfaTrustedDeviceBizModel bean not found in container");
        return (NopAuthMfaTrustedDeviceBizModel) bean;
    }

    private NopAuthMfaTrustedDevice findTrustedDevice(String userId) {
        return ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthMfaTrustedDevice> dao = daoProvider.daoFor(NopAuthMfaTrustedDevice.class);
            NopAuthMfaTrustedDevice example = dao.newEntity();
            example.setUserId(userId);
            List<NopAuthMfaTrustedDevice> found = dao.findAllByExample(example);
            return found.isEmpty() ? null : found.get(0);
        });
    }

    /** 轮询取指定 description 的审计行 requestData（批处理分批落库容忍，最多 ~10s）。 */
    private List<String> pollAuditRequests(String description) {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            List<NopAuthOpLog> found = ContextProvider.runWithTenant(TENANT_ID, () -> {
                NopAuthOpLog example = new NopAuthOpLog();
                example.setDescription(description);
                return daoProvider.daoFor(NopAuthOpLog.class).findAllByExample(example);
            });
            if (!found.isEmpty()) {
                return found.stream().map(l -> String.valueOf(l.getOpRequest())).collect(Collectors.toList());
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return List.of();
    }

    private ApiResponse<?> rpcMutation(String operation, Map<String, Object> data, UserContextImpl user) {
        IUserContext.set(user);
        try {
            ApiRequest<Map<String, Object>> request = new ApiRequest<>();
            request.setData(data);
            IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation, operation, request);
            return syncGet(graphQLEngine.executeRpcAsync(ctx));
        } finally {
            IUserContext.set(null);
        }
    }

    private UserContextImpl adminCtx(String userId, String sessionId) {
        return ctx(userId, sessionId, true);
    }

    private UserContextImpl nonAdminCtx(String userId, String sessionId) {
        return ctx(userId, sessionId, false);
    }

    private UserContextImpl ctx(String userId, String sessionId, boolean admin) {
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId(userId);
        uc.setUserName(userId);
        uc.setTenantId(TENANT_ID);
        uc.setSessionId(sessionId);
        Set<String> roles = new HashSet<>();
        if (admin) {
            roles.add(NopAuthConstants.ROLE_ADMIN);
        } else {
            roles.add("ordinary-role");
        }
        uc.setRoles(roles);
        return uc;
    }

    private void saveUser(String userId) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
            if (dao.getEntityById(userId) == null) {
                NopAuthUser user = dao.newEntity();
                user.setUserId(userId);
                user.setUserName(userId);
                user.setNickName(userId);
                String salt = passwordEncoder.generateSalt();
                user.setPassword(passwordEncoder.encodePassword(salt, "123"));
                user.setSalt(salt);
                user.setOpenId(userId);
                user.setUserType(1);
                user.setStatus(1);
                user.setGender(1);
                user.setTenantId(TENANT_ID);
                dao.saveEntity(user);
            }
            return null;
        });
    }

    private void ensureRole(String roleId) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthRole> dao = daoProvider.daoFor(NopAuthRole.class);
            if (dao.getEntityById(roleId) == null) {
                NopAuthRole role = dao.newEntity();
                role.setRoleId(roleId);
                role.setRoleName(roleId);
                dao.saveEntity(role);
            }
            return null;
        });
    }
}
