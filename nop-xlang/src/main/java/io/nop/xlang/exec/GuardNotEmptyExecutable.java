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
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

import static io.nop.xlang.XLangErrors.ARG_TARGET;
import static io.nop.xlang.XLangErrors.ERR_EXEC_VALUE_NOT_ALLOW_EMPTY;

public class GuardNotEmptyExecutable extends AbstractExecutable {
    private final IExecutableExpression expr;
    private final String target;

    public GuardNotEmptyExecutable(SourceLocation loc, IExecutableExpression expr, String target) {
        super(loc);
        this.expr = Guard.notNull(expr, "expr");
        this.target = target;
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object v = executor.execute(expr, scope);
        if (StringHelper.isEmptyObject(v))
            throw newError(ERR_EXEC_VALUE_NOT_ALLOW_EMPTY).param(ARG_TARGET, target);
        return v;
    }

    @Override
    public void display(StringBuilder sb) {
        expr.display(sb);
        sb.append('!');
    }
}
