package io.nop.metadata.service.entity;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.biz.INopMetaQualityScoreBiz;
import io.nop.metadata.api.dto.QualityScoreResultDTO;
import io.nop.metadata.dao.entity.NopMetaQualityScore;
import io.nop.metadata.service.quality.MetaQualityScorer;
import jakarta.inject.Inject;

import java.sql.Timestamp;

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

        NopMetaQualityScore row = dao().newEntity();
        row.setMetaTableId(metaTableId);
        row.setScoreTime(CoreMetrics.currentTimestamp());
        row.setOverallScore(result.getOverallScore());
        row.setDimensionScores(JsonTool.stringify(result.getDimensionScores()));
        row.setRuleSummary(JsonTool.stringify(result.getRuleSummary()));
        row.setTrend(JsonTool.stringify(result.getTrend()));

        checkDataAuth(io.nop.biz.BizConstants.METHOD_SAVE, row, context);
        dao().saveEntity(row);

        QualityScoreResultDTO dto = new QualityScoreResultDTO();
        dto.setScoreId(row.getQualityScoreId());
        dto.setQualityScoreId(row.getQualityScoreId());
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
