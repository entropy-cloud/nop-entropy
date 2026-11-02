package io.nop.metadata.service;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.time.Duration;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * 5.5-W-02（plan 2502）：nop-metadata 六张 append-only 时序表（nop_meta_quality_result /
 * nop_meta_quality_score / nop_meta_catalog / nop_meta_profiling_result /
 * nop_meta_reconciliation_result / nop_meta_model_changed_event）保留期清理参数。键命名与
 * 缺省值的唯一事实源 = ai-dev/design/concurrency/ledger-retention-policy.md §3.1/§3.2
 * （nop-metadata 行，NopSysDaoConfigs 形态新建载体）；模块落地不得私改参数语义，参数修订
 * 只走该设计文档。
 * <p>
 * 七面同窗：event 表（mediumtext 前后双快照，全平台最重的账本行）的最长保留窗口 = 本统一
 * 保留期。第七面（C7-101 衍生 qualityBreachApproval 工作流实例，nop-wf 侧 NopWfInstance）已由
 * R3-05（plan 2528 裁定⑩）兑现承接——NopMetaLedgerCleaner 第七通道与六表共用本载体三键
 * （七面同窗，零新配置键，nop-wf 零产品 diff）。
 */
public interface NopMetadataLedgerConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(NopMetadataLedgerConfigs.class);

    @Description("元数据时序账本保留期（天，含 nop-wf 侧告警工作流实例第七通道——plan 2528 裁定⑩），"
            + "超期行由周期清理批量清扫；0=显式禁用周期清理；负值启动期 fail-fast。"
            + "G2 契约：本保留窗即 R3-05 W-28 reJudge 可复用的历史窗上界（ledger-retention-policy §3.5 差异条款②）")
    IConfigReference<Integer> CFG_META_LEDGER_RETENTION_DAYS = varRef(s_loc, "nop.metadata.ledger.retention-days",
            Integer.class, 30);

    @Description("元数据账本清理触发周期。下限 60s，低于下限启动期 fail-fast")
    IConfigReference<Duration> CFG_META_LEDGER_CLEANUP_INTERVAL = varRef(s_loc, "nop.metadata.ledger.cleanup-interval",
            Duration.class, Duration.ofHours(1));

    @Description("元数据账本单轮单表删除上限（先有界拾取主键集再按 IN 批删）。必须为正数")
    IConfigReference<Integer> CFG_META_LEDGER_DELETE_BATCH_SIZE = varRef(s_loc, "nop.metadata.ledger.delete-batch-size",
            Integer.class, 1000);

    /**
     * 清理触发周期下限（设计 §3.1 第 2 项）：低于该值启动期 fail-fast，防高频清理冲击业务库。
     */
    long META_LEDGER_MIN_CLEANUP_INTERVAL_MILLIS = 60_000L;
}
