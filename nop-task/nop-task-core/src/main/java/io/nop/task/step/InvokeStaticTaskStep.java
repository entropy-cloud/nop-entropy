/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;

import java.util.List;

public class InvokeStaticTaskStep extends AbstractTaskStep {
    private IEvalFunction method;

    private List<String> argNames;

    public void setMethod(IEvalFunction method) {
        this.method = method;
    }

    public void setArgNames(List<String> argNames) {
        this.argNames = argNames;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        IEvalScope scope = stepRt.getEvalScope();
        Object[] args = new Object[argNames.size()];
        for (int i = 0, n = argNames.size(); i < n; i++) {
            args[i] = scope.getValue(argNames.get(i));
        }

        Object returnValue = method.invoke(null, args, scope);
        return makeReturn(returnValue);
    }
}