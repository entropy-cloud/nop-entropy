/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.bind;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalExprParser;
import io.nop.core.lang.json.bind.compile.ValueResolverCompiler;

public class JsonBindExprEvaluator {
    public static Object evalBindExpr(Object value, boolean ignoreUnknown, IEvalExprParser exprParser,
                                      ValueResolverCompilerRegistry registry, IEvalContext ctx) {
        ValueResolverCompileOptions options = new ValueResolverCompileOptions(value);
        options.setExprParser(exprParser);
        options.setRegistry(registry);
        options.setIgnoreUnknown(ignoreUnknown);

        IValueResolver resolver = ValueResolverCompiler.INSTANCE.compile(null, value, options);

        return resolver.resolveValue(ctx);
    }
}
