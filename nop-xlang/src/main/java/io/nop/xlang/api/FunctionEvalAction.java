/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.api;

import io.nop.core.context.IEvalContext;
import io.nop.core.reflect.IFunctionModel;

public class FunctionEvalAction extends AbstractEvalAction {
    private final IFunctionModel function;

    public FunctionEvalAction(IFunctionModel function) {
        this.function = function;
    }

    @Override
    public Object invoke(IEvalContext ctx) {
        Object[] argValues = function.buildArgValuesFromScope(ctx.getEvalScope());
        return function.invoke(null, argValues, ctx.getEvalScope());
    }
}
