/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

public class ExitTaskStep extends AbstractTaskStep {
    private IEvalAction result;

    public IEvalAction getResult() {
        return result;
    }

    public void setResult(IEvalAction result) {
        this.result = result;
    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskRuntime taskRt) {
        Object ret = result == null ? null : result.invoke(taskRt);
        return TaskStepResult.RESULT_EXIT(ret);
    }
}