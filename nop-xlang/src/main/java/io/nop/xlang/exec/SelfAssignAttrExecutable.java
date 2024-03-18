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
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.xlang.ast.XLangOperator;

import static io.nop.xlang.XLangErrors.ARG_ATTR_EXPR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_OBJ_ATTR_IS_NULL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_READ_ATTR_EXPR_RETURN_NULL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_WRITE_ATTR_EXPR_RETURN_NULL;

public class SelfAssignAttrExecutable extends AbstractExecutable {
    private final IExecutableExpression objExpr;
    private final IExecutableExpression attrExpr;
    private final XLangOperator operator;
    private final IExecutableExpression valueExpr;

    public SelfAssignAttrExecutable(SourceLocation loc, IExecutableExpression objExpr, IExecutableExpression attrExpr,
                                    XLangOperator operator, IExecutableExpression valueExpr) {
        super(loc);
        this.objExpr = Guard.notNull(objExpr, "objExpr");
        this.operator = Guard.notNull(operator, "operator");
        this.attrExpr = Guard.notNull(attrExpr, "attrExpr");
        this.valueExpr = Guard.notNull(valueExpr, "valueExpr");
    }

    public IExecutableExpression getObjExpr() {
        return objExpr;
    }

    public IExecutableExpression getAttrExpr() {
        return attrExpr;
    }

    public IExecutableExpression getValueExpr() {
        return valueExpr;
    }

    @Override
    public void display(StringBuilder sb) {
        objExpr.display(sb);
        sb.append('[');
        attrExpr.display(sb);
        sb.append(']');
        sb.append(" ").append(operator).append(' ');
        valueExpr.display(sb);
    }

    protected Object returnNull() {
        return null;
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object o = executor.execute(objExpr, rt);
        if (o == null)
            return returnNull();

        Object attr = executor.execute(attrExpr, rt);

        Object oldValue;
        if (attr instanceof Integer) {
            oldValue = readIndex(o, (Integer) attr);
        } else {
            IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(o.getClass());
            if (attr == null && !beanModel.isMapLike())
                throw newError(ERR_EXEC_READ_ATTR_EXPR_RETURN_NULL).param(ARG_ATTR_EXPR, attrExpr.display());
            oldValue = readAttr(beanModel, o, attr);
        }

        if (oldValue == null)
            throw newError(ERR_EXEC_OBJ_ATTR_IS_NULL).param(ARG_ATTR_EXPR, attrExpr.display());

        Object value = executor.execute(valueExpr, rt);
        value = selfAssignValue(operator, oldValue, value);

        if (attr instanceof Integer) {
            setByIndex(o, (Integer) attr, value);
            return value;
        }

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(o.getClass());
        if (attr == null && !beanModel.isMapLike())
            throw newError(ERR_EXEC_WRITE_ATTR_EXPR_RETURN_NULL).param(ARG_ATTR_EXPR, attrExpr.display());

        setAttr(beanModel, o, attr, value);
        return value;
    }

}
