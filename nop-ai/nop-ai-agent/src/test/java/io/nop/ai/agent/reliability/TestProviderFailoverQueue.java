package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-08-01-1905-3：{@link ProviderFailoverQueue} 单元测试——per-provider 冷却去重 +
 * 可注入时间源（裁定 B/D）。验证防震荡机制（冷却期内不可用）+ 可测试性（不复制 ThresholdBreaker
 * {@code System.currentTimeMillis()} 反模式）。
 */
public class TestProviderFailoverQueue {

    // ========================================================================
    // NoOp 默认：恒可用、record 为 no-op（零回归）
    // ========================================================================

    @Test
    void noOpQueueAlwaysAvailableAndRecordsAreNoOp() {
        IProviderFailoverQueue q = NoOpProviderFailoverQueue.noOp();
        assertTrue(q.isProviderAvailable("p1"));
        q.recordProviderFailure("p1");
        q.recordProviderSuccess("p1");
        assertTrue(q.isProviderAvailable("p1"), "NoOp 恒可用——零回归");
    }

    // ========================================================================
    // 冷却去重：失败后冷却期内不可用，过冷却期恢复可用（防震荡）
    // ========================================================================

    @Test
    void providerUnavailableWithinCooldownAndAvailableAfter() {
        AtomicLong clock = new AtomicLong(1_000_000L);
        ProviderFailoverQueue q = new ProviderFailoverQueue(60_000L, clock::get);

        // 未失败的 provider 恒可用（健康默认）。
        assertTrue(q.isProviderAvailable("p1"));

        // 记录 p1 失败。
        q.recordProviderFailure("p1");
        assertEquals(1, q.getConsecutiveFailures("p1"));

        // 冷却期内（now 不变）→ 不可用（防切回刚失败的 provider）。
        assertFalse(q.isProviderAvailable("p1"), "冷却期内不可用（防震荡）");

        // 推进时间到刚好冷却期边界（60s）→ 仍不可用（< cooldown 严格小于）。
        clock.set(1_000_000L + 60_000L - 1);
        assertFalse(q.isProviderAvailable("p1"), "边界前 1ms 仍冷却中");

        // 推进时间过冷却期 → 恢复可用。
        clock.set(1_000_000L + 60_000L);
        assertTrue(q.isProviderAvailable("p1"), "过冷却期恢复可用");
    }

    // ========================================================================
    // 成功重置失败计数
    // ========================================================================

    @Test
    void successResetsFailureCount() {
        AtomicLong clock = new AtomicLong(0L);
        ProviderFailoverQueue q = new ProviderFailoverQueue(60_000L, clock::get);

        q.recordProviderFailure("p1");
        q.recordProviderFailure("p1");
        assertEquals(2, q.getConsecutiveFailures("p1"));
        assertFalse(q.isProviderAvailable("p1"));

        q.recordProviderSuccess("p1");
        assertEquals(0, q.getConsecutiveFailures("p1"), "成功重置失败计数");
        assertTrue(q.isProviderAvailable("p1"), "成功后立即可用（计数清零，lastFailureAt 不影响——0-0>=0）");
    }

    // ========================================================================
    // 每 provider 独立熔断状态（裁定 B "每 provider 独立熔断状态"）
    // ========================================================================

    @Test
    void perProviderIndependentCircuitState() {
        AtomicLong clock = new AtomicLong(0L);
        ProviderFailoverQueue q = new ProviderFailoverQueue(60_000L, clock::get);

        q.recordProviderFailure("p1");
        assertFalse(q.isProviderAvailable("p1"), "p1 冷却中");
        assertTrue(q.isProviderAvailable("p2"), "p2 独立——p1 失败不影响 p2");
        assertTrue(q.isProviderAvailable("p3"), "p3 独立");
    }

    // ========================================================================
    // 可测试时间源：可控推进（裁定 D，不复制 ThresholdBreaker 反模式）
    // ========================================================================

    @Test
    void injectableClockAllowsDeterministicTimeAdvance() {
        AtomicLong clock = new AtomicLong(5_000L);
        ProviderFailoverQueue q = new ProviderFailoverQueue(1_000L, clock::get);

        q.recordProviderFailure("p1");
        assertFalse(q.isProviderAvailable("p1"));

        // 精确推进 1000ms → 边界，恢复可用。确定性测试，无真实 sleep。
        clock.addAndGet(1_000L);
        assertTrue(q.isProviderAvailable("p1"));
    }

    // ========================================================================
    // cooldownMs=0 → 去重禁用（边界）
    // ========================================================================

    @Test
    void zeroCooldownDisablesDedup() {
        AtomicLong clock = new AtomicLong(0L);
        ProviderFailoverQueue q = new ProviderFailoverQueue(0L, clock::get);

        q.recordProviderFailure("p1");
        assertTrue(q.isProviderAvailable("p1"), "cooldownMs=0 → 不冷却，恒可用（去重禁用）");
    }

    // ========================================================================
    // 线程安全：并发 record/isAvailable 不抛（smoke）
    // ========================================================================

    @Test
    void concurrentAccessDoesNotThrow() throws InterruptedException {
        AtomicLong clock = new AtomicLong(0L);
        ProviderFailoverQueue q = new ProviderFailoverQueue(100L, clock::get);

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                q.recordProviderFailure("p1");
                clock.incrementAndGet();
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                q.isProviderAvailable("p1");
                q.isProviderAvailable("p2");
            }
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        assertTrue(q.getConsecutiveFailures("p1") > 0, "并发 record 累积");
    }

    // ========================================================================
    // 参数校验（fail-fast，Minimum Rules #24——不静默）
    // ========================================================================

    @Test
    void nullProviderRejected() {
        ProviderFailoverQueue q = new ProviderFailoverQueue();
        assertThrows(NopAiAgentException.class, () -> q.recordProviderFailure(null));
        assertThrows(NopAiAgentException.class, () -> q.isProviderAvailable(null));
        assertThrows(NopAiAgentException.class, () -> q.recordProviderSuccess(null));
    }

    @Test
    void invalidCooldownRejected() {
        assertThrows(NopAiAgentException.class, () -> new ProviderFailoverQueue(-1L, System::currentTimeMillis));
    }

    @Test
    void nullClockRejected() {
        assertThrows(NopAiAgentException.class, () -> new ProviderFailoverQueue(60_000L, null));
    }
}
