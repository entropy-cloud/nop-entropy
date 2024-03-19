package io.nop.task;

import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.ISourceLocationGetter;

public interface IEnhancedTaskStep extends ISourceLocationGetter {
    String getStepName();

    TaskStepResult execute(ITaskStepState parentState, ICancelToken cancelToken,
                           ITaskRuntime taskRt);
}
