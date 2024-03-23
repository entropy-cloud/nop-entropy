package io.nop.task;

import io.nop.task.model.TaskDecoratorModel;

public interface ITaskStepDecorator {
    ITaskStep decorate(ITaskStep step, TaskDecoratorModel config);
}
