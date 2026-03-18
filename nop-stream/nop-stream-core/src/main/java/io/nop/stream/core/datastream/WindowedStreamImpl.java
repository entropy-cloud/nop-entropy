/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.transformation.OneInputTransformation;
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.Window;

import java.io.Serializable;

public class WindowedStreamImpl<T, K, W extends Window>
        extends DataStreamImpl<T> implements WindowedStream<T, K, W>, Serializable {

    private static final long serialVersionUID = 1L;

    private final KeyedStream<T, K> keyedStream;
    private final WindowAssigner<? super T, W> windowAssigner;
    private Trigger<? super T, W> trigger;
    private Evictor<? super T, W> evictor;
    private long allowedLateness = 0L;

    public WindowedStreamImpl(
            KeyedStream<T, K> keyedStream,
            WindowAssigner<? super T, W> windowAssigner) {
        super(extractEnvironment(keyedStream), extractTransformation(keyedStream));
        this.keyedStream = keyedStream;
        this.windowAssigner = windowAssigner;
        this.trigger = windowAssigner.getDefaultTrigger(null);
    }

    private static <T, K> StreamExecutionEnvironment extractEnvironment(KeyedStream<T, K> keyedStream) {
        if (keyedStream instanceof DataStreamImpl) {
            return ((DataStreamImpl<T>) keyedStream).getEnvironment();
        }
        return null;
    }

    private static <T, K> Transformation<T> extractTransformation(KeyedStream<T, K> keyedStream) {
        if (keyedStream instanceof DataStreamImpl) {
            return ((DataStreamImpl<T>) keyedStream).getTransformation();
        }
        return null;
    }

    public WindowedStreamImpl(
            StreamExecutionEnvironment environment,
            Transformation<T> transformation,
            KeyedStream<T, K> keyedStream,
            WindowAssigner<? super T, W> windowAssigner,
            Trigger<? super T, W> trigger) {
        super(environment, transformation);
        this.keyedStream = keyedStream;
        this.windowAssigner = windowAssigner;
        this.trigger = trigger;
    }

    public WindowAssigner<? super T, W> getWindowAssigner() {
        return windowAssigner;
    }

    public Trigger<? super T, W> getTrigger() {
        return trigger;
    }

    @Override
    public WindowedStreamImpl<T, K, W> trigger(Trigger<? super T, ? super W> trigger) {
        this.trigger = (Trigger<? super T, W>) trigger;
        return this;
    }

    public Evictor<? super T, W> getEvictor() {
        return evictor;
    }

    @Override
    public WindowedStreamImpl<T, K, W> evictor(Evictor<? super T, ? super W> evictor) {
        this.evictor = (Evictor<? super T, W>) evictor;
        return this;
    }

    public WindowedStreamImpl<T, K, W> allowedLateness(long allowedLateness) {
        this.allowedLateness = allowedLateness;
        return this;
    }

    public long getAllowedLateness() {
        return allowedLateness;
    }

    public KeyedStream<T, K> getKeyedStream() {
        return keyedStream;
    }

    @Override
    public <R> SingleOutputStreamOperator<R> apply(WindowFunction<T, R, K, W> function) {
        TypeInformation<R> outTypeInfo = null;

        OneInputTransformation<T, R> transform = new OneInputTransformation<>(
                this.transformation,
                "WindowApply",
                null,
                outTypeInfo,
                environment.getParallelism()
        );

        environment.addTransformation(transform);
        return new SingleOutputStreamOperatorImpl<>(environment, transform);
    }

    @Override
    public <ACC, R> SingleOutputStreamOperator<R> aggregate(AggregateFunction<T, ACC, R> function) {
        TypeInformation<R> outTypeInfo = null;

        OneInputTransformation<T, R> transform = new OneInputTransformation<>(
                this.transformation,
                "WindowAggregate",
                null,
                outTypeInfo,
                environment.getParallelism()
        );

        environment.addTransformation(transform);
        return new SingleOutputStreamOperatorImpl<>(environment, transform);
    }

    @Override
    public SingleOutputStreamOperator<T> reduce(ReduceFunction<T> function) {
        TypeInformation<T> outTypeInfo = getType();

        OneInputTransformation<T, T> transform = new OneInputTransformation<>(
                this.transformation,
                "WindowReduce",
                null,
                outTypeInfo,
                environment.getParallelism()
        );

        environment.addTransformation(transform);
        return new SingleOutputStreamOperatorImpl<>(environment, transform);
    }

    @Override
    public <R> SingleOutputStreamOperator<R> transform(
            String operatorName,
            TypeInformation<R> outTypeInfo,
            OneInputStreamOperator<T, R> operator) {
        return super.transform(operatorName, outTypeInfo, operator);
    }
}
