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

public class EndTaskStep extends AbstractStep {
    private IEvalAction result;

    public IEvalAction getResult() {
        return result;
    }

    public void setResult(IEvalAction result) {
        this.result = result;
    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskContext context) {
        Object ret = result == null ? null : result.invoke(context);
        return TaskStepResult.RESULT_END(ret);
    }
}
