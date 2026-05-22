/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import io.nop.stream.core.common.functions.*;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.StreamReduceOperator;
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.core.windowing.assigners.GlobalWindows;
import io.nop.stream.core.windowing.assigners.SlidingEventTimeWindows;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.evictors.CountEvictor;
import io.nop.stream.core.windowing.triggers.CountTrigger;
import io.nop.stream.core.windowing.triggers.PurgingTrigger;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.windowing.windows.Window;

import java.lang.reflect.Field;

public class KeyedStreamImpl<T, KEY> extends DataStreamImpl<T> implements KeyedStream<T, KEY> {
    private static final long serialVersionUID = 1L;

    private final KeySelector<T, KEY> keySelector;
    private final DataStream<T> parentStream;

    public KeyedStreamImpl(
            StreamExecutionEnvironment environment,
            Transformation<T> transformation,
            KeySelector<T, KEY> keySelector) {
        super(environment, transformation);
        this.keySelector = keySelector;
        this.parentStream = null;
    }

    public KeyedStreamImpl(DataStream<T> parentStream, KeySelector<T, KEY> keySelector) {
        super(parentStream instanceof DataStreamImpl ? ((DataStreamImpl<T>) parentStream).getEnvironment() : null,
              parentStream instanceof DataStreamImpl ? ((DataStreamImpl<T>) parentStream).getTransformation() : null);
        this.keySelector = keySelector;
        this.parentStream = parentStream;
    }

    @Override
    public TypeInformation<T> getType() {
        if (parentStream != null) {
            return parentStream.getType();
        }
        return super.getType();
    }

    @Override
    public KeySelector<T, KEY> getKeySelector() {
        return keySelector;
    }

    @Override
    public <K> KeyedStream<T, K> keyBy(KeySelector<T, K> key) {
        if (parentStream != null) {
            return new KeyedStreamImpl<>(parentStream, key);
        }
        return super.keyBy(key);
    }

    @Override
    public <R> SingleOutputStreamOperator<R> map(MapFunction<T, R> mapper) {
        if (parentStream != null) {
            return parentStream.map(mapper);
        }
        return super.map(mapper);
    }

    @Override
    public SingleOutputStreamOperator<T> filter(FilterFunction<T> filter) {
        if (parentStream != null) {
            return parentStream.filter(filter);
        }
        return super.filter(filter);
    }

    @Override
    public <R> SingleOutputStreamOperator<R> flatMap(FlatMapFunction<T, R> flatMapper) {
        if (parentStream != null) {
            return parentStream.flatMap(flatMapper);
        }
        return super.flatMap(flatMapper);
    }

    @Override
    public <R> SingleOutputStreamOperator<R> transform(
            String operatorName,
            TypeInformation<R> outTypeInfo,
            OneInputStreamOperator<T, R> operator) {
        if (parentStream != null) {
            return parentStream.transform(operatorName, outTypeInfo, operator);
        }
        return super.transform(operatorName, outTypeInfo, operator);
    }

    @Override
    public void print() {
        if (parentStream != null) {
            parentStream.print();
        } else {
            super.print();
        }
    }

    @Override
    public void print(SinkFunction<T> sinkFunction) {
        if (parentStream != null) {
            parentStream.print(sinkFunction);
        } else {
            super.print(sinkFunction);
        }
    }

    @Override
    public void collect(SinkFunction<T> collectorFunction) {
        if (parentStream != null) {
            parentStream.collect(collectorFunction);
        } else {
            super.collect(collectorFunction);
        }
    }

    @Override
    public void sink(SinkFunction<T> sinkFunction) {
        if (parentStream != null) {
            parentStream.sink(sinkFunction);
        } else {
            super.sink(sinkFunction);
        }
    }

    @Override
    public WindowedStream<T, KEY, TimeWindow> timeWindow(long size) {
        return window(TumblingEventTimeWindows.of(size));
    }

    @Override
    public WindowedStream<T, KEY, TimeWindow> timeWindow(long size, long slide) {
        return window(SlidingEventTimeWindows.of(size, slide));
    }

    @Override
    public WindowedStream<T, KEY, GlobalWindow> countWindow(long size) {
        return window(GlobalWindows.create()).trigger(PurgingTrigger.of(CountTrigger.of(size)));
    }

    @Override
    public WindowedStream<T, KEY, GlobalWindow> countWindow(long size, long slide) {
        return window(GlobalWindows.create()).evictor(CountEvictor.of(size)).trigger(CountTrigger.of(slide));
    }

    @Override
    public <W extends Window> WindowedStream<T, KEY, W> window(WindowAssigner<? super T, W> assigner) {
        return new WindowedStreamImpl<>(this, assigner);
    }

    @Override
    public SingleOutputStreamOperator<T> reduce(ReduceFunction<T> reducer) {
        return transform("Reduce", getType(), new StreamReduceOperator<>(reducer));
    }

    @Override
    @SuppressWarnings("unchecked")
    public SingleOutputStreamOperator<T> sum(int field) {
        if (field != 0) {
            throw new UnsupportedOperationException(
                    "sum(int field) with field != 0 requires Tuple types");
        }
        ReduceFunction<T> reducer = (v1, v2) -> {
            if (v1 instanceof Integer) return (T) (Integer) (((Integer) v1) + ((Number) v2).intValue());
            if (v1 instanceof Long) return (T) (Long) (((Long) v1) + ((Number) v2).longValue());
            if (v1 instanceof Double) return (T) (Double) (((Double) v1) + ((Number) v2).doubleValue());
            if (v1 instanceof Float) return (T) (Float) (((Float) v1) + ((Number) v2).floatValue());
            throw new UnsupportedOperationException("sum(int field) requires Number elements");
        };
        return transform("Sum", getType(), new StreamReduceOperator<>(reducer));
    }

    @Override
    public SingleOutputStreamOperator<T> sum(String field) {
        return transform("Sum", getType(),
                new StreamReduceOperator<>(new FieldAggregationReducer<>(field, FieldAggregationReducer.AggregationType.SUM)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public SingleOutputStreamOperator<T> min(int field) {
        if (field != 0) {
            throw new UnsupportedOperationException(
                    "min(int field) with field != 0 requires Tuple types");
        }
        ReduceFunction<T> reducer = (v1, v2) -> {
            if (v1 instanceof Comparable) {
                return ((Comparable<T>) v1).compareTo(v2) <= 0 ? v1 : v2;
            }
            throw new UnsupportedOperationException("min(int field) requires Comparable elements");
        };
        return transform("Min", getType(), new StreamReduceOperator<>(reducer));
    }

    @Override
    public SingleOutputStreamOperator<T> min(String field) {
        return transform("Min", getType(),
                new StreamReduceOperator<>(new FieldAggregationReducer<>(field, FieldAggregationReducer.AggregationType.MIN)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public SingleOutputStreamOperator<T> max(int field) {
        if (field != 0) {
            throw new UnsupportedOperationException(
                    "max(int field) with field != 0 requires Tuple types");
        }
        ReduceFunction<T> reducer = (v1, v2) -> {
            if (v1 instanceof Comparable) {
                return ((Comparable<T>) v1).compareTo(v2) >= 0 ? v1 : v2;
            }
            throw new UnsupportedOperationException("max(int field) requires Comparable elements");
        };
        return transform("Max", getType(), new StreamReduceOperator<>(reducer));
    }

    @Override
    public SingleOutputStreamOperator<T> max(String field) {
        return transform("Max", getType(),
                new StreamReduceOperator<>(new FieldAggregationReducer<>(field, FieldAggregationReducer.AggregationType.MAX)));
    }

    static class FieldAggregationReducer<T> implements ReduceFunction<T> {

        private static final long serialVersionUID = 1L;

        enum AggregationType { SUM, MIN, MAX }

        private final String fieldName;
        private final AggregationType type;
        private transient Field fieldAccessor;

        FieldAggregationReducer(String fieldName, AggregationType type) {
            this.fieldName = fieldName;
            this.type = type;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T reduce(T acc, T value) throws Exception {
            initField(acc.getClass());
            Number accVal = (Number) fieldAccessor.get(acc);
            Number newVal = (Number) fieldAccessor.get(value);
            Number result = aggregate(accVal, newVal);
            fieldAccessor.set(acc, convert(accVal, result));
            return acc;
        }

        private void initField(Class<?> clazz) throws NoSuchFieldException {
            if (fieldAccessor == null) {
                Field f = findField(clazz, fieldName);
                f.setAccessible(true);
                fieldAccessor = f;
            }
        }

        private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
            Class<?> c = clazz;
            while (c != null) {
                try {
                    return c.getDeclaredField(name);
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
            throw new NoSuchFieldException("Field '" + name + "' not found in " + clazz.getName());
        }

        private Number aggregate(Number a, Number b) {
            switch (type) {
                case SUM: return add(a, b);
                case MIN: return compare(a, b) <= 0 ? a : b;
                case MAX: return compare(a, b) >= 0 ? a : b;
                default: throw new IllegalStateException("Unknown type: " + type);
            }
        }

        private static Number add(Number a, Number b) {
            if (a instanceof Integer) return a.intValue() + b.intValue();
            if (a instanceof Long) return a.longValue() + b.longValue();
            if (a instanceof Double) return a.doubleValue() + b.doubleValue();
            if (a instanceof Float) return a.floatValue() + b.floatValue();
            return a.doubleValue() + b.doubleValue();
        }

        private static int compare(Number a, Number b) {
            if (a instanceof Integer) return Integer.compare(a.intValue(), b.intValue());
            if (a instanceof Long) return Long.compare(a.longValue(), b.longValue());
            if (a instanceof Double) return Double.compare(a.doubleValue(), b.doubleValue());
            if (a instanceof Float) return Float.compare(a.floatValue(), b.floatValue());
            return Double.compare(a.doubleValue(), b.doubleValue());
        }

        private static Object convert(Number template, Number result) {
            if (template instanceof Integer) return result.intValue();
            if (template instanceof Long) return result.longValue();
            if (template instanceof Double) return result.doubleValue();
            if (template instanceof Float) return result.floatValue();
            return result.doubleValue();
        }
    }
}
