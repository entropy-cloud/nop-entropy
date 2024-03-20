package io.nop.task;

import io.nop.api.core.util.ISourceLocationGetter;
import jakarta.annotation.Nonnull;

/**
 * 负责将ITaskStep的输入输出与父步骤的scope绑定，从父scope中读取变量作为input，将output中的结果数据设置到父scope中。
 */
public interface IEnhancedTaskStep extends ISourceLocationGetter {
    String getStepName();

    default TaskStepResult executeWithParentRt(ITaskStepRuntime parentRt){
        return executeWithParentRt(parentRt, null,null);
    }


    @Nonnull
    TaskStepResult executeWithParentRt(ITaskStepRuntime parentRt, String varName, Object varValue);
}
