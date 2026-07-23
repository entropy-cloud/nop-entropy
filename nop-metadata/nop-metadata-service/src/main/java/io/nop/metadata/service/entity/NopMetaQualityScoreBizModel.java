package io.nop.metadata.service.entity;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.biz.INopMetaQualityScoreBiz;
import io.nop.metadata.api.dto.QualityScoreResultDTO;
import io.nop.metadata.dao.entity.NopMetaQualityScore;
import io.nop.metadata.service.quality.MetaQualityScorer;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

@BizModel("NopMetaQualityScore")
public class NopMetaQualityScoreBizModel extends CrudBizModel<NopMetaQualityScore>
        implements INopMetaQualityScoreBiz {

    @Inject
    protected io.nop.dao.api.IDaoProvider daoProvider;

    private MetaQualityScorer scorer;

    public NopMetaQualityScoreBizModel() {
        setEntityName(NopMetaQualityScore.class.getName());
    }

    @BizMutation
    public QualityScoreResultDTO computeQualityScore(@Name("metaTableId") String metaTableId,
                                                       IServiceContext context) {
        MetaQualityScorer.QualityScoreResult result = ensureScorer().score(metaTableId);

        // Cron/scheduler path may pass null context; create a minimal one for pipeline compatibility
        if (context == null) {
            context = new ServiceContextImpl();
        }

        // Build data map for pipeline-based save (respects xmeta insertable/updatable validation)
        Map<String, Object> data = new HashMap<>();
        data.put("metaTableId", metaTableId);
        data.put("scoreTime", CoreMetrics.currentTimestamp());
        data.put("overallScore", result.getOverallScore());
        data.put("dimensionScores", JsonTool.stringify(result.getDimensionScores()));
        data.put("ruleSummary", JsonTool.stringify(result.getRuleSummary()));
        data.put("trend", JsonTool.stringify(result.getTrend()));

        NopMetaQualityScore saved = doSave(data, null, (entityData, ctx) -> {}, context);

        QualityScoreResultDTO dto = new QualityScoreResultDTO();
        dto.setScoreId(saved.getQualityScoreId());
        dto.setQualityScoreId(saved.getQualityScoreId());
        dto.setOverallScore(result.getOverallScore());
        dto.setDimensionScores(result.getDimensionScores());
        dto.setRuleSummary(result.getRuleSummary());
        dto.setTrend(result.getTrend());
        return dto;
    }

    private MetaQualityScorer ensureScorer() {
        if (scorer == null) {
            scorer = new MetaQualityScorer(daoProvider());
        }
        return scorer;
    }
}
