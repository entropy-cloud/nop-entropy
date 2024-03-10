/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.ast.XLangOperator;

import static io.nop.api.core.convert.ConvertHelper.toTruthy;

public class AndExecutable extends AbstractBinaryExecutable {
    public AndExecutable(SourceLocation loc, IExecutableExpression left, IExecutableExpression right) {
        super(loc, left, right);
    }

    @Override
    public XLangOperator getOperator() {
        return XLangOperator.AND;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object v1 = executor.execute(left, scope);
        if (toTruthy(v1))
            return executor.execute(right, scope);
        return v1;
    }
}