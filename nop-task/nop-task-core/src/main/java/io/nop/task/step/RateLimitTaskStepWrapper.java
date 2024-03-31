package io.nop.task.step;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.concurrent.ratelimit.DefaultRateLimiter;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;

import static io.nop.task.TaskErrors.ERR_TASK_REQUEST_RATE_EXCEED_LIMIT;

public class RateLimitTaskStepWrapper extends DelegateTaskStep {
    private final double requestPerSecond;
    private final int maxWait;
    private final IEvalAction keyExpr;

    public RateLimitTaskStepWrapper(ITaskStep taskStep, double requestPerSecond, int maxWait, IEvalAction keyExpr) {
        super(taskStep);
        this.requestPerSecond = requestPerSecond;
        this.maxWait = maxWait;
        this.keyExpr = keyExpr;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        String key = getKey(stepRt);

        IRateLimiter rateLimiter = (IRateLimiter) stepRt.getTaskRuntime().computeAttributeIfAbsent(key, k -> {
            return new DefaultRateLimiter(requestPerSecond);
        });

        if (!rateLimiter.tryAcquire(1, maxWait))
            throw TaskStepHelper.newError(getLocation(), stepRt, ERR_TASK_REQUEST_RATE_EXCEED_LIMIT);

        return getTaskStep().execute(stepRt);
    }

    private String getKey(ITaskStepRuntime stepRt) {
        String key;
        if (keyExpr == null) {
            key = stepRt.getStepId();
        } else {
            key = ConvertHelper.toString(keyExpr.invoke(stepRt));
            if (StringHelper.isEmpty(key))
                key = stepRt.getStepId();
        }
        return "rate-limit:" + key;
    }
}
