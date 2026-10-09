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
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
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

    /**
     * WI11 (plan 2357): static per-invocation bindings (the declarative side-input
     * snapshots, bound under {@code sideInputs}); {@code null} = no extra bindings.
     */
    private final java.util.Map<String, Object> bindings;

    public XplFlatMapFunction(IEvalFunction body) {
        this(body, null);
    }

    public XplFlatMapFunction(IEvalFunction body, java.util.Map<String, Object> bindings) {
        if (body == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "xpl FlatMapFunction body");
        }
        this.body = body;
        this.bindings = bindings;
    }

    @Override
    public void flatMap(T value, Collector<R> out) {
        IEvalScope scope = XplFunctionSupport.newCallScope(bindings);
        body.call2(null, value, out, scope);
    }
}
