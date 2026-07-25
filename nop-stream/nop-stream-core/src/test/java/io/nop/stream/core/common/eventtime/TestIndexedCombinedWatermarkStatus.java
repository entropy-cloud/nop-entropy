/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.eventtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the multi-input watermark valve math (G47). The valve is N-capable by design
 * ({@link IndexedCombinedWatermarkStatus#forInputsCount(int)}); these tests verify min-combine
 * and idleness at the unit level. Runtime wiring (e2e) is deferred to the two-input-operator
 * successor because nop-stream has no two-input operator consumer (Anti-Hollow exemption).
 *
 * <p>Valve semantics: each partial watermark starts at {@code Long.MIN_VALUE}; the combined
 * watermark is the min across all non-idle inputs, so it only advances past
 * {@code Long.MIN_VALUE} once every non-idle input has received a real watermark.
 */
public class TestIndexedCombinedWatermarkStatus {

    @Test
    void testTwoInputMinCombine() {
        IndexedCombinedWatermarkStatus valve = IndexedCombinedWatermarkStatus.forInputsCount(2);

        // Input not yet set => its MIN_VALUE dominates the min, combined stays MIN_VALUE.
        assertFalse(valve.updateWatermark(0, 100L),
                "Combined cannot advance while one input is still at MIN_VALUE");

        // Now both inputs have real watermarks => combined = min(100, 200) = 100.
        assertTrue(valve.updateWatermark(1, 200L),
                "Once all inputs have watermarks, combined advances to the min");
        assertEquals(100L, valve.getCombinedWatermark());

        // Advancing the non-min input does not move combined.
        assertFalse(valve.updateWatermark(1, 250L));
        assertEquals(100L, valve.getCombinedWatermark());

        // Advancing the min input advances combined.
        assertTrue(valve.updateWatermark(0, 150L));
        assertEquals(150L, valve.getCombinedWatermark());
    }

    @Test
    void testNInputMinCombineForInputsCountGreaterThanTwo() {
        // The valve is N-capable (not hardcoded to 2).
        IndexedCombinedWatermarkStatus valve = IndexedCombinedWatermarkStatus.forInputsCount(4);

        valve.updateWatermark(0, 100L);
        valve.updateWatermark(1, 50L);
        valve.updateWatermark(2, 300L);
        // Still one input at MIN_VALUE => combined cannot advance yet.
        assertEquals(Long.MIN_VALUE, valve.getCombinedWatermark());

        assertTrue(valve.updateWatermark(3, 200L),
                "Last input set => combined advances to min(100,50,300,200)=50");
        assertEquals(50L, valve.getCombinedWatermark(),
                "Combined watermark should be the minimum across all 4 inputs");

        // Advancing the minimum input (1) past input0=100 makes 100 the new min.
        assertTrue(valve.updateWatermark(1, 120L));
        assertEquals(100L, valve.getCombinedWatermark(),
                "New min is input0=100 after input1 advanced to 120");
    }

    @Test
    void testIdlenessExcludesInputFromMin() {
        IndexedCombinedWatermarkStatus valve = IndexedCombinedWatermarkStatus.forInputsCount(2);

        valve.updateWatermark(0, 100L);
        valve.updateWatermark(1, 50L);
        assertEquals(50L, valve.getCombinedWatermark(),
                "input1=50 is the min");
        assertFalse(valve.isIdle());

        // Mark input1 idle -> it is excluded from the min, combined should jump to input0=100.
        assertTrue(valve.updateStatus(1, true),
                "Excluding the idle min input should advance combined watermark");
        assertEquals(100L, valve.getCombinedWatermark());
        assertFalse(valve.isIdle(), "One active input means the valve is not idle");
    }

    @Test
    void testAllInputsIdleMakesValveIdle() {
        IndexedCombinedWatermarkStatus valve = IndexedCombinedWatermarkStatus.forInputsCount(3);

        valve.updateWatermark(0, 100L);
        valve.updateWatermark(1, 200L);
        valve.updateWatermark(2, 300L);
        assertFalse(valve.isIdle());

        valve.updateStatus(0, true);
        valve.updateStatus(1, true);
        assertFalse(valve.isIdle(), "Still one active input (index 2)");

        valve.updateStatus(2, true);
        assertTrue(valve.isIdle(), "All inputs idle => valve idle");
    }

    @Test
    void testResumeFromIdleReincludesInput() {
        IndexedCombinedWatermarkStatus valve = IndexedCombinedWatermarkStatus.forInputsCount(2);

        valve.updateWatermark(0, 100L);
        valve.updateWatermark(1, 50L);
        assertEquals(50L, valve.getCombinedWatermark());

        // input1 idle => combined = input0 = 100.
        valve.updateStatus(1, true);
        assertEquals(100L, valve.getCombinedWatermark());

        // input1 resumes active with watermark 50 (its last value); combined recomputes
        // min(100, 50) = 50, but 50 < current combined 100, so combined does NOT regress.
        valve.updateStatus(1, false);
        assertEquals(100L, valve.getCombinedWatermark(),
                "Combined never regresses even when a resumed lower-watermark input rejoins");

        // Advance input1 beyond input0 => combined does not move (input0=100 is still min).
        assertFalse(valve.updateWatermark(1, 150L));
        assertEquals(100L, valve.getCombinedWatermark(),
                "input0=100 still the min");

        assertTrue(valve.updateWatermark(0, 180L));
        assertEquals(150L, valve.getCombinedWatermark(),
                "Now input1=150 is min after input0 advanced to 180");
    }

    @Test
    void testWatermarkNeverGoesBackwards() {
        IndexedCombinedWatermarkStatus valve = IndexedCombinedWatermarkStatus.forInputsCount(2);

        valve.updateWatermark(0, 100L);
        valve.updateWatermark(1, 100L);
        assertEquals(100L, valve.getCombinedWatermark());

        // A lower watermark on an input must not regress the combined watermark.
        assertFalse(valve.updateWatermark(1, 10L),
                "Lower watermark must not regress combined");
        assertEquals(100L, valve.getCombinedWatermark());
    }
}
