/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.ast.XLangOperator;

public abstract class AbstractSelfAssignExecutable extends AbstractExecutable {
    protected final String varName;
    protected final XLangOperator operator;
    protected final IExecutableExpression expr;

    public AbstractSelfAssignExecutable(SourceLocation loc, String varName, XLangOperator operator,
                                        IExecutableExpression expr) {
        super(loc);
        this.varName = varName;
        this.operator = operator;
        this.expr = expr;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(varName);
        sb.append(operator);
        expr.display(sb);
    }
}
