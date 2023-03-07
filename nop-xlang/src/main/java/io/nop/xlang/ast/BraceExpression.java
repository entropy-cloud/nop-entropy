/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._BraceExpression;

public class BraceExpression extends _BraceExpression {
    public static BraceExpression valueOf(SourceLocation loc, Expression expr) {
        Guard.notNull(expr, "expr is null");
        BraceExpression node = new BraceExpression();
        node.setLocation(loc);
        node.setExpr(expr);
        return node;
    }
}