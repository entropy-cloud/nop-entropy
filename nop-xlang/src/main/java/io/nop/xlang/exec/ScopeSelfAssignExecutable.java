/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.ast.XLangOperator;

public class ScopeSelfAssignExecutable extends AbstractSelfAssignExecutable {

    public ScopeSelfAssignExecutable(SourceLocation loc, String varName, XLangOperator operator,
                                     IExecutableExpression expr) {
        super(loc, varName, operator, expr);
    }

    public static ScopeSelfAssignExecutable build(SourceLocation loc, String varName, XLangOperator operator,
                                                  IExecutableExpression expr) {
        return new ScopeSelfAssignExecutable(loc, varName, operator, expr);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object value = rt.getValue(varName);
        Object change = executor.execute(expr, rt);
        Object newValue = selfAssignValue(operator, value, change);
        rt.setLocalValue(getLocation(), varName, newValue);
        return newValue;
    }
}
