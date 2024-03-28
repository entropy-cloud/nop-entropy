package io.nop.task.step;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepResult;
import jakarta.annotation.Nonnull;

import java.util.LinkedHashMap;
import java.util.Map;

public class BuildOutputTaskStepWrapper extends DelegateTaskStep {
    private final Map<String, IEvalAction> outputExprs;

    public BuildOutputTaskStepWrapper(ITaskStep taskStep, Map<String, IEvalAction> outputExprs) {
        super(taskStep);
        this.outputExprs = outputExprs;
    }

    public Map<String, IEvalAction> getOutputExprs() {
        return outputExprs;
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepRuntime stepRt) {
        return getTaskStep().execute(stepRt).thenApply(res -> {
            Map<String, Object> result = res.getReturnValues() != null ? new LinkedHashMap<>(res.getReturnValues())
                    : new LinkedHashMap<>();
            outputExprs.forEach((name, expr) -> {
                if (stepRt.isNeedOutput(name)) {
                    result.put(name, expr.invoke(stepRt));
                } else {
                    result.remove(name);
                }
            });
            return TaskStepResult.of(res.getNextStepName(), result);
        });
    }
}
