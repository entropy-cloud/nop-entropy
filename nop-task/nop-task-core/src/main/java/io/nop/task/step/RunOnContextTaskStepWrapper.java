package io.nop.task.step;

import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepResult;
import jakarta.annotation.Nonnull;

/**
 * 确保异步回调在当前context上运行
 */
public class RunOnContextTaskStepWrapper extends DelegateTaskStep {
    public RunOnContextTaskStepWrapper(ITaskStep taskStep) {
        super(taskStep);
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepRuntime stepRt) {
        TaskStepResult result = getTaskStep().execute(stepRt);
        return result.runOnContext();
    }
}
