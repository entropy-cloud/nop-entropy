/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestWindowOperatorBasic {

    @Test
    public void testTumblingEventTimeWindowsCreation() {
        TumblingEventTimeWindows assigner = TumblingEventTimeWindows.of(1000L);
        assertNotNull(assigner);
        assertTrue(assigner.isEventTime());
    }

    @Test
    public void testEventTimeTriggerCreation() {
        EventTimeTrigger trigger = EventTimeTrigger.create();
        assertNotNull(trigger);
        assertTrue(trigger.canMerge());
    }

    @Test
    public void testTimeWindowProperties() {
        long start = 1000L;
        long end = 2000L;
        TimeWindow window = new TimeWindow(start, end);
        
        assertEquals(start, window.getStart());
        assertEquals(end, window.getEnd());
        assertEquals(end - 1, window.maxTimestamp());
        assertEquals(1000L, end - start);
    }

    @Test
    public void testTimeWindowIntersects() {
        TimeWindow window1 = new TimeWindow(0, 1000);
        TimeWindow window2 = new TimeWindow(500, 1500);
        TimeWindow window3 = new TimeWindow(1000, 2000);
        TimeWindow window4 = new TimeWindow(1001, 2000);
        
        assertTrue(window1.intersects(window2));
        assertTrue(window1.intersects(window3));
        assertFalse(window1.intersects(window4));
    }

    @Test
    public void testTimeWindowCover() {
        TimeWindow window1 = new TimeWindow(0, 1000);
        TimeWindow window2 = new TimeWindow(500, 1500);
        TimeWindow covered = window1.cover(window2);
        
        assertEquals(0L, covered.getStart());
        assertEquals(1500L, covered.getEnd());
    }
}
