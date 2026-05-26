/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.assigners;

import java.util.Collection;
import java.util.Collections;

import jakarta.annotation.Nullable;

import io.nop.core.context.IServiceContext;
import io.nop.core.context.IServiceContext;

import io.nop.stream.core.windowing.triggers.ProcessingTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.exceptions.StreamException;

public class TumblingProcessingTimeWindows extends WindowAssigner<Object, TimeWindow> {
    private static final long serialVersionUID = 1L;

    private final long size;

    protected TumblingProcessingTimeWindows(long size) {
        if (size <= 0) {
            throw new StreamException("Window size must be positive.");
        }
        this.size = size;
    }

    @Override
    public Collection<TimeWindow> assignWindows(
            Object element, long timestamp, WindowAssignerContext assignerContext) {
        long now = assignerContext.getCurrentProcessingTime();
        long start = TimeWindow.getWindowStartWithOffset(now, 0, size);
        return Collections.singletonList(new TimeWindow(start, start + size));
    }

    @Override
    public Trigger<Object, TimeWindow> getDefaultTrigger(@Nullable IServiceContext serviceContext) {
        return ProcessingTimeTrigger.create();
    }

    @Override
    public boolean isEventTime() {
        return false;
    }

    public static TumblingProcessingTimeWindows of(long size) {
        return new TumblingProcessingTimeWindows(size);
    }

    @Override
    public String toString() {
        return "TumblingProcessingTimeWindows(" + size + ")";
    }
}
