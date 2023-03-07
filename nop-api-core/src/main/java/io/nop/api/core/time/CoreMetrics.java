/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.time;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class CoreMetrics {
    static final IClock DEFAULT_CLOCK = new IClock() {
        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        public long nanoTime() {
            return System.nanoTime();
        }
    };

    static IClock s_clock = DEFAULT_CLOCK;

    public static IClock defaultClock() {
        return DEFAULT_CLOCK;
    }

    public static long currentTimeMillis() {
        return s_clock.currentTimeMillis();
    }

    public static Timestamp currentTimestamp() {
        return new Timestamp(currentTimeMillis());
    }

    public static long nanoTime() {
        return s_clock.nanoTime();
    }

    public static long timeoutToExpireTime(long timeout) {
        if (timeout < 0)
            return Long.MAX_VALUE;
        return currentTimeMillis() + timeout;
    }

    public static boolean isExpiredNanos(long nanos) {
        return nanoTime() - nanos < 0;
    }

    /**
     * <ol> timeout < 0 表示不会超时，</ol>
     * <ol> timeout=0表示不能等待，不过不能立刻得到服务，那么就要超时，</ol>
     * <ol> timeout>0表示等待一段时间后会超时 </ol>
     */
    public static long expireTimeToTimeout(long expireTime) {
        if (expireTime == Long.MAX_VALUE)
            return -1L;

        long timeout = expireTime - currentTimeMillis();
        if (timeout < 0)
            timeout = 0;
        return timeout;
    }

    public static long nanoTimeDiff(long beginTime) {
        return nanoTime() - beginTime;
    }

    public static long nanoToMillis(long nanoTime) {
        return TimeUnit.NANOSECONDS.toMillis(nanoTime);
    }

    public static int calcNewTimeout(int timeout, long beginTime, Function<ErrorCode, NopException> errorFactory) {
        if (timeout <= 0)
            return timeout;

        int diff = (int) (CoreMetrics.currentTimeMillis() - beginTime);
        if (diff < 0)
            return timeout;

        if (timeout <= diff)
            throw errorFactory.apply(ApiErrors.ERR_TIMEOUT);

        timeout -= diff;
        return timeout;
    }
}
