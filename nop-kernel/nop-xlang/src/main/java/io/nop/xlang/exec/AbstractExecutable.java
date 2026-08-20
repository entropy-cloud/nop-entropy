/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.xlang.ast.XLangOperator;


import static io.nop.xlang.XLangErrors.ARG_EXPR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_READ_ATTR_FAIL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_WRITE_ATTR_FAIL;

public abstract class AbstractExecutable implements IExecutableExpression {
    private static final Object[] EMPTY_ARGS = new Object[0];

    private final SourceLocation loc;

    public AbstractExecutable(SourceLocation loc) {
        this.loc = loc;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        display(sb);
        sb.append('@').append(loc);
        return sb.toString();
    }

    public boolean containsReturnStatement() {
        return false;
    }

    public boolean containsBreakStatement() {
        return false;
    }

    @Override
    public SourceLocation getLocation() {
        return loc;
    }

    protected NopException newError(ErrorCode errorCode) {
        return new NopEvalException(errorCode).loc(loc).param(ARG_EXPR, display());
    }

    protected NopException newError(ErrorCode errorCode, Throwable e) {
        return new NopEvalException(errorCode, e).loc(loc).param(ARG_EXPR, display());
    }

    protected void addArgExprs(StringBuilder sb, IExecutableExpression[] args) {
        sb.append('(');
        for (int i = 0, n = args.length; i < n; i++) {
            args[i].display(sb);
            if (i != n - 1)
                sb.append(',');
        }
        sb.append(')');
    }

    protected Object eval(IExecutableExpression expr, IExpressionExecutor executor, EvalRuntime rt) {
        try {
            return executor.execute(expr, rt);
        } catch (NopException e) {
            e.addXplStack(this);
            throw e;
        }
    }

    protected Object[] evaluateArgs(IExecutableExpression[] argExprs, IExpressionExecutor executor, EvalRuntime rt) {
        Object[] argValues;
        if (argExprs.length == 0) {
            argValues = EMPTY_ARGS;
        } else {
            argValues = new Object[argExprs.length];
            for (int i = 0, n = argExprs.length; i < n; i++) {
                argValues[i] = executor.execute(argExprs[i], rt);
            }
        }
        return argValues;
    }

    protected Object selfAssignValue(XLangOperator op, Object value, Object change) {
        return XLangSemantics.selfAssignValue(loc, display(), op, value, change);
    }


    protected Object readIndex(Object o, int index) {
        return BeanTool.getByIndex(o, index);
    }

    protected Object readAttr(IBeanModel beanModel, Object obj, Object attrValue) {
        return XLangSemantics.readAttrValue(loc, display(), ERR_EXEC_READ_ATTR_FAIL, beanModel, obj, attrValue);
    }

    protected void setByIndex(Object o, int index, Object value) {
        BeanTool.setByIndex(o, index, value);
    }

    protected void setAttr(IBeanModel beanModel, Object obj, Object attrValue, Object value) {
        XLangSemantics.writeAttrValue(loc, display(), ERR_EXEC_WRITE_ATTR_FAIL, beanModel, obj, attrValue, value);
    }

    /**
     * 将属性读/写异常包装为 {@code NopEvalException(errorCode)}，保留 {@code wrapException} /
     * cause / errorCode 全部既有语义。当属性 getter/setter 抛出 bizFatal
     * {@link NopException} 时，将 bizFatal 标记传播到包装异常，使下游分类器（如
     * {@code RetryPolicy.isRecoverableException}）能正确识别不可恢复异常。
     */
    private NopException wrapAttrException(ErrorCode errorCode, Throwable e, Object obj, Object attrValue) {
        return XLangSemantics.wrapAttrException(loc, display(), errorCode, e, obj, attrValue);
    }

    /**
     * 将属性读/写异常包装为 {@code NopEvalException(errorCode)}，保留 {@code wrapException} /
     * cause / errorCode 全部既有语义。当属性 getter/setter 抛出 bizFatal
     * {@link NopException} 时，将 bizFatal 标记传播到包装异常，使下游分类器（如
     * {@code RetryPolicy.isRecoverableException}）能正确识别不可恢复异常。
     */
    protected NopException wrapPropException(ErrorCode errorCode, Throwable e, String className, String propName) {
        return XLangSemantics.wrapPropException(loc, display(), errorCode, e, className, propName);
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        visitor.onVisitSimpleExpr(this);
    }
}
