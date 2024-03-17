/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

public class SimpleTaskStep extends AbstractTaskStep {
    private ITaskStepExecution stepImplementor;

    public void setStepImplementor(ITaskStepExecution stepImplementor) {
        this.stepImplementor = stepImplementor;
    }

    @Override
    protected void initStepState(ITaskStepState state, ITaskRuntime context) {
        stepImplementor.prepareState(this, state, context);
    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskRuntime taskRt) {
        return stepImplementor.execute(this, state, taskRt);
    }
}