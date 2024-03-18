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
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;

import static io.nop.xlang.XLangErrors.ARG_ARG_COUNT;
import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CLASS_NO_CONSTRUCTOR;

public class NewObjectExecutable extends AbstractExecutable {
    private final IClassModel classModel;
    private final IExecutableExpression[] argExprs;

    public NewObjectExecutable(SourceLocation loc, IClassModel classModel, IExecutableExpression[] argExprs) {
        super(loc);
        this.classModel = classModel;
        this.argExprs = argExprs;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("new ");
        sb.append(classModel.getClassName());
        sb.append('(');
        for (int i = 0, n = argExprs.length; i < n; i++) {
            argExprs[i].display(sb);
            if (i != n - 1)
                sb.append(',');
        }
        sb.append(')');
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object[] argValues = evaluateArgs(argExprs, executor, rt);

        IFunctionModel constructor = classModel.getConstructorForArgs(argValues);
        if (constructor == null)
            throw newError(ERR_EXEC_CLASS_NO_CONSTRUCTOR).param(ARG_CLASS_NAME, classModel.getClassName())
                    .param(ARG_ARG_COUNT, argExprs.length);
        return constructor.invoke(null, argValues, rt.getScope());
    }
}
