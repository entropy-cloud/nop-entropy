package io.nop.task.step;

import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
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
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        TaskStepReturn result = getTaskStep().execute(stepRt);
        return result.runOnContext();
    }
}
