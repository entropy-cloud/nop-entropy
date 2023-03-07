/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_FUNC_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_INVOKE_METHOD_FAIL;

public abstract class AbstractObjFunctionExecutable extends AbstractExecutable {
    protected final IExecutableExpression objExpr;
    protected final String funcName;
    protected final boolean optional;
    protected final IExecutableExpression[] args;

    protected AbstractObjFunctionExecutable(SourceLocation loc,
                                            IExecutableExpression objExpr, String funcName,
                                            boolean optional,
                                            IExecutableExpression[] args) {
        super(loc);
        this.objExpr = Guard.notNull(objExpr, "objExpr");
        this.funcName = Guard.notEmpty(funcName, "funcName");
        this.optional = optional;
        this.args = args;
    }

    @Override
    public void display(StringBuilder sb) {
        objExpr.display(sb);
        sb.append('.');
        sb.append(funcName);
        if (optional) {
            sb.append("?.");
        }
        sb.append('(');
        for (int i = 0, n = args.length; i < n; i++) {
            args[i].display(sb);
            if (i != n - 1)
                sb.append(',');
        }
        sb.append(')');
    }

    protected Object doInvoke(IEvalFunction func, Object obj, Object[] attrValues, IEvalScope scope) {
        try {
            return func.invoke(obj, attrValues, scope);
        } catch (Exception e) {
            throw newError(ERR_EXEC_INVOKE_METHOD_FAIL, e).forWrap().param(ARG_CLASS_NAME, getClassName(obj))
                    .param(ARG_FUNC_NAME, funcName);
        }
    }

    static String getClassName(Object o) {
        if (o == null)
            return "null";
        if (o instanceof Class)
            return o.toString();
        return o.getClass().getName();
    }

    protected Object doInvoke0(IEvalFunction func, Object obj, IEvalScope scope) {
        try {
            return func.call0(obj, scope);
        } catch (Exception e) {
            throw newError(ERR_EXEC_INVOKE_METHOD_FAIL, e).forWrap().param(ARG_CLASS_NAME, getClassName(obj))
                    .param(ARG_FUNC_NAME, funcName);
        }
    }

    protected Object doInvoke1(IEvalFunction func, Object obj, Object arg1, IEvalScope scope) {
        try {
            return func.call1(obj, arg1, scope);
        } catch (Exception e) {
            throw newError(ERR_EXEC_INVOKE_METHOD_FAIL, e).forWrap().param(ARG_CLASS_NAME, getClassName(obj))
                    .param(ARG_FUNC_NAME, funcName);
        }
    }

    protected Object doInvoke2(IEvalFunction func, Object obj, Object arg1, Object arg2, IEvalScope scope) {
        try {
            return func.call2(obj, arg1, arg2, scope);
        } catch (Exception e) {
            throw newError(ERR_EXEC_INVOKE_METHOD_FAIL, e).forWrap().param(ARG_CLASS_NAME, getClassName(obj))
                    .param(ARG_FUNC_NAME, funcName);
        }
    }

    protected Object doInvoke3(IEvalFunction func, Object obj, Object arg1, Object arg2, Object arg3,
                               IEvalScope scope) {
        try {
            return func.call3(obj, arg1, arg2, arg3, scope);
        } catch (Exception e) {
            throw newError(ERR_EXEC_INVOKE_METHOD_FAIL, e).forWrap().param(ARG_CLASS_NAME, getClassName(obj))
                    .param(ARG_FUNC_NAME, funcName);
        }
    }
}
