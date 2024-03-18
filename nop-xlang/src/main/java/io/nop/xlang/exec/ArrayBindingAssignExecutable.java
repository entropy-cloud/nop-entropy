/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_EXEC_ARRAY_BINDING_NOT_LIST;

public class ArrayBindingAssignExecutable extends AbstractExecutable {
    private final AssignIdentifier[] elementBindings;
    private final AssignIdentifier restBinding;
    private final IExecutableExpression expr;

    public ArrayBindingAssignExecutable(SourceLocation loc, AssignIdentifier[] elementBindings,
                                        AssignIdentifier restBinding, IExecutableExpression expr) {
        super(loc);
        this.elementBindings = elementBindings;
        this.restBinding = restBinding;
        this.expr = expr;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("let ");
        sb.append('[');
        for (AssignIdentifier id : elementBindings) {
            sb.append(id.getVarName());
            sb.append(',');
        }
        if (restBinding == null) {
            sb.deleteCharAt(sb.length() - 1);
        } else {
            sb.append("...").append(restBinding.getVarName());
        }
        sb.append(']');
        sb.append(" = ");
        expr.display(sb);
        sb.append(';');
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object value = executor.execute(expr, rt);
        if (!(value instanceof List)) {
            throw newError(ERR_EXEC_ARRAY_BINDING_NOT_LIST).param(ARG_VALUE, value);
        }

        List<Object> list = (List<Object>) value;
        for (int i = 0, n = elementBindings.length; i < n; i++) {
            elementBindings[i].assign(list.get(i), rt);
        }
        if (restBinding != null) {
            List<Object> subList = CollectionHelper.copyTail(list, elementBindings.length);
            restBinding.assign(subList, rt);
        }
        return null;
    }
}
