/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder.functions;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.stream.core.common.functions.SinkFunction;

/**
 * Adapts a parsed {@code <sink><source>xpl-fn:(event)=>void</source></sink>} body to a
 * {@link SinkFunction}. The xpl body is invoked for every consumed element; any return
 * value is discarded.
 */
public final class XplSinkFunction<T> implements SinkFunction<T> {

    private static final long serialVersionUID = 1L;

    private final IEvalFunction body;

    public XplSinkFunction(IEvalFunction body) {
        if (body == null) {
            throw new IllegalArgumentException("XplSinkFunction body must not be null");
        }
        this.body = body;
    }

    @Override
    public void consume(T value) {
        body.call1(null, value, XplFunctionSupport.newCallScope());
    }
}
