/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExpressionExecutor;

public class ContinueExecutable extends AbstractExecutable {
    public ContinueExecutable(SourceLocation loc) {
        super(loc);
    }

    public boolean containsBreakStatement() {
        return true;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        scope.setExitMode(ExitMode.CONTINUE);
        return null;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("continue;");
    }
}
