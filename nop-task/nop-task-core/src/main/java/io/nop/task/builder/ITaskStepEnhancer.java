package io.nop.task.builder;

import io.nop.task.model.TaskStepModel;
import io.nop.task.step.EnhancedTaskStep;

public interface ITaskStepEnhancer {
    EnhancedTaskStep buildEnhanced(TaskStepModel stepModel, ITaskStepBuilder stepBuilder);
}
