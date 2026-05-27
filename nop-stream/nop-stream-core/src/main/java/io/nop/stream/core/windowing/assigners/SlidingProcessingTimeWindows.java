/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.assigners;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.annotation.Nullable;

import io.nop.core.context.IServiceContext;

import io.nop.stream.core.windowing.triggers.ProcessingTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.*;
public class SlidingProcessingTimeWindows extends WindowAssigner<Object, TimeWindow> {
    private static final long serialVersionUID = 1L;

    private final long size;
    private final long slide;
    private final long offset;

    protected SlidingProcessingTimeWindows(long size, long slide, long offset) {
        if (size <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "size").param(ARG_DETAIL, "must be positive");
        }
        if (slide <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "slide").param(ARG_DETAIL, "must be positive");
        }
        if (offset < 0 || offset >= slide) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "offset").param(ARG_DETAIL, "must be in [0, slide)");
        }
        this.size = size;
        this.slide = slide;
        this.offset = offset;
    }

    @Override
    public Collection<TimeWindow> assignWindows(
            Object element, long timestamp, WindowAssignerContext assignerContext) {
        long now = assignerContext.getCurrentProcessingTime();
        List<TimeWindow> windows = new ArrayList<>();
        long lastStart = TimeWindow.getWindowStartWithOffset(now, offset, slide);
        for (long start = lastStart; start > now - size; start -= slide) {
            windows.add(new TimeWindow(start, start + size));
        }
        return windows;
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
