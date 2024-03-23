package io.nop.task.step;

import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepResult;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;

public class TimeoutTaskStepWrapper extends DelegateTaskStep {
    private final long timeout;

    public TimeoutTaskStepWrapper(ITaskStep taskStep, long timeout) {
        super(taskStep);
        this.timeout = timeout;
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepRuntime stepRt) {
        return TaskStepHelper.timeout(timeout, cancellable -> {
            stepRt.setCancelToken(cancellable);
            return getTaskStep().execute(stepRt);
        }, stepRt.getCancelToken(), stepRt.getTaskRuntime().getScheduledExecutor());
    }
}
