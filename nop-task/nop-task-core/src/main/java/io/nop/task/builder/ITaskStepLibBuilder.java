package io.nop.task.builder;

import io.nop.task.ITaskStepLib;
import io.nop.task.model.TaskFlowModel;

public interface ITaskStepLibBuilder {
    ITaskStepLib buildTaskStepLib(TaskFlowModel taskFlowModel);
}
