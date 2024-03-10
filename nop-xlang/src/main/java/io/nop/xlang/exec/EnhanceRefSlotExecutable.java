/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.EvalReference;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExpressionExecutor;

public class EnhanceRefSlotExecutable extends AbstractExecutable {
    private final String varName;
    private final int slot;

    public EnhanceRefSlotExecutable(SourceLocation loc, String varName, int slot) {
        super(loc);
        this.varName = varName;
        this.slot = slot;
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("enhance_ref(").append(varName).append(')');
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        EvalFrame frame = scope.getCurrentFrame();
        frame.setStackValue(slot, new EvalReference(frame.getStackValue(slot)));
        return null;
    }
}
