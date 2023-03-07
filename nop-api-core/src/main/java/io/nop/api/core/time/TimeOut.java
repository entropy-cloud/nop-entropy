/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.time;

import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.util.ApiHeaders;

/**
 * 用于timeout相关计算的帮助类。它根据timeout设置和系统当前时间不断更新剩余可用时间
 */
public class TimeOut {
    private final long timeout;
    private final long expireTime;

    public TimeOut(long timeout) {
        this.timeout = timeout;
        this.expireTime = CoreMetrics.timeoutToExpireTime(timeout);
    }

    public static TimeOut from(long timeout) {
        return new TimeOut(timeout);
    }

    public static TimeOut fromMessage(ApiMessage message) {
        return from(ApiHeaders.getTimeout(message, -1L));
    }

    /**
     * 是否已经到达超时时间
     */
    public boolean isExpired() {
        return timeout == 0;
    }

    /**
     * 是否永不超时
     */
    public boolean isNeverExpired() {
        return timeout < 0;
    }

    public long getExpireTime() {
        return expireTime;
    }

    /**
     * 剩余可用时间。
     *
     * @return 小于0表示永不超时。等于0则表示当前已经超时。
     */
    public long getRemainingTime() {
        long remainingTime = CoreMetrics.expireTimeToTimeout(expireTime);
        return remainingTime;
    }

    /**
     * 更新ApiMessage的timeout信息为 remainingTime
     */
    public void updateTimeoutHeader(ApiMessage message) {
        if (timeout < 0) {
            ApiHeaders.setTimeout(message, null);
        } else {
            ApiHeaders.setTimeout(message, getRemainingTime());
        }
    }
}
