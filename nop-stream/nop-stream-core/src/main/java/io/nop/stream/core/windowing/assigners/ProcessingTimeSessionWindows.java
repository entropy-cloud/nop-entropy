package io.nop.stream.core.windowing.assigners;

import java.util.Collection;
import java.util.Collections;

import jakarta.annotation.Nullable;

import io.nop.core.context.IServiceContext;

import io.nop.stream.core.windowing.triggers.ProcessingTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;

/**
 * A {@link MergingWindowAssigner} that windows elements into sessions based on the current
 * PROCESSING time. G-1 (plan 369 Phase 2): the processing-time counterpart of
 * {@link EventTimeSessionWindows} — same gap semantics (a session window starts at the
 * element's processing time and extends {@code sessionTimeout} into the future; overlapping
 * windows are merged by {@link TimeWindow#mergeWindows}), with the clock source switched from
 * the event timestamp to {@link WindowAssignerContext#getCurrentProcessingTime()}.
 *
 * <p>Windows are assigned the range {@code [currentProcessingTime, currentProcessingTime +
 * sessionTimeout)}. Because every window's end strictly exceeds its assignment time, the
 * {@code WindowOperator} merge-validity check
 * ({@code mergeResult.maxTimestamp() <= currentProcessingTime}) can never trip for a freshly
 * assigned window.
 *
 * <p>The default trigger is {@link ProcessingTimeTrigger}: each merged session fires once when
 * processing time passes the merged window's max timestamp.
 */
public class ProcessingTimeSessionWindows extends MergingWindowAssigner<Object, TimeWindow> {
    private static final long serialVersionUID = 1L;

    private final long sessionTimeout;

    public ProcessingTimeSessionWindows(long sessionTimeout) {
        if (sessionTimeout <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "sessionTimeout").param(ARG_DETAIL, "must be positive, got: " + sessionTimeout);
        }
        this.sessionTimeout = sessionTimeout;
    }

    @Override
    public Collection<TimeWindow> assignWindows(Object element, long timestamp, WindowAssignerContext assignerContext) {
        long now = assignerContext.getCurrentProcessingTime();
        long end = now + sessionTimeout;
        if (end < now) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "sessionTimeout")
                    .param(ARG_DETAIL, "Session window end overflows: currentProcessingTime=" + now + ", sessionTimeout=" + sessionTimeout);
        }
        return Collections.singletonList(new TimeWindow(now, end));
    }

    @Override
    public Trigger<Object, TimeWindow> getDefaultTrigger(@Nullable IServiceContext serviceContext) {
        return ProcessingTimeTrigger.create();
    }

    @Override
    public boolean isEventTime() {
        return false;
    }

    @Override
    public void mergeWindows(Collection<TimeWindow> windows, MergeCallback<TimeWindow> callback) {
        TimeWindow.mergeWindows(windows, callback);
    }

    public long getSessionTimeout() {
        return sessionTimeout;
    }

    public static ProcessingTimeSessionWindows withGap(long sessionTimeout) {
        return new ProcessingTimeSessionWindows(sessionTimeout);
    }

    public static ProcessingTimeSessionWindows withGap(java.time.Duration sessionTimeout) {
        return new ProcessingTimeSessionWindows(sessionTimeout.toMillis());
    }

    @Override
    public String toString() {
        return "ProcessingTimeSessionWindows{sessionTimeout=" + sessionTimeout + '}';
    }
}
