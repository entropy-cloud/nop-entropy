/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder.functions;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.stream.core.common.functions.FilterFunction;

/**
 * Adapts a parsed {@code <filter><source>xpl-fn:(event)=>boolean</source></filter>}
 * body to a {@link FilterFunction}. The return value is converted to a truthy
 * boolean; {@code null} is treated as {@code false}.
 */
public final class XplFilterFunction<T> implements FilterFunction<T> {

    private static final long serialVersionUID = 1L;

    private final IEvalFunction body;

    public XplFilterFunction(IEvalFunction body) {
        if (body == null) {
            throw new IllegalArgumentException("XplFilterFunction body must not be null");
        }
        this.body = body;
    }

    @Override
    public boolean filter(T value) {
        Object result = body.call1(null, value, XplFunctionSupport.newCallScope());
        return XplFunctionSupport.toBoolean(result);
    }
}
