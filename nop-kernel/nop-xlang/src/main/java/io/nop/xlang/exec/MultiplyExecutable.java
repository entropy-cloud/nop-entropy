/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.MathHelper;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.ast.XLangOperator;

public class MultiplyExecutable extends AbstractBinaryExecutable {
    public MultiplyExecutable(SourceLocation loc, IExecutableExpression left, IExecutableExpression right) {
        super(loc, left, right);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object v1 = executor.execute(left, rt);
        Object v2 = executor.execute(right, rt);
        return MathHelper.multiply(v1, v2);
    }

    @Override
    public XLangOperator getOperator() {
        return XLangOperator.MULTIPLY;
    }
}
