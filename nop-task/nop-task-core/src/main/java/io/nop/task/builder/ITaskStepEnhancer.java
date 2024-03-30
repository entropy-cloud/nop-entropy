package io.nop.task.builder;

import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStep;
import io.nop.task.model.TaskStepModel;

public interface ITaskStepEnhancer {
    ITaskStep buildDecorated(TaskStepModel stepModel, ITaskStepBuilder stepBuilder);

    ITaskStepExecution buildEnhanced(TaskStepModel stepModel, ITaskStepBuilder stepBuilder);
}
