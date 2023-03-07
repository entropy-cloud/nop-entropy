/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.bind;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.json.bind.IValueResolver;
import io.nop.core.lang.json.bind.IValueResolverCompiler;
import io.nop.core.lang.json.bind.ValueResolverCompileOptions;
import io.nop.core.lang.json.bind.resolver.ValueResolverCompileHelper;
import io.nop.xlang.expr.SimpleExprHelper;

public class ExprValueResolver implements IValueResolver {

    public static final String TYPE = "expr";

    private final IEvalAction action;

    public ExprValueResolver(IEvalAction action) {
        this.action = action;
    }

    @Override
    public Object resolveValue(IEvalContext ctx) {
        return action.invoke(ctx);
    }

    public static IValueResolverCompiler COMPILER = ExprValueResolver::compile;

    public static IValueResolver compile(SourceLocation loc, Object value, ValueResolverCompileOptions options) {
        String expr = ValueResolverCompileHelper.getStringConfig("expr", loc, value);
        return new ExprValueResolver(SimpleExprHelper.compileSimpleExpr(loc, expr));
    }
}