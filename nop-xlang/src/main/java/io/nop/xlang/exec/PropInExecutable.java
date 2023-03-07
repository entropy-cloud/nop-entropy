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
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.xlang.ast.XLangOperator;

public class PropInExecutable extends AbstractBinaryExecutable {

    public PropInExecutable(SourceLocation loc, IExecutableExpression leftExpr, IExecutableExpression rightExpr) {
        super(loc, leftExpr, rightExpr);
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public XLangOperator getOperator() {
        return XLangOperator.IN;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object leftValue = executor.execute(left, scope);
        Object rightValue = executor.execute(right, scope);
        if (leftValue == null || rightValue == null)
            return false;

        if (!(leftValue instanceof String))
            return false;

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(rightValue.getClass());
        String propName = leftValue.toString();
        IBeanPropertyModel propModel = beanModel.getPropertyModel(propName);
        if (propModel != null)
            return true;

        return beanModel.isAllowExtProperty(rightValue, propName);
    }
}
