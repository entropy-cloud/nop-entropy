/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task;

import io.nop.api.core.util.ISourceLocationGetter;

import javax.annotation.Nonnull;

public interface ITaskStep extends ISourceLocationGetter {
    /**
     * 步骤的模型id，在整个task范围内唯一
     *
     * @return
     */
    String getStepId();

    /**
     * 步骤类型
     */
    String getStepType();

    @Nonnull
    TaskStepResult execute(int runId, ITaskStepState parentState, ITaskContext context);
}