package io.nop.task.step;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.semaphore.ISemaphore;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;

import static io.nop.core.CoreErrors.ARG_KEY;
import static io.nop.task.TaskErrors.ARG_STEP_PATH;
import static io.nop.task.TaskErrors.ARG_TASK_INSTANCE_ID;
import static io.nop.task.TaskErrors.ARG_TASK_NAME;
import static io.nop.task.TaskErrors.ERR_TASK_THROTTLE_TIMEOUT;

public class ThrottleTaskStepWrapper extends DelegateTaskStep {

    private final boolean global;
    private final int maxConcurrency;
    private final int maxWait;

    private final IEvalAction keyExpr;

    public ThrottleTaskStepWrapper(ITaskStep taskStep, boolean global,
                                   int maxConcurrency, int maxWait, IEvalAction keyExpr) {
        super(taskStep);
        this.global = global;
        this.maxConcurrency = maxConcurrency;
        this.maxWait = maxWait;
        this.keyExpr = keyExpr;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        String key = getKey(stepRt);
        ISemaphore semaphore = stepRt.getTaskRuntime().getSemaphore(key, maxConcurrency, global);
        if (!semaphore.tryAcquire(1, maxWait)) {
            throw new NopException(ERR_TASK_THROTTLE_TIMEOUT)
                    .param(ARG_KEY, key).param(ARG_TASK_NAME, stepRt.getTaskRuntime().getTaskName())
                    .param(ARG_TASK_INSTANCE_ID, stepRt.getTaskRuntime().getTaskInstanceId())
                    .param(ARG_STEP_PATH, stepRt.getStepPath());
        }
        try {
            return getTaskStep().execute(stepRt).whenComplete((ret, err) -> {
                semaphore.release(1);
            });
        } catch (Exception e) {
            semaphore.release(1);
            throw NopException.adapt(e);
        }
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