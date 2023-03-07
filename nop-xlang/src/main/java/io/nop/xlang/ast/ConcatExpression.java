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
import io.nop.xlang.ast._gen._ConcatExpression;

import java.util.List;

public class ConcatExpression extends _ConcatExpression {
    public static ConcatExpression valueOf(SourceLocation loc, List<Expression> exprs) {
        Guard.notEmpty(exprs, "exprs is empty");
        ConcatExpression node = new ConcatExpression();
        node.setLocation(loc);
        node.setExpressions(exprs);
        return node;
    }

}