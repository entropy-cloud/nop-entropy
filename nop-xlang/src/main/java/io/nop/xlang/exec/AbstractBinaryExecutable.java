/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.ast.XLangOperator;

public abstract class AbstractBinaryExecutable extends AbstractExecutable {
    protected final IExecutableExpression left;
    protected final IExecutableExpression right;

    public AbstractBinaryExecutable(SourceLocation loc, IExecutableExpression left, IExecutableExpression right) {
        super(loc);
        this.left = Guard.notNull(left, "left");
        this.right = Guard.notNull(right, "right");
    }

    public abstract XLangOperator getOperator();

    public void display(StringBuilder sb) {
        left.display(sb);
        sb.append(' ');
        sb.append(getOperator());
        sb.append(' ');
        right.display(sb);
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }
}
