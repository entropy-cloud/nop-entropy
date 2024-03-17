/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

public interface ITaskStateStore {
    ITaskStepState newStepState(String stepType, String stepId, int runId, ITaskRuntime context);

    /**
     * 因为存在动态生成的步骤，因此需要通过runId来区分
     *
     * @param stepId 步骤的定义id
     * @param runId  每次执行步骤所对应的执行id
     * @return
     */
    ITaskStepState loadStepState(String stepId, int runId, ITaskRuntime context);

    void saveStepState(ITaskStepState state, ITaskRuntime context);

    ITaskState newTaskState(String taskName, long taskVersion, ITaskRuntime context);

    ITaskState loadTaskState(String taskStateId, ITaskRuntime context);

    void saveTaskState(ITaskState taskState, ITaskRuntime context);
}