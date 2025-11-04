/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
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
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        return new EvalFunctionalAdapter(getLocation(), function, rt.getScope());
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        visitor.onVisitSimpleExpr(this);
    }
}
