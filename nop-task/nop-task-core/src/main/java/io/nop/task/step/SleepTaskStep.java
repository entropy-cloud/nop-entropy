/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

public class SleepTaskStep extends AbstractTaskStep {
    private IEvalAction sleepMillisExpr;

    public void setSleepMillisExpr(IEvalAction sleepMillisExpr) {
        this.sleepMillisExpr = sleepMillisExpr;
    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskRuntime taskRt) {
        Long sleep = ConvertHelper.toLong(sleepMillisExpr.invoke(state.getEvalScope()));
        if (sleep == null)
            sleep = -1L;
        if (sleep <= 0)
            return TaskStepResult.CONTINUE;

        FutureHelper.waitUntil(() -> !taskRt.isCancelled(), sleep);

        return TaskStepResult.CONTINUE;
    }
}
