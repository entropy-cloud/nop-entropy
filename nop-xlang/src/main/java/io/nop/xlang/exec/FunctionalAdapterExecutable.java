/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.lang.eval.functions.EvalFunctionalAdapter;

public class FunctionalAdapterExecutable extends AbstractExecutable {
    private final IEvalFunction function;

    public FunctionalAdapterExecutable(SourceLocation loc, IEvalFunction function) {
        super(loc);
        this.function = function;
    }

    public IEvalFunction getFunction() {
        return function;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(function);
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        return new EvalFunctionalAdapter(getLocation(), function, scope);
    }
}
