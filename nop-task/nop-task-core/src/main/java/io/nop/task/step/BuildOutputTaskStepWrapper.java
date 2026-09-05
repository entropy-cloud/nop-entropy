package io.nop.task.step;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class BuildOutputTaskStepWrapper extends DelegateTaskStep {
    static final Logger LOG = LoggerFactory.getLogger(BuildOutputTaskStepWrapper.class);

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
            // SUSPEND 直接透传（plan 349 Phase 1）：挂起点不得提前求值 output 表达式，
            // 也不得被 RETURN(...) 重建为普通返回（否则挂起步骤被驱动为 COMPLETED 并抛 UNKNOWN_NEXT_STEP）
            if (res.isSuspend())
                return res;

            stepRt.setValue(TaskConstants.VAR_STEP_RESULT, res.getOutputs());
            stepRt.setValue(TaskConstants.VAR_RESULT, res.getOutput(TaskConstants.VAR_RESULT));


            // 这里output是独立选择并独立计算的，原则上没有先后关系，否则当output没有被选择时可能会跳过计算
            Map<String, Object> result = res.getOutputs() != null ? new LinkedHashMap<>(res.getOutputs())
                    : new LinkedHashMap<>();
            outputExprs.forEach((name, expr) -> {
                if (stepRt.needOutput(name)) {
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
            return TaskStepReturn.RETURN(res.getNextStepName(), result);
        });
    }
}
