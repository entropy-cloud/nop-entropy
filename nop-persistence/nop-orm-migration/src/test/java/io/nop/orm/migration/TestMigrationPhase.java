/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static io.nop.orm.migration.MigrationErrors.ERR_MIG_PHASE_TRANSITION_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 五状态机迁移矩阵（设计 §4.1）：合法序列推进 + 每条非法迁移显式失败 + 终态无出边。
 */
public class TestMigrationPhase {

    @Test
    public void testHappyPathRescale() {
        assertTrue(MigrationPhase.PREPARE.canTransitionTo(MigrationPhase.FULL));
        assertTrue(MigrationPhase.FULL.canTransitionTo(MigrationPhase.INCREMENTAL));
        assertTrue(MigrationPhase.INCREMENTAL.canTransitionTo(MigrationPhase.VERIFY));
        assertTrue(MigrationPhase.VERIFY.canTransitionTo(MigrationPhase.SWITCH));
        assertTrue(MigrationPhase.SWITCH.canTransitionTo(MigrationPhase.DONE));
    }

    @Test
    public void testFullCanSkipIncrementalForNonCdcSources() {
        // 白名单外数据库的停写全量形态：FULL -> VERIFY 三段（设计 §三.4）
        assertTrue(MigrationPhase.FULL.canTransitionTo(MigrationPhase.VERIFY));
    }

    @Test
    public void testAbortReachableFromEveryActivePhase() {
        assertTrue(MigrationPhase.PREPARE.canTransitionTo(MigrationPhase.ABORT));
        assertTrue(MigrationPhase.FULL.canTransitionTo(MigrationPhase.ABORT));
        assertTrue(MigrationPhase.INCREMENTAL.canTransitionTo(MigrationPhase.ABORT));
        assertTrue(MigrationPhase.VERIFY.canTransitionTo(MigrationPhase.ABORT));
        // SWITCH 无中止出路：配置已翻转，唯一出路是收敛后 DONE（回翻走运维面）
        assertFalse(MigrationPhase.SWITCH.canTransitionTo(MigrationPhase.ABORT));
    }

    @Test
    public void testIllegalTransitionsFail() {
        assertThrows(NopException.class,
                () -> MigrationPhase.PREPARE.checkTransition("j1", MigrationPhase.VERIFY));
        assertThrows(NopException.class,
                () -> MigrationPhase.PREPARE.checkTransition("j1", MigrationPhase.DONE));
        assertThrows(NopException.class,
                () -> MigrationPhase.FULL.checkTransition("j1", MigrationPhase.SWITCH));
        assertThrows(NopException.class,
                () -> MigrationPhase.VERIFY.checkTransition("j1", MigrationPhase.FULL));
        assertThrows(NopException.class,
                () -> MigrationPhase.DONE.checkTransition("j1", MigrationPhase.FULL));
        assertThrows(NopException.class,
                () -> MigrationPhase.ABORT.checkTransition("j1", MigrationPhase.FULL));
    }

    @Test
    public void testIllegalTransitionCarriesPhases() {
        NopException e = assertThrows(NopException.class,
                () -> MigrationPhase.VERIFY.checkTransition("job-x", MigrationPhase.FULL));
        assertEquals(ERR_MIG_PHASE_TRANSITION_INVALID.getErrorCode(), e.getErrorCode());
        assertEquals("job-x", e.getParams().get(MigrationErrors.ARG_JOB_ID));
        assertEquals("VERIFY", e.getParams().get(MigrationErrors.ARG_FROM_PHASE));
        assertEquals("FULL", e.getParams().get(MigrationErrors.ARG_TO_PHASE));
    }

    @Test
    public void testTerminalStates() {
        assertTrue(MigrationPhase.DONE.isTerminal());
        assertTrue(MigrationPhase.ABORT.isTerminal());
        assertFalse(MigrationPhase.SWITCH.isTerminal());
    }
}
