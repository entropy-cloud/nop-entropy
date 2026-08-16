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
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.crypto.CredentialCipher;
import io.nop.credential.dao.entity.NopCredential;
import io.nop.credential.dao.entity.NopCredentialAuth;
import io.nop.credential.dao.entity.NopCredentialUsage;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W11 Part B Phase 2：{@link NopCredentialAuthBizModel} 授权管理面的 <b>GraphQL 入口级</b>
 * 端到端 AutoTest（经 GraphQL 引擎驱动容器 BizModel——同时即容器接线验证：bean 由 codegen
 * `_service.beans.xml` 注册，grant/revoke 动作经引擎可达）。
 *
 * <p>覆盖：admin grant/revoke 幂等往返 / 非 admin（含无登录态）grant/查询拒绝 / grant user 级
 * 凭证被拒（user 级不叠加角色授权）/ grant 不存在凭证归一 NOT_FOUND / grant 已删凭证 DELETED /
 * roleId 空拒绝 / 七个标准旁路动作收口 / 凭证删除物理级联清理授权行（删除被 usage 拦截时
 * 授权行与凭证同存）/ 两层防御端到端（grant → 非授予用户直调 provider 被拒 → 授予角色放行 →
 * revoke 后再拒）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopCredentialAuthBizModel extends JunitBaseTestCase {

    private static final String ALICE = "alice";
    private static final String ADMIN = "cred-admin";
    private static final String ROLE_A = "role-granted-a";
    private static final String ROLE_B = "role-granted-b";
    private static final String ROLE_C = "role-granted-c";

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    ICredentialProvider credentialProvider;

    @Inject
    CredentialCipher credentialCipher;

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

    private String grantViaGraphQL(String credentialId, String roleId) {
        GraphQLResponseBean response = executeGraphQL(
                "mutation { NopCredentialAuth__grant(credentialId: \"" + credentialId
                        + "\", roleId: \"" + roleId + "\") }");
        assertFalse(response.hasError(), "grant must succeed, errors=" + response.getErrors());
        return credentialId;
    }

    private String saveRowDirect(String id, String scope, String ownerId) {
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        NopCredential entity = dao.newEntity();
        entity.setCredentialId(id);
        entity.setName(id);
        entity.setTypeName("generic-secret");
        entity.setStatus("enabled");
        entity.setDelFlag((byte) 0);
        entity.setVersion(1);
        entity.setScope(scope);
        entity.setOwnerId(ownerId);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("secretValue", "sk-" + id);
        entity.setData(credentialCipher.encrypt(JsonTool.stringify(fields)));
        dao.saveEntityDirectly(entity);
        return id;
    }

    private long countAuthRows(String credentialId, String roleId) {
        IEntityDao<NopCredentialAuth> dao = daoProvider.daoFor(NopCredentialAuth.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopCredentialAuth.PROP_NAME_credentialId, credentialId));
        if (roleId != null) {
            query.addFilter(FilterBeans.eq(NopCredentialAuth.PROP_NAME_roleId, roleId));
        }
        return dao.countByQuery(query);
    }

    // ==================== admin grant/revoke 幂等往返 ====================

    @Test
    public void adminGrantRevokeIdempotentRoundTrip() {
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        String id = saveRowDirect("auth-rt-sys", "system", null);

        // grant 两次：均成功、恰一行（幂等：已存在 no-op 成功）
        grantViaGraphQL(id, ROLE_A);
        grantViaGraphQL(id, ROLE_A);
        assertEquals(1, countAuthRows(id, ROLE_A), "duplicate grant must be a no-op (unique constraint backstop)");

        // 多角色授权共存
        grantViaGraphQL(id, ROLE_B);
        assertEquals(1, countAuthRows(id, ROLE_B));

        // revoke 两次：均成功（幂等：不存在 no-op 成功）
        GraphQLResponseBean revoke1 = executeGraphQL(
                "mutation { NopCredentialAuth__revoke(credentialId: \"" + id + "\", roleId: \"" + ROLE_A + "\") }");
        assertFalse(revoke1.hasError(), "revoke must succeed, errors=" + revoke1.getErrors());
        GraphQLResponseBean revoke2 = executeGraphQL(
                "mutation { NopCredentialAuth__revoke(credentialId: \"" + id + "\", roleId: \"" + ROLE_A + "\") }");
        assertFalse(revoke2.hasError(), "revoking a non-existent grant must be a no-op success");
        assertEquals(0, countAuthRows(id, ROLE_A));
        assertEquals(1, countAuthRows(id, ROLE_B), "other grants untouched");
    }

    // ==================== 非 admin（含无登录态）grant/查询拒绝 ====================

    @Test
    public void nonAdminAndNoLoginDeniedForQueryAndMutation() {
        saveRowDirect("auth-deny-sys", "system", null);

        IUserContext.set(RoleUserContext.plain(ALICE));
        GraphQLResponseBean deniedGrant = executeGraphQL(
                "mutation { NopCredentialAuth__grant(credentialId: \"auth-deny-sys\", roleId: \"" + ROLE_A + "\") }");
        assertTrue(deniedGrant.hasError(), "non-admin grant must be denied");
        assertEquals("nop.err.credential.admin-required", deniedGrant.getErrorCode());

        GraphQLResponseBean deniedQuery = executeGraphQL(
                "query { NopCredentialAuth__findPage { items { authId } } }");
        assertTrue(deniedQuery.hasError(), "non-admin query must be denied");
        assertEquals("nop.err.credential.admin-required", deniedQuery.getErrorCode());

        GraphQLResponseBean deniedGet = executeGraphQL(
                "query { NopCredentialAuth__get(id: \"whatever\") { authId } }");
        assertTrue(deniedGet.hasError(), "non-admin get must be denied");
        assertEquals("nop.err.credential.admin-required", deniedGet.getErrorCode());

        GraphQLResponseBean deniedBatchGet = executeGraphQL(
                "query { NopCredentialAuth__batchGet(ids: [\"whatever\"]) { authId } }");
        assertTrue(deniedBatchGet.hasError(), "non-admin batchGet must be denied (admin-only semantics)");
        assertEquals("nop.err.credential.admin-required", deniedBatchGet.getErrorCode());

        // 无登录态：同样拒绝（授权管理面唯一合法消费方是管理员）
        IUserContext.set(null);
        GraphQLResponseBean noLoginGrant = executeGraphQL(
                "mutation { NopCredentialAuth__grant(credentialId: \"auth-deny-sys\", roleId: \"" + ROLE_A + "\") }");
        assertTrue(noLoginGrant.hasError(), "no-login grant must be denied");
        assertEquals("nop.err.credential.admin-required", noLoginGrant.getErrorCode());

        GraphQLResponseBean noLoginQuery = executeGraphQL(
                "query { NopCredentialAuth__findPage { items { authId } } }");
        assertTrue(noLoginQuery.hasError(), "no-login query must be denied");
        assertEquals("nop.err.credential.admin-required", noLoginQuery.getErrorCode());

        assertEquals(0, countAuthRows("auth-deny-sys", null), "denied grants must not persist anything");
    }

    @Test
    public void adminQueryFaceAllowed() {
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        String id = saveRowDirect("auth-q-sys", "system", null);
        grantViaGraphQL(id, ROLE_A);

        GraphQLResponseBean resp = executeGraphQL(
                "query { NopCredentialAuth__findPage { items { authId credentialId roleId createTime createdBy } total } }");
        assertFalse(resp.hasError(), "admin query must succeed, errors=" + resp.getErrors());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        Map<String, Object> page = (Map<String, Object>) data.get("NopCredentialAuth__findPage");
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        assertEquals(1, items.size());
        assertEquals(ROLE_A, items.get(0).get("roleId"));
        assertEquals(id, items.get(0).get("credentialId"));
        assertNotNull(items.get(0).get("createdBy"), "grant must be audited (createdBy auto-stamped)");
    }

    // ==================== grant 目标凭证预检 ====================

    @Test
    public void grantUserScopeCredentialRejected() {
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        String id = saveRowDirect("auth-user-scope", "user", ALICE);

        GraphQLResponseBean resp = executeGraphQL(
                "mutation { NopCredentialAuth__grant(credentialId: \"" + id + "\", roleId: \"" + ROLE_A + "\") }");
        assertTrue(resp.hasError(), "grant on user-scope credential must be rejected (user 级不叠加角色授权)");
        assertEquals("nop.err.credential.auth-not-system-scope", resp.getErrorCode());
        assertEquals(0, countAuthRows(id, null));
    }

    @Test
    public void grantUnknownCredentialNormalizedAsNotFound() {
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        GraphQLResponseBean resp = executeGraphQL(
                "mutation { NopCredentialAuth__grant(credentialId: \"no-such-credential\", roleId: \"" + ROLE_A + "\") }");
        assertTrue(resp.hasError(), "grant on unknown credential must fail");
        assertEquals("nop.err.credential.not-found", resp.getErrorCode());
    }

    @Test
    public void grantDeletedCredentialReportsDeleted() {
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        NopCredential deleted = dao.newEntity();
        deleted.setCredentialId("auth-deleted-cred");
        deleted.setName("auth-deleted-cred");
        deleted.setTypeName("generic-secret");
        deleted.setStatus("enabled");
        deleted.setDelFlag((byte) 1);
        deleted.setVersion(1);
        deleted.setScope("system");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("secretValue", "sk-x");
        deleted.setData(credentialCipher.encrypt(JsonTool.stringify(fields)));
        dao.saveEntityDirectly(deleted);

        GraphQLResponseBean resp = executeGraphQL(
                "mutation { NopCredentialAuth__grant(credentialId: \"auth-deleted-cred\", roleId: \"" + ROLE_A + "\") }");
        assertTrue(resp.hasError(), "grant on deleted credential must fail closed");
        assertEquals("nop.err.credential.deleted", resp.getErrorCode());
    }

    @Test
    public void grantEmptyRoleIdRejected() {
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        String id = saveRowDirect("auth-empty-role", "system", null);
        // GraphQL 入口对非空参数的空串先序拒绝（field-empty-arg）；BizModel 层的
        // role-id-required 守卫为直调 Java 的纵深防御（Rule #24 fail-closed）
        GraphQLResponseBean resp = executeGraphQL(
                "mutation { NopCredentialAuth__grant(credentialId: \"" + id + "\", roleId: \"\") }");
        assertTrue(resp.hasError(), "empty roleId must be rejected");
        assertEquals("nop.err.graphql.field-empty-arg", resp.getErrorCode());
        assertEquals(0, countAuthRows(id, null));
    }

    // ==================== 标准旁路动作收口 ====================

    @Test
    public void standardBypassActionsClosed() {
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));

        GraphQLResponseBean save = executeGraphQL(
                "mutation { NopCredentialAuth__save(data: {authId: \"x\", roleId: \"y\"}) { authId } }");
        assertTrue(save.hasError(), "standard save must be disabled");

        GraphQLResponseBean update = executeGraphQL(
                "mutation { NopCredentialAuth__update(data: {authId: \"x\", roleId: \"y\"}) { authId } }");
        assertTrue(update.hasError(), "standard update must be disabled (auth rows immutable)");

        GraphQLResponseBean delete = executeGraphQL(
                "mutation { NopCredentialAuth__delete(id: \"x\") }");
        assertTrue(delete.hasError(), "standard delete must be disabled (revoke is the only delete channel)");

        GraphQLResponseBean batchDelete = executeGraphQL(
                "mutation { NopCredentialAuth__batchDelete(ids: [\"x\"]) }");
        assertTrue(batchDelete.hasError(), "standard batchDelete must be disabled");

        GraphQLResponseBean updateByQuery = executeGraphQL(
                "mutation { NopCredentialAuth__updateByQuery(query: {}, data: {roleId: \"y\"}) }");
        assertTrue(updateByQuery.hasError(), "updateByQuery must be disabled");

        GraphQLResponseBean deleteByQuery = executeGraphQL(
                "mutation { NopCredentialAuth__deleteByQuery(query: {}) }");
        assertTrue(deleteByQuery.hasError(), "deleteByQuery must be disabled (bypasses revoke contract)");

        GraphQLResponseBean copyForNew = executeGraphQL(
                "mutation { NopCredentialAuth__copyForNew(data: {authId: \"x\"}) { authId } }");
        assertTrue(copyForNew.hasError(), "copyForNew must be disabled (duplicates auth rows outside grant)");
    }

    // ==================== 凭证删除的授权行级联语义 ====================

    @Test
    public void credentialDeletePhysicallyCascadesAuthRows() {
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        String id = saveRowDirect("auth-cascade-sys", "system", null);
        grantViaGraphQL(id, ROLE_A);
        grantViaGraphQL(id, ROLE_B);
        assertEquals(2, countAuthRows(id, null));

        // 凭证删除成功路径（无 usage 引用）：授权行物理级联清理
        GraphQLResponseBean delete = executeGraphQL(
                "mutation { NopCredential__delete(id: \"" + id + "\") }");
        assertFalse(delete.hasError(), "credential delete must succeed, errors=" + delete.getErrors());
        assertEquals(0, countAuthRows(id, null), "auth rows must be physically cascade-deleted with the credential");
    }

    @Test
    public void blockedDeleteKeepsAuthRowsAlongsideCredential() {
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        String id = saveRowDirect("auth-blocked-sys", "system", null);
        grantViaGraphQL(id, ROLE_A);

        // 登记 usage 引用 → 删除被引用计数拦截 → 授权行与凭证同存（正常语义，非缺陷）
        IEntityDao<NopCredentialUsage> usageDao = daoProvider.daoFor(NopCredentialUsage.class);
        NopCredentialUsage usage = usageDao.newEntity();
        usage.setUsageId("usage-block-1");
        usage.setCredentialId(id);
        usage.setConsumerRef("consumer-x");
        usageDao.saveEntityDirectly(usage);

        GraphQLResponseBean delete = executeGraphQL(
                "mutation { NopCredential__delete(id: \"" + id + "\") }");
        assertTrue(delete.hasError(), "delete with active usage must be blocked");
        assertEquals("nop.err.credential.has-active-usage", delete.getErrorCode());

        assertEquals(1, countAuthRows(id, null),
                "auth rows stay alongside the credential when delete is blocked (normal semantics)");
        NopCredential stillThere = daoProvider.daoFor(NopCredential.class).getEntityById(id);
        assertNotNull(stillThere);
        assertFalse(stillThere.getDelFlag() != null && stillThere.getDelFlag() != 0);
    }

    // ==================== 两层防御端到端（grant → provider 拦截/放行 → revoke 后再拒） ====================

    @Test
    public void twoLayerDefenseEndToEndViaGraphQLAndProvider() {
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        String id = saveRowDirect("auth-e2e-sys", "system", null);

        // 1. admin 经 GraphQL grant（两个角色；revoke 其一后仍处收紧态——
        //    撤销全部记录会回到矩阵第 4 行开放态，属设计语义而非缺陷）
        grantViaGraphQL(id, ROLE_A);
        grantViaGraphQL(id, ROLE_C);

        // 2. 非授予角色用户绕过 BizModel 直调 provider：被拒（provider 层第二道 fail-closed）
        IUserContext.set(RoleUserContext.withRoles(ALICE, ROLE_B));
        NopException denied = assertThrows(NopException.class,
                () -> credentialProvider.getCredential(id));
        assertEquals("nop.err.credential.role-not-granted", denied.getErrorCode());

        // 3. 授予角色用户放行（provider 直调 + 一期消费链语义）
        IUserContext.set(RoleUserContext.withRoles("carol", ROLE_A));
        assertEquals("sk-auth-e2e-sys", credentialProvider.getCredential(id).getField("secretValue"));

        // 4. revoke 该角色后再拒（授权记录即时生效：DB 点查；ROLE_C 记录仍在 → 保持收紧态）
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        GraphQLResponseBean revoke = executeGraphQL(
                "mutation { NopCredentialAuth__revoke(credentialId: \"" + id + "\", roleId: \"" + ROLE_A + "\") }");
        assertFalse(revoke.hasError(), "revoke must succeed, errors=" + revoke.getErrors());

        IUserContext.set(RoleUserContext.withRoles("carol", ROLE_A));
        NopException deniedAgain = assertThrows(NopException.class,
                () -> credentialProvider.getCredential(id));
        assertEquals("nop.err.credential.role-not-granted", deniedAgain.getErrorCode(),
                "revoke takes effect immediately (DB point-query at the plaintext exit)");

        // 5. 撤销全部记录 → 回到矩阵第 4 行开放态（一期行为，默认开放的对称性）
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        GraphQLResponseBean revokeAll = executeGraphQL(
                "mutation { NopCredentialAuth__revoke(credentialId: \"" + id + "\", roleId: \"" + ROLE_C + "\") }");
        assertFalse(revokeAll.hasError(), "revoke must succeed, errors=" + revokeAll.getErrors());
        IUserContext.set(RoleUserContext.withRoles(ALICE, ROLE_B));
        assertEquals("sk-auth-e2e-sys", credentialProvider.getCredential(id).getField("secretValue"),
                "revoking ALL grants reverts the credential to the open state (matrix row 4, phase-1 behavior)");
    }
}
