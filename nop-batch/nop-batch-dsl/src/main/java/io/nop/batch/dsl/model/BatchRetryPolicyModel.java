package io.nop.batch.dsl.model;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.batch.dsl.model._gen._BatchRetryPolicyModel;
import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.core.context.IEvalContext;

public class BatchRetryPolicyModel extends _BatchRetryPolicyModel {
    private IRetryPolicy<? extends IEvalContext> retryPolicy;

    public BatchRetryPolicyModel() {

    }

    public IRetryPolicy<? extends IEvalContext> buildRetryPolicy() {
        if (retryPolicy != null)
            return retryPolicy;

        RetryPolicy<IEvalContext> policy = new RetryPolicy<>();
        if (getRetryDelay() != null)
            policy.setRetryDelay(getRetryDelay());
        if (getMaxRetryCount() != null) {
            policy.setMaxRetryCount(getMaxRetryCount());
        }
        if (getExponentialDelay() != null)
            policy.setExponentialDelay(getExponentialDelay());
        if (getJitterRatio() != null) {
            policy.setJitterRatio(getJitterRatio());
        }
        if (getExceptionFilter() != null)
            policy.setExceptionFilter((err, ctx) -> {
                return ConvertHelper.toTruthy(getExceptionFilter().call2(null, err, ctx, ctx.getEvalScope()));
            });

        this.retryPolicy = policy;
        return policy;
    }
}
