/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.exceptions.StreamRuntimeException;
import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_OPERATOR_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_OUTPUT_TAG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHAINING_OUTPUT_EXCEPTION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.OutputTag;

/**
 * An {@link Output} implementation that forwards collected records to the next operator's
 * {@link Input#processElement} method. This is used to chain operators together in a
 * single-threaded execution pipeline.
 *
 * @param <T> The type of elements flowing through this output
 */
public class ChainingOutput<T> implements Output<StreamRecord<T>> {

    private final Input<T> input;
    private final String operatorName;
    /**
     * RL-7 (R15-AR-4): registered side-output consumers, shared across all ChainingOutputs of a
     * chained task (see {@code StreamTaskInvokable#registerSideOutputConsumer}). A side output
     * with no registered consumer fails fast — side outputs are never silently dropped.
     */
    private final Map<OutputTag<?>, Consumer<StreamRecord<?>>> sideOutputConsumers;

    /**
     * W-07 (CRA 8.42 R4-02, ruling C.2): the execution-context-local registry
     * carried from the owning invokable (nullable — the remote/分进程 build path
     * leaves it null, and its miss answers the existing NO_CONSUMER fail-fast).
     * Non-final: the invokable's {@code setSideOutputRegistry} injection point
     * propagates the env registry after wiring.
     */
    private volatile io.nop.stream.core.datastream.SideOutputRegistry sideOutputRegistry;

    public ChainingOutput(Input<T> input) {
        this(input, null);
    }

    public ChainingOutput(Input<T> input, String operatorName) {
        this(input, operatorName, new HashMap<>());
    }

    public ChainingOutput(Input<T> input, String operatorName,
                          Map<OutputTag<?>, Consumer<StreamRecord<?>>> sideOutputConsumers) {
        this(input, operatorName, sideOutputConsumers, null);
    }

    /**
     * W-07 (CRA 8.42 R4-02, ruling C.2): full form carrying the
     * execution-context-local side-output registry (the owning env's instance).
     * Inert until the fallback lookup below migrates off the static map.
     */
    public ChainingOutput(Input<T> input, String operatorName,
                          Map<OutputTag<?>, Consumer<StreamRecord<?>>> sideOutputConsumers,
                          io.nop.stream.core.datastream.SideOutputRegistry sideOutputRegistry) {
        this.input = input;
        this.operatorName = operatorName;
        this.sideOutputConsumers = sideOutputConsumers != null
                ? sideOutputConsumers : new HashMap<>();
        this.sideOutputRegistry = sideOutputRegistry;
    }

    /**
     * RL-7 (R15-AR-4): registers a consumer for the given side-output tag. When the map is
     * shared with {@code StreamTaskInvokable}, registration may happen before or after wiring.
     */
    @SuppressWarnings("unchecked")
    public <X> void registerSideOutputConsumer(OutputTag<X> outputTag, Consumer<StreamRecord<X>> consumer) {
        sideOutputConsumers.put(outputTag, (Consumer<StreamRecord<?>>) (Consumer<?>) consumer);
    }

    /**
     * W-07 (CRA 8.42 R4-02, ruling C.2): injects the owning execution's
     * side-output registry. Called only by the owning {@code StreamTaskInvokable}
     * (which tracks the ChainingOutputs it wired) — public because the invokable
     * lives in another package (both are module-internal impl leaves).
     */
    public void setSideOutputRegistry(io.nop.stream.core.datastream.SideOutputRegistry registry) {
        // ChainingOutput's collect is invoked on the task thread while the
        // injection happens on the plan-build/supervision thread; a plain field
        // write of a reference is safe (no compound state).
        this.sideOutputRegistry = registry;
    }

    @Override
    public void collect(StreamRecord<T> record) {
        try {
            input.processElement(record);
        } catch (Exception e) {
            throw new StreamRuntimeException(ERR_STREAM_CHAINING_OUTPUT_EXCEPTION, e)
                    .param(ARG_DETAIL, "Error forwarding element to next operator")
                    .param(ARG_OPERATOR_NAME, operatorName != null ? operatorName : "unknown");
        }
    }

    @Override
    public void close() {
        // No-op for chaining
    }

    @Override
    public void emitWatermark(Watermark mark) {
        try {
            input.processWatermark(mark);
        } catch (Exception e) {
            throw new StreamRuntimeException(ERR_STREAM_CHAINING_OUTPUT_EXCEPTION, e)
                    .param(ARG_DETAIL, "Error forwarding watermark")
                    .param(ARG_OPERATOR_NAME, operatorName != null ? operatorName : "unknown");
        }
    }

    @Override
    public void emitWatermarkStatus(WatermarkStatus watermarkStatus) {
        try {
            input.processWatermarkStatus(watermarkStatus);
        } catch (Exception e) {
            throw new StreamRuntimeException(ERR_STREAM_CHAINING_OUTPUT_EXCEPTION, e)
                    .param(ARG_DETAIL, "Error forwarding watermark status")
                    .param(ARG_OPERATOR_NAME, operatorName != null ? operatorName : "unknown");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        // RL-7 (R15-AR-4): forward to the registered side-output consumer instead of silently
        // discarding (the pre-I4 behavior was LOG.warn + drop). No consumer => fail fast
        // (plan guide #24) — a task that emits side outputs without wiring a consumer is
        // misconfigured and must not lose records silently.
        Consumer<StreamRecord<X>> consumer =
                (Consumer<StreamRecord<X>>) (Consumer<?>) sideOutputConsumers.get(outputTag);
        if (consumer == null) {
            // W-07 (CRA 8.42 R4-02, ruling C.2): fall back to the execution-context-local
            // registry carried from the owning env (a consumer registered at assembly via
            // getSideOutput(tag).sink(...) landed in THAT env's instance). A null registry
            // (remote/分进程 build path) misses here and answers the existing
            // NO_CONSUMER fail-fast — the split-process semantics, now explicit for
            // the embedded same-JVM remote form too (adjudication C.2/F-3 tightening).
            consumer = (Consumer<StreamRecord<X>>) (Consumer<?>)
                    (sideOutputRegistry != null ? sideOutputRegistry.get(outputTag.getId()) : null);
        }
        if (consumer == null) {
            throw new StreamRuntimeException(ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER)
                    .param(ARG_OUTPUT_TAG, outputTag.getId())
                    .param(ARG_DETAIL, "Side output '" + outputTag.getId()
                            + "' has no registered consumer in the chained execution; register one via "
                            + "ChainingOutput.registerSideOutputConsumer / StreamTaskInvokable.registerSideOutputConsumer");
        }
        consumer.accept(record);
    }

    @Override
    public void emitLatencyMarker(LatencyMarker latencyMarker) {
        try {
            input.processLatencyMarker(latencyMarker);
        } catch (Exception e) {
            throw new StreamRuntimeException(ERR_STREAM_CHAINING_OUTPUT_EXCEPTION, e)
                    .param(ARG_DETAIL, "Error forwarding latency marker")
                    .param(ARG_OPERATOR_NAME, operatorName != null ? operatorName : "unknown");
        }
    }

    @Override
    public void emitBarrier(CheckpointBarrier barrier) {
        try {
            input.processBarrier(barrier);
        } catch (Exception e) {
            throw new StreamRuntimeException(ERR_STREAM_CHAINING_OUTPUT_EXCEPTION, e)
                    .param(ARG_DETAIL, "Error forwarding barrier to next operator")
                    .param(ARG_OPERATOR_NAME, operatorName != null ? operatorName : "unknown");
        }
    }
}
