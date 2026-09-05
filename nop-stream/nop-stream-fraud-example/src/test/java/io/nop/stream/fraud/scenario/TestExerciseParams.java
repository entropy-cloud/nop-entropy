/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Item 15 (no-silent-skip rule): the joint entry's exercise parameters fail fast
 * on illegal values (non-numeric, <= 0, blank) instead of silently using defaults.
 */
class TestExerciseParams {

    @BeforeEach
    void setUp() {
        System.clearProperty("exercise.test.long");
        System.clearProperty("exercise.test.levels");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("exercise.test.long");
        System.clearProperty("exercise.test.levels");
    }

    @Test
    void longParamFallsBackToDefaultOnlyWhenUnset() {
        assertEquals(42L, StabilityExerciseSupport.longParam("exercise.test.long", 42L));
        System.setProperty("exercise.test.long", " 7 ");
        assertEquals(7L, StabilityExerciseSupport.longParam("exercise.test.long", 42L));
        System.setProperty("exercise.test.long", "0");
        assertThrows(IllegalArgumentException.class,
                () -> StabilityExerciseSupport.longParam("exercise.test.long", 42L));
        System.setProperty("exercise.test.long", "-5");
        assertThrows(IllegalArgumentException.class,
                () -> StabilityExerciseSupport.longParam("exercise.test.long", 42L));
        System.setProperty("exercise.test.long", "abc");
        assertThrows(IllegalArgumentException.class,
                () -> StabilityExerciseSupport.longParam("exercise.test.long", 42L));
    }

    @Test
    void levelsParamParsesAndValidates() {
        assertEquals(List.of(50L, 200L, 500L),
                StabilityExerciseSupport.msLevelsParam("exercise.test.levels", "50,200,500"));
        assertEquals(List.of(0L), StabilityExerciseSupport.msLevelsParam("exercise.test.levels", "0"));
        assertThrows(IllegalArgumentException.class,
                () -> StabilityExerciseSupport.msLevelsParam("exercise.test.levels", "50,-1,500"));
        assertThrows(IllegalArgumentException.class,
                () -> StabilityExerciseSupport.msLevelsParam("exercise.test.levels", "50,x"));
        assertThrows(IllegalArgumentException.class,
                () -> StabilityExerciseSupport.msLevelsParam("exercise.test.levels", null));
    }

    @Test
    void intParamRejectsOverflow() {
        System.setProperty("exercise.test.long", String.valueOf(1L + Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> StabilityExerciseSupport.intParam("exercise.test.long", 1));
    }
}
