/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.impl;

import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.expr.ExprFeatures;
import io.nop.xlang.expr.IXLangExprParser;
import io.nop.xlang.expr.simple.SimpleExprParser;
import io.nop.xlang.xpl.utils.XplParseHelper;

public class XplExprParser extends SimpleExprParser {
    private final IXLangExprParser cp;
    private final IXLangCompileScope scope;
    private final boolean resolveMacro;

    public XplExprParser(IXLangExprParser cp, IXLangCompileScope scope, boolean resolveMacro) {
        this.cp = cp;
        this.scope = scope;
        this.resolveMacro = resolveMacro;

        setUseEvalException(true);
        enableFeatures(ExprFeatures.ALL);
    }

    @Override
    protected Expression macroExpr(Expression expr) {
        if (resolveMacro)
            return XplParseHelper.runMacroExpr(expr, cp, scope);
        return super.macroExpr(expr);
    }
}
