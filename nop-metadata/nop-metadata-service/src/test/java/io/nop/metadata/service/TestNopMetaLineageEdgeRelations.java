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
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaPipeline;
import io.nop.metadata.dao.entity.NopMetaTable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 NopMetaLineageEdge 的 to-one 关系（sourceTable / targetTable / pipeline）
 * 与 NopMetaTable 的反向 to-many（lineageAsSource / lineageAsTarget）。
 *
 * <p>plan 1250-2 Phase 1 Proof：维度04-02 修复——LineageEdge 此前完全缺失 relations 块，
 * 三个 FK 列在 ORM 层无关系导航。本测试通过 GraphQL selection 触达真实导航路径，证明运行时可被加载
 * （Anti-Hollow：不只检查 xmeta 字段存在，而是真正触达 ORM 反向导航）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaLineageEdgeRelations extends JunitBaseTestCase {

    public TestNopMetaLineageEdgeRelations() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testLineageEdgeToOneRelationsResolveViaGraphQL() {
        String moduleId = ensureModule();
        String srcTableId = saveTable(moduleId, "REL_SRC");
        String tgtTableId = saveTable(moduleId, "REL_TGT");
        String pipelineId = savePipeline(moduleId, "REL_PIPE");

        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        NopMetaLineageEdge edge = dao.newEntity();
        edge.setSourceTableId(srcTableId);
        edge.setTargetTableId(tgtTableId);
        edge.setPipelineId(pipelineId);
        edge.setLineageSource(_NopMetadataCoreConstants.LINEAGE_SOURCE_MANUAL);
        edge.setTransformType(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT);
        dao.saveEntity(edge);
        dao.flushSession();
        String edgeId = edge.getLineageEdgeId();

        // GraphQL selection-set 包含 sourceTable / targetTable / pipeline
        // 证明 ORM 反向导航在运行时确实被 GraphQL 引擎触达（Anti-Hollow）
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaLineageEdge__get(id: \"" + edgeId + "\") { lineageEdgeId "
                        + "sourceTable { metaTableId tableName } "
                        + "targetTable { metaTableId tableName } "
                        + "pipeline { pipelineId pipelineName } } }")));
        assertFalse(resp.hasError(), "GraphQL get with relations should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("REL_SRC"),
                "sourceTable.tableName must resolve (audit typo: tableName not targetName): " + data);
        assertTrue(data.contains("REL_TGT"),
                "targetTable.tableName must resolve: " + data);
        assertTrue(data.contains("REL_PIPE"),
                "pipeline.pipelineName must resolve: " + data);
    }

    @Test
    public void testReverseToManyLineageAsSourceAndTargetViaGraphQL() {
        String moduleId = ensureModule();
        String srcTableId = saveTable(moduleId, "REV_SRC");
        String tgtTableId = saveTable(moduleId, "REV_TGT");

        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        for (int i = 0; i < 3; i++) {
            NopMetaLineageEdge edge = dao.newEntity();
            edge.setSourceTableId(srcTableId);
            edge.setTargetTableId(tgtTableId);
            edge.setLineageSource(_NopMetadataCoreConstants.LINEAGE_SOURCE_MANUAL);
            edge.setTransformType(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT);
            dao.saveEntity(edge);
        }
        dao.flushSession();

        // 反向 to-many: sourceTable.lineageAsSource 包含 3 条边
        GraphQLResponseBean srcResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaTable__get(id: \"" + srcTableId + "\") { tableName "
                        + "lineageAsSource { lineageEdgeId } } }")));
        assertFalse(srcResp.hasError(), "source table get should not error: " + srcResp);
        String srcData = String.valueOf(srcResp.getData());
        assertTrue(srcData.contains("lineageEdgeId"),
                "lineageAsSource reverse to-many must be reachable via GraphQL: " + srcData);

        GraphQLResponseBean tgtResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaTable__get(id: \"" + tgtTableId + "\") { tableName "
                        + "lineageAsTarget { lineageEdgeId } } }")));
        assertFalse(tgtResp.hasError(), "target table get should not error: " + tgtResp);
        String tgtData = String.valueOf(tgtResp.getData());
        assertTrue(tgtData.contains("lineageEdgeId"),
                "lineageAsTarget reverse to-many must be reachable via GraphQL: " + tgtData);

        // Cleanup (no deleteAll; we just let the test data be torn down)
    }

    private String ensureModule() {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        NopMetaModule m = dao.newEntity();
        m.setModuleId("mod-rel-test-" + System.nanoTime());
        m.setModuleName("rel-test");
        m.setModuleVersion(1L);
        m.setStatus(_NopMetadataCoreConstants.MODULE_STATUS_RELEASED);
        m.setImportedAt(new Timestamp(System.currentTimeMillis()));
        dao.saveEntity(m);
        dao.flushSession();
        return m.getMetaModuleId();
    }

    private String saveTable(String moduleId, String tableName) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(moduleId);
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL);
        dao.saveEntity(t);
        dao.flushSession();
        return t.getMetaTableId();
    }

    private String savePipeline(String moduleId, String pipelineName) {
        IEntityDao<NopMetaPipeline> dao = daoProvider.daoFor(NopMetaPipeline.class);
        NopMetaPipeline p = dao.newEntity();
        p.setMetaModuleId(moduleId);
        p.setPipelineName(pipelineName);
        p.setDisplayName(pipelineName);
        p.setPipelineType(_NopMetadataCoreConstants.PIPELINE_TYPE_SQL);
        dao.saveEntity(p);
        dao.flushSession();
        return p.getPipelineId();
    }

    private GraphQLRequestBean req(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return request;
    }
}
