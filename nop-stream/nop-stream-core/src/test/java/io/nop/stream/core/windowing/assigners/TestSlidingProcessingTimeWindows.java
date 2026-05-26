/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.assigners;

import io.nop.stream.core.windowing.triggers.ProcessingTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import io.nop.stream.core.exceptions.StreamException;

public class TestSlidingProcessingTimeWindows {

    private static final long SIZE = 10;
    private static final long SLIDE = 5;

    private SlidingProcessingTimeWindows assigner;
    private MockContext context;

    @BeforeEach
    public void setUp() {
        assigner = SlidingProcessingTimeWindows.of(SIZE, SLIDE);
        context = new MockContext(100);
    }

    @Test
    public void testWindowAssignment() {
        Collection<TimeWindow> windows = assigner.assignWindows(null, Long.MIN_VALUE, context);

        assertEquals(2, windows.size());
        assertTrue(windows.contains(new TimeWindow(100, 110)));
        assertTrue(windows.contains(new TimeWindow(95, 105)));
    }

    @Test
    public void testWindowBoundary() {
        MockContext boundaryContext = new MockContext(95);
        Collection<TimeWindow> windows = assigner.assignWindows(null, Long.MIN_VALUE, boundaryContext);

        assertEquals(2, windows.size());
        assertTrue(windows.contains(new TimeWindow(95, 105)));
        assertTrue(windows.contains(new TimeWindow(90, 100)));
    }

    @Test
    public void testInvalidArguments() {
        assertThrows(StreamException.class, () -> SlidingProcessingTimeWindows.of(0, 5));
        assertThrows(StreamException.class, () -> SlidingProcessingTimeWindows.of(-1, 5));
        assertThrows(StreamException.class, () -> SlidingProcessingTimeWindows.of(10, 0));
        assertThrows(StreamException.class, () -> SlidingProcessingTimeWindows.of(10, -1));
    }

    @Test
    public void testInvalidOffset() {
        assertThrows(StreamException.class, () -> SlidingProcessingTimeWindows.of(10, 5, -1));
        assertThrows(StreamException.class, () -> SlidingProcessingTimeWindows.of(10, 5, 5));
    }

    @Test
    public void testIsEventTimeFalse() {
        assertFalse(assigner.isEventTime());
    }

    @Test
    public void testMultipleOverlappingWindows() {
        SlidingProcessingTimeWindows overlapping = SlidingProcessingTimeWindows.of(20, 5);
        MockContext ctx = new MockContext(100);
        Collection<TimeWindow> windows = overlapping.assignWindows(null, Long.MIN_VALUE, ctx);

        assertTrue(windows.size() > 1);
        for (TimeWindow window : windows) {
            assertTrue(window.getStart() <= 100);
            assertTrue(window.getEnd() > 100);
        }
    }

    @Test
    public void testSingleWindowWhenSlideEqualsSize() {
        SlidingProcessingTimeWindows tumblingLike = SlidingProcessingTimeWindows.of(10, 10);
        MockContext ctx = new MockContext(15);
        Collection<TimeWindow> windows = tumblingLike.assignWindows(null, Long.MIN_VALUE, ctx);

        assertEquals(1, windows.size());
        assertTrue(windows.contains(new TimeWindow(10, 20)));
    }

    @Test
    public void testWithOffset() {
        SlidingProcessingTimeWindows offsetAssigner = SlidingProcessingTimeWindows.of(10, 5, 2);
        MockContext ctx = new MockContext(100);
        Collection<TimeWindow> windows = offsetAssigner.assignWindows(null, Long.MIN_VALUE, ctx);

        assertEquals(2, windows.size());
        assertTrue(windows.contains(new TimeWindow(97, 107)));
        assertTrue(windows.contains(new TimeWindow(92, 102)));
    }

    @Test
    public void testDefaultTrigger() {
        assertTrue(assigner.getDefaultTrigger(null) instanceof ProcessingTimeTrigger);
    }

    @Test
    public void testGetters() {
        assertEquals(SIZE, assigner.getSize());
        assertEquals(SLIDE, assigner.getSlide());
    }

    @Test
    public void testToString() {
        String str = assigner.toString();
        assertTrue(str.contains("SlidingProcessingTimeWindows"));
        assertTrue(str.contains("10"));
        assertTrue(str.contains("5"));
    }

    static class MockContext implements WindowAssigner.WindowAssignerContext {
        private final long processingTime;

        MockContext(long processingTime) {
            this.processingTime = processingTime;
        }

        @Override
        public long getCurrentProcessingTime() {
            return processingTime;
        }
    }
}
