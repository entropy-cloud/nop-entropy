/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.common.functions.FilterFunction;
import io.nop.stream.core.common.functions.FlatMapFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.core.util.OutputTag;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_OPERATION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_UNSUPPORTED;

/**
 * G-2+09e① (plan 369 Phase 2): a live view of one side output of a producing operator,
 * returned by {@link SingleOutputStreamOperator#getSideOutput(OutputTag)}.
 *
 * <p>Consumption registers a consumer for the view's tag in the
 * {@link SideOutputRegistry}: the runtime output protocol ({@code ChainingOutput} for
 * chained execution, {@code StreamTaskInvokable} for cross-task routing) consults the
 * registry when a tagged {@code collect(OutputTag, record)} arrives, so the records of
 * this side output are delivered to the registered sink. Re-registering for the same tag
 * replaces the previous consumer ({@code OutputTag} equality is id-based).
 *
 * <p>Graph-level downstream transformations ({@code map}, {@code filter}, {@code keyBy},
 * ...) on a side-output view are NOT deployable — a side-output edge would need its own
 * routing in the execution graph. Those operations fail fast with
 * {@code ERR_STREAM_UNSUPPORTED} instead of silently attaching to the producing
 * operator's MAIN output.
 *
 * @param <T> the type of the elements in the side output
 */
public class SideOutputDataStream<T> extends DataStreamImpl<T> {

    private final OutputTag<T> outputTag;

    @SuppressWarnings("unchecked")
    public SideOutputDataStream(StreamExecutionEnvironment environment,
                                Transformation<?> sourceTransformation,
                                OutputTag<T> outputTag) {
        super(environment, (Transformation<T>) sourceTransformation);
        if (outputTag == null) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "outputTag")
                    .param(ARG_DETAIL, "getSideOutput requires a non-null OutputTag");
        }
        this.outputTag = outputTag;
    }

    /** The tag identifying the side output this view consumes. */
    public OutputTag<T> getOutputTag() {
        return outputTag;
    }

    @Override
    public void sink(SinkFunction<T> sinkFunction) {
        sink(sinkFunction, 1);
    }

    /**
     * Registers the sink as this side output's consumer in the {@link SideOutputRegistry}.
     * The parallelism parameter is accepted for API symmetry with
     * {@link DataStream#sink(SinkFunction, int)} but has no effect: a side output is
     * delivered where it is produced, not through a deployed sink vertex.
     */
    @Override
    public void sink(SinkFunction<T> sinkFunction, int parallelism) {
        if (sinkFunction == null) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "sinkFunction")
                    .param(ARG_DETAIL, "Side output sink must not be null");
        }
        SideOutputRegistry.register(outputTag.getId(), record -> {
            try {
                @SuppressWarnings("unchecked")
                StreamRecord<T> typed = (StreamRecord<T>) record;
                sinkFunction.consume(typed.getValue());
            } catch (Exception e) {
                throw new StreamException(ERR_STREAM_UNSUPPORTED, e)
                        .param(ARG_OPERATION, "SideOutputDataStream.sink")
                        .param(ARG_DETAIL, "Side output sink for tag '" + outputTag.getId()
                                + "' failed while consuming a record");
            }
        });
    }

    private io.nop.api.core.exceptions.NopException unsupported(String operation) {
        return new StreamException(ERR_STREAM_UNSUPPORTED)
                .param(ARG_OPERATION, operation)
                .param(ARG_DETAIL, "Downstream graph transformations on a side-output view"
                        + " (getSideOutput) are not deployable: side-output edges have no"
                        + " graph routing yet. Consume the side output with sink(...),"
                        + " print() or collect(...) instead.");
    }

    @Override
    public DataStream<T> setParallelism(int parallelism) {
        throw unsupported("SideOutputDataStream.setParallelism");
    }

    @Override
    public <K> KeyedStream<T, K> keyBy(KeySelector<T, K> key) {
        throw unsupported("SideOutputDataStream.keyBy");
    }

    @Override
    public <R> SingleOutputStreamOperator<R> map(MapFunction<T, R> mapper) {
        throw unsupported("SideOutputDataStream.map");
    }

    @Override
    public SingleOutputStreamOperator<T> filter(FilterFunction<T> filter) {
        throw unsupported("SideOutputDataStream.filter");
    }

    @Override
    public <R> SingleOutputStreamOperator<R> flatMap(FlatMapFunction<T, R> flatMapper) {
        throw unsupported("SideOutputDataStream.flatMap");
    }

    @Override
    public <R> SingleOutputStreamOperator<R> process(ProcessFunction<T, R> processFunction) {
        throw unsupported("SideOutputDataStream.process");
    }

    @Override
    public SingleOutputStreamOperator<T> assignTimestampsAndWatermarks(WatermarkStrategy<T> strategy) {
        throw unsupported("SideOutputDataStream.assignTimestampsAndWatermarks");
    }

    @Override
    public SingleOutputStreamOperator<T> assignTimestampsAndWatermarks(
            WatermarkStrategy<T> strategy, long watermarkInterval) {
        throw unsupported("SideOutputDataStream.assignTimestampsAndWatermarks");
    }

    @Override
    public <R> SingleOutputStreamOperator<R> transform(String operatorName,
                                                       TypeInformation<R> outTypeInfo,
                                                       OneInputStreamOperator<T, R> operator) {
        throw unsupported("SideOutputDataStream.transform");
    }
}
