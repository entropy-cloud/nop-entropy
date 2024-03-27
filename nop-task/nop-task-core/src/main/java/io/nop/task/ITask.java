/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

import io.nop.api.core.util.FutureHelper;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface ITask {
    String getTaskName();

    long getTaskVersion();

    TaskStepResult execute(ITaskRuntime taskRt);

    default CompletionStage<Map<String, Object>> executeAsync(ITaskRuntime taskRt) {
        try {
            return execute(taskRt).getReturnPromise().thenApply(TaskStepResult::getReturnValues);
        } catch (Exception e) {
            return FutureHelper.reject(e);
        }
    }
}