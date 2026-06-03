/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import java.io.Serializable;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.AggregateAggregationFunction;
import io.nop.stream.core.operators.ApplyAggregationFunction;
import io.nop.stream.core.operators.IWindowOperatorFactory;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.ProcessWindowAggregationFunction;
import io.nop.stream.core.operators.ReduceAggregationFunction;
import io.nop.stream.core.operators.WindowAggregationFunction;
import io.nop.stream.core.operators.WindowAggregationOperator;
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.Window;

public class WindowedStreamImpl<T, K, W extends Window>
        extends DataStreamImpl<T> implements WindowedStream<T, K, W>, Serializable {

    private static final long serialVersionUID = 1L;

    private final KeyedStream<T, K> keyedStream;
    private final WindowAssigner<? super T, W> windowAssigner;
    private Trigger<? super T, W> trigger;
    private Evictor<? super T, W> evictor;
    private long allowedLateness = 0L;

    private String windowingStrategyId;
    private StreamComponents components;

    public WindowedStreamImpl(
            KeyedStream<T, K> keyedStream,
            WindowAssigner<? super T, W> windowAssigner) {
        super(extractEnvironment(keyedStream), extractTransformation(keyedStream));
        this.keyedStream = keyedStream;
        this.windowAssigner = windowAssigner;
        // No IServiceContext available during stream plan construction; all current
        // WindowAssigner implementations ignore this parameter, so null is safe here.
        this.trigger = windowAssigner.getDefaultTrigger(null);
    }

    public WindowedStreamImpl(
            KeyedStream<T, K> keyedStream,
            String windowingStrategyId,
            StreamComponents components) {
        this(keyedStream, lookupAssigner(windowingStrategyId, components));
        this.windowingStrategyId = windowingStrategyId;
        this.components = components;
    }

    private static <T, W extends Window> WindowAssigner<? super T, W> lookupAssigner(
            String windowingStrategyId, StreamComponents components) {
        return components.getBean(windowingStrategyId, WindowAssigner.class);
    }

    private WindowAssigner<? super T, W> getEffectiveWindowAssigner() {
        if (windowingStrategyId != null && components != null) {
            return components.getBean(windowingStrategyId, WindowAssigner.class);
        }
        return windowAssigner;
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

    private IWindowOperatorFactory getFactory() {
        if (components != null) {
            return components.getWindowOperatorFactory();
        }
        return null;
    }

    public WindowedStreamImpl<T, K, W> withComponents(StreamComponents components) {
        this.components = components;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> SingleOutputStreamOperator<R> apply(WindowFunction<T, R, K, W> function) {
        WindowAssigner<? super T, W> assigner = getEffectiveWindowAssigner();
        IWindowOperatorFactory factory = getFactory();
        if (factory != null) {
            OneInputStreamOperator<T, R> operator = factory.createApplyOperator(
                    assigner, trigger, evictor, allowedLateness, function,
                    (Class<T>) (Class<?>) Object.class,
                    keyedStream.getKeySelector(), (Class<K>) (Class<?>) Object.class);
            return transform("WindowApply", (TypeInformation<R>) UnknownTypeInformation.INSTANCE, operator);
        }
        WindowAggregationFunction<T, java.util.List<T>, R, K, W> aggFn =
                new ApplyAggregationFunction<>(function);
        WindowAggregationOperator<T, java.util.List<T>, R, K, W> operator =
                new WindowAggregationOperator<>(assigner, trigger, aggFn, keyedStream.getKeySelector());
        operator.setAllowedLateness(allowedLateness);
        return transform("WindowApply", (TypeInformation<R>) UnknownTypeInformation.INSTANCE, operator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <ACC, R> SingleOutputStreamOperator<R> aggregate(AggregateFunction<T, ACC, R> function) {
        WindowAssigner<? super T, W> assigner = getEffectiveWindowAssigner();
        IWindowOperatorFactory factory = getFactory();
        if (factory != null) {
            OneInputStreamOperator<T, R> operator = factory.createAggregateOperator(
                    assigner, trigger, evictor, allowedLateness, function,
                    (Class<ACC>) (Class<?>) Object.class,
                    keyedStream.getKeySelector(), (Class<K>) (Class<?>) Object.class);
            return transform("WindowAggregate", (TypeInformation<R>) UnknownTypeInformation.INSTANCE, operator);
        }
        WindowAggregationFunction<T, ACC, R, K, W> aggFn =
                new AggregateAggregationFunction<>(function);
        WindowAggregationOperator<T, ACC, R, K, W> operator =
                new WindowAggregationOperator<>(assigner, trigger, aggFn, keyedStream.getKeySelector());
        operator.setAllowedLateness(allowedLateness);
        return transform("WindowAggregate", (TypeInformation<R>) UnknownTypeInformation.INSTANCE, operator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SingleOutputStreamOperator<T> reduce(ReduceFunction<T> function) {
        WindowAssigner<? super T, W> assigner = getEffectiveWindowAssigner();
        IWindowOperatorFactory factory = getFactory();
        if (factory != null) {
            OneInputStreamOperator<T, T> operator = factory.createReduceOperator(
                    assigner, trigger, evictor, allowedLateness, function,
                    (Class<T>) (Class<?>) Object.class,
                    keyedStream.getKeySelector(), (Class<K>) (Class<?>) Object.class);
            return transform("WindowReduce", getType(), operator);
        }
        WindowAggregationFunction<T, T, T, K, W> aggFn =
                new ReduceAggregationFunction<>(function);
        WindowAggregationOperator<T, T, T, K, W> operator =
                new WindowAggregationOperator<>(assigner, trigger, aggFn, keyedStream.getKeySelector());
        operator.setAllowedLateness(allowedLateness);
        return transform("WindowReduce", getType(), operator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> SingleOutputStreamOperator<R> process(ProcessWindowFunction<T, R, K, W> function) {
        WindowAssigner<? super T, W> assigner = getEffectiveWindowAssigner();
        IWindowOperatorFactory factory = getFactory();
        if (factory != null) {
            OneInputStreamOperator<T, R> operator = factory.createProcessOperator(
                    assigner, trigger, evictor, allowedLateness, function,
                    (Class<T>) (Class<?>) Object.class,
                    keyedStream.getKeySelector(), (Class<K>) (Class<?>) Object.class);
            return transform("WindowProcess", (TypeInformation<R>) UnknownTypeInformation.INSTANCE, operator);
        }
        WindowAggregationFunction<T, java.util.List<T>, R, K, W> aggFn =
                new ProcessWindowAggregationFunction<>(function);
        WindowAggregationOperator<T, java.util.List<T>, R, K, W> operator =
                new WindowAggregationOperator<>(assigner, trigger, aggFn, keyedStream.getKeySelector());
        operator.setAllowedLateness(allowedLateness);
        return transform("WindowProcess", (TypeInformation<R>) UnknownTypeInformation.INSTANCE, operator);
    }

    @Override
    public <R> SingleOutputStreamOperator<R> transform(
            String operatorName,
            TypeInformation<R> outTypeInfo,
            OneInputStreamOperator<T, R> operator) {
        return super.transform(operatorName, outTypeInfo, operator);
    }
}
