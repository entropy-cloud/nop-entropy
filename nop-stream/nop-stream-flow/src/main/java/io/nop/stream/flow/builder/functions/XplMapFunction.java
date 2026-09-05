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
import io.nop.stream.core.common.functions.MapFunction;

/**
 * Adapts a parsed {@code <map><source>xpl-fn:(event)=>any</source></map>} body to a
 * {@link MapFunction}. The xpl body receives {@code event} as its single argument.
 */
public final class XplMapFunction<T, R> implements MapFunction<T, R> {

    private static final long serialVersionUID = 1L;

    private final IEvalFunction body;

    public XplMapFunction(IEvalFunction body) {
        if (body == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "xpl MapFunction body");
        }
        this.body = body;
    }

    @Override
    @SuppressWarnings("unchecked")
    public R map(T value) {
        Object result = body.call1(null, value, XplFunctionSupport.newCallScope());
        return (R) result;
    }
}
