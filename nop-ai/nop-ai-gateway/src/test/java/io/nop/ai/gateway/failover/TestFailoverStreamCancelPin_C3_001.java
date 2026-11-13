package io.nop.ai.gateway.failover;

import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.service.ChatServiceImpl;
import io.nop.ai.core.service.DefaultChatLogger;
import io.nop.ai.gateway.routing.ConcurrencyRegistry;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.ai.gateway.failover.FailoverTestSupport.FakeHttpClient;
import static io.nop.ai.gateway.failover.FailoverTestSupport.StreamScenario;
import static io.nop.ai.gateway.failover.FailoverTestSupport.request;
import static io.nop.ai.gateway.failover.FailoverTestSupport.streamChunkJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.1-C3-001 消费面行为钉（plan 2505 Phase 2）：{@code FailoverStreamFlow} 作为
 * {@code ChatServiceImpl.callStream} 的<b>直接订阅者</b>，调用方取消后其 attempt 必须
 * 有界终止且订阅注册不泄漏（并发计数释放、上游 SSE 订阅取消、无重复记账）——
 * gateway 本体零改动（R2-06 边界，勘误 6）。
 *
 * <p>边界注记：适配器对调用方取消的既有契约 = teardown 当前 attempt 且<b>不向输出订阅
 * 发终态信号</b>（callerCancelStopsCurrentAttemptOnly 钉死，先 cancel 内部订阅再 cancel
 * attempt token 的顺序使核心侧 closeExceptionally 不达已取消订阅）——该输出语义属
 * R2-06（FailoverStreamFlow 本体批）所有，本钉不越界改动/断言。核心侧取消闭合下游的
 * 红转绿证据在 nop-ai-core（cancelClosesDownstreamSubscriber_C3_001 /
 * cancelTerminatesAggregatedFuture_C3_001，红形态①）。
 */
class TestFailoverStreamCancelPin_C3_001 {

    private FakeHttpClient fake;
    private ThresholdBreaker breaker;
    private ConcurrencyRegistry registry;
    private ChatServiceFailoverAdapter adapter;

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
    }

    @AfterEach
    void tearDown() {
        fake.streamSubscriptions.forEach(Flow.Subscription::cancel);
    }

    private static void awaitStreamCallCount(FakeHttpClient fake, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (fake.streamCallCount() < expected && System.currentTimeMillis() < deadline)
            Thread.sleep(20);
    }

    /**
     * 本地输出订阅者：终态 latch 可观测（不复用/不改既有共享 harness——零改动纪律）。
     */
    static final class PinSubscriber implements Flow.Subscriber<ChatStreamChunk> {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CompletableFuture<Void> terminal = new CompletableFuture<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ChatStreamChunk item) {
        }

        @Override
        public void onError(Throwable throwable) {
            error.set(throwable);
            terminal.complete(null);
        }

        @Override
        public void onComplete() {
            terminal.complete(null);
        }
    }

    @Test
    void callerCancelCompletesBoundedAttemptTeardown_C3_001() throws Exception {
        // 挂死传输形态：chunk 在 gate 前不发射，attempt 停留在首段缓冲窗口内
        CountDownLatch neverOpen = new CountDownLatch(1);
        fake.queueStream(new StreamScenario().events(streamChunkJson("hello")).gate(neverOpen));

        TestChatServiceFailoverAdapterStreaming.SettableCancelToken callerToken =
                new TestChatServiceFailoverAdapterStreaming.SettableCancelToken();
        PinSubscriber sub = new PinSubscriber();
        adapter.callStream(request("gw-test", "gw-model-1", true), callerToken).subscribe(sub);
        // PinSubscriber 保持订阅（适配器契约：调用方取消不向输出发终态——该语义归 R2-06）

        awaitStreamCallCount(fake, 1);
        assertNull(sub.error.get(), "no terminal signal before cancellation");
        assertEquals(1, registry.currentCount("gw-test", null),
                "in-flight attempt holds exactly one concurrency count");

        callerToken.cancel("test-cancel");

        // 有界窗内 attempt 终止 + 订阅注册不泄漏：计数释放、上游 SSE 订阅取消、
        // attempt token 精确取消一次（不重复不遗漏）
        long deadline = System.currentTimeMillis() + 3_000;
        while (registry.currentCount("gw-test", null) != 0 && System.currentTimeMillis() < deadline)
            Thread.sleep(20);
        assertEquals(0, registry.currentCount("gw-test", null),
                "attempt teardown must release the concurrency count within the bounded window "
                        + "(3.1-C3-001 consumer-side pin: bounded termination, no registration leak)");
        assertTrue(fake.streamSubscriptions.get(0).isCancelled(),
                "delegate upstream SSE subscription must be cancelled");
        assertEquals(1, fake.attemptTokenCancels.size(),
                "exactly the current attempt's token must be cancelled");
        neverOpen.countDown();
    }
}
