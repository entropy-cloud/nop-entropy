/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.dao;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static io.nop.api.core.config.AppConfig.varRef;

public interface NopSysDaoConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(NopSysDaoConfigs.class);
    @Description("是否自动初始化缺省Sequence")
    IConfigReference<Boolean> CFG_SYS_INIT_DEFAULT_SEQUENCE =
            varRef(s_loc,"nop.sys.init-default-sequence", Boolean.class, true);

    @Description("编码规则缓存的容量上限")
    IConfigReference<Integer> CFG_SYS_CODE_RULE_CACHE_MAX_SIZE =
            varRef(s_loc, "nop.sys.code-rule.cache-max-size", Integer.class, 1000);

    @Description("编码规则缓存的超时时间。BizModel失效钩子只覆盖本节点的管理端修改，"
            + "多节点部署下其他节点依赖此超时最终一致")
    IConfigReference<Duration> CFG_SYS_CODE_RULE_CACHE_TTL =
            varRef(s_loc, "nop.sys.code-rule.cache-timeout", Duration.class,
                    Duration.of(60, ChronoUnit.SECONDS));

    // ===== 账本保留策略（5.2-MA-04，plan 2502；键名/缺省值 SSOT =
    // ai-dev/design/concurrency/ledger-retention-policy.md §3.2 nop-sys 行，双 face 段） =====

    @Description("sys_lock 过期僵尸锁行（崩溃持有者+无人争用）保留期（天），超期行由周期清理批量清扫；"
            + "0=显式禁用 lock 面清理；负值启动期 fail-fast")
    IConfigReference<Integer> CFG_SYS_LEDGER_LOCK_RETENTION_DAYS =
            varRef(s_loc, "nop.sys.ledger.lock.retention-days", Integer.class, 7);

    @Description("sys_change_log 审计留痕保留期（天，全平台缺省最长——审计宁长勿短）；"
            + "0=永久保留的显式 opt-in（合规场景，禁止无声明/无清理/无上限的三无状态延续）；负值启动期 fail-fast")
    IConfigReference<Integer> CFG_SYS_LEDGER_CHANGELOG_RETENTION_DAYS =
            varRef(s_loc, "nop.sys.ledger.changelog.retention-days", Integer.class, 90);

    @Description("账本清理触发周期（lock/changelog 双 face 共用）。下限 60s，低于下限启动期 fail-fast")
    IConfigReference<Duration> CFG_SYS_LEDGER_CLEANUP_INTERVAL =
            varRef(s_loc, "nop.sys.ledger.cleanup-interval", Duration.class, Duration.ofHours(1));

    @Description("账本单轮单表删除上限（先按锚列谓词+limit 有界拾取主键集，再按 IN 集批删）。必须为正数")
    IConfigReference<Integer> CFG_SYS_LEDGER_DELETE_BATCH_SIZE =
            varRef(s_loc, "nop.sys.ledger.delete-batch-size", Integer.class, 1000);

    /**
     * 账本清理触发周期下限（保留策略设计 §3.1 第 2 项）：低于该值启动期 fail-fast，防高频清理冲击业务库。
     */
    long SYS_LEDGER_MIN_CLEANUP_INTERVAL_MILLIS = 60_000L;
}
