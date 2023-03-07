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
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;

import static io.nop.xlang.XLangErrors.ARG_ATTR_EXPR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_WRITE_ATTR_EXPR_RETURN_NULL;

public class SetAttrExecutable extends AbstractExecutable {
    private final IExecutableExpression objExpr;
    private final IExecutableExpression attrExpr;
    private final IExecutableExpression valueExpr;

    public SetAttrExecutable(SourceLocation loc, IExecutableExpression objExpr, IExecutableExpression attrExpr,
                             IExecutableExpression valueExpr) {
        super(loc);
        this.objExpr = Guard.notNull(objExpr, "objExpr is null");
        this.attrExpr = Guard.notNull(attrExpr, "attrExpr is null");
        this.valueExpr = Guard.notNull(valueExpr, "valueExpr is null");
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
        sb.append(" = ");
        valueExpr.display(sb);
    }

    protected Object returnNull() {
        return null;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object o = executor.execute(objExpr, scope);
        if (o == null)
            return returnNull();

        Object attr = executor.execute(attrExpr, scope);

        Object value = executor.execute(valueExpr, scope);

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
