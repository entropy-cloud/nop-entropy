/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task.step;

import io.nop.api.core.util.ICancellable;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.task.ITaskContext;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TimeoutTaskStep extends AbstractTaskStep {
    private long timeout;

    private IScheduledExecutor scheduledExecutor;

    private ITaskStep body;

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public void setScheduledExecutor(IScheduledExecutor scheduledExecutor) {
        this.scheduledExecutor = scheduledExecutor;
    }

    public void setBody(ITaskStep body) {
        this.body = body;
    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskContext context) {
        Cancellable cancelToken = new Cancellable();

        ITaskContext subContext = context.withCancelToken(cancelToken);

        Future<?> future = scheduledExecutor.schedule(() -> {
            cancelToken.cancel(ICancellable.CANCEL_REASON_TIMEOUT);
            return null;
        }, timeout, TimeUnit.MILLISECONDS);

        TaskStepResult result = body.execute(state.getRunId(), state, subContext);
        return result.whenComplete((v, e) -> {
            future.cancel(false);
        });
    }
}
