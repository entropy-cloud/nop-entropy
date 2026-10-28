package io.nop.ai.gateway.failover;

import io.nop.ai.core.reliability.CircuitState;
import io.nop.ai.core.service.ChatServiceImpl;
import io.nop.ai.core.service.DefaultChatLogger;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.nop.ai.gateway.failover.FailoverTestSupport.ManualDemandSubscriber;
import static io.nop.ai.gateway.failover.FailoverTestSupport.StreamScenario;
import static io.nop.ai.gateway.failover.FailoverTestSupport.request;
import static io.nop.ai.gateway.failover.FailoverTestSupport.streamChunkJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2509 R2-06 成员 3.3-C1-001（C10-002 面）回归钉：monitor 完整性（裁定③）。
 *
 * <p>红形态（修复前）：{@code handleStreamFailure} 的 {@code buffer.clear()} 是全文件唯一
 * 锁外 buffer 触点，且位于 inWindow 判定与 {@code metrics.onRequestFailure} 之后——注入
 * 阻塞型 metrics 桩拉宽「inWindow 已判定 / clear 未执行」交错窗，期间下游 request() 冲刷
 * （flushBuffer 只看 passed 不校验 identity）把 attempt-1 已缓冲前缀交付进最终输出（跨
 * attempt 前缀泄漏）；clear 与 poll 并发还可能撕裂 ArrayDeque 致 onNext(null)。
 * 绿形态：clear 入锁（先行于 metrics 钩子）+ flushBuffer/flushAll attempt-identity 守卫——
 * 放弃 attempt 的缓冲永不交付、零 onNext(null)、零前缀泄漏。
 */
class TestFailoverAttemptIdentityGuard_C1_001 {

    private static final String ERR_MSG_PREFIX = "unexpected stream error";

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    @Timeout(60)
    void clearInsideMonitorPreventsPrefixLeakOnInterleavedRequest() throws Exception {
        FailoverTestSupport.FakeHttpClient fake = new FailoverTestSupport.FakeHttpClient();
        ChatServiceImpl impl = new ChatServiceImpl();
        impl.setHttpClient(fake);
        impl.setChatLogger(new DefaultChatLogger());
        CountDownLatch inFailureHook = new CountDownLatch(1);
        CountDownLatch releaseHook = new CountDownLatch(1);
        FailoverTestSupport.CountingFailoverMetrics metrics = new FailoverTestSupport.CountingFailoverMetrics() {
            @Override
            public void onRequestFailure(String provider, String model, String accountKey, long durationMs) {
                // 确定性交错钩子：失败路径进入记账点（inWindow 判定之后、clear 之前/之后即为本钉
                // 的红绿分界）——拉宽窗口供下游 request 交错。
                inFailureHook.countDown();
                try {
                    releaseHook.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        ChatServiceFailoverAdapter adapter = new ChatServiceFailoverAdapter();
        adapter.setDelegate(impl);
        adapter.setMetrics(metrics);
        adapter.setRegistry(new io.nop.ai.gateway.routing.ConcurrencyRegistry());
        adapter.setBreaker(new io.nop.ai.core.reliability.ThresholdBreaker(3, 0));
        adapter.setBufferSize(8);
        adapter.setBufferTimeMs(0); // 首元素即越窗：a-前缀全部滞留越窗缓冲（demand==0）

        // attempt-1：a-前缀 ×4 后窗口内失败（429）→ 重订阅；attempt-2：b ×2 成功。
        fake.queueStream(StreamScenario.success(
                        streamChunkJson("a1"), streamChunkJson("a2"),
                        streamChunkJson("a3"), streamChunkJson("a4"))
                .error(FailoverTestSupport.streamError(429, FailoverTestSupport.quotaBody())))
                .queueStream(StreamScenario.success(
                        streamChunkJson("b1"), streamChunkJson("b2")));

        ManualDemandSubscriber sub = new ManualDemandSubscriber();
        adapter.callStream(request("gw-test", "gw-model-1", true), null).subscribe(sub);

        // 等失败路径进入 metrics 钩子（inWindow 已判定）→ 下游授信 request(4) 交错冲刷。
        assertTrue(inFailureHook.await(20, TimeUnit.SECONDS), "failure path must reach the metrics hook");
        sub.request(4);
        Thread.sleep(300); // 有界观察窗：让冲刷在交错窗内执行（红形态在此交付 a-前缀）
        releaseHook.countDown();

        sub.awaitTerminal(20000);
        assertNull(sub.error, ERR_MSG_PREFIX + ": " + sub.error);
        assertTrue(sub.completed, "stream must complete via the resubscribed attempt");
        assertEquals(0, sub.nullElements, "ArrayDeque tearing must not surface as onNext(null)");
        // 绿形态核心断言：attempt-1 已缓冲前缀零泄漏（identity 守卫 + clear 入锁）。
        assertEquals(List.of("b1", "b2"), sub.texts,
                "attempt-1 buffered prefix must never be delivered after the in-window failure, got " + sub.texts);
        assertEquals(2, fake.streamCallCount(), "in-window failure must resubscribe exactly once");
        assertEquals(CircuitState.CLOSED, adapter.getBreaker().getState("gw-test:gw-model-1"),
                "final success must leave the breaker closed");
    }
}
