package io.nop.metadata.service.quality;


import io.nop.api.core.time.CoreMetrics;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaQualityResult;

import java.sql.Timestamp;

/**
 * 质量结果写入共享 helper（架构基线 §2.7.3 D3）。把单规则判定结果（{@link QualityRuleJudgment}）追加为一行
 * 新的 {@link NopMetaQualityResult}（时序语义：executeTime=now，不覆盖旧行）。
 *
 * <p>本类是「单规则执行路径」（{@code NopMetaQualityRuleBizModel}）与「检查点编排路径」
 * （{@link MetaQualityCheckpointExecutor}）共用的结果落盘逻辑，避免跨类复制或提升 BizModel 私有方法可见性
 * 污染边界（§2.7.3 D3：不自建连接、不重写判定逻辑，仅复用既有写入语义）。
 *
 * <p>无状态，可在多个 BizModel / executor 间共享实例。
 */
public class QualityResultWriter {

    /**
     * 将单规则判定结果追加为一行 NopMetaQualityResult 并保存。
     *
     * @param resultDao     结果实体 DAO（由调用方按其上下文获取，BizModel 用 {@code daoFor(...)}，
     *                      checkpoint executor 用 {@code daoProvider.daoFor(...)}）
     * @param qualityRuleId 规则 ID
     * @param judgment      判定结果（status/actualValue/expectedValue/message/details 全显式填充）
     * @return 已保存的结果行
     */
    public NopMetaQualityResult append(IEntityDao<NopMetaQualityResult> resultDao,
                                       String qualityRuleId, QualityRuleJudgment judgment) {
        NopMetaQualityResult row = resultDao.newEntity();
        row.setQualityRuleId(qualityRuleId);
        row.setExecuteTime(CoreMetrics.currentTimestamp());
        row.setStatus(judgment.getStatus());
        row.setActualValue(judgment.getActualValue());
        row.setExpectedValue(judgment.getExpectedValue());
        row.setMessage(judgment.getMessage());
        row.setDetails(JsonTool.stringify(judgment.getDetails()));
        resultDao.saveEntity(row);
        return row;
    }
}
