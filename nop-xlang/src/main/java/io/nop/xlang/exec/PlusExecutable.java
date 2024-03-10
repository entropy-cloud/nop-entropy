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
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.ast.XLangOperator;

public class PlusExecutable extends AbstractBinaryExecutable {
    public PlusExecutable(SourceLocation loc, IExecutableExpression left, IExecutableExpression right) {
        super(loc, left, right);
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object v1 = executor.execute(left, scope);
        Object v2 = executor.execute(right, scope);
        if (v1 instanceof String || v2 instanceof String) {
            // if (v1 == null)
            // return String.valueOf(v2);
            // if (v2 == null)
            // return String.valueOf(v1);
            return String.valueOf(v1) + String.valueOf(v2);
        }
        return MathHelper.add(v1, v2);
    }

    @Override
    public XLangOperator getOperator() {
        return XLangOperator.ADD;
    }
}
