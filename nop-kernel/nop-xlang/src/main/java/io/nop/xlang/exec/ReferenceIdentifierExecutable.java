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


public class ReferenceIdentifierExecutable extends AbstractExecutable {
    private final String id;
    private final int slot;

    public ReferenceIdentifierExecutable(SourceLocation loc, String id, int slot) {
        super(loc);
        this.id = Guard.notEmpty(id, "id");
        this.slot = Guard.nonNegativeInt(slot, "slot");
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(id);
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        return XLangSemantics.getRefValue(getLocation(), display(), id, rt.getCurrentFrame().getStackValue(slot));
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        visitor.onVisitSimpleExpr(this);
    }
    public String getId() {
        return id;
    }

    public int getSlot() {
        return slot;
    }

}
