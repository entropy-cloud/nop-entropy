/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.flow.builder.functions.XplFunctionSupport;

/**
 * {@link KeySelector} backed by a parsed {@code <keyBy keyExpr="..."/>} expression
 * ({@link IEvalAction}). Each invocation runs the expression in a fresh child scope
 * with {@code event} bound to the input element. Returned {@code null} keys are passed
 * through unchanged (the runtime partitioner routes them to partition 0).
 */
public final class EvalActionKeySelector<T, K> implements KeySelector<T, K> {

    private static final long serialVersionUID = 1L;

    private final IEvalAction keyExpr;

    public EvalActionKeySelector(IEvalAction keyExpr) {
        if (keyExpr == null) {
            throw new IllegalArgumentException("EvalActionKeySelector keyExpr must not be null");
        }
        this.keyExpr = keyExpr;
    }

    @Override
    @SuppressWarnings("unchecked")
    public K getKey(T value) {
        return (K) keyExpr.invoke(XplFunctionSupport.newCallScope()
                .newChildScope(java.util.Collections.singletonMap("event", value)));
    }
}
