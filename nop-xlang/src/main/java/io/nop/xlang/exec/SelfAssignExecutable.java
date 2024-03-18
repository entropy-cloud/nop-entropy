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
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.ast.XLangOperator;

public class SelfAssignExecutable extends AbstractSelfAssignExecutable {
    protected final int slot;

    public SelfAssignExecutable(SourceLocation loc, String varName, int slot, XLangOperator operator,
                                IExecutableExpression expr) {
        super(loc, varName, operator, expr);
        this.slot = slot;
    }

    public static SelfAssignExecutable build(SourceLocation loc, String varName, int slot, XLangOperator operator,
                                             IExecutableExpression expr) {
        return new SelfAssignExecutable(loc, varName, slot, operator, expr);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object value = rt.getCurrentFrame().getVar(slot);
        Object change = executor.execute(expr, rt);
        Object newValue = selfAssignValue(operator, value, change);
        rt.getCurrentFrame().setStackValue(slot, newValue);
        return newValue;
    }

}
