package io.nop.task.builder;

import io.nop.task.IEnhancedTaskStep;
import io.nop.task.ITaskStep;
import io.nop.task.model.TaskStepModel;

public interface ITaskStepEnhancer {
    ITaskStep buildDecorated(TaskStepModel stepModel, ITaskStepBuilder stepBuilder);

    IEnhancedTaskStep buildEnhanced(TaskStepModel stepModel, ITaskStepBuilder stepBuilder);
}
