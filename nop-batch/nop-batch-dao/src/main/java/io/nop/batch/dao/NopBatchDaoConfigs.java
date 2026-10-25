package io.nop.batch.dao;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.time.Duration;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * 1.7-C7-003（plan 2502）：batch 三账本表（nop_batch_task / nop_batch_task_var /
 * nop_batch_record_result + nop_batch_file 级联面）保留期清理参数。键命名与缺省值的唯一
 * 事实源 = ai-dev/design/concurrency/ledger-retention-policy.md §3.1/§3.2（nop-batch 行，
 * 与 nop-retry 同参——1.6-C7-001↔1.7-C7-003 批次对齐声明 batch-proposal §四 L314）；
 * 模块落地不得私改参数语义，参数修订只走该设计文档。
 */
public interface NopBatchDaoConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(NopBatchDaoConfigs.class);

    @Description("batch账本表保留期（天）。终态任务行/record_result超期清理；0=显式禁用周期清理")
    IConfigReference<Integer> CFG_BATCH_LEDGER_RETENTION_DAYS = varRef(s_loc, "nop.batch.ledger.retention-days",
            Integer.class, 30);

    @Description("batch账本清理触发周期。下限60s，低于下限启动期fail-fast")
    IConfigReference<Duration> CFG_BATCH_LEDGER_CLEANUP_INTERVAL = varRef(s_loc, "nop.batch.ledger.cleanup-interval",
            Duration.class, Duration.ofHours(1));

    @Description("batch账本单轮单表删除上限（先有界拾取主键集再按IN批删）。必须为正数")
    IConfigReference<Integer> CFG_BATCH_LEDGER_DELETE_BATCH_SIZE = varRef(s_loc, "nop.batch.ledger.delete-batch-size",
            Integer.class, 1000);

    /**
     * 清理触发周期下限（设计 §3.1 第2项）：低于该值启动期 fail-fast，防高频清理冲击业务库。
     */
    long BATCH_LEDGER_MIN_CLEANUP_INTERVAL_MILLIS = 60_000L;
}
