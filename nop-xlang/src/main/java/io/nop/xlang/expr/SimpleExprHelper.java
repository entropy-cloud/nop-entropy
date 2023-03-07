/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.expr;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;

import static io.nop.commons.cache.CacheConfig.newConfig;

public class SimpleExprHelper {
    static final ICache<String, ExprEvalAction> exprCache = LocalCache.newCache("simple-expr-compile-cache",
            newConfig(1000), expr -> compileSimpleExpr(null, expr));

    public static ExprEvalAction getCompiledExpr(String expr) {
        return exprCache.get(expr);
    }

    public static ExprEvalAction compileSimpleExpr(SourceLocation loc, String expr) {
        return XLang.newCompileTool().allowUnregisteredScopeVar(true).compileSimpleExpr(loc, expr);
    }
}
