/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task.step;

import io.nop.task.ITaskContext;
import io.nop.task.ITaskStepImplementor;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

public class SimpleTaskStep extends AbstractTaskStep {
    private ITaskStepImplementor stepImplementor;

    public void setStepImplementor(ITaskStepImplementor stepImplementor) {
        this.stepImplementor = stepImplementor;
    }

    @Override
    protected void initStepState(ITaskStepState state, ITaskContext context) {
        stepImplementor.prepareState(this, state, context);
    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskContext context) {
        return stepImplementor.execute(this, state, context);
    }
}