package io.nop.ai.gateway.failover;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.model.ModelClassCandidate;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.ai.gateway.routing.ConcurrencyRegistry;
import io.nop.ai.gateway.routing.IModelClassHealth;
import io.nop.ai.gateway.routing.ISelectionStrategy;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2509 R2-06 成员 3.3-C2-001（MA-07 主，C10-003 面）回归钉：取消×装配窗口
 * acquire/release 配对（裁定②）。
 *
 * <p>红形态（修复前）：取消落在 {@code startAttemptWithOptions} 取消检查之后、acquire 之后
 * /装配订阅之前——{@code AttemptSubscriber.onSubscribe} 取消/放弃分支仅
 * {@code subscription.cancel()} 不回滚并发计数 → per-账号计数永久 +1（幽灵计数，
 * 饱和误判）。绿形态：漏斗单行 cancel + releaseAttempt（released CAS exactly-once），
 * 进入点矩阵（:227 首 attempt / :462 重订阅）交错下计数终值归零。
 */
class TestFailoverAttemptReleaseOnCancel_C2_001 {

    private ThresholdBreaker breaker;
    private ConcurrencyRegistry registry;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        breaker = new ThresholdBreaker(3, 0);
        registry = new ConcurrencyRegistry();
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.gw-test.api-key", "key-gw-main");
    }

    @AfterEach
    void tearDown() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.gw-test.api-key", null);
        LlmConfigHelper.reset();
    }

    /**
     * 简单调用方取消令牌：cancel() 同步触发已注册 listener（确定性交错钩子）。
     */
    static final class TestCancelToken implements ICancelToken {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final List<Consumer<String>> tasks = new java.util.concurrent.CopyOnWriteArrayList<>();
        private volatile String reason;

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public String getCancelReason() {
            return reason;
        }

        @Override
        public void appendOnCancel(Consumer<String> task) {
            if (cancelled.get()) {
                task.accept(reason);
            } else {
                tasks.add(task);
            }
        }

        @Override
        public void removeOnCancel(Consumer<String> task) {
            tasks.remove(task);
        }

        void cancel(String reason) {
            if (cancelled.compareAndSet(false, true)) {
                this.reason = reason;
                for (Consumer<String> task : tasks) {
                    task.accept(reason);
                }
            }
        }
    }

    /**
     * 门控 delegate：callStream 返回的 publisher 把 onSubscribe 推迟到 gate 放行之后
     * （拉宽 acquire → 装配订阅 的交错窗口）。
     */
    static final class GatedStreamDelegate implements IChatService {
        final CountDownLatch onSubscribeGate = new CountDownLatch(1);

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> CompletableFuture.runAsync(() -> {
                try {
                    onSubscribeGate.await(15, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                    }

                    @Override
                    public void cancel() {
                    }
                });
            });
        }
    }

    /**
     * 钩子策略：第 {@code cancelOnCall} 次被调用时先取消调用方令牌（取消精确落在
     * startAttemptWithOptions 的取消检查之后、acquire 之前的窗口内），再返回首个候选。
     */
    static final class CancelDuringSelectStrategy implements ISelectionStrategy {
        private final TestCancelToken callerToken;
        private final int cancelOnCall;
        private final AtomicInteger calls = new AtomicInteger();
        volatile ModelClassCandidate lastSelected;

        CancelDuringSelectStrategy(TestCancelToken callerToken, int cancelOnCall) {
            this.callerToken = callerToken;
            this.cancelOnCall = cancelOnCall;
        }

        @Override
        public ModelClassCandidate select(ChatRequest request, List<ModelClassCandidate> candidates,
                                          IModelClassHealth health, Set<ModelClassCandidate> attempted) {
            if (calls.incrementAndGet() == cancelOnCall) {
                callerToken.cancel("cancelled inside selection hook (interleave window)");
            }
            for (ModelClassCandidate c : candidates) {
                if (attempted.contains(c)) {
                    continue;
                }
                lastSelected = c;
                return c;
            }
            return null;
        }
    }

    private ChatServiceFailoverAdapter newAdapter(IChatService delegate, ISelectionStrategy strategy) {
        ChatServiceFailoverAdapter adapter = new ChatServiceFailoverAdapter();
        adapter.setDelegate(delegate);
        adapter.setBreaker(breaker);
        adapter.setRegistry(registry);
        adapter.setStrategy(strategy);
        adapter.setBufferSize(4);
        adapter.setBufferTimeMs(60000);
        return adapter;
    }

    @Test
    @Timeout(30)
    void cancelInAssemblyWindowReleasesFirstAttemptAcquire() throws Exception {
        // 进入点 :227（首 attempt）：取消落在取消检查之后 / acquire 之后 / 装配订阅之前。
        TestCancelToken callerToken = new TestCancelToken();
        GatedStreamDelegate delegate = new GatedStreamDelegate();
        CancelDuringSelectStrategy strategy = new CancelDuringSelectStrategy(callerToken, 1);
        ChatServiceFailoverAdapter adapter = newAdapter(delegate, strategy);

        FailoverTestSupport.ManualDemandSubscriber sub = new FailoverTestSupport.ManualDemandSubscriber();
        adapter.callStream(FailoverTestSupport.request("gw-test", "gw-model-1", true), callerToken).subscribe(sub);

        ModelClassCandidate candidate = strategy.lastSelected;
        assertNotNull(candidate, "selection hook must have run and captured the candidate");
        // 取消已触发（策略钩子内同步），装配订阅被 gate 拉宽——放行 onSubscribe 走漏斗行。
        assertTrue(callerToken.isCancelled());
        delegate.onSubscribeGate.countDown();
        Thread.sleep(300); // 有界观察窗：等 onSubscribe 漏斗行执行

        // 绿形态：计数终值归零（红形态：acquire 幽灵 +1 永不释放 → 饱和误判）。
        assertEquals(0, registry.currentCount(candidate.getProvider(), candidate.getAccountKey()),
                "cancel in the assembly window must release the attempt acquire exactly-once (C2-001), count was "
                        + registry.currentCount(candidate.getProvider(), candidate.getAccountKey()));
    }

    @Test
    @Timeout(30)
    void cancelInResubscribeAssemblyWindowReleasesAcquire() throws Exception {
        // 进入点 :462（切换类重订阅）：attempt-1 窗口内失败 → 重选（第 2 次策略调用中取消）→
        // 新候选 acquire → 装配窗口内已取消 → 漏斗行必须释放新候选计数。
        TestCancelToken callerToken = new TestCancelToken();
        FailoverTestSupport.FakeHttpClient fake = new FailoverTestSupport.FakeHttpClient();
        fake.queueStream(FailoverTestSupport.StreamScenario.failing(
                FailoverTestSupport.streamError(429, FailoverTestSupport.quotaBody())));
        io.nop.ai.core.service.ChatServiceImpl impl = new io.nop.ai.core.service.ChatServiceImpl();
        impl.setHttpClient(fake);
        impl.setChatLogger(new io.nop.ai.core.service.DefaultChatLogger());
        CancelDuringSelectStrategy strategy = new CancelDuringSelectStrategy(callerToken, 2);
        ChatServiceFailoverAdapter adapter = newAdapter(impl, strategy);

        FailoverTestSupport.ManualDemandSubscriber sub = new FailoverTestSupport.ManualDemandSubscriber();
        adapter.callStream(FailoverTestSupport.request("gw-test", "gw-model-1", true), callerToken).subscribe(sub);

        ModelClassCandidate candidate = strategy.lastSelected;
        assertNotNull(candidate);
        // 等待失败分类 → 重选（取消钩子触发）→ 新 attempt acquire → onSubscribe 漏斗行。
        Thread.sleep(1000);

        assertEquals(0, registry.currentCount(candidate.getProvider(), candidate.getAccountKey()),
                "cancel in the resubscribe assembly window must release the new attempt acquire (C2-001), count was "
                        + registry.currentCount(candidate.getProvider(), candidate.getAccountKey()));
    }
}
