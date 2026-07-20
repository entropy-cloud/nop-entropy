/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.entity;


import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.biz.INopMetaQualityScoreBiz;
import io.nop.metadata.dao.entity.NopMetaQualityScore;
import io.nop.metadata.service.quality.MetaQualityScorer;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 质量评分 BizModel：基线 CRUD（{@link CrudBizModel}）+ 手动评分入口（架构基线 §2.7.4）。
 *
 * <p>评分计算委托无状态 {@link MetaQualityScorer}（读 QualityResult → 维度聚合 → 加权 → 趋势 → 结构化结果），
 * 本 BizModel 仅承担落盘新 {@link NopMetaQualityScore} 行 + 返回结果摘要。延迟装配 scorer（需 {@code daoProvider()}，
 * 构造时不可用）。
 *
 * <p>不可评路径（表不存在 / 无规则 / 全 SKIP）由 scorer 显式抛 inline ErrorCode（D6，不静默 0 分、不伪造）。
 */
@BizModel("NopMetaQualityScore")
public class NopMetaQualityScoreBizModel extends CrudBizModel<NopMetaQualityScore>
        implements INopMetaQualityScoreBiz {

    @Inject
    protected io.nop.dao.api.IDaoProvider daoProvider;

    /** 质量评分器（无状态，延迟初始化，需 daoProvider）。 */
    private MetaQualityScorer scorer;

    public NopMetaQualityScoreBizModel() {
        setEntityName(NopMetaQualityScore.class.getName());
    }

    /**
     * 手动计算目标表的质量评分（架构基线 §2.7.4 D2-D6）。
     *
     * <p>委托 {@link MetaQualityScorer#score}（读规则最新 QualityResult → 维度映射 → 维度 pass rate →
     * 加权总分 → 趋势先查后写），落盘一行新 {@link NopMetaQualityScore}（scoreTime=now，时序追加不覆盖），
     * 返回 {@code {scoreId, overallScore, dimensionScores, ruleSummary, trend}}。
     *
     * @param metaTableId 目标逻辑表 ID（NopMetaTable.metaTableId）
     * @param context     服务上下文
     * @return 评分摘要 {@code {scoreId, overallScore, dimensionScores, ruleSummary, trend}}
     */
    @BizMutation
    public Map<String, Object> computeQualityScore(@Name("metaTableId") String metaTableId,
                                                    IServiceContext context) {
        MetaQualityScorer.QualityScoreResult result = ensureScorer().score(metaTableId);

        // 落盘新评分行（时序语义：scoreTime=now，不覆盖）
        NopMetaQualityScore row = dao().newEntity();
        row.setMetaTableId(metaTableId);
        row.setScoreTime(CoreMetrics.currentTimestamp());
        row.setOverallScore(result.getOverallScore());
        row.setDimensionScores(JsonTool.stringify(result.getDimensionScores()));
        row.setRuleSummary(JsonTool.stringify(result.getRuleSummary()));
        row.setTrend(JsonTool.stringify(result.getTrend()));
        dao().saveEntity(row);

        // 返回摘要（含落盘后的 scoreId）
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scoreId", row.getQualityScoreId());
        summary.put("overallScore", result.getOverallScore());
        summary.put("dimensionScores", result.getDimensionScores());
        summary.put("ruleSummary", result.getRuleSummary());
        summary.put("trend", result.getTrend());
        return summary;
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 延迟初始化 MetaQualityScorer（需 daoProvider，构造时不可用）。 */
    private MetaQualityScorer ensureScorer() {
        if (scorer == null) {
            scorer = new MetaQualityScorer(daoProvider());
        }
        return scorer;
    }
}
