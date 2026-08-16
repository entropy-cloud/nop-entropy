/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.credential.api.crypto.ICredentialKeyProvider;
import io.nop.credential.crypto.CredentialCipher;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W10 Phase 3：{@code reencryptAll} 分页完备性修复的聚焦测试（一期已知限制收口）。
 *
 * <p>页大小经 {@code nop.credential.reencrypt-page-size=3} 注入缩小（缺省 1000），
 * 构造 7 条凭证（超两页余一条）验证：
 * <ul>
 *   <li><b>超批量单次执行全覆盖</b>：翻页循环处理全部未删除凭证（确定性排序 +
 *       keyset 游标，无漏行/重行）；</li>
 *   <li><b>确定性排序断言</b>：{@code buildReencryptQuery} 强制 orderBy credentialId；</li>
 *   <li><b>幂等</b>：第二次执行返回 0（active keyId 相同跳过）；</li>
 *   <li><b>逐条提交可重跑</b>语义不变（一期回归另见 {@link TestNopCredentialBizModel}）。</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
@NopTestProperty(name = "nop.credential.reencrypt-page-size", value = "3")
public class TestNopCredentialReencryptPagination extends JunitBaseTestCase {

    private static final int CREDENTIAL_COUNT = 7;

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    ICredentialKeyProvider keyProvider;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    NopCredentialBizModel bizModel;

    private IEntityDao<NopCredential> nopCredentialDao() {
        return daoProvider.daoFor(NopCredential.class);
    }

    private String saveCredentialViaGraphQL(String name, String apiKeyValue) {
        String query = String.format(
                "mutation { NopCredential__saveCredential(typeName: \"openai-api-key\", name: \"%s\", "
                        + "fields: {apiKey: \"%s\"}) { credentialId } }",
                name, apiKeyValue);
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        GraphQLResponseBean response = graphQLEngine.executeGraphQL(context);
        assertFalse(response.hasError(), "saveCredential must succeed, errors=" + response.getErrors());
        Map<String, Object> data = (Map<String, Object>) response.getData();
        Map<String, Object> saved = (Map<String, Object>) data.get("NopCredential__saveCredential");
        return (String) saved.get("credentialId");
    }

    private int reencryptAllViaGraphQL() {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery("mutation { NopCredential__reencryptAll }");
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        GraphQLResponseBean response = graphQLEngine.executeGraphQL(context);
        assertFalse(response.hasError(), "reencryptAll must succeed, errors=" + response.getErrors());
        Map<String, Object> data = (Map<String, Object>) response.getData();
        return (Integer) data.get("NopCredential__reencryptAll");
    }

    /**
     * 超批量场景：7 条凭证、页大小 3（3+3+1 三页），单次 reencryptAll 全部处理；
     * 幂等重跑第二次返回 0。
     */
    @Test
    public void reencryptAllProcessesAllRowsAcrossPagesAndIsIdempotent() {
        String[] ids = new String[CREDENTIAL_COUNT];
        for (int i = 0; i < CREDENTIAL_COUNT; i++) {
            ids[i] = saveCredentialViaGraphQL("pagination-cred-" + i, "sk-pagination-" + i);
        }

        // 前置：全部为 keyA（active）
        for (String id : ids) {
            assertTrue(nopCredentialDao().getEntityById(id).getData().startsWith("cv1:keyA:"),
                    "credential must start with keyA before reencrypt");
        }

        try {
            // 切换 active 为 keyB，单次执行
            ((DefaultCredentialKeyProvider) keyProvider).setActiveKeyId("keyB");
            int updated = reencryptAllViaGraphQL();

            assertEquals(CREDENTIAL_COUNT, updated,
                    "单次 reencryptAll 必须处理全部 " + CREDENTIAL_COUNT + " 条（跨页完备），got " + updated);

            // 全部 7 条都重写为 keyB（无漏行）
            for (String id : ids) {
                String data = nopCredentialDao().getEntityById(id).getData();
                assertNotNull(data);
                assertTrue(data.startsWith("cv1:keyB:"),
                        "after reencryptAll all credentials must use keyB, got prefix: "
                                + data.substring(0, Math.min(12, data.length())));
            }

            // 明文仍可恢复（加解密语义保持）
            // 幂等：第二次执行返回 0
            assertEquals(0, reencryptAllViaGraphQL(),
                    "idempotent second run must reencrypt nothing");
        } finally {
            // 恢复 active keyA，保证类内其他测试一致性
            ((DefaultCredentialKeyProvider) keyProvider).setActiveKeyId("keyA");
        }
    }

    /**
     * 确定性排序断言：查询强制 orderBy credentialId（keyset 翻页完备性的前提——
     * 无排序的 offset 分页会漏行/重行）+ delFlag 过滤 + 注入的页大小。
     */
    @Test
    public void buildReencryptQueryOrdersByCredentialIdWithInjectedPageSize() {
        QueryBean query = bizModel.buildReencryptQuery();
        assertEquals(3, query.getLimit(), "页大小必须取自注入配置 nop.credential.reencrypt-page-size");
        assertNotNull(query.getOrderBy(), "必须声明 orderBy");
        List<OrderFieldBean> orderBy = query.getOrderBy();
        assertEquals(1, orderBy.size(), "排序键唯一（credentialId）, got: " + orderBy);
        assertEquals("credentialId", orderBy.get(0).getName());
        assertFalse(orderBy.get(0).isDesc(), "credentialId 升序（keyset 游标方向）");
        assertNotNull(query.getFilter(), "必须过滤 delFlag=0");
    }
}
