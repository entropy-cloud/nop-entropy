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
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.crypto.CredentialCipher;
import io.nop.credential.dao.entity.NopCredential;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W11 Phase 2：{@link NopCredentialBizModel} 归属过滤/写分级/继承动作面收口的
 * <b>GraphQL 入口级</b>端到端 AutoTest（经 GraphQL 引擎驱动，非 BizModel 直调）。
 *
 * <p>覆盖：普通用户建 user 级凭证（ownerId 强制本人）/ 管理员代建（createdBy≠ownerId）/
 * system 级创建限管理员 / 可见集边界（system+NULL ∨ 本人 user 级）/ 越权单条归一"不存在"
 * （get/maskList/test）/ 六旁路动作收口 / 归属不可变 / 写分级 / 两层防御（BizModel 绕过时
 * provider 仍拦截）/ reencryptAll 与 usage 查询面限管理员。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopCredentialOwnershipBizModel extends JunitBaseTestCase {

    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String ADMIN = "cred-admin";

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

    private GraphQLResponseBean executeGraphQL(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataOf(GraphQLResponseBean response, String key) {
        Map<String, Object> data = (Map<String, Object>) response.getData();
        return (Map<String, Object>) data.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> itemsOf(GraphQLResponseBean response, String key) {
        return (List<Map<String, Object>>) dataOf(response, key).get("items");
    }

    private String saveCredentialViaGraphQL(String name, String scope, String ownerId, String id) {
        StringBuilder sb = new StringBuilder();
        sb.append("mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"")
                .append(name).append("\", fields: {apiKey: \"sk-").append(name).append("\"}");
        if (scope != null) {
            sb.append(", scope: \"").append(scope).append("\"");
        }
        if (ownerId != null) {
            sb.append(", ownerId: \"").append(ownerId).append("\"");
        }
        if (id != null) {
            sb.append(", id: \"").append(id).append("\"");
        }
        sb.append(") { credentialId scope ownerId } }");
        GraphQLResponseBean response = executeGraphQL(sb.toString());
        assertFalse(response.hasError(), "saveCredential must succeed, errors=" + response.getErrors());
        return (String) dataOf(response, "NopCredential__saveCredential").get("credentialId");
    }

    private String saveRowDirect(String id, String scope, String ownerId) {
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        NopCredential entity = dao.newEntity();
        entity.setCredentialId(id);
        entity.setName(id);
        entity.setTypeName("openai-api-key");
        entity.setStatus("enabled");
        entity.setDelFlag((byte) 0);
        entity.setVersion(1);
        entity.setScope(scope);
        entity.setOwnerId(ownerId);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("apiKey", "sk-" + id);
        entity.setData(credentialCipher.encrypt(JsonTool.stringify(fields)));
        dao.saveEntityDirectly(entity);
        return id;
    }

    private NopCredential reload(String id) {
        return daoProvider.daoFor(NopCredential.class).getEntityById(id);
    }

    // ==================== 普通用户建 user 级凭证（GraphQL 入口级） ====================

    @Test
    public void normalUserCreatesOwnUserLevelCredential() {
        IUserContext.set(RoleUserContext.plain(ALICE));

        GraphQLResponseBean response = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"alice-key\", "
                        + "fields: {apiKey: \"sk-alice\"}, scope: \"user\") { credentialId scope ownerId } }");
        assertFalse(response.hasError(),
                "normal user must be able to create own user-level credential, errors=" + response.getErrors());

        String id = (String) dataOf(response, "NopCredential__saveCredential").get("credentialId");
        NopCredential row = reload(id);
        assertEquals("user", row.getScope());
        assertEquals(ALICE, row.getOwnerId(), "ownerId must be forced to the logged-in user");
    }

    @Test
    public void normalUserCannotSpecifyOthersAsOwner() {
        IUserContext.set(RoleUserContext.plain(ALICE));

        // 传入他人 ownerId：强制等于当前登录用户（设计 §5.3"不可指定他人"）
        String id = saveCredentialViaGraphQL("alice-forced", "user", BOB, null);
        NopCredential row = reload(id);
        assertEquals(ALICE, row.getOwnerId(), "ownerId input for others must be forced to current user");
    }

    @Test
    public void normalUserCannotCreateSystemLevel() {
        IUserContext.set(RoleUserContext.plain(ALICE));

        GraphQLResponseBean explicit = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"x\", "
                        + "fields: {apiKey: \"sk-x\"}, scope: \"system\") { credentialId } }");
        assertTrue(explicit.hasError(), "system-level creation must be denied for non-admin");
        assertEquals("nop.err.credential.admin-required", explicit.getErrorCode());

        // 缺省不传 scope = system：同样限管理员（一期行为只保留给无登录态内部调用）
        GraphQLResponseBean implicit = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"y\", "
                        + "fields: {apiKey: \"sk-y\"}) { credentialId } }");
        assertTrue(implicit.hasError(), "default(system) creation must be denied for logged-in non-admin");
        assertEquals("nop.err.credential.admin-required", implicit.getErrorCode());
    }

    // ==================== 管理员创建与代建 ====================

    @Test
    public void adminCreatesSystemLevelAndUserLevelForOther() {
        IUserContext.set(RoleUserContext.admin(ADMIN));

        // system 级（缺省）
        String sysId = saveCredentialViaGraphQL("admin-sys", null, null, null);
        assertEquals("system", reload(sysId).getScope());
        assertNull(reload(sysId).getOwnerId(), "system-level credential must have empty ownerId");

        // 代建 user 级（指定他人 owner；审计载体 createdBy≠ownerId——测试环境无登录 IContext
        // 时 createdBy 为 ORM sys 用户名，同为"非 owner"的自证）
        String delegatedId = saveCredentialViaGraphQL("admin-for-bob", "user", BOB, null);
        NopCredential row = reload(delegatedId);
        assertEquals("user", row.getScope());
        assertEquals(BOB, row.getOwnerId());
        assertFalse(BOB.equals(row.getCreatedBy()),
                "admin delegation audit: createdBy must differ from ownerId (createdBy=" + row.getCreatedBy() + ")");
    }

    @Test
    public void userLevelWithoutOwnerIdRejected() {
        IUserContext.set(RoleUserContext.admin(ADMIN));

        GraphQLResponseBean response = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"x\", "
                        + "fields: {apiKey: \"sk-x\"}, scope: \"user\") { credentialId } }");
        assertTrue(response.hasError(), "admin creating user-level must explicitly specify ownerId");
        assertEquals("nop.err.credential.owner-required", response.getErrorCode());
    }

    @Test
    public void systemWithOwnerIdRejected() {
        IUserContext.set(RoleUserContext.admin(ADMIN));

        GraphQLResponseBean response = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"x\", "
                        + "fields: {apiKey: \"sk-x\"}, scope: \"system\", ownerId: \"" + BOB + "\") { credentialId } }");
        assertTrue(response.hasError(), "system-level credential must not carry ownerId");
        assertEquals("nop.err.credential.owner-not-allowed", response.getErrorCode());
    }

    @Test
    public void invalidScopeRejected() {
        IUserContext.set(RoleUserContext.admin(ADMIN));

        GraphQLResponseBean response = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"x\", "
                        + "fields: {apiKey: \"sk-x\"}, scope: \"team\") { credentialId } }");
        assertTrue(response.hasError(), "scope value domain is system|user");
        assertEquals("nop.err.credential.invalid-scope", response.getErrorCode());
    }

    // ==================== 可见性边界（读类结构性过滤） ====================

    @Test
    public void normalUserVisibilityBoundary() {
        saveRowDirect("vis-sys-null", null, null);      // 存量 NULL scope 行
        saveRowDirect("vis-sys", "system", null);
        saveRowDirect("vis-mine", "user", ALICE);
        saveRowDirect("vis-others", "user", BOB);

        // 普通用户 alice：system + NULL + 自己的 user 级；不见 bob 的
        IUserContext.set(RoleUserContext.plain(ALICE));
        GraphQLResponseBean resp = executeGraphQL(
                "query { NopCredential__findPage { items { credentialId scope ownerId } } }");
        assertFalse(resp.hasError(), "findPage must succeed, errors=" + resp.getErrors());
        List<Map<String, Object>> items = itemsOf(resp, "NopCredential__findPage");
        List<String> ids = items.stream().map(i -> (String) i.get("credentialId")).sorted().collect(java.util.stream.Collectors.toList());
        assertTrue(ids.contains("vis-sys-null"), "legacy NULL-scope row must stay visible (NULL = system)");
        assertTrue(ids.contains("vis-sys"), "system row must be visible");
        assertTrue(ids.contains("vis-mine"), "own user-level row must be visible");
        assertFalse(ids.contains("vis-others"), "other's user-level row must be invisible");

        // 管理员：全部可见
        IUserContext.set(RoleUserContext.admin(ADMIN));
        GraphQLResponseBean adminResp = executeGraphQL(
                "query { NopCredential__findPage { items { credentialId } } }");
        List<String> adminIds = itemsOf(adminResp, "NopCredential__findPage").stream()
                .map(i -> (String) i.get("credentialId")).collect(java.util.stream.Collectors.toList());
        assertTrue(adminIds.contains("vis-others"), "admin must see all rows");
    }

    @Test
    public void findListInheritsSameFilter() {
        saveRowDirect("fl-others", "user", BOB);
        IUserContext.set(RoleUserContext.plain(ALICE));

        GraphQLResponseBean resp = executeGraphQL(
                "query { NopCredential__findList { credentialId } }");
        assertFalse(resp.hasError(), "errors=" + resp.getErrors());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("NopCredential__findList");
        List<String> ids = list.stream().map(i -> (String) i.get("credentialId")).collect(java.util.stream.Collectors.toList());
        assertFalse(ids.contains("fl-others"), "findList must apply the same structural filter as findPage");
    }

    // ==================== 越权单条归一"不存在" ====================

    @Test
    public void unauthorizedGetNormalizedAsNotFound() {
        saveRowDirect("norm-others", "user", BOB);
        IUserContext.set(RoleUserContext.plain(ALICE));

        GraphQLResponseBean resp = executeGraphQL(
                "query { NopCredential__get(id: \"norm-others\") { credentialId } }");
        assertTrue(resp.hasError(), "unauthorized get must fail");
        assertEquals("nop.err.dao.unknown-entity", resp.getErrorCode(),
                "unauthorized get must be normalized as not-found (no ownership leak)");
    }

    @Test
    public void maskListInvisibleNormalizedAsNotFound() {
        saveRowDirect("mask-others", "user", BOB);
        IUserContext.set(RoleUserContext.plain(ALICE));

        GraphQLResponseBean resp = executeGraphQL(
                "query { NopCredential__maskList(ids: [\"mask-others\"]) { fields } }");
        assertTrue(resp.hasError(), "invisible maskList must fail");
        assertEquals("nop.err.credential.not-found", resp.getErrorCode(),
                "BizModel pre-check must normalize as NOT_FOUND before provider (no ownership leak)");
    }

    @Test
    public void testInvisibleNormalizedAsNotFound() {
        saveRowDirect("test-others", "user", BOB);
        IUserContext.set(RoleUserContext.plain(ALICE));

        GraphQLResponseBean resp = executeGraphQL(
                "mutation { NopCredential__test(id: \"test-others\") { success } }");
        assertTrue(resp.hasError(), "invisible test must fail");
        assertEquals("nop.err.credential.not-found", resp.getErrorCode(),
                "BizModel pre-check must normalize as NOT_FOUND before provider");
    }

    // ==================== 六旁路动作收口 ====================

    @Test
    public void sixBypassActionsClosed() {
        IUserContext.set(RoleUserContext.admin(ADMIN));

        GraphQLResponseBean update = executeGraphQL(
                "mutation { NopCredential__update(data: {credentialId:\"x\", name:\"y\"}) { credentialId } }");
        assertTrue(update.hasError(), "standard update must be disabled");

        GraphQLResponseBean batchDelete = executeGraphQL(
                "mutation { NopCredential__batchDelete(ids: [\"x\"]) }");
        assertTrue(batchDelete.hasError(), "standard batchDelete must be disabled");

        GraphQLResponseBean updateByQuery = executeGraphQL(
                "mutation { NopCredential__updateByQuery(query: {}, data: {name: \"z\"}) }");
        assertTrue(updateByQuery.hasError(), "updateByQuery must be disabled (prepareQuery bypass)");

        GraphQLResponseBean deleteByQuery = executeGraphQL(
                "mutation { NopCredential__deleteByQuery(query: {}) }");
        assertTrue(deleteByQuery.hasError(), "deleteByQuery must be disabled (usage-count bypass)");

        GraphQLResponseBean copyForNew = executeGraphQL(
                "mutation { NopCredential__copyForNew(data: {credentialId: \"x\"}) { credentialId } }");
        assertTrue(copyForNew.hasError(), "copyForNew must be disabled (ciphertext row duplication)");
    }

    @Test
    public void batchGetAppliesVisibilityFilter() {
        saveRowDirect("bg-sys", "system", null);
        saveRowDirect("bg-others", "user", BOB);
        IUserContext.set(RoleUserContext.plain(ALICE));

        GraphQLResponseBean resp = executeGraphQL(
                "query { NopCredential__batchGet(ids: [\"bg-sys\", \"bg-others\"]) { credentialId } }");
        assertFalse(resp.hasError(), "errors=" + resp.getErrors());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("NopCredential__batchGet");
        List<String> ids = list.stream().map(i -> (String) i.get("credentialId")).collect(java.util.stream.Collectors.toList());
        assertTrue(ids.contains("bg-sys"), "visible row must be returned");
        assertFalse(ids.contains("bg-others"), "invisible row must be filtered out (batchGet 走过滤语义)");
    }

    // ==================== 归属不可变 + 写分级 ====================

    @Test
    public void ownershipImmutableOnUpdate() {
        IUserContext.set(RoleUserContext.plain(ALICE));
        String id = saveCredentialViaGraphQL("immutable-own", "user", null, null);

        // 显式传等值 scope：放行（NULL→显式规范化只作用于 system 行）
        IUserContext.set(RoleUserContext.plain(ALICE));
        GraphQLResponseBean sameScope = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"immutable-own\", "
                        + "fields: {apiKey: \"sk-2\"}, id: \"" + id + "\", scope: \"user\") { credentialId } }");
        assertFalse(sameScope.hasError(), "passing identical scope on update must be allowed, errors="
                + sameScope.getErrors());

        // scope 变更 → 拒绝
        GraphQLResponseBean scopeChange = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"immutable-own\", "
                        + "fields: {apiKey: \"sk-2\"}, id: \"" + id + "\", scope: \"system\") { credentialId } }");
        assertTrue(scopeChange.hasError(), "scope change must be rejected (immutable)");
        assertEquals("nop.err.credential.ownership-immutable", scopeChange.getErrorCode());

        // ownerId 变更 → 拒绝（即便管理员）
        IUserContext.set(RoleUserContext.admin(ADMIN));
        GraphQLResponseBean ownerChange = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"immutable-own\", "
                        + "fields: {apiKey: \"sk-2\"}, id: \"" + id + "\", ownerId: \"" + BOB + "\") { credentialId } }");
        assertTrue(ownerChange.hasError(), "owner transfer must be rejected (immutable)");
        assertEquals("nop.err.credential.ownership-immutable", ownerChange.getErrorCode());

        // 缺省不传 = 保持不变
        IUserContext.set(RoleUserContext.plain(ALICE));
        GraphQLResponseBean keep = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"immutable-own2\", "
                        + "fields: {apiKey: \"sk-3\"}, id: \"" + id + "\") { credentialId } }");
        assertFalse(keep.hasError(), "update without scope/ownerId keeps ownership, errors=" + keep.getErrors());
        assertEquals("user", reload(id).getScope());
        assertEquals(ALICE, reload(id).getOwnerId());
    }

    @Test
    public void writeGradingOnUpdateAndDelete() {
        String sysId = saveRowDirect("wg-sys", "system", null);
        String bobId = saveRowDirect("wg-bob", "user", BOB);

        // 普通用户改 system 级 → 与"不存在"不可区分（D1-03/D4-07：三态归一
        // UnknownEntityException——越权保存与不存在目标对外响应一致，防 credentialId 枚举探测）
        IUserContext.set(RoleUserContext.plain(ALICE));
        GraphQLResponseBean sysUpdate = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"n\", "
                        + "fields: {apiKey: \"sk\"}, id: \"" + sysId + "\") { credentialId } }");
        assertTrue(sysUpdate.hasError(), "non-admin updating system credential must be denied");
        assertEquals("nop.err.dao.unknown-entity", sysUpdate.getErrorCode(),
                "unauthorized saveCredential update must be normalized as not-found (D1-03/D4-07)");

        // 普通用户删 system 级 → admin-required（可见行，显式拒绝）
        GraphQLResponseBean sysDelete = executeGraphQL(
                "mutation { NopCredential__delete(id: \"" + sysId + "\") }");
        assertTrue(sysDelete.hasError(), "non-admin deleting system credential must be denied");
        assertEquals("nop.err.credential.admin-required", sysDelete.getErrorCode());

        // 普通用户改他人 user 级 → 与"不存在"不可区分（D1-03/D4-07：原 owner-or-admin 归一）
        GraphQLResponseBean otherUpdate = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"n\", "
                        + "fields: {apiKey: \"sk\"}, id: \"" + bobId + "\") { credentialId } }");
        assertTrue(otherUpdate.hasError(), "non-owner updating others' user-level credential must be denied");
        assertEquals("nop.err.dao.unknown-entity", otherUpdate.getErrorCode(),
                "unauthorized saveCredential update must be normalized as not-found (D1-03/D4-07)");

        // 普通用户删他人 user 级 → 归一"不存在"
        GraphQLResponseBean otherDelete = executeGraphQL(
                "mutation { NopCredential__delete(id: \"" + bobId + "\") }");
        assertTrue(otherDelete.hasError(), "non-owner deleting others' user-level credential must be denied");
        assertEquals("nop.err.dao.unknown-entity", otherDelete.getErrorCode(),
                "delete of invisible row must be normalized as not-found");

        // owner 改/删自己的 user 级 → 放行（delete 无引用时成功）
        String aliceId = saveRowDirect("wg-alice", "user", ALICE);
        IUserContext.set(RoleUserContext.plain(ALICE));
        GraphQLResponseBean ownUpdate = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"n2\", "
                        + "fields: {apiKey: \"sk-2\"}, id: \"" + aliceId + "\") { credentialId } }");
        assertFalse(ownUpdate.hasError(), "owner must be able to update own user-level credential, errors="
                + ownUpdate.getErrors());

        GraphQLResponseBean ownDelete = executeGraphQL(
                "mutation { NopCredential__delete(id: \"" + aliceId + "\") }");
        assertFalse(ownDelete.hasError(), "owner must be able to delete own user-level credential, errors="
                + ownDelete.getErrors());
        assertNotNull(reload(aliceId), "soft-deleted row stays in DB");
    }

    // ==================== D1-03/D4-07：写路径三态归一（不存在/不可见/越权不可区分） ====================

    /**
     * saveCredential 更新路径：不存在目标、越权目标（system 级非管理员 / 他人 user 级）
     * 三态对外统一 {@code nop.err.dao.unknown-entity}——credentialId 枚举探测无法区分
     * "凭证存在但无权"与"凭证不存在"（A1-audit D1-03/D4-07 收口，对齐 get/delete 先例）。
     */
    @Test
    public void saveCredentialUpdateThreeStatesIndistinguishable() {
        saveRowDirect("tri-sys", "system", null);
        saveRowDirect("tri-bob", "user", BOB);
        IUserContext.set(RoleUserContext.plain(ALICE));

        GraphQLResponseBean nonExistent = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"n\", "
                        + "fields: {apiKey: \"sk\"}, id: \"no-such-credential\") { credentialId } }");
        assertTrue(nonExistent.hasError(), "update on non-existent credential must fail");
        assertEquals("nop.err.dao.unknown-entity", nonExistent.getErrorCode());

        GraphQLResponseBean sysDenied = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"n\", "
                        + "fields: {apiKey: \"sk\"}, id: \"tri-sys\") { credentialId } }");
        assertTrue(sysDenied.hasError(), "non-admin update on system credential must fail");

        GraphQLResponseBean userDenied = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"n\", "
                        + "fields: {apiKey: \"sk\"}, id: \"tri-bob\") { credentialId } }");
        assertTrue(userDenied.hasError(), "non-owner update on others' user-level credential must fail");

        assertEquals(nonExistent.getErrorCode(), sysDenied.getErrorCode(),
                "non-existent and unauthorized(system) must be indistinguishable (D1-03/D4-07)");
        assertEquals(nonExistent.getErrorCode(), userDenied.getErrorCode(),
                "non-existent and unauthorized(user) must be indistinguishable (D1-03/D4-07)");
    }

    // ==================== 两层防御：绕过 BizModel 直调 provider 仍拦截 ====================

    @Test
    public void providerLayerStillBlocksWhenBizModelBypassed() {
        saveRowDirect("defense-bob", "user", BOB);
        IUserContext.set(RoleUserContext.plain(ALICE));

        NopException ex = org.junit.jupiter.api.Assertions.assertThrows(NopException.class,
                () -> credentialProvider.getCredential("defense-bob"));
        assertEquals("nop.err.credential.owner-only", ex.getErrorCode(),
                "provider-layer ownership check is the second fail-closed line");
    }

    // ==================== reencryptAll / usage 查询面限管理员 ====================

    @Test
    public void reencryptAllDeniedForNonAdmin() {
        IUserContext.set(RoleUserContext.plain(ALICE));
        GraphQLResponseBean resp = executeGraphQL("mutation { NopCredential__reencryptAll }");
        assertTrue(resp.hasError(), "reencryptAll must be admin-only");
        assertEquals("nop.err.credential.admin-required", resp.getErrorCode());
    }

    @Test
    public void usageQueryDeniedForNonAdminAndAllowedForAdmin() {
        IUserContext.set(RoleUserContext.plain(ALICE));
        GraphQLResponseBean denied = executeGraphQL(
                "query { NopCredentialUsage__findPage { items { usageId } } }");
        assertTrue(denied.hasError(), "usage query face must be admin-only");
        assertEquals("nop.err.credential.admin-required", denied.getErrorCode());

        IUserContext.set(RoleUserContext.admin(ADMIN));
        GraphQLResponseBean allowed = executeGraphQL(
                "query { NopCredentialUsage__findPage { items { usageId } } }");
        assertFalse(allowed.hasError(), "admin must query usage face, errors=" + allowed.getErrors());
    }

    // ==================== 一期零回归：无登录态内部调用 ====================

    @Test
    public void noLoginInternalCallsKeepPhase1Behavior() {
        IUserContext.set(null);

        // 无登录态：缺省创建 system 级（一期行为）
        String id = saveCredentialViaGraphQL("internal-sys", null, null, null);
        assertEquals("system", reload(id).getScope());

        // 无登录态：findPage 无过滤（provider 层仍守住明文出口）
        GraphQLResponseBean resp = executeGraphQL(
                "query { NopCredential__findPage { items { credentialId } } }");
        assertFalse(resp.hasError(), "errors=" + resp.getErrors());

        // 无登录态：system 级凭证取明文（一期消费链语义）
        Map<String, Object> fields = new HashMap<>();
        fields.put("apiKey", "sk-legacy");
        NopCredential legacy = reload(id);
        legacy.setData(credentialCipher.encrypt(JsonTool.stringify(fields)));
        daoProvider.daoFor(NopCredential.class).updateEntityDirectly(legacy);
        assertEquals("sk-legacy", credentialProvider.getCredential(id).getField("apiKey"));
    }
}
