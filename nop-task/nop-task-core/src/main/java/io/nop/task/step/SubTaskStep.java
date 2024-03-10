/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.task.ITask;
import io.nop.task.ITaskContext;
import io.nop.task.ITaskManager;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

public class SubTaskStep extends AbstractTaskStep {
    private String taskName;

    private ITaskManager taskManager;

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public ITaskManager getTaskManager() {
        return taskManager;
    }

    public void setTaskManager(ITaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskContext context) {
        Long taskVersion = (Long) state.getStateBean();
        if (taskVersion == null)
            taskVersion = -1L;

        ITaskContext subContext = context.newChildContext(taskName, taskVersion);
        state.setStateBean(subContext.getTaskVersion());
        saveState(state, context);

        ITask task = taskManager.getTask(subContext);

        Object result = task.execute(subContext);
        if (result instanceof TaskStepResult) {
            result = ((TaskStepResult) result).getReturnValue();
        }

        return TaskStepResult.of(getNextStepId(), result);
    }
}