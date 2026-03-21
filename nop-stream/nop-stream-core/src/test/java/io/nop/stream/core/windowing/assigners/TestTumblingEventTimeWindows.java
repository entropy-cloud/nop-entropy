/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.assigners;

import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TumblingEventTimeWindows}.
 */
public class TestTumblingEventTimeWindows {

    private static final long WINDOW_SIZE = 1000;
    private TumblingEventTimeWindows assigner;
    private MockWindowAssignerContext context;

    @BeforeEach
    public void setUp() {
        assigner = TumblingEventTimeWindows.of(WINDOW_SIZE);
        context = new MockWindowAssignerContext();
    }

    @Test
    public void testWindowAssignment() {
        long timestamp = 1500;
        Collection<TimeWindow> windows = assigner.assignWindows(null, timestamp, context);

        assertEquals(1, windows.size());
        TimeWindow window = windows.iterator().next();
        assertEquals(1000, window.getStart());
        assertEquals(2000, window.getEnd());
    }

    @Test
    public void testWindowAssignmentAtBoundary() {
        long timestamp = 1000;
        Collection<TimeWindow> windows = assigner.assignWindows(null, timestamp, context);

        assertEquals(1, windows.size());
        TimeWindow window = windows.iterator().next();
        assertEquals(1000, window.getStart());
        assertEquals(2000, window.getEnd());
    }

    @Test
    public void testWindowAssignmentWithOffset() {
        TumblingEventTimeWindows offsetAssigner = TumblingEventTimeWindows.of(WINDOW_SIZE, 500);
        long timestamp = 1000;
        Collection<TimeWindow> windows = offsetAssigner.assignWindows(null, timestamp, context);

        assertEquals(1, windows.size());
        TimeWindow window = windows.iterator().next();
        assertEquals(500, window.getStart());
        assertEquals(1500, window.getEnd());
    }

    @Test
    public void testIsEventTime() {
        assertTrue(assigner.isEventTime());
    }

    @Test
    public void testInvalidWindowSize() {
        assertThrows(IllegalArgumentException.class, () -> TumblingEventTimeWindows.of(0));
        assertThrows(IllegalArgumentException.class, () -> TumblingEventTimeWindows.of(-1));
    }

    @Test
    public void testInvalidOffset() {
        assertThrows(IllegalArgumentException.class, () -> TumblingEventTimeWindows.of(WINDOW_SIZE, -1));
        assertThrows(IllegalArgumentException.class, () -> TumblingEventTimeWindows.of(WINDOW_SIZE, WINDOW_SIZE));
    }

    @Test
    public void testToString() {
        String str = assigner.toString();
        assertTrue(str.contains("TumblingEventTimeWindows"));
        assertTrue(str.contains("1000"));
    }

    @Test
    public void testMultipleElementsInSameWindow() {
        long[] timestamps = {1000, 1200, 1500, 1999};

        for (long timestamp : timestamps) {
            Collection<TimeWindow> windows = assigner.assignWindows(null, timestamp, context);
            TimeWindow window = windows.iterator().next();
            assertEquals(1000, window.getStart());
            assertEquals(2000, window.getEnd());
        }
    }

    @Test
    public void testElementsInDifferentWindows() {
        Collection<TimeWindow> windows1 = assigner.assignWindows(null, 500, context);
        Collection<TimeWindow> windows2 = assigner.assignWindows(null, 1500, context);

        TimeWindow window1 = windows1.iterator().next();
        TimeWindow window2 = windows2.iterator().next();

        assertNotEquals(window1, window2);
    }

    private static class MockWindowAssignerContext implements WindowAssigner.WindowAssignerContext {
        @Override
        public long getCurrentProcessingTime() {
            return System.currentTimeMillis();
        }
    }
}
