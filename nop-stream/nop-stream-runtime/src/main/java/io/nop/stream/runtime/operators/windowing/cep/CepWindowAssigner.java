/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing.cep;

import io.nop.core.context.IServiceContext;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;

import java.util.Collection;
import java.util.Collections;

/**
 * A {@link WindowAssigner} that assigns elements to windows based on CEP pattern time constraints.
 * 
 * <p>This assigner works with CEP patterns that have a {@code within(Duration)} constraint.
 * It creates tumbling windows that match the pattern's time window.</p>
 *
 * <p>For example, if a CEP pattern has a 30-second time window, this assigner will create
 * 30-second tumbling windows for pattern matching.</p>
 *
 * @see io.nop.stream.cep.pattern.Pattern#within(java.time.Duration)
 */
public class CepWindowAssigner extends WindowAssigner<Object, TimeWindow> {

    private static final long serialVersionUID = 1L;

    private final long windowSizeMs;

    private CepWindowAssigner(long windowSizeMs) {
        this.windowSizeMs = windowSizeMs;
    }

    /**
     * Creates a new {@link CepWindowAssigner} that assigns elements to tumbling windows
     * based on the given window size.
     *
     * @param windowSizeMs The size of the window in milliseconds
     * @return The CEP window assigner
     */
    public static CepWindowAssigner of(long windowSizeMs) {
        return new CepWindowAssigner(windowSizeMs);
    }

    @Override
    public Collection<TimeWindow> assignWindows(
            Object element, long timestamp, WindowAssignerContext context) {
        if (timestamp > Long.MIN_VALUE) {
            long start = TimeWindow.getWindowStartWithOffset(timestamp, 0, windowSizeMs);
            return Collections.singletonList(new TimeWindow(start, start + windowSizeMs));
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

    /**
     * Gets the window size in milliseconds.
     *
     * @return The window size in milliseconds
     */
    public long getWindowSizeMs() {
        return windowSizeMs;
    }

    @Override
    public String toString() {
        return "CepWindowAssigner(" + windowSizeMs + "ms)";
    }
}
