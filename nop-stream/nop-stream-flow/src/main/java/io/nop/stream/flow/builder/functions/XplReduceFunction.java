/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder.functions;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import io.nop.stream.core.common.functions.ReduceFunction;

/**
 * Adapts a parsed {@code <reduce><source>xpl-fn:(a,b)=>any</source></reduce>}
 * body to a {@link ReduceFunction}. The xpl body receives the two values to combine.
 */
public final class XplReduceFunction<T> implements ReduceFunction<T> {

    private static final long serialVersionUID = 1L;

    private final IEvalFunction body;

    public XplReduceFunction(IEvalFunction body) {
        if (body == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "xpl ReduceFunction body");
        }
        this.body = body;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T reduce(T value1, T value2) {
        Object result = body.call2(null, value1, value2, XplFunctionSupport.newCallScope());
        return (T) result;
    }
}
