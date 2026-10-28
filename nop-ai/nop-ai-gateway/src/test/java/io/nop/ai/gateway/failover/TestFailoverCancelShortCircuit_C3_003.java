package io.nop.ai.gateway.failover;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.core.reliability.CircuitState;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.ai.gateway.routing.ConcurrencyRegistry;
import io.nop.ai.gateway.routing.IModelClassHealth;
import io.nop.ai.gateway.routing.ISelectionStrategy;
import io.nop.ai.core.model.ModelClassCandidate;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.initialize.CoreInitialization;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.IGatewayInvocation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * plan 2509 R2-06 成员 3.3-C3-003（C10 面取消误分类记账污染）回归钉：取消短路（裁定⑦(d)）。
 *
 * <p>红形态（修复前）：调用方取消的 stage 异常完成被 {@code LlmErrorClassifier} 兜底为
 * TRANSIENT（可切换）→ onRequestFailure 记账 + 熔断 recordFailure（健康账号污染）+ 重试
 * 预算误耗（重选/重发真实发生）。
 * 绿形态：whenComplete err 分支入口取消短路（cancelToken.isCancelled() 或 cause 链
 * CancellationException）→ completeExceptionally，零记账、零 recordFailure、零预算消耗。
 * 注记（仅注记不修）：「取消」独立 ErrorClassification 归 3.1/nop-ai-core 侧（R2-16 对接）。
 */
class TestFailoverCancelShortCircuit_C3_003 {

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
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.gw-test.api-key", "key-gw-main");
    }

    @AfterEach
    void tearDown() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.gw-test.api-key", null);
        LlmConfigHelper.reset();
    }

    /**
     * 受控非流式 delegate：callAsync 返回受控 future（测试显式异常完成）+ 调用计数。
     */
    static final class ControlledAsyncDelegate implements IChatService {
        final AtomicInteger calls = new AtomicInteger();
        final CompletableFuture<ChatResponse> stage = new CompletableFuture<>();

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            calls.incrementAndGet();
            return stage;
        }

        @Override
        public Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }
    }

    static final class CountingStrategy implements ISelectionStrategy {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public ModelClassCandidate select(ChatRequest request, List<ModelClassCandidate> candidates,
                                          IModelClassHealth health, Set<ModelClassCandidate> attempted) {
            calls.incrementAndGet();
            for (ModelClassCandidate c : candidates) {
                if (attempted.contains(c) || !health.healthOf(c).isAvailable()) {
                    continue;
                }
                return c;
            }
            return null;
        }
    }

    @Test
    @Timeout(30)
    void adapterCancelShortCircuitsNoAccountingNoBudget() throws Exception {
        ControlledAsyncDelegate delegate = new ControlledAsyncDelegate();
        CountingStrategy strategy = new CountingStrategy();
        FailoverTestSupport.CountingFailoverMetrics metrics = new FailoverTestSupport.CountingFailoverMetrics();
        ThresholdBreaker breaker = new ThresholdBreaker(1, 60000); // 阈值 1：一次 recordFailure 即 OPEN（红形态可观测）
        ConcurrencyRegistry registry = new ConcurrencyRegistry();
        ChatServiceFailoverAdapter adapter = new ChatServiceFailoverAdapter();
        adapter.setDelegate(delegate);
        adapter.setStrategy(strategy);
        adapter.setMetrics(metrics);
        adapter.setBreaker(breaker);
        adapter.setRegistry(registry);

        // 令牌在 delegate 发起后、stage 完成前取消（入口取消检查已过、whenComplete err 分支
        // 判定第一半）；stage 再以 CancellationException 异常完成（cause 链判定第二半）。
        TestCancelCallerToken token = new TestCancelCallerToken();

        CompletableFuture<ChatResponse> future =
                adapter.callAsync(FailoverTestSupport.request("gw-test", "gw-model-1", false), token)
                        .toCompletableFuture();
        token.cancel("caller gave up");
        delegate.stage.completeExceptionally(new CancellationException("stage cancelled"));

        try {
            future.get(10, TimeUnit.SECONDS);
            fail("cancelled call must complete exceptionally");
        } catch (CancellationException | java.util.concurrent.ExecutionException e) {
            // completeExceptionally(CancellationException) 使 future 进入 cancelled 态 →
            // get() 直接抛 CancellationException（与 ExecutionException 包装同为异常完成）。
            // future 异常完成（取消语义），不得转为成功响应。
            assertTrue(e.getCause() instanceof CancellationException
                            || e.getCause() instanceof io.nop.api.core.exceptions.NopException,
                    "cancellation must not be converted to a success response, got: " + e.getCause());
        }

        // 绿形态核心断言：零记账、零 recordFailure、零预算消耗（单次 delegate 调用）。
        assertEquals(1, delegate.calls.get(), "cancelled failure must not consume retry budget (single attempt)");
        assertEquals(1, strategy.calls.get(), "cancelled failure must not trigger reselection");
        assertEquals(0, metrics.requestFailures.get(), "cancelled failure must not be recorded as request failure");
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-test:gw-model-1"),
                "cancelled failure must not pollute the breaker (recordFailure)");
    }

    static final class TestCancelCallerToken implements ICancelToken {
        private final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
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
        public void appendOnCancel(java.util.function.Consumer<String> task) {
            if (cancelled.get()) {
                task.accept(reason);
            }
        }

        @Override
        public void removeOnCancel(java.util.function.Consumer<String> task) {
        }

        void cancel(String reason) {
            this.reason = reason;
            cancelled.set(true);
        }
    }

    @Test
    @Timeout(30)
    void interceptorCancelShortCircuitsNoAccountingNoBudget() throws Exception {
        ConcurrencyRegistry registry = new ConcurrencyRegistry();
        AiGatewayFailoverInterceptor interceptor = new AiGatewayFailoverInterceptor();
        ThresholdBreaker breaker = new ThresholdBreaker(1, 60000);
        interceptor.setBreaker(breaker);
        interceptor.setRegistry(registry);
        interceptor.setRetryBudget(2);
        interceptor.setPrimaryProvider("gw-test");
        FailoverTestSupport.CountingFailoverMetrics metrics = new FailoverTestSupport.CountingFailoverMetrics();
        interceptor.setMetrics(metrics);

        CompletableFuture<ApiResponse<?>> inner = new CompletableFuture<>();
        AtomicInteger proceedCalls = new AtomicInteger();
        IGatewayInvocation invocation = proceedingTo(inner, proceedCalls);
        IGatewayContext ctx = nullContext();

        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(new java.util.LinkedHashMap<>(java.util.Map.of("model", "gw-model-1")));

        CompletableFuture<ApiResponse<?>> future =
                interceptor.invoke(invocation, request, ctx).toCompletableFuture();
        // stage 以 CancellationException 异常完成（interceptor 无 cancelToken——cause 链判定路径）。
        inner.completeExceptionally(new CancellationException("downstream cancelled"));

        try {
            future.get(10, TimeUnit.SECONDS);
            fail("cancelled call must complete exceptionally");
        } catch (CancellationException | java.util.concurrent.ExecutionException e) {
            // completeExceptionally(CancellationException) 使 future 进入 cancelled 态 →
            // get() 直接抛 CancellationException（与 ExecutionException 包装同为异常完成）。
            assertTrue(e.getCause() instanceof CancellationException
                            || e.getCause() instanceof io.nop.api.core.exceptions.NopException,
                    "cancellation must not be converted to a success response, got: " + e.getCause());
        }

        assertEquals(1, proceedCalls.get(), "cancelled failure must not consume retry budget (single attempt)");
        assertEquals(0, metrics.requestFailures.get(), "cancelled failure must not be recorded as request failure");
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-test:gw-model-1"),
                "cancelled failure must not pollute the breaker (recordFailure)");
    }

    private static IGatewayInvocation proceedingTo(CompletableFuture<ApiResponse<?>> inner,
                                                   AtomicInteger counter) {
        return new IGatewayInvocation() {
            @Override
            public ApiRequest<?> proceedOnRequest(ApiRequest<?> request, IGatewayContext ctx) {
                return request;
            }

            @Override
            public ApiResponse<?> proceedOnResponse(ApiResponse<?> response, IGatewayContext ctx) {
                return response;
            }

            @Override
            public ApiResponse<?> proceedOnError(Throwable exception, IGatewayContext ctx) {
                throw io.nop.api.core.exceptions.NopException.adapt(exception);
            }

            @Override
            public void proceedOnStreamStart(ApiRequest<?> request, IGatewayContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Object proceedOnStreamElement(Object element, IGatewayContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Object proceedOnStreamError(Throwable exception, IGatewayContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void proceedOnStreamComplete(IGatewayContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletionStage<ApiResponse<?>> proceedInvoke(ApiRequest<?> request, IGatewayContext ctx) {
                counter.incrementAndGet();
                return inner;
            }
        };
    }

    private static IGatewayContext nullContext() {
        return (IGatewayContext) Proxy.newProxyInstance(
                TestFailoverCancelShortCircuit_C3_003.class.getClassLoader(),
                new Class<?>[]{IGatewayContext.class},
                (proxy, method, args) -> {
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) return false;
                    return null;
                });
    }
}
