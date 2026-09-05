package io.nop.task.step;

import io.nop.api.core.util.ICancelToken;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
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
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        ICancelToken outerToken = stepRt.getCancelToken();
        TaskStepReturn ret = TaskStepHelper.timeout(timeout, cancellable -> {
            stepRt.setCancelToken(cancellable);
            return getTaskStep().execute(stepRt);
        }, outerToken, stepRt.getTaskRuntime().getScheduledExecutor());
        // 步骤结束后恢复外层 token（plan 349 Phase 4，对照 TaskStepHelper.withCancellable 的复位模式）：
        // 修复后运行时不再持有已完结的 timeout cancellable
        return ret.whenComplete((v, e) -> stepRt.setCancelToken(outerToken));
    }
}
