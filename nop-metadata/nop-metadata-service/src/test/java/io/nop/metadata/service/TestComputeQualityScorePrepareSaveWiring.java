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
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.dao.entity.NopMetaQualityScore;
import io.nop.metadata.dao.entity.NopMetaTable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P2-24（plan 2026-08-16-0226-3 Phase 3）接线验证：computeQualityScore 恢复 defaultPrepareSave 定制点。
 *
 * <p>修复前 computeQualityScore 以空 lambda 调 doSave，绕过 {@code invokeDefaultPrepareSave}
 * （CrudBizModel 经 {@code getThisObj().invoke("defaultPrepareSave",...)} 分发 = xbiz 可覆盖定制点）。
 * 修复后传 {@code this::invokeDefaultPrepareSave}，与基类 save 同形态。
 *
 * <p>接线路径（禁止测试子类直调）：测试 delta xbiz（{@code _vfs/_delta/default/.../NopMetaQualityScore.xbiz}）
 * 以 {@code <action>} 覆盖 defaultPrepareSave（副作用写 remark 列），经 GraphQL 入口调
 * computeQualityScore → doSave(prepareSave=this::invokeDefaultPrepareSave) → getThisObj() 容器分发
 * → xbiz 覆盖真实执行 → 落盘行 remark 为哨兵值。评分行既有字段（overallScore）不变。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestComputeQualityScorePrepareSaveWiring extends JunitBaseTestCase {

    public TestComputeQualityScorePrepareSaveWiring() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testXbizDefaultPrepareSaveInvokedViaContainerDispatch() {
        String tableId = saveTable("T_PREPARE_SAVE_WIRING");
        saveRule("r-psw", "not_null", tableId);
        saveResult("r-psw", "PASS", 1_700_000_000_000L);

        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery("mutation { NopMetaQualityScore__computeQualityScore(metaTableId: \"" + tableId + "\") "
                + "{ scoreId overallScore } }");
        IGraphQLExecutionContext ctx = graphQLEngine.newGraphQLContext(request);
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(ctx);
        assertFalse(resp.hasError(), "computeQualityScore should not error: " + resp);

        NopMetaQualityScore row = findLatestScore(tableId);
        assertNotNull(row, "score row must be persisted");
        assertNotNull(row.getQualityScoreId());
        assertEquals("defaultPrepareSave-invoked", row.getRemark(),
                "xbiz-overridden defaultPrepareSave must run via getThisObj dispatch in computeQualityScore chain");
        // 评分行为不变：单 not_null PASS → completeness=100 → overall=100
        assertEquals(100.0, row.getOverallScore(), 0.001, "scoring behavior must stay unchanged");
    }

    private String saveTable(String tableName) {
        IEntityDao<NopMetaModule> moduleDao = daoProvider.daoFor(NopMetaModule.class);
        NopMetaModule module = moduleDao.newEntity();
        module.setModuleId("nop/meta-prepare-save-wiring");
        module.setModuleName("meta-prepare-save-wiring");
        module.setDisplayName("prepareSave 接线测试模块");
        module.setModuleVersion(1L);
        module.setStatus("RELEASED");
        module.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        module.setCreatedBy("autotest");
        module.setCreateTime(now);
        module.setUpdatedBy("autotest");
        module.setUpdateTime(now);
        moduleDao.saveEntity(module);

        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = tableDao.newEntity();
        t.setMetaModuleId(module.getMetaModuleId());
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType("entity");
        t.setVersion(1L);
        t.setCreatedBy("autotest");
        t.setCreateTime(now);
        t.setUpdatedBy("autotest");
        t.setUpdateTime(now);
        tableDao.saveEntity(t);
        return t.getMetaTableId();
    }

    private void saveRule(String ruleId, String ruleType, String entityId) {
        IEntityDao<NopMetaQualityRule> dao = daoProvider.daoFor(NopMetaQualityRule.class);
        NopMetaQualityRule rule = dao.newEntity();
        rule.setQualityRuleId(ruleId);
        rule.setRuleName(ruleId);
        rule.setDisplayName(ruleId);
        rule.setRuleType(ruleType);
        rule.setEntityType("table");
        rule.setEntityId(entityId);
        rule.setSeverity("WARNING");
        rule.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        rule.setCreatedBy("autotest");
        rule.setCreateTime(now);
        rule.setUpdatedBy("autotest");
        rule.setUpdateTime(now);
        dao.saveEntity(rule);
    }

    private void saveResult(String ruleId, String status, long executeTimeMillis) {
        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        NopMetaQualityResult r = dao.newEntity();
        r.setQualityRuleId(ruleId);
        r.setExecuteTime(new Timestamp(executeTimeMillis));
        r.setStatus(status);
        r.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        r.setCreatedBy("autotest");
        r.setCreateTime(now);
        r.setUpdatedBy("autotest");
        r.setUpdateTime(now);
        dao.saveEntity(r);
    }

    private NopMetaQualityScore findLatestScore(String metaTableId) {
        IEntityDao<NopMetaQualityScore> dao = daoProvider.daoFor(NopMetaQualityScore.class);
        io.nop.api.core.beans.query.QueryBean q = new io.nop.api.core.beans.query.QueryBean();
        q.addFilter(io.nop.api.core.beans.FilterBeans.eq(NopMetaQualityScore.PROP_NAME_metaTableId, metaTableId));
        q.addOrderField(NopMetaQualityScore.PROP_NAME_scoreTime, true);
        return dao.findFirstByQuery(q);
    }
}
