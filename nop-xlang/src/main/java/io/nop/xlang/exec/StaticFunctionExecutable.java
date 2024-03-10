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
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.IMethodModelCollection;

import static io.nop.xlang.XLangErrors.ARG_ARG_COUNT;
import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_METHOD_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_OBJ_UNKNOWN_METHOD;

public class StaticFunctionExecutable extends AbstractExecutable {
    private final String className;
    private final String funcName;
    private final IMethodModelCollection methodCollection;
    private final IExecutableExpression[] argExprs;
    private final boolean optional;

    public StaticFunctionExecutable(SourceLocation loc, String className, String funcName, boolean optional,
                                    IMethodModelCollection methodCollection, IExecutableExpression[] argExprs) {
        super(loc);
        this.className = Guard.notEmpty(className, "className");
        this.funcName = Guard.notEmpty(funcName, "funcName");
        this.optional = optional;
        this.methodCollection = Guard.notNull(methodCollection, "methodCollection");
        this.argExprs = Guard.notNull(argExprs, "argExprs");
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(className);
        sb.append('.').append(funcName);
        addArgExprs(sb, argExprs);
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object[] argValues = evaluateArgs(argExprs, executor, scope);
        IFunctionModel fn = methodCollection.getMethodForArgValues(argValues);
        if (fn == null) {
            throw newError(ERR_EXEC_OBJ_UNKNOWN_METHOD).param(ARG_CLASS_NAME, className)
                    .param(ARG_METHOD_NAME, funcName).param(ARG_ARG_COUNT, argExprs.length);
        }
        return fn.invoke(null, argValues, scope);
    }
}
