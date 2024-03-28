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
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepResult;
import jakarta.annotation.Nonnull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DelayTaskStep extends AbstractTaskStep {
    private IEvalAction delayMillsExpr;


    public void setDelayMillsExpr(IEvalAction delayMillsExpr) {
        this.delayMillsExpr = delayMillsExpr;
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepRuntime stepRt) {
        Long delay = ConvertHelper.toLong(delayMillsExpr.invoke(stepRt.getEvalScope()));
        if (delay == null)
            delay = -1L;
        if (delay <= 0)
            return TaskStepResult.CONTINUE;

        IScheduledExecutor scheduledExecutor = stepRt.getTaskRuntime().getScheduledExecutor();

        CompletableFuture<?> future = scheduledExecutor.schedule(() -> {
            return null;
        }, delay, TimeUnit.MILLISECONDS);

        // 在等待的过程中如果context已经被cancel，则会取消等待
        ICancelToken cancelToken = stepRt.getCancelToken();
        FutureHelper.bindCancelToken(cancelToken, future);

        return TaskStepResult.ASYNC(null, future);
    }
}