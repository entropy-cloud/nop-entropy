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
import io.nop.auth.api.mfa.MfaRequiredMeta;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.credential.crypto.CredentialCipher;
import io.nop.credential.dao.entity.NopCredential;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.GraphQLEngine;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C1b（A1-audit §二#4 终局裁定）：凭证库敏感动作 {@code @MfaRequired} 标注的
 * <b>容器级</b>元数据验证 + 零介入回归（可选语义不破坏）。
 *
 * <p><b>元数据断言优先容器级</b>（对齐 {@link TestNopCredentialAuthBizModel} 的
 * {@code @NopTestConfig + @Inject IGraphQLEngine} 先例）：经容器 schema loader
 * （{@code BizObjectManager}，含 xmeta/merge 管线）取 operation 定义断言
 * {@code mfaRequiredMeta}——纯反射级断言不足以证明元数据经构建管线后仍存活。
 *
 * <p>覆盖：
 * <ul>
 *   <li>四标注方法（{@code NopCredential__reencryptAll}/{@code NopCredential__delete}/
 *       {@code NopCredentialAuth__grant}/{@code NopCredentialAuth__revoke}）元数据可观察。</li>
 *   <li>负例（缩窄裁定不被执行期放宽）：{@code NopCredential__saveCredential}/
 *       {@code NopCredential__maskList} + {@code CredentialOAuthApi__beginOAuthFlow}
 *       （跨 BizModel 文件）零元数据。</li>
 *   <li>零介入回归：容器未装配 checker（nop-auth-service 不在 classpath）时引擎 checker
 *       为 null，四动作经 GraphQL 调用路径行为与标注前一致（不触发操作级检查、全部成功）。</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestCredentialMfaRequiredAnnotations extends JunitBaseTestCase {

    private static final String ADMIN = "mfa-annot-admin";
    private static final String ROLE_A = "role-mfa-annot";

    @Inject
    IGraphQLEngine graphQLEngine;

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

    /** 容器级 operation 定义（BizObjectManager——经 xmeta/merge 管线后的最终定义）。 */
    private GraphQLFieldDefinition operation(GraphQLOperationType opType, String name) {
        GraphQLFieldDefinition field = graphQLEngine.getSchemaLoader().getOperationDefinition(opType, name);
        assertNotNull(field, "operation definition must exist in container schema: " + name);
        return field;
    }

    private GraphQLResponseBean executeGraphQL(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    private String saveRowDirect(String id) {
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        NopCredential entity = dao.newEntity();
        entity.setCredentialId(id);
        entity.setName(id);
        entity.setTypeName("generic-secret");
        entity.setStatus("enabled");
        entity.setDelFlag((byte) 0);
        entity.setVersion(1);
        entity.setScope("system");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("secretValue", "sk-" + id);
        entity.setData(credentialCipher.encrypt(JsonTool.stringify(fields)));
        dao.saveEntityDirectly(entity);
        return id;
    }

    // ==================== 元数据传播（容器级，经 xmeta/merge 管线后仍存活） ====================

    @Test
    public void fourSensitiveActionsCarryMfaRequiredMeta() {
        assertSame(MfaRequiredMeta.INSTANCE,
                operation(GraphQLOperationType.mutation, "NopCredential__reencryptAll").getMfaRequiredMeta(),
                "reencryptAll must carry mfaRequiredMeta after container build pipeline");
        assertSame(MfaRequiredMeta.INSTANCE,
                operation(GraphQLOperationType.mutation, "NopCredential__delete").getMfaRequiredMeta(),
                "delete must carry mfaRequiredMeta after container build pipeline");
        assertSame(MfaRequiredMeta.INSTANCE,
                operation(GraphQLOperationType.mutation, "NopCredentialAuth__grant").getMfaRequiredMeta(),
                "grant must carry mfaRequiredMeta after container build pipeline");
        assertSame(MfaRequiredMeta.INSTANCE,
                operation(GraphQLOperationType.mutation, "NopCredentialAuth__revoke").getMfaRequiredMeta(),
                "revoke must carry mfaRequiredMeta after container build pipeline");
    }

    /**
     * 负例（缩窄裁定 A1 §二#4 不被执行期静默放宽）：saveCredential/beginOAuthFlow 裁定不标注
     * （高频用户操作 UX / state 一次性绑定已防劫持），maskList 为脱敏读动作。负例断言跨
     * 两个 BizModel 文件（NopCredentialBizModel + CredentialOAuthApiBizModel）。
     */
    @Test
    public void unannotatedActionsHaveNoMfaRequiredMeta() {
        assertNull(operation(GraphQLOperationType.mutation, "NopCredential__saveCredential").getMfaRequiredMeta(),
                "saveCredential must NOT carry mfaRequiredMeta (narrowing adjudication: high-frequency user action)");
        assertNull(operation(GraphQLOperationType.query, "NopCredential__maskList").getMfaRequiredMeta(),
                "maskList must NOT carry mfaRequiredMeta (masked read action)");
        assertNull(operation(GraphQLOperationType.mutation, "CredentialOAuthApi__beginOAuthFlow").getMfaRequiredMeta(),
                "beginOAuthFlow must NOT carry mfaRequiredMeta (one-time state binding already prevents hijacking)");
    }

    // ==================== 零介入回归（无 checker 装配时可选语义不破坏） ====================

    /**
     * 容器未装配 checker（nop-auth-service 不在 nop-credential-service classpath，
     * {@code OperationMfaCheckerImpl} bean 不存在 → {@code @Inject @Nullable} 注入 null）
     * 时：引擎 checker 为 null，四动作经 GraphQL 调用路径不触发操作级检查、行为与标注前
     * 完全一致（admin 场景全部成功）。
     */
    @Test
    public void zeroInterventionWithoutCheckerAssembled() {
        assertTrue(graphQLEngine instanceof GraphQLEngine,
                "container engine must be GraphQLEngine for checker accessor");
        assertNull(((GraphQLEngine) graphQLEngine).getOperationMfaChecker(),
                "no nop-auth-service on classpath → checker bean must be null (zero intervention)");

        IUserContext.set(RoleUserContext.withRoles(ADMIN, "admin"));
        String id = saveRowDirect("mfa-annot-cred");

        // grant / revoke：标注动作在无 checker 时行为不变（幂等往返成功）
        GraphQLResponseBean grant = executeGraphQL(
                "mutation { NopCredentialAuth__grant(credentialId: \"" + id + "\", roleId: \"" + ROLE_A + "\") }");
        assertFalse(grant.hasError(), "grant without checker must behave as before, errors=" + grant.getErrors());
        GraphQLResponseBean revoke = executeGraphQL(
                "mutation { NopCredentialAuth__revoke(credentialId: \"" + id + "\", roleId: \"" + ROLE_A + "\") }");
        assertFalse(revoke.hasError(), "revoke without checker must behave as before, errors=" + revoke.getErrors());

        // reencryptAll：标注动作在无 checker 时行为不变（active key 加密的行幂等跳过 → 0）
        GraphQLResponseBean reencrypt = executeGraphQL(
                "mutation { NopCredential__reencryptAll }");
        assertFalse(reencrypt.hasError(), "reencryptAll without checker must behave as before, errors="
                + reencrypt.getErrors());
        Map<String, Object> reencryptData = (Map<String, Object>) reencrypt.getData();
        assertTrue(Integer.valueOf(0).equals(reencryptData.get("NopCredential__reencryptAll")),
                "row already encrypted with active key → idempotent skip returns 0");

        // delete：标注动作在无 checker 时行为不变（无 usage 引用 → 成功软删除）
        GraphQLResponseBean delete = executeGraphQL(
                "mutation { NopCredential__delete(id: \"" + id + "\") }");
        assertFalse(delete.hasError(), "delete without checker must behave as before, errors=" + delete.getErrors());
        Map<String, Object> deleteData = (Map<String, Object>) delete.getData();
        assertTrue(Boolean.TRUE.equals(deleteData.get("NopCredential__delete")),
                "delete must return true (no usage references)");
    }
}
