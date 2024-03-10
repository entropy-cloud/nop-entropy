/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval.functions;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

import java.util.function.Function;

import static io.nop.api.core.util.Guard.notNull;

public class ThenEvalFunction implements IEvalFunction {
    private final IEvalFunction fn;
    private final Function then;

    public ThenEvalFunction(IEvalFunction fn, Function then) {
        this.fn = fn;
        this.then = notNull(then, "then");
    }

    public String toString() {
        return fn + ".then(" + then + ")";
    }

    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        Object o = fn.invoke(thisObj, args, scope);
        return then.apply(o);
    }
}
