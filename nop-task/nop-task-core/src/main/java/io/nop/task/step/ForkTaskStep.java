package io.nop.task.step;

import io.nop.task.ITaskContext;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

public class ForkTaskStep extends AbstractTaskStep {
    private ITaskStep body;

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskContext context) {
        return null;
    }
}
