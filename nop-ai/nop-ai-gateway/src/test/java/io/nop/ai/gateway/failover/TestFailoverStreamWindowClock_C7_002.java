package io.nop.ai.gateway.failover;

import io.nop.ai.core.reliability.CircuitState;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.service.ChatServiceImpl;
import io.nop.ai.core.service.DefaultChatLogger;
import io.nop.ai.gateway.routing.ConcurrencyRegistry;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.api.core.time.CoreMetrics;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.ai.gateway.failover.FailoverTestSupport.FakeHttpClient;
import static io.nop.ai.gateway.failover.FailoverTestSupport.StreamScenario;
import static io.nop.ai.gateway.failover.FailoverTestSupport.request;
import static io.nop.ai.gateway.failover.FailoverTestSupport.streamChunkJson;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R4-32（3.3-C7-002，plan 2566）FailoverStreamFlow 窗口时长单调钉：windowStart（:183）与
 * 两处 metrics 时长消费（:611/:708）成对同源换源 CoreMetrics.nanoTime() 后——
 * 墙钟回拨注入下 success 时长指标仍按真实（单调）流逝为非负毫秒（修复前 wall 回拨 →
 * duration = wall - windowStart 为巨大负值 RED）。StubClock 经 registerClock + finally 恢复。
 * <p>C7-003 第 8 面预留注记（防扩，勘正 2）：FeishuConnector:465 入站滚动窗不入本批。
 */
class TestFailoverStreamWindowClock_C7_002 {

    static class StubClock implements io.nop.api.core.time.IClock {
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

    private FakeHttpClient fake;
    private ThresholdBreaker breaker;
    private ConcurrencyRegistry registry;
    private ChatServiceFailoverAdapter adapter;
    private CapturingMetrics metrics;

    static class CapturingMetrics implements IFailoverMetrics {
        final AtomicReference<Long> successDuration = new AtomicReference<>();

        @Override
        public void onSwitchAttempt(String provider, String model, String accountKey) {
        }

        @Override
        public void onResubscribe(String provider, String model, String accountKey) {
        }

        @Override
        public void onCircuitTransition(String provider, String model, CircuitState toState) {
        }

        @Override
        public void onCooldown(String provider, String model, CooldownEventType type) {
        }

        @Override
        public void onRequestSuccess(String provider, String model, String accountKey, long durationMs) {
            successDuration.compareAndSet(null, durationMs);
        }

        @Override
        public void onRequestFailure(String provider, String model, String accountKey, long durationMs) {
        }

        @Override
        public void onSaturation(String provider, String modelClass) {
        }

        @Override
        public void onConcurrencyAcquire(String provider, String accountKey) {
        }

        @Override
        public void onConcurrencyRelease(String provider, String accountKey) {
        }

        @Override
        public void onTakeover(String provider, String model, String accountKey) {
        }

        @Override
        public void onDegradedTermination(String provider, String model, String accountKey) {
        }

        @Override
        public void onStreamElementConverted(String provider, String model, String accountKey) {
        }
    }

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
        System.setProperty("nop.ai.service.rate-limit-acquire-timeout", "0");
    }

    @AfterAll
    static void destroy() {
        System.clearProperty("nop.ai.service.rate-limit-acquire-timeout");
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        fake = new FakeHttpClient();
        ChatServiceImpl impl = new ChatServiceImpl();
        impl.setHttpClient(fake);
        impl.setChatLogger(new DefaultChatLogger());
        breaker = new ThresholdBreaker(3, 0);
        registry = new ConcurrencyRegistry();
        adapter = new ChatServiceFailoverAdapter();
        adapter.setDelegate(impl);
        adapter.setBreaker(breaker);
        adapter.setRegistry(registry);
        adapter.setBufferSize(10);
        adapter.setBufferTimeMs(1000L);
        adapter.setRetryBudget(2);
        adapter.setStrategy(new FailoverTestSupport.NoConfigCandidateStrategy("gw-test", "gw-model-1", 1));
        metrics = new CapturingMetrics();
        adapter.setMetrics(metrics);

        CoreMetrics.registerClock(clock = new StubClock());
    }

    StubClock clock;

    @AfterEach
    void tearDown() {
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
        fake.streamSubscriptions.forEach(Flow.Subscription::cancel);
    }

    static final class DoneSubscriber implements Flow.Subscriber<ChatStreamChunk> {
        final CompletableFuture<Void> terminal = new CompletableFuture<>();

        @Override
        public void onSubscribe(Flow.Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ChatStreamChunk item) {
        }

        @Override
        public void onError(Throwable t) {
            terminal.completeExceptionally(t);
        }

        @Override
        public void onComplete() {
            terminal.complete(null);
        }
    }

    @Test
    void wallRollbackDoesNotNegativeInflateSuccessDuration() throws Exception {
        // 挂门形态：windowStart 已取（attempt 建立），回拨发生在记账（complete）之前
        CountDownLatch gate = new CountDownLatch(1);
        fake.queueStream(new StreamScenario().events(streamChunkJson("hello")).gate(gate));

        DoneSubscriber sub = new DoneSubscriber();
        adapter.callStream(request("gw-test", "gw-model-1", true), null).subscribe(sub);

        // 等 attempt 建立（windowStart 已取）后墙钟回拨 1h
        long deadline = System.currentTimeMillis() + 10_000;
        while (fake.streamCallCount() < 1 && System.currentTimeMillis() < deadline)
            Thread.sleep(20);
        clock.rollbackWall(3_600_000);
        clock.advanceNanos(5_000_000L); // 5ms 单调流逝

        gate.countDown(); // 放行完成路径（success 记账在回拨之后发生）
        sub.terminal.get(10, TimeUnit.SECONDS);

        Long duration = metrics.successDuration.get();
        assertNotNull(duration, "success metrics must be recorded on stream completion");
        assertTrue(duration >= 0, "success duration must be non-negative under wall rollback "
                + "(pre-fix wall-domain windowStart yields a huge negative duration): got " + duration);
        assertTrue(duration < 60_000, "success duration must reflect the real short attempt: got " + duration);
    }
}
