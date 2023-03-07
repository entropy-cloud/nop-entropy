/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task;

public interface ITaskStateStore {
    ITaskStepState newStepState(String stepType, String stepId, String runId, ITaskContext context);

    /**
     * 因为存在动态生成的步骤，因此需要通过runId来区分
     *
     * @param stepId
     * @param runId
     * @return
     */
    ITaskStepState loadStepState(String stepId, String runId, ITaskContext context);

    void saveStepState(ITaskStepState state, ITaskContext context);

    ITaskState newTaskState(String taskName, long taskVersion, ITaskContext context);

    ITaskState loadTaskState(String taskStateId, ITaskContext context);

    void saveTaskState(ITaskState taskState, ITaskContext context);
}