package io.nop.ai.gateway.failover;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.service.ChatServiceImpl;
import io.nop.ai.core.service.DefaultChatLogger;
import io.nop.ai.core.service.LlmConfigHelper;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * plan 2509 R2-06 成员 3.3-MA-05（C3-004 主 + C7-001 吸收，C10-005 面）回归钉：
 * 三回调体护栏（裁定⑦(c)）。
 *
 * <p>红形态（修复前）：终态回调体（adapter/interceptor {@code whenComplete} 体）内异常
 * 吞入无人消费的从属 stage —— 非流式 future 永不完成（不可检测挂死）。
 * 绿形态：try/catch(Throwable) + completeExceptionally 兜底——future 必达终态；
 * NopException 语义按异常完成传播保持。
 */
class TestFailoverCallbackGuard_MA_05 {

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

    private ChatServiceFailoverAdapter newAdapter(FailoverTestSupport.CountingFailoverMetrics metrics) {
        FailoverTestSupport.FakeHttpClient fake = new FailoverTestSupport.FakeHttpClient();
        fake.queueResponse(successHttpResponse());
        ChatServiceImpl impl = new ChatServiceImpl();
        impl.setHttpClient(fake);
        impl.setChatLogger(new DefaultChatLogger());
        ChatServiceFailoverAdapter adapter = new ChatServiceFailoverAdapter();
        adapter.setDelegate(impl);
        adapter.setMetrics(metrics);
        adapter.setBreaker(new io.nop.ai.core.reliability.ThresholdBreaker(3, 0));
        adapter.setRegistry(new io.nop.ai.gateway.routing.ConcurrencyRegistry());
        return adapter;
    }

    private static FailoverTestSupport.FakeHttpResponse successHttpResponse() {
        FailoverTestSupport.FakeHttpResponse resp = new FailoverTestSupport.FakeHttpResponse();
        resp.status = 200;
        resp.body = FailoverTestSupport.successBody();
        return resp;
    }

    @Test
    @Timeout(30)
    void adapterWhenCompleteBodyGuardCompletesFutureOnUnexpectedThrow() throws Exception {
        // 红形态触发器：成功路径记账回调（whenComplete 体内）抛非 NopException。
        FailoverTestSupport.CountingFailoverMetrics metrics = new FailoverTestSupport.CountingFailoverMetrics();
        metrics.onSuccessThrow = new IllegalStateException("metrics backend exploded");
        ChatServiceFailoverAdapter adapter = newAdapter(metrics);

        CompletableFuture<io.nop.ai.api.chat.ChatResponse> future =
                adapter.callAsync(FailoverTestSupport.request("gw-test", "gw-model-1", false), null)
                        .toCompletableFuture();

        // 绿形态：future 必达终态（红形态：get 超时——异常吞入从属 stage，永久挂死）。
        try {
            future.get(10, TimeUnit.SECONDS);
            fail("expected the future to complete exceptionally when the callback body throws");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            assertNotNull(cause, "completion exception must carry the callback failure");
            assertEquals(IllegalStateException.class, cause.getClass(),
                    "the injected callback failure must surface as the completion cause");
        }
    }

    @Test
    @Timeout(30)
    void adapterWhenCompleteBodyGuardPropagatesNopExceptionSemantics() throws Exception {
        FailoverTestSupport.CountingFailoverMetrics metrics = new FailoverTestSupport.CountingFailoverMetrics();
        metrics.onSuccessThrow = new NopException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                .param(NopAiCoreErrors.ARG_MSG, "metrics nop failure");
        ChatServiceFailoverAdapter adapter = newAdapter(metrics);

        CompletableFuture<io.nop.ai.api.chat.ChatResponse> future =
                adapter.callAsync(FailoverTestSupport.request("gw-test", "gw-model-1", false), null)
                        .toCompletableFuture();

        // NopException 语义传播保持：异常完成且 cause 为该 NopException。
        try {
            future.get(10, TimeUnit.SECONDS);
            fail("expected the future to complete exceptionally with the NopException");
        } catch (java.util.concurrent.ExecutionException e) {
            assertTrue(e.getCause() instanceof NopException,
                    "NopException must propagate as the completion cause, got: " + e.getCause());
        }
    }

    @Test
    @Timeout(30)
    void interceptorWhenCompleteBodyGuardCompletesFutureOnUnexpectedThrow() throws Exception {
        io.nop.ai.gateway.routing.ConcurrencyRegistry registry = new io.nop.ai.gateway.routing.ConcurrencyRegistry();
        AiGatewayFailoverInterceptor interceptor = new AiGatewayFailoverInterceptor();
        interceptor.setBreaker(new io.nop.ai.core.reliability.ThresholdBreaker(3, 0));
        interceptor.setRegistry(registry);
        interceptor.setRetryBudget(2);
        interceptor.setPrimaryProvider("gw-test");
        FailoverTestSupport.CountingFailoverMetrics metrics = new FailoverTestSupport.CountingFailoverMetrics();
        metrics.onSuccessThrow = new IllegalStateException("metrics backend exploded");
        interceptor.setMetrics(metrics);

        // 未完成 stage：whenComplete 回调体在 stage 完成时（另一线程上下文）触发——
        // 红形态 = 异常吞入从属依赖、outer future 永不完成（挂死），而非同步抛出。
        CompletableFuture<ApiResponse<?>> inner = new CompletableFuture<>();
        IGatewayInvocation invocation = new IGatewayInvocation() {
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
                throw NopException.adapt(exception);
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
                return inner;
            }
        };
        IGatewayContext ctx = (IGatewayContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IGatewayContext.class},
                (proxy, method, args) -> {
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) return false;
                    return null;
                });

        ApiRequest<Object> request = new ApiRequest<>();
        io.nop.ai.api.chat.ChatOptions options = new io.nop.ai.api.chat.ChatOptions();
        options.setProvider("gw-test");
        options.setModel("gw-model-1");
        request.setData(new java.util.LinkedHashMap<>(java.util.Map.of("model", "gw-model-1")));

        CompletableFuture<ApiResponse<?>> future =
                interceptor.invoke(invocation, request, ctx).toCompletableFuture();
        inner.complete(ApiResponse.success("ok")); // 触发 whenComplete 回调体（红形态：异常吞入）

        // 绿形态：outer future 必达终态（红形态：永久挂死）。
        try {
            future.get(10, TimeUnit.SECONDS);
            fail("expected the interceptor future to complete exceptionally when the callback body throws");
        } catch (java.util.concurrent.ExecutionException e) {
            assertEquals(IllegalStateException.class, e.getCause().getClass(),
                    "the injected callback failure must surface as the completion cause");
        }
    }
}
