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
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;

public class ScopeSelfDecExecutable extends AbstractExecutable {
    private final String varName;

    public ScopeSelfDecExecutable(SourceLocation loc, String varName) {
        super(loc);
        this.varName = varName;
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object value = rt.getValue(varName);
        Object newValue = MathHelper.add(value, -1);
        rt.setLocalValue(getLocation(), varName, newValue);
        return value;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(varName);
        sb.append("--");
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        visitor.onVisitSimpleExpr(this);
    }
}
