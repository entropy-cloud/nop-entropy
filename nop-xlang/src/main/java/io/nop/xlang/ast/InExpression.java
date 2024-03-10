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
import io.nop.xlang.ast._gen._InExpression;

public class InExpression extends _InExpression {
    public static InExpression valueOf(SourceLocation loc, Expression left, Expression right) {
        Guard.notNull(left, "left is null");
        Guard.notNull(right, "right is null");

        InExpression node = new InExpression();
        node.setLocation(loc);
        node.setLeft(left);
        node.setRight(right);
        return node;
    }
}