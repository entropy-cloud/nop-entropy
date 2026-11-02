package io.nop.metadata.service;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * W-08（plan 2528 裁定⑥）：对账候选池加载上界配置。键命名沿 {@code nop.metadata.reconciliation.*}
 * 惯例（与既有 {@code nop.metadata.reconciliation.fetch-limit} 同 face 段，行侧闸
 * {@code TestReconciliationFetchLimitMarker} 零回退）；载体形态对齐 {@link NopMetadataDatasourceConfigs}
 * / {@link NopMetadataLedgerConfigs} 先例。
 *
 * <p>超界语义：显式失败（{@code ERR_RECON_CANDIDATE_POOL_EXCEED_LIMIT} fail-fast，对齐同模块
 * 血缘 maxEdges 先例），不静默截断候选池——截断即静默改变匹配语义。调优 = 显式上调本键。
 */
public interface NopMetadataReconciliationConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(NopMetadataReconciliationConfigs.class);

    @Description("对账候选池单次执行加载上界（行间复用同一池快照）。超出即显式失败（不静默截断）；"
            + "非法值语义：<=0 视为无界并显式拒绝（配置期 fail-fast）")
    IConfigReference<Integer> CFG_RECON_CANDIDATE_POOL_LIMIT = varRef(s_loc,
            "nop.metadata.reconciliation.candidate-pool-limit", Integer.class, 1000);
}
