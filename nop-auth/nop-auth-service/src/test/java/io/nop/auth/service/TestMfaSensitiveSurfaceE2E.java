/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthOpLog;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2-followup-1 Phase 3 E2E：敏感面收敛——
 * <ul>
 *   <li><b>D1-7</b>：`NopAuthMfaSetting` 通用查询面输出不含 phone 字段（结构性排除——
 *       GraphQL schema 无该 field，选择 phone 的查询在 schema 校验即失败）；`getMfaStatus`
 *       既有脱敏输出零回归。</li>
 *   <li><b>W12 路由项 3</b>：NopAuthUser phone/email 经通用 CRUD 的非 admin 修改被显式拒绝
 *       （含本人行）；admin 修改/创建（含 phone/email）零回归且产生 contact-changed 审计；
 *       非联系字段通用修改行为不变；受限会话 enrollment attack 组合链断链。</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestMfaSensitiveSurfaceE2E extends JunitBaseTestCase {

    private static final String TENANT_ID = "0";

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    io.nop.auth.core.password.IPasswordEncoder passwordEncoder;

    @AfterEach
    void clearUserContext() {
        IUserContext.set(null);
    }

    // ===================== D1-7：setting.phone 结构性排除 =====================

    @Test
    public void testMfaSettingGenericQueryStructurallyExcludesPhone() {
        String userId = "d17-user";
        saveUser(userId);
        // 直插含 phone 的 setting 行（sms enabled）——服务内部 dao 可写可读
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthMfaSetting> dao = daoProvider.daoFor(NopAuthMfaSetting.class);
            NopAuthMfaSetting setting = dao.newEntity();
            setting.setUserId(userId);
            setting.setTenantId(TENANT_ID);
            setting.setMfaType(NopAuthConstants.MFA_TYPE_SMS);
            setting.setStatus(NopAuthConstants.MFA_STATUS_ENABLED);
            setting.setPhone("13955556666");
            dao.saveEntity(setting);
            return null;
        });

        UserContextImpl admin = adminCtx(userId, "sess-d17");

        // 1. schema 结构性断言：选择 phone 的查询在 GraphQL 校验即抛 undefined-field（field 不在 schema）
        IUserContext.set(admin);
        try {
            GraphQLRequestBean request = new GraphQLRequestBean();
            request.setQuery("query { NopAuthMfaSetting__findPage { items { userId mfaType status phone } } }");
            IGraphQLExecutionContext ctx = graphQLEngine.newGraphQLContext(request);
            GraphQLResponseBean bad = syncGet(graphQLEngine.executeGraphQLAsync(ctx));
            assertTrue(bad.hasError(), "selecting the unpublished phone field must fail");
        } catch (NopException e) {
            assertEquals(io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNDEFINED_FIELD.getErrorCode(), e.getErrorCode(),
                    "schema validation must reject the unknown field 'phone'");
        } finally {
            IUserContext.set(null);
        }

        // 2. 合法选择面正常返回（published 字段）——通用查询面仍可用（只读监控面）
        GraphQLResponseBean ok = gqlQuery("query { NopAuthMfaSetting__findPage { total items { userId mfaType status } } }", admin);
        assertFalse(ok.hasError(), "published fields must remain queryable: " + ok.getErrors());

        // 3. get 面同口径：get 选择 phone 同样 schema 拒绝
        try {
            IUserContext.set(admin);
            GraphQLRequestBean request = new GraphQLRequestBean();
            request.setQuery("query { NopAuthMfaSetting__get(id:\"" + userId + "\") { userId phone } }");
            IGraphQLExecutionContext ctx = graphQLEngine.newGraphQLContext(request);
            GraphQLResponseBean badGet = syncGet(graphQLEngine.executeGraphQLAsync(ctx));
            assertTrue(badGet.hasError(), "get selection with phone must fail");
        } catch (NopException e) {
            assertEquals(io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNDEFINED_FIELD.getErrorCode(), e.getErrorCode(),
                    "get schema validation must reject the unknown field 'phone'");
        } finally {
            IUserContext.set(null);
        }

        // 4. getMfaStatus 既有脱敏输出零回归（biz 面不受 published=false 影响）
        GraphQLResponseBean status = gqlQuery("query { NopAuthUser__getMfaStatus { mfaType status phone } }", admin);
        assertFalse(status.hasError(), "getMfaStatus must keep its masked phone output: " + status.getErrors());
        String json = String.valueOf(status.getData());
        assertTrue(json.contains("******6666"), "getMfaStatus phone must stay masked: " + json);
        assertTrue(!json.contains("13955556666"), "getMfaStatus must not leak the raw phone: " + json);
    }

    // ===================== W12 路由项 3：联系方式通用 CRUD 拦截 =====================

    @Test
    public void testNonAdminContactChangeRejectedOnUpdateAndSave() {
        String userId = "w12-guard-user";
        saveUserWithPhone(userId, "13900001111");

        // 非 admin 修改本人行 phone → 显式拒绝（错误码断言）
        ApiResponse<?> deniedPhone = rpcMutation("NopAuthUser__update",
                Map.of("data", Map.of("id", userId, "phone", "13900009999")),
                nonAdminCtx(userId, "sess-w12-a"));
        assertFalse(deniedPhone.isOk(), "non-admin phone change must be rejected");
        assertEquals(NopAuthErrors.ERR_AUTH_CONTACT_CHANGE_NOT_ALLOWED.getErrorCode(), deniedPhone.getCode(),
                "unexpected error: " + deniedPhone.getCode());

        // 非 admin 修改本人行 email → 拒绝
        ApiResponse<?> deniedEmail = rpcMutation("NopAuthUser__update",
                Map.of("data", Map.of("id", userId, "email", "attacker@evil.com")),
                nonAdminCtx(userId, "sess-w12-b"));
        assertFalse(deniedEmail.isOk());
        assertEquals(NopAuthErrors.ERR_AUTH_CONTACT_CHANGE_NOT_ALLOWED.getErrorCode(), deniedEmail.getCode());

        // 非 admin save（创建）携带 phone → 拒绝
        ApiResponse<?> deniedCreate = rpcMutation("NopAuthUser__save",
                Map.of("data", new HashMap<>(Map.of("userName", "w12-create-user", "password", "12355678$DFs",
                        "nickName", "x", "userType", 1, "gender", 1, "phone", "13922223333"))),
                nonAdminCtx(userId, "sess-w12-c"));
        assertFalse(deniedCreate.isOk(), "non-admin create with phone must be rejected");
        assertEquals(NopAuthErrors.ERR_AUTH_CONTACT_CHANGE_NOT_ALLOWED.getErrorCode(), deniedCreate.getCode());

        // 行未被篡改
        NopAuthUser row = getUser(userId);
        assertEquals("13900001111", row.getPhone(), "rejected change must not persist");

        // 非联系字段（nickname）通用修改行为不变
        ApiResponse<?> nickOk = rpcMutation("NopAuthUser__update",
                Map.of("data", Map.of("id", userId, "nickName", "renamed-ok")),
                nonAdminCtx(userId, "sess-w12-d"));
        assertTrue(nickOk.isOk(), "non-contact generic update must remain allowed: " + nickOk.getErrors());
        assertEquals("renamed-ok", getUser(userId).getNickName());
    }

    @Test
    public void testAdminContactChangeAllowedAndAudited() {
        String adminId = "w12-admin";
        saveUserWithPhone(adminId, "13900002222");
        String targetId = "w12-target";
        saveUserWithPhone(targetId, "13900003333");

        // admin 修改他人 phone → 成功 + user:contact-changed 审计
        ApiResponse<?> upd = rpcMutation("NopAuthUser__update",
                Map.of("data", Map.of("id", targetId, "phone", "13900004444")),
                adminCtx(adminId, "sess-w12-e"));
        assertTrue(upd.isOk(), "admin contact change must remain allowed: " + upd.getErrors());
        assertEquals("13900004444", getUser(targetId).getPhone());

        // admin 创建用户（含 phone）零回归 + 审计（create 形态）
        ApiResponse<?> created = rpcMutation("NopAuthUser__save",
                Map.of("data", new HashMap<>(Map.of("userName", "w12-created", "password", "12355678$DFs",
                        "nickName", "created", "userType", 1, "gender", 1, "phone", "13900005555"))),
                adminCtx(adminId, "sess-w12-f"));
        assertTrue(created.isOk(), "admin create with phone must remain allowed: " + created.getErrors());

        List<String> audits = pollAuditRequests("user:contact-changed",
                list -> list.stream().anyMatch(a -> a.contains("\"create\"")));
        assertTrue(audits.stream().anyMatch(a -> a.contains("\"phone\"") && a.contains(targetId)
                        && a.contains("\"update\"")),
                "admin update must audit contact-changed (update mode): " + audits);
        assertTrue(audits.stream().anyMatch(a -> a.contains("\"create\"")),
                "admin create with phone must audit contact-changed (create mode): " + audits);
    }

    /** 受限会话 enrollment attack 组合链断链：受限用户改 phone 的通用 mutation 在拦截点即被拒。 */
    @Test
    public void testRestrictedSessionContactChangeChainSevered() {
        String userId = "w12-restricted";
        saveUserWithPhone(userId, "13900006666");

        // 受限会话：非白名单 mutation（NopAuthUser__update）被受限拦截（链断在第一道门）
        GraphQLResponseBean doc = gqlMutation("NopAuthUser__update",
                "data:{id:\"" + userId + "\", phone:\"13900007777\"}", "{ id }", restrictedCtx(userId, "sess-w12-r"));
        assertTrue(doc.hasError(), "restricted session must not reach generic user update");
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_RESTRICTED_SESSION.getErrorCode(), doc.getErrorCode());

        // 非受限但非 admin 会话：链断在联系方式拦截门（guard）
        ApiResponse<?> denied = rpcMutation("NopAuthUser__update",
                Map.of("data", Map.of("id", userId, "phone", "13900007777")),
                nonAdminCtx(userId, "sess-w12-r2"));
        assertEquals(NopAuthErrors.ERR_AUTH_CONTACT_CHANGE_NOT_ALLOWED.getErrorCode(), denied.getCode(),
                "non-restricted non-admin must hit the contact-change guard");

        // 行未被篡改（组合路径断链：改 phone 后 bindSms 的前半步已不可达）
        assertEquals("13900006666", getUser(userId).getPhone());
    }

    // ===================== Helpers =====================

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

    private GraphQLResponseBean gqlQuery(String query, UserContextImpl user) {
        IUserContext.set(user);
        try {
            GraphQLRequestBean request = new GraphQLRequestBean();
            request.setQuery(query);
            IGraphQLExecutionContext ctx = graphQLEngine.newGraphQLContext(request);
            return syncGet(graphQLEngine.executeGraphQLAsync(ctx));
        } finally {
            IUserContext.set(null);
        }
    }

    private GraphQLResponseBean gqlMutation(String operation, String argsLiteral, String resultSelection,
                                            UserContextImpl user) {
        IUserContext.set(user);
        try {
            GraphQLRequestBean request = new GraphQLRequestBean();
            request.setQuery("mutation { " + operation + "(" + argsLiteral + ") " + resultSelection + " }");
            IGraphQLExecutionContext ctx = graphQLEngine.newGraphQLContext(request);
            return syncGet(graphQLEngine.executeGraphQLAsync(ctx));
        } finally {
            IUserContext.set(null);
        }
    }

    private UserContextImpl adminCtx(String userId, String sessionId) {
        return ctx(userId, sessionId, false, true);
    }

    private UserContextImpl nonAdminCtx(String userId, String sessionId) {
        return ctx(userId, sessionId, false, false);
    }

    private UserContextImpl restrictedCtx(String userId, String sessionId) {
        return ctx(userId, sessionId, true, true);
    }

    private UserContextImpl ctx(String userId, String sessionId, boolean restricted, boolean adminRole) {
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId(userId);
        uc.setUserName(userId);
        uc.setTenantId(TENANT_ID);
        uc.setSessionId(sessionId);
        uc.setMfaRestricted(restricted);
        Set<String> roles = new HashSet<>();
        if (adminRole) {
            roles.add(NopAuthConstants.ROLE_ADMIN);
        } else {
            roles.add("ordinary-role");
        }
        uc.setRoles(roles);
        return uc;
    }

    private NopAuthUser getUser(String userId) {
        return ContextProvider.runWithTenant(TENANT_ID,
                () -> daoProvider.daoFor(NopAuthUser.class).getEntityById(userId));
    }

    /** 轮询取指定 description 的审计行 requestData（批处理分批落库容忍；until 谓词满足或 ~10s 止）。 */
    private List<String> pollAuditRequests(String description, java.util.function.Predicate<List<String>> until) {
        long deadline = System.currentTimeMillis() + 10_000L;
        List<String> result = List.of();
        while (System.currentTimeMillis() < deadline) {
            List<NopAuthOpLog> found = ContextProvider.runWithTenant(TENANT_ID, () -> {
                NopAuthOpLog example = new NopAuthOpLog();
                example.setDescription(description);
                return daoProvider.daoFor(NopAuthOpLog.class).findAllByExample(example);
            });
            result = found.stream().map(l -> String.valueOf(l.getOpRequest())).collect(Collectors.toList());
            if (!result.isEmpty() && until.test(result)) {
                return result;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return result;
    }

    private void saveUser(String userId) {
        saveUserWithPhone(userId, null);
    }

    private void saveUserWithPhone(String userId, String phone) {
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
                if (phone != null) {
                    user.setPhone(phone);
                }
                dao.saveEntity(user);
            }
            return null;
        });
    }
}
