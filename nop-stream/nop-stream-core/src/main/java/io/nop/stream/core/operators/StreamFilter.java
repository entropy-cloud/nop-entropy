/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.common.functions.FilterFunction;
import io.nop.stream.core.streamrecord.StreamRecord;

/**
 * A stream operator that applies a filter function to each element in the stream.
 * The filter function decides whether to keep or discard each element.
 * 
 * @param <T> the type of elements being filtered
 */
public class StreamFilter<T> extends AbstractUdfStreamOperator<T, FilterFunction<T>> 
        implements OneInputStreamOperator<T, T> {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Creates a new StreamFilter operator with the specified filter function.
     * 
     * @param filter the filter function to apply to each element
     */
    public StreamFilter(FilterFunction<T> filter) {
        super(filter);
    }
    
    @Override
    public void processElement(StreamRecord<T> element) throws Exception {
        if (userFunction.filter(element.getValue())) {
            output.collect(element);
        }
    }
}
