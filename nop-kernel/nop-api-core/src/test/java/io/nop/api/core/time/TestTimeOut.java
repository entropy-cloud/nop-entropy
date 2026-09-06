/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.time;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTimeOut {

    private final MutableClock clock = new MutableClock();

    static class MutableClock implements IClock {
        long millis = 1_000_000L;

        void advance(long delta) {
            millis += delta;
        }

        @Override
        public long currentTimeMillis() {
            return millis;
        }

        @Override
        public LocalDate currentDate() {
            return LocalDate.now();
        }

        @Override
        public LocalDateTime currentDateTime() {
            return LocalDateTime.now();
        }
    }

    @AfterEach
    void restoreClock() {
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
    }

    /**
     * 回归：isExpired必须基于当前时间判断，而不是恒等于初始timeout是否为0。
     */
    @Test
    public void testIsExpired() {
        CoreMetrics.registerClock(clock);

        // timeout=0：立刻超时
        assertTrue(TimeOut.from(0).isExpired());

        // timeout<0：永不超时
        assertFalse(TimeOut.from(-1).isExpired());

        // timeout>0：未到期不超时，时间流逝超过timeout后超时
        TimeOut timeout = TimeOut.from(100);
        assertFalse(timeout.isExpired(), "not expired before timeout elapses");
        clock.advance(50);
        assertFalse(timeout.isExpired());
        clock.advance(60);
        assertTrue(timeout.isExpired(), "expired after timeout elapses");
    }
}
