/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;

public class EndTaskStep extends AbstractTaskStep {
    private IEvalAction result;

    public IEvalAction getResult() {
        return result;
    }

    public void setResult(IEvalAction result) {
        this.result = result;
    }

    @Override
    @Nonnull
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        Object ret = result == null ? null : result.invoke(stepRt);
        return makeReturn(TaskConstants.STEP_NAME_END, ret);
    }
}
