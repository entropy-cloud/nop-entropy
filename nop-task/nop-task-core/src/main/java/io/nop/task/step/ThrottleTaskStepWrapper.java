package io.nop.task.step;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;

public class ThrottleTaskStepWrapper extends DelegateTaskStep {

    private final boolean global;
    private final int maxConcurrent;
    private final int maxWait;

    private final IEvalAction keyExpr;

    public ThrottleTaskStepWrapper(ITaskStep taskStep, boolean global,
                                   int maxConcurrent, int maxWait, IEvalAction keyExpr) {
        super(taskStep);
        this.global = global;
        this.maxConcurrent = maxConcurrent;
        this.maxWait = maxWait;
        this.keyExpr = keyExpr;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        String key = getKey(stepRt);

        return getTaskStep().execute(stepRt);
    }

    private String getKey(ITaskStepRuntime stepRt) {
        String key;
        if (keyExpr == null) {
            key = stepRt.getStepPath();
        } else {
            key = ConvertHelper.toString(keyExpr.invoke(stepRt));
            if (StringHelper.isEmpty(key))
                key = stepRt.getStepPath();
        }
        return key;
    }
}
