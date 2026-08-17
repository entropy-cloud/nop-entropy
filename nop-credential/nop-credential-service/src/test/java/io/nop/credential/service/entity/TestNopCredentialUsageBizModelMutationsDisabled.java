package io.nop.credential.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.credential.api.ICredentialProvider;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D4-06（A1-audit successor，2026-08-17）：{@link NopCredentialUsageBizModel} mutation 面
 * 收口的 GraphQL 入口级测试（对齐 {@link TestNopCredentialOauthStateBizModel} 先例）——
 * 7 个标准 mutation（save/update/delete/batchDelete/updateByQuery/deleteByQuery/copyForNew）
 * 全部禁用（admin 亦不可达）：删除 usage 行可间接解锁被
 * {@code NopCredentialBizModel.delete} 引用计数拦截的凭证删除（绕过一期契约）；
 * 消费方登记/注销唯一合法通道 = SPI {@code registerUsage}/{@code unregisterUsage}（回归保护用例）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopCredentialUsageBizModelMutationsDisabled extends JunitBaseTestCase {

    private static final String ALICE = "alice";
    private static final String ADMIN = "cred-admin";

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    ICredentialProvider credentialProvider;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
    }

    // ==================== 测试基建（对齐 TestNopCredentialOwnershipBizModel 先例） ====================

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

    /** 落一行可直接引用的凭证（usage 前置校验 D6-03 要求 credentialId 存在），经 GraphQL 正规入口。 */
    private String seedCredential(String id) {
        GraphQLResponseBean resp = executeGraphQL(String.format(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"%s\", "
                        + "fields: {apiKey: \"sk-%s\"}) { credentialId } }", id, id));
        assertFalse(resp.hasError(), "seed credential must succeed, errors=" + resp.getErrors());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        return (String) ((Map<String, Object>) data.get("NopCredential__saveCredential")).get("credentialId");
    }

    // ==================== 全部标准 mutation 旁路收口（admin 亦不可达） ====================

    @Test
    public void standardBypassActionsClosed() {
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));

        GraphQLResponseBean save = executeGraphQL(
                "mutation { NopCredentialUsage__save(data: {credentialId: \"x\", consumerRef: \"y\"}) { usageId } }");
        assertTrue(save.hasError(), "standard save must be disabled (forged usage rows, D4-06)");

        GraphQLResponseBean update = executeGraphQL(
                "mutation { NopCredentialUsage__update(data: {usageId: \"x\", credentialId: \"attacker-cred\"}) { usageId } }");
        assertTrue(update.hasError(), "standard update must be disabled (reference retargeting, D4-06)");

        GraphQLResponseBean delete = executeGraphQL(
                "mutation { NopCredentialUsage__delete(id: \"whatever\") }");
        assertTrue(delete.hasError(), "standard delete must be disabled (bypasses credential-deletion interception, D4-06)");

        GraphQLResponseBean batchDelete = executeGraphQL(
                "mutation { NopCredentialUsage__batchDelete(ids: [\"whatever\"]) }");
        assertTrue(batchDelete.hasError(), "standard batchDelete must be disabled (D4-06)");

        GraphQLResponseBean updateByQuery = executeGraphQL(
                "mutation { NopCredentialUsage__updateByQuery(query: {}, data: {credentialId: \"attacker-cred\"}) }");
        assertTrue(updateByQuery.hasError(), "updateByQuery must be disabled (D4-06)");

        GraphQLResponseBean deleteByQuery = executeGraphQL(
                "mutation { NopCredentialUsage__deleteByQuery(query: {}) }");
        assertTrue(deleteByQuery.hasError(), "deleteByQuery must be disabled (unlocks blocked deletions, D4-06)");

        GraphQLResponseBean copyForNew = executeGraphQL(
                "mutation { NopCredentialUsage__copyForNew(data: {credentialId: \"x\", consumerRef: \"y\"}) { usageId } }");
        assertTrue(copyForNew.hasError(), "copyForNew must be disabled (D4-06)");
    }

    // ==================== 非管理员的 mutation 面同样不可达（fail-closed 无论身份） ====================

    @Test
    public void nonAdminMutationsUnreachable() {
        IUserContext.set(RoleUserContext.plain(ALICE));

        GraphQLResponseBean deleteByQuery = executeGraphQL(
                "mutation { NopCredentialUsage__deleteByQuery(query: {}) }");
        assertTrue(deleteByQuery.hasError(), "non-admin deleteByQuery must fail closed (D4-06)");

        GraphQLResponseBean updateByQuery = executeGraphQL(
                "mutation { NopCredentialUsage__updateByQuery(query: {}, data: {credentialId: \"attacker-cred\"}) }");
        assertTrue(updateByQuery.hasError(), "non-admin updateByQuery must fail closed (D4-06)");
    }

    // ==================== 消费方 SPI 登记通道不受收口影响（回归保护） ====================

    /**
     * usage 行由消费方经 SPI 登记/注销（唯一合法通道）——mutation 收口后 SPI 路径不受影响，
     * 且"删除 usage 行解锁凭证删除"的旁路被堵死（delete 动作仍被引用计数拦截）。
     */
    @Test
    public void spiRegistrationUnaffectedAndBypassClosed() {
        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        String credentialId = seedCredential("d4-06-cred");

        // SPI 登记可用
        credentialProvider.registerUsage(credentialId, "consumer-d406");
        GraphQLResponseBean usagePage = executeGraphQL(
                "query { NopCredentialUsage__findPage { items { usageId credentialId consumerRef } } }");
        assertFalse(usagePage.hasError(), "admin usage query must succeed, errors=" + usagePage.getErrors());
        assertTrue(JsonTool.serialize(usagePage.getData(), false).contains("consumer-d406"),
                "SPI-registered usage row must be visible on the admin query face");

        // GraphQL 删除 usage 行（旁路）不可用 → 凭证删除仍被引用计数拦截（一期契约保持）
        GraphQLResponseBean credentialDelete = executeGraphQL(
                "mutation { NopCredential__delete(id: \"" + credentialId + "\") }");
        assertTrue(credentialDelete.hasError(),
                "credential deletion must stay blocked by reference count (D4-06 closes the bypass)");
        assertTrue("nop.err.credential.has-active-usage".equals(credentialDelete.getErrorCode()),
                "blocked by reference-count interception, got: " + credentialDelete.getErrorCode());

        // SPI 注销后删除恢复（完整生命周期回归）
        credentialProvider.unregisterUsage(credentialId, "consumer-d406");
        GraphQLResponseBean credentialDelete2 = executeGraphQL(
                "mutation { NopCredential__delete(id: \"" + credentialId + "\") }");
        assertFalse(credentialDelete2.hasError(),
                "after SPI unregister the credential deletion must succeed, errors=" + credentialDelete2.getErrors());
    }
}
