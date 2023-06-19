package io.nop.task.step;

import io.nop.task.ITaskContext;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

public class WrapStep extends AbstractStep {
    private ITaskStep step;

    public WrapStep() {
    }

    public WrapStep(ITaskStep step) {
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
