package io.nop.ai.gateway.failover;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.reliability.CircuitState;
import io.nop.ai.core.reliability.LlmErrorClassifier;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.routing.ConcurrencyRegistry;
import io.nop.ai.core.routing.ISelectionStrategy;
import io.nop.ai.core.routing.ModelClassCandidate;
import io.nop.ai.core.service.ChatServiceImpl;
import io.nop.ai.core.service.DefaultChatLogger;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.nop.ai.gateway.failover.FailoverTestSupport.CollectingSubscriber;
import static io.nop.ai.gateway.failover.FailoverTestSupport.FakeHttpClient;
import static io.nop.ai.gateway.failover.FailoverTestSupport.StreamScenario;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W6 本地适配器流式测试（plan 2026-08-15-1116-2 Phase 4，LOCAL-04 流式部分）：
 * 端到端全链（缓冲 → 窗口内失败 → 重订阅 → 越窗透传 → 终止）、Major-1 缓冲元素丢弃、
 * M-3 流式分类恢复、窗口外断流、预算耗尽断流、M-4 重订阅同步异常、Minor-2 流式成功
 * recordSuccess（熔断 CLOSED）、M-1 per-attempt token 隔离 + 取消顺序、Minor-10 输出侧
 * 取消 teardown、Minor-5 调用方已取消、并发 +1/-1 配对（M-2/M-3/M-4）、全池饱和 fail-loud、
 * 接线验证（Minimum Rules #23）。
 */
class TestChatServiceFailoverAdapterStreaming {

    private FakeHttpClient fake;
    private ThresholdBreaker breaker;
    private ConcurrencyRegistry registry;
    private ChatServiceFailoverAdapter adapter;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
        // M-4/M-2 同步抛测试：本地限流许可获取超时 0 = 立即失败（fail-fast），确定性。
        System.setProperty("nop.ai.service.rate-limit-acquire-timeout", "0");
    }

    @AfterAll
    static void destroy() {
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
        io.nop.api.core.config.AppConfig.getConfigProvider().assignConfigValue(
                "nop.ai.llm.gw-test.api-key", "key-gw-main");
    }

    @AfterEach
    void tearDown() {
        io.nop.api.core.config.AppConfig.getConfigProvider().assignConfigValue(
                "nop.ai.llm.gw-test.api-key", null);
        LlmConfigHelper.reset();
    }

    private static StreamScenario quotaFailure() {
        return StreamScenario.failing(FailoverTestSupport.streamError(429, FailoverTestSupport.quotaBody()));
    }

    private static StreamScenario authFailure() {
        return StreamScenario.failing(FailoverTestSupport.streamError(401, FailoverTestSupport.authBody()));
    }

    // ======================= 端到端全链（Minimum Rules #22）+ Major-1 + 接线 =======================

    @Test
    void endToEndInWindowFailureResubscribeAndPassThrough() {
        final int[] strategyCalls = {0};
        ISelectionStrategy recording = (request, candidates, health, attempted) -> {
            strategyCalls[0]++;
            for (ModelClassCandidate c : candidates) {
                if (attempted.contains(c) || !health.healthOf(c).isAvailable()) {
                    continue;
                }
                return c;
            }
            return null;
        };
        adapter.setStrategy(recording);
        fake.queueStream(StreamScenario.success(FailoverTestSupport.streamChunkJson("a1"),
                        FailoverTestSupport.streamChunkJson("a2"))
                        .error(FailoverTestSupport.streamError(429, FailoverTestSupport.quotaBody())))
                .queueStream(StreamScenario.success(
                        FailoverTestSupport.streamChunkJson("b1"), FailoverTestSupport.streamChunkJson("b2"),
                        FailoverTestSupport.streamChunkJson("b3"), FailoverTestSupport.streamChunkJson("b4"),
                        FailoverTestSupport.streamChunkJson("b5"), FailoverTestSupport.streamChunkJson("b6"),
                        FailoverTestSupport.streamChunkJson("b7"), FailoverTestSupport.streamChunkJson("b8"),
                        FailoverTestSupport.streamChunkJson("b9"), FailoverTestSupport.streamChunkJson("b10"),
                        FailoverTestSupport.streamChunkJson("b11"), FailoverTestSupport.streamChunkJson("b12")));

        CollectingSubscriber sub = new CollectingSubscriber();
        adapter.callStream(FailoverTestSupport.request("gw-test", "gw-model-1", true), null).subscribe(sub);

        // 端到端（#22）：callStream 入口 → 缓冲 → 窗口内失败 → 重订阅新账号 → 越窗透传 → 正常终止。
        sub.awaitAndAssertSuccess("b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", "b9", "b10", "b11", "b12");
        // Major-1：attempt1 已缓冲元素（a1/a2）不得进入最终输出。
        assertEquals(0, sub.texts.stream().filter(t -> t.startsWith("a")).count(),
                "attempt1 buffered elements must be dropped on resubscribe (Major-1)");
        assertEquals(2, fake.streamCallCount(), "in-window failure → one resubscribe");
        assertBearer(fake.requests.get(1), "key-gw-a",
                "resubscribe must sink the next account's apiKey (four-field sink-down)");
        assertTrue(strategyCalls[0] >= 1, "adapter must invoke the selection strategy at runtime (wiring)");
        assertEquals(0, registry.currentCount("gw-test", null), "count must return to 0 after terminal");
        assertEquals(0, registry.currentCount("gw-test", "key-gw-a"));
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-test:gw-model-1"),
                "final success must recordSuccess (Minor-2)");
    }

    // ======================= 窗口内失败分类（M-3） =======================

    @Test
    void streamAuthInvalidSwitchesAccount() {
        // M-3：流式 onError 携带 NopException ARG_HTTP_STATUS/ARG_BODY → 经 parseErrorResponse
        // 恢复响应级分类 AUTH_INVALID（裸 LlmErrorClassifier 会判 NON_TRANSIENT 不切换）。
        fake.queueStream(authFailure())
                .queueStream(StreamScenario.success(FailoverTestSupport.streamChunkJson("ok")));

        CollectingSubscriber sub = new CollectingSubscriber();
        adapter.callStream(FailoverTestSupport.request("gw-test", "gw-model-1", true), null).subscribe(sub);

        sub.awaitAndAssertSuccess("ok");
        assertEquals(2, fake.streamCallCount(), "AUTH_INVALID must trigger account switch");
        assertBearer(fake.requests.get(1), "key-gw-a");
        assertEquals(0, registry.currentCount("gw-test", null));
    }

    // ======================= 预算耗尽断流（fail-loud） + 记账 =======================

    @Test
    void budgetExhaustedBreaksStreamFailsLoudAndTripsBreaker() {
        // retryBudget 默认 2：初始 + 2 次重订阅 = 3 次流，全 QUOTA 失败 → 断流报错（fail-loud）。
        fake.queueStream(quotaFailure()).queueStream(quotaFailure()).queueStream(quotaFailure());

        CollectingSubscriber sub = new CollectingSubscriber();
        adapter.callStream(FailoverTestSupport.request("gw-test", "gw-model-1", true), null).subscribe(sub);

        sub.await();
        assertNotNull(sub.error, "budget-exhausted stream must fail loudly, not complete silently");
        assertTrue(!sub.completed, "fail-loud must not masquerade as completion");
        assertEquals(3, fake.streamCallCount(), "budget 2 = 1 initial + 2 resubscribes, then break");
        assertEquals(CircuitState.OPEN, breaker.getState("gw-test:gw-model-1"),
                "3 consecutive failures across accounts must trip the breaker (D6)");
        assertEquals(0, registry.currentCount("gw-test", "key-gw-b"), "every terminal path releases (M-2)");
    }

    // ======================= 窗口外失败：断流不切换 =======================

    @Test
    void outOfWindowFailureBreaksStreamWithoutSwitch() {
        // 12 元素越窗（N=2）后失败 → 不切换，交付已转发内容后断流报错（需求 §3.2）。
        // 注：被包装的 ChatServiceImpl 经 SubmissionPublisher 转发，closeExceptionally 与同线程
        // submit 存在 JDK 竞态（实证：12 连发仅前 4 到达订阅者，tail 被丢弃）——适配器对该行为
        // 的响应是正确的（已转发 → 断流报错；未转发 → 窗口内透明重订阅），故本测试用
        // bufferSize=2 使越窗在"必然到达的前 4 元素"内确定性发生，断言下限而非精确计数。
        adapter.setBufferSize(2);
        fake.queueStream(StreamScenario.success(
                        FailoverTestSupport.streamChunkJson("c1"), FailoverTestSupport.streamChunkJson("c2"),
                        FailoverTestSupport.streamChunkJson("c3"), FailoverTestSupport.streamChunkJson("c4"),
                        FailoverTestSupport.streamChunkJson("c5"), FailoverTestSupport.streamChunkJson("c6"),
                        FailoverTestSupport.streamChunkJson("c7"), FailoverTestSupport.streamChunkJson("c8"),
                        FailoverTestSupport.streamChunkJson("c9"), FailoverTestSupport.streamChunkJson("c10"),
                        FailoverTestSupport.streamChunkJson("c11"), FailoverTestSupport.streamChunkJson("c12"))
                        .error(FailoverTestSupport.streamError(429, FailoverTestSupport.quotaBody())));

        CollectingSubscriber sub = new CollectingSubscriber();
        adapter.callStream(FailoverTestSupport.request("gw-test", "gw-model-1", true), null).subscribe(sub);

        sub.await();
        assertTrue(sub.texts.size() >= 3,
                "window (N=2) must have passed and forwarded data before the error; got: " + sub.texts);
        assertEquals("c1", sub.texts.get(0));
        assertEquals("c2", sub.texts.get(1));
        assertNotNull(sub.error, "out-of-window failure must surface as stream error");
        assertEquals(1, fake.streamCallCount(), "no switch after data was forwarded");
        assertEquals(0, registry.currentCount("gw-test", null));
    }

    // ======================= 窗口期成功终止：缓冲全量交付 =======================

    @Test
    void completionDuringWindowDeliversBufferedItems() {
        // 短流（3 元素 < N=10，未越窗）正常完成 → 缓冲元素必须全量交付（窗口只延迟转发不吞数据）。
        fake.queueStream(StreamScenario.success(FailoverTestSupport.streamChunkJson("x1"),
                FailoverTestSupport.streamChunkJson("x2"), FailoverTestSupport.streamChunkJson("x3")));

        CollectingSubscriber sub = new CollectingSubscriber();
        adapter.callStream(FailoverTestSupport.request("gw-test", "gw-model-1", true), null).subscribe(sub);

        sub.awaitAndAssertSuccess("x1", "x2", "x3");
        assertEquals(1, fake.streamCallCount());
        assertEquals(0, registry.currentCount("gw-test", null));
    }

    // ======================= 熔断探活恢复（B-1）+ 流式成功 recordSuccess（Minor-2） =======================

    @Test
    void streamProbeRecoveryRestoresCircuitClosed() {
        // 全池 OPEN（冷却 0）→ 探活 allowCall → HALF_OPEN → 选中主账号 → 流成功 →
        // onComplete recordSuccess → CLOSED（探活成功 → CLOSED 恢复，需求 §3.3）。
        tripOpen("gw-test:gw-model-1");
        tripOpen("gw-test2:gw-model-2");
        fake.queueStream(StreamScenario.success(FailoverTestSupport.streamChunkJson("probe-ok")));

        CollectingSubscriber sub = new CollectingSubscriber();
        adapter.callStream(FailoverTestSupport.request("gw-test", "gw-model-1", true), null).subscribe(sub);

        sub.awaitAndAssertSuccess("probe-ok");
        assertEquals(1, fake.streamCallCount(), "probe must admit the primary candidate directly");
        assertBearer(fake.requests.get(0), "key-gw-main");
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-test:gw-model-1"),
                "probe success (recordSuccess on stream complete) must restore CLOSED");
        assertEquals(0, registry.currentCount("gw-test", null));
    }

    // ======================= 重订阅同步异常（M-4 / Minor-2 本地限流边界） =======================

    @Test
    void streamSyncRateLimitOnResubscribeSwitchesAndRecovers() {
        // attempt1 = gw-rl 主账号（限流首次通过）→ 窗口内 QUOTA 失败；attempt2 = gw-rl-b1
        // （同 provider 同一限流器 → 同步抛 RATE_LIMITED）；attempt3 = 类内耗尽 → provider 链
        // 扩展 → gw-sat 主账号 → 成功。
        fake.queueStream(quotaFailure())
                .queueStream(StreamScenario.success(FailoverTestSupport.streamChunkJson("recovered")));

        CollectingSubscriber sub = new CollectingSubscriber();
        adapter.callStream(FailoverTestSupport.request("gw-rl", "gw-rl-model", true), null).subscribe(sub);

        sub.awaitAndAssertSuccess("recovered");
        assertEquals(2, fake.streamCallCount(),
                "attempt1 (gw-rl) + attempt3 (gw-sat); attempt2 sync-throws before any fetch");
        assertTrue(fake.requests.get(1).getUrl().contains("gw-sat.example.com"),
                "final attempt must hit the provider-chain candidate gw-sat");
        assertEquals(0, registry.currentCount("gw-rl", null), "sync-throw path must release (M-2)");
        assertEquals(0, registry.currentCount("gw-rl", "key-rl-b1"));
        assertEquals(0, registry.currentCount("gw-sat", null));
    }

    @Test
    void streamSyncRateLimitBudgetExhaustedDeliversError() {
        adapter.setRetryBudget(1);
        fake.queueStream(quotaFailure());
        // attempt1 = gw-rl 主账号 → 窗口内 QUOTA；attempt2 = gw-rl-b1 → 同步抛 RATE_LIMITED →
        // 2 > 1 预算耗尽 → onError（RATE_LIMITED 同步异常，流式路径错误语义）。
        CollectingSubscriber sub = new CollectingSubscriber();
        adapter.callStream(FailoverTestSupport.request("gw-rl", "gw-rl-model", true), null).subscribe(sub);

        sub.await();
        assertNotNull(sub.error, "budget-exhausted sync-throw must surface as stream error");
        assertEquals(ErrorClassification.RATE_LIMITED, LlmErrorClassifier.classify(sub.error));
        assertEquals(1, fake.streamCallCount(), "attempt2 sync-throws before any fetch");
        assertEquals(0, registry.currentCount("gw-rl", "key-rl-b1"));
    }

    // ======================= 全池饱和 fail-loud（流式） =======================

    @Test
    void streamingFullPoolConcurrencySaturationFailsLoud() {
        registry.acquire("gw-sat", null);
        registry.acquire("gw-sat", null);
        registry.acquire("gw-sat", "key-sat-s1");
        registry.acquire("gw-sat", "key-sat-s2");

        CollectingSubscriber sub = new CollectingSubscriber();
        adapter.callStream(FailoverTestSupport.request("gw-sat", "gw-sat-model", true), null).subscribe(sub);

        sub.await();
        assertNotNull(sub.error, "full-pool saturation must fail loud on the stream path");
        assertTrue(sub.error instanceof NopException);
        assertEquals(NopAiCoreErrors.ERR_AI_MODEL_CLASS_SATURATED.getErrorCode(),
                ((NopException) sub.error).getErrorCode());
        assertEquals(0, fake.streamCallCount(), "no fetch when the pool is saturated");
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-sat:gw-sat-model"),
                "active-path saturation must not record breaker failures");
    }

    // ======================= 调用方取消（M-1 per-attempt 隔离 + Minor-5） =======================

    @Test
    void callerCancelStopsCurrentAttemptOnly() throws Exception {
        // M-1 per-attempt token 隔离：attempt1 窗口内失败 → 重订阅 attempt2（gate 挂起中）→
        // 调用方取消 → 只 teardown 当前 attempt（attempt2）：SSE 订阅取消 + 计数释放，
        // 不产生终态信号（与 ChatServiceImpl 取消语义一致）。
        CountDownLatch gate = new CountDownLatch(1);
        fake.queueStream(quotaFailure())
                .queueStream(StreamScenario.success(FailoverTestSupport.streamChunkJson("late"))
                        .gate(gate));

        SettableCancelToken callerToken = new SettableCancelToken();
        CollectingSubscriber sub = new CollectingSubscriber();
        adapter.callStream(FailoverTestSupport.request("gw-test", "gw-model-1", true), callerToken).subscribe(sub);

        awaitStreamCallCount(2);
        assertNull(sub.error, "no terminal signal before cancellation");
        callerToken.cancel("test-cancel");
        Thread.sleep(50);
        assertTrue(fake.streamSubscriptions.get(1).isCancelled(),
                "current attempt's SSE subscription must be cancelled (per-attempt token isolation)");
        assertEquals(1, fake.attemptTokenCancels.size(),
                "exactly the current attempt's token must be cancelled (never the caller token)");
        assertEquals(0, registry.currentCount("gw-test", null), "cancel must release the in-flight count");
        assertEquals(0, registry.currentCount("gw-test", "key-gw-a"));
        gate.countDown();
    }

    @Test
    void callerCancelledBeforeStartNoFetch() {
        // Minor-5：调用方已取消 → 干净终态（onError(CancellationException)），不发起任何新 fetch。
        SettableCancelToken callerToken = new SettableCancelToken();
        callerToken.cancel("pre-cancelled");
        CollectingSubscriber sub = new CollectingSubscriber();
        adapter.callStream(FailoverTestSupport.request("gw-test", "gw-model-1", true), callerToken).subscribe(sub);

        sub.await();
        assertEquals(0, fake.streamCallCount(), "pre-cancelled caller → no attempt must be started");
        assertEquals(0, registry.currentCount("gw-test", null));
        assertEquals(0, sub.texts.size());
        assertNotNull(sub.error, "clean terminal must deliver a terminal signal (ReactiveStreams)");
        assertTrue(sub.error instanceof CancellationException,
                "pre-cancelled caller must surface CancellationException, got: " + sub.error);
        assertTrue(!sub.completed);
    }

    @Test
    void outputSubscriptionCancelTriggersFullTeardown() throws Exception {
        // Minor-10：输出侧 cancel 可观测——cancel 输出订阅 → 内部 attempt teardown（SSE 订阅
        // 取消 + attempt token 取消 + 计数释放），不依赖 SubmissionPublisher 静默移除语义。
        CountDownLatch gate = new CountDownLatch(1);
        fake.queueStream(StreamScenario.success(FailoverTestSupport.streamChunkJson("d1"),
                        FailoverTestSupport.streamChunkJson("d2"))
                .gate(gate));

        Flow.Subscription[] outputSub = new Flow.Subscription[1];
        CollectingSubscriber sub = new CollectingSubscriberWithSubscribe(outputSub);
        adapter.callStream(FailoverTestSupport.request("gw-test", "gw-model-1", true), null).subscribe(sub);

        awaitStreamCallCount(1);
        outputSub[0].cancel();
        Thread.sleep(50);
        assertTrue(fake.streamSubscriptions.get(0).isCancelled(),
                "output cancel must tear down the SSE subscription (token cancel path)");
        assertEquals(1, fake.attemptTokenCancels.size(), "output cancel must cancel the attempt token");
        assertEquals(0, registry.currentCount("gw-test", null), "output cancel must release the count");
        assertEquals(0, sub.texts.size(), "no items may be delivered after cancel");
        gate.countDown();
    }

    // ======================= 取消顺序契约（M-1 / spike） =======================

    @Test
    void cancelOrderInternalSubscriptionBeforeAttemptToken() {
        // 取消顺序契约：先 cancel 适配器对 delegate publisher 的内部订阅（唤醒阻塞 submit）→
        // 再 cancel 该 attempt 的 token（断 HTTP）。
        List<String> log = new ArrayList<>();
        IChatService recording = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                cancelToken.appendOnCancelTask(() -> log.add("token-cancel"));
                return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                    }

                    @Override
                    public void cancel() {
                        log.add("internal-sub-cancel");
                    }
                });
            }
        };
        adapter.setDelegate(recording);

        Flow.Subscription[] outputSub = new Flow.Subscription[1];
        adapter.callStream(FailoverTestSupport.request("gw-test", "gw-model-1", true), null)
                .subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        outputSub[0] = subscription;
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ChatStreamChunk item) {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                    }

                    @Override
                    public void onComplete() {
                    }
                });

        outputSub[0].cancel();
        assertEquals(List.of("internal-sub-cancel", "token-cancel"), log,
                "cancel order must be: internal subscription first, then attempt token (spike)");
        assertEquals(0, registry.currentCount("gw-test", null));
    }

    // ======================= 单订阅者契约 =======================

    @Test
    void secondSubscriberRejectedExplicitly() {
        fake.queueStream(StreamScenario.success(FailoverTestSupport.streamChunkJson("only")));
        CollectingSubscriber first = new CollectingSubscriber();
        Flow.Publisher<ChatStreamChunk> publisher =
                adapter.callStream(FailoverTestSupport.request("gw-test", "gw-model-1", true), null);
        publisher.subscribe(first);
        first.awaitAndAssertSuccess("only");

        // 第二个订阅者：显式失败（不静默吞）。
        CollectingSubscriber second = new CollectingSubscriber();
        publisher.subscribe(second);
        second.await();
        assertNotNull(second.error, "second subscriber must be rejected explicitly");
        assertTrue(second.error instanceof IllegalStateException);
    }

    // ======================= 并发 +1/-1 在途观测（M-2 挂钩时点） =======================

    @Test
    void concurrencyCountHooksAtCallStreamInvocation() {
        // +1 挂钩 delegate callStream 调用时刻：subscribe() 返回（同步）时计数已 +1；
        // 终止后 -1 归零。
        fake.queueStream(StreamScenario.success(FailoverTestSupport.streamChunkJson("hook")));
        CollectingSubscriber sub = new CollectingSubscriber();
        adapter.callStream(FailoverTestSupport.request("gw-test", "gw-model-1", true), null).subscribe(sub);

        assertEquals(1, registry.currentCount("gw-test", null),
                "count must be +1 at callStream invocation time (W1 spike input)");
        sub.awaitAndAssertSuccess("hook");
        assertEquals(0, registry.currentCount("gw-test", null), "count must return to 0 after terminal");
    }

    // ======================= 辅助 =======================

    private void tripOpen(String modelKey) {
        for (int i = 0; i < 3; i++) {
            breaker.recordFailure(modelKey);
        }
    }

    private void awaitStreamCallCount(int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (fake.streamCallCount() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(expected, fake.streamCallCount(), "expected " + expected + " stream fetches");
    }

    private static void assertBearer(io.nop.http.api.client.HttpRequest request, String expected) {
        assertBearer(request, expected, "bearer token must contain " + expected);
    }

    private static void assertBearer(io.nop.http.api.client.HttpRequest request, String expected, String msg) {
        String token = request.getBearerToken();
        assertNotNull(token, msg);
        assertTrue(token.contains(expected), msg + " (token=" + token + ")");
    }

    static final class CollectingSubscriberWithSubscribe extends CollectingSubscriber {
        CollectingSubscriberWithSubscribe(Flow.Subscription[] outputSub) {
            this.outputSub = outputSub;
        }

        private final Flow.Subscription[] outputSub;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            outputSub[0] = subscription;
            subscription.request(Long.MAX_VALUE);
        }
    }

    /** 可编程取消的 ICancelToken（测试侧 caller token）。 */
    static final class SettableCancelToken implements ICancelToken {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final List<java.util.function.Consumer<String>> tasks = new ArrayList<>();
        private volatile String reason;

        void cancel(String reason) {
            if (cancelled.compareAndSet(false, true)) {
                this.reason = reason;
                for (java.util.function.Consumer<String> task : new ArrayList<>(tasks)) {
                    task.accept(reason);
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public String getCancelReason() {
            return reason;
        }

        @Override
        public void appendOnCancel(java.util.function.Consumer<String> task) {
            if (cancelled.get()) {
                task.accept(reason);
            } else {
                tasks.add(task);
            }
        }

        @Override
        public void removeOnCancel(java.util.function.Consumer<String> task) {
            tasks.remove(task);
        }
    }
}
