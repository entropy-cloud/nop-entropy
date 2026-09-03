/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.assigners;

import java.util.Collection;

import jakarta.annotation.Nullable;

import io.nop.core.context.IServiceContext;

import io.nop.stream.core.windowing.triggers.ProcessingTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;

/**
 * A {@link WindowAssigner} that windows elements into windows based on the
 * current processing time. Windows can overlap.
 *
 * <p>item 21 D-4 convergence: the constructor validation and the sliding
 * expansion loop live in {@link WindowAssignerSupport} (shared with the
 * event-time twin and the tumbling family).
 */
public class SlidingProcessingTimeWindows extends WindowAssigner<Object, TimeWindow> {
    private static final long serialVersionUID = 1L;

    private final long size;
    private final long slide;
    private final long offset;

    protected SlidingProcessingTimeWindows(long size, long slide, long offset) {
        WindowAssignerSupport.validateSliding(size, slide, offset);
        this.size = size;
        this.slide = slide;
        this.offset = offset;
    }

    @Override
    public Collection<TimeWindow> assignWindows(
            Object element, long timestamp, WindowAssignerContext assignerContext) {
        long now = assignerContext.getCurrentProcessingTime();
        return WindowAssignerSupport.slidingWindowsFor(now, size, slide, offset);
    }

    @Override
    public Trigger<Object, TimeWindow> getDefaultTrigger(@Nullable IServiceContext serviceContext) {
        return ProcessingTimeTrigger.create();
    }

    @Override
    public boolean isEventTime() {
        return false;
    }

    public static SlidingProcessingTimeWindows of(long size, long slide) {
        return new SlidingProcessingTimeWindows(size, slide, 0);
    }

    public static SlidingProcessingTimeWindows of(long size, long slide, long offset) {
        return new SlidingProcessingTimeWindows(size, slide, offset);
    }

    public long getSize() {
        return size;
    }

    public long getSlide() {
        return slide;
    }

    @Override
    public String toString() {
        return "SlidingProcessingTimeWindows(" + size + ", " + slide + ")";
    }
}
