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
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
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

    public int getSlot() {
        return slot;
    }

    /**
     * 只读访问器（truffle 翻译器消费，纯增量，解释器行为不变）。
     */
    public String getId() {
        return id;
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        return rt.getCurrentFrame().getStackValue(slot);
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        visitor.onVisitSimpleExpr(this);
    }
}
