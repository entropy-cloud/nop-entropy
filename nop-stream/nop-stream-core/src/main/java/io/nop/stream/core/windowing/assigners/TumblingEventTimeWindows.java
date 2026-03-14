/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.stream.core.windowing.assigners;

import io.nop.core.context.IServiceContext;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;

import java.util.Collection;
import java.util.Collections;

/**
 * A {@link WindowAssigner} that windows elements into windows based on the timestamp of the
 * elements. Windows cannot overlap.
 *
 * <p>For example, in order to window into windows of 1 minute:
 *
 * <pre>{@code
 * DataStream<T> input = ...;
 * KeyedStream<T, K> keyedStream = input.keyBy(...);
 * WindowedStream<T, K, TimeWindow> windowedStream = keyedStream.window(TumblingEventTimeWindows.of(Time.minutes(1)));
 * }</pre>
 */
public class TumblingEventTimeWindows extends WindowAssigner<Object, TimeWindow> {
    private static final long serialVersionUID = 1L;

    private final long size;

    private final long globalOffset;

    private final WindowStagger windowStagger;

    protected TumblingEventTimeWindows(long size, long offset, WindowStagger windowStagger) {
        if (size <= 0) {
            throw new IllegalArgumentException("Window size must be positive.");
        }
        if (offset >= size || offset < 0) {
            throw new IllegalArgumentException(
                    "Window offset must be in [0, size), but is " + offset);
        }
        this.size = size;
        this.globalOffset = offset;
        this.windowStagger = windowStagger;
    }

    @Override
    public Collection<TimeWindow> assignWindows(
            Object element, long timestamp, WindowAssignerContext context) {
        if (timestamp > Long.MIN_VALUE) {
            // Long.MIN_VALUE is currently assigned when no timestamp is present
            long start =
                    TimeWindow.getWindowStartWithOffset(
                            timestamp, (globalOffset + windowStagger.getStaggerOffset(context.getCurrentProcessingTime(), size)), size);
            return Collections.singletonList(new TimeWindow(start, start + size));
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
     * Creates a new {@code TumblingEventTimeWindows} {@link WindowAssigner} that assigns elements
     * to windows based on the element timestamp and window size.
     *
     * @param size The size of the generated windows.
     * @return The tumbling event-time windows policy.
     */
    public static TumblingEventTimeWindows of(long size) {
        return new TumblingEventTimeWindows(size, 0, WindowStagger.ALIGNED);
    }

    /**
     * Creates a new {@code TumblingEventTimeWindows} {@link WindowAssigner} that assigns elements
     * to windows based on the element timestamp and window size.
     *
     * @param size The size of the generated windows.
     * @param offset The offset which window start would be shifted by.
     * @return The tumbling event-time windows policy.
     */
    public static TumblingEventTimeWindows of(long size, long offset) {
        return new TumblingEventTimeWindows(size, offset, WindowStagger.ALIGNED);
    }

    /**
     * Creates a new {@code TumblingEventTimeWindows} {@link WindowAssigner} that assigns elements
     * to windows based on the element timestamp and window size.
     *
     * @param size The size of the generated windows.
     * @param offset The offset which window start would be shifted by.
     * @param windowStagger The stagger strategy to use.
     * @return The tumbling event-time windows policy.
     */
    public static TumblingEventTimeWindows of(long size, long offset, WindowStagger windowStagger) {
        return new TumblingEventTimeWindows(size, offset, windowStagger);
    }

    @Override
    public String toString() {
        return "TumblingEventTimeWindows(" + size + ")";
    }
}
