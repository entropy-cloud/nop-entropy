/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;

public class RetryTaskStepWrapper extends DelegateTaskStep {
    private final IRetryPolicy<ITaskStepRuntime> retryPolicy;

    public RetryTaskStepWrapper(ITaskStep taskStep, IRetryPolicy<ITaskStepRuntime> retryPolicy) {
        super(taskStep);
        this.retryPolicy = retryPolicy;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        return TaskStepHelper.retry(getLocation(), stepRt, retryPolicy,
                () -> getTaskStep().execute(stepRt));
    }
}
