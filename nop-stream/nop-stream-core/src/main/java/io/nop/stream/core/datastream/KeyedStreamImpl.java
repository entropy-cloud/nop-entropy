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
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.windowing.windows.Window;

/**
 * A {@code KeyedStreamImpl} represents a {@link DataStream} on which operator state is partitioned by
 * key using a provided {@link KeySelector}.
 *
 * <p>This implementation wraps either a transformation (for the full DataStreamImpl case)
 * or directly wraps a parent DataStream (for the SimpleDataStreamSource case).
 *
 * @param <T> The type of elements in the stream.
 * @param <KEY> The type of the key.
 */
public class KeyedStreamImpl<T, KEY> extends DataStreamImpl<T> implements KeyedStream<T, KEY> {
    private static final long serialVersionUID = 1L;

    private final KeySelector<T, KEY> keySelector;
    private final DataStream<T> parentStream;

    /**
     * Creates a new KeyedStream from a transformation (used by DataStreamImpl).
     *
     * @param environment The execution environment
     * @param transformation The transformation that produces this stream
     * @param keySelector The key selector for partitioning
     */
    public KeyedStreamImpl(
            StreamExecutionEnvironment environment,
            Transformation<T> transformation,
            KeySelector<T, KEY> keySelector) {
        super(environment, transformation);
        this.keySelector = keySelector;
        this.parentStream = null;
    }

    /**
     * Creates a new KeyedStream from a parent DataStream (used by SimpleDataStreamSource).
     *
     * @param parentStream The parent data stream
     * @param keySelector The key selector for partitioning
     */
    public KeyedStreamImpl(DataStream<T> parentStream, KeySelector<T, KEY> keySelector) {
        super(null, null);
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
        // KeyedStream is already keyed, return a new KeyedStream with the new key selector
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
    public void print() throws Exception {
        if (parentStream != null) {
            parentStream.print();
        } else {
            super.print();
        }
    }

    @Override
    public void print(SinkFunction<T> sinkFunction) throws Exception {
        if (parentStream != null) {
            parentStream.print(sinkFunction);
        } else {
            super.print(sinkFunction);
        }
    }

    @Override
    public void collect(SinkFunction<T> collectorFunction) throws Exception {
        if (parentStream != null) {
            parentStream.collect(collectorFunction);
        } else {
            super.collect(collectorFunction);
        }
    }

    @Override
    public void sink(SinkFunction<T> sinkFunction) throws Exception {
        if (parentStream != null) {
            parentStream.sink(sinkFunction);
        } else {
            super.sink(sinkFunction);
        }
    }

    // ------------------------------------------------------------------------
    //  Windowing operations (placeholder implementations for I8)
    // ------------------------------------------------------------------------

    @Override
    public WindowedStream<T, KEY, TimeWindow> timeWindow(long size) {
        throw new UnsupportedOperationException("timeWindow() not yet implemented. Will be implemented in I8.");
    }

    @Override
    public WindowedStream<T, KEY, TimeWindow> timeWindow(long size, long slide) {
        throw new UnsupportedOperationException("timeWindow() with slide not yet implemented. Will be implemented in I8.");
    }

    @Override
    public WindowedStream<T, KEY, Window> countWindow(long size) {
        throw new UnsupportedOperationException("countWindow() not yet implemented. Will be implemented in I8.");
    }

    @Override
    public WindowedStream<T, KEY, Window> countWindow(long size, long slide) {
        throw new UnsupportedOperationException("countWindow() with slide not yet implemented. Will be implemented in I8.");
    }

    @Override
    public <W extends Window> WindowedStream<T, KEY, W> window(WindowAssigner<? super T, W> assigner) {
        throw new UnsupportedOperationException("window() not yet implemented. Will be implemented in I8.");
    }
}
