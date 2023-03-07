/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.MathHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.xlang.ast.XLangOperator;

import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_ATTR_VALUE;
import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_EXPR;
import static io.nop.xlang.XLangErrors.ARG_OP;
import static io.nop.xlang.XLangErrors.ERR_EXEC_NOT_SUPPORTED_OPERATOR;
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

    protected Object eval(IExecutableExpression expr, IExpressionExecutor executor, IEvalScope scope) {
        try {
            return executor.execute(expr, scope);
        } catch (NopException e) {
            e.addXplStack(this);
            throw e;
        }
    }

    protected Object[] evaluateArgs(IExecutableExpression[] argExprs, IExpressionExecutor executor, IEvalScope scope) {
        Object[] argValues;
        if (argExprs.length == 0) {
            argValues = EMPTY_ARGS;
        } else {
            argValues = new Object[argExprs.length];
            for (int i = 0, n = argExprs.length; i < n; i++) {
                argValues[i] = executor.execute(argExprs[i], scope);
            }
        }
        return argValues;
    }

    protected Object selfAssignValue(XLangOperator op, Object value, Object change) {
        switch (op) {
            case SELF_ASSIGN_BIT_AND:
                return MathHelper.band(value, change);
            case SELF_ASSIGN_BIT_OR:
                return MathHelper.bor(value, change);
            case SELF_ASSIGN_BIT_XOR:
                return MathHelper.bxor(value, change);
            case SELF_ASSIGN_DIV:
                return MathHelper.divide(value, change);
            case SELF_ASSIGN_MULTI:
                return MathHelper.multiply(value, change);
            case SELF_ASSIGN_MOD:
                return MathHelper.mod(value, change);
            case SELF_ASSIGN_LEFT_SHIFT:
                return MathHelper.sl(value, change);
            case SELF_ASSIGN_RIGHT_SHIFT:
                return MathHelper.sr(value, change);
            case SELF_ASSIGN_UNSIGNED_RIGHT_SHIFT:
                return MathHelper.usr(value, change);
            case SELF_ASSIGN_ADD:
                return MathHelper.add(value, change);
            case SELF_ASSIGN_MINUS:
                return MathHelper.minus(value, change);
            default:
                throw newError(ERR_EXEC_NOT_SUPPORTED_OPERATOR).param(ARG_OP, op);
        }

    }


    protected Object readIndex(Object o, int index) {
        return BeanTool.getByIndex(o, index);
    }

    protected Object readAttr(IBeanModel beanModel, Object obj, Object attrValue) {
        try {
            if (beanModel.isMapLike())
                return ((Map) obj).get(attrValue);

            return beanModel.getProperty(obj, attrValue.toString());
        } catch (Exception e) {
            throw newError(ERR_EXEC_READ_ATTR_FAIL, e).forWrap().param(ARG_CLASS_NAME, obj.getClass().getName())
                    .param(ARG_ATTR_VALUE, attrValue);
        }
    }

    protected void setByIndex(Object o, int index, Object value) {
        BeanTool.setByIndex(o, index, value);
    }

    protected void setAttr(IBeanModel beanModel, Object obj, Object attrValue, Object value) {
        try {
            if (beanModel.isMapLike()) {
                ((Map) obj).put(attrValue, value);
            } else {
                beanModel.setProperty(obj, attrValue.toString(), value);
            }
        } catch (Exception e) {
            throw newError(ERR_EXEC_WRITE_ATTR_FAIL, e).forWrap().param(ARG_CLASS_NAME, obj.getClass().getName())
                    .param(ARG_ATTR_VALUE, attrValue);
        }
    }
}
