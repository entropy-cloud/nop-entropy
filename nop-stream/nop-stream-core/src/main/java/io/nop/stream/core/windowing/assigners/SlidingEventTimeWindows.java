/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.assigners;

import java.util.Collection;

import io.nop.core.context.IServiceContext;

import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;


/**
 * A {@link WindowAssigner} that windows elements into windows based on the timestamp of the
 * elements. Windows can overlap.
 *
 * <p>item 21 D-4 convergence: the constructor validation and the sliding
 * expansion loop live in {@link WindowAssignerSupport} (shared with the
 * processing-time twin and the tumbling family).
 */
public class SlidingEventTimeWindows extends WindowAssigner<Object, TimeWindow> {
    private static final long serialVersionUID = 1L;

    private final long size;
    private final long slide;
    private final long offset;

    protected SlidingEventTimeWindows(long size, long slide, long offset) {
        WindowAssignerSupport.validateSliding(size, slide, offset);
        this.size = size;
        this.slide = slide;
        this.offset = offset;
    }

    @Override
    public Collection<TimeWindow> assignWindows(
            Object element, long timestamp, WindowAssignerContext context) {
        if (timestamp > Long.MIN_VALUE) {
            return WindowAssignerSupport.slidingWindowsFor(timestamp, size, slide, offset);
        } else {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "Record has Long.MIN_VALUE timestamp (= no timestamp marker). "
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
