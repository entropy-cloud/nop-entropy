package io.nop.task.builder;

import io.nop.task.model.TaskStepModel;
import io.nop.task.step.AbstractTaskStep;

public interface ITaskStepBuilder {
    AbstractTaskStep buildStep(TaskStepModel stepModel);
}
