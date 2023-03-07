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
import io.nop.xlang.ast.XLangOperator;

public class NullCoalesceExecutable extends AbstractBinaryExecutable {
    public NullCoalesceExecutable(SourceLocation loc, IExecutableExpression left, IExecutableExpression right) {
        super(loc, left, right);
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object v1 = executor.execute(left, scope);
        if (v1 != null)
            return v1;
        return executor.execute(right, scope);
    }

    @Override
    public XLangOperator getOperator() {
        return XLangOperator.NULL_COALESCE;
    }
}