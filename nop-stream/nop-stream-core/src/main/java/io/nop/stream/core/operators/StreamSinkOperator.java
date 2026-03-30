/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.streamrecord.StreamRecord;

/**
 * A stream operator that wraps a {@link SinkFunction} and consumes elements from the stream.
 * This is a terminal operator — it has no output and simply passes each element to the
 * user-supplied sink function.
 *
 * @param <IN> The type of elements consumed by this sink
 */
public class StreamSinkOperator<IN> extends AbstractUdfStreamOperator<Void, SinkFunction<IN>>
        implements OneInputStreamOperator<IN, Void> {

    private static final long serialVersionUID = 1L;

    public StreamSinkOperator(SinkFunction<IN> sinkFunction) {
        super(sinkFunction);
    }

    @Override
    public void processElement(StreamRecord<IN> element) throws Exception {
        userFunction.consume(element.getValue());
    }
}
