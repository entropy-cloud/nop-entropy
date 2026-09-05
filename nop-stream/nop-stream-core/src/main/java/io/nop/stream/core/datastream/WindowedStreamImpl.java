/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.operators.ChainingStrategy;
import io.nop.stream.core.operators.IWindowOperatorFactory;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.SimpleStreamOperatorFactory;
import io.nop.stream.core.operators.StreamOperatorFactory;
import io.nop.stream.core.transformation.OneInputTransformation;
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.Window;

public class WindowedStreamImpl<T, K, W extends Window>
        extends DataStreamImpl<T> implements WindowedStream<T, K, W>, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(WindowedStreamImpl.class);

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

    /**
     * F-05 (plan 1326-2 Phase 1): infers the window ListState element (IN) class from
     * the stream's {@link TypeInformation}. The previous call-sites hardcoded
     * {@code Object.class}, which broke bean elements on the RocksDB backend
     * (first window fire CCE via JSON-native LinkedHashMap) and on Memory-JSON
     * checkpoint restore. {@code UnknownTypeInformation.getTypeClass()} returns
     * {@code Object.class}, so genuinely unknown element types keep the legacy
     * generic descriptor (the factory then warns — No-Silent).
     */
    @SuppressWarnings("unchecked")
    private static <T> Class<T> inferElementClass(TypeInformation<T> type) {
        if (type != null) {
            try {
                Class<T> clazz = type.getTypeClass();
                if (clazz != null && clazz != Object.class) {
                    return clazz;
                }
            } catch (Exception e) {
                LOG.debug("Failed to infer window element class from TypeInformation; "
                        + "falling back to Object.class", e);
            }
        }
        return (Class<T>) (Class<?>) Object.class;
    }

    private static volatile IWindowOperatorFactory defaultFactory;

    public static void setDefaultFactory(IWindowOperatorFactory factory) {
        defaultFactory = factory;
    }

    private IWindowOperatorFactory getFactory() {
        if (components != null) {
            return components.getWindowOperatorFactory();
        }
        if (defaultFactory == null) {
            synchronized (WindowedStreamImpl.class) {
                if (defaultFactory == null) {
                    try {
                        Class<?> factoryClass = Class.forName(
                                "io.nop.stream.runtime.operators.windowing.WindowOperatorFactoryImpl");
                        defaultFactory = (IWindowOperatorFactory)
                                factoryClass.getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        // nop-stream-runtime not on classpath. Leave defaultFactory=null so that the
                        // call-sites (apply/aggregate/reduce/process) fail fast with StreamException
                        // ("requires nop-stream-runtime on classpath") rather than silently swallowing.
                        LOG.warn("nop-stream-runtime not on classpath; IWindowOperatorFactory unavailable"
                                + " (window operations will fail fast at call-site)", e);
                    }
                }
            }
        }
        return defaultFactory;
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
        if (factory == null) {
            throw new StreamException("WindowOperator requires nop-stream-runtime on classpath: no IWindowOperatorFactory available");
        }
        OneInputStreamOperator<T, R> operator = factory.createApplyOperator(
                assigner, trigger, evictor, allowedLateness, function,
                inferElementClass(getType()),
                keyedStream.getKeySelector(), (Class<K>) (Class<?>) Object.class);
        return transform("WindowApply", (TypeInformation<R>) UnknownTypeInformation.INSTANCE, operator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <ACC, R> SingleOutputStreamOperator<R> aggregate(AggregateFunction<T, ACC, R> function) {
        WindowAssigner<? super T, W> assigner = getEffectiveWindowAssigner();
        IWindowOperatorFactory factory = getFactory();
        if (factory == null) {
            throw new StreamException("WindowOperator requires nop-stream-runtime on classpath: no IWindowOperatorFactory available");
        }
        // F-05: pass the inferred IN element class — the aggregate+evictor branch
        // buffers raw IN elements in a ListState and needs the element type.
        OneInputStreamOperator<T, R> operator = factory.createAggregateOperator(
                assigner, trigger, evictor, allowedLateness, function,
                (Class<ACC>) (Class<?>) Object.class,
                (Class<T>) (Class<?>) inferElementClass(getType()),
                keyedStream.getKeySelector(), (Class<K>) (Class<?>) Object.class);
        return transform("WindowAggregate", (TypeInformation<R>) UnknownTypeInformation.INSTANCE, operator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SingleOutputStreamOperator<T> reduce(ReduceFunction<T> function) {
        WindowAssigner<? super T, W> assigner = getEffectiveWindowAssigner();
        IWindowOperatorFactory factory = getFactory();
        if (factory == null) {
            throw new StreamException("WindowOperator requires nop-stream-runtime on classpath: no IWindowOperatorFactory available");
        }
        OneInputStreamOperator<T, T> operator = factory.createReduceOperator(
                assigner, trigger, evictor, allowedLateness, function,
                (Class<T>) (Class<?>) inferElementClass(getType()),
                keyedStream.getKeySelector(), (Class<K>) (Class<?>) Object.class);
        return transform("WindowReduce", getType(), operator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> SingleOutputStreamOperator<R> process(ProcessWindowFunction<T, R, K, W> function) {
        WindowAssigner<? super T, W> assigner = getEffectiveWindowAssigner();
        IWindowOperatorFactory factory = getFactory();
        if (factory == null) {
            throw new StreamException("WindowOperator requires nop-stream-runtime on classpath: no IWindowOperatorFactory available");
        }
        OneInputStreamOperator<T, R> operator = factory.createProcessOperator(
                assigner, trigger, evictor, allowedLateness, function,
                (Class<T>) (Class<?>) inferElementClass(getType()),
                keyedStream.getKeySelector(), (Class<K>) (Class<?>) Object.class);
        return transform("WindowProcess", (TypeInformation<R>) UnknownTypeInformation.INSTANCE, operator);
    }

    @Override
    public <R> SingleOutputStreamOperator<R> transform(
            String operatorName,
            TypeInformation<R> outTypeInfo,
            OneInputStreamOperator<T, R> operator) {
        StreamOperatorFactory<R> operatorFactory = new SimpleStreamOperatorFactory<R>(
                operator, operatorName, environment.getParallelism(), ChainingStrategy.NEVER);
        OneInputTransformation<T, R> transform = new OneInputTransformation<>(
                this.transformation,
                operatorName,
                operatorFactory,
                outTypeInfo,
                environment.getParallelism()
        );
        environment.addTransformation(transform);
        return new SingleOutputStreamOperatorImpl<>(environment, transform);
    }
}
