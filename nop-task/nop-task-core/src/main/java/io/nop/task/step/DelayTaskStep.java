/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task.step;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskContext;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class DelayTaskStep extends AbstractTaskStep {
    private IScheduledExecutor scheduledExecutor;

    private IEvalAction delayMillsExpr;

    public void setScheduledExecutor(IScheduledExecutor scheduledExecutor) {
        this.scheduledExecutor = scheduledExecutor;
    }

    public void setDelayMillsExpr(IEvalAction delayMillsExpr) {
        this.delayMillsExpr = delayMillsExpr;
    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskContext context) {
        Long delay = ConvertHelper.toLong(delayMillsExpr.invoke(state.getEvalScope()));
        if (delay == null)
            delay = -1L;
        if (delay <= 0)
            return TaskStepResult.RESULT_SUCCESS;

        CompletableFuture<?> future = scheduledExecutor.schedule(() -> {
            return null;
        }, delay, TimeUnit.MILLISECONDS);

        // 在等待的过程中如果context已经被cancel，则会取消等待
        Consumer<String> cancel = reason -> future.cancel(false);
        context.appendOnCancel(cancel);

        future.whenComplete((v, err) -> {
            context.removeOnCancel(cancel);
        });
        return toStepResult(future);
    }
}