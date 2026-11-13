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
import jakarta.annotation.Nullable;

public class RetryTaskStepWrapper extends DelegateTaskStep {
    private final IRetryPolicy<ITaskStepRuntime> retryPolicy;

    /**
     * 步骤级 {@code executor} 隔离配置的 bean 名（plan 2498 Phase 2，finding 1.5-C4-001/
     * C4-008）：非空时延迟重试轮次的步骤体与完成回调链（含 saveState 簿记）在该池上执行，
     * 使 executor 隔离配置对延迟路径真实生效；为 null 时回退共享 globalWorker——永不落在
     * 共享 globalTimer 线程上。
     */
    private final String executorBean;

    public RetryTaskStepWrapper(ITaskStep taskStep, IRetryPolicy<ITaskStepRuntime> retryPolicy) {
        this(taskStep, retryPolicy, null);
    }

    public RetryTaskStepWrapper(ITaskStep taskStep, IRetryPolicy<ITaskStepRuntime> retryPolicy,
                                @Nullable String executorBean) {
        super(taskStep);
        this.retryPolicy = retryPolicy;
        this.executorBean = executorBean;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        return TaskStepHelper.retry(getLocation(), stepRt, retryPolicy,
                () -> getTaskStep().execute(stepRt), executorBean);
    }
}
