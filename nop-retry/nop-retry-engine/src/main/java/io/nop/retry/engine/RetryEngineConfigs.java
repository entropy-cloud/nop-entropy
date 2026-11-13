package io.nop.retry.engine;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.time.Duration;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * 1.6-C7-001（plan 2502）：retry 三账本表（nop_retry_record / nop_retry_attempt /
 * nop_retry_dead_letter）保留期清理参数。键命名与缺省值的唯一事实源 =
 * ai-dev/design/concurrency/ledger-retention-policy.md §3.1/§3.2（nop-retry 行）；
 * 模块落地不得私改参数语义，参数修订只走该设计文档。
 */
public interface RetryEngineConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(RetryEngineConfigs.class);

    @Description("retry账本表保留期（天）。COMPLETED记录/死信/孤儿attempt超期清理；0=显式禁用周期清理")
    IConfigReference<Integer> CFG_RETRY_LEDGER_RETENTION_DAYS = varRef(s_loc, "nop.retry.ledger.retention-days",
            Integer.class, 30);

    @Description("retry账本清理触发周期。下限60s，低于下限启动期fail-fast")
    IConfigReference<Duration> CFG_RETRY_LEDGER_CLEANUP_INTERVAL = varRef(s_loc, "nop.retry.ledger.cleanup-interval",
            Duration.class, Duration.ofHours(1));

    @Description("retry账本单轮单表删除上限（先有界拾取主键集再按IN批删）。必须为正数")
    IConfigReference<Integer> CFG_RETRY_LEDGER_DELETE_BATCH_SIZE = varRef(s_loc, "nop.retry.ledger.delete-batch-size",
            Integer.class, 1000);

    /**
     * 清理触发周期下限（设计 §3.1 第2项）：低于该值启动期 fail-fast，防高频清理冲击业务库。
     */
    long RETRY_LEDGER_MIN_CLEANUP_INTERVAL_MILLIS = 60_000L;

    /**
     * 1.6-C3-003（plan 2515 裁定④）：attempt 级执行超时上界（毫秒）。缺省取租约
     * （DEFAULT_RETRYING_TIMEOUT_MS=600s）的一半分数档；装配期 fail-fast 校验必须小于租约——
     * 挂死 attempt 在租约到期前被超时取消落账，扫描器接管读到的必是已终态化的 attempt，
     * attempt 内双执行窗不可达。
     */
    IConfigReference<Long> CFG_RETRY_ATTEMPT_TIMEOUT_MS = varRef(s_loc, "nop.retry.attempt.timeout-ms",
            Long.class, NopRetryConstants.DEFAULT_RETRYING_TIMEOUT_MS / 2);

    /**
     * 1.6-C7-008（plan 2543 W-09）：回调链深度上限。回调记录 idempotentId 逐环追加 "_callback"，
     * 策略成环（A→A / A→B→A）时每环孵化一条新记录且永不撞 UK——深度护栏在孵化前拒孵，
     * 使「自维持无限副作用 + 无界记录增长」不可达。&lt;=0 = 显式不限制（已知无环的回调链）。
     */
    IConfigReference<Integer> CFG_RETRY_CALLBACK_MAX_DEPTH = varRef(s_loc, "nop.retry.callback.max-depth",
            Integer.class, 3);
}
