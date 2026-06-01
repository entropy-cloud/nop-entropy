/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.IWindowOperatorFactory;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.windowing.windows.Window;

public class WindowOperatorFactoryImpl implements IWindowOperatorFactory {

    private static final long serialVersionUID = 1L;

    @Override
    public <IN, ACC, OUT, K, W extends Window>
    OneInputStreamOperator<IN, OUT> createAggregateOperator(
            WindowAssigner<? super IN, W> windowAssigner,
            Trigger<? super IN, ? super W> trigger,
            Evictor<? super IN, W> evictor,
            long allowedLateness,
            AggregateFunction<IN, ACC, OUT> aggregateFunction,
            Class<ACC> accumulatorType,
            KeySelector<IN, K> keySelector,
            Class<K> keyClass) {
        WindowOperatorBuilder<IN, K, W> builder = new WindowOperatorBuilder<>();
        builder.windowAssigner(windowAssigner)
               .trigger(trigger)
               .evictor(evictor)
               .allowedLateness(allowedLateness)
               .keySelector(keySelector)
               .keyClass(keyClass)
               .keySerializer(createDummySerializer(keyClass))
               .windowSerializer(inferWindowSerializer(windowAssigner));
        return builder.aggregate(aggregateFunction, accumulatorType);
    }

    @Override
    public <IN, K, W extends Window>
    OneInputStreamOperator<IN, IN> createReduceOperator(
            WindowAssigner<? super IN, W> windowAssigner,
            Trigger<? super IN, ? super W> trigger,
            Evictor<? super IN, W> evictor,
            long allowedLateness,
            ReduceFunction<IN> reduceFunction,
            Class<IN> valueType,
            KeySelector<IN, K> keySelector,
            Class<K> keyClass) {
        WindowOperatorBuilder<IN, K, W> builder = new WindowOperatorBuilder<>();
        builder.windowAssigner(windowAssigner)
               .trigger(trigger)
               .evictor(evictor)
               .allowedLateness(allowedLateness)
               .keySelector(keySelector)
               .keyClass(keyClass)
               .keySerializer(createDummySerializer(keyClass))
               .windowSerializer(inferWindowSerializer(windowAssigner));
        return builder.reduce(reduceFunction, valueType);
    }

    @Override
    public <IN, OUT, K, W extends Window>
    OneInputStreamOperator<IN, OUT> createApplyOperator(
            WindowAssigner<? super IN, W> windowAssigner,
            Trigger<? super IN, ? super W> trigger,
            Evictor<? super IN, W> evictor,
            long allowedLateness,
            WindowFunction<IN, OUT, K, W> windowFunction,
            Class<IN> elementType,
            KeySelector<IN, K> keySelector,
            Class<K> keyClass) {
        WindowOperatorBuilder<IN, K, W> builder = new WindowOperatorBuilder<>();
        builder.windowAssigner(windowAssigner)
               .trigger(trigger)
               .evictor(evictor)
               .allowedLateness(allowedLateness)
               .keySelector(keySelector)
               .keyClass(keyClass)
               .keySerializer(createDummySerializer(keyClass))
               .windowSerializer(inferWindowSerializer(windowAssigner));
        return builder.apply(windowFunction, elementType);
    }

    @Override
    public <IN, OUT, K, W extends Window>
    OneInputStreamOperator<IN, OUT> createProcessOperator(
            WindowAssigner<? super IN, W> windowAssigner,
            Trigger<? super IN, ? super W> trigger,
            Evictor<? super IN, W> evictor,
            long allowedLateness,
            ProcessWindowFunction<IN, OUT, K, W> processWindowFunction,
            Class<IN> elementType,
            KeySelector<IN, K> keySelector,
            Class<K> keyClass) {
        WindowOperatorBuilder<IN, K, W> builder = new WindowOperatorBuilder<>();
        builder.windowAssigner(windowAssigner)
               .trigger(trigger)
               .evictor(evictor)
               .allowedLateness(allowedLateness)
               .keySelector(keySelector)
               .keyClass(keyClass)
               .keySerializer(createDummySerializer(keyClass))
               .windowSerializer(inferWindowSerializer(windowAssigner));
        return builder.process(processWindowFunction, elementType);
    }

    @SuppressWarnings("unchecked")
    private <T> TypeSerializer<T> createDummySerializer(Class<T> typeClass) {
        return new TypeSerializer<T>() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isImmutableType() {
                return true;
            }

            @Override
            public TypeSerializer<T> duplicate() {
                return this;
            }

            @Override
            public T createInstance() {
                try {
                    return typeClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            public T copy(T from) {
                return from;
            }

            @Override
            public T copy(T from, T reuse) {
                return from;
            }

            @Override
            public int getLength() {
                return -1;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <W extends Window> TypeSerializer<W> inferWindowSerializer(WindowAssigner<?, W> windowAssigner) {
        if (windowAssigner.isEventTime()) {
            return (TypeSerializer<W>) new TimeWindowSerializer();
        }
        return (TypeSerializer<W>) new GlobalWindowSerializer();
    }

    static class TimeWindowSerializer implements TypeSerializer<TimeWindow> {
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

    static class GlobalWindowSerializer implements TypeSerializer<GlobalWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public TypeSerializer<GlobalWindow> duplicate() {
            return this;
        }

        @Override
        public GlobalWindow createInstance() {
            return GlobalWindow.get();
        }

        @Override
        public GlobalWindow copy(GlobalWindow from) {
            return GlobalWindow.get();
        }

        @Override
        public GlobalWindow copy(GlobalWindow from, GlobalWindow reuse) {
            return GlobalWindow.get();
        }

        @Override
        public int getLength() {
            return -1;
        }
    }
}
