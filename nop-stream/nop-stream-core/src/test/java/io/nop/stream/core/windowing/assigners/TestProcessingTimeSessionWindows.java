/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.assigners;

import io.nop.stream.core.windowing.triggers.ProcessingTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-1 (plan 369 Phase 2): {@link ProcessingTimeSessionWindows} — the processing-time
 * counterpart of {@link EventTimeSessionWindows}. Gap semantics must match the event-time
 * version (unanchored session windows of length {@code gap}, overlapping windows merged),
 * with the clock source switched to the processing time.
 */
public class TestProcessingTimeSessionWindows {

    private static final long FIXED_NOW = 10_000L;

    private static WindowAssigner.WindowAssignerContext nowContext(long now) {
        return new WindowAssigner.WindowAssignerContext() {
            @Override
            public long getCurrentProcessingTime() {
                return now;
            }
        };
    }

    @Test
    void assignWindowsUsesProcessingTimeClock() {
        ProcessingTimeSessionWindows assigner = ProcessingTimeSessionWindows.withGap(100L);

        Collection<TimeWindow> windows = assigner.assignWindows("e", 42L, nowContext(FIXED_NOW));

        // The EVENT timestamp is irrelevant for a PT assigner: the window is anchored
        // at the current processing time and extends the gap into the future.
        assertEquals(1, windows.size());
        TimeWindow window = windows.iterator().next();
        assertEquals(FIXED_NOW, window.getStart(), "session starts at current processing time");
        assertEquals(FIXED_NOW + 100L, window.getEnd(), "session extends by the gap");
    }

    @Test
    void mergeWindowsDelegatesToTimeWindowMerge() {
        ProcessingTimeSessionWindows assigner = ProcessingTimeSessionWindows.withGap(100L);

        List<TimeWindow> candidates = new ArrayList<>(Arrays.asList(
                new TimeWindow(0L, 150L),
                new TimeWindow(100L, 250L),
                new TimeWindow(10_000L, 10_100L)));

        List<TimeWindow> merged = new ArrayList<>();
        assigner.mergeWindows(candidates, (toBeMerged, mergeResult) -> merged.add(mergeResult));

        // The two overlapping sessions merge; the far-apart one stays untouched.
        assertEquals(1, merged.size());
        TimeWindow mergeResult = merged.get(0);
        assertEquals(0L, mergeResult.getStart());
        assertEquals(250L, mergeResult.getEnd());
    }

    @Test
    void isEventTimeIsFalseAndDefaultTriggerIsProcessingTimeTrigger() {
        ProcessingTimeSessionWindows assigner = ProcessingTimeSessionWindows.withGap(100L);

        assertFalse(assigner.isEventTime(),
                "PT session windows run on processing time");
        Trigger<Object, TimeWindow> trigger = assigner.getDefaultTrigger(null);
        assertInstanceOf(ProcessingTimeTrigger.class, trigger);
        assertTrue(trigger.canMerge(), "session triggers must participate in merging");
    }

    @Test
    void gapMustBePositive() {
        assertThrows(Exception.class, () -> ProcessingTimeSessionWindows.withGap(0L));
        assertThrows(Exception.class, () -> ProcessingTimeSessionWindows.withGap(-5L));
    }

    @Test
    void durationFactoryConvertsToMillis() {
        ProcessingTimeSessionWindows assigner =
                ProcessingTimeSessionWindows.withGap(java.time.Duration.ofSeconds(2));
        assertEquals(2000L, assigner.getSessionTimeout());
    }
}
