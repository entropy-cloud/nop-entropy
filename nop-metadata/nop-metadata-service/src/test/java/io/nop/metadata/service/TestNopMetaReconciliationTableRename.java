/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaReconciliationConfig;
import io.nop.metadata.dao.entity.NopMetaReconciliationEntity;
import io.nop.metadata.dao.entity.NopMetaReconciliationResult;
import io.nop.metadata.dao.entity.NopMetaTable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Phase 3 表名重命名（nop_meta_recon_* → nop_meta_reconciliation_*）后，
 * 3 个 Reconciliation 实体的 CRUD 完整可用（plan 1250-2 Phase 3 Proof，维度19-01）。
 *
 * <p>重命名前：表名缩写违反 nop_{模块}_{实体snake_case} 全称映射规则。
 * 重命名后：测试库 DDL 使用新表名，CRUD 落库到新表名，GraphQL 查询返回正确数据。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaReconciliationTableRename extends JunitBaseTestCase {

    public TestNopMetaReconciliationTableRename() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    /** Config CRUD on new table name `nop_meta_reconciliation_config`. */
    @Test
    public void testReconciliationConfigCrudOnNewTable() {
        String tableId = ensureTable();
        IEntityDao<NopMetaReconciliationConfig> dao = daoProvider.daoFor(NopMetaReconciliationConfig.class);

        // Create via DAO (writes to new table)
        NopMetaReconciliationConfig c = dao.newEntity();
        c.setConfigName("rn-test-1");
        c.setDisplayName("rn-test-1");
        c.setMetaTableId(tableId);
        c.setColumnName("user_id");
        c.setMatchStrategy("exact");
        c.setAutoMatch((byte) 0);
        c.setAutoMatchThreshold(0.5);
        dao.saveEntity(c);
        dao.flushSession();
        String configId = c.getConfigId();

        // GraphQL query on new table
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaReconciliationConfig__get(id: \"" + configId + "\") { configId configName matchStrategy } }")));
        assertFalse(resp.hasError(), "GraphQL get on new table name must succeed: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("rn-test-1"), "data must include row from new table: " + data);

        // Delete via GraphQL mutation (follow existing test pattern)
        GraphQLResponseBean delResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaReconciliationConfig__delete(id: \"" + configId + "\") }")));
        assertFalse(delResp.hasError(), "delete should not error: " + delResp);
        assertNull(dao.getEntityById(configId), "row must be deleted from new table");
    }

    /** Result CRUD on new table name `nop_meta_reconciliation_result`. */
    @Test
    public void testReconciliationResultCrudOnNewTable() {
        String tableId = ensureTable();
        IEntityDao<NopMetaReconciliationConfig> cfgDao = daoProvider.daoFor(NopMetaReconciliationConfig.class);
        NopMetaReconciliationConfig c = cfgDao.newEntity();
        c.setConfigName("rn-result-test");
        c.setMetaTableId(tableId);
        c.setColumnName("user_id");
        c.setMatchStrategy("exact");
        c.setAutoMatch((byte) 0);
        c.setAutoMatchThreshold(0.5);
        cfgDao.saveEntity(c);
        cfgDao.flushSession();

        IEntityDao<NopMetaReconciliationResult> dao = daoProvider.daoFor(NopMetaReconciliationResult.class);
        NopMetaReconciliationResult r = dao.newEntity();
        r.setConfigId(c.getConfigId());
        r.setMetaTableId(tableId);
        r.setExecuteTime(new Timestamp(System.currentTimeMillis()));
        dao.saveEntity(r);
        dao.flushSession();
        String resultId = r.getResultId();

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaReconciliationResult__get(id: \"" + resultId + "\") { resultId configId } }")));
        assertFalse(resp.hasError(), "GraphQL get on new result table must succeed: " + resp);

        GraphQLResponseBean delResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaReconciliationResult__delete(id: \"" + resultId + "\") }")));
        assertFalse(delResp.hasError(), "delete should not error: " + delResp);
        assertNull(dao.getEntityById(resultId), "row must be deleted");
    }

    /** Entity CRUD on new table name `nop_meta_reconciliation_entity`. */
    @Test
    public void testReconciliationEntityCrudOnNewTable() {
        IEntityDao<NopMetaReconciliationEntity> dao = daoProvider.daoFor(NopMetaReconciliationEntity.class);
        NopMetaReconciliationEntity e = dao.newEntity();
        e.setEntityId("rn-entity-1");
        e.setEntityName("Microsoft");
        e.setEntityType("company");
        dao.saveEntity(e);
        dao.flushSession();
        String reconEntityId = e.getReconEntityId();

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaReconciliationEntity__get(id: \"" + reconEntityId + "\") { reconEntityId entityId entityName entityType } }")));
        assertFalse(resp.hasError(), "GraphQL get on new entity table must succeed: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("Microsoft"), "data must contain entityName: " + data);

        GraphQLResponseBean delResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaReconciliationEntity__delete(id: \"" + reconEntityId + "\") }")));
        assertFalse(delResp.hasError(), "delete should not error: " + delResp);
        assertNull(dao.getEntityById(reconEntityId), "row must be deleted");
    }

    /** End-to-end: GraphQL save → DB → GraphQL query round-trip on new table name. */
    @Test
    public void testEndToEndSaveAndQueryOnNewTable() {
        String tableId = ensureTable();
        GraphQLResponseBean saveResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaReconciliationConfig__save(data: { configName: \"e2e-test\", "
                        + "metaTableId: \"" + tableId + "\", columnName: \"id\", matchStrategy: \"exact\", "
                        + "autoMatch: 0, autoMatchThreshold: 0.5 }) { configId configName } }")));
        assertFalse(saveResp.hasError(), "save must succeed: " + saveResp);
        String saveData = String.valueOf(saveResp.getData());
        assertTrue(saveData.contains("e2e-test"), "saved data must include configName: " + saveData);
        // Extract configId
        int idx = saveData.indexOf("configId=");
        String configId = saveData.substring(idx + "configId=".length(),
                saveData.indexOf(",", idx));

        GraphQLResponseBean findResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaReconciliationConfig__findPage(query: { limit: 10 }) "
                        + "{ total items { configId configName } } }")));
        assertFalse(findResp.hasError(), "findPage must succeed: " + findResp);
        String findData = String.valueOf(findResp.getData());
        assertTrue(findData.contains(configId), "findPage must return saved row: " + findData);

        // Cleanup via GraphQL mutation
        GraphQLResponseBean delResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaReconciliationConfig__delete(id: \"" + configId + "\") }")));
        assertFalse(delResp.hasError(), "cleanup delete should not error: " + delResp);
    }

    private String ensureTable() {
        IEntityDao<NopMetaModule> moduleDao = daoProvider.daoFor(NopMetaModule.class);
        NopMetaModule m = moduleDao.newEntity();
        m.setModuleId("mod-rn-test-" + System.nanoTime());
        m.setModuleName("rn-test");
        m.setModuleVersion(1L);
        m.setStatus("RELEASED");
        m.setImportedAt(new Timestamp(System.currentTimeMillis()));
        moduleDao.saveEntity(m);
        moduleDao.flushSession();

        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(m.getMetaModuleId());
        t.setTableName("RN_TEST_TABLE_" + System.nanoTime());
        t.setDisplayName("rn-test");
        t.setTableType("external");
        dao.saveEntity(t);
        dao.flushSession();
        return t.getMetaTableId();
    }

    private GraphQLRequestBean req(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return request;
    }
}
