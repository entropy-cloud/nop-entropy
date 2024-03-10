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
import io.nop.core.lang.eval.IExpressionExecutor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NewListExecutable extends AbstractExecutable {
    private final ListItemExecutable[] exprs;

    public NewListExecutable(SourceLocation loc, ListItemExecutable[] exprs) {
        super(loc);
        this.exprs = exprs;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append('[');
        for (int i = 0, n = exprs.length; i < n; i++) {
            exprs[i].display(sb);
            if (i != n - 1)
                sb.append(',');
        }
        sb.append(']');
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        List<Object> ret = new ArrayList<>(exprs.length);
        for (ListItemExecutable expr : exprs) {
            Object value = expr.getValue(executor, scope);
            if (expr.isSpread()) {
                if (value != null) {
                    if (value instanceof Collection) {
                        ret.addAll((Collection<?>) value);
                    } else {
                        ret.add(value);
                    }
                }
            } else {
                ret.add(value);
            }
        }
        return ret;
    }
}
