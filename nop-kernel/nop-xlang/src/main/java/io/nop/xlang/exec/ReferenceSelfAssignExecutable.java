/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalReference;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.ast.XLangOperator;

import static io.nop.xlang.XLangErrors.ARG_VAR_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_IDENTIFIER_NOT_INITIALIZED;

public class ReferenceSelfAssignExecutable extends AbstractSelfAssignExecutable {
    protected final int slot;

    public ReferenceSelfAssignExecutable(SourceLocation loc, String varName, int slot, XLangOperator operator,
                                         IExecutableExpression expr) {
        super(loc, varName, operator, expr);
        this.slot = slot;
    }

    public static ReferenceSelfAssignExecutable build(SourceLocation loc, String varName, int slot,
                                                      XLangOperator operator, IExecutableExpression expr) {
        return new ReferenceSelfAssignExecutable(loc, varName, slot, operator, expr);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        EvalReference ref = rt.getCurrentFrame().getRef(slot);
        if (ref == null)
            throw newError(ERR_EXEC_IDENTIFIER_NOT_INITIALIZED).param(ARG_VAR_NAME, varName);

        Object change = executor.execute(expr, rt);
        Object newValue = selfAssignValue(operator, ref.getValue(), change);
        ref.setValue(newValue);
        return newValue;
    }
}
