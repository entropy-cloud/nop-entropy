/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.InternalTimerService;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;

/**
 * Shared scaffolding for the WindowOperator test family (behavior /
 * integration / accumulator-type / MapState-fallback write-back /
 * timer-checkpoint E2E): the trivial serializer implementations, the
 * identity window function and the testable operator subclass that exposes
 * watermark advancement. Scenarios and assertions live in the individual
 * test classes.
 */
public final class WindowingTestSupport {

    private WindowingTestSupport() {
    }

    /**
     * {@link WindowOperator} exposing the package-private timer service for
     * watermark advancement and timer assertions.
     */
    public static class TestableWindowOperator
            extends WindowOperator<String, Integer, Object, String, TimeWindow> {

        public TestableWindowOperator(
                TumblingEventTimeWindows windowAssigner,
                TypeSerializer<TimeWindow> windowSerializer,
                KeySelector<Integer, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction<Object, String, String, TimeWindow> windowFunction,
                EventTimeTrigger trigger,
                long allowedLateness,
                OutputTag<Integer> lateDataOutputTag) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag);
        }

        public void advanceInternalWatermark(long timestamp) throws Exception {
            if (internalTimerService instanceof HeapInternalTimerService) {
                ((HeapInternalTimerService<String, TimeWindow>) internalTimerService).advanceWatermark(timestamp);
            }
        }

        public InternalTimerService<TimeWindow> getInternalTimerService() {
            return internalTimerService;
        }
    }

    /** Identity window function emitting {@code String.valueOf(input)}. */
    public static class ToStringWindowFunction<K>
            implements InternalWindowFunction<Object, String, K, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(K key, TimeWindow window, InternalWindowContext context,
                            Object input, io.nop.stream.core.util.Collector<String> out) {
            out.collect(String.valueOf(input));
        }

        @Override
        public void clear(TimeWindow window, InternalWindowContext context) {
        }
    }

    public static class SimpleTimeWindowSerializer implements TypeSerializer<TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public TypeSerializer<TimeWindow> duplicate() {
            return this;
        }

        @Override
        public TimeWindow createInstance() {
            return new TimeWindow(0, 0);
        }

        @Override
        public TimeWindow copy(TimeWindow from) {
            return new TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public TimeWindow copy(TimeWindow from, TimeWindow reuse) {
            return new TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public int getLength() {
            return -1;
        }
    }

    public static class SimpleStringSerializer implements TypeSerializer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public TypeSerializer<String> duplicate() {
            return this;
        }

        @Override
        public String createInstance() {
            return "";
        }

        @Override
        public String copy(String from) {
            return from;
        }

        @Override
        public String copy(String from, String reuse) {
            return from;
        }

        @Override
        public int getLength() {
            return -1;
        }
    }

    public static class SimpleLongSerializer implements TypeSerializer<Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public TypeSerializer<Long> duplicate() {
            return this;
        }

        @Override
        public Long createInstance() {
            return 0L;
        }

        @Override
        public Long copy(Long from) {
            return from;
        }

        @Override
        public Long copy(Long from, Long reuse) {
            return from;
        }

        @Override
        public int getLength() {
            return -1;
        }
    }
}
