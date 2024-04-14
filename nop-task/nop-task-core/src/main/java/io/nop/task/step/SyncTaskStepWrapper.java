package io.nop.task.step;

import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;

public class SyncTaskStepWrapper extends DelegateTaskStep {
    public SyncTaskStepWrapper(ITaskStep taskStep) {
        super(taskStep);
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        // 同步等待执行结果
        return getTaskStep().execute(stepRt).sync();
    }
}
