package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Phase 3 DataProduct 资产关联（linkAsset/unlinkAsset/getLinkedAssets）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaDataProductLinkAsset extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    private String setupDomainAndProduct() {
        IEntityDao<NopMetaBusinessDomain> domainDao = daoProvider.daoFor(NopMetaBusinessDomain.class);
        IEntityDao<NopMetaDataProduct> productDao = daoProvider.daoFor(NopMetaDataProduct.class);
        Timestamp now = new Timestamp(System.currentTimeMillis());

        NopMetaBusinessDomain domain = domainDao.newEntity();
        domain.setBusinessDomainId("bd-link-001");
        domain.setName("LinkTestDomain");
        domain.setVersion(1L);
        domain.setCreatedBy("autotest");
        domain.setUpdatedBy("autotest");
        domain.setCreateTime(now);
        domain.setUpdateTime(now);
        domainDao.saveEntity(domain);

        NopMetaDataProduct product = productDao.newEntity();
        product.setDataProductId("dp-link-001");
        product.setBusinessDomainId("bd-link-001");
        product.setName("TestDataProduct");
        product.setVersion(1L);
        product.setCreatedBy("autotest");
        product.setUpdatedBy("autotest");
        product.setCreateTime(now);
        product.setUpdateTime(now);
        productDao.saveEntity(product);
        return "dp-link-001";
    }

    @Test
    public void testLinkAsset() {
        String dataProductId = setupDomainAndProduct();

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataProduct__linkAsset("
                        + "dataProductId: \"" + dataProductId + "\""
                        + ", entityType: \"NopMetaTable\""
                        + ", entityId: \"table-001\") { tagLabelId source labelType state entityType entityId } }");
        assertFalse(response.hasError(), "linkAsset should not error: " + response);

        String result = response.getData().toString();
        assertTrue(result.contains("Automated"), "should contain labelType Automated: " + result);
        assertTrue(result.contains("NopMetaTable"), "should contain entityType: " + result);
        assertTrue(result.contains("table-001"), "should contain entityId: " + result);

        // Clean up
        IEntityDao<NopMetaDataProduct> productDao = daoProvider.daoFor(NopMetaDataProduct.class);
        IEntityDao<NopMetaBusinessDomain> domainDao = daoProvider.daoFor(NopMetaBusinessDomain.class);
        response = execute("mutation { NopMetaDataProduct__delete(id: \"" + dataProductId + "\") }");
        assertFalse(response.hasError(), "delete should not error: " + response);
        response = execute("mutation { NopMetaBusinessDomain__delete(id: \"bd-link-001\") }");
        assertFalse(response.hasError(), "delete domain should not error: " + response);
    }

    @Test
    public void testLinkAssetIdempotent() {
        String dataProductId = setupDomainAndProduct();

        // First link
        GraphQLResponseBean response1 = execute(
                "mutation { NopMetaDataProduct__linkAsset("
                        + "dataProductId: \"" + dataProductId + "\""
                        + ", entityType: \"NopMetaEntity\""
                        + ", entityId: \"entity-001\") { tagLabelId } }");
        assertFalse(response1.hasError(), "first linkAsset should not error: " + response1);

        // Second link (same)
        GraphQLResponseBean response2 = execute(
                "mutation { NopMetaDataProduct__linkAsset("
                        + "dataProductId: \"" + dataProductId + "\""
                        + ", entityType: \"NopMetaEntity\""
                        + ", entityId: \"entity-001\") { tagLabelId } }");
        assertFalse(response2.hasError(), "second linkAsset should not error: " + response2);

        // Verify only one TagLabel created
        IEntityDao<NopMetaTagLabel> labelDao = daoProvider.daoFor(NopMetaTagLabel.class);
        java.util.List<NopMetaTagLabel> labels = labelDao.findAll();
        long matchCount = labels.stream()
                .filter(l -> "Automated".equals(l.getLabelType()))
                .filter(l -> "entity-001".equals(l.getEntityId()))
                .count();
        assertEquals(1, matchCount, "linkAsset should be idempotent");

        // Clean up
        execute("mutation { NopMetaDataProduct__delete(id: \"" + dataProductId + "\") }");
        execute("mutation { NopMetaBusinessDomain__delete(id: \"bd-link-001\") }");
    }

    @Test
    public void testGetLinkedAssets() {
        String dataProductId = setupDomainAndProduct();

        // Link two assets
        execute("mutation { NopMetaDataProduct__linkAsset("
                + "dataProductId: \"" + dataProductId + "\""
                + ", entityType: \"NopMetaTable\""
                + ", entityId: \"table-001\") { tagLabelId } }");
        execute("mutation { NopMetaDataProduct__linkAsset("
                + "dataProductId: \"" + dataProductId + "\""
                + ", entityType: \"NopMetaTableMeasure\""
                + ", entityId: \"measure-001\") { tagLabelId } }");

        // Get linked assets
        GraphQLResponseBean response = execute(
                "query { NopMetaDataProduct__getLinkedAssets("
                        + "dataProductId: \"" + dataProductId + "\") { entityType entityId } }");
        assertFalse(response.hasError(), "getLinkedAssets should not error: " + response);
        String result = response.getData().toString();
        assertTrue(result.contains("table-001"), "should contain table-001: " + result);
        assertTrue(result.contains("measure-001"), "should contain measure-001: " + result);

        // Clean up
        execute("mutation { NopMetaDataProduct__delete(id: \"" + dataProductId + "\") }");
        execute("mutation { NopMetaBusinessDomain__delete(id: \"bd-link-001\") }");
    }

    @Test
    public void testUnlinkAsset() {
        String dataProductId = setupDomainAndProduct();

        // Link first
        execute("mutation { NopMetaDataProduct__linkAsset("
                + "dataProductId: \"" + dataProductId + "\""
                + ", entityType: \"NopMetaTable\""
                + ", entityId: \"table-001\") { tagLabelId } }");

        // Verify linked
        GraphQLResponseBean getResponse = execute(
                "query { NopMetaDataProduct__getLinkedAssets("
                        + "dataProductId: \"" + dataProductId + "\") { entityType entityId } }");
        assertTrue(getResponse.getData().toString().contains("table-001"));

        // Unlink
        GraphQLResponseBean unlinkResponse = execute(
                "mutation { NopMetaDataProduct__unlinkAsset("
                        + "dataProductId: \"" + dataProductId + "\""
                        + ", entityType: \"NopMetaTable\""
                        + ", entityId: \"table-001\") }");
        assertFalse(unlinkResponse.hasError(), "unlinkAsset should not error: " + unlinkResponse);
        assertTrue(unlinkResponse.getData().toString().contains("true"), "unlinkAsset should return true");

        // Verify unlinked
        getResponse = execute(
                "query { NopMetaDataProduct__getLinkedAssets("
                        + "dataProductId: \"" + dataProductId + "\") { entityType entityId } }");
        assertFalse(getResponse.getData().toString().contains("table-001"),
                "should not contain table-001 after unlink: " + getResponse.getData());

        // Clean up
        execute("mutation { NopMetaDataProduct__delete(id: \"" + dataProductId + "\") }");
        execute("mutation { NopMetaBusinessDomain__delete(id: \"bd-link-001\") }");
    }

    @Test
    public void testUnlinkAssetNotFound() {
        String dataProductId = setupDomainAndProduct();

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataProduct__unlinkAsset("
                        + "dataProductId: \"" + dataProductId + "\""
                        + ", entityType: \"NopMetaTable\""
                        + ", entityId: \"nonexistent\") }");
        assertTrue(response.hasError(), "unlinkAsset on non-existent link should error: " + response);

        // Clean up
        execute("mutation { NopMetaDataProduct__delete(id: \"" + dataProductId + "\") }");
        execute("mutation { NopMetaBusinessDomain__delete(id: \"bd-link-001\") }");
    }

    @Test
    public void testLinkAssetInvalidEntityType() {
        String dataProductId = setupDomainAndProduct();

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataProduct__linkAsset("
                        + "dataProductId: \"" + dataProductId + "\""
                        + ", entityType: \"InvalidType\""
                        + ", entityId: \"x\") { tagLabelId } }");
        assertTrue(response.hasError(), "linkAsset with invalid entityType should error: " + response);

        // Clean up
        execute("mutation { NopMetaDataProduct__delete(id: \"" + dataProductId + "\") }");
        execute("mutation { NopMetaBusinessDomain__delete(id: \"bd-link-001\") }");
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
