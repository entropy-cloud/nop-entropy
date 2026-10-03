/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;

/**
 * Pass-through operator backing the {@code UnionTransformation} vertex (WI6): every
 * element arriving on any channel of the vertex's input gate is forwarded unchanged.
 *
 * <p>Record provenance is intentionally dropped — union semantics does not preserve
 * which input a record came from (joins that need per-input differentiation build on
 * {@code union → keyBy → process}, not on this operator). Watermarks and barriers are
 * also forwarded: watermark min-merge across channels already happened in the
 * {@code InputGate}, and barrier alignment is driven by the gate before this operator
 * sees the barrier.
 *
 * <p>Stateless and side-effect free: subtasks share one instance via
 * {@link #copyForSubtask()}.
 *
 * @param <T> the element type
 */
public class StreamUnionOperator<T> extends AbstractStreamOperator<T>
        implements OneInputStreamOperator<T, T> {

    private static final long serialVersionUID = 1L;

    @Override
    public StreamUnionOperator<T> copyForSubtask() {
        return this;
    }

    @Override
    public boolean isShareable() {
        return true;
    }

    @Override
    public void processElement(StreamRecord<T> element) throws Exception {
        output.collect(element);
    }

    @Override
    public void processWatermark(Watermark mark) throws Exception {
        output.emitWatermark(mark);
    }

    @Override
    public void processWatermarkStatus(WatermarkStatus watermarkStatus) throws Exception {
        output.emitWatermarkStatus(watermarkStatus);
    }

    // processBarrier: inherited from AbstractStreamOperator — it runs the (no-op for
    // this stateless operator) snapshot protocol and forwards the barrier downstream.
}
