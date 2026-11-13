package io.nop.gateway.core.interceptor;

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
 * R4-32（2.3-C8-005，plan 2566）令牌桶回拨/前跳红证钉：TokenBucket 静态嵌套类无构造缝 →
 * 全局 registerClock 双针 stub 兜底 + finally 恢复（设计 §1.3-3，先例 NopJunitExtension）。
 * <ul>
 *   <li>回拨腿：修复前 wall 回拨 → elapsed&lt;0 → 桶冻结（永不回填）RED；修复后按真实单调流逝回填。</li>
 *   <li>前跳腿（对称）：修复前 wall 前跳 → 集中回填（interval 数暴增一次灌满）RED；修复后不受影响。</li>
 * </ul>
 */
public class TestAiRateLimitTokenBucket_C8_005 {

    static class StubClock implements IClock {
        long wallMillis = 1_700_000_000_000L;
        long nanoTicks = 1_000_000_000L;

        void advanceNanos(long deltaNanos) {
            nanoTicks += deltaNanos;
        }

        void rollbackWall(long deltaMillis) {
            wallMillis -= deltaMillis;
        }

        void forwardWall(long deltaMillis) {
            wallMillis += deltaMillis;
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

    AiRateLimitGatewayInterceptor.TokenBucket bucket(int capacity, long intervalMs) {
        return new AiRateLimitGatewayInterceptor.TokenBucket(capacity, 1.0, intervalMs);
    }

    @Test
    public void testWallRollbackDoesNotFreezeBucket() {
        registerStub();
        AiRateLimitGatewayInterceptor.TokenBucket b = bucket(2, 100);

        // 消费至空
        assertTrue(b.tryConsume());
        assertTrue(b.tryConsume());
        assertFalse(b.tryConsume(), "bucket must be empty after consuming capacity tokens");

        // 墙钟回拨 1h：单调流逝 150ms > interval 100ms → 正常回填 ≥1
        // （修复前 wall 回拨 → elapsed<0 → 桶冻结 RED）
        clock.rollbackWall(3_600_000);
        clock.advanceNanos(TimeUnit.MILLISECONDS.toNanos(150));
        assertTrue(b.tryConsume(), "bucket must refill by real monotonic elapse despite wall rollback (pre-fix: frozen)");
    }

    @Test
    public void testWallForwardJumpDoesNotBulkRefill() {
        registerStub();
        AiRateLimitGatewayInterceptor.TokenBucket b = bucket(2, 100);

        // 消费至空
        assertTrue(b.tryConsume());
        assertTrue(b.tryConsume());
        assertFalse(b.tryConsume());

        // 墙钟前跳 1h：单调未流逝 → 不回填（修复前 wall 前跳 → intervals 暴增 → 一次灌满 RED）
        clock.forwardWall(3_600_000);
        assertFalse(b.tryConsume(), "wall forward jump must not bulk refill the bucket");

        // 单调流逝超过 interval → 正常回填
        clock.advanceNanos(TimeUnit.MILLISECONDS.toNanos(150));
        assertTrue(b.tryConsume());
    }
}
