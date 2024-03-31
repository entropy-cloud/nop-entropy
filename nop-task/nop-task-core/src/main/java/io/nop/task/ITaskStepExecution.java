package io.nop.task;

import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ISourceLocationGetter;
import jakarta.annotation.Nonnull;

import java.util.concurrent.CompletionStage;

/**
 * 负责将ITaskStep的输入输出与父步骤的scope绑定，从父scope中读取变量作为input，将output中的结果数据设置到父scope中。
 */
public interface ITaskStepExecution extends ISourceLocationGetter {
    String getStepName();

    @Nonnull
    TaskStepReturn executeWithParentRt(ITaskStepRuntime parentRt);

    default CompletionStage<TaskStepReturn> executeAsync(ITaskStepRuntime stepRt) {
        try {
            return executeWithParentRt(stepRt).getReturnPromise();
        } catch (Exception e) {
            return FutureHelper.reject(e);
        }
    }
}
