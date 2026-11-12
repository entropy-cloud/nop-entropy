/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.time;

import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.util.ApiHeaders;

import java.util.concurrent.TimeUnit;

/**
 * 用于timeout相关计算的帮助类。它根据timeout设置和系统当前时间不断更新剩余可用时间
 *
 * <p>R4-32（CL-034，plan 2566）：内部期限判定换源单调时钟 {@link CoreMetrics#nanoTime()}
 * （绕开 wall 版 timeoutToExpireTime/expireTimeToTimeout），消除墙钟回拨导致的期限误判/
 * 前跳导致的提前超时。构造入参 timeout 保持毫秒口径，类内换算纳秒与 nanoTime 比较。
 */
public class TimeOut {
    private final long timeout;
    // 单调时钟域到期点（纳秒）；timeout<0 时为 Long.MAX_VALUE（永不超时）
    private final long expireTime;

    public TimeOut(long timeout) {
        this.timeout = timeout;
        this.expireTime = timeout < 0 ? Long.MAX_VALUE
                : CoreMetrics.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
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
        // timeout<0表示永不超时；timeout=0表示立刻超时；timeout>0按剩余时间判断
        if (timeout < 0)
            return false;
        return CoreMetrics.nanoTime() - expireTime >= 0;
    }

    /**
     * 是否永不超时
     */
    public boolean isNeverExpired() {
        return timeout < 0;
    }

    /**
     * R4-32（CL-034[R2-m4]）：自单调时钟换源后本值语义由 epoch 毫秒翻转为<strong>单调相对值
     * （纳秒）</strong>，仅可与同进程 {@code CoreMetrics.nanoTime()} 返回值比较或相减求剩余，
     * 不得持久化或跨进程传输。timeout<0 时返回 Long.MAX_VALUE。
     */
    public long getExpireTime() {
        return expireTime;
    }

    /**
     * 剩余可用时间。
     *
     * @return 小于0表示永不超时。等于0则表示当前已经超时。
     */
    public long getRemainingTime() {
        if (timeout < 0)
            return -1L;
        long remainingTime = TimeUnit.NANOSECONDS.toMillis(expireTime - CoreMetrics.nanoTime());
        if (remainingTime < 0)
            remainingTime = 0;
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
