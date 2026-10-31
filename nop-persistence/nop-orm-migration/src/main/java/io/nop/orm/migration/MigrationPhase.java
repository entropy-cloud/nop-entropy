/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;

import java.util.Map;
import java.util.Set;

import static io.nop.orm.migration.MigrationErrors.ARG_FROM_PHASE;
import static io.nop.orm.migration.MigrationErrors.ARG_JOB_ID;
import static io.nop.orm.migration.MigrationErrors.ARG_TO_PHASE;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_PHASE_TRANSITION_INVALID;

/**
 * 搬迁作业五状态机（设计 §4.1）：PREPARE → FULL → INCREMENTAL → VERIFY → SWITCH → DONE，
 * 外加 ABORT 中止出路（m4：任何阶段均可中止，旧配置未动无损）。
 *
 * <p>状态迁移只能由编排器按前置门禁推进（{@link #checkTransition}）；增量未启用时 FULL 直达
 * VERIFY（白名单外数据库的停写全量形态，设计 §三.4）。DONE/ABORT 为终态，无任何出边。
 * 受控自环（plan 2518 裁定⑪）：INCREMENTAL→INCREMENTAL、VERIFY→VERIFY 两条白名单自环边，
 * 仅承载 fix-forward 瞬时故障重试（编排器侧带重试计数上界），非法迁移仍显式失败。</p>
 */
public enum MigrationPhase {
    PREPARE,
    FULL,
    INCREMENTAL,
    VERIFY,
    SWITCH,
    DONE,
    ABORT;

    private static final Map<MigrationPhase, Set<MigrationPhase>> ALLOWED = Map.of(
            PREPARE, Set.of(FULL, ABORT),
            FULL, Set.of(INCREMENTAL, VERIFY, ABORT),
            // 裁定⑪（C7-002）：失败相位白名单受控自环——INCREMENTAL/VERIFY 仅经既有编排 API
            // 显式重入 + 进程内重试计数上界（fix-forward 重试，非全开；SWITCH/DONE/ABORT 不增）
            INCREMENTAL, Set.of(VERIFY, ABORT, INCREMENTAL),
            VERIFY, Set.of(SWITCH, ABORT, VERIFY),
            SWITCH, Set.of(DONE),
            DONE, Set.of(),
            ABORT, Set.of());

    public boolean isTerminal() {
        return this == DONE || this == ABORT;
    }

    public boolean canTransitionTo(MigrationPhase to) {
        return ALLOWED.get(this).contains(to);
    }

    /**
     * 门禁前置的阶段迁移检查：非法迁移显式失败（设计 §4.1：绝不隐式回退阶段）。
     */
    public void checkTransition(String jobId, MigrationPhase to) {
        if (!canTransitionTo(to)) {
            throw new NopException(ERR_MIG_PHASE_TRANSITION_INVALID)
                    .param(ARG_JOB_ID, jobId)
                    .param(ARG_FROM_PHASE, name())
                    .param(ARG_TO_PHASE, to.name());
        }
    }
}
