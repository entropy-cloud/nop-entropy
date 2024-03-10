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
import io.nop.xlang.ast._gen._AssignmentExpression;

public class AssignmentExpression extends _AssignmentExpression {
    public static AssignmentExpression valueOf(SourceLocation loc, Expression left, XLangOperator op,
                                               Expression right) {
        Guard.notNull(left, "left is null");
        Guard.notNull(right, "right is null");
        Guard.notNull(op, "op is null");

        AssignmentExpression node = new AssignmentExpression();
        node.setLocation(loc);
        node.setLeft(left);
        node.setOperator(op);
        node.setRight(right);
        return node;
    }
}