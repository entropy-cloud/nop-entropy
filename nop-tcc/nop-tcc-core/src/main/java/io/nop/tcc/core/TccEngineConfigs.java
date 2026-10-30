/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.core;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.time.Duration;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * plan 2515 裁定⑧（C7-005）：tcc 两表（nop_tcc_record / nop_tcc_branch_record）保留期清理参数。
 * 键命名与缺省值的唯一事实源 = ai-dev/design/concurrency/ledger-retention-policy.md §3.1/§3.2
 * （nop-tcc 行，R01 族级 SSOT 投影）；模块落地不得私改参数语义，参数修订只走该设计文档。
 */
public interface TccEngineConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(TccEngineConfigs.class);

    @Description("tcc账本表保留期（天）。已完结（含耗尽终态化KILLED）记录超期清理；0=显式禁用周期清理")
    IConfigReference<Integer> CFG_TCC_LEDGER_RETENTION_DAYS = varRef(s_loc, "nop.tcc.ledger.retention-days",
            Integer.class, 30);

    @Description("tcc账本清理触发周期。下限60s，低于下限启动期fail-fast")
    IConfigReference<Duration> CFG_TCC_LEDGER_CLEANUP_INTERVAL = varRef(s_loc, "nop.tcc.ledger.cleanup-interval",
            Duration.class, Duration.ofHours(1));

    @Description("tcc账本单轮单表删除上限（锚列beginTime拾取+按IN批删）。必须为正数")
    IConfigReference<Integer> CFG_TCC_LEDGER_DELETE_BATCH_SIZE = varRef(s_loc, "nop.tcc.ledger.delete-batch-size",
            Integer.class, 1000);

    @Description("tcc事务补偿重试次数上限（耗尽行终态化判定阈值，与nop.tcc.default-max-retry-times同键）")
    IConfigReference<Integer> CFG_TCC_DEFAULT_MAX_RETRY_TIMES = varRef(s_loc, "nop.tcc.default-max-retry-times",
            Integer.class, 100);

    /**
     * 清理触发周期下限（R01 SSOT §3.1 第2项包络）：低于该值启动期 fail-fast，防高频清理冲击业务库。
     */
    long TCC_LEDGER_MIN_CLEANUP_INTERVAL_MILLIS = 60_000L;
}
