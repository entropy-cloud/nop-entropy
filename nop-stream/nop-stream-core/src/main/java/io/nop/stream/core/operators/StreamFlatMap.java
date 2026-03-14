/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.common.functions.FlatMapFunction;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.util.Collector;

/**
 * A stream operator that applies a flat map function to each element in the stream.
 * The flat map function can transform each input element into zero, one, or more output elements.
 * 
 * @param <IN> the type of input elements
 * @param <OUT> the type of output elements
 */
public class StreamFlatMap<IN, OUT> extends AbstractUdfStreamOperator<OUT, FlatMapFunction<IN, OUT>> 
        implements OneInputStreamOperator<IN, OUT> {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Creates a new StreamFlatMap operator with the specified flat map function.
     * 
     * @param flatMapper the flat map function to apply to each element
     */
    public StreamFlatMap(FlatMapFunction<IN, OUT> flatMapper) {
        super(flatMapper);
    }
    
    @Override
    public void processElement(StreamRecord<IN> element) throws Exception {
        userFunction.flatMap(element.getValue(), new Collector<OUT>() {
            @Override
            public void collect(OUT record) {
                output.collect(new StreamRecord<>(record, element.getTimestamp()));
            }
            
            @Override
            public void close() {
                // Nothing to close for this simple collector
            }
        });
    }
}
