/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder.functions;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.stream.core.common.functions.FlatMapFunction;
import io.nop.stream.core.util.Collector;

/**
 * Adapts a parsed {@code <flatMap><source>xpl-fn:(event,out)=>void</source></flatMap>}
 * body to a {@link FlatMapFunction}. The xpl body receives {@code event} and the
 * runtime {@link Collector} as its two arguments, so an inline xpl snippet can call
 * {@code out.collect(...)} for each emitted element.
 */
public final class XplFlatMapFunction<T, R> implements FlatMapFunction<T, R> {

    private static final long serialVersionUID = 1L;

    private final IEvalFunction body;

    public XplFlatMapFunction(IEvalFunction body) {
        if (body == null) {
            throw new IllegalArgumentException("XplFlatMapFunction body must not be null");
        }
        this.body = body;
    }

    @Override
    public void flatMap(T value, Collector<R> out) {
        IEvalScope scope = XplFunctionSupport.newCallScope();
        body.call2(null, value, out, scope);
    }
}
