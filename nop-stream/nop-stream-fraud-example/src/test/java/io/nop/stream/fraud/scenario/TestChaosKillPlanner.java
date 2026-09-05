/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 15 (Phase 2 device verification): the chaos kill planner's bounds,
 * partition-round allocation, seed reproducibility and fail-fast validation.
 */
class TestChaosKillPlanner {

    @Test
    void planProducesBoundedReproducibleSchedules() {
        List<ChaosKillPlanner.KillStep> steps = ChaosKillPlanner.plan(8, 20_000L, 60_000L, 7, 15L);
        assertEquals(8, steps.size());
        long total = 0L;
        for (ChaosKillPlanner.KillStep step : steps) {
            assertTrue(step.getDelayMsBefore() >= 20_000L && step.getDelayMsBefore() <= 60_000L,
                    "delay within bounds: " + step);
            assertTrue(step.getVictim().equals("tm-0") || step.getVictim().equals("tm-1"));
            assertEquals(step.getRound() >= 7
                            ? ChaosKillPlanner.StepKind.PARTITION_PAUSE_RESUME
                            : ChaosKillPlanner.StepKind.KILL_RESTART_TM,
                    step.getKind());
            assertEquals(step.getRound(), steps.indexOf(step) + 1);
            total += step.getDelayMsBefore();
        }
        assertEquals(total, ChaosKillPlanner.totalPlannedDelayMs(steps));
    }

    @Test
    void sameSeedReproducesTheSchedule() {
        List<ChaosKillPlanner.KillStep> a = ChaosKillPlanner.plan(6, 10_000L, 20_000L, 7, 42L);
        List<ChaosKillPlanner.KillStep> b = ChaosKillPlanner.plan(6, 10_000L, 20_000L, 7, 42L);
        assertEquals(join(a), join(b));
    }

    @Test
    void differentSeedChangesTheSchedule() {
        List<ChaosKillPlanner.KillStep> a = ChaosKillPlanner.plan(8, 10_000L, 30_000L, 9, 1L);
        List<ChaosKillPlanner.KillStep> b = ChaosKillPlanner.plan(8, 10_000L, 30_000L, 9, 2L);
        assertNotEquals(join(a), join(b));
    }

    private static String join(List<ChaosKillPlanner.KillStep> steps) {
        StringBuilder sb = new StringBuilder();
        for (ChaosKillPlanner.KillStep step : steps) {
            sb.append(step).append('\n');
        }
        return sb.toString();
    }

    @Test
    void noPartitionRoundsWhenPartitionFromRoundExceedsRounds() {
        List<ChaosKillPlanner.KillStep> steps = ChaosKillPlanner.plan(3, 1000L, 2000L, 4, 7L);
        for (ChaosKillPlanner.KillStep step : steps) {
            assertEquals(ChaosKillPlanner.StepKind.KILL_RESTART_TM, step.getKind());
        }
    }

    @Test
    void planValidatesParametersFailFast() {
        assertThrows(IllegalArgumentException.class,
                () -> ChaosKillPlanner.plan(0, 1000L, 2000L, 99, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> ChaosKillPlanner.plan(3, 0L, 2000L, 99, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> ChaosKillPlanner.plan(3, 3000L, 2000L, 99, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> ChaosKillPlanner.plan(3, 1000L, 2000L, 0, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> ChaosKillPlanner.plan(3, 1000L, 2000L, 5, 1L));
    }
}
