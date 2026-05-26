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

public class TestTumblingProcessingTimeWindows {

    private static final long WINDOW_SIZE = 1000;

    private TumblingProcessingTimeWindows assigner;
    private MockContext context;

    @BeforeEach
    public void setUp() {
        assigner = TumblingProcessingTimeWindows.of(WINDOW_SIZE);
        context = new MockContext(1500);
    }

    @Test
    public void testWindowAssignment() {
        Collection<TimeWindow> windows = assigner.assignWindows(null, Long.MIN_VALUE, context);

        assertEquals(1, windows.size());
        TimeWindow window = windows.iterator().next();
        assertEquals(1000, window.getStart());
        assertEquals(2000, window.getEnd());
    }

    @Test
    public void testWindowBoundary() {
        MockContext boundaryContext = new MockContext(1000);
        Collection<TimeWindow> windows = assigner.assignWindows(null, Long.MIN_VALUE, boundaryContext);

        assertEquals(1, windows.size());
        TimeWindow window = windows.iterator().next();
        assertEquals(1000, window.getStart());
        assertEquals(2000, window.getEnd());
    }

    @Test
    public void testInvalidArguments() {
        assertThrows(StreamException.class, () -> TumblingProcessingTimeWindows.of(0));
        assertThrows(StreamException.class, () -> TumblingProcessingTimeWindows.of(-1));
    }

    @Test
    public void testIsEventTimeFalse() {
        assertFalse(assigner.isEventTime());
    }

    @Test
    public void testDefaultTrigger() {
        assertTrue(assigner.getDefaultTrigger(null) instanceof ProcessingTimeTrigger);
    }

    @Test
    public void testUsableWithKeyedStream() {
        TumblingProcessingTimeWindows windows = TumblingProcessingTimeWindows.of(1000);
        assertTrue(windows instanceof WindowAssigner);
    }

    @Test
    public void testTimestampIgnored() {
        MockContext ctx = new MockContext(2500);
        Collection<TimeWindow> windows1 = assigner.assignWindows(null, 100, ctx);
        Collection<TimeWindow> windows2 = assigner.assignWindows(null, 9999, ctx);
        assertEquals(windows1, windows2);
    }

    @Test
    public void testToString() {
        String str = assigner.toString();
        assertTrue(str.contains("TumblingProcessingTimeWindows"));
        assertTrue(str.contains("1000"));
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
