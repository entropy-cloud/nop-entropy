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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTimeOut {

    private final MutableClock clock = new MutableClock();

    /**
     * R4-32（CL-034）：TimeOut 内部期限判定换源 nanoTime——测试时钟需双针可控
     * （wall 与 nanoTime 独立推进/回拨），nanoTime 针独立于墙钟调整。
     */
    static class MutableClock implements IClock {
        long millis = 1_000_000L;
        long nanos = 10_000_000_000L;

        void advance(long delta) {
            millis += delta;
            nanos += TimeUnit.MILLISECONDS.toNanos(delta);
        }

        // 单调针独立推进/回拨（纳秒口径）
        void advanceNanos(long deltaNanos) {
            nanos += deltaNanos;
        }

        // 墙钟独立调整（不影响单调针）
        void rollbackWall(long deltaMillis) {
            millis -= deltaMillis;
        }

        void forwardWall(long deltaMillis) {
            millis += deltaMillis;
        }

        @Override
        public long currentTimeMillis() {
            return millis;
        }

        @Override
        public long nanoTime() {
            return nanos;
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

    /**
     * R4-32（CL-034）回拨腿：墙钟回拨不影响单调期限判定（修复前 wall 回拨→期限顺延 RED）。
     */
    @Test
    public void testWallClockRollbackDoesNotExtendDeadline() {
        CoreMetrics.registerClock(clock);

        TimeOut timeout = TimeOut.from(100);
        assertFalse(timeout.isExpired());

        // 墙钟回拨 1h：单调期限判定不受影响（按真实单调流逝过期）
        clock.rollbackWall(3_600_000);
        clock.advanceNanos(TimeUnit.MILLISECONDS.toNanos(150));
        assertTrue(timeout.isExpired(), "deadline must follow monotonic clock despite wall rollback");
    }

    /**
     * R4-32（CL-034）前跳腿：墙钟前跳不提前触发超时。
     */
    @Test
    public void testWallClockForwardJumpDoesNotExpireEarly() {
        CoreMetrics.registerClock(clock);

        TimeOut timeout = TimeOut.from(100);
        clock.forwardWall(3_600_000);
        assertFalse(timeout.isExpired(), "wall forward jump must not expire monotonic deadline early");

        clock.advanceNanos(TimeUnit.MILLISECONDS.toNanos(150));
        assertTrue(timeout.isExpired());
    }
}
