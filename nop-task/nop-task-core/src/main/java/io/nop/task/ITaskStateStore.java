/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

public interface ITaskStateStore {
    boolean isSupportPersist();

    ITaskStepState newMainStepState(ITaskState taskState);

    ITaskStepState newStepState(ITaskStepState parentState, String stepName, String stepType, ITaskRuntime taskRt);

    ITaskStepState loadStepState(ITaskStepState parentState, String stepName, String stepType, ITaskRuntime taskRt);


    void saveStepState(ITaskStepRuntime stepRt);

    ITaskState newTaskState(String taskName, long taskVersion, ITaskRuntime taskRt);

    ITaskState loadTaskState(String taskInstanceId, ITaskRuntime taskRt);

    void saveTaskState(ITaskRuntime taskRt);
}