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
import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.operators.ChainingStrategy;
import io.nop.stream.core.operators.IWindowOperatorFactory;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.SimpleStreamOperatorFactory;
import io.nop.stream.core.operators.StreamOperatorFactory;
import io.nop.stream.core.operators.WindowSpec;
import io.nop.stream.core.transformation.OneInputTransformation;
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.windowing.AccumulationMode;
import io.nop.stream.core.windowing.WindowLifecycleEvent;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.Window;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_OPERATION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_UNSUPPORTED;

public class WindowedStreamImpl<T, K, W extends Window>
        extends DataStreamImpl<T> implements WindowedStream<T, K, W>, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(WindowedStreamImpl.class);

    private final KeyedStream<T, K> keyedStream;
    private final WindowAssigner<? super T, W> windowAssigner;
    private Trigger<? super T, W> trigger;
    private Evictor<? super T, W> evictor;
    private long allowedLateness = 0L;

    /**
     * WI38 (plan 03, FU-8 fix): accumulation mode declared by the model's windowing
     * strategy. Null = pure Java API path without a declaration — the created
     * operator keeps its legacy default (ACCUMULATING). The model path
     * (AdvancedTransforms.buildWindow) always passes a value (null normalized to
     * DISCARDING, the xdef default) so the declaration is what the operator runs.
     */
    private AccumulationMode accumulationMode;

    /**
     * G-2+09e① (plan 369 Phase 2): tag for the late-data side output configured via
     * {@link #sideOutputLateData(OutputTag)}. Reaches the created window operator
     * through the {@code IWindowOperatorFactory} tag-aware variants; late records are
     * retrievable via {@link SingleOutputStreamOperator#getSideOutput(OutputTag)}.
     */
    private OutputTag<T> lateDataOutputTag;

    /**
     * WI8 (plan 2332): max-delay fallback emission window (ms past window end,
     * processing-time clock). 0 = disabled. Flows to the created window operator
     * through the factory's WI8-aware variants.
     */
    private long maxDelay = 0L;

    /**
     * WI8 (plan 2332): lifecycle event switch configured via {@link #emitWindowEvents()}
     * together with its dedicated side-output tag. The tag id is derived from the
     * operator uid ({@code <uid>:window-events}, R-events-tag m2) so two window
     * operators never clobber each other's side-output registration.
     */
    private boolean emitWindowEvents = false;
    private OutputTag<WindowLifecycleEvent> windowEventsOutputTag;

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

    /**
     * WI6 (plan 2352): the resolved evictor observation point (mirror of
     * {@link #getTrigger()} — additive accessor, no behavior change).
     */
    public Evictor<? super T, W> getEvictor() {
        return evictor;
    }

    @Override
    public WindowedStreamImpl<T, K, W> trigger(Trigger<? super T, ? super W> trigger) {
        this.trigger = (Trigger<? super T, W>) trigger;
        return this;
    }

    @Override
    public WindowedStreamImpl<T, K, W> evictor(Evictor<? super T, ? super W> evictor) {
        this.evictor = (Evictor<? super T, W>) evictor;
        return this;
    }

    @Override
    public WindowedStreamImpl<T, K, W> sideOutputLateData(OutputTag<T> outputTag) {
        this.lateDataOutputTag = outputTag;
        return this;
    }

    /** Late-data side output tag configured via {@link #sideOutputLateData(OutputTag)}. */
    public OutputTag<T> getLateDataOutputTag() {
        return lateDataOutputTag;
    }

    @Override
    public WindowedStreamImpl<T, K, W> emitWindowEvents() {
        this.emitWindowEvents = true;
        // R-events-tag (plan 2332 m2): the tag id is derived from the operator uid —
        // the upstream transformation id is stable within a build and unique per
        // chain position, so two window operators do not share a side-output tag.
        String operatorUid = transformation != null
                ? String.valueOf(transformation.getId())
                : "window";
        this.windowEventsOutputTag = new OutputTag<>(operatorUid + ":window-events",
                BasicTypeInfo.of(WindowLifecycleEvent.class));
        return this;
    }

    /** Whether lifecycle events were enabled via {@link #emitWindowEvents()}. */
    public boolean isEmitWindowEvents() {
        return emitWindowEvents;
    }

    /** The dedicated lifecycle-event side-output tag (non-null once enabled). */
    public OutputTag<WindowLifecycleEvent> getWindowEventsOutputTag() {
        return windowEventsOutputTag;
    }

    @Override
    public WindowedStreamImpl<T, K, W> maxDelay(long maxDelay) {
        if (maxDelay < 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "maxDelay")
                    .param(ARG_DETAIL, "maxDelay must be >= 0 (0 = disabled), but was: " + maxDelay);
        }
        this.maxDelay = maxDelay;
        return this;
    }

    /** Max-delay fallback window configured via {@link #maxDelay(long)}. */
    public long getMaxDelay() {
        return maxDelay;
    }

    public WindowedStreamImpl<T, K, W> allowedLateness(long allowedLateness) {
        this.allowedLateness = allowedLateness;
        return this;
    }

    public long getAllowedLateness() {
        return allowedLateness;
    }

    /** WI38 (plan 03): declares the accumulation mode (null = operator legacy default). */
    public WindowedStreamImpl<T, K, W> accumulationMode(AccumulationMode accumulationMode) {
        this.accumulationMode = accumulationMode;
        return this;
    }

    public AccumulationMode getAccumulationMode() {
        return accumulationMode;
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
                    // WI7 plan 2417: the window operator family now lives in this module
                    // (io.nop.stream.core.operators.windowing) — direct construction, no
                    // reflective fallback. defaultFactory can only stay null when a caller
                    // supplies StreamComponents without a factory (call-sites fail fast).
                    defaultFactory = new io.nop.stream.core.operators.windowing.WindowOperatorFactoryImpl();
                }
            }
        }
        return defaultFactory;
    }

    /**
     * WI8 (plan 2420): assembles the immutable {@link WindowSpec} from the fluent
     * fields. The effective assigner comes from the call-site
     * ({@link #getEffectiveWindowAssigner()}, components-aware); the element type
     * keeps the {@link #inferElementClass(TypeInformation)} inference. Every axis
     * the former four-overload factory generations carried travels in the spec, so
     * a new field cannot be silently dropped by a shim.
     */
    @SuppressWarnings("unchecked")
    private WindowSpec<T, K, W> buildSpec(WindowAssigner<? super T, W> assigner) {
        return WindowSpec.<T, K, W>builder(
                        assigner,
                        keyedStream.getKeySelector(),
                        (Class<K>) (Class<?>) Object.class)
                .trigger(trigger)
                .evictor(evictor)
                .allowedLateness(allowedLateness)
                .elementType((Class<T>) (Class<?>) inferElementClass(getType()))
                .lateDataOutputTag(lateDataOutputTag)
                .maxDelay(maxDelay)
                .emitWindowEvents(emitWindowEvents)
                .windowEventsOutputTag(windowEventsOutputTag)
                .accumulationMode(accumulationMode)
                .build();
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
            throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_OPERATION,
                    "WindowOperator requires an IWindowOperatorFactory: none available (components factory is null)");
        }
        OneInputStreamOperator<T, R> operator = factory.createApplyOperator(
                buildSpec(assigner), function);
        return transform("WindowApply", (TypeInformation<R>) UnknownTypeInformation.INSTANCE, operator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <ACC, R> SingleOutputStreamOperator<R> aggregate(AggregateFunction<T, ACC, R> function) {
        WindowAssigner<? super T, W> assigner = getEffectiveWindowAssigner();
        IWindowOperatorFactory factory = getFactory();
        if (factory == null) {
            throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_OPERATION,
                    "WindowOperator requires an IWindowOperatorFactory: none available (components factory is null)");
        }
        // F-05: the spec's elementType carries the inferred IN element class — the
        // aggregate+evictor branch buffers raw IN elements in a ListState and needs
        // the element type.
        OneInputStreamOperator<T, R> operator = factory.createAggregateOperator(
                buildSpec(assigner), function, (Class<ACC>) (Class<?>) Object.class);
        return transform("WindowAggregate", (TypeInformation<R>) UnknownTypeInformation.INSTANCE, operator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SingleOutputStreamOperator<T> reduce(ReduceFunction<T> function) {
        WindowAssigner<? super T, W> assigner = getEffectiveWindowAssigner();
        IWindowOperatorFactory factory = getFactory();
        if (factory == null) {
            throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_OPERATION,
                    "WindowOperator requires an IWindowOperatorFactory: none available (components factory is null)");
        }
        OneInputStreamOperator<T, T> operator = factory.createReduceOperator(
                buildSpec(assigner), function);
        return transform("WindowReduce", getType(), operator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> SingleOutputStreamOperator<R> process(ProcessWindowFunction<T, R, K, W> function) {
        WindowAssigner<? super T, W> assigner = getEffectiveWindowAssigner();
        IWindowOperatorFactory factory = getFactory();
        if (factory == null) {
            throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_OPERATION,
                    "WindowOperator requires an IWindowOperatorFactory: none available (components factory is null)");
        }
        OneInputStreamOperator<T, R> operator = factory.createProcessOperator(
                buildSpec(assigner), function);
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
