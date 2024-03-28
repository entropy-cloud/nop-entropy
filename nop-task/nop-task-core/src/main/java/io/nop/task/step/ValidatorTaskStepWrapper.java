package io.nop.task.step;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepResult;
import jakarta.annotation.Nonnull;

public class ValidatorTaskStepWrapper extends DelegateTaskStep {
    private final IEvalAction validator;
    private final IEvalAction onReload;

    public ValidatorTaskStepWrapper(ITaskStep taskStep, IEvalAction validator, IEvalAction onReload) {
        super(taskStep);
        this.validator = validator;
        this.onReload = onReload;
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepRuntime stepRt) {
        if (stepRt.isRecoverMode()) {
            if (onReload != null)
                onReload.invoke(stepRt);
        } else {
            if (validator != null)
                validator.invoke(stepRt);
        }
        return getTaskStep().execute(stepRt);
    }
}