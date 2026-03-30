/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

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

    public ChainingOutput(Input<T> input) {
        this.input = input;
    }

    @Override
    public void collect(StreamRecord<T> record) {
        try {
            input.processElement(record);
        } catch (Exception e) {
            throw new RuntimeException("Error forwarding element to next operator", e);
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
            throw new RuntimeException("Error forwarding watermark", e);
        }
    }

    @Override
    public void emitWatermarkStatus(WatermarkStatus watermarkStatus) {
        try {
            input.processWatermarkStatus(watermarkStatus);
        } catch (Exception e) {
            throw new RuntimeException("Error forwarding watermark status", e);
        }
    }

    @Override
    public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        // Side outputs not supported in simplified execution
    }

    @Override
    public void emitLatencyMarker(LatencyMarker latencyMarker) {
        try {
            input.processLatencyMarker(latencyMarker);
        } catch (Exception e) {
            throw new RuntimeException("Error forwarding latency marker", e);
        }
    }
}
