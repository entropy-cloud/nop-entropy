
package io.nop.metadata.service.quality;


import io.nop.api.core.time.CoreMetrics;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;

import java.sql.Timestamp;
import java.util.Set;

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
     * 质量结果 status 合法值（对应 orm 模型 dict meta/quality-result-status）。
     * 落盘前显式校验（service 层共享 helper 不持有 xmeta 上下文，故为显式字段校验而非走 xmeta 管线，
     * plan 2026-08-04-1543-3 R2.8 / 11-04）。
     */
    private static final Set<String> ALLOWED_STATUSES = Set.of("PASS", "FAIL", "ERROR", "SKIP");

    /**
     * 将单规则判定结果追加为一行 NopMetaQualityResult 并保存。
     *
     * @param resultDao     结果实体 DAO（由调用方按其上下文获取，BizModel 用 {@code daoFor(...)}，
     *                      checkpoint executor 用 {@code daoProvider.daoFor(...)}）
     * @param qualityRuleId 规则 ID
     * @param checkpointId  检查点 ID（检查点执行路径非 null；单规则路径为 null）
     * @param runId         执行批次 ID（UUID，每次执行唯一；检查点执行路径非 null；单规则路径为 null）。
     *                      配合复合 UK {@code UK_NOP_META_QUALITY_RESULT_CP_RUN_RULE (checkpointId, runId,
     *                      qualityRuleId)} 兜底拒绝同一次执行的重复写行
     * @param judgment      判定结果（status/actualValue/expectedValue/message/details 全显式填充）
     * @return 已保存的结果行
     */
    public NopMetaQualityResult append(IEntityDao<NopMetaQualityResult> resultDao,
                                       String qualityRuleId, String checkpointId, String runId,
                                       QualityRuleJudgment judgment) {
        String status = judgment.getStatus();
        if (status == null || !ALLOWED_STATUSES.contains(status)) {
            // 落盘前 fail-fast：非法 status 不静默写入（参照 xmeta dict 校验语义）
            throw new NopMetadataException(NopMetadataErrors.ERR_QUALITY_RESULT_STATUS_INVALID)
                    .param("status", String.valueOf(status));
        }
        NopMetaQualityResult row = resultDao.newEntity();
        row.setQualityRuleId(qualityRuleId);
        row.setCheckpointId(checkpointId);
        row.setRunId(runId);
        row.setExecuteTime(CoreMetrics.currentTimestamp());
        row.setStatus(status);
        row.setActualValue(judgment.getActualValue());
        row.setExpectedValue(judgment.getExpectedValue());
        row.setMessage(judgment.getMessage());
        row.setDetails(JsonTool.stringify(judgment.getDetails()));
        resultDao.saveEntity(row);
        return row;
    }
}
