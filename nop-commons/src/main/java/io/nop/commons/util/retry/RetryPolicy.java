/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.util.retry;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICloneable;
import io.nop.commons.util.MathHelper;

import java.io.Serializable;

public class RetryPolicy<C> implements Serializable, IRetryPolicy<C>, ICloneable {

    private static final long serialVersionUID = 8950466247476591272L;

    /**
     * 最多重试几次，例如重试两次，则加上最初执行的一次，总共会尝试执行3次。
     */
    private int maxRetryCount = 2;
    private int retryDelay = 1000;
    private int maxRetryDelay = 1000 * 60 * 5;
    private boolean exponentialDelay = true;
    private double jitterRatio = 0.3;
    private IRetryExceptionFilter<C> exceptionFilter;

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RetryPolicy[maxRetryCount=").append(maxRetryCount).append(",retryDelay=").append(retryDelay)
                .append(",maxRetryDelay=").append(maxRetryDelay).append(",exponentialDelay=").append(exponentialDelay)
                .append(",randomizationFactor=").append(jitterRatio).append("]");
        return sb.toString();
    }

    public static RetryPolicy createRetryPolicy() {
        return new RetryPolicy();
    }

    public RetryPolicy cloneInstance() {
        RetryPolicy ret = new RetryPolicy();
        ret.maxRetryCount = maxRetryCount;
        ret.retryDelay = retryDelay;
        ret.maxRetryDelay = maxRetryDelay;
        ret.exponentialDelay = exponentialDelay;
        ret.jitterRatio = jitterRatio;
        ret.exceptionFilter = exceptionFilter;
        return ret;
    }

    public static RetryPolicy retryNTimes(int times) {
        RetryPolicy retry = new RetryPolicy();
        retry.setMaxRetryCount(times);
        retry.setRetryDelay(0);
        return retry;
    }

    public double getJitterRatio() {
        return jitterRatio;
    }

    public void setJitterRatio(double jitterRatio) {
        this.jitterRatio = jitterRatio;
    }

    public RetryPolicy withJitterRatio(double jitterRatio) {
        this.setJitterRatio(jitterRatio);
        return this;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public RetryPolicy withMaxRetryCount(int maxRetryCount) {
        this.setMaxRetryCount(maxRetryCount);
        return this;
    }

    public int getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(int retryDelay) {
        Guard.nonNegativeInt(retryDelay, "retryDelay is negative");
        this.retryDelay = retryDelay;
    }

    public RetryPolicy withRetryDelay(int retryDelay) {
        this.setMaxRetryDelay(retryDelay);
        return this;
    }

    public int getMaxRetryDelay() {
        return maxRetryDelay;
    }

    public void setMaxRetryDelay(int maxRetryDelay) {
        Guard.nonNegativeInt(retryDelay, "maxRetryDelay is negative");
        this.maxRetryDelay = maxRetryDelay;
    }

    public RetryPolicy withMaxRetryDelay(int maxRetryDelay) {
        this.setMaxRetryDelay(maxRetryDelay);
        return this;
    }

    public boolean isExponentialDelay() {
        return exponentialDelay;
    }

    public void setExponentialDelay(boolean exponentialDelay) {
        this.exponentialDelay = exponentialDelay;
    }

    public RetryPolicy<C> withExponentialDelay(boolean exponentialDelay) {
        this.setExponentialDelay(exponentialDelay);
        return this;
    }

    public IRetryExceptionFilter<C> getExceptionFilter() {
        return exceptionFilter;
    }

    public void setExceptionFilter(IRetryExceptionFilter<C> exceptionFilter) {
        this.exceptionFilter = exceptionFilter;
    }

    public RetryPolicy<C> withExceptionFilter(IRetryExceptionFilter<C> exceptionFilter) {
        this.setExceptionFilter(exceptionFilter);
        return this;
    }

    public boolean isExceedRetryCount(int retryTimes) {
        int max = this.getMaxRetryCount();
        if (max < 0)
            return false;
        return retryTimes > max;
    }

    public boolean isRecoverableException(Throwable exception, C context) {
        if (exceptionFilter != null)
            return exceptionFilter.isRecoverable(exception, context);
        if (exception instanceof NopException) {
            return !((NopException) exception).isBizFatal();
        }
        return !(exception instanceof Error);
    }

    @Override
    public long getRetryDelay(Throwable e, int retryTimes, C context) {
        if (e != null) {
            if (!isRecoverableException(e, context))
                return -1;
        }
        if (isExceedRetryCount(retryTimes))
            return -1;

        int tryDelay = this.getRetryDelay();

        long timeToSleep = 0;
        if (tryDelay > 0) {
            boolean exponentialDelay = this.isExponentialDelay();
            int maxRetryDelay = this.getMaxRetryDelay();
            if (maxRetryDelay < tryDelay) {
                maxRetryDelay = tryDelay;
            }
            if (exponentialDelay) {
                timeToSleep = tryDelay * (long) Math.pow(2, retryTimes);
            } else {
                timeToSleep = tryDelay * retryTimes;
            }

            if (timeToSleep < 0 || timeToSleep > maxRetryDelay) {
                timeToSleep = maxRetryDelay;
            }

            if (timeToSleep > 0 && jitterRatio > 0) {
                timeToSleep = MathHelper.randomizeLong(timeToSleep, jitterRatio);
            }
        }
        return timeToSleep;
    }
}