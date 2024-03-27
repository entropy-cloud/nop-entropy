/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.model;

import io.nop.task.ITask;
import io.nop.task.ITaskStepLib;
import io.nop.task.TaskConstants;
import io.nop.task.builder.TaskFlowBuilder;
import io.nop.task.builder.TaskStepLibBuilder;
import io.nop.task.model._gen._TaskFlowModel;

public class TaskFlowModel extends _TaskFlowModel implements IGraphTaskStepModel {
    private ITask task;

    private ITaskStepLib taskStepLib;

    public TaskFlowModel() {

    }

    public synchronized ITask getTask() {
        if (task == null) {
            task = new TaskFlowBuilder().buildTask(this);
        }
        return task;
    }

    public void setTask(ITask task) {
        this.task = task;
    }

    public ITaskStepLib getTaskStepLib() {
        if (taskStepLib == null) {
            taskStepLib = new TaskStepLibBuilder().buildTaskStepLib(this);
        }
        return taskStepLib;
    }

    public void setTaskStepLib(ITaskStepLib taskStepLib) {
        this.taskStepLib = taskStepLib;
    }

    @Override
    public String getName() {
        String name = super.getName();
        if (name == null)
            return TaskConstants.MAIN_STEP_NAME;
        return name;
    }

    @Override
    public String getType() {
        return TaskConstants.STEP_TYPE_TASK;
    }
}
