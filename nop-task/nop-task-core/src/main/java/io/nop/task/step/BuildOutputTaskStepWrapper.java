package io.nop.task.step;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
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
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        return getTaskStep().execute(stepRt).thenApply(res -> {
            stepRt.setValue(TaskConstants.VAR_STEP_RESULT, res.getOutputs());
            Map<String, Object> result = res.getOutputs() != null ? new LinkedHashMap<>(res.getOutputs())
                    : new LinkedHashMap<>();
            outputExprs.forEach((name, expr) -> {
                if (stepRt.isNeedOutput(name)) {
                    if (expr == null) {
                        if (!result.containsKey(name))
                            result.put(name, stepRt.getValue(name));
                    } else {
                        result.put(name, expr.invoke(stepRt));
                    }
                } else {
                    result.remove(name);
                }
            });
            return TaskStepReturn.of(res.getNextStepName(), result);
        });
    }
}
