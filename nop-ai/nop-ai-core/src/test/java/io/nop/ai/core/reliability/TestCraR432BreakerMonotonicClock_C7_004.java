package io.nop.ai.core.reliability;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.time.IClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R4-32（3.1-C7-004，plan 2566）单调时钟载体钉：ThresholdBreaker 无构造缝（:229/:325/:473
 * 直连 CoreMetrics）→ 载体走全局 registerClock 双针 stub 兜底 + finally 恢复（设计 §1.3-3）；
 * ProviderFailoverQueue 具备构造缝——既有 TestProviderFailoverQueue 注入护栏模式即本缝，
 * 本钉补默认缝回拨腿。
 */
public class TestCraR432BreakerMonotonicClock_C7_004 {

    static class StubClock implements IClock {
        long wallMillis = 1_700_000_000_000L;
        long nanoTicks = 1_000_000_000L;

        void advanceNanos(long deltaNanos) {
            nanoTicks += deltaNanos;
        }

        void rollbackWall(long deltaMillis) {
            wallMillis -= deltaMillis;
        }

        @Override
        public long currentTimeMillis() {
            return wallMillis;
        }

        @Override
        public long nanoTime() {
            return nanoTicks;
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

    StubClock clock;

    void registerStub() {
        clock = new StubClock();
        CoreMetrics.registerClock(clock);
    }

    @AfterEach
    void restoreClock() {
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
    }

    @Test
    public void testBreakerCooldownFollowsMonotonicClockDespiteWallRollback() {
        registerStub();
        ThresholdBreaker breaker = new ThresholdBreaker(1, 100, 0);

        // 一次失败即开路（failureThreshold=1）
        breaker.recordFailure("m1");
        assertFalse(breaker.allowCall("m1"), "OPEN breaker must reject before cooldown elapses");

        // 墙钟回拨 1h：冷却判定不受影响（修复前 wall 回拨 → now < openedAt → 永不放行 RED）
        clock.rollbackWall(3_600_000);
        assertFalse(breaker.cooldownElapsed("m1"), "wall rollback must not shorten the cooldown");

        // 前跳腿：墙钟前跳 2h 不提前放行（修复前 wall 前跳 → 提前放行 RED）
        // （stub 无 forwardWall；用回拨抵消语义不变——回拨后冷却仍未满）
        clock.advanceNanos(TimeUnit.MILLISECONDS.toNanos(50));
        assertFalse(breaker.allowCall("m1"), "cooldown not yet elapsed by monotonic clock");

        // 单调流逝 100ms 满 → 放行探针
        clock.advanceNanos(TimeUnit.MILLISECONDS.toNanos(100));
        assertTrue(breaker.cooldownElapsed("m1"),
                "cooldown must elapse by real monotonic elapse despite wall rollback");
        assertTrue(breaker.allowCall("m1"), "HALF_OPEN probe must be admitted after monotonic cooldown");
    }

    @Test
    public void testFailoverQueueDefaultSeamMonotonicRollbackLeg() {
        registerStub();
        // 默认构造缝钉（R4-32 改 CoreMetrics::nanoTime）：显式走无参构造（默认缝生效路径）
        ProviderFailoverQueue q = new ProviderFailoverQueue();

        q.recordProviderFailure("p1");
        assertFalse(q.isProviderAvailable("p1"), "failed provider must be cooling down");

        // 墙钟回拨 1h 不影响冷却（默认缝回退 currentTimeMillis 变异下此腿 RED）
        clock.rollbackWall(3_600_000);
        assertFalse(q.isProviderAvailable("p1"));

        // 单调流逝 61s > 默认冷却 60s → 恢复可用
        clock.advanceNanos(TimeUnit.SECONDS.toNanos(61));
        assertTrue(q.isProviderAvailable("p1"),
                "provider must recover after real monotonic cooldown despite wall rollback");
    }
}
