/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;

/**
 * Wraps a downstream {@link Input} so that before each element is forwarded the
 * key is extracted and set on the target operator's {@link KeyContext}.
 *
 * <p>This enables per-key state isolation in single-threaded (parallelism=1)
 * execution: the downstream operator's {@code keyedStateBackend} will see the
 * correct current key when reading/writing state.
 */
public class KeyExtractingOutput<IN> implements Input<IN> {

    private final Input<IN> delegate;
    private final KeySelector<IN, ?> keySelector;
    private final KeyContext keyContext;

    public KeyExtractingOutput(Input<IN> delegate,
                               KeySelector<IN, ?> keySelector,
                               KeyContext keyContext) {
        this.delegate = delegate;
        this.keySelector = keySelector;
        this.keyContext = keyContext;
    }

    @Override
    public void processElement(StreamRecord<IN> record) throws Exception {
        Object key = keySelector.getKey(record.getValue());
        keyContext.setCurrentKey(key);
        delegate.processElement(record);
    }

    @Override
    public void processWatermark(Watermark mark) throws Exception {
        delegate.processWatermark(mark);
    }

    @Override
    public void processWatermarkStatus(WatermarkStatus watermarkStatus) throws Exception {
        delegate.processWatermarkStatus(watermarkStatus);
    }

    @Override
    public void processLatencyMarker(LatencyMarker latencyMarker) throws Exception {
        delegate.processLatencyMarker(latencyMarker);
    }

    @Override
    public void setKeyContextElement(StreamRecord<IN> record) throws Exception {
        Object key = keySelector.getKey(record.getValue());
        keyContext.setCurrentKey(key);
    }
}
