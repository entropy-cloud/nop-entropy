/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._TemplateExpression;

import java.util.List;

public class TemplateExpression extends _TemplateExpression {

    public static TemplateExpression valueOf(SourceLocation loc, List<Expression> exprs, String prefix, String postfix) {
        Guard.notEmpty(exprs, "exprs");
        TemplateExpression node = new TemplateExpression();
        node.setLocation(loc);
        node.setExpressions(exprs);
        node.setPrefix(prefix);
        node.setPostfix(postfix);
        return node;
    }

    public static String toTemplateString(Expression expr, String prefix, String postfix) {
        if (Literal.isStringLiteral(expr))
            return (String) ((Literal) expr).getValue();
        if (expr instanceof TemplateExpression)
            return expr.toExprString();
        return prefix + expr.toExprString() + postfix;
    }
}
