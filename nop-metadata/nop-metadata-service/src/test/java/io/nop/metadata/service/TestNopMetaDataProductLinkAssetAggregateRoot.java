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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-4/P1-5 回归测试（plan 2026-08-15-1913-2 Phase 3）：DataProduct 资产挂链聚合根校验 + 属主管线。
 *
 * <p>契约：
 * <ul>
 *   <li>P1-4：linkAsset/unlinkAsset 对不存在 dataProductId 显式抛聚合根错误（requireEntity →
 *       nop.err.dao.unknown-entity，携带 NopMetaDataProduct + id），与标签层
 *       nop.err.metadata.link-asset-not-found 语义区分；不产生孤儿 linkage 行。</li>
 *   <li>P1-5：linkAsset 创建路径走 TagLabel 属主 save 管线（bizObject invoke("save")，沿
 *       GlossaryTerm 传播先例）——Automated 标签 state=Suggested + approveStatus=SUBMITTED
 *       （真实触发提审，与 TagLabel save 管线行为一致；接线验证：SUBMITTED 只可能经
 *       submitForApproval 产生，只有属主管线会调用它）。</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaDataProductLinkAssetAggregateRoot extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    private static final String TEST_USER_ID = "u-dp-linkasset-autotest";

    /** wf 启动需要真实操作人（沿 TestNopMetaTagLabelApproval.ensureUser 注记）。 */
    private void ensureUser() {
        IEntityDao<NopAuthUser> userDao = daoProvider.daoFor(NopAuthUser.class);
        if (userDao.getEntityById(TEST_USER_ID) == null) {
            NopAuthUser user = userDao.newEntity();
            user.setUserName("dp-linkasset-autotest");
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

    /** P1-4：linkAsset 对不存在 dataProductId 显式失败（聚合根层），且不产生孤儿 linkage 行。 */
    @Test
    public void testLinkAssetNonExistentProductFailsNoOrphanRow() {
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaDataProduct__linkAsset("
                        + "dataProductId: \"__no_such_product__\""
                        + ", entityType: \"NopMetaTable\""
                        + ", entityId: \"tbl-x\") { tagLabelId } }");
        assertTrue(resp.hasError(),
                "linkAsset on non-existent dataProductId must fail (was silently creating linkage row): " + resp);
        String errorCode = resp.getErrorCode();
        assertNotNull(errorCode, "error must carry errorCode: " + resp);
        assertTrue(errorCode.contains("nop.err.dao.unknown-entity"),
                "aggregate-root error must be entity-not-found, got: " + errorCode);
        assertFalse(errorCode.contains("link-asset"),
                "error must NOT be label-layer ERR_LINK_ASSET_NOT_FOUND, got: " + errorCode);

        // 不产生孤儿行：无 Automated 标签引用该 dataProductId
        assertEquals(0L, countAutomatedLabelsForProduct("__no_such_product__"),
                "no orphan linkage row may be created for non-existent dataProductId");
    }

    /**
     * P1-4：unlinkAsset 对不存在 dataProductId 抛聚合根错误（而非修复前因无 label 匹配抛的
     * ERR_LINK_ASSET_NOT_FOUND——错误语义修正为"产品不存在"）。
     */
    @Test
    public void testUnlinkAssetNonExistentProductFailsWithAggregateRootError() {
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaDataProduct__unlinkAsset("
                        + "dataProductId: \"__no_such_product__\""
                        + ", entityType: \"NopMetaTable\""
                        + ", entityId: \"tbl-x\") }");
        assertTrue(resp.hasError(),
                "unlinkAsset on non-existent dataProductId must fail with aggregate-root error: " + resp);
        String errorCode = resp.getErrorCode();
        assertNotNull(errorCode, "error must carry errorCode: " + resp);
        assertTrue(errorCode.contains("nop.err.dao.unknown-entity"),
                "non-existent product must surface entity-not-found (aggregate root), got: " + errorCode);
        assertFalse(errorCode.contains("link-asset"),
                "must NOT report label-layer ERR_LINK_ASSET_NOT_FOUND for missing product, got: " + errorCode);
    }

    /** P1-4 对偶："存在产品但无标签"场景错误码保持标签层语义（ERR_LINK_ASSET_NOT_FOUND）不变。 */
    @Test
    public void testUnlinkAssetExistingProductNoLabelKeepsLabelError() {
        String dataProductId = setupDomainAndProduct("dp-link-root-002", "bd-link-root-002");
        try {
            GraphQLResponseBean resp = execute(
                    "mutation { NopMetaDataProduct__unlinkAsset("
                            + "dataProductId: \"" + dataProductId + "\""
                            + ", entityType: \"NopMetaTable\""
                            + ", entityId: \"nonexistent\") }");
            assertTrue(resp.hasError(), "unlink of non-existent link must error: " + resp);
            String errorCode = resp.getErrorCode();
            assertNotNull(errorCode, "error must carry errorCode: " + resp);
            assertTrue(errorCode.contains("nop.err.metadata.link-asset-not-found"),
                    "existing product + missing label must stay label-layer semantics, got: " + errorCode);
        } finally {
            cleanup("dp-link-root-002", "bd-link-root-002");
        }
    }

    /**
     * P1-5 + 接线验证：linkAsset 创建路径经 TagLabel 属主 save 管线——Automated 标签
     * state=Suggested + approveStatus=SUBMITTED（自动提审真实触发；修复前直写 DAO 的标签
     * approveStatus 恒 null，永不进审批流）。与 GlossaryTerm 传播路径同构。
     */
    @Test
    public void testLinkAssetGoesThroughTagLabelOwnerPipeline() {
        ensureUser();
        String dataProductId = setupDomainAndProduct("dp-link-root-001", "bd-link-root-001");
        try {
            IServiceContext svcCtx = newServiceContext();
            GraphQLResponseBean resp = execute(
                    "mutation { NopMetaDataProduct__linkAsset("
                            + "dataProductId: \"" + dataProductId + "\""
                            + ", entityType: \"NopMetaTable\""
                            + ", entityId: \"tbl-pipeline-001\")"
                            + " { tagLabelId source labelType state approveStatus entityType entityId } }",
                    svcCtx);
            assertFalse(resp.hasError(), "linkAsset via owner pipeline should not error: " + resp);
            String result = String.valueOf(resp.getData());
            assertTrue(result.contains("Automated"), "labelType Automated: " + result);

            // 管线行为一致性：state=Suggested（管线设置，非手工 setState）
            // + approveStatus=SUBMITTED（submitForApproval 真实调用——接线验证：
            // SUBMITTED 只可能经属主管线的 triggerApprovalIfNeeded 产生）
            IEntityDao<NopMetaTagLabel> labelDao = daoProvider.daoFor(NopMetaTagLabel.class);
            NopMetaTagLabel label = findAutomatedLabel(labelDao, dataProductId, "NopMetaTable", "tbl-pipeline-001");
            assertNotNull(label, "linked label must be persisted");
            assertEquals("Suggested", label.getState(),
                    "owner pipeline must set state=Suggested for Automated label: " + label.getState());
            assertEquals("SUBMITTED", label.getApproveStatus(),
                    "owner pipeline must auto-submit for approval (approveStatus=SUBMITTED; was null when "
                            + "bypassing the pipeline): " + label.getApproveStatus());
        } finally {
            cleanup("dp-link-root-001", "bd-link-root-001");
        }
    }

    // ===== helpers =====

    private NopMetaTagLabel findAutomatedLabel(IEntityDao<NopMetaTagLabel> labelDao,
                                               String dataProductId, String entityType, String entityId) {
        String metadata = "{\"dataProductId\":\"" + dataProductId + "\"}";
        return labelDao.findAll().stream()
                .filter(l -> "Automated".equals(l.getLabelType()))
                .filter(l -> entityType.equals(l.getEntityType()))
                .filter(l -> entityId.equals(l.getEntityId()))
                .filter(l -> metadata.equals(l.getMetadata()))
                .findFirst().orElse(null);
    }

    private long countAutomatedLabelsForProduct(String dataProductId) {
        String metadata = "{\"dataProductId\":\"" + dataProductId + "\"}";
        return daoProvider.daoFor(NopMetaTagLabel.class).findAll().stream()
                .filter(l -> "Automated".equals(l.getLabelType()))
                .filter(l -> metadata.equals(l.getMetadata()))
                .count();
    }

    private String setupDomainAndProduct(String productId, String domainId) {
        IEntityDao<NopMetaBusinessDomain> domainDao = daoProvider.daoFor(NopMetaBusinessDomain.class);
        IEntityDao<NopMetaDataProduct> productDao = daoProvider.daoFor(NopMetaDataProduct.class);
        Timestamp now = new Timestamp(System.currentTimeMillis());

        NopMetaBusinessDomain domain = domainDao.newEntity();
        domain.setBusinessDomainId(domainId);
        domain.setName("LinkRootDomain-" + domainId);
        domain.setVersion(1L);
        domain.setCreatedBy("autotest");
        domain.setUpdatedBy("autotest");
        domain.setCreateTime(now);
        domain.setUpdateTime(now);
        domainDao.saveEntity(domain);

        NopMetaDataProduct product = productDao.newEntity();
        product.setDataProductId(productId);
        product.setBusinessDomainId(domainId);
        product.setName("LinkRootProduct-" + productId);
        product.setVersion(1L);
        product.setCreatedBy("autotest");
        product.setUpdatedBy("autotest");
        product.setCreateTime(now);
        product.setUpdateTime(now);
        productDao.saveEntity(product);
        return productId;
    }

    private void cleanup(String productId, String domainId) {
        // 每个测试方法独享独立 H2 内存库（NopAutoTest configLocalDb 按 case 生成 UUID 库名），
        // 无跨方法泄漏面；label 行随库销毁，无需显式清理（labelDao 直删会因跨 session 报
        // entity-not-in-session，且不必要）。仅删聚合根行保持与既有测试相同的收尾形态。
        execute("mutation { NopMetaDataProduct__delete(id: \"" + productId + "\") }");
        execute("mutation { NopMetaBusinessDomain__delete(id: \"" + domainId + "\") }");
    }

    private IServiceContext newServiceContext() {
        ServiceContextImpl ctx = new ServiceContextImpl();
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(TEST_USER_ID);
        userContext.setUserName("dp-linkasset-autotest");
        ctx.setUserContext(userContext);
        return ctx;
    }

    private GraphQLResponseBean execute(String query) {
        return execute(query, null);
    }

    private GraphQLResponseBean execute(String query, IServiceContext svcCtx) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = svcCtx != null
                ? graphQLEngine.newGraphQLContext(request, svcCtx)
                : graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
