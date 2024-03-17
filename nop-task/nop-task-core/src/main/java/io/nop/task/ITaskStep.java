/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.task.model.ITaskInputModel;
import io.nop.task.model.ITaskOutputModel;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Set;

public interface ITaskStep extends ISourceLocationGetter {
    /**
     * 步骤的名称，在父步骤范围内唯一
     */
    String getStepName();

    /**
     * 步骤类型
     */
    String getStepType();

    List<? extends ITaskInputModel> getInputs();

    List<? extends ITaskOutputModel> getOutputs();

    default TaskStepResult execute(int runId, ITaskStepState parentState, ICancelToken cancelToken, ITaskRuntime taskRt) {
        return null;
    }

    @Nonnull
    TaskStepResult execute(ITaskStepState stepState, Set<String> outputNames,
                           ICancelToken cancelToken, ITaskRuntime taskRt);
}