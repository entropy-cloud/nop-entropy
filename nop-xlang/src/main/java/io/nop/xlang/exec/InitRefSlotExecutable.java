/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.EvalReference;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExpressionExecutor;

public class InitRefSlotExecutable extends AbstractExecutable {
    private final String varName;
    private final int slot;

    public InitRefSlotExecutable(SourceLocation loc, String varName, int slot) {
        super(loc);
        this.varName = varName;
        this.slot = slot;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("init_ref(").append(varName).append(')');
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        EvalFrame frame = scope.getCurrentFrame();
        frame.setStackValue(slot, new EvalReference(null));
        return null;
    }
}
