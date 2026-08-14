/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_STREAM_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ATTR_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ATTR_VALUE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ELEMENT;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_STREAM_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_REF_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_REF_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NOT_IMPLEMENTED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_REF_UNKNOWN;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_REQUIRED_ATTR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_REQUIRED_BODY;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_UPSTREAM_NULL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_UPSTREAM_NOT_STREAM;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_UPSTREAM_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_WINDOW_ATTR_UNSUPPORTED;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.nop.stream.cep.CEP;
import io.nop.stream.cep.PatternStream;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.model.CepPatternModel;
import io.nop.stream.cep.model.builder.CepPatternBuilder;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeyedProcessFunction;
import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.eventtime.TimestampAssigner;
import io.nop.stream.core.common.eventtime.TimestampAssignerSupplier;
import io.nop.stream.core.common.eventtime.WatermarkGenerator;
import io.nop.stream.core.common.eventtime.WatermarkGeneratorSupplier;
import io.nop.stream.core.common.eventtime.WatermarkOutput;
import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.datastream.DataStream;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.datastream.WindowedStream;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.AccumulationMode;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.flow.builder.functions.XplReduceFunction;
import io.nop.stream.flow.model.StreamAggregateModel;
import io.nop.stream.flow.model.StreamCepModel;
import io.nop.stream.flow.model.StreamCustomModel;
import io.nop.stream.flow.model.StreamProcessModel;
import io.nop.stream.flow.model.StreamReduceModel;
import io.nop.stream.flow.model.StreamSideOutputModel;
import io.nop.stream.flow.model.StreamTimestampsAndWatermarksModel;
import io.nop.stream.flow.model.StreamTransformModel;
import io.nop.stream.flow.model.StreamUnionModel;
import io.nop.stream.flow.model.StreamWindowModel;
import io.nop.stream.flow.model.WindowingStrategyModel;

/**
 * Implements the advanced transform translations declared in {@code stream.xdef} that
 * {@link StreamModelDslBuilder} does not handle inline.
 *
 * <p>Every branch is either a real DataStream-API call or fails fast with a
 * {@link StreamException} carrying an {@code ERR_STREAM_*} code — no silent no-ops. Two
 * transforms ({@code <union>}, {@code <sideOutput>}) fail fast because the underlying
 * {@code nop-stream-core} runtime does not yet expose the corresponding API
 * ({@code DataStream.union} / {@code SingleOutputStreamOperator.getSideOutput});
 * these are tracked as runtime-API-gap follow-ups in the plan.
 *
 * <p>P1-XDSL-6: window strategy declarations ({@code triggerId}/{@code allowedLateness}/
 * {@code accumulationMode}) and window node-level {@code allowedLateness}/{@code triggerId}
 * children are rejected at build time when they differ from the xdef defaults — the core
 * {@link WindowedStream} only exposes {@code trigger()}/{@code evictor()}, so a declared
 * non-default value would otherwise be silently ignored.
 */
final class AdvancedTransforms {

    private static final long XDEF_DEFAULT_ALLOWED_LATENESS = 0L;

    private AdvancedTransforms() {
    }

    static Object build(StreamModelDslBuilder owner, StreamExecutionEnvironment env,
                        StreamTransformModel t, Set<String> upstreamIds,
                        Map<String, Object> streamRegistry) {
        if (t instanceof StreamWindowModel) {
            return buildWindow(owner, t, upstreamIds, streamRegistry, (StreamWindowModel) t);
        }
        if (t instanceof StreamAggregateModel) {
            return buildAggregate(owner, t, upstreamIds, streamRegistry, (StreamAggregateModel) t);
        }
        if (t instanceof StreamReduceModel) {
            return buildReduce(owner, t, upstreamIds, streamRegistry, (StreamReduceModel) t);
        }
        if (t instanceof StreamProcessModel) {
            return buildProcess(owner, t, upstreamIds, streamRegistry, (StreamProcessModel) t);
        }
        if (t instanceof StreamUnionModel) {
            return buildUnion(t, upstreamIds);
        }
        if (t instanceof StreamCustomModel) {
            return buildCustom(owner, t, upstreamIds, streamRegistry, (StreamCustomModel) t);
        }
        if (t instanceof StreamCepModel) {
            return buildCep(owner, t, upstreamIds, streamRegistry, (StreamCepModel) t);
        }
        if (t instanceof StreamSideOutputModel) {
            return buildSideOutput((StreamSideOutputModel) t);
        }
        if (t instanceof StreamTimestampsAndWatermarksModel) {
            return buildTimestampsAndWatermarks(owner, t, upstreamIds, streamRegistry,
                    (StreamTimestampsAndWatermarksModel) t);
        }
        throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                .param(ARG_DETAIL, "unknown transform type " + t.getClass().getName());
    }

    // ----------------------------------------------------------------
    // window / aggregate / reduce
    // ----------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T, K> WindowedStream<?, ?, ?> buildWindow(StreamModelDslBuilder owner,
                                                               StreamTransformModel t,
                                                               Set<String> upstreamIds,
                                                               Map<String, Object> streamRegistry,
                                                               StreamWindowModel m) {
        Object in = requireSingleInput(upstreamIds, streamRegistry, owner, t);
        if (!(in instanceof KeyedStream)) {
            throw new StreamException(ERR_STREAM_UPSTREAM_TYPE)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_EXPECTED_STREAM_TYPE, "KeyedStream")
                    .param(ARG_ACTUAL_STREAM_TYPE, in == null ? "null" : in.getClass().getName());
        }
        if (m.getStrategyRef() == null) {
            throw new StreamException(ERR_STREAM_REQUIRED_ATTR)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_ATTR_NAME, "strategyRef");
        }
        WindowingStrategyModel strategy = owner.model().getStrategy(m.getStrategyRef());
        if (strategy == null) {
            throw new StreamException(ERR_STREAM_REF_UNKNOWN)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_REF_TYPE, "<windowingStrategies>/<strategy>")
                    .param(ARG_REF_NAME, m.getStrategyRef());
        }
        failFastOnUnsupportedWindowStrategy(t, strategy);
        failFastOnUnsupportedWindowNodeAttrs(t, m);
        WindowAssigner assigner = (WindowAssigner) resolveWindowAssigner(owner, strategy);
        KeyedStream keyed = (KeyedStream<T, K>) in;
        return keyed.window(assigner);
    }

    /**
     * P1-XDSL-6: strategy-level windowing attributes that the core {@link WindowedStream}
     * cannot express (no {@code allowedLateness()}/{@code accumulationMode()} API; triggers
     * would require a trigger registry) must fail fast on non-default values instead of
     * being silently ignored. xdef defaults: allowedLateness=0, accumulationMode=DISCARDING,
     * triggerId unset.
     */
    private static void failFastOnUnsupportedWindowStrategy(StreamTransformModel t,
                                                            WindowingStrategyModel strategy) {
        if (strategy.getTriggerId() != null) {
            throw new StreamException(ERR_STREAM_WINDOW_ATTR_UNSUPPORTED)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_ATTR_NAME, "triggerId").param(ARG_ATTR_VALUE, strategy.getTriggerId());
        }
        if (strategy.getAllowedLateness() != XDEF_DEFAULT_ALLOWED_LATENESS) {
            throw new StreamException(ERR_STREAM_WINDOW_ATTR_UNSUPPORTED)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_ATTR_NAME, "allowedLateness")
                    .param(ARG_ATTR_VALUE, strategy.getAllowedLateness());
        }
        if (strategy.getAccumulationMode() != null
                && strategy.getAccumulationMode() != AccumulationMode.DISCARDING) {
            throw new StreamException(ERR_STREAM_WINDOW_ATTR_UNSUPPORTED)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_ATTR_NAME, "accumulationMode")
                    .param(ARG_ATTR_VALUE, strategy.getAccumulationMode());
        }
    }

    /**
     * AR-12 (P2, closed by natural coverage): window node-level {@code allowedLateness}/
     * {@code triggerId} children would also be silently ignored; the same fail-fast
     * mechanism applies (xdef default: allowedLateness=0, triggerId unset).
     */
    private static void failFastOnUnsupportedWindowNodeAttrs(StreamTransformModel t,
                                                             StreamWindowModel m) {
        if (m.getAllowedLateness() != null && m.getAllowedLateness() != XDEF_DEFAULT_ALLOWED_LATENESS) {
            throw new StreamException(ERR_STREAM_WINDOW_ATTR_UNSUPPORTED)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_ATTR_NAME, "allowedLateness").param(ARG_ATTR_VALUE, m.getAllowedLateness());
        }
        if (m.getTriggerId() != null) {
            throw new StreamException(ERR_STREAM_WINDOW_ATTR_UNSUPPORTED)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_ATTR_NAME, "triggerId").param(ARG_ATTR_VALUE, m.getTriggerId());
        }
    }

    private static WindowAssigner<? super Object, ?> resolveWindowAssigner(
            StreamModelDslBuilder owner, WindowingStrategyModel strategy) {
        String windowFnId = strategy.getWindowFnId();
        if (owner.beanResolver().contains(windowFnId)) {
            return owner.beanResolver().resolve(windowFnId, WindowAssigner.class);
        }
        // A small builtin catalog so tests can reference well-known window assigners by id
        // without registering a bean. Production code should register an explicit bean.
        switch (windowFnId) {
            case "tumbling-global":
            case "global":
                return io.nop.stream.core.windowing.assigners.GlobalWindows.create();
            case "tumbling-event-time-1s":
                return io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows.of(1000L);
            case "tumbling-event-time-5s":
                return io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows.of(5000L);
            default:
                throw new StreamException(ERR_STREAM_REF_UNKNOWN)
                        .param(ARG_ELEMENT, "windowingStrategies")
                        .param(ARG_REF_TYPE, "windowFnId").param(ARG_REF_NAME, windowFnId);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T, ACC, R> SingleOutputStreamOperator<R> buildAggregate(
            StreamModelDslBuilder owner, StreamTransformModel t, Set<String> upstreamIds,
            Map<String, Object> streamRegistry, StreamAggregateModel m) {
        Object in = requireSingleInput(upstreamIds, streamRegistry, owner, t);
        if (!(in instanceof WindowedStream)) {
            throw new StreamException(ERR_STREAM_UPSTREAM_TYPE)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_EXPECTED_STREAM_TYPE, "WindowedStream")
                    .param(ARG_ACTUAL_STREAM_TYPE, in == null ? "null" : in.getClass().getName());
        }
        if (m.getBean() == null) {
            throw new StreamException(ERR_STREAM_REQUIRED_ATTR)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_ATTR_NAME, "bean");
        }
        AggregateFunction fn = owner.beanResolver().resolve(m.getBean(), AggregateFunction.class);
        WindowedStream windowed = (WindowedStream) in;
        return (SingleOutputStreamOperator<R>) windowed.aggregate(fn);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> SingleOutputStreamOperator<T> buildReduce(
            StreamModelDslBuilder owner, StreamTransformModel t, Set<String> upstreamIds,
            Map<String, Object> streamRegistry, StreamReduceModel m) {
        Object in = requireSingleInput(upstreamIds, streamRegistry, owner, t);
        ReduceFunction<T> fn;
        if (m.getBean() != null) {
            fn = owner.beanResolver().resolve(m.getBean(), ReduceFunction.class);
        } else if (m.getSource() != null) {
            fn = new XplReduceFunction<>(m.getSource());
        } else {
            throw new StreamException(ERR_STREAM_REQUIRED_BODY)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t));
        }
        if (in instanceof KeyedStream) {
            KeyedStream<T, Object> keyed = (KeyedStream<T, Object>) in;
            return (SingleOutputStreamOperator<T>) keyed.reduce((ReduceFunction) fn);
        }
        if (in instanceof WindowedStream) {
            WindowedStream windowed = (WindowedStream) in;
            return (SingleOutputStreamOperator<T>) windowed.reduce((ReduceFunction) fn);
        }
        throw new StreamException(ERR_STREAM_UPSTREAM_TYPE)
                .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                .param(ARG_EXPECTED_STREAM_TYPE, "KeyedStream or WindowedStream")
                .param(ARG_ACTUAL_STREAM_TYPE, in == null ? "null" : in.getClass().getName());
    }

    // ----------------------------------------------------------------
    // process
    // ----------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T, R> SingleOutputStreamOperator<R> buildProcess(
            StreamModelDslBuilder owner, StreamTransformModel t, Set<String> upstreamIds,
            Map<String, Object> streamRegistry, StreamProcessModel m) {
        if (m.getBean() == null) {
            throw new StreamException(ERR_STREAM_REQUIRED_ATTR)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_ATTR_NAME, "bean");
        }
        Object in = requireSingleInput(upstreamIds, streamRegistry, owner, t);
        if (in instanceof KeyedStream) {
            KeyedProcessFunction<Object, T, R> fn = owner.beanResolver()
                    .resolve(m.getBean(), KeyedProcessFunction.class);
            KeyedStream<T, Object> keyed = (KeyedStream<T, Object>) in;
            return (SingleOutputStreamOperator<R>) keyed.process((KeyedProcessFunction) fn);
        }
        if (in instanceof DataStream) {
            ProcessFunction<T, R> fn = owner.beanResolver().resolve(m.getBean(), ProcessFunction.class);
            DataStream<T> dataStream = (DataStream<T>) in;
            return (SingleOutputStreamOperator<R>) dataStream.process((ProcessFunction) fn);
        }
        throw new StreamException(ERR_STREAM_UPSTREAM_TYPE)
                .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                .param(ARG_EXPECTED_STREAM_TYPE, "DataStream or KeyedStream")
                .param(ARG_ACTUAL_STREAM_TYPE, in == null ? "null" : in.getClass().getName());
    }

    // ----------------------------------------------------------------
    // union — runtime API gap (DataStream.union does not exist)
    // ----------------------------------------------------------------

    private static Object buildUnion(StreamTransformModel t, Set<String> upstreamIds) {
        throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                .param(ARG_DETAIL, "Stream DSL <union id='" + t.getId()
                        + "'> requires DataStream.union() which is not yet implemented in "
                        + "nop-stream-core runtime. Union is a multi-input operator and the "
                        + "runtime only supports OneInputStreamOperator. Tracked as a "
                        + "runtime-API-gap follow-up.");
    }

    // ----------------------------------------------------------------
    // custom
    // ----------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T, R> SingleOutputStreamOperator<R> buildCustom(
            StreamModelDslBuilder owner, StreamTransformModel t, Set<String> upstreamIds,
            Map<String, Object> streamRegistry, StreamCustomModel m) {
        if (m.getCustomType() == null) {
            throw new StreamException(ERR_STREAM_REQUIRED_ATTR)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_ATTR_NAME, "customType");
        }
        Object in = requireSingleInput(upstreamIds, streamRegistry, owner, t);
        if (!(in instanceof DataStream)) {
            throw new StreamException(ERR_STREAM_UPSTREAM_TYPE)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_EXPECTED_STREAM_TYPE, "DataStream")
                    .param(ARG_ACTUAL_STREAM_TYPE, in == null ? "null" : in.getClass().getName());
        }
        OneInputStreamOperator<T, R> operator = owner.beanResolver()
                .resolve(m.getCustomType(), OneInputStreamOperator.class);
        DataStream<T> dataStream = (DataStream<T>) in;
        TypeInformation<R> outType = (TypeInformation<R>) UnknownTypeInformation.INSTANCE;
        return (SingleOutputStreamOperator<R>) dataStream.transform(
                "Custom:" + m.getCustomType(), outType, (OneInputStreamOperator) operator);
    }

    // ----------------------------------------------------------------
    // CEP
    // ----------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T, K, R> SingleOutputStreamOperator<R> buildCep(
            StreamModelDslBuilder owner, StreamTransformModel t, Set<String> upstreamIds,
            Map<String, Object> streamRegistry, StreamCepModel m) {
        Object in = requireSingleInput(upstreamIds, streamRegistry, owner, t);
        if (!(in instanceof KeyedStream)) {
            throw new StreamException(ERR_STREAM_UPSTREAM_TYPE)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_EXPECTED_STREAM_TYPE, "KeyedStream")
                    .param(ARG_ACTUAL_STREAM_TYPE, in == null ? "null" : in.getClass().getName());
        }
        if (m.getPatternRef() == null) {
            throw new StreamException(ERR_STREAM_REQUIRED_ATTR)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_ATTR_NAME, "patternRef");
        }
        CepPatternModel patternModel = owner.model().getPattern(m.getPatternRef());
        if (patternModel == null) {
            throw new StreamException(ERR_STREAM_REF_UNKNOWN)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_REF_TYPE, "<patterns>/<pattern>").param(ARG_REF_NAME, m.getPatternRef());
        }
        Pattern<T, ?> pattern = (Pattern<T, ?>) new CepPatternBuilder().buildFromModel(patternModel);
        KeyedStream<T, K> keyed = (KeyedStream<T, K>) in;
        PatternStream<T> patternStream = CEP.pattern((DataStream) keyed, (Pattern) pattern);
        if (m.getBean() == null) {
            throw new StreamException(ERR_STREAM_REQUIRED_ATTR)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_ATTR_NAME, "bean");
        }
        PatternProcessFunction<T, R> selectFn = owner.beanResolver()
                .resolve(m.getBean(), PatternProcessFunction.class);
        TypeInformation<R> outType = (TypeInformation<R>) UnknownTypeInformation.INSTANCE;
        return (SingleOutputStreamOperator<R>) patternStream.process(
                (PatternProcessFunction) selectFn, outType);
    }

    // ----------------------------------------------------------------
    // sideOutput — runtime API gap (SingleOutputStreamOperator.getSideOutput
    // does not exist; ProcessFunction.Context.output exists for emitting but
    // there's no way to retrieve the side-output stream)
    // ----------------------------------------------------------------

    private static Object buildSideOutput(StreamSideOutputModel m) {
        throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                .param(ARG_DETAIL, "Stream DSL <sideOutput id='" + m.getId() + "' tag='" + m.getTag()
                        + "'> requires SingleOutputStreamOperator.getSideOutput(OutputTag) which is "
                        + "not yet implemented in nop-stream-core runtime. ProcessFunction can emit to "
                        + "an OutputTag but there is no API to retrieve the emitted stream. Tracked as "
                        + "a runtime-API-gap follow-up.");
    }

    // ----------------------------------------------------------------
    // timestampsAndWatermarks
    // ----------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> SingleOutputStreamOperator<T> buildTimestampsAndWatermarks(
            StreamModelDslBuilder owner, StreamTransformModel t, Set<String> upstreamIds,
            Map<String, Object> streamRegistry, StreamTimestampsAndWatermarksModel m) {
        Object in = requireSingleInput(upstreamIds, streamRegistry, owner, t);
        if (!(in instanceof DataStream)) {
            throw new StreamException(ERR_STREAM_UPSTREAM_TYPE)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_EXPECTED_STREAM_TYPE, "DataStream")
                    .param(ARG_ACTUAL_STREAM_TYPE, in == null ? "null" : in.getClass().getName());
        }
        WatermarkStrategy<T> strategy;
        if (m.getWatermarkStrategyBean() != null) {
            strategy = owner.beanResolver()
                    .resolve(m.getWatermarkStrategyBean(), WatermarkStrategy.class);
        } else {
            strategy = new XplWatermarkStrategy<>(m.getTimestampAssigner(), m.getWatermarkGenerator());
        }
        DataStream<T> dataStream = (DataStream<T>) in;
        return (SingleOutputStreamOperator<T>) dataStream.assignTimestampsAndWatermarks((WatermarkStrategy) strategy);
    }

    // ----------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------

    private static DataStream<?> requireSingleInput(Set<String> upstreamIds,
                                                    Map<String, Object> streamRegistry,
                                                    StreamModelDslBuilder owner,
                                                    StreamTransformModel t) {
        if (upstreamIds.size() != 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "upstream edges")
                    .param(ARG_DETAIL, "exactly one upstream edge required, found " + upstreamIds.size());
        }
        String upstream = upstreamIds.iterator().next();
        Object in = streamRegistry.get(upstream);
        if (in == null) {
            throw new StreamException(ERR_STREAM_UPSTREAM_NULL)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t));
        }
        if (!(in instanceof DataStream) && !(in instanceof KeyedStream) && !(in instanceof WindowedStream)) {
            throw new StreamException(ERR_STREAM_UPSTREAM_NOT_STREAM)
                    .param(ARG_ELEMENT, StreamModelDslBuilder.elementDesc(t))
                    .param(ARG_ACTUAL_STREAM_TYPE, in.getClass().getName());
        }
        // P1-XDSL-5: apply the declared edge partition (HASH + keyExpr) to the input stream.
        return owner.applyEdgePartition((DataStream<?>) in, upstream, t.getId());
    }

    /**
     * {@link WatermarkStrategy} backed by parsed xpl bodies for the timestamp assigner and
     * watermark generator. Either may be {@code null} (falls back to a no-op).
     */
    private static final class XplWatermarkStrategy<T> implements WatermarkStrategy<T> {

        private static final long serialVersionUID = 1L;

        private final io.nop.core.lang.eval.IEvalFunction timestampAssignerBody;
        private final io.nop.core.lang.eval.IEvalFunction watermarkGeneratorBody;

        XplWatermarkStrategy(io.nop.core.lang.eval.IEvalFunction timestampAssignerBody,
                             io.nop.core.lang.eval.IEvalFunction watermarkGeneratorBody) {
            this.timestampAssignerBody = timestampAssignerBody;
            this.watermarkGeneratorBody = watermarkGeneratorBody;
        }

        @Override
        public TimestampAssigner<T> createTimestampAssigner(TimestampAssignerSupplier.Context context) {
            if (timestampAssignerBody == null) {
                return null;
            }
            return (event, recordTimestamp) -> io.nop.api.core.convert.ConvertHelper.toLong(
                    timestampAssignerBody.call2(null, event, recordTimestamp,
                            io.nop.stream.flow.builder.functions.XplFunctionSupport.newCallScope()));
        }

        @Override
        public WatermarkGenerator<T> createWatermarkGenerator(WatermarkGeneratorSupplier.Context context) {
            if (watermarkGeneratorBody == null) {
                return new NoOpWatermarkGenerator<>();
            }
            return new XplWatermarkGenerator<>(watermarkGeneratorBody);
        }
    }

    private static final class NoOpWatermarkGenerator<T> implements WatermarkGenerator<T> {
        @Override
        public void onEvent(T event, long eventTimestamp, WatermarkOutput output) {
        }

        @Override
        public void onPeriodicEmit(WatermarkOutput output) {
        }
    }

    private static final class XplWatermarkGenerator<T> implements WatermarkGenerator<T> {

        private final io.nop.core.lang.eval.IEvalFunction body;

        XplWatermarkGenerator(io.nop.core.lang.eval.IEvalFunction body) {
            this.body = body;
        }

        @Override
        public void onEvent(T event, long eventTimestamp, WatermarkOutput output) {
            body.call3(null, event, eventTimestamp, output,
                    io.nop.stream.flow.builder.functions.XplFunctionSupport.newCallScope());
        }

        @Override
        public void onPeriodicEmit(WatermarkOutput output) {
        }
    }
}
