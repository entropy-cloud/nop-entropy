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
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;

/**
 * 用于循环变量引用，确保每个循环都使用一个新的变量
 */
public class RenewReferenceExecutable extends AbstractExecutable {
    private final String varName;
    private final int slot;

    public RenewReferenceExecutable(SourceLocation loc, String varName, int slot) {
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
        sb.append(varName);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        EvalFrame frame = rt.getCurrentFrame();
        EvalReference ref = frame.getRef(slot);
        if (ref != null) {
            ref = new EvalReference(ref.getValue());
        } else {
            ref = new EvalReference(0);
        }
        frame.setStackValue(slot, ref);
        return null;
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        visitor.onVisitSimpleExpr(this);
    }
}