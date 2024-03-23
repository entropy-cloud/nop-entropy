package io.nop.task.builder;

import io.nop.task.IEnhancedTaskStep;
import io.nop.task.ITaskStep;
import io.nop.task.model.TaskStepModel;
import io.nop.task.step.AbstractTaskStep;

public interface ITaskStepBuilder {
    AbstractTaskStep buildRawStep(TaskStepModel stepModel);

    ITaskStep buildDecoratedStep(TaskStepModel stepModel);

    IEnhancedTaskStep buildEnhancedStep(TaskStepModel stepModel);
}