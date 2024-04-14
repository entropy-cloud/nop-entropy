package io.nop.task;

import io.nop.task.model.TaskDecoratorModel;
import io.nop.task.model.TaskStepModel;

public interface ITaskStepDecorator {
    ITaskStep decorate(ITaskStep step, TaskDecoratorModel config, TaskStepModel stepModel);
}
