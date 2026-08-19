/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;

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

    public IExecutableExpression getObjExpr() {
        return objExpr;
    }

    public String getFuncName() {
        return funcName;
    }

    public boolean isOptional() {
        return optional;
    }

    public IExecutableExpression[] getArgs() {
        return args;
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
        return XLangSemantics.invokeObjFunction(func, obj, attrValues, scope, getLocation(), display(), funcName);
    }

    /**
     * 方法调用异常包装统一收口于共享 helper {@link XLangSemantics#wrapInvokeException}
     * （错误码 / loc / forWrap / params / bizFatal 传播语义不变，解释器与生成代码同一实现来源）。
     */
    private NopException wrapInvokeException(Exception e, Object obj) {
        return XLangSemantics.wrapInvokeException(getLocation(), display(), funcName, e, obj);
    }

    protected Object doInvoke0(IEvalFunction func, Object obj, IEvalScope scope) {
        return XLangSemantics.invokeObjFunction0(func, obj, scope, getLocation(), display(), funcName);
    }

    protected Object doInvoke1(IEvalFunction func, Object obj, Object arg1, IEvalScope scope) {
        return XLangSemantics.invokeObjFunction1(func, obj, arg1, scope, getLocation(), display(), funcName);
    }

    protected Object doInvoke2(IEvalFunction func, Object obj, Object arg1, Object arg2, IEvalScope scope) {
        return XLangSemantics.invokeObjFunction2(func, obj, arg1, arg2, scope, getLocation(), display(), funcName);
    }

    protected Object doInvoke3(IEvalFunction func, Object obj, Object arg1, Object arg2, Object arg3,
                               IEvalScope scope) {
        return XLangSemantics.invokeObjFunction3(func, obj, arg1, arg2, arg3, scope, getLocation(), display(),
                funcName);
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            objExpr.visit(visitor);
            for (IExecutableExpression arg : args) {
                arg.visit(visitor);
            }
            visitor.onEndVisitExpr(this);
        }
    }
}
