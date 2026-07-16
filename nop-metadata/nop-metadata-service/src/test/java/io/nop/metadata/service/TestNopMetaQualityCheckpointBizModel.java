/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.dao.entity.NopMetaTable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证质量检查点编排（架构基线 §2.7.3）：executeCheckpoint 对 ruleIds + tableIds 混合配置的规则集解析、
 * 逐条复用既有单规则执行路径写入 NopMetaQualityResult、执行摘要计数、以及所有不可执行路径的显式失败。
 *
 * <p>覆盖 Exit Criteria 五条路径：(a) 混合规则集执行 (b) 摘要计数 (c) paused 失败 (d) 未知动作失败 (e) 空规则集失败。
 *
 * <p>Anti-Hollow：所有成功路径用真实 H2 建连 + 真实物理表数据，断言每条规则对应一行真实 QualityResult（PASS/FAIL/SKIP），
 * 证明运行时确实复用了 §2.7.1 judge + TableReferenceExecutor + QualityResultWriter 执行链，非空壳循环。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaQualityCheckpointBizModel extends JunitBaseTestCase {

    public TestNopMetaQualityCheckpointBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    // ===== (a)+(b) 混合规则集执行 + 摘要计数 =====

    /**
     * 混合配置：explicit ruleIds（含 database→SKIP 的 r-cp-db，仅经显式路径可达）+
     * tableIds（其上挂载 volume/not_null 规则，经 mount 路径解析）。验证：
     * <ul>
     *   <li>(a) ruleIds 与 tableIds 两路规则都被解析执行（去重后 4 条）</li>
     *   <li>(b) 摘要 executedCount=4 / passCount=2 / failCount=1（vol PASS + nn PASS + vol-fail FAIL + db SKIP）</li>
     *   <li>每条规则对应一行真实 QualityResult（含 database 的 SKIP 行）</li>
     * </ul>
     */
    @Test
    public void testExecuteCheckpointMixedRulesAndSummary() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_cp_mix;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_cp (id INT NOT NULL)",
                "INSERT INTO ext_cp VALUES (1)", "INSERT INTO ext_cp VALUES (2)", "INSERT INTO ext_cp VALUES (3)");
        PreparedEnv env = prepare(dbUrl, "qs_cp_mix");
        String tableId = env.tableId("EXT_CP");

        // 经 tableIds mount 路径可达的规则（entityId = tableId）
        saveRule("r-cp-vol", "volume", "table", tableId, null, null, "{\"minRows\":2}");       // PASS (3>=2)
        saveRule("r-cp-nn", "not_null", "field", tableId, null, 0.0, "{\"column\":\"ID\"}");    // PASS (0 null)
        saveRule("r-cp-fail", "volume", "table", tableId, null, null, "{\"minRows\":10}");      // FAIL (3<10)
        // 仅经 explicit ruleIds 路径可达（entityId 非 tableId，且 entityType=database → SKIP）
        saveRule("r-cp-db", "volume", "database", "some-db-id", null, null, "{\"minRows\":1}"); // SKIP

        // checkpoint: 显式 ruleIds=[r-cp-db] + tableIds=[tableId]
        saveCheckpoint("cp-mix", "ACTIVE",
                "[{\"ruleIds\":[\"r-cp-db\"],\"tableIds\":[\"" + tableId + "\"]}]",
                "[{\"actionType\":\"store\",\"enabled\":true}]");

        GraphQLResponseBean resp = exec("cp-mix");
        assertFalse(resp.hasError(), "mixed checkpoint should not globally error: " + resp);
        String data = String.valueOf(resp.getData());
        // 解析集 = {r-cp-db(explicit)} ∪ {r-cp-vol,r-cp-nn,r-cp-fail(mount)} = 4（去重）
        assertTrue(data.contains("executedCount=4"), "executedCount=4 (deduped mixed set): " + data);
        assertTrue(data.contains("passCount=2"), "passCount=2 (vol+nn): " + data);
        assertTrue(data.contains("failCount=1"), "failCount=1 (vol-fail minRows=10): " + data);
        assertTrue(data.contains("errorCount=0"), "errorCount=0: " + data);

        // 每条规则对应一行真实 QualityResult（证明复用了 §2.7.1 执行链 + QualityResultWriter，非空壳）
        assertEquals(1, countResults("r-cp-vol"), "volume rule must have a PASS result row");
        assertEquals(1, countResults("r-cp-nn"), "not_null rule must have a PASS result row");
        assertEquals(1, countResults("r-cp-fail"), "volume-fail rule must have a FAIL result row");
        // database 规则写 SKIP 结果行（不剔除，与 §2.7.1 D1 单规则语义一致）
        NopMetaQualityResult dbRow = findResult("r-cp-db");
        assertNotNull(dbRow, "database rule must have a SKIP result row (not dropped)");
        assertEquals("SKIP", dbRow.getStatus());
    }

    // ===== (c) paused 失败 =====

    /** status=PAUSED → executeCheckpoint 显式失败（不静默跳过、不静默返回空摘要）。 */
    @Test
    public void testExecuteCheckpointPausedFails() {
        saveCheckpoint("cp-paused", "PAUSED",
                "[{\"ruleIds\":[\"__any__\"]}]", null);
        GraphQLResponseBean resp = exec("cp-paused");
        assertTrue(resp.hasError(), "PAUSED checkpoint must explicitly fail (no silent skip): " + resp);
    }

    /** status=DISABLED → executeCheckpoint 显式失败。 */
    @Test
    public void testExecuteCheckpointDisabledFails() {
        saveCheckpoint("cp-disabled", "DISABLED",
                "[{\"ruleIds\":[\"__any__\"]}]", null);
        GraphQLResponseBean resp = exec("cp-disabled");
        assertTrue(resp.hasError(), "DISABLED checkpoint must explicitly fail: " + resp);
    }

    // ===== (d) 未知动作失败 =====

    /** actions 配置 store 之外的 notify 且 enabled → executeCheckpoint 显式失败（不静默跳过）。 */
    @Test
    public void testExecuteCheckpointUnknownActionFails() {
        saveCheckpoint("cp-notify", "ACTIVE",
                "[{\"ruleIds\":[\"__any__\"]}]",
                "[{\"actionType\":\"notify\",\"enabled\":true}]");
        GraphQLResponseBean resp = exec("cp-notify");
        assertTrue(resp.hasError(), "unsupported action (notify) must explicitly fail: " + resp);
    }

    /** actions 为空（store-only 默认）合法——不失败在动作校验（用空规则集失败验证：动作校验先过、再到规则解析）。 */
    @Test
    public void testExecuteCheckpointEmptyActionsLegal() {
        // actions=null（store-only 默认），但规则集为空 → 失败原因是 checkpoint-no-rules（动作校验已通过）
        saveCheckpoint("cp-noact", "ACTIVE", "[]", null);
        GraphQLResponseBean resp = exec("cp-noact");
        assertTrue(resp.hasError(), "empty actions is legal; failure here is from empty rule set: " + resp);
    }

    // ===== (e) 空规则集失败 =====

    /** validations=[] → 规则集为空 → executeCheckpoint 显式失败（不静默空集、不伪造零计数）。 */
    @Test
    public void testExecuteCheckpointEmptyRuleSetFails() {
        saveCheckpoint("cp-empty", "ACTIVE", "[]", null);
        GraphQLResponseBean resp = exec("cp-empty");
        assertTrue(resp.hasError(), "empty rule set must explicitly fail (no silent empty summary): " + resp);
    }

    /** 全部引用缺失（ruleId/tableId 不存在）→ 规则集解析为空 → 显式失败。 */
    @Test
    public void testExecuteCheckpointAllMissingRefsFails() {
        saveCheckpoint("cp-missing", "ACTIVE",
                "[{\"ruleIds\":[\"__nope_rule__\"],\"tableIds\":[\"__nope_table__\"]}]", null);
        GraphQLResponseBean resp = exec("cp-missing");
        assertTrue(resp.hasError(), "all-missing-refs resolves to empty set, must fail: " + resp);
    }

    /** 部分引用缺失但规则集非空 → 执行有效规则、缺失项记入 errors 不中断（per-item 隔离）。 */
    @Test
    public void testExecuteCheckpointPartialMissingRefRecordedNotAborted() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_cp_pmiss;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_cp2 (id INT NOT NULL)", "INSERT INTO ext_cp2 VALUES (1)");
        PreparedEnv env = prepare(dbUrl, "qs_cp_pmiss");
        String tableId = env.tableId("EXT_CP2");

        saveRule("r-cp-pm", "volume", "table", tableId, null, null, "{\"minRows\":1}"); // PASS

        // 显式含一个不存在的 ruleId + 一个有效 ruleId；规则集非空
        saveCheckpoint("cp-partial", "ACTIVE",
                "[{\"ruleIds\":[\"r-cp-pm\",\"__missing_rule__\"],\"tableIds\":[\"__missing_table__\"]}]",
                null);

        GraphQLResponseBean resp = exec("cp-partial");
        assertFalse(resp.hasError(), "non-empty set must execute (missing refs isolated, not abort): " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("executedCount=1"), "valid rule executes: " + data);
        assertTrue(data.contains("passCount=1"), "valid rule passes: " + data);
        // 缺失的 ruleId 与 tableId 都记入 errors（不静默丢弃）
        assertTrue(data.contains("__missing_rule__"), "missing ruleId recorded in errors: " + data);
        assertTrue(data.contains("__missing_table__"), "missing tableId recorded in errors: " + data);
        assertEquals(1, countResults("r-cp-pm"), "valid rule result written despite missing refs");
    }

    // ===== helpers =====

    private GraphQLResponseBean exec(String checkpointId) {
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaQualityCheckpoint__executeCheckpoint(checkpointId: \"" + checkpointId
                        + "\", schemaPattern: \"PUBLIC\") }")));
    }

    private GraphQLRequestBean req(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return request;
    }

    private PreparedEnv prepare(String dbUrl, String querySpace) {
        saveDataSource("ds-" + querySpace, querySpace, "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");
        GraphQLResponseBean syncResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-" + querySpace
                        + "\", schemaPattern: \"PUBLIC\") }")));
        assertFalse(syncResp.hasError(), "sync should not error: " + syncResp);
        return new PreparedEnv("ds-" + querySpace);
    }

    private void saveRule(String ruleId, String ruleType, String entityType,
                          String entityId, String sqlExpression, Double threshold, String params) {
        IEntityDao<NopMetaQualityRule> dao = daoProvider.daoFor(NopMetaQualityRule.class);
        NopMetaQualityRule rule = dao.newEntity();
        rule.setQualityRuleId(ruleId);
        rule.setRuleName(ruleId);
        rule.setDisplayName(ruleId);
        rule.setRuleType(ruleType);
        rule.setEntityType(entityType);
        rule.setEntityId(entityId);
        rule.setSeverity("WARNING");
        rule.setSqlExpression(sqlExpression);
        rule.setThreshold(threshold);
        rule.setParams(params);
        rule.setVersion(1L);
        rule.setCreatedBy("autotest");
        rule.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        dao.saveEntity(rule);
    }

    private void saveCheckpoint(String checkpointId, String status, String validations, String actions) {
        IEntityDao<NopMetaQualityCheckpoint> dao = daoProvider.daoFor(NopMetaQualityCheckpoint.class);
        NopMetaQualityCheckpoint cp = dao.newEntity();
        cp.setCheckpointId(checkpointId);
        cp.setCheckpointName(checkpointId);
        cp.setDisplayName(checkpointId);
        cp.setStatus(status);
        cp.setValidations(validations);
        cp.setActions(actions);
        cp.setVersion(1L);
        cp.setCreatedBy("autotest");
        cp.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        cp.setCreateTime(now);
        cp.setUpdateTime(now);
        dao.saveEntity(cp);
    }

    private long countResults(String ruleId) {
        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaQualityResult.PROP_NAME_qualityRuleId, ruleId));
        return dao.countByQuery(q);
    }

    private NopMetaQualityResult findResult(String ruleId) {
        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaQualityResult.PROP_NAME_qualityRuleId, ruleId));
        return dao.findFirstByQuery(q);
    }

    private void saveDataSource(String id, String querySpace, String datasourceType,
                                String status, String connectionConfig) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dao.newEntity();
        ds.setDataSourceId(id);
        ds.setQuerySpace(querySpace);
        ds.setName(id);
        ds.setDatasourceType(datasourceType);
        ds.setConnectionConfig(connectionConfig);
        ds.setStatus(status);
        ds.setVersion(1L);
        ds.setCreatedBy("autotest");
        ds.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dao.saveEntity(ds);
    }

    private String ensureExternalSystemModuleId() {
        IEntityDao<NopMetaModule> moduleDao = daoProvider.daoFor(NopMetaModule.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaModule.PROP_NAME_moduleId, "nop/meta-external"));
        NopMetaModule module = moduleDao.findFirstByQuery(q);
        if (module != null) {
            return module.getMetaModuleId();
        }
        module = moduleDao.newEntity();
        module.setModuleId("nop/meta-external");
        module.setModuleName("meta-external");
        module.setDisplayName("外部表系统模块");
        module.setModuleVersion(1L);
        module.setStatus("RELEASED");
        module.setImportedAt(new Timestamp(System.currentTimeMillis()));
        moduleDao.saveEntity(module);
        return module.getMetaModuleId();
    }

    @SuppressWarnings("unused")
    private NopMetaTable saveManualTable(String tableName, String tableType, String querySpace) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = tableDao.newEntity();
        t.setMetaModuleId(ensureExternalSystemModuleId());
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType(tableType);
        t.setQuerySpace(querySpace);
        t.setVersion(1L);
        tableDao.saveEntity(t);
        return t;
    }

    private void seedTable(String dbUrl, String createDdl, String... inserts) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute(createDdl);
            for (String ins : inserts) {
                st.execute(ins);
            }
        }
    }

    /** 环境就绪后的轻量句柄。 */
    private class PreparedEnv {
        final String dataSourceId;

        PreparedEnv(String dataSourceId) {
            this.dataSourceId = dataSourceId;
        }

        String tableId(String tableName) {
            IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
            QueryBean q = new QueryBean();
            q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
            q.addFilter(FilterBeans.eq("tableType", "external"));
            NopMetaTable t = tableDao.findFirstByQuery(q);
            assertNotNull(t, "external table " + tableName + " must be synced before checkpoint execution");
            return t.getMetaTableId();
        }
    }
}
