package io.nop.ai.gateway.failover;

import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.routing.ConcurrencyRegistry;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.ai.gateway.AiDialectBackendMessageConverter;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.config.AppConfig;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.impl.GatewayHandler;
import io.nop.http.api.client.IHttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W7 Phase 5 非流式路由级测试（plan 2026-08-15-1116-3，GW-04 路由级部分）。
 *
 * <p>路径：路由入口（{@code GatewayHandler.handle}）→ 拦截器 invoke（选择/切换/重试编排）→
 * {@code executeRouteLogic}（converter 经路由 backendMessageConverter 声明 + 全局容器解析）→
 * 转发 → 失败分类 → 重选 → 二次转发成功。覆盖：F3 非流式 baseUrl 覆盖（route URL 表达式读
 * request properties）、B-8 无双重转换（streaming.enabled=false 判定同构 F2）、备份账号认证头
 * 下沉、预算耗尽 fail-loud、无路由组直通零回归。
 */
class TestAiGatewayFailoverInterceptorNonStreaming {

    private FailoverTestSupport.FakeHttpClient fake;
    private ThresholdBreaker breaker;
    private ConcurrencyRegistry registry;
    private AiGatewayFailoverInterceptor interceptor;
    private AiDialectBackendMessageConverter converter;
    private GatewayHandler handler;

    @BeforeAll
    static void init() {
        W7GatewayTestSupport.init();
    }

    @AfterAll
    static void destroy() {
        W7GatewayTestSupport.destroy();
    }

    @BeforeEach
    void setUp() {
        W7GatewayTestSupport.BEANS.clear();
        fake = new FailoverTestSupport.FakeHttpClient();
        breaker = new ThresholdBreaker(3, 0);
        registry = new ConcurrencyRegistry();
        converter = new AiDialectBackendMessageConverter();
        interceptor = new AiGatewayFailoverInterceptor();
        interceptor.setBreaker(breaker);
        interceptor.setRegistry(registry);
        interceptor.setConverter(converter);
        interceptor.setRetryBudget(2);
        interceptor.setPrimaryProvider("gw-test");
        W7GatewayTestSupport.BEANS.put("nopAiGatewayFailoverInterceptor", () -> interceptor);
        W7GatewayTestSupport.BEANS.put("nopBackendMessageConverter_AI_DIALECT", () -> converter);
        handler = W7GatewayTestSupport.buildHandler(fake);
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.gw-test.api-key", "key-gw-main");
    }

    @AfterEach
    void tearDown() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.gw-test.api-key", null);
        LlmConfigHelper.reset();
    }

    private static IHttpResponse okResponse() {
        return httpResponse(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}");
    }

    private static IHttpResponse authErrorResponse() {
        return httpResponse(401, "{\"error\":{\"message\":\"bad key\"}}");
    }

    /**
     * 非流式响应 fake：InvokeProcessor 经 getBodyAsBean(ApiResponse.class) 解析 body——
     * FailoverTestSupport.FakeHttpResponse 不支持该调用（W6 仅流式/本地形态使用）。
     * ApiResponse 反序列化严格（unknown-bean-prop）——仿真实 HTTP client 的宽松行为：
     * data = 完整 body map（converter 的 toFrontendResponse 再经 parseResponse 解析）。
     */
    private static IHttpResponse httpResponse(int status, String body) {
        return new io.nop.http.api.client.IHttpResponse() {
            @Override
            public int getHttpStatus() {
                return status;
            }

            @Override
            public String getContentType() {
                return "application/json";
            }

            @Override
            public String getCharset() {
                return "UTF-8";
            }

            @Override
            public byte[] getBodyAsBytes() {
                return body != null ? body.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
            }

            @Override
            public String getBodyAsString() {
                return body;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getBodyAsBean(Class<T> beanClass) {
                if (beanClass == io.nop.api.core.beans.ApiResponse.class) {
                    io.nop.api.core.beans.ApiResponse<Object> resp = new io.nop.api.core.beans.ApiResponse<>();
                    resp.setData(io.nop.core.lang.json.JsonTool.parseMap(body));
                    return (T) resp;
                }
                throw new UnsupportedOperationException("getBodyAsBean: " + beanClass);
            }

            @Override
            public Object getBody() {
                return body;
            }

            @Override
            public Map<String, String> getHeaders() {
                return new LinkedHashMap<>();
            }
        };
    }

    private static RuntimeException networkError() {
        return new RuntimeException("connection refused");
    }

    // ======================= 端到端全链：网络错误 → TRANSIENT → 账号链切换 → 成功 =======================

    @Test
    void nonStreamingNetworkErrorSwitchesAccountAndSucceeds() {
        // attempt 1（gw-test 主）：网络错误 → TRANSIENT → 重选 gw-backup-a（无 baseUrl）
        fake.queueFailure(networkError());
        // attempt 2（gw-backup-a）：成功
        fake.queueResponse(okResponse());

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-model-1", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/nonstream");
        CompletionStage<ApiResponse<?>> future = handler.handle(request, ctx);
        ApiResponse<?> response = future.toCompletableFuture().join();

        assertTrue(response.isOk(), "重试成功后必须返回成功响应");
        assertEquals(2, fake.requests.size(), "1 次失败切换 + 1 次成功");
        // attempt 2 认证头下沉（备份账号 apiKey 直沉；setBearerToken 用小写 authorization 键）
        assertEquals("Bearer key-gw-a",
                fake.requests.get(1).getHeader(io.nop.http.api.HttpApiConstants.HEADER_AUTHORIZATION),
                "备份账号 apiKey 必须下沉为认证头（Phase 4 sinkAuthHeader）");
        assertEquals(0, registry.currentCount("gw-test", null), "全终止路径 release 配对");
        assertEquals(0, registry.currentCount("gw-test", "key-gw-a"));
    }

    // ======================= 端到端全链：切换到带 baseUrl 的账号（F3 表达式消费） =======================

    @Test
    void nonStreamingBaseUrlOverrideFlowsThroughUrlExpression() {
        // attempt 1/2（gw-test 主 / gw-backup-a）：网络错误
        fake.queueFailure(networkError());
        fake.queueFailure(networkError());
        // attempt 3（gw-backup-b，baseUrl=https://gw-b.example.com）：成功
        fake.queueResponse(okResponse());

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-model-1", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/nonstream");
        CompletionStage<ApiResponse<?>> future = handler.handle(request, ctx);
        ApiResponse<?> response = future.toCompletableFuture().join();

        assertTrue(response.isOk());
        assertEquals(3, fake.requests.size());
        // F3：route URL 表达式读 request properties baseUrl 覆盖 → 目标账号 base 生效
        assertEquals("https://gw-b.example.com/v1/chat/completions", fake.requests.get(2).getUrl(),
                "非流式 per-attempt baseUrl 覆盖必须经 URL 表达式消费（F3）");
        assertEquals("Bearer key-gw-b",
                fake.requests.get(2).getHeader(io.nop.http.api.HttpApiConstants.HEADER_AUTHORIZATION));
        assertEquals(0, registry.currentCount("gw-test", null));
        assertEquals(0, registry.currentCount("gw-test", "key-gw-b"));
    }

    // ======================= B-8 无双重转换（F2 判定同构） =======================

    @Test
    void streamingDisabledRouteDoesNotDoubleConvert() {
        // w7-stream-disabled：streaming.enabled=false → 非流式路径（F2 判定同构）
        fake.queueResponse(okResponse());

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-switch-model", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/stream-disabled");
        CompletionStage<ApiResponse<?>> future = handler.handle(request, ctx);
        ApiResponse<?> response = future.toCompletableFuture().join();

        assertTrue(response.isOk());
        assertEquals(1, fake.requests.size());
        // 仅 executeRouteLogic 转换一次：body 无 stream=true（若 onRequest 误按流式转换则会写 stream=true）
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) fake.requests.get(0).getBody();
        assertEquals(Boolean.FALSE, body.get("stream"),
                "非流式路径不得按流式转换（B-8 双转消除，F2 判定同构）");
    }

    // ======================= 预算耗尽 fail-loud =======================

    @Test
    void nonStreamingBudgetExhaustedFailsLoud() {
        interceptor.setRetryBudget(1);
        fake.queueFailure(networkError());
        fake.queueFailure(networkError());

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-model-1", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/nonstream");
        CompletionStage<ApiResponse<?>> future = handler.handle(request, ctx);
        ApiResponse<?> response = future.toCompletableFuture().join();

        assertFalse(response.isOk(), "预算耗尽必须显式失败（fail-loud）");
        assertEquals(2, fake.requests.size(), "budget=1：1 次重试后预算耗尽");
        assertNotNull(response.getMsg());
        assertEquals(0, registry.currentCount("gw-test", null));
        assertEquals(0, registry.currentCount("gw-test", "key-gw-a"));
    }

    // ======================= 无路由组直通零回归 =======================

    @Test
    void noRoutingGroupNonStreamingPassthrough() {
        fake.queueResponse(okResponse());

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("unknown-model", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/nonstream");
        CompletionStage<ApiResponse<?>> future = handler.handle(request, ctx);
        ApiResponse<?> response = future.toCompletableFuture().join();

        assertTrue(response.isOk(), "无路由组直通（零回归）");
        assertEquals(1, fake.requests.size());
        assertEquals(0, registry.currentCount("gw-test", null), "直通路径不计数");
    }
}
