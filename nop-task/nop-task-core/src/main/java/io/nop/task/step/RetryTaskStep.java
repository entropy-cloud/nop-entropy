/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;

import java.util.Set;

public class RetryTaskStep extends DelegateTaskStep {
    private final IRetryPolicy<ITaskStepState> retryPolicy;

    public RetryTaskStep(ITaskStep taskStep, IRetryPolicy<ITaskStepState> retryPolicy) {
        super(taskStep);
        this.retryPolicy = retryPolicy;
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepState stepState, Set<String> outputNames, ICancelToken cancelToken, ITaskRuntime taskRt) {
        return TaskStepHelper.retry(getLocation(), stepState, cancelToken, taskRt, retryPolicy,
                () -> getTaskStep().execute(stepState, outputNames, cancelToken, taskRt));
    }
}
