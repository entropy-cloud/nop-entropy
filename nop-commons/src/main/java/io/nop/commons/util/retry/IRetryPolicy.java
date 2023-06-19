/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.util.retry;

public interface IRetryPolicy<C> {

    /**
     * 得到下次重试的等待时间。如果返回-1， 则表示不再允许重试
     *
     * @param ex         异常信息
     * @param retryTimes 已经重试的次数。第一次重试retryTimes=0
     * @return 返回-1表示不再重试
     */
    long getRetryDelay(Throwable ex, int retryTimes, C context);

    @FunctionalInterface
    interface IRetryExceptionFilter<C> {
        boolean isRecoverable(Throwable e, C context);
    }
}