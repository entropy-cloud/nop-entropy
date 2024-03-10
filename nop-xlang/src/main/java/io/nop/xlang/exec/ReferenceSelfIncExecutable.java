/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.MathHelper;
import io.nop.core.lang.eval.EvalReference;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExpressionExecutor;

public class ReferenceSelfIncExecutable extends AbstractExecutable {
    private final String varName;
    private final int slot;

    public ReferenceSelfIncExecutable(SourceLocation loc, String varName, int slot) {
        super(loc);
        this.varName = varName;
        this.slot = slot;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        EvalReference ref = scope.getCurrentFrame().getRef(slot);
        Object value = ref.getValue();
        Object newValue = MathHelper.add(value, 1);
        ref.setValue(newValue);
        return value;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(varName);
        sb.append("++");
    }
}
