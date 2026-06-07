package io.nop.stream.core.windowing.assigners;

import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

public class TestWindowOverflow {

    private static final WindowAssigner.WindowAssignerContext CTX = new WindowAssigner.WindowAssignerContext() {
        @Override
        public long getCurrentProcessingTime() {
            return 1000;
        }
    };

    @Test
    void testSlidingEventTimeWindowsNoOverflow() {
        SlidingEventTimeWindows assigner = SlidingEventTimeWindows.of(100, 50);
        Collection<TimeWindow> windows = assigner.assignWindows(new Object(), 1000, CTX);
        for (TimeWindow w : windows) {
            assertTrue(w.getEnd() >= w.getStart(),
                    "Window end should not overflow: " + w);
        }
    }

    @Test
    void testSlidingEventTimeWindowsNearMaxTimestamp() {
        SlidingEventTimeWindows assigner = SlidingEventTimeWindows.of(100, 50);
        long nearMax = Long.MAX_VALUE - 10;
        Collection<TimeWindow> windows = assigner.assignWindows(new Object(), nearMax, CTX);
        assertFalse(windows.isEmpty());
        for (TimeWindow w : windows) {
            assertTrue(w.getEnd() >= w.getStart(),
                    "Window end should clamp to MAX_VALUE on overflow: " + w);
        }
    }

    @Test
    void testSlidingProcessingTimeWindowsNearMaxTimestamp() {
        WindowAssigner.WindowAssignerContext highCtx = new WindowAssigner.WindowAssignerContext() {
            @Override
            public long getCurrentProcessingTime() {
                return Long.MAX_VALUE - 10;
            }
        };
        SlidingProcessingTimeWindows assigner = SlidingProcessingTimeWindows.of(100, 50);
        Collection<TimeWindow> windows = assigner.assignWindows(new Object(), 0, highCtx);
        assertFalse(windows.isEmpty());
        for (TimeWindow w : windows) {
            assertTrue(w.getEnd() >= w.getStart(),
                    "Window end should clamp to MAX_VALUE on overflow: " + w);
        }
    }

    @Test
    void testTumblingEventTimeWindowsNearMaxTimestamp() {
        TumblingEventTimeWindows assigner = TumblingEventTimeWindows.of(100);
        Collection<TimeWindow> windows = assigner.assignWindows(new Object(), Long.MAX_VALUE - 10, CTX);
        assertEquals(1, windows.size());
        TimeWindow w = windows.iterator().next();
        assertTrue(w.getEnd() >= w.getStart(),
                "Window end should not be less than start: " + w);
    }

    @Test
    void testTumblingProcessingTimeWindowsNearMaxTimestamp() {
        WindowAssigner.WindowAssignerContext highCtx = new WindowAssigner.WindowAssignerContext() {
            @Override
            public long getCurrentProcessingTime() {
                return Long.MAX_VALUE - 10;
            }
        };
        TumblingProcessingTimeWindows assigner = TumblingProcessingTimeWindows.of(100);
        Collection<TimeWindow> windows = assigner.assignWindows(new Object(), 0, highCtx);
        assertEquals(1, windows.size());
        TimeWindow w = windows.iterator().next();
        assertTrue(w.getEnd() >= w.getStart(),
                "Window end should not be less than start: " + w);
    }

    @Test
    void testNormalTimestampsUnaffected() {
        SlidingEventTimeWindows sliding = SlidingEventTimeWindows.of(1000, 500);
        Collection<TimeWindow> windows = sliding.assignWindows(new Object(), 5000, CTX);
        assertFalse(windows.isEmpty());
        for (TimeWindow w : windows) {
            assertTrue(w.getEnd() < Long.MAX_VALUE,
                    "Normal timestamps should not be clamped: " + w);
        }

        TumblingEventTimeWindows tumbling = TumblingEventTimeWindows.of(1000);
        Collection<TimeWindow> tumblingWindows = tumbling.assignWindows(new Object(), 5000, CTX);
        assertEquals(1, tumblingWindows.size());
        TimeWindow w = tumblingWindows.iterator().next();
        assertTrue(w.getEnd() < Long.MAX_VALUE);
    }
}
