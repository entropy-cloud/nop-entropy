/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

import io.nop.api.core.util.FutureHelper;
import io.nop.task.model.ITaskInputModel;
import io.nop.task.model.ITaskOutputModel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public interface ITask {
    String getTaskName();

    long getTaskVersion();

    List<? extends ITaskInputModel> getInputs();

    List<? extends ITaskOutputModel> getOutputs();

    TaskStepReturn execute(ITaskRuntime taskRt, Set<String> outputNames);

    default TaskStepReturn execute(ITaskRuntime taskRt) {
        return execute(taskRt, null);
    }

    default CompletionStage<Map<String, Object>> executeAsync(ITaskRuntime taskRt) {
        return executeAsync(taskRt, null);
    }

    default CompletionStage<Map<String, Object>> executeAsync(ITaskRuntime taskRt, Set<String> outputNames) {
        try {
            return execute(taskRt).getReturnPromise().thenApply(TaskStepReturn::getOutputs);
        } catch (Exception e) {
            return FutureHelper.reject(e);
        }
    }
}