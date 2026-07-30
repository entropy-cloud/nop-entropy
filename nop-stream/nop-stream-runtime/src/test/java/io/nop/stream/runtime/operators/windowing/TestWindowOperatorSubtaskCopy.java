/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestWindowOperatorSubtaskCopy {

    static class SimpleTimeWindowSerializer implements TypeSerializer<TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() { return true; }

        @Override
        public TypeSerializer<TimeWindow> duplicate() { return this; }

        @Override
        public TimeWindow createInstance() { return new TimeWindow(0, 0); }

        @Override
        public TimeWindow copy(TimeWindow from) { return new TimeWindow(from.getStart(), from.getEnd()); }

        @Override
        public TimeWindow copy(TimeWindow from, TimeWindow reuse) { return new TimeWindow(from.getStart(), from.getEnd()); }

        @Override
        public int getLength() { return -1; }
    }

    static class SimpleStringSerializer implements TypeSerializer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() { return true; }

        @Override
        public TypeSerializer<String> duplicate() { return this; }

        @Override
        public String createInstance() { return ""; }

        @Override
        public String copy(String from) { return from; }

        @Override
        public String copy(String from, String reuse) { return from; }

        @Override
        public int getLength() { return -1; }
    }

    @Test
    void windowOperatorCopyForSubtaskReturnsDistinctInstance() {
        InternalWindowFunction<Object, String, String, TimeWindow> windowFunction =
                new InternalWindowFunction<>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void process(String key, TimeWindow window, InternalWindowContext context,
                                        Object input, io.nop.stream.core.util.Collector<String> out) {
                        out.collect("result");
                    }

                    @Override
                    public void clear(TimeWindow window, InternalWindowContext context) {}
                };

        WindowOperator<String, Integer, Object, String, TimeWindow> operator =
                new WindowOperator<>(
                        TumblingEventTimeWindows.of(100L),
                        new SimpleTimeWindowSerializer(),
                        (KeySelector<Integer, String>) v -> "key1",
                        new SimpleStringSerializer(),
                        String.class,
                        windowFunction,
                        EventTimeTrigger.create(),
                        0L,
                        null
                );

        WindowOperator<String, Integer, Object, String, TimeWindow> copy = operator.copyForSubtask();

        assertNotSame(operator, copy, "WindowOperator copy must be a fresh instance");
        assertSame(operator.getUserFunction(), copy.getUserFunction(),
                "WindowOperator user function must be shared across subtasks");
    }
}
