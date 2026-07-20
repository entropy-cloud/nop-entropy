/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.quality;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.dao.entity.NopMetaQualityScore;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 质量评分器（架构基线 §2.7.4）。无状态计算器：读取目标表挂载规则的**最新** QualityResult（D4）→ 按 D2
 * 映射维度 → 算维度 pass rate（D3，SKIP 排除分母外）→ 加权总分（仅对非 null 维度归一化，D3）→ 趋势（D5，
 * 先查后写，本方法读上一条 QualityScore 但**不写**新行，写入由 BizModel 承担）。
 *
 * <p>本类**不自建连接**、**不写实体**（纯读 + 计算），返回结构化 {@link QualityScoreResult} 供
 * {@code NopMetaQualityScoreBizModel} 落盘。失败/不可评路径均显式（D6，不静默 0 分、不伪造、不产生 NaN）：
 * <ul>
 *   <li>metaTableId 不存在 → 抛 {@link #NopMetadataErrors.ERR_SCORE_TABLE_NOT_FOUND}</li>
 *   <li>表无任何挂载规则 → 抛 {@link #NopMetadataErrors.ERR_SCORE_NO_RULES}</li>
 *   <li>所有规则最新结果全 SKIP / 无可评结果（全维度 null）→ 抛 {@link #NopMetadataErrors.ERR_SCORE_ALL_SKIP}</li>
 * </ul>
 *
 * <p>降级语义（D2，对齐 Profiling 降级铁律，不伪造）：
 * <ul>
 *   <li>无规则的维度 → dimensionScores 该维度 null + {@code unavailable=["no-rules"]}</li>
 *   <li>SKIP-only 维度（有规则但最新结果全 SKIP/无结果）→ null + {@code unavailable=["skipped"]}</li>
 * </ul>
 */
public class MetaQualityScorer {

    /** 趋势 stable 阈值：|changeRate| &lt; 此值视为 stable（架构基线 §2.7.4 D5）。 */
    public static final double TREND_STABLE_THRESHOLD = 1.0d;


    // ===== 五个评分维度（架构基线 §2.7.4 D2） =====
    static final String DIM_COMPLETENESS = "completeness";
    static final String DIM_ACCURACY = "accuracy";
    static final String DIM_CONSISTENCY = "consistency";
    static final String DIM_TIMELINESS = "timeliness";
    static final String DIM_UNIQUENESS = "uniqueness";

    /** 五维有序集合（dimensionScores 按此顺序输出）。 */
    private static final List<String> ALL_DIMENSIONS =
            Arrays.asList(DIM_COMPLETENESS, DIM_ACCURACY, DIM_CONSISTENCY, DIM_TIMELINESS, DIM_UNIQUENESS);

    private static final Set<String> VALID_DIMENSIONS = new LinkedHashSet<>(ALL_DIMENSIONS);

    // ===== 默认权重（design 06 §5.2 / §2.7.4 D3） =====
    private static final Map<String, Double> DEFAULT_WEIGHTS = new LinkedHashMap<>();
    static {
        DEFAULT_WEIGHTS.put(DIM_COMPLETENESS, 0.3d);
        DEFAULT_WEIGHTS.put(DIM_ACCURACY, 0.3d);
        DEFAULT_WEIGHTS.put(DIM_CONSISTENCY, 0.2d);
        DEFAULT_WEIGHTS.put(DIM_TIMELINESS, 0.1d);
        DEFAULT_WEIGHTS.put(DIM_UNIQUENESS, 0.1d);
    }

    private final IDaoProvider daoProvider;

    public MetaQualityScorer(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    /**
     * 计算目标表的质量评分（架构基线 §2.7.4 D2-D6）。
     *
     * <p>本方法读 QualityResult + 上一条 QualityScore 计算，**不写**新行。BizModel 拿到
     * {@link QualityScoreResult} 后落盘。
     *
     * @param metaTableId 目标逻辑表 ID（NopMetaTable.metaTableId）
     * @return 结构化评分结果（overallScore + dimensionScores + ruleSummary + trend）
     * @throws NopException 不可评路径（D6）：表不存在 / 无规则 / 全 SKIP
     */
    public QualityScoreResult score(String metaTableId) {
        // D6：metaTableId 不存在 → 显式失败
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable table = tableDao.getEntityById(metaTableId);
        if (table == null) {
            throw new NopException(NopMetadataErrors.ERR_SCORE_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }

        // 加载挂载规则（entityId = metaTableId，即 §2.7.1 D1 规则仅挂载于 NopMetaTable）
        IEntityDao<NopMetaQualityRule> ruleDao = daoProvider.daoFor(NopMetaQualityRule.class);
        QueryBean ruleQuery = new QueryBean();
        ruleQuery.addFilter(FilterBeans.eq(NopMetaQualityRule.PROP_NAME_entityId, metaTableId));
        List<NopMetaQualityRule> rules = ruleDao.findAllByQuery(ruleQuery);

        // D6：表无任何挂载规则 → 显式失败（不静默 0 分）
        if (rules.isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_SCORE_NO_RULES).param("metaTableId", metaTableId);
        }

        IEntityDao<NopMetaQualityResult> resultDao = daoProvider.daoFor(NopMetaQualityResult.class);

        // 每规则取最新 QualityResult（D4：executeTime DESC 取首；无结果 → SKIP 等价，不计入维度分子分母）
        // 同时统计 ruleSummary（passed/failed/error/skip）。
        int passedRules = 0;
        int failedRules = 0;
        int errorRules = 0;
        int skipRules = 0;

        // 每维度的计数器：pass / nonPass(FAIL+ERROR) / skipOrAbsent
        Map<String, int[]> dimCounts = new LinkedHashMap<>();
        for (String dim : ALL_DIMENSIONS) {
            dimCounts.put(dim, new int[]{0, 0, 0}); // [pass, nonPass, skip]
        }

        for (NopMetaQualityRule rule : rules) {
            NopMetaQualityResult latest = findLatestResult(resultDao, rule.getQualityRuleId());
            String status = latest != null ? latest.getStatus() : _NopMetadataCoreConstants.QUALITY_RESULT_STATUS_SKIP;
            String dim = mapDimension(rule);

            if (_NopMetadataCoreConstants.QUALITY_RESULT_STATUS_PASS.equals(status)) {
                passedRules++;
                dimCounts.get(dim)[0] += 1;
            } else if (_NopMetadataCoreConstants.QUALITY_RESULT_STATUS_FAIL.equals(status)) {
                failedRules++;
                dimCounts.get(dim)[1] += 1;
            } else if (_NopMetadataCoreConstants.QUALITY_RESULT_STATUS_ERROR.equals(status)) {
                // D2：ERROR 保守计未通过（进 nonPass，与 FAIL 同列）
                errorRules++;
                dimCounts.get(dim)[1] += 1;
            } else {
                // SKIP（含无结果等价处理，D4）——不计入任何维度的分子分母，单列 skipRules
                skipRules++;
                dimCounts.get(dim)[2] += 1;
            }
        }

        // 计算维度分 + 降级标记（D2/D3）
        Map<String, Object> dimensionScores = new LinkedHashMap<>();
        Map<String, List<String>> unavailableByDim = new LinkedHashMap<>();
        for (String dim : ALL_DIMENSIONS) {
            int[] counts = dimCounts.get(dim);
            int pass = counts[0];
            int nonPass = counts[1];
            int skip = counts[2];
            int total = pass + nonPass + skip;
            if (total == 0) {
                // 该维度无任何挂载规则 → null + no-rules
                dimensionScores.put(dim, null);
                unavailableByDim.put(dim, Collections.singletonList("no-rules"));
            } else if (pass == 0 && nonPass == 0) {
                // SKIP-only 维度（有规则但全 SKIP/无结果）→ null + skipped（不计 0、不产生 NaN）
                dimensionScores.put(dim, null);
                unavailableByDim.put(dim, Collections.singletonList("skipped"));
            } else {
                // D3：维度分 = pass / (pass + nonPass) * 100（SKIP 排除分母外）
                double dimScore = pass * 100.0d / (pass + nonPass);
                dimensionScores.put(dim, dimScore);
            }
        }

        // D3：总分 = Σ(非 null 维度分 × 权重) / Σ(非 null 维度权重)（仅对非 null 维度归一化）
        double weightedSum = 0.0d;
        double weightSum = 0.0d;
        for (String dim : ALL_DIMENSIONS) {
            Object v = dimensionScores.get(dim);
            if (v instanceof Number) {
                double dimScore = ((Number) v).doubleValue();
                double weight = DEFAULT_WEIGHTS.get(dim);
                weightedSum += dimScore * weight;
                weightSum += weight;
            }
        }

        // D6：全维度 null（无任何可评规则或全 SKIP）→ 显式失败（不静默 0 分、不伪造）
        if (weightSum == 0.0d) {
            throw new NopException(NopMetadataErrors.ERR_SCORE_ALL_SKIP).param("metaTableId", metaTableId);
        }
        double overallScore = weightedSum / weightSum;

        // ruleSummary
        Map<String, Object> ruleSummary = new LinkedHashMap<>();
        ruleSummary.put("totalRules", rules.size());
        ruleSummary.put("passedRules", passedRules);
        ruleSummary.put("failedRules", failedRules);
        ruleSummary.put("errorRules", errorRules);
        ruleSummary.put("skipRules", skipRules);

        // 附加 unavailable 汇总到 dimensionScores 输出（便于消费方识别降级维度，不伪造分值）
        if (!unavailableByDim.isEmpty()) {
            dimensionScores.put("unavailable", unavailableByDim);
        }

        // D5：趋势（先查后写——读上一条 QualityScore，此时新行尚未写入）
        Map<String, Object> trend = computeTrend(metaTableId, overallScore);

        return new QualityScoreResult(overallScore, dimensionScores, ruleSummary, trend);
    }

    // ============================================================
    // D2：维度映射 ruleType → dimension（含 extConfig.dimension 覆盖）
    // ============================================================

    /**
     * 维度映射（§2.7.4 D2）。优先读 rule.extConfig.dimension 覆盖（覆盖值不在五维内 → consistency）；
     * 否则按 ruleType 静态映射。
     */
    private String mapDimension(NopMetaQualityRule rule) {
        String override = readExtConfigDimension(rule);
        if (override != null && !override.isEmpty()) {
            if (VALID_DIMENSIONS.contains(override)) {
                return override;
            }
            // 覆盖值不在五维内 → 计 consistency（D2）
            return DIM_CONSISTENCY;
        }
        String ruleType = rule.getRuleType();
        if (ruleType == null) {
            return DIM_CONSISTENCY;
        }
        switch (ruleType) {
            case _NopMetadataCoreConstants.QUALITY_RULE_TYPE_NOT_NULL:
            case _NopMetadataCoreConstants.QUALITY_RULE_TYPE_VOLUME:
                return DIM_COMPLETENESS;
            case _NopMetadataCoreConstants.QUALITY_RULE_TYPE_UNIQUE:
                return DIM_UNIQUENESS;
            case _NopMetadataCoreConstants.QUALITY_RULE_TYPE_RANGE:
            case _NopMetadataCoreConstants.QUALITY_RULE_TYPE_REGEX:
                return DIM_ACCURACY;
            case _NopMetadataCoreConstants.QUALITY_RULE_TYPE_FRESHNESS:
                return DIM_TIMELINESS;
            case _NopMetadataCoreConstants.QUALITY_RULE_TYPE_CUSTOM_SQL:
            default:
                return DIM_CONSISTENCY;
        }
    }

    /** 读取 rule.extConfig.dimension 覆盖（JSON 列，缺失返回 null）。 */
    @SuppressWarnings("unchecked")
    private String readExtConfigDimension(NopMetaQualityRule rule) {
        String json = rule.getExtConfig();
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            Object parsed = JsonTool.parse(json);
            if (!(parsed instanceof Map)) {
                return null;
            }
            Object dim = ((Map<String, Object>) parsed).get("dimension");
            return dim != null ? String.valueOf(dim) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ============================================================
    // D5：趋势计算（先查后写）
    // ============================================================

    /** 读同 (metaTableId) 上一条 QualityScore（scoreTime DESC 取首），算 changeRate + trendDirection。 */
    private Map<String, Object> computeTrend(String metaTableId, double overallScore) {
        IEntityDao<NopMetaQualityScore> scoreDao = daoProvider.daoFor(NopMetaQualityScore.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaQualityScore.PROP_NAME_metaTableId, metaTableId));
        q.addOrderField(NopMetaQualityScore.PROP_NAME_scoreTime, true);
        NopMetaQualityScore previous = scoreDao.findFirstByQuery(q);

        Map<String, Object> trend = new LinkedHashMap<>();
        if (previous == null || previous.getOverallScore() == null) {
            // 无历史 → trend null + 标记（不伪造 0）
            trend.put("previousScore", null);
            trend.put("changeRate", null);
            trend.put("trendDirection", null);
            trend.put("unavailable", Collections.singletonList("no-history"));
            return trend;
        }
        double prev = previous.getOverallScore();
        double changeRate = overallScore - prev;
        String direction;
        if (Math.abs(changeRate) < TREND_STABLE_THRESHOLD) {
            direction = _NopMetadataCoreConstants.QUALITY_TREND_DIRECTION_STABLE;
        } else if (changeRate > 0) {
            direction = _NopMetadataCoreConstants.QUALITY_TREND_DIRECTION_IMPROVING;
        } else {
            direction = _NopMetadataCoreConstants.QUALITY_TREND_DIRECTION_DEGRADING;
        }
        trend.put("previousScore", prev);
        trend.put("changeRate", changeRate);
        trend.put("trendDirection", direction);
        return trend;
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 取某 ruleId 最新一条 QualityResult（按 executeTime DESC 取首，§2.7.4 D4）。 */
    private NopMetaQualityResult findLatestResult(IEntityDao<NopMetaQualityResult> dao, String qualityRuleId) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaQualityResult.PROP_NAME_qualityRuleId, qualityRuleId));
        q.addOrderField(NopMetaQualityResult.PROP_NAME_executeTime, true);
        return dao.findFirstByQuery(q);
    }

    // ============================================================
    // 结构化结果（供 BizModel 落盘）
    // ============================================================

    /**
     * 评分计算结果（无状态计算产物，BizModel 据此落盘 NopMetaQualityScore 行）。
     * 字段对齐 §2.7.4 D1 实体结构（overallScore / dimensionScores / ruleSummary / trend）。
     */
    public static class QualityScoreResult {
        private final double overallScore;
        private final Map<String, Object> dimensionScores;
        private final Map<String, Object> ruleSummary;
        private final Map<String, Object> trend;

        public QualityScoreResult(double overallScore, Map<String, Object> dimensionScores,
                                  Map<String, Object> ruleSummary, Map<String, Object> trend) {
            this.overallScore = overallScore;
            this.dimensionScores = dimensionScores;
            this.ruleSummary = ruleSummary;
            this.trend = trend;
        }

        public double getOverallScore() {
            return overallScore;
        }

        public Map<String, Object> getDimensionScores() {
            return dimensionScores;
        }

        public Map<String, Object> getRuleSummary() {
            return ruleSummary;
        }

        public Map<String, Object> getTrend() {
            return trend;
        }
    }
}
