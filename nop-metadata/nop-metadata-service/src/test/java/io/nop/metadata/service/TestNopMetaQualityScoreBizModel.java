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
import io.nop.core.lang.json.JsonTool;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证质量评分（架构基线 §2.7.4）：computeQualityScore 对多 ruleType 混合 QualityResult 的维度映射、
 * 维度 pass rate + 总分归一化、SKIP-only / 无规则维度降级、趋势先查后写，以及所有不可评路径的显式失败。
 *
 * <p>覆盖 Exit Criteria 六条路径：(a) 混合 ruleType 维度映射 (b) 维度分 pass rate + 总分归一化
 * (c) SKIP-only 维度 null (d) 趋势 (e) 无规则失败 (f) 全 SKIP 失败。
 *
 * <p>Anti-Hollow：评分器直接消费预置的真实 QualityResult 行（按 qualityRuleId + executeTime DESC 取最新），
 * 断言落盘的 NopMetaQualityScore 行的 dimensionScores/ruleSummary/trend JSON 与手工计算逐字段一致，
 * 证明运行时确实读取 QualityResult 并按维度聚合（非空壳返回固定分、非伪造、非 NaN）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaQualityScoreBizModel extends JunitBaseTestCase {

    public TestNopMetaQualityScoreBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    // ===== (a)+(b) 混合 ruleType 维度映射 + 维度分 pass rate + 总分归一化 =====

    /**
     * 6 规则覆盖全部五维 + ERROR 计未通过 + 最新结果选取（D4 executeTime DESC）。验证：
     * <ul>
     *   <li>(a) ruleType→dimension 映射正确（not_null/volume→completeness；unique→uniqueness；
     *       range→accuracy；custom_sql→consistency；freshness→timeliness）</li>
     *   <li>(b) 维度分 = PASS/(PASS+FAIL+ERROR)×100；总分 = 加权归一化（与手工计算 55.0 一致）</li>
     *   <li>r-nn 有历史 FAIL + 最新 PASS → 取最新 PASS（证明 D4 DESC 取首）</li>
     *   <li>ERROR（r-range）保守计未通过（accuracy=0）</li>
     *   <li>落盘 NopMetaQualityScore 行的 dimensionScores/ruleSummary 与 fixture 一致（非空壳）</li>
     * </ul>
     *
     * 手工计算：completeness=50 (nn PASS + vol FAIL → 1/2)、uniqueness=100、accuracy=0 (range ERROR)、
     * consistency=100、timeliness=100。权重 completeness 0.3 / accuracy 0.3 / consistency 0.2 /
     * timeliness 0.1 / uniqueness 0.1（design 06 §5.2）。overall = 50×0.3 + 0×0.3 + 100×0.2 + 100×0.1 +
     * 100×0.1 = 15+0+20+10+10 = 55.0。ruleSummary: total=6, passed=4, failed=1, error=1, skip=0。
     */
    @Test
    public void testMixedDimensionsAndOverallScore() {
        String tableId = saveTable("T_MIX");
        saveRule("r-nn", "not_null", tableId);
        saveRule("r-vol", "volume", tableId);
        saveRule("r-uniq", "unique", tableId);
        saveRule("r-range", "range", tableId);
        saveRule("r-cs", "custom_sql", tableId);
        saveRule("r-fresh", "freshness", tableId);

        long base = 1_700_000_000_000L;
        // r-nn：历史 FAIL（旧）+ 最新 PASS（新）→ 取最新 PASS（D4）
        saveResult("r-nn", "FAIL", base);
        saveResult("r-nn", "PASS", base + 2000);
        saveResult("r-vol", "FAIL", base);
        saveResult("r-uniq", "PASS", base);
        saveResult("r-range", "ERROR", base);   // ERROR 保守计未通过
        saveResult("r-cs", "PASS", base);
        saveResult("r-fresh", "PASS", base);

        GraphQLResponseBean resp = exec(tableId);
        assertFalse(resp.hasError(), "mixed scoring should not globally error: " + resp);

        NopMetaQualityScore row = findLatestScore(tableId);
        assertNotNull(row, "a NopMetaQualityScore row must be persisted (anti-hollow)");
        assertEquals(55.0, row.getOverallScore(), 0.001, "overall must match manual calc 55.0");

        Map<String, Object> dims = parseJson(row.getDimensionScores());
        assertEquals(50.0, toDouble(dims.get("completeness")), 0.001, "completeness=1/2*100");
        assertEquals(100.0, toDouble(dims.get("uniqueness")), 0.001, "uniqueness=1/1*100");
        assertEquals(0.0, toDouble(dims.get("accuracy")), 0.001, "accuracy=0 (range ERROR counts as non-pass)");
        assertEquals(100.0, toDouble(dims.get("consistency")), 0.001, "consistency=1/1*100");
        assertEquals(100.0, toDouble(dims.get("timeliness")), 0.001, "timeliness=1/1*100");

        Map<String, Object> ruleSummary = parseJson(row.getRuleSummary());
        assertEquals(6, toInt(ruleSummary.get("totalRules")), "totalRules=6");
        assertEquals(4, toInt(ruleSummary.get("passedRules")), "passedRules=4 (nn,uniq,cs,fresh)");
        assertEquals(1, toInt(ruleSummary.get("failedRules")), "failedRules=1 (vol)");
        assertEquals(1, toInt(ruleSummary.get("errorRules")), "errorRules=1 (range)");
        assertEquals(0, toInt(ruleSummary.get("skipRules")), "skipRules=0");
    }

    // ===== (c) SKIP-only 维度 null + 无规则维度 null（降级，不伪造） =====

    /**
     * 表 T_SKIP 上：freshness 规则最新结果全 SKIP（SKIP-only 维度）+ not_null 规则 PASS。验证：
     * <ul>
     *   <li>timeliness（freshness 全 SKIP）→ null + unavailable=["skipped"]（不计 0、不 NaN）</li>
     *   <li>accuracy/consistency/uniqueness 无规则 → null + unavailable=["no-rules"]（不伪造）</li>
     *   <li>completeness（not_null PASS）→ 100（有可评结果，正常算分）</li>
     *   <li>overall 仅对非 null 维度归一化 = 100（completeness 权重 0.3 / 0.3）</li>
     *   <li>非全 null → 不失败（completeness 可评）</li>
     * </ul>
     */
    @Test
    public void testSkipOnlyAndNoRulesDimensionsNull() {
        String tableId = saveTable("T_SKIP");
        saveRule("r-skip", "freshness", tableId);
        saveRule("r-nn2", "not_null", tableId);
        saveResult("r-skip", "SKIP", 1_700_000_000_000L);
        saveResult("r-nn2", "PASS", 1_700_000_000_000L);

        GraphQLResponseBean resp = exec(tableId);
        assertFalse(resp.hasError(), "non-all-null must score (no silent fail): " + resp);

        NopMetaQualityScore row = findLatestScore(tableId);
        assertNotNull(row);
        // SKIP-only 维度 + 无规则维度都不失败整体（completeness 可评）
        assertEquals(100.0, row.getOverallScore(), 0.001, "overall normalized over non-null completeness only");

        Map<String, Object> dims = parseJson(row.getDimensionScores());
        assertEquals(100.0, toDouble(dims.get("completeness")), 0.001, "completeness=100 (nn2 PASS)");
        assertNull(dims.get("timeliness"), "SKIP-only dimension must be null (not 0/NaN)");
        assertNull(dims.get("accuracy"), "no-rule dimension must be null (not forged)");
        assertNull(dims.get("consistency"), "no-rule dimension must be null (not forged)");
        assertNull(dims.get("uniqueness"), "no-rule dimension must be null (not forged)");

        @SuppressWarnings("unchecked")
        Map<String, Object> unavailable = (Map<String, Object>) dims.get("unavailable");
        assertNotNull(unavailable, "unavailable markers must be present (explicit degradation)");
        assertEquals("[skipped]", String.valueOf(unavailable.get("timeliness")));
        assertEquals("[no-rules]", String.valueOf(unavailable.get("accuracy")));
        assertEquals("[no-rules]", String.valueOf(unavailable.get("consistency")));
        assertEquals("[no-rules]", String.valueOf(unavailable.get("uniqueness")));
    }

    // ===== (d) 趋势（先查后写） =====

    /**
     * 同一表连续两次评分。验证 D5：
     * <ul>
     *   <li>第一次评分：无历史 → trend.previousScore=null + unavailable=["no-history"]</li>
     *   <li>改最新结果使分数下降后再评：trend.previousScore=首次分、changeRate&lt;0、trendDirection=degrading</li>
     * </ul>
     * 表 T_TREND：单条 not_null 规则。首次 PASS（completeness=100, overall=100）；插入更新 FAIL 后再评
     * （completeness=0, overall=0；trend.previousScore=100, changeRate=-100, degrading）。
     */
    @Test
    public void testTrendBeforeAndAfter() throws Exception {
        String tableId = saveTable("T_TREND");
        saveRule("r-tr", "not_null", tableId);
        long t0 = 1_700_000_000_000L;
        saveResult("r-tr", "PASS", t0);

        // 第一次评分：无历史
        GraphQLResponseBean resp1 = exec(tableId);
        assertFalse(resp1.hasError(), "first score should not error: " + resp1);
        NopMetaQualityScore row1 = findLatestScore(tableId);
        assertEquals(100.0, row1.getOverallScore(), 0.001);
        Map<String, Object> trend1 = parseJson(row1.getTrend());
        assertNull(trend1.get("previousScore"), "no history → previousScore null");
        assertNull(trend1.get("trendDirection"), "no history → trendDirection null");
        assertNotNull(trend1.get("unavailable"), "no history → unavailable marker");

        // 插入更新的（更晚 executeTime）FAIL 结果：completeness 降为 0
        saveResult("r-tr", "FAIL", t0 + 5000);

        // 第二次评分：有历史，分数下降
        GraphQLResponseBean resp2 = exec(tableId);
        assertFalse(resp2.hasError(), "second score should not error: " + resp2);
        NopMetaQualityScore row2 = findLatestScore(tableId);
        assertEquals(0.0, row2.getOverallScore(), 0.001, "completeness now 0 → overall 0");
        Map<String, Object> trend2 = parseJson(row2.getTrend());
        assertEquals(100.0, toDouble(trend2.get("previousScore")), 0.001, "previousScore=first overall 100");
        assertEquals(-100.0, toDouble(trend2.get("changeRate")), 0.001, "changeRate=0-100=-100");
        assertEquals("degrading", String.valueOf(trend2.get("trendDirection")), "degrading");
    }

    // ===== (e) 无规则失败 =====

    /** 表无任何挂载规则 → computeQualityScore 显式失败（D6，不静默 0 分）。 */
    @Test
    public void testNoRulesFails() {
        String tableId = saveTable("T_NO_RULES");
        GraphQLResponseBean resp = exec(tableId);
        assertTrue(resp.hasError(), "table with no rules must explicitly fail (no silent 0): " + resp);
    }

    // ===== (f) 全 SKIP 失败 =====

    /**
     * 表上规则最新结果全 SKIP（全维度 null）→ computeQualityScore 显式失败（D6，不静默 0 分、不伪造）。
     */
    @Test
    public void testAllSkipResultsFails() {
        String tableId = saveTable("T_ALL_SKIP");
        saveRule("r-sk1", "not_null", tableId);
        saveRule("r-sk2", "unique", tableId);
        saveResult("r-sk1", "SKIP", 1_700_000_000_000L);
        saveResult("r-sk2", "SKIP", 1_700_000_000_000L);

        GraphQLResponseBean resp = exec(tableId);
        assertTrue(resp.hasError(), "all-SKIP results (all dimensions null) must explicitly fail: " + resp);
    }

    /** 表不存在 → computeQualityScore 显式失败（D6）。 */
    @Test
    public void testTableNotFoundFails() {
        GraphQLResponseBean resp = exec("__nope_table__");
        assertTrue(resp.hasError(), "non-existent table must explicitly fail: " + resp);
    }

    /** 规则从未执行（无任何 QualityResult）→ 等价 SKIP 处理；全无结果 → 全维度 null → 显式失败（D4+D6）。 */
    @Test
    public void testRulesNeverExecutedFails() {
        String tableId = saveTable("T_NEVER_EXEC");
        saveRule("r-ne", "not_null", tableId);
        // 不写任何 QualityResult
        GraphQLResponseBean resp = exec(tableId);
        assertTrue(resp.hasError(), "rules never executed (all dimensions null) must explicitly fail: " + resp);
    }

    // ===== custom_sql extConfig.dimension 覆盖（D2 扩展验证） =====

    /** custom_sql 默认 consistency；通过 extConfig.dimension=accuracy 覆盖 → 计 accuracy。 */
    @Test
    public void testCustomSqlDimensionOverride() {
        String tableId = saveTable("T_OVERRIDE");
        // custom_sql 默认 consistency，但 extConfig.dimension=accuracy 覆盖
        saveRuleWithExt("r-cs-acc", "custom_sql", tableId, "{\"dimension\":\"accuracy\"}");
        saveResult("r-cs-acc", "PASS", 1_700_000_000_000L);

        GraphQLResponseBean resp = exec(tableId);
        assertFalse(resp.hasError(), "override scoring should not error: " + resp);
        NopMetaQualityScore row = findLatestScore(tableId);
        Map<String, Object> dims = parseJson(row.getDimensionScores());
        assertEquals(100.0, toDouble(dims.get("accuracy")), 0.001, "custom_sql overridden to accuracy");
        assertNull(dims.get("consistency"), "consistency must be null (no rules mapped to it)");
    }

    // ============================================================
    // helpers
    // ============================================================

    private GraphQLResponseBean exec(String metaTableId) {
        IGraphQLExecutionContext ctx = graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaQualityScore__computeQualityScore(metaTableId: \"" + metaTableId + "\") "
                        + "{ scoreId overallScore dimensionScores ruleSummary trend } }"));
        return graphQLEngine.executeGraphQL(ctx);
    }

    private GraphQLRequestBean req(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new java.util.LinkedHashMap<>();
        }
        Object parsed = JsonTool.parse(json);
        return parsed instanceof Map ? (Map<String, Object>) parsed : new java.util.LinkedHashMap<>();
    }

    private static double toDouble(Object v) {
        if (v == null) {
            return Double.NaN;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        return Double.parseDouble(String.valueOf(v));
    }

    private static int toInt(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return Integer.parseInt(String.valueOf(v));
    }

    private String saveTable(String tableName) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = tableDao.newEntity();
        t.setMetaModuleId(ensureExternalSystemModuleId());
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType("entity");
        t.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        t.setCreatedBy("autotest");
        t.setCreateTime(now);
        t.setUpdatedBy("autotest");
        t.setUpdateTime(now);
        tableDao.saveEntity(t);
        return t.getMetaTableId();
    }

    private String ensureExternalSystemModuleId() {
        IEntityDao<NopMetaModule> moduleDao = daoProvider.daoFor(NopMetaModule.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaModule.PROP_NAME_moduleId, "nop/meta-score-test"));
        NopMetaModule module = moduleDao.findFirstByQuery(q);
        if (module != null) {
            return module.getMetaModuleId();
        }
        module = moduleDao.newEntity();
        module.setModuleId("nop/meta-score-test");
        module.setModuleName("meta-score-test");
        module.setDisplayName("评分测试模块");
        module.setModuleVersion(1L);
        module.setStatus("RELEASED");
        module.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        module.setCreatedBy("autotest");
        module.setCreateTime(now);
        module.setUpdatedBy("autotest");
        module.setUpdateTime(now);
        moduleDao.saveEntity(module);
        return module.getMetaModuleId();
    }

    private void saveRule(String ruleId, String ruleType, String entityId) {
        saveRuleWithExt(ruleId, ruleType, entityId, null);
    }

    private void saveRuleWithExt(String ruleId, String ruleType, String entityId, String extConfig) {
        IEntityDao<NopMetaQualityRule> dao = daoProvider.daoFor(NopMetaQualityRule.class);
        NopMetaQualityRule rule = dao.newEntity();
        rule.setQualityRuleId(ruleId);
        rule.setRuleName(ruleId);
        rule.setDisplayName(ruleId);
        rule.setRuleType(ruleType);
        rule.setEntityType("table");
        rule.setEntityId(entityId);
        rule.setSeverity("WARNING");
        rule.setExtConfig(extConfig);
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
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaQualityScore.PROP_NAME_metaTableId, metaTableId));
        q.addOrderField(NopMetaQualityScore.PROP_NAME_scoreTime, true);
        return dao.findFirstByQuery(q);
    }
}
