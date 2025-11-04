/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.api;

import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.api.core.json.IJsonString;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.NullExecutable;

@ImmutableBean
public class ExprEvalAction extends AbstractEvalAction implements ISourceLocationGetter, IJsonString {
    private final IExecutableExpression expr;

    public ExprEvalAction(IExecutableExpression expr) {
        this.expr = expr == null ? NullExecutable.NULL : expr;
    }

    public String toString() {
        return expr.toString();
    }

    public IExecutableExpression getExpr() {
        return expr;
    }

    public XplModel toXplModel() {
        return new XplModel(expr);
    }

    @Override
    public SourceLocation getLocation() {
        return expr.getLocation();
    }

    @Override
    public Object invoke(IEvalContext ctx) {
        return XLang.execute(expr, new EvalRuntime(ctx.getEvalScope()));
    }

    @Override
    protected Object doInvoke(EvalRuntime rt) {
        return XLang.execute(expr, rt);
    }
}
