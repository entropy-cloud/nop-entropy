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

public class NullExecutable extends AbstractExecutable {
    public static final NullExecutable NULL = new NullExecutable(null);

    public NullExecutable(SourceLocation loc) {
        super(loc);
    }

    public static NullExecutable valueOf(SourceLocation loc) {
        if (loc == null)
            return NULL;
        return new NullExecutable(loc);
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        return null;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("null");
    }
}