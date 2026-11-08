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
            // 1.7-C7-004（plan 2545 W-10）：负值在 RetryPolicy 中的语义是「无限重试」
            // （isExceedRetryCount max<0 恒 false），而多数框架惯例 -1=禁用重试——语义反转
            // 是配置陷阱（毒记录 + 负配置 → chunk 线程永久自旋）。装配期显式失败。
            if (getMaxRetryCount() < 0)
                throw new IllegalArgumentException(
                        "nop.err.batch.retry-max-count-negative:maxRetryCount=" + getMaxRetryCount());
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
