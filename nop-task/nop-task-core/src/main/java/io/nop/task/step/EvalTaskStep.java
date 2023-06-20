/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task.step;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskContext;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

public class EvalTaskStep extends AbstractTaskStep {
    private IEvalAction source;

    public void setSource(IEvalAction source) {
        this.source = source;
    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskContext context) {
        Object result = source.invoke(state.evalScope());
        return toStepResult(result);
    }
}