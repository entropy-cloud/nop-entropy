package io.nop.task.step;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStepResultAggregator;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepResult;

public class EvalTaskStepResultAggregator implements ITaskStepResultAggregator {
    private final IEvalAction action;

    public EvalTaskStepResultAggregator(IEvalAction action) {
        this.action = action;
    }

    @Override
    public TaskStepResult aggregate(MultiStepResultBean results, ITaskStepRuntime stepRt) {
        return TaskStepResult.of(null, stepRt);
    }
}
