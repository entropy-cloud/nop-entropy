/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.commons.util.StringHelper;
import io.nop.task.ITask;
import io.nop.task.ITaskManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepResult;
import jakarta.annotation.Nonnull;

public class CallTaskStep extends AbstractTaskStep {
    private String taskName;

    private long taskVersion;

    public long getTaskVersion() {
        return taskVersion;
    }

    public void setTaskVersion(long taskVersion) {
        this.taskVersion = taskVersion;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepRuntime stepRt) {
        ITaskRuntime taskRt = stepRt.getTaskRuntime();
        ITaskManager taskManager = taskRt.getTaskManager();

        String taskName = taskRt.getTaskName();
        long taskVersion = taskRt.getTaskVersion();

        String taskId = stepRt.getStateBean(String.class);
        ITask task;
        ITaskRuntime subRt;
        if (StringHelper.isEmpty(taskId)) {
            subRt = taskRt.newChildContext(taskName, taskVersion, stepRt.isSupportPersist());
            task = taskManager.getTask(taskName, taskVersion);

            stepRt.setStateBean(subRt.getTaskInstanceId());
            stepRt.saveState();
        } else {
            subRt = taskManager.getTaskRuntime(taskId, taskRt.getSvcCtx());
            task = taskManager.getTask(taskName, taskVersion);
        }

        return task.execute(subRt);
    }
}