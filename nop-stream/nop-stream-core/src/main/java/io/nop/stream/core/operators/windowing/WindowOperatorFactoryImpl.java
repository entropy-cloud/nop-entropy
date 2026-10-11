/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators.windowing;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.operators.IWindowOperatorFactory;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.WindowSpec;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.windowing.windows.Window;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_SERIALIZATION;

/**
 * WI8 (plan 2420): the former 17-method adapter (four overload generations,
 * each silently dropping the newest arguments) collapses to one
 * {@link WindowSpec}-driven method per operation. Every configuration axis
 * arrives inside the spec, so the aggregate/reduce/apply/process paths differ
 * only in their builder head.
 */
public class WindowOperatorFactoryImpl implements IWindowOperatorFactory {

    private static final long serialVersionUID = 1L;

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(WindowOperatorFactoryImpl.class);

    @Override
    public <IN, ACC, OUT, K, W extends Window>
    OneInputStreamOperator<IN, OUT> createAggregateOperator(
            WindowSpec<IN, K, W> spec,
            AggregateFunction<IN, ACC, OUT> aggregateFunction,
            Class<ACC> accumulatorType) {
        WindowOperatorBuilder<IN, K, W> builder = configureBuilder(spec);
        warnIfUninferableElementType("aggregate", spec.getElementType(), spec.getEvictor() != null);
        return builder.aggregate(aggregateFunction, inferAccumulatorType(aggregateFunction, accumulatorType),
                spec.getElementType());
    }

    @Override
    public <IN, K, W extends Window>
    OneInputStreamOperator<IN, IN> createReduceOperator(
            WindowSpec<IN, K, W> spec,
            ReduceFunction<IN> reduceFunction) {
        WindowOperatorBuilder<IN, K, W> builder = configureBuilder(spec);
        warnIfUninferableElementType("reduce", spec.getElementType(), spec.getEvictor() != null);
        return builder.reduce(reduceFunction, spec.getElementType());
    }

    @Override
    public <IN, OUT, K, W extends Window>
    OneInputStreamOperator<IN, OUT> createApplyOperator(
            WindowSpec<IN, K, W> spec,
            WindowFunction<IN, OUT, K, W> windowFunction) {
        WindowOperatorBuilder<IN, K, W> builder = configureBuilder(spec);
        warnIfUninferableElementType("apply", spec.getElementType(), true);
        return builder.apply(windowFunction, spec.getElementType());
    }

    @Override
    public <IN, OUT, K, W extends Window>
    OneInputStreamOperator<IN, OUT> createProcessOperator(
            WindowSpec<IN, K, W> spec,
            ProcessWindowFunction<IN, OUT, K, W> processWindowFunction) {
        WindowOperatorBuilder<IN, K, W> builder = configureBuilder(spec);
        warnIfUninferableElementType("process", spec.getElementType(), true);
        return builder.process(processWindowFunction, spec.getElementType());
    }

    /**
     * Shared builder setup for all operations (paths differ only in the builder
     * chain head): assigns assigner/trigger/evictor/lateness/key configuration
     * plus the inferred serializers, the late-data output tag, the max-delay /
     * window-events configuration and the accumulation mode — all read from the
     * spec so no field can be dropped between the call-site and the builder.
     */
    private <IN, K, W extends Window> WindowOperatorBuilder<IN, K, W> configureBuilder(WindowSpec<IN, K, W> spec) {
        WindowOperatorBuilder<IN, K, W> builder = new WindowOperatorBuilder<>();
        builder.windowAssigner(spec.getWindowAssigner())
               .trigger(spec.getTrigger())
               .evictor(spec.getEvictor())
               .allowedLateness(spec.getAllowedLateness())
               .keySelector(spec.getKeySelector())
               .keyClass(spec.getKeyClass())
               .keySerializer(createDummySerializer(spec.getKeyClass()))
               .windowSerializer(inferWindowSerializer(spec.getWindowAssigner()))
               .maxDelay(spec.getMaxDelay())
               .accumulationMode(spec.getAccumulationMode());
        if (spec.getLateDataOutputTag() != null) {
            builder.lateDataOutputTag(spec.getLateDataOutputTag());
        }
        if (spec.isEmitWindowEvents()) {
            builder.emitWindowEvents(true, spec.getWindowEventsOutputTag());
        }
        return builder;
    }

    /**
     * The aggregate API passes {@code Object.class} as the accumulator type
     * (generic erasure at the WindowedStreamImpl call-site). A generic
     * Object-typed accumulator breaks the RocksDB state read path (the DB value
     * round-trip deserializes via the descriptor's valueType: a {@code long[]}
     * accumulator comes back as an ArrayList and the user function's {@code add}
     * ClassCastExceptions) and records an unusable snapshot valueType/schema
     * checksum. Infer the real accumulator type from the live function's
     * {@code createAccumulator()} when the recorded type is the generic
     * {@code Object}; functions that return {@code null} (e.g. the
     * reduce-function wrapper) keep the recorded type (JSON-native accumulators
     * work either way).
     */
    @SuppressWarnings("unchecked")
    private static <ACC> Class<ACC> inferAccumulatorType(AggregateFunction<?, ACC, ?> function, Class<ACC> recordedType) {
        if (recordedType != Object.class || function == null) {
            return recordedType;
        }
        try {
            ACC accumulator = function.createAccumulator();
            if (accumulator != null) {
                return (Class<ACC>) accumulator.getClass();
            }
        } catch (Exception e) {
            // Keep the recorded (generic) type.
        }
        return recordedType;
    }

    /**
     * F-05 No-Silent guard (plan 1326-2 Phase 1): when the window element type stays
     * {@code Object.class} (stream built with {@code UnknownTypeInformation}), bean
     * elements come back as LinkedHashMap on the RocksDB backend and after a
     * Memory-JSON checkpoint restore. Warn instead of failing silently.
     */
    private static void warnIfUninferableElementType(String operation, Class<?> elementType,
                                                     boolean listStateBacked) {
        if (listStateBacked && elementType == Object.class) {
            LOG.warn("Window {} operator created with an uninferable element type (Object.class): "
                            + "bean elements will deserialize as LinkedHashMap on the RocksDB backend "
                            + "and after Memory-JSON checkpoint restore. Provide a typed stream "
                            + "(TypeInformation) upstream to fix this.",
                    operation);
        }
    }

    /**
     * Placeholder key serializer used only when no real
     * {@link TypeSerializer} is registered for the key class. The current
     * production paths never invoke copy/createInstance on it (the audit found
     * zero live call sites), and the contract is now honest so a future caller
     * cannot be silently corrupted:
     * <ul>
     *   <li>{@code copy} returns the argument but the serializer no longer
     *       claims immutability it cannot guarantee — {@code isImmutableType()}
     *       reports {@code false} so defensive-copy paths stay defensive.</li>
     *   <li>{@code createInstance} throws a typed exception on a non-default-
     *       constructible class instead of returning {@code null} (no-silent-
     *       no-op, guide #24).</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private <T> TypeSerializer<T> createDummySerializer(Class<T> typeClass) {
        return new TypeSerializer<T>() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isImmutableType() {
                // unknown class: cannot promise immutability — keep defensive
                // copies defensive instead of aliasing mutable keys
                return false;
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
                    throw new StreamException(ERR_STREAM_SERIALIZATION, e)
                            .param(ARG_DETAIL, "Dummy key serializer cannot create an instance of "
                                    + typeClass.getName()
                                    + " (class has no accessible default constructor)");
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
