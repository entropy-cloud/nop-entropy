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

package io.nop.stream.runtime.operators.windowing;

import java.util.function.BiFunction;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.Window;
import io.nop.stream.runtime.operators.windowing.functions.InternalIterableProcessWindowFunction;
import io.nop.stream.runtime.operators.windowing.functions.InternalIterableWindowFunction;
import io.nop.stream.runtime.operators.windowing.functions.InternalSingleValueWindowFunction;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;

public class WindowOperatorBuilder<IN, K, W extends Window> {

    private WindowAssigner<? super IN, W> windowAssigner;
    private Trigger<? super IN, ? super W> trigger;
    private Evictor<? super IN, W> evictor;
    private long allowedLateness;
    private KeySelector<IN, K> keySelector;
    private Class<K> keyClass;
    private TypeSerializer<K> keySerializer;
    private TypeSerializer<W> windowSerializer;
    private OutputTag<IN> lateDataOutputTag;

    public WindowOperatorBuilder<IN, K, W> windowAssigner(WindowAssigner<? super IN, W> windowAssigner) {
        this.windowAssigner = windowAssigner;
        return this;
    }

    public WindowOperatorBuilder<IN, K, W> trigger(Trigger<? super IN, ? super W> trigger) {
        this.trigger = trigger;
        return this;
    }

    public WindowOperatorBuilder<IN, K, W> evictor(Evictor<? super IN, W> evictor) {
        this.evictor = evictor;
        return this;
    }

    public WindowOperatorBuilder<IN, K, W> allowedLateness(long allowedLateness) {
        this.allowedLateness = allowedLateness;
        return this;
    }

    public WindowOperatorBuilder<IN, K, W> keySelector(KeySelector<IN, K> keySelector) {
        this.keySelector = keySelector;
        return this;
    }

    public WindowOperatorBuilder<IN, K, W> keyClass(Class<K> keyClass) {
        this.keyClass = keyClass;
        return this;
    }

    public WindowOperatorBuilder<IN, K, W> keySerializer(TypeSerializer<K> keySerializer) {
        this.keySerializer = keySerializer;
        return this;
    }

    public WindowOperatorBuilder<IN, K, W> windowSerializer(TypeSerializer<W> windowSerializer) {
        this.windowSerializer = windowSerializer;
        return this;
    }

    public WindowOperatorBuilder<IN, K, W> lateDataOutputTag(OutputTag<IN> lateDataOutputTag) {
        this.lateDataOutputTag = lateDataOutputTag;
        return this;
    }

    @SuppressWarnings("unchecked")
    public <ACC, OUT> WindowOperator<K, IN, ACC, OUT, W> aggregate(
            AggregateFunction<IN, ACC, OUT> aggregateFunction, Class<ACC> accumulatorType) {

        if (evictor != null) {
            ListStateDescriptor<IN> listDesc = new ListStateDescriptor<>("window-contents",
                    (Class<IN>) (Class<?>) Object.class);
            InternalIterableProcessWindowFunction<IN, OUT, K, W> windowFn =
                    new InternalIterableProcessWindowFunction<>(
                            new BufferingAggregateProcessWindowFunction<>(aggregateFunction));
            return (WindowOperator<K, IN, ACC, OUT, W>) (WindowOperator<?, ?, ?, ?, ?>)
                    buildWindowOperator(listDesc, windowFn, accumulatorType, null);
        }

        AggregatingStateDescriptor<IN, ACC, OUT> aggDesc =
                new AggregatingStateDescriptor<>("window-contents", aggregateFunction, accumulatorType);
        InternalSingleValueWindowFunction<ACC, OUT, K, W> windowFn =
                new InternalSingleValueWindowFunction<>(
                        (acc, ignored) -> aggregateFunction.getResult(acc));
        BiFunction<ACC, ACC, ACC> mergeFn = aggregateFunction::merge;
        return buildWindowOperator(aggDesc, windowFn, accumulatorType, mergeFn);
    }

    @SuppressWarnings("unchecked")
    public WindowOperator<K, IN, IN, IN, W> reduce(
            ReduceFunction<IN> reduceFunction, Class<IN> valueType) {

        if (evictor != null) {
            ListStateDescriptor<IN> listDesc = new ListStateDescriptor<>("window-contents", valueType);
            InternalIterableProcessWindowFunction<IN, IN, K, W> windowFn =
                    new InternalIterableProcessWindowFunction<>(
                            new BufferingReduceProcessWindowFunction<>(reduceFunction));
            return (WindowOperator<K, IN, IN, IN, W>) (WindowOperator<?, ?, ?, ?, ?>)
                    buildWindowOperator(listDesc, windowFn, valueType, null);
        }

        AggregateFunction<IN, IN, IN> reduceAsAgg = reduceFunctionAsAggregate(reduceFunction);
        AggregatingStateDescriptor<IN, IN, IN> aggDesc =
                new AggregatingStateDescriptor<>("window-contents", reduceAsAgg, valueType);
        InternalSingleValueWindowFunction<IN, IN, K, W> windowFn =
                new InternalSingleValueWindowFunction<>((acc, ignored) -> acc);
        BiFunction<IN, IN, IN> mergeFn = (a, b) -> {
            try {
                return reduceFunction.reduce(a, b);
            } catch (Exception e) {
                throw new io.nop.stream.core.exceptions.StreamException(e.getMessage(), e);
            }
        };
        return buildWindowOperator(aggDesc, windowFn, valueType, mergeFn);
    }

    @SuppressWarnings("unchecked")
    public <OUT> WindowOperator<K, IN, Iterable<IN>, OUT, W> apply(
            WindowFunction<IN, OUT, K, W> windowFunction, Class<IN> elementType) {
        ListStateDescriptor<IN> stateDesc = new ListStateDescriptor<>("window-contents", elementType);
        InternalIterableWindowFunction<IN, OUT, K, W> windowFn =
                new InternalIterableWindowFunction<>(windowFunction);
        return (WindowOperator<K, IN, Iterable<IN>, OUT, W>) (WindowOperator<?, ?, ?, ?, ?>)
                buildWindowOperator(stateDesc, windowFn, elementType, null);
    }

    @SuppressWarnings("unchecked")
    public <OUT> WindowOperator<K, IN, Iterable<IN>, OUT, W> process(
            ProcessWindowFunction<IN, OUT, K, W> processWindowFunction, Class<IN> elementType) {
        ListStateDescriptor<IN> stateDesc = new ListStateDescriptor<>("window-contents", elementType);
        InternalIterableProcessWindowFunction<IN, OUT, K, W> windowFn =
                new InternalIterableProcessWindowFunction<>(processWindowFunction);
        return (WindowOperator<K, IN, Iterable<IN>, OUT, W>) (WindowOperator<?, ?, ?, ?, ?>)
                buildWindowOperator(stateDesc, windowFn, elementType, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <ACC, OUT> WindowOperator<K, IN, ACC, OUT, W> buildWindowOperator(
            StateDescriptor<?> stateDesc,
            InternalWindowFunction<ACC, OUT, K, W> windowFn,
            Class<?> accClass,
            BiFunction<ACC, ACC, ACC> mergeFn) {
        return new WindowOperator(
                windowAssigner,
                windowSerializer,
                keySelector,
                keySerializer,
                keyClass,
                windowFn,
                trigger,
                allowedLateness,
                lateDataOutputTag,
                (Class) accClass,
                stateDesc,
                mergeFn,
                evictor);
    }

    private static <T> AggregateFunction<T, T, T> reduceFunctionAsAggregate(ReduceFunction<T> reduceFunction) {
        return new AggregateFunction<T, T, T>() {
            private static final long serialVersionUID = 1L;

            @Override
            public T createAccumulator() {
                return null;
            }

            @Override
            public T add(T value, T accumulator) {
                if (accumulator == null) {
                    return value;
                }
                try {
                    return reduceFunction.reduce(value, accumulator);
                } catch (Exception e) {
                    throw new io.nop.stream.core.exceptions.StreamException(e.getMessage(), e);
                }
            }

            @Override
            public T getResult(T accumulator) {
                return accumulator;
            }

            @Override
            public T merge(T a, T b) {
                if (a == null) {
                    return b;
                }
                if (b == null) {
                    return a;
                }
                try {
                    return reduceFunction.reduce(a, b);
                } catch (Exception e) {
                    throw new io.nop.stream.core.exceptions.StreamException(e.getMessage(), e);
                }
            }
        };
    }

    private static class BufferingAggregateProcessWindowFunction<IN, ACC, OUT, K, W extends Window>
            implements ProcessWindowFunction<IN, OUT, K, W> {
        private static final long serialVersionUID = 1L;

        private final AggregateFunction<IN, ACC, OUT> aggregateFunction;

        BufferingAggregateProcessWindowFunction(AggregateFunction<IN, ACC, OUT> aggregateFunction) {
            this.aggregateFunction = aggregateFunction;
        }

        @Override
        public void process(K key, W window, Iterable<IN> input,
                            ProcessWindowFunction.Context context,
                            Collector<OUT> out) {
            ACC acc = aggregateFunction.createAccumulator();
            for (IN element : input) {
                acc = aggregateFunction.add(element, acc);
            }
            OUT result = aggregateFunction.getResult(acc);
            if (result != null) {
                out.collect(result);
            }
        }
    }

    private static class BufferingReduceProcessWindowFunction<IN, K, W extends Window>
            implements ProcessWindowFunction<IN, IN, K, W> {
        private static final long serialVersionUID = 1L;

        private final ReduceFunction<IN> reduceFunction;

        BufferingReduceProcessWindowFunction(ReduceFunction<IN> reduceFunction) {
            this.reduceFunction = reduceFunction;
        }

        @Override
        public void process(K key, W window, Iterable<IN> input,
                            ProcessWindowFunction.Context context,
                            Collector<IN> out) throws Exception {
            IN result = null;
            for (IN element : input) {
                if (result == null) {
                    result = element;
                } else {
                    result = reduceFunction.reduce(result, element);
                }
            }
            if (result != null) {
                out.collect(result);
            }
        }
    }
}
