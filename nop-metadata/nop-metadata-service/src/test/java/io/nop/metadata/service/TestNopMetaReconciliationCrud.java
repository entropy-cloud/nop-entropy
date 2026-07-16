package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaReconciliationConfig;
import io.nop.metadata.dao.entity.NopMetaReconciliationEntity;
import io.nop.metadata.dao.entity.NopMetaReconciliationResult;
import io.nop.metadata.dao.entity.NopMetaTable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 3 个 Reconciliation 实体建模（Phase 1，plan 0900-2）：
 * GraphQL CRUD 由 CrudBizModel 自动暴露（registerShortName=true），无需手写 BizModel。
 *
 * <p>覆盖 Config/Result/Entity 三个实体的 create + query + findPage + delete，以及 dict 约束。
 *
 * <p>Anti-Hollow：写入真实实体行（含 statistics/details JSON），GraphQL 读回断言字段值一致，
 * 证明实体建模、列映射、to-one relation、dict 全链路可用，非空壳。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaReconciliationCrud extends JunitBaseTestCase {

    public TestNopMetaReconciliationCrud() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    // ===== NopMetaReconciliationConfig =====

    /** Config create + query：DAO 写入 → GraphQL get 读回，字段一致。 */
    @Test
    public void testConfigCreateAndQueryViaGraphQL() {
        String tableId = ensureTable();
        String configId = saveConfigDirect("rc-crud-1", tableId, "exact");

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaReconciliationConfig__get(id: \"" + configId + "\") { configId configName "
                        + "matchStrategy columnName autoMatch autoMatchThreshold targetEntityType identifierSpace } }")));
        assertFalse(resp.hasError(), "get should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("configId=" + configId), "configId must be returned: " + data);
        assertTrue(data.contains("matchStrategy=exact"), "matchStrategy must be exact: " + data);
        assertTrue(data.contains("columnName=company_name"), "columnName must be returned: " + data);
        assertTrue(data.contains("autoMatch=1"), "autoMatch must be truthy: " + data);
    }

    /** Config findPage 返回真实行；delete 后不可查。 */
    @Test
    public void testConfigFindPageAndDelete() {
        String tableId = ensureTable();
        String id1 = saveConfigDirect("rc-fp-1", tableId, "fuzzy");
        String id2 = saveConfigDirect("rc-fp-2", tableId, "exact");

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaReconciliationConfig__findPage(query: { limit: 10 }) { total items { configId matchStrategy } } }")));
        assertFalse(resp.hasError(), "findPage should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("rc-fp-1"), "findPage must return rc-fp-1: " + data);
        assertTrue(data.contains("rc-fp-2"), "findPage must return rc-fp-2: " + data);

        GraphQLResponseBean delResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaReconciliationConfig__delete(id: \"" + id1 + "\") }")));
        assertFalse(delResp.hasError(), "delete should not error: " + delResp);
        IEntityDao<NopMetaReconciliationConfig> dao = daoProvider.daoFor(NopMetaReconciliationConfig.class);
        assertNull(dao.getEntityById(id1), "config must be deleted");
    }

    /** matchStrategy dict 两值均可持久化（建模约束验证）。 */
    @Test
    public void testConfigMatchStrategyDictValues() {
        String tableId = ensureTable();
        saveConfigDirect("rc-ms-exact", tableId, "exact");
        saveConfigDirect("rc-ms-fuzzy", tableId, "fuzzy");
        IEntityDao<NopMetaReconciliationConfig> dao = daoProvider.daoFor(NopMetaReconciliationConfig.class);
        assertEquals("exact", dao.getEntityById("rc-ms-exact").getMatchStrategy());
        assertEquals("fuzzy", dao.getEntityById("rc-ms-fuzzy").getMatchStrategy());
    }

    // ===== NopMetaReconciliationResult =====

    /** Result create + query，statistics + details JSON 持久化可读。 */
    @Test
    public void testResultCreateAndQueryViaGraphQL() {
        String tableId = ensureTable();
        String configId = saveConfigDirect("rc-res-1", tableId, "exact");
        String resultId = saveResultDirect("rr-1", configId, tableId,
                "{\"totalRows\":3,\"matchedRows\":1,\"unmatchedRows\":1,\"multipleMatches\":1,\"matchRate\":0.33}",
                "[{\"rowIndex\":0,\"originalValue\":\"A\",\"status\":\"MATCHED\"}]");

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaReconciliationResult__get(id: \"" + resultId + "\") { resultId configId metaTableId "
                        + "executeTime statistics details } }")));
        assertFalse(resp.hasError(), "get should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("resultId=" + resultId), "resultId must be returned: " + data);
        assertTrue(data.contains("matchedRows"), "statistics JSON must be stored: " + data);
        assertTrue(data.contains("originalValue"), "details JSON must be stored: " + data);
    }

    /** Result findPage 返回真实行；delete 后不可查。 */
    @Test
    public void testResultFindPageAndDelete() {
        String tableId = ensureTable();
        String configId = saveConfigDirect("rc-res-2", tableId, "fuzzy");
        String id1 = saveResultDirect("rr-fp-1", configId, tableId, "{}", "[]");
        String id2 = saveResultDirect("rr-fp-2", configId, tableId, "{}", "[]");

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaReconciliationResult__findPage(query: { limit: 10 }) { total items { resultId } } }")));
        assertFalse(resp.hasError(), "findPage should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("rr-fp-1"), "findPage must return rr-fp-1: " + data);
        assertTrue(data.contains("rr-fp-2"), "findPage must return rr-fp-2: " + data);

        GraphQLResponseBean delResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaReconciliationResult__delete(id: \"" + id1 + "\") }")));
        assertFalse(delResp.hasError(), "delete should not error: " + delResp);
        IEntityDao<NopMetaReconciliationResult> dao = daoProvider.daoFor(NopMetaReconciliationResult.class);
        assertNull(dao.getEntityById(id1), "result must be deleted");
    }

    // ===== NopMetaReconciliationEntity =====

    /** Entity create + query，properties JSON 持久化可读。 */
    @Test
    public void testEntityCreateAndQueryViaGraphQL() {
        String reconEntityId = saveReconEntityDirect("re-crud-1", "Q1", "Microsoft", "company",
                "{\"country\":\"US\"}");

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaReconciliationEntity__get(id: \"" + reconEntityId + "\") { reconEntityId entityId "
                        + "entityName entityType identifierSpace properties } }")));
        assertFalse(resp.hasError(), "get should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("reconEntityId=" + reconEntityId), "reconEntityId must be returned: " + data);
        assertTrue(data.contains("entityName=Microsoft"), "entityName must be returned: " + data);
        assertTrue(data.contains("country"), "properties JSON must be stored: " + data);
    }

    /** Entity findPage + delete。 */
    @Test
    public void testEntityFindPageAndDelete() {
        String id1 = saveReconEntityDirect("re-fp-1", "Q2", "Apple", "company", "{}");
        String id2 = saveReconEntityDirect("re-fp-2", "Q3", "Google", "company", "{}");

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaReconciliationEntity__findPage(query: { limit: 10 }) { total items { reconEntityId } } }")));
        assertFalse(resp.hasError(), "findPage should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("re-fp-1"), "findPage must return re-fp-1: " + data);
        assertTrue(data.contains("re-fp-2"), "findPage must return re-fp-2: " + data);

        GraphQLResponseBean delResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaReconciliationEntity__delete(id: \"" + id1 + "\") }")));
        assertFalse(delResp.hasError(), "delete should not error: " + delResp);
        IEntityDao<NopMetaReconciliationEntity> dao = daoProvider.daoFor(NopMetaReconciliationEntity.class);
        assertNull(dao.getEntityById(id1), "recon entity must be deleted");
    }

    /** to-one relation metaTable 可查（验证 Config→NopMetaTable 关系建模）。 */
    @Test
    public void testConfigMetaTableRelation() {
        String tableId = ensureTable();
        saveConfigDirect("rc-rel-1", tableId, "exact");

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaReconciliationConfig__get(id: \"rc-rel-1\") { configName metaTable { tableName } } }")));
        assertFalse(resp.hasError(), "get with relation should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("RECON_TEST_TABLE"), "metaTable relation must resolve: " + data);
    }

    // ===== helpers =====

    private String ensureTable() {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(ensureExternalSystemModuleId());
        t.setTableName("RECON_TEST_TABLE");
        t.setDisplayName("RECON_TEST_TABLE");
        t.setTableType("external");
        t.setQuerySpace("qs-recon-test");
        t.setVersion(1L);
        dao.saveEntity(t);
        return t.getMetaTableId();
    }

    private String ensureExternalSystemModuleId() {
        IEntityDao<NopMetaModule> moduleDao = daoProvider.daoFor(NopMetaModule.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaModule.PROP_NAME_moduleId, "nop/meta-recon"));
        NopMetaModule module = moduleDao.findFirstByQuery(q);
        if (module != null) {
            return module.getMetaModuleId();
        }
        module = moduleDao.newEntity();
        module.setModuleId("nop/meta-recon");
        module.setModuleName("meta-recon");
        module.setDisplayName("对账系统模块");
        module.setModuleVersion(1L);
        module.setStatus("RELEASED");
        module.setImportedAt(new Timestamp(System.currentTimeMillis()));
        moduleDao.saveEntity(module);
        return module.getMetaModuleId();
    }

    private String saveConfigDirect(String configId, String metaTableId, String matchStrategy) {
        IEntityDao<NopMetaReconciliationConfig> dao = daoProvider.daoFor(NopMetaReconciliationConfig.class);
        NopMetaReconciliationConfig c = dao.newEntity();
        c.setConfigId(configId);
        c.setConfigName(configId + "-name");
        c.setDisplayName(configId + "-name");
        c.setMetaTableId(metaTableId);
        c.setColumnName("company_name");
        c.setIdentifierSpace("wikidata");
        c.setTargetEntityType("company");
        c.setMatchStrategy(matchStrategy);
        c.setAutoMatch((byte) 1);
        c.setAutoMatchThreshold(0.8);
        c.setVersion(1L);
        c.setCreatedBy("autotest");
        c.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        c.setCreateTime(now);
        c.setUpdateTime(now);
        dao.saveEntity(c);
        return configId;
    }

    private String saveResultDirect(String resultId, String configId, String metaTableId,
                                    String statistics, String details) {
        IEntityDao<NopMetaReconciliationResult> dao = daoProvider.daoFor(NopMetaReconciliationResult.class);
        NopMetaReconciliationResult r = dao.newEntity();
        r.setResultId(resultId);
        r.setConfigId(configId);
        r.setMetaTableId(metaTableId);
        r.setExecuteTime(new Timestamp(System.currentTimeMillis()));
        r.setStatistics(statistics);
        r.setDetails(details);
        r.setVersion(1L);
        r.setCreatedBy("autotest");
        r.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        r.setCreateTime(now);
        r.setUpdateTime(now);
        dao.saveEntity(r);
        return resultId;
    }

    private String saveReconEntityDirect(String reconEntityId, String entityId, String entityName,
                                         String entityType, String properties) {
        IEntityDao<NopMetaReconciliationEntity> dao = daoProvider.daoFor(NopMetaReconciliationEntity.class);
        NopMetaReconciliationEntity e = dao.newEntity();
        e.setReconEntityId(reconEntityId);
        e.setEntityId(entityId);
        e.setEntityName(entityName);
        e.setEntityType(entityType);
        e.setIdentifierSpace("wikidata");
        e.setProperties(properties);
        e.setLastSyncedAt(new Timestamp(System.currentTimeMillis()));
        e.setVersion(1L);
        e.setCreatedBy("autotest");
        e.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        e.setCreateTime(now);
        e.setUpdateTime(now);
        dao.saveEntity(e);
        return reconEntityId;
    }

    private GraphQLRequestBean req(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return request;
    }
}
