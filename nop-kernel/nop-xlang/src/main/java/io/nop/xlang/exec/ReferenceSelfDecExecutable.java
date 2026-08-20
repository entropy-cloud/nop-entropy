/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalReference;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;

public class ReferenceSelfDecExecutable extends AbstractExecutable {
    private final String varName;
    private final int slot;

    public ReferenceSelfDecExecutable(SourceLocation loc, String varName, int slot) {
        super(loc);
        this.varName = varName;
        this.slot = slot;
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        EvalReference ref = XLangSemantics.asRef(getLocation(), display(), varName,
                rt.getCurrentFrame().getStackValue(slot));
        Object value = ref.getValue();
        Object newValue = XLangSemantics.selfIncValue(value, -1);
        ref.setValue(newValue);
        return value;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(varName);
        sb.append("--");
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        visitor.onVisitSimpleExpr(this);
    }
    public String getVarName() {
        return varName;
    }

    public int getSlot() {
        return slot;
    }

}
