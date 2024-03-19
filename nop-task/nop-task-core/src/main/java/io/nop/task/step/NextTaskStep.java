package io.nop.task.step;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;
import jakarta.annotation.Nonnull;

import java.util.Set;

public class NextTaskStep extends DelegateTaskStep {
    private final String nextStepName;

    public NextTaskStep(ITaskStep taskStep, String nextStepName) {
        super(taskStep);
        this.nextStepName = Guard.notEmpty(nextStepName, "nextStepName");
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepState stepState, Set<String> outputNames,
                                  ICancelToken cancelToken, ITaskRuntime taskRt) {
        return getTaskStep().execute(stepState, outputNames, cancelToken, taskRt)
                .thenApply(ret -> TaskStepResult.of(nextStepName, ret));
    }
}
