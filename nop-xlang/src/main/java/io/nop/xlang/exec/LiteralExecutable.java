/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

import java.io.IOException;

public class LiteralExecutable extends AbstractExecutable {
    private final Object value;

    private LiteralExecutable(SourceLocation loc, Object value) {
        super(loc);
        this.value = value;
    }

    public static IExecutableExpression build(SourceLocation loc, Object value) {
        if (value == null)
            return new NullExecutable(loc);
        return new LiteralExecutable(loc, value);
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        return value;
    }

    @Override
    public void display(StringBuilder sb) {
        if (value instanceof String) {
            sb.append('"');
            try {
                StringHelper.escapeJsonTo(value.toString(), sb);
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
            sb.append('"');
        } else {
            sb.append(value);
        }
    }
}
