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
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.credential.api.crypto.ICredentialKeyProvider;
import io.nop.credential.api.registry.CredentialType;
import io.nop.credential.crypto.DefaultCredentialKeyProvider;
import io.nop.credential.dao.entity.NopCredential;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NopCredentialBizModel} 的端到端 AutoTest，通过 GraphQL 引擎驱动。
 *
 * <p>核心验证（Plan Phase 1 Exit Criteria）：
 * <ul>
 *   <li>结构性明文边界：{@code saveCredential} 是唯一的明文输入路径；返回的实体、{@code findPage}、
 *       {@code get} 均不暴露 {@code data} 字段。</li>
 *   <li>端到端路径：用户输入 → 加密 → DB 存储 cv1: 密文 → API 响应（无明文） → SPI 解密（明文可恢复）</li>
 *   <li>{@code maskList}、{@code typeList}、{@code test}、{@code reencryptAll} 行为正确</li>
 *   <li>fail-closed（Rule #24）：null fields、未知 typeName、不存在凭证均抛异常</li>
 *   <li>标准 {@code save} 被禁用（抛 {@link UnsupportedOperationException}）</li>
 *   <li>幂等性：{@code reencryptAll} 对已经是 active key 的凭证跳过</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopCredentialBizModel extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    ICredentialProvider credentialProvider;

    @Inject
    ICredentialKeyProvider keyProvider;

    @Inject
    IDaoProvider daoProvider;

    /**
     * NopIoC 不自动按泛型参数解析 {@code IEntityDao<T>}，因此通过 {@link IDaoProvider#daoFor} 获取。
     */
    private IEntityDao<NopCredential> nopCredentialDao() {
        return daoProvider.daoFor(NopCredential.class);
    }

    /**
     * 执行 GraphQL query/mutation 字符串，返回响应。
     */
    private GraphQLResponseBean executeGraphQL(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    private String saveCredentialViaGraphQL(String typeName, String name, Map<String, Object> fields) {
        String query = String.format(
                "mutation { NopCredential__saveCredential(typeName: \"%s\", name: \"%s\", fields: %s) { credentialId name typeName } }",
                typeName, name, toGraphQLObject(fields));
        GraphQLResponseBean response = executeGraphQL(query);
        assertFalse(response.hasError(),
                "saveCredential must succeed, errors=" + response.getErrors());
        Map<String, Object> data = (Map<String, Object>) response.getData();
        Map<String, Object> saved = (Map<String, Object>) data.get("NopCredential__saveCredential");
        return (String) saved.get("credentialId");
    }

    /**
     * 将 {@code Map<String,Object>} 转换为 GraphQL 输入对象字面量（键不带引号，字符串值带双引号）。
     * 例如 {@code {apiKey:"sk-xxx"}}。GraphQL 不接受原始 JSON 作为输入对象。
     */
    private static String toGraphQLObject(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(e.getKey()).append(":\"").append(e.getValue()).append("\"");
        }
        return sb.append("}").toString();
    }

    // ==================== 端到端：明文边界 + 加密持久化 + SPI 解密 ====================

    @Test
    public void endToEndPlaintextBoundary() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("apiKey", "sk-test123-end-to-end");
        fields.put("orgId", "org-abc");

        String credentialId = saveCredentialViaGraphQL("openai-api-key", "e2e-cred", fields);

        // 1. findPage 响应不包含 plaintext 'sk-test123-end-to-end' 也不包含 data
        // 1a. 查询中显式包含 data 字段必须在解析阶段就失败（结构性边界：data 是 published=false，
        //     schema 中不存在，GraphQLSelectionResolver 直接抛 nop.err.graphql.undefined-field）
        NopException schemaEx = assertThrows(NopException.class, () -> executeGraphQL(
                "query { NopCredential__findPage { items { credentialId name typeName data } } }"),
                "querying the data field must fail at parse time because data is published=false in xmeta");
        assertEquals("nop.err.graphql.undefined-field", schemaEx.getErrorCode(),
                "expected undefined-field error for data prop, got: " + schemaEx.getMessage());

        // 1b. 合法查询（不包含 data）的响应中 items 必须不含 plaintext 也不含 data
        GraphQLResponseBean fpResp = executeGraphQL(
                "query { NopCredential__findPage { items { credentialId name typeName status } } }");
        assertFalse(fpResp.hasError(),
                "findPage without data field must succeed, errors=" + fpResp.getErrors());
        Map<String, Object> fpData = (Map<String, Object>) fpResp.getData();
        Map<String, Object> findPage = (Map<String, Object>) fpData.get("NopCredential__findPage");
        List<Map<String, Object>> items = (List<Map<String, Object>>) findPage.get("items");
        assertNotNull(items);
        assertFalse(items.isEmpty());
        for (Map<String, Object> item : items) {
            assertNull(item.get("data"),
                    "findPage items must not contain data field (published=false + setData(null)");
            assertFalse(JSON.stringify(item).contains("sk-test123-end-to-end"),
                    "findPage response must not leak plaintext apiKey");
        }

        // 2. get 响应不包含 plaintext
        GraphQLResponseBean getResp = executeGraphQL(
                "query { NopCredential__get(id: \"" + credentialId + "\") { credentialId name typeName } }");
        Map<String, Object> getData = (Map<String, Object>) getResp.getData();
        Map<String, Object> entity = (Map<String, Object>) getData.get("NopCredential__get");
        assertNotNull(entity);
        assertFalse(JSON.stringify(entity).contains("sk-test123-end-to-end"),
                "get response must not leak plaintext apiKey");

        // 3. SPI 解密恢复明文（证明数据已加密持久化）
        CredentialData recovered = credentialProvider.getCredential(credentialId);
        assertNotNull(recovered);
        assertEquals("sk-test123-end-to-end", recovered.getField("apiKey"),
                "SPI getCredential must recover plaintext apiKey");
        assertEquals("org-abc", recovered.getField("orgId"));
    }

    // ==================== 接线验证：DB 存储 cv1: 密文 ====================

    @Test
    public void saveCredentialStoresCv1CiphertextInDb() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("apiKey", "sk-wiring-test");
        String credentialId = saveCredentialViaGraphQL("openai-api-key", "wiring-cred", fields);

        // 直接读 DB 验证 data 是 cv1: 密文
        NopCredential entity = nopCredentialDao().getEntityById(credentialId);
        assertNotNull(entity);
        String data = entity.getData();
        assertNotNull(data, "data column must be populated");
        assertTrue(data.startsWith("cv1:"),
                "DB-stored data must start with cv1: prefix, got: " + data.substring(0, Math.min(20, data.length())));
        assertTrue(data.startsWith("cv1:keyA:"),
                "DB-stored data must use active keyId 'keyA'");
    }

    // ==================== 标准 save 被禁用 ====================

    @Test
    public void standardSaveIsDisabled() {
        GraphQLResponseBean response = executeGraphQL(
                "mutation { NopCredential__save(data: {credentialId:\"x\",name:\"y\",typeName:\"openai-api-key\"}) { credentialId } }");
        // 标准 save 在 BizModel 中抛 UnsupportedOperationException，GraphQL 层应当报错
        assertTrue(response.hasError(),
                "standard save must throw UnsupportedOperationException, response=" + JSON.stringify(response));
    }

    // ==================== fail-closed: null/empty fields 抛异常 ====================

    @Test
    public void saveCredentialWithNullFieldsThrows() {
        GraphQLResponseBean response = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"x\", fields: {}) { credentialId } }");
        assertTrue(response.hasError(),
                "saveCredential with empty fields must fail-closed, response=" + JSON.stringify(response));
    }

    @Test
    public void saveCredentialWithUnknownTypeThrows() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("foo", "bar");
        GraphQLResponseBean response = executeGraphQL(
                "mutation { NopCredential__saveCredential(typeName: \"non-existent-type\", name: \"x\", fields: "
                        + toGraphQLObject(fields) + ") { credentialId } }");
        assertTrue(response.hasError(),
                "saveCredential with unknown typeName must fail-closed, response=" + JSON.stringify(response));
    }

    // ==================== maskList ====================

    @Test
    public void maskListReturnsMaskedValues() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("apiKey", "sk-mask-list-secret");
        fields.put("orgId", "orgid-long-enough-to-truncate");
        String credentialId = saveCredentialViaGraphQL("openai-api-key", "mask-cred", fields);

        GraphQLResponseBean response = executeGraphQL(
                "query { NopCredential__maskList(ids: [\"" + credentialId + "\"]) { fields } }");
        assertFalse(response.hasError(),
                "maskList must succeed, errors=" + response.getErrors());

        Map<String, Object> data = (Map<String, Object>) response.getData();
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("NopCredential__maskList");
        assertNotNull(list);
        assertEquals(1, list.size());
        Map<String, Object> maskedFields = (Map<String, Object>) list.get(0).get("fields");
        assertEquals("****", maskedFields.get("apiKey"),
                "sensitive apiKey field must be masked as ****");
        assertFalse(((String) maskedFields.get("orgId")).contains("orgid-long-enough"),
                "non-sensitive long orgId field must be truncated (not full value)");
    }

    @Test
    public void maskListOnNonExistentThrows() {
        GraphQLResponseBean response = executeGraphQL(
                "query { NopCredential__maskList(ids: [\"no-such-credential-id\"]) { fields } }");
        assertTrue(response.hasError(),
                "maskList on non-existent credential must fail-closed, response=" + JSON.stringify(response));
    }

    // ==================== typeList ====================

    @Test
    public void typeListReturnsAllRegisteredTypesWithFieldSchema() {
        GraphQLResponseBean response = executeGraphQL(
                "query { NopCredential__typeList { name displayName authType fields { name label type sensitive required } } }");
        assertFalse(response.hasError(),
                "typeList must succeed, errors=" + response.getErrors());

        Map<String, Object> data = (Map<String, Object>) response.getData();
        List<Map<String, Object>> types = (List<Map<String, Object>>) data.get("NopCredential__typeList");
        assertNotNull(types);
        assertTrue(types.size() >= 2,
                "expected at least 2 types (openai-api-key + generic-secret), got " + types.size());

        // 验证 openai-api-key 字段 schema
        Map<String, Object> openaiType = types.stream()
                .filter(t -> "openai-api-key".equals(t.get("name")))
                .findFirst().orElse(null);
        assertNotNull(openaiType);
        List<Map<String, Object>> openaiFields = (List<Map<String, Object>>) openaiType.get("fields");
        assertNotNull(openaiFields);

        Map<String, Object> apiKeyField = openaiFields.stream()
                .filter(f -> "apiKey".equals(f.get("name")))
                .findFirst().orElse(null);
        assertNotNull(apiKeyField);
        assertEquals("password", apiKeyField.get("type"));
        assertEquals(true, apiKeyField.get("sensitive"));
        assertEquals(true, apiKeyField.get("required"));
    }

    // ==================== test action ====================

    @Test
    public void testActionReturnsExplicitNotImplementedResult() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("apiKey", "sk-test-action");
        String credentialId = saveCredentialViaGraphQL("openai-api-key", "test-cred", fields);

        GraphQLResponseBean response = executeGraphQL(
                "mutation { NopCredential__test(id: \"" + credentialId + "\") { success message testedAt } }");
        assertFalse(response.hasError(),
                "test must succeed, errors=" + response.getErrors());

        Map<String, Object> data = (Map<String, Object>) response.getData();
        Map<String, Object> result = (Map<String, Object>) data.get("NopCredential__test");
        assertNotNull(result);
        assertEquals(false, result.get("success"),
                "W2 test action must return success=false");
        assertNotNull(result.get("message"));
        assertFalse(((String) result.get("message")).isEmpty(),
                "message must not be empty (Rule #24)");

        // 验证 lastUsedAt 被更新（直接读 DB）
        NopCredential entity = nopCredentialDao().getEntityById(credentialId);
        assertNotNull(entity.getLastUsedAt(), "test action must update lastUsedAt");
        assertNotNull(entity.getTestResult(), "test action must update testResult");
    }

    @Test
    public void testOnNonExistentThrows() {
        GraphQLResponseBean response = executeGraphQL(
                "mutation { NopCredential__test(id: \"no-such-cred\") { success message } }");
        assertTrue(response.hasError(),
                "test on non-existent credential must fail-closed, response=" + JSON.stringify(response));
    }

    // ==================== reencryptAll ====================

    @Test
    public void reencryptAllReencryptsWithActiveKey() {
        // 用 keyA（默认 active）保存凭证
        Map<String, Object> fields = new HashMap<>();
        fields.put("apiKey", "sk-reencrypt-1");
        String id1 = saveCredentialViaGraphQL("openai-api-key", "reenc-1", fields);

        Map<String, Object> fields2 = new HashMap<>();
        fields2.put("apiKey", "sk-reencrypt-2");
        String id2 = saveCredentialViaGraphQL("openai-api-key", "reenc-2", fields2);

        // 验证初始为 keyA
        assertTrue(nopCredentialDao().getEntityById(id1).getData().startsWith("cv1:keyA:"));
        assertTrue(nopCredentialDao().getEntityById(id2).getData().startsWith("cv1:keyA:"));

        // 切换 active 为 keyB
        ((DefaultCredentialKeyProvider) keyProvider).setActiveKeyId("keyB");

        // 执行 reencryptAll
        GraphQLResponseBean response = executeGraphQL("mutation { NopCredential__reencryptAll }");
        assertFalse(response.hasError(),
                "reencryptAll must succeed, errors=" + response.getErrors());

        // 验证 DB 中数据已重写为 keyB
        assertTrue(nopCredentialDao().getEntityById(id1).getData().startsWith("cv1:keyB:"),
                "after reencryptAll, credential 1 must use keyB");
        assertTrue(nopCredentialDao().getEntityById(id2).getData().startsWith("cv1:keyB:"),
                "after reencryptAll, credential 2 must use keyB");

        // SPI 仍然能恢复明文
        assertEquals("sk-reencrypt-1", credentialProvider.getCredential(id1).getField("apiKey"));
        assertEquals("sk-reencrypt-2", credentialProvider.getCredential(id2).getField("apiKey"));

        // 再次调用 reencryptAll 应当跳过所有（幂等）
        GraphQLResponseBean response2 = executeGraphQL("mutation { NopCredential__reencryptAll }");
        assertFalse(response2.hasError(),
                "idempotent reencryptAll must succeed, errors=" + response2.getErrors());

        // 切回 keyA 以保证后续测试的一致性
        ((DefaultCredentialKeyProvider) keyProvider).setActiveKeyId("keyA");
    }

    // ==================== ICredentialProvider 直接调用覆盖 ====================

    @Test
    public void credentialProviderMaskIsConsistentWithBizModelMaskList() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("apiKey", "sk-direct-mask");
        String credentialId = saveCredentialViaGraphQL("openai-api-key", "direct-mask", fields);

        MaskedCredential masked = credentialProvider.mask(credentialId);
        assertNotNull(masked);
        assertEquals("****", masked.getFields().get("apiKey"));
    }

    // ==================== Phase 2: 删除引用计数拦截 ====================

    /**
     * 凭证存在活跃使用引用时，{@code delete} 必须 fail-closed（抛异常），凭证不被删除。
     */
    @Test
    public void deleteBlocksWhenUsageReferencesExist() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("apiKey", "sk-delete-blocked");
        String credentialId = saveCredentialViaGraphQL("openai-api-key", "delete-blocked-cred", fields);

        // 注册一条使用引用
        credentialProvider.registerUsage(credentialId, "consumer-delete-test");

        // 调用 delete mutation 应当失败
        GraphQLResponseBean response = executeGraphQL(
                "mutation { NopCredential__delete(id: \"" + credentialId + "\") }");
        assertTrue(response.hasError(),
                "delete with active usage references must fail-closed, response=" + JSON.stringify(response));
        // 错误码必须是 ERR_CREDENTIAL_HAS_ACTIVE_USAGE
        assertEquals("nop.err.credential.has-active-usage", response.getErrorCode(),
                "delete must fail with ERR_CREDENTIAL_HAS_ACTIVE_USAGE, errors=" + response.getErrors());

        // 凭证仍然可访问（未被删除）
        NopCredential entity = nopCredentialDao().getEntityById(credentialId);
        assertNotNull(entity, "credential must NOT be deleted when usage references exist");
        // 清理：注销引用以便后续测试
        credentialProvider.unregisterUsage(credentialId, "consumer-delete-test");
    }

    /**
     * 凭证无使用引用时，{@code delete} 成功：软删除（{@code delFlag=1}）+ 业务级禁用
     * （{@code status=disabled}）。{@code ICredentialProvider.getCredential} 对软删除凭证
     * fail-closed（抛 {@code ERR_CREDENTIAL_DELETED}）。
     */
    @Test
    public void deleteSucceedsWhenNoUsageReferences() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("apiKey", "sk-delete-ok");
        String credentialId = saveCredentialViaGraphQL("openai-api-key", "delete-ok-cred", fields);

        GraphQLResponseBean response = executeGraphQL(
                "mutation { NopCredential__delete(id: \"" + credentialId + "\") }");
        assertFalse(response.hasError(),
                "delete with no usage references must succeed, errors=" + response.getErrors());

        // 直接通过 DAO 读 DB 验证 delFlag 与 status
        NopCredential entity = nopCredentialDao().getEntityById(credentialId);
        assertNotNull(entity, "soft-deleted entity should still be physically present in DB");
        assertEquals(Byte.valueOf((byte) 1), entity.getDelFlag(),
                "ORM useLogicalDelete must set delFlag=1");
        assertEquals("disabled", entity.getStatus(),
                "prepareDelete callback must set status=disabled");

        // SPI 对软删除凭证 fail-closed
        NopException ex = assertThrows(NopException.class,
                () -> credentialProvider.getCredential(credentialId));
        assertEquals("nop.err.credential.deleted", ex.getErrorCode(),
                "getCredential on soft-deleted credential must throw ERR_CREDENTIAL_DELETED");
    }

    /**
     * 注册使用引用 → 删除失败 → 注销引用后删除成功（端到端引用计数生命周期）。
     */
    @Test
    public void deleteSucceedsAfterUnregisterUsage() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("apiKey", "sk-delete-after-unreg");
        String credentialId = saveCredentialViaGraphQL("openai-api-key", "unreg-then-delete", fields);

        // 注册引用 → 删除失败
        credentialProvider.registerUsage(credentialId, "consumer-A");
        GraphQLResponseBean blocked = executeGraphQL(
                "mutation { NopCredential__delete(id: \"" + credentialId + "\") }");
        assertTrue(blocked.hasError(),
                "delete with active usage must fail, response=" + JSON.stringify(blocked));

        // 注销引用 → 删除成功
        credentialProvider.unregisterUsage(credentialId, "consumer-A");
        GraphQLResponseBean ok = executeGraphQL(
                "mutation { NopCredential__delete(id: \"" + credentialId + "\") }");
        assertFalse(ok.hasError(),
                "delete after unregisterUsage must succeed, errors=" + ok.getErrors());

        NopCredential entity = nopCredentialDao().getEntityById(credentialId);
        assertNotNull(entity);
        assertEquals(Byte.valueOf((byte) 1), entity.getDelFlag(),
                "delFlag must be 1 after successful delete");
        assertEquals("disabled", entity.getStatus(),
                "status must be disabled after successful delete");
    }
}
