/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExpressionExecutor;

public class SlotIdentifierExecutable extends AbstractExecutable {
    private final String id;
    private final int slot;

    public SlotIdentifierExecutable(SourceLocation loc, String id, int slot) {
        super(loc);
        this.id = Guard.notEmpty(id, "id");
        this.slot = Guard.nonNegativeInt(slot, "slot");
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(id);
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        return scope.getCurrentFrame().getStackValue(slot);
    }
}
