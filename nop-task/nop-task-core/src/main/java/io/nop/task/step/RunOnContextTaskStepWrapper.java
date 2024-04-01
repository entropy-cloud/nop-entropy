package io.nop.task.step;

import io.nop.api.core.context.IContext;
import io.nop.api.core.util.FutureHelper;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;

import java.util.concurrent.CompletableFuture;

/**
 * 确保步骤在当前context上运行。如果同时存在多个步骤runOnContext，实际上会导致它们串行执行
 */
public class RunOnContextTaskStepWrapper extends DelegateTaskStep {
    public RunOnContextTaskStepWrapper(ITaskStep taskStep) {
        super(taskStep);
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        IContext context = stepRt.getContext();
        CompletableFuture<TaskStepReturn> future = new CompletableFuture<>();
        context.execute(() -> {
            try {
                TaskStepReturn result = getTaskStep().execute(stepRt);
                FutureHelper.bindResult(result.getReturnPromise(), future);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return TaskStepReturn.ASYNC(null, future);
    }
}
