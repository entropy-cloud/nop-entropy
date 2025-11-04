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
import io.nop.xlang.ast._gen._UnaryExpression;

public class UnaryExpression extends _UnaryExpression {
    public static UnaryExpression valueOf(SourceLocation loc, XLangOperator op, Expression argument) {
        Guard.notNull(op, "op is null");
        Guard.notNull(argument, "argument is null");
        UnaryExpression node = new UnaryExpression();
        node.setLocation(loc);
        node.setOperator(op);
        node.setArgument(argument);
        return node;
    }

    public static UnaryExpression not(SourceLocation loc, Expression expr) {
        return valueOf(loc, XLangOperator.NOT, expr);
    }
}