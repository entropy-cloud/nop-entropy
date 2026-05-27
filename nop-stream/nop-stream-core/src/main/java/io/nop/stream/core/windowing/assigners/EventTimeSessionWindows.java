package io.nop.stream.core.windowing.assigners;

import java.util.Collection;
import java.util.Collections;

import jakarta.annotation.Nullable;

import io.nop.core.context.IServiceContext;

import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.*;
public class EventTimeSessionWindows extends MergingWindowAssigner<Object, TimeWindow> {
    private static final long serialVersionUID = 1L;

    private final long sessionTimeout;

    public EventTimeSessionWindows(long sessionTimeout) {
        if (sessionTimeout <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "sessionTimeout").param(ARG_DETAIL, "must be positive, got: " + sessionTimeout);
        }
        this.sessionTimeout = sessionTimeout;
    }

    @Override
    public Collection<TimeWindow> assignWindows(Object element, long timestamp, WindowAssignerContext assignerContext) {
        return Collections.singletonList(new TimeWindow(timestamp, timestamp + sessionTimeout));
    }

    @Override
    public Trigger<Object, TimeWindow> getDefaultTrigger(@Nullable IServiceContext serviceContext) {
        return EventTimeTrigger.create();
    }

    @Override
    public boolean isEventTime() {
        return true;
    }

    @Override
    public void mergeWindows(Collection<TimeWindow> windows, MergeCallback<TimeWindow> callback) {
        TimeWindow.mergeWindows(windows, callback);
    }

    public long getSessionTimeout() {
        return sessionTimeout;
    }

    public static EventTimeSessionWindows withGap(long sessionTimeout) {
        return new EventTimeSessionWindows(sessionTimeout);
    }

    public static EventTimeSessionWindows withGap(java.time.Duration sessionTimeout) {
        return new EventTimeSessionWindows(sessionTimeout.toMillis());
    }

    @Override
    public String toString() {
        return "EventTimeSessionWindows{sessionTimeout=" + sessionTimeout + '}';
    }
}
