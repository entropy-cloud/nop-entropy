/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.credential.dao.entity.NopCredentialOauthState;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A1-audit D2-01/D4-01 修复回归（2026-08-17，对抗探查测试）：{@link NopCredentialOauthStateBizModel}
 * 收口面的 <b>GraphQL 入口级</b> AutoTest——state 绑定表为 OAuth 引擎内部存储，codegen 裸 CRUD 面
 * 必须不可达：查询面 admin-only（双层防御之 BizModel 运行时判定层）+ 全部标准 mutation 禁用
 * （save/update/delete/batchDelete/updateByQuery/deleteByQuery/copyForNew）。
 *
 * <p>攻击面回归对象（D2-01 风险模型）：普通登录用户经 {@code deleteByQuery}/{@code updateByQuery}
 * 的授权流 DoS 与 token 落点劫持载体、经 {@code findPage} 的发起时机侦查面——修复后全部不可达。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopCredentialOauthStateBizModel extends JunitBaseTestCase {

    private static final String ALICE = "alice";
    private static final String ADMIN = "cred-admin";

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
    }

    // ==================== 测试基建 ====================

    /** 最小角色感知 IUserContext（对齐 TestNopCredentialAuthBizModel 先例）。 */
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

    private GraphQLResponseBean executeGraphQL(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    /** 经 dao 直插 state 行（模拟引擎 NopCredentialOauthStateStore 的写路径——不经 BizModel）。 */
    private String saveStateRowDirect(String state, String credentialId, String userId) {
        IEntityDao<NopCredentialOauthState> dao = daoProvider.daoFor(NopCredentialOauthState.class);
        NopCredentialOauthState entity = dao.newEntity();
        entity.setState(state);
        entity.setCredentialId(credentialId);
        entity.setUserId(userId);
        entity.setExpireAt(System.currentTimeMillis() + 600_000L);
        entity.setConsumed((byte) 0);
        dao.saveEntityDirectly(entity);
        return state;
    }

    // ==================== 查询面 admin-only ====================

    @Test
    public void adminQueryFaceAllowed() {
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        saveStateRowDirect("audit-state-admin-1", "cred-sys-1", ALICE);

        GraphQLResponseBean resp = executeGraphQL(
                "query { NopCredentialOauthState__findPage { items { credentialId userId expireAt consumed createTime createdBy } total } }");
        assertFalse(resp.hasError(), "admin query must succeed, errors=" + resp.getErrors());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        Map<String, Object> page = (Map<String, Object>) data.get("NopCredentialOauthState__findPage");
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        assertEquals(1, items.size());
        assertEquals("cred-sys-1", items.get(0).get("credentialId"));
        assertEquals(ALICE, items.get(0).get("userId"));

        GraphQLResponseBean get = executeGraphQL(
                "query { NopCredentialOauthState__get(id: \"audit-state-admin-1\") { credentialId userId } }");
        assertFalse(get.hasError(), "admin get must succeed, errors=" + get.getErrors());

        GraphQLResponseBean batchGet = executeGraphQL(
                "query { NopCredentialOauthState__batchGet(ids: [\"audit-state-admin-1\"]) { credentialId } }");
        assertFalse(batchGet.hasError(), "admin batchGet must succeed, errors=" + batchGet.getErrors());
    }

    @Test
    public void nonAdminAndNoLoginDeniedForQuery() {
        IUserContext.set(RoleUserContext.plain(ALICE));
        GraphQLResponseBean deniedPage = executeGraphQL(
                "query { NopCredentialOauthState__findPage { items { credentialId } } }");
        assertTrue(deniedPage.hasError(), "non-admin findPage must be denied (recon face closed)");
        assertEquals("nop.err.credential.admin-required", deniedPage.getErrorCode());

        GraphQLResponseBean deniedGet = executeGraphQL(
                "query { NopCredentialOauthState__get(id: \"whatever\") { credentialId } }");
        assertTrue(deniedGet.hasError(), "non-admin get must be denied");
        assertEquals("nop.err.credential.admin-required", deniedGet.getErrorCode());

        GraphQLResponseBean deniedBatchGet = executeGraphQL(
                "query { NopCredentialOauthState__batchGet(ids: [\"whatever\"]) { credentialId } }");
        assertTrue(deniedBatchGet.hasError(), "non-admin batchGet must be denied");
        assertEquals("nop.err.credential.admin-required", deniedBatchGet.getErrorCode());

        IUserContext.set(null);
        GraphQLResponseBean noLogin = executeGraphQL(
                "query { NopCredentialOauthState__findPage { items { credentialId } } }");
        assertTrue(noLogin.hasError(), "no-login query must be denied");
        assertEquals("nop.err.credential.admin-required", noLogin.getErrorCode());
    }

    // ==================== 全部标准 mutation 旁路收口（admin 亦不可达） ====================

    @Test
    public void standardBypassActionsClosed() {
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));

        GraphQLResponseBean save = executeGraphQL(
                "mutation { NopCredentialOauthState__save(data: {credentialId: \"x\", userId: \"y\"}) { credentialId } }");
        assertTrue(save.hasError(), "standard save must be disabled (forged state rows)");

        GraphQLResponseBean update = executeGraphQL(
                "mutation { NopCredentialOauthState__update(data: {credentialId: \"x\"}) { credentialId } }");
        assertTrue(update.hasError(), "standard update must be disabled (binding/consumed tampering)");

        GraphQLResponseBean delete = executeGraphQL(
                "mutation { NopCredentialOauthState__delete(id: \"whatever\") }");
        assertTrue(delete.hasError(), "standard delete must be disabled (in-flight DoS)");

        GraphQLResponseBean batchDelete = executeGraphQL(
                "mutation { NopCredentialOauthState__batchDelete(ids: [\"whatever\"]) }");
        assertTrue(batchDelete.hasError(), "standard batchDelete must be disabled");

        GraphQLResponseBean updateByQuery = executeGraphQL(
                "mutation { NopCredentialOauthState__updateByQuery(query: {}, data: {credentialId: \"attacker-cred\"}) }");
        assertTrue(updateByQuery.hasError(), "updateByQuery must be disabled (token-capture vector)");

        GraphQLResponseBean deleteByQuery = executeGraphQL(
                "mutation { NopCredentialOauthState__deleteByQuery(query: {}) }");
        assertTrue(deleteByQuery.hasError(), "deleteByQuery must be disabled (DoS vector)");

        GraphQLResponseBean copyForNew = executeGraphQL(
                "mutation { NopCredentialOauthState__copyForNew(data: {credentialId: \"x\", userId: \"y\"}) { credentialId } }");
        assertTrue(copyForNew.hasError(), "copyForNew must be disabled");
    }

    // ==================== 非管理员的 mutation 面同样不可达（fail-closed 无论身份） ====================

    @Test
    public void nonAdminMutationsUnreachable() {
        IUserContext.set(RoleUserContext.plain(ALICE));

        GraphQLResponseBean deleteByQuery = executeGraphQL(
                "mutation { NopCredentialOauthState__deleteByQuery(query: {}) }");
        assertTrue(deleteByQuery.hasError(), "non-admin deleteByQuery must fail closed");

        GraphQLResponseBean updateByQuery = executeGraphQL(
                "mutation { NopCredentialOauthState__updateByQuery(query: {}, data: {credentialId: \"attacker-cred\"}) }");
        assertTrue(updateByQuery.hasError(), "non-admin updateByQuery must fail closed");
    }

    // ==================== 引擎写路径不受收口影响 ====================

    @Test
    public void engineDirectDaoWriteUnaffected() {
        // 引擎（NopCredentialOauthStateStore）经 dao 直写不经 BizModel——收口后仍可写入（回归保护）
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        saveStateRowDirect("audit-state-engine-1", "cred-sys-2", ALICE);

        GraphQLResponseBean resp = executeGraphQL(
                "query { NopCredentialOauthState__get(id: \"audit-state-engine-1\") { credentialId userId } }");
        assertFalse(resp.hasError(), "engine-written row must be visible to admin, errors=" + resp.getErrors());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        Map<String, Object> row = (Map<String, Object>) data.get("NopCredentialOauthState__get");
        assertEquals("cred-sys-2", row.get("credentialId"), "dao-written row persists (engine path unaffected)");
    }
}
