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
import io.nop.api.core.util.ICancelToken;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;

import java.util.Set;

public class SleepTaskStep extends AbstractTaskStep {
    private IEvalAction sleepMillisExpr;

    public IEvalAction getSleepMillisExpr() {
        return sleepMillisExpr;
    }

    public void setSleepMillisExpr(IEvalAction sleepMillisExpr) {
        this.sleepMillisExpr = sleepMillisExpr;
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepState stepState, Set<String> outputNames, ICancelToken cancelToken, ITaskRuntime taskRt) {
        Long sleep = ConvertHelper.toLong(sleepMillisExpr.invoke(stepState));
        if (sleep == null)
            sleep = -1L;
        if (sleep <= 0)
            return TaskStepResult.CONTINUE;

        FutureHelper.waitUntil(() -> !TaskStepHelper.isCancelled(cancelToken, taskRt), sleep);

        return TaskStepResult.CONTINUE;
    }
}