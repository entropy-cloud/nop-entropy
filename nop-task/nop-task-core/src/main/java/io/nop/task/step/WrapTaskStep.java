package io.nop.task.step;

import io.nop.task.ITaskContext;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

public class WrapTaskStep extends AbstractTaskStep {
    private ITaskStep step;

    public WrapTaskStep() {
    }

    public WrapTaskStep(ITaskStep step) {
        this.step = step;
    }

    public ITaskStep getStep() {
        return step;
    }

    public void setStep(ITaskStep step) {
        this.step = step;
    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskContext context) {
        return step.execute(state.getRunId(), state, context);
    }
}
