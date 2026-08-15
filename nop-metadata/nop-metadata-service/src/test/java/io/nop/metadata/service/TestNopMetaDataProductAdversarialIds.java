package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaBusinessDomain;
import io.nop.metadata.dao.entity.NopMetaDataProduct;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-21（plan 2026-08-16-0226-3 Phase 1）：DataProduct 三方法 metadata JSON 经 JsonTool 生成的对抗验证。
 *
 * <p>修复前三处手工拼接 {@code "{\"dataProductId\":\"" + dataProductId + "\"}"}——dataProductId 含
 * {@code "} / {@code \} / 控制字符时 JSON 损坏（写入坏行 + unlink/getLinked 的 eq 整串匹配失真）。
 *
 * <p>覆盖：(a) 坏字符 ID 端到端 round-trip（linkAsset 写入合法 JSON 可 parse 回 → getLinkedAssets
 * eq 命中 → unlinkAsset eq 匹配删除）；(b) 正常 ID 输出与旧手工拼接逐字节一致（存量行兼容）；
 * (c) linkAsset 幂等（同 ID 二次 link 复用既有行——eq 匹配在 JsonTool 输出上成立）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaDataProductAdversarialIds extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    private static final String TEST_USER_ID = "u-dp-adv-autotest";

    /** dataProductId 含双引号、反斜杠、换行控制字符——旧拼接必损坏的对抗向量。 */
    private static final String ADVERSARIAL_ID = "dp-\"ad\\v\nx\"";

    private void ensureUser() {
        IEntityDao<NopAuthUser> userDao = daoProvider.daoFor(NopAuthUser.class);
        if (userDao.getEntityById(TEST_USER_ID) == null) {
            NopAuthUser user = userDao.newEntity();
            user.setUserName("dp-adv-autotest");
            user.setUserId(TEST_USER_ID);
            user.setNickName(user.getUserName());
            user.setPassword("123");
            user.setOpenId(TEST_USER_ID);
            user.setUserType(1);
            user.setStatus(1);
            user.setGender(1);
            user.setTenantId("0");
            userDao.saveEntity(user);
        }
    }

    private void setupDomainAndProduct(String dataProductId) {
        IEntityDao<NopMetaBusinessDomain> domainDao = daoProvider.daoFor(NopMetaBusinessDomain.class);
        IEntityDao<NopMetaDataProduct> productDao = daoProvider.daoFor(NopMetaDataProduct.class);
        Timestamp now = new Timestamp(System.currentTimeMillis());

        NopMetaBusinessDomain domain = domainDao.newEntity();
        domain.setBusinessDomainId("bd-adv-001");
        domain.setName("AdversarialDomain");
        domain.setVersion(1L);
        domain.setCreatedBy("autotest");
        domain.setUpdatedBy("autotest");
        domain.setCreateTime(now);
        domain.setUpdateTime(now);
        domainDao.saveEntity(domain);

        NopMetaDataProduct product = productDao.newEntity();
        product.setDataProductId(dataProductId);
        product.setBusinessDomainId("bd-adv-001");
        product.setName("AdversarialProduct");
        product.setVersion(1L);
        product.setCreatedBy("autotest");
        product.setUpdatedBy("autotest");
        product.setCreateTime(now);
        product.setUpdateTime(now);
        productDao.saveEntity(product);
    }

    /** GraphQL 字符串字面量转义（仅测试入参构造用；生产修复点在 BizModel 的 JsonTool 生成）。 */
    private static String graphqlEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private GraphQLResponseBean execute(String query) {
        ensureUser();
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IServiceContext svcCtx = new ServiceContextImpl();
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(TEST_USER_ID);
        userContext.setUserName("dp-adv-autotest");
        svcCtx.setUserContext(userContext);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request, svcCtx);
        return graphQLEngine.executeGraphQL(context);
    }

    private List<NopMetaTagLabel> automatedLabels() {
        IEntityDao<NopMetaTagLabel> labelDao = daoProvider.daoFor(NopMetaTagLabel.class);
        return labelDao.findAll().stream()
                .filter(l -> "Automated".equals(l.getLabelType()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * (a) 坏字符 ID 端到端：linkAsset 写入的 metadata 是合法 JSON（parse 回 dataProductId 原值），
     * getLinkedAssets eq 匹配命中，unlinkAsset eq 匹配删除——修复前该链路在坏字符 ID 下断裂
     * （拼接损坏 → unlink 永远 ERR_LINK_ASSET_NOT_FOUND，行成孤儿）。
     */
    @Test
    public void testAdversarialIdEndToEndRoundTrip() {
        setupDomainAndProduct(ADVERSARIAL_ID);
        String gqId = graphqlEscape(ADVERSARIAL_ID);

        // link：metadata 必须是合法 JSON 且 round-trip 回原 ID
        GraphQLResponseBean linkResp = execute(
                "mutation { NopMetaDataProduct__linkAsset(dataProductId: \"" + gqId + "\""
                        + ", entityType: \"NopMetaTable\", entityId: \"adv-table-001\") { tagLabelId metadata } }");
        assertFalse(linkResp.hasError(), "linkAsset with adversarial id should not error: " + linkResp);

        List<NopMetaTagLabel> labels = automatedLabels();
        assertEquals(1, labels.size(), "exactly one Automated label row expected");
        NopMetaTagLabel label = labels.get(0);
        assertNotNull(label.getMetadata(), "metadata must be written");

        Map<String, Object> parsed = parse(label.getMetadata());
        assertEquals(ADVERSARIAL_ID, parsed.get("dataProductId"),
                "metadata must be valid JSON that round-trips the adversarial dataProductId");

        // getLinkedAssets：eq 整串匹配在 JsonTool 输出上命中
        GraphQLResponseBean getResp = execute(
                "query { NopMetaDataProduct__getLinkedAssets(dataProductId: \"" + gqId + "\") { entityType entityId } }");
        assertFalse(getResp.hasError(), "getLinkedAssets should match: " + getResp);
        assertTrue(getResp.getData().toString().contains("adv-table-001"),
                "getLinkedAssets must find the row via eq metadata match: " + getResp.getData());

        // unlink：eq 匹配删除成功（修复前损坏 JSON 无法被 unlink 匹配到）
        GraphQLResponseBean unlinkResp = execute(
                "mutation { NopMetaDataProduct__unlinkAsset(dataProductId: \"" + gqId + "\""
                        + ", entityType: \"NopMetaTable\", entityId: \"adv-table-001\") }");
        assertFalse(unlinkResp.hasError(), "unlinkAsset must match and delete: " + unlinkResp);
        assertEquals(0, automatedLabels().size(), "label row must be deleted after unlink");
    }

    /**
     * (b) 正常 ID 输出逐字节等价：JsonTool 紧凑输出与旧手工拼接对常规 ID 完全一致
     * ——存量行（旧代码写入）eq 匹配与新代码等价，兼容性成立。
     */
    @Test
    public void testNormalIdByteEquivalenceWithLegacyConcatenation() {
        String normalId = "dp-link-001";
        String legacyManual = "{\"dataProductId\":\"" + normalId + "\"}";
        String jsonTool = JsonTool.stringify(Map.of("dataProductId", normalId));
        assertEquals(legacyManual, jsonTool,
                "JsonTool output must be byte-identical to legacy manual concatenation for normal ids");
    }

    /**
     * (c) 幂等回归：正常 ID 重复 linkAsset 只产一行（eq 匹配在 JsonTool 输出上去重）。
     */
    @Test
    public void testNormalIdIdempotentLink() {
        setupDomainAndProduct("dp-norm-001");
        String q = "mutation { NopMetaDataProduct__linkAsset(dataProductId: \"dp-norm-001\""
                + ", entityType: \"NopMetaTable\", entityId: \"norm-table-001\") { tagLabelId } }";
        GraphQLResponseBean resp1 = execute(q);
        GraphQLResponseBean resp2 = execute(q);
        assertFalse(resp1.hasError(), "first link should succeed: " + resp1);
        assertFalse(resp2.hasError(), "second link should succeed: " + resp2);
        assertEquals(1, automatedLabels().size(), "linkAsset must stay idempotent (eq dedup)");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        Object parsed = JsonTool.parse(json);
        assertTrue(parsed instanceof Map, "metadata must parse to a JSON object: " + json);
        return (Map<String, Object>) parsed;
    }
}
