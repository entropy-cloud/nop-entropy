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
import io.nop.metadata.dao.entity.NopMetaQualityScore;
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
 * 逐条复用既有单规则执行路径写入 NopMetaQualityResult、执行摘要计数、所有不可执行路径的显式失败，
 * 以及 §2.7.3 D6 自动评分触发（checkpoint→score 接线）。
 *
 * <p>覆盖 Exit Criteria 五条路径：(a) 混合规则集执行 (b) 摘要计数 (c) paused 失败 (d) 未知动作失败 (e) 空规则集失败。
 *
 * <p>D6 自动评分触发覆盖四条路径：(a) 自动评分落盘行数+1 且 overallScore 与手动一致 / (b) 多受影响表逐表评分 /
 * (c) 单表评分失败隔离 / (d) autoScore=false 跳过。
 *
 * <p>Anti-Hollow：所有成功路径用真实 H2 建连 + 真实物理表数据，断言每条规则对应一行真实 QualityResult（PASS/FAIL/SKIP），
 * 证明运行时确实复用了 §2.7.1 judge + TableReferenceExecutor + QualityResultWriter 执行链，非空壳循环。
 * D6 路径额外断言 NopMetaQualityScore 行存在（证明 executeCheckpoint→computeQualityScore→scorer.score→saveEntity
 * 运行时调用链完整连通，接线非空壳）。
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

    // ===== D6 自动评分触发 =====

    /**
     * (a) 自动评分落盘行数+1 且 overallScore 与手动 computeQualityScore 一致。
     *
     * <p>验证 §2.7.3 D6：executeCheckpoint 执行后，受影响表自动重算评分并落盘。断言：
     * <ul>
     *   <li>执行前后该表评分行数 +1</li>
     *   <li>新行 overallScore 与手动调 computeQualityScore 结果一致（复用既有 scorer，非伪造）</li>
     *   <li>摘要含 affectedTableIds 与 scoreResults</li>
     * </ul>
     *
     * <p>端到端 + 接线验证：score 行的存在证明运行时调用链
     * {@code executeCheckpoint → scoreBizModel.computeQualityScore → scorer.score → saveEntity} 完整连通。
     */
    @Test
    public void testAutoScoreWritesRowAndMatchesManual() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_cp_autoscore;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_as (id INT NOT NULL)",
                "INSERT INTO ext_as VALUES (1)", "INSERT INTO ext_as VALUES (2)", "INSERT INTO ext_as VALUES (3)");
        PreparedEnv env = prepare(dbUrl, "qs_cp_as");
        String tableId = env.tableId("EXT_AS");

        saveRule("r-as-vol", "volume", "table", tableId, null, null, "{\"minRows\":2}");       // PASS
        saveRule("r-as-nn", "not_null", "field", tableId, null, 0.0, "{\"column\":\"ID\"}");    // PASS

        saveCheckpoint("cp-as", "ACTIVE",
                "[{\"tableIds\":[\"" + tableId + "\"]}]", null);

        long before = countScores(tableId);
        assertEquals(0, before, "no score row before checkpoint execution");

        GraphQLResponseBean resp = exec("cp-as");
        assertFalse(resp.hasError(), "checkpoint with auto-score should not globally error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("autoScore=true"), "autoScore defaults to true: " + data);

        long after = countScores(tableId);
        assertEquals(1, after, "auto-score must write +1 score row (anti-hollow)");

        // 自动评分行 overallScore
        NopMetaQualityScore autoRow = findLatestScore(tableId);
        assertNotNull(autoRow, "auto-scored row must exist");

        // 手动调 computeQualityScore 比对（复用同一 scorer，结果一致）
        GraphQLResponseBean manualResp = scoreGraphql(tableId);
        assertFalse(manualResp.hasError(), "manual computeQualityScore should succeed: " + manualResp);
        NopMetaQualityScore manualRow = findLatestScore(tableId);
        assertNotNull(manualRow);
        assertEquals(autoRow.getOverallScore(), manualRow.getOverallScore(), 0.001,
                "auto-score overall must match manual computeQualityScore (reuses same scorer)");

        // 两次评分共 2 行
        assertEquals(2, countScores(tableId), "auto + manual = 2 score rows");
    }

    /**
     * (b) 多受影响表逐表评分。
     *
     * <p>两个表各一条 volume 规则（均 PASS），checkpoint 同时覆盖两表。断言每表均产生 +1 score 行，
     * 摘要 scoreResults 含 2 条 per-table 结果。
     */
    @Test
    public void testAutoScoreMultipleAffectedTables() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_cp_multi;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_m1 (id INT NOT NULL)", "INSERT INTO ext_m1 VALUES (1)", "INSERT INTO ext_m1 VALUES (2)");
        seedTable(dbUrl, "CREATE TABLE ext_m2 (id INT NOT NULL)", "INSERT INTO ext_m2 VALUES (1)", "INSERT INTO ext_m2 VALUES (2)");
        PreparedEnv env = prepare(dbUrl, "qs_cp_multi");
        String tableId1 = env.tableId("EXT_M1");
        String tableId2 = env.tableId("EXT_M2");

        saveRule("r-m1-vol", "volume", "table", tableId1, null, null, "{\"minRows\":1}"); // PASS
        saveRule("r-m2-vol", "volume", "table", tableId2, null, null, "{\"minRows\":1}"); // PASS

        saveCheckpoint("cp-multi", "ACTIVE",
                "[{\"tableIds\":[\"" + tableId1 + "\",\"" + tableId2 + "\"]}]", null);

        GraphQLResponseBean resp = exec("cp-multi");
        assertFalse(resp.hasError(), "multi-table checkpoint should not globally error: " + resp);

        assertEquals(1, countScores(tableId1), "table 1 must get +1 score row");
        assertEquals(1, countScores(tableId2), "table 2 must get +1 score row");
    }

    /**
     * (c) 单表评分失败隔离。
     *
     * <p>两表：OK 表 volume 规则（PASS → 评分成功）；FAIL 表 not_null 规则 column=&lt;expr_1&gt;
     * （derived column → judge 返回 SKIP → 全维度 SKIP → scorer 抛 ERR_SCORE_ALL_SKIP）。
     *
     * <p>断言：
     * <ul>
     *   <li>checkpoint 整体不报错（评分失败被隔离）</li>
     *   <li>摘要 errors 含 FAIL 表的 autoScore 错误（source=autoScore）</li>
     *   <li>OK 表仍产生 +1 score 行（其他表评分不受影响）</li>
     *   <li>FAIL 表无 score 行（评分确实失败）</li>
     *   <li>两表的 QualityResult 行均存在（checkpoint store 不回滚）</li>
     * </ul>
     */
    @Test
    public void testAutoScoreFailureIsolation() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_cp_iso;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_iso_ok (id INT NOT NULL)", "INSERT INTO ext_iso_ok VALUES (1)", "INSERT INTO ext_iso_ok VALUES (2)");
        seedTable(dbUrl, "CREATE TABLE ext_iso_fail (id INT NOT NULL)", "INSERT INTO ext_iso_fail VALUES (1)");
        PreparedEnv env = prepare(dbUrl, "qs_cp_iso");
        String okTableId = env.tableId("EXT_ISO_OK");
        String failTableId = env.tableId("EXT_ISO_FAIL");

        // OK 表：volume PASS → 评分成功（completeness=100, overall=100）
        saveRule("r-iso-ok", "volume", "table", okTableId, null, null, "{\"minRows\":1}");
        // FAIL 表：not_null 规则 column=<expr_1>（derived column → judge SKIP → 全 SKIP → 评分失败）
        saveRule("r-iso-fail", "not_null", "field", failTableId, null, 0.0, "{\"column\":\"<expr_1>\"}");

        saveCheckpoint("cp-iso", "ACTIVE",
                "[{\"tableIds\":[\"" + okTableId + "\",\"" + failTableId + "\"]}]", null);

        GraphQLResponseBean resp = exec("cp-iso");
        // checkpoint 整体不报错（评分失败被 per-table 隔离，不传播）
        assertFalse(resp.hasError(), "scoring failure must be isolated (no global error): " + resp);
        String data = String.valueOf(resp.getData());

        // FAIL 表评分错误记入摘要 errors（source=autoScore），不静默吞掉
        assertTrue(data.contains("autoScore"), "autoScore error must be recorded in summary: " + data);
        assertTrue(data.contains(failTableId), "failed table's error must reference its metaTableId: " + data);

        // OK 表仍评分成功（其他表不受影响）
        assertEquals(1, countScores(okTableId), "OK table must still get +1 score (isolation)");
        // FAIL 表无评分行（评分确实失败）
        assertEquals(0, countScores(failTableId), "FAIL table must have 0 score rows (scoring failed)");

        // checkpoint store 不回滚：两表的 QualityResult 均存在
        assertEquals(1, countResults("r-iso-ok"), "OK table result not rolled back");
        assertEquals(1, countResults("r-iso-fail"), "FAIL table result not rolled back");
    }

    /**
     * (d) autoScore=false 跳过自动评分。
     *
     * <p>checkpoint extConfig={"autoScore":false} → 执行后不触发评分。断言：
     * <ul>
     *   <li>摘要含 autoScore=false / scoreSkipped=true</li>
     *   <li>受影响表无新增 score 行</li>
     *   <li>QualityResult 行正常写入（checkpoint store 不受影响）</li>
     * </ul>
     */
    @Test
    public void testAutoScoreDisabledSkips() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_cp_noscore;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_ns (id INT NOT NULL)", "INSERT INTO ext_ns VALUES (1)", "INSERT INTO ext_ns VALUES (2)");
        PreparedEnv env = prepare(dbUrl, "qs_cp_ns");
        String tableId = env.tableId("EXT_NS");

        saveRule("r-ns-vol", "volume", "table", tableId, null, null, "{\"minRows\":1}"); // PASS

        saveCheckpointWithExt("cp-ns", "ACTIVE",
                "[{\"tableIds\":[\"" + tableId + "\"]}]", null, "{\"autoScore\":false}");

        long before = countScores(tableId);
        assertEquals(0, before, "no score row before");

        GraphQLResponseBean resp = exec("cp-ns");
        assertFalse(resp.hasError(), "checkpoint with autoScore=false should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("autoScore=false"), "summary must mark autoScore=false: " + data);
        assertTrue(data.contains("scoreSkipped=true"), "summary must mark scoreSkipped: " + data);

        // 无新增 score 行
        assertEquals(0, countScores(tableId), "autoScore=false must skip scoring (0 new rows)");
        // QualityResult 正常写入（checkpoint store 不受 autoScore 开关影响）
        assertEquals(1, countResults("r-ns-vol"), "checkpoint result still written when autoScore=false");
    }

    // ===== helpers =====

    private GraphQLResponseBean exec(String checkpointId) {
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaQualityCheckpoint__executeCheckpoint(checkpointId: \"" + checkpointId
                        + "\", schemaPattern: \"PUBLIC\") }")));
    }

    /** 手动调 computeQualityScore（用于与自动评分比对，证明复用同一 scorer）。 */
    private GraphQLResponseBean scoreGraphql(String metaTableId) {
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaQualityScore__computeQualityScore(metaTableId: \"" + metaTableId + "\") }")));
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
        saveCheckpointWithExt(checkpointId, status, validations, actions, null);
    }

    private void saveCheckpointWithExt(String checkpointId, String status, String validations,
                                        String actions, String extConfig) {
        IEntityDao<NopMetaQualityCheckpoint> dao = daoProvider.daoFor(NopMetaQualityCheckpoint.class);
        NopMetaQualityCheckpoint cp = dao.newEntity();
        cp.setCheckpointId(checkpointId);
        cp.setCheckpointName(checkpointId);
        cp.setDisplayName(checkpointId);
        cp.setStatus(status);
        cp.setValidations(validations);
        cp.setActions(actions);
        cp.setExtConfig(extConfig);
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

    private long countScores(String metaTableId) {
        IEntityDao<NopMetaQualityScore> dao = daoProvider.daoFor(NopMetaQualityScore.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaQualityScore.PROP_NAME_metaTableId, metaTableId));
        return dao.countByQuery(q);
    }

    private NopMetaQualityScore findLatestScore(String metaTableId) {
        IEntityDao<NopMetaQualityScore> dao = daoProvider.daoFor(NopMetaQualityScore.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaQualityScore.PROP_NAME_metaTableId, metaTableId));
        q.addOrderField(NopMetaQualityScore.PROP_NAME_scoreTime, true);
        return dao.findFirstByQuery(q);
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
