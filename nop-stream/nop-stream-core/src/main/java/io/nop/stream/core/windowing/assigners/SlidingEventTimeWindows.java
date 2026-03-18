/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.assigners;

import io.nop.core.context.IServiceContext;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SlidingEventTimeWindows extends WindowAssigner<Object, TimeWindow> {
    private static final long serialVersionUID = 1L;

    private final long size;
    private final long slide;
    private final long offset;

    protected SlidingEventTimeWindows(long size, long slide, long offset) {
        if (size <= 0) {
            throw new IllegalArgumentException("Window size must be positive.");
        }
        if (slide <= 0) {
            throw new IllegalArgumentException("Window slide must be positive.");
        }
        if (offset < 0 || offset >= slide) {
            throw new IllegalArgumentException("Window offset must be in [0, slide)");
        }
        this.size = size;
        this.slide = slide;
        this.offset = offset;
    }

    @Override
    public Collection<TimeWindow> assignWindows(
            Object element, long timestamp, WindowAssignerContext context) {
        if (timestamp > Long.MIN_VALUE) {
            List<TimeWindow> windows = new ArrayList<>();
            long lastStart = TimeWindow.getWindowStartWithOffset(timestamp, offset, slide);
            for (long start = lastStart;
                 start > timestamp - size;
                 start -= slide) {
                windows.add(new TimeWindow(start, start + size));
            }
            return windows;
        } else {
            throw new RuntimeException(
                    "Record has Long.MIN_VALUE timestamp (= no timestamp marker). "
                            + "Is the time characteristic set to 'ProcessingTime', or "
                            + "did you forget to call 'DataStream.assignTimestampsAndWatermarks(...)'?");
        }
    }

    @Override
    public Trigger<Object, TimeWindow> getDefaultTrigger(IServiceContext env) {
        return EventTimeTrigger.create();
    }

    @Override
    public boolean isEventTime() {
        return true;
    }

    public static SlidingEventTimeWindows of(long size, long slide) {
        return new SlidingEventTimeWindows(size, slide, 0);
    }

    public static SlidingEventTimeWindows of(long size, long slide, long offset) {
        return new SlidingEventTimeWindows(size, slide, offset);
    }

    public long getSize() {
        return size;
    }

    public long getSlide() {
        return slide;
    }

    @Override
    public String toString() {
        return "SlidingEventTimeWindows(" + size + ", " + slide + ")";
    }
}
