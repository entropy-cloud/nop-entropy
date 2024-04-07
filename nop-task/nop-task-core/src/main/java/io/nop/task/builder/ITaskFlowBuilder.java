package io.nop.task.builder;

import io.nop.task.ITask;
import io.nop.task.model.TaskFlowModel;

public interface ITaskFlowBuilder {
    ITask buildTask(TaskFlowModel taskFlowModel);
}
