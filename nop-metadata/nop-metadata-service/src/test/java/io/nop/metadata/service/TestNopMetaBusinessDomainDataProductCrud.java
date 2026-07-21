package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaBusinessDomain;
import io.nop.metadata.dao.entity.NopMetaDataProduct;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmUniqueKeyModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Phase 3 新增实体（BusinessDomain / DataProduct）的 GraphQL CRUD 操作，
 * 以及 domain->product 嵌套创建。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaBusinessDomainDataProductCrud extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate orm;

    @Test
    public void testBusinessDomainCrud() {
        IEntityDao<NopMetaBusinessDomain> dao = daoProvider.daoFor(NopMetaBusinessDomain.class);

        NopMetaBusinessDomain entity = dao.newEntity();
        String id = "bd-test-001";
        entity.setBusinessDomainId(id);
        entity.setName("Marketing");
        entity.setDisplayName("营销域");
        entity.setDomainType("SourceAligned");
        entity.setVersion(1L);
        entity.setCreatedBy("autotest");
        entity.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        dao.saveEntity(entity);

        // Read via GraphQL
        GraphQLResponseBean response = execute(
                "query { NopMetaBusinessDomain__get(id: \"" + id + "\") { businessDomainId name displayName domainType } }");
        assertFalse(response.hasError(), "get should not error: " + response);
        assertTrue(response.getData().toString().contains("Marketing"), "should contain name Marketing: " + response.getData());
        assertTrue(response.getData().toString().contains("SourceAligned"), "should contain domainType: " + response.getData());

        // Verify created in DB
        NopMetaBusinessDomain created = dao.getEntityById(id);
        assertNotNull(created);
        assertEquals("Marketing", created.getName());

        // Delete via GraphQL
        response = execute(
                "mutation { NopMetaBusinessDomain__delete(id: \"" + id + "\") }");
        assertFalse(response.hasError(), "delete should not error: " + response);

        // Verify deleted
        assertNull(dao.getEntityById(id));
    }

    @Test
    public void testBusinessDomainWithParentHierarchy() {
        IEntityDao<NopMetaBusinessDomain> dao = daoProvider.daoFor(NopMetaBusinessDomain.class);

        Timestamp now = new Timestamp(System.currentTimeMillis());

        // Create parent domain
        NopMetaBusinessDomain parent = dao.newEntity();
        parent.setBusinessDomainId("bd-parent-001");
        parent.setName("Corporate");
        parent.setDisplayName("企业域");
        parent.setDomainType("Aggregate");
        parent.setVersion(1L);
        parent.setCreatedBy("autotest");
        parent.setUpdatedBy("autotest");
        parent.setCreateTime(now);
        parent.setUpdateTime(now);
        dao.saveEntity(parent);

        // Create child domain
        NopMetaBusinessDomain child = dao.newEntity();
        child.setBusinessDomainId("bd-child-001");
        child.setParentDomainId("bd-parent-001");
        child.setName("Payments");
        child.setDisplayName("支付域");
        child.setDomainType("ConsumerAligned");
        child.setVersion(1L);
        child.setCreatedBy("autotest");
        child.setUpdatedBy("autotest");
        child.setCreateTime(now);
        child.setUpdateTime(now);
        dao.saveEntity(child);

        // Read child via GraphQL
        GraphQLResponseBean response = execute(
                "query { NopMetaBusinessDomain__get(id: \"bd-child-001\") { businessDomainId name parentDomainId } }");
        assertFalse(response.hasError(), "get child should not error: " + response);
        assertTrue(response.getData().toString().contains("bd-parent-001"), "should contain parentDomainId: " + response.getData());

        // Clean up via GraphQL
        response = execute("mutation { NopMetaBusinessDomain__delete(id: \"bd-child-001\") }");
        assertFalse(response.hasError(), "delete child should not error: " + response);
        response = execute("mutation { NopMetaBusinessDomain__delete(id: \"bd-parent-001\") }");
        assertFalse(response.hasError(), "delete parent should not error: " + response);

        assertNull(dao.getEntityById("bd-child-001"));
        assertNull(dao.getEntityById("bd-parent-001"));
    }

    @Test
    public void testDataProductCrud() {
        IEntityDao<NopMetaBusinessDomain> domainDao = daoProvider.daoFor(NopMetaBusinessDomain.class);
        IEntityDao<NopMetaDataProduct> productDao = daoProvider.daoFor(NopMetaDataProduct.class);

        Timestamp now = new Timestamp(System.currentTimeMillis());

        NopMetaBusinessDomain domain = domainDao.newEntity();
        domain.setBusinessDomainId("bd-prod-001");
        domain.setName("Sales");
        domain.setDisplayName("销售域");
        domain.setVersion(1L);
        domain.setCreatedBy("autotest");
        domain.setUpdatedBy("autotest");
        domain.setCreateTime(now);
        domain.setUpdateTime(now);
        domainDao.saveEntity(domain);

        // Create DataProduct
        NopMetaDataProduct product = productDao.newEntity();
        String prodId = "dp-test-001";
        product.setDataProductId(prodId);
        product.setBusinessDomainId("bd-prod-001");
        product.setName("Customer360");
        product.setDisplayName("客户360视图");
        product.setLifecycleStage("PRODUCTION");
        product.setDataProductType("DERIVED_DATA");
        product.setVisibility("ORGANISATION");
        product.setPortfolioPriority("HIGH");
        product.setVersion(1L);
        product.setCreatedBy("autotest");
        product.setUpdatedBy("autotest");
        product.setCreateTime(now);
        product.setUpdateTime(now);
        productDao.saveEntity(product);

        // Read via GraphQL
        GraphQLResponseBean response = execute(
                "query { NopMetaDataProduct__get(id: \"" + prodId + "\") { dataProductId name businessDomainId lifecycleStage dataProductType } }");
        assertFalse(response.hasError(), "get should not error: " + response);
        assertTrue(response.getData().toString().contains("Customer360"), "should contain name: " + response.getData());
        assertTrue(response.getData().toString().contains("PRODUCTION"), "should contain lifecycleStage: " + response.getData());
        assertTrue(response.getData().toString().contains("DERIVED_DATA"), "should contain dataProductType: " + response.getData());

        // Create a DataProduct nested under the same domain
        NopMetaDataProduct product2 = productDao.newEntity();
        product2.setDataProductId("dp-test-002");
        product2.setBusinessDomainId("bd-prod-001");
        product2.setName("SalesDashboard");
        product2.setDisplayName("销售仪表盘");
        product2.setLifecycleStage("DEVELOPMENT");
        product2.setDataProductType("REPORTS");
        product2.setVisibility("PRIVATE");
        product2.setPortfolioPriority("MEDIUM");
        product2.setVersion(1L);
        product2.setCreatedBy("autotest");
        product2.setUpdatedBy("autotest");
        product2.setCreateTime(now);
        product2.setUpdateTime(now);
        productDao.saveEntity(product2);

        // Query DataProducts by domain
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaDataProduct.PROP_NAME_businessDomainId, "bd-prod-001"));
        List<NopMetaDataProduct> products = productDao.findAllByQuery(q);
        assertEquals(2, products.size(), "should find 2 DataProducts under the same domain");

        // Clean up via GraphQL
        response = execute("mutation { NopMetaDataProduct__delete(id: \"dp-test-002\") }");
        assertFalse(response.hasError(), "delete product2 should not error: " + response);
        response = execute("mutation { NopMetaDataProduct__delete(id: \"dp-test-001\") }");
        assertFalse(response.hasError(), "delete product should not error: " + response);
        response = execute("mutation { NopMetaBusinessDomain__delete(id: \"bd-prod-001\") }");
        assertFalse(response.hasError(), "delete domain should not error: " + response);

        assertNull(productDao.getEntityById("dp-test-001"));
        assertNull(productDao.getEntityById("dp-test-002"));
        assertNull(domainDao.getEntityById("bd-prod-001"));
    }

    @Test
    public void testBusinessDomainUKDeclaredInOrmModel() {
        OrmEntityModel model = (OrmEntityModel) orm.getOrmModel().getEntityModel("io.nop.metadata.dao.entity.NopMetaBusinessDomain");
        assertNotNull(model, "NopMetaBusinessDomain model must be loaded");
        assertTrue(hasUniqueKeyWithColumns(model, "parentDomainId", "name"),
                "NopMetaBusinessDomain must declare UK on (parentDomainId, name): " + ukNames(model));
    }

    @Test
    public void testDataProductUKDeclaredInOrmModel() {
        OrmEntityModel model = (OrmEntityModel) orm.getOrmModel().getEntityModel("io.nop.metadata.dao.entity.NopMetaDataProduct");
        assertNotNull(model, "NopMetaDataProduct model must be loaded");
        assertTrue(hasUniqueKeyWithColumns(model, "businessDomainId", "name"),
                "NopMetaDataProduct must declare UK on (businessDomainId, name): " + ukNames(model));
    }

    private static boolean hasUniqueKeyWithColumns(OrmEntityModel model, String... propNames) {
        List<OrmUniqueKeyModel> uks = model.getUniqueKeys();
        if (uks == null || uks.isEmpty()) {
            return false;
        }
        for (OrmUniqueKeyModel uk : uks) {
            List<OrmColumnModel> cols = uk.getColumnModels();
            if (cols == null || cols.size() != propNames.length) {
                continue;
            }
            boolean allFound = true;
            for (String expectedProp : propNames) {
                boolean found = false;
                for (OrmColumnModel col : cols) {
                    if (expectedProp.equals(col.getName())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    allFound = false;
                    break;
                }
            }
            if (allFound) return true;
        }
        return false;
    }

    private static String ukNames(OrmEntityModel model) {
        StringBuilder sb = new StringBuilder("[");
        if (model.getUniqueKeys() != null) {
            for (OrmUniqueKeyModel uk : model.getUniqueKeys()) {
                if (sb.length() > 1) sb.append(", ");
                sb.append(uk.getName());
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
