/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
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
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object value = scope.getCurrentFrame().getVar(slot);
        Object change = executor.execute(expr, scope);
        Object newValue = selfAssignValue(operator, value, change);
        scope.getCurrentFrame().setStackValue(slot, newValue);
        return newValue;
    }

}
