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
            } catch (Throwable t) {
                // W-06（CRA 8.48 R4-08，MA-13 裁定 B）：catch 收宽为 Throwable——context.execute
                // 派发的步骤体抛出 Error 也保证 future 必然终结（修复前悬挂零信号）；
                // Error 原样 rethrow（runnable 语义同 executor 池线程：complete 不豁免线程侧传播）
                future.completeExceptionally(t);
                if (t instanceof Error)
                    throw (Error) t;
            }
        });
        return TaskStepReturn.ASYNC(null, future);
    }
}
