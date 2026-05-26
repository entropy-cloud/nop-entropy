/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.assigners;

import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import io.nop.stream.core.exceptions.StreamException;

public class TestSlidingEventTimeWindows {

    private static final long SIZE = 10;
    private static final long SLIDE = 5;

    private SlidingEventTimeWindows assigner;
    private MockWindowAssignerContext context;

    @BeforeEach
    public void setUp() {
        assigner = SlidingEventTimeWindows.of(SIZE, SLIDE);
        context = new MockWindowAssignerContext();
    }

    @Test
    public void testWindowAssignment() {
        long timestamp = 100;
        Collection<TimeWindow> windows = assigner.assignWindows(null, timestamp, context);

        // lastStart = getWindowStartWithOffset(100, 0, 5) = 100
        // loop: start=100 > 100-10=90 => [100,110); start=95 > 90 => [95,105); start=90 not > 90 => stop
        assertEquals(2, windows.size());
        assertTrue(windows.contains(new TimeWindow(100, 110)));
        assertTrue(windows.contains(new TimeWindow(95, 105)));
    }

    @Test
    public void testWindowBoundaryAssignment() {
        long timestamp = 95;
        Collection<TimeWindow> windows = assigner.assignWindows(null, timestamp, context);

        // lastStart = getWindowStartWithOffset(95, 0, 5) = 95
        // loop: start=95 > 95-10=85 => [95,105); start=90 > 85 => [90,100); start=85 not > 85 => stop
        assertEquals(2, windows.size());
        assertTrue(windows.contains(new TimeWindow(95, 105)));
        assertTrue(windows.contains(new TimeWindow(90, 100)));
    }

    @Test
    public void testSingleWindowWhenSlideEqualsSize() {
        SlidingEventTimeWindows tumblingLike = SlidingEventTimeWindows.of(10, 10);
        long timestamp = 15;
        Collection<TimeWindow> windows = tumblingLike.assignWindows(null, timestamp, context);

        // lastStart = getWindowStartWithOffset(15, 0, 10) = 10
        // loop: start=10 > 15-10=5 => [10,20); start=0 not > 5 => stop
        assertEquals(1, windows.size());
        assertTrue(windows.contains(new TimeWindow(10, 20)));
    }

    @Test
    public void testInvalidArguments() {
        assertThrows(StreamException.class, () -> SlidingEventTimeWindows.of(0, 5));
        assertThrows(StreamException.class, () -> SlidingEventTimeWindows.of(-1, 5));
        assertThrows(StreamException.class, () -> SlidingEventTimeWindows.of(10, 0));
        assertThrows(StreamException.class, () -> SlidingEventTimeWindows.of(10, -1));
    }

    @Test
    public void testInvalidOffset() {
        assertThrows(StreamException.class, () -> SlidingEventTimeWindows.of(10, 5, -1));
        assertThrows(StreamException.class, () -> SlidingEventTimeWindows.of(10, 5, 5));
        assertThrows(StreamException.class, () -> SlidingEventTimeWindows.of(10, 5, 10));
    }

    @Test
    public void testIsEventTime() {
        assertTrue(assigner.isEventTime());
    }

    @Test
    public void testDefaultTrigger() {
        assertTrue(assigner.getDefaultTrigger(null) instanceof EventTimeTrigger);
    }

    @Test
    public void testWithOffset() {
        SlidingEventTimeWindows offsetAssigner = SlidingEventTimeWindows.of(10, 5, 2);
        long timestamp = 100;
        Collection<TimeWindow> windows = offsetAssigner.assignWindows(null, timestamp, context);

        // lastStart = getWindowStartWithOffset(100, 2, 5) = (100-2)%5=3, 100-3=97
        // loop: start=97 > 100-10=90 => [97,107); start=92 > 90 => [92,102); start=87 not > 90 => stop
        assertEquals(2, windows.size());
        assertTrue(windows.contains(new TimeWindow(97, 107)));
        assertTrue(windows.contains(new TimeWindow(92, 102)));
    }

    @Test
    public void testTimestampAtMinValueThrows() {
        assertThrows(RuntimeException.class, () -> assigner.assignWindows(null, Long.MIN_VALUE, context));
    }

    @Test
    public void testGetters() {
        assertEquals(SIZE, assigner.getSize());
        assertEquals(SLIDE, assigner.getSlide());
    }

    @Test
    public void testToString() {
        String str = assigner.toString();
        assertTrue(str.contains("SlidingEventTimeWindows"));
        assertTrue(str.contains("10"));
        assertTrue(str.contains("5"));
    }

    private static class MockWindowAssignerContext implements WindowAssigner.WindowAssignerContext {
        @Override
        public long getCurrentProcessingTime() {
            return System.currentTimeMillis();
        }
    }
}
