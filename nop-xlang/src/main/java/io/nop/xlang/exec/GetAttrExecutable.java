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
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;

import static io.nop.xlang.XLangErrors.ARG_ATTR_EXPR;
import static io.nop.xlang.XLangErrors.ARG_OBJ_EXPR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_GET_ATTR_ON_NULL_OBJ;
import static io.nop.xlang.XLangErrors.ERR_EXEC_READ_ATTR_EXPR_RETURN_NULL;

public class GetAttrExecutable extends AbstractExecutable {
    private final IExecutableExpression objExpr;
    private final IExecutableExpression attrExpr;
    private final boolean optional;

    public GetAttrExecutable(SourceLocation loc, IExecutableExpression objExpr, boolean optional,
                             IExecutableExpression attrExpr) {
        super(loc);
        this.objExpr = objExpr;
        this.optional = optional;
        this.attrExpr = attrExpr;
    }

    public boolean isOptional() {
        return optional;
    }

    public IExecutableExpression getObjExpr() {
        return objExpr;
    }

    public IExecutableExpression getAttrExpr() {
        return attrExpr;
    }

    @Override
    public void display(StringBuilder sb) {
        objExpr.display(sb);
        if (optional)
            sb.append("?.");
        sb.append('[');
        attrExpr.display(sb);
        sb.append(']');
    }

    protected Object returnNull() {
        return null;
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object o = executor.execute(objExpr, scope);
        if (o == null) {
            if (!optional)
                throw newError(ERR_EXEC_GET_ATTR_ON_NULL_OBJ)
                        .param(ARG_ATTR_EXPR, attrExpr.display()).param(ARG_OBJ_EXPR, objExpr.display());
            return returnNull();
        }

        Object attr = executor.execute(attrExpr, scope);
        if (attr instanceof Integer) {
            return readIndex(o, (Integer) attr);
        }

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(o.getClass());
        if (attr == null && !beanModel.isMapLike())
            throw newError(ERR_EXEC_READ_ATTR_EXPR_RETURN_NULL).param(ARG_ATTR_EXPR, attrExpr.display());
        return readAttr(beanModel, o, attr);
    }
}
