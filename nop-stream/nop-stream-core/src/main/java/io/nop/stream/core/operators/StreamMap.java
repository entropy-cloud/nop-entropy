/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.streamrecord.StreamRecord;

/**
 * A stream operator that applies a map function to each element in the stream.
 * The map function transforms each input element into exactly one output element.
 * 
 * @param <IN> the type of input elements
 * @param <OUT> the type of output elements
 */
public class StreamMap<IN, OUT> extends AbstractUdfStreamOperator<OUT, MapFunction<IN, OUT>> 
        implements OneInputStreamOperator<IN, OUT> {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Creates a new StreamMap operator with the specified map function.
     * 
     * @param mapper the map function to apply to each element
     */
    public StreamMap(MapFunction<IN, OUT> mapper) {
        super(mapper);
    }
    
    @Override
    public void processElement(StreamRecord<IN> element) throws Exception {
        OUT result = userFunction.map(element.getValue());
        output.collect(new StreamRecord<>(result, element.getTimestamp()));
    }
}
