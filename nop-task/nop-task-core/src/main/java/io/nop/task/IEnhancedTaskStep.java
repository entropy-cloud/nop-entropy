package io.nop.task;

import io.nop.api.core.util.ISourceLocationGetter;

public interface IEnhancedTaskStep extends ISourceLocationGetter {
    String getStepName();

    TaskStepResult executeWithParentRt(ITaskStepRuntime parentRt);
}
