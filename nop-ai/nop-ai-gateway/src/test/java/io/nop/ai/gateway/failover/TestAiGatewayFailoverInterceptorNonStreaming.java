package io.nop.ai.gateway.failover;

import io.nop.ai.core.reliability.CircuitState;
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
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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
     * data = 完整 body map（converter 的 toFrontendResponse 再经 parseResponse 解析），
     * 并抽取 body 中的 status/code/msg 字段（W8 OBS-02：Nop 风格错误体 —— 非 2xx 响应只有
     * status != 0 时才被拦截器判定为失败响应，OpenAI 风格 body 无 status = 视为成功直通）。
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
                    Map<String, Object> parsed = body != null
                            ? io.nop.core.lang.json.JsonTool.parseMap(body) : new LinkedHashMap<>();
                    io.nop.api.core.beans.ApiResponse<Object> resp = new io.nop.api.core.beans.ApiResponse<>();
                    resp.setData(parsed);
                    Object statusVal = parsed.get("status");
                    if (statusVal instanceof Number) {
                        resp.setStatus(((Number) statusVal).intValue());
                    }
                    Object code = parsed.get("code");
                    if (code != null) {
                        resp.setCode(code.toString());
                    }
                    Object msg = parsed.get("msg");
                    if (msg != null) {
                        resp.setMsg(msg.toString());
                    }
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

    private void tripOpen(String modelKey) {
        for (int i = 0; i < 3; i++) {
            breaker.recordFailure(modelKey);
        }
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

    // ======================= 端到端全链：429 → RATE_LIMITED → 账号链切换（W8 OBS-02 补缺） =======================

    @Test
    void nonStreamingRateLimited429SwitchesAccount() {
        // InvokeProcessor 对 429 内置 3 次退避重试（2s/4s/8s + jitter）后才抛 NopException
        // （ERR_GATEWAY_UPSTREAM_429，httpStatus=429）→ classifyStreamError（body 键不匹配
        // ARG_BODY → parseErrorResponse 不可达）→ LlmErrorClassifier(429) → RATE_LIMITED → 切换。
        // 4×429 耗尽内置重试，第 5 个响应 = attempt 2 成功。
        fake.queueResponse(httpResponse(429, "{\"error\":{\"message\":\"slow down\"}}"))
                .queueResponse(httpResponse(429, "{\"error\":{\"message\":\"slow down\"}}"))
                .queueResponse(httpResponse(429, "{\"error\":{\"message\":\"slow down\"}}"))
                .queueResponse(httpResponse(429, "{\"error\":{\"message\":\"slow down\"}}"))
                .queueResponse(okResponse());

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-model-1", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/nonstream");
        CompletionStage<ApiResponse<?>> future = handler.handle(request, ctx);
        ApiResponse<?> response = future.toCompletableFuture().join();

        assertTrue(response.isOk(), "429 → RATE_LIMITED → switch must recover: " + response.getMsg());
        assertEquals(5, fake.requests.size(),
                "4×429 exhaust InvokeProcessor internal retries, then 1 switch attempt");
        assertEquals("Bearer key-gw-a",
                fake.requests.get(4).getHeader(io.nop.http.api.HttpApiConstants.HEADER_AUTHORIZATION),
                "switch must sink the backup account apiKey");
        assertEquals(0, registry.currentCount("gw-test", null));
        assertEquals(0, registry.currentCount("gw-test", "key-gw-a"));
    }

    // ======================= 端到端全链：401 → AUTH_INVALID → 账号链切换（W8 OBS-02 补缺） =======================

    @Test
    void nonStreamingAuthInvalidResponseSwitchesAccount() {
        // 401 非 429/5xx → InvokeProcessor 原样返回 ApiResponse（Nop 风格 body status=401）→
        // isOk()=false → classifyResponseStatus(401) → AUTH_INVALID → 切换 → 成功。
        // 注：converter 路由（/chat/nonstream）经 toFrontendResponse 把非 2xx 归一化为
        // ApiResponse.success（status=0 = isOk()=true）——响应级分类仅在本无 converter 路由可达。
        fake.queueResponse(httpResponse(401, "{\"status\":401,\"code\":\"auth_error\",\"msg\":\"bad key\"}"))
                .queueResponse(okResponse());

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-model-1", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/nonstream-raw");
        CompletionStage<ApiResponse<?>> future = handler.handle(request, ctx);
        ApiResponse<?> response = future.toCompletableFuture().join();

        assertTrue(response.isOk());
        assertEquals(2, fake.requests.size(), "AUTH_INVALID must trigger account switch");
        assertEquals("Bearer key-gw-a",
                fake.requests.get(1).getHeader(io.nop.http.api.HttpApiConstants.HEADER_AUTHORIZATION));
        assertEquals(0, registry.currentCount("gw-test", null));
        assertEquals(0, registry.currentCount("gw-test", "key-gw-a"));
    }

    // ======================= 端到端全链：400 → NON_TRANSIENT → 不切换（W8 OBS-02 补缺） =======================

    @Test
    void nonStreamingNonTransientFailsWithoutSwitch() {
        // 400 → 原样返回 ApiResponse（status=400）→ classifyResponseStatus → NON_TRANSIENT →
        // 不切换直接失败（不耗预算、不记熔断）。
        fake.queueResponse(httpResponse(400, "{\"status\":400,\"code\":\"bad_request\",\"msg\":\"bad request\"}"));

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-model-1", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/nonstream-raw");
        CompletionStage<ApiResponse<?>> future = handler.handle(request, ctx);
        ApiResponse<?> response = future.toCompletableFuture().join();

        assertFalse(response.isOk(), "NON_TRANSIENT must fail directly");
        assertEquals(1, fake.requests.size(), "NON_TRANSIENT must not switch");
        assertEquals(0, registry.currentCount("gw-test", null));
    }

    // ======================= P2 round-4：候选 provider 无 .llm.xml（sinkAuthHeader config null 守卫） =======================

    @Test
    void nonStreamingNoConfigCandidateKeepsClientHeader() {
        // P2 round-4：路由候选 provider 无 .llm.xml（策略注造 no-config-provider）→ 公开入口
        // （选择 → sinkCandidate → sinkAuthHeader）不抛裸 NPE——裁定 A：认证头不下沉
        // （客户端头保留，零回归）+ WARN 日志（可观测，非静默）；下游请求构建链路连通
        // （fake 收到请求且认证头为客户端原值）。无 converter 路由（/chat/nonstream-raw）
        // 保证链路不因 converter 的 provider 配置 fail-loud 中断（converter 契约面独立）。
        interceptor.setStrategy(new FailoverTestSupport.NoConfigCandidateStrategy(
                "no-config-provider", "no-config-model", 0));
        Logger logger = (Logger) LoggerFactory.getLogger(AiGatewayFailoverInterceptor.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            fake.queueResponse(okResponse());
            ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-model-1", "hello");
            request.setHeader(io.nop.http.api.HttpApiConstants.HEADER_AUTHORIZATION, "Bearer client-key");
            IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/nonstream-raw");
            CompletionStage<ApiResponse<?>> future = handler.handle(request, ctx);
            ApiResponse<?> response = future.toCompletableFuture().join();

            assertTrue(response.isOk(), "无配置候选不得中断请求链路（端到端链路连通）");
            assertEquals(1, fake.requests.size(), "链路连通：下游请求必须发出");
            assertEquals("Bearer client-key",
                    fake.requests.get(0).getHeader(io.nop.http.api.HttpApiConstants.HEADER_AUTHORIZATION),
                    "config==null 不下沉认证头：客户端头保留（零回归）");
            assertTrue(appender.list.stream().anyMatch(
                            e -> e.getFormattedMessage().contains("sink-auth-header-skipped")),
                    "config==null 必须有显式 WARN（非静默跳过）");
            assertEquals(0, registry.currentCount("no-config-provider", null));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    // ======================= 熔断探活恢复（网关非流式，W8 OBS-02 补缺） =======================

    @Test
    void nonStreamingProbeRecoveryRestoresCircuitClosed() throws Exception {
        // 全池 OPEN（冷却 0）→ 探活遍历 allowCall → HALF_OPEN 放行 → 调用成功 → recordSuccess
        // → CLOSED 恢复（与本地形态同款 FailoverProbeSupport 语义）。
        tripOpen("gw-test:gw-model-1");
        tripOpen("gw-test2:gw-model-2");

        fake.queueResponse(okResponse());

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-model-1", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/nonstream");
        CompletionStage<ApiResponse<?>> future = handler.handle(request, ctx);
        ApiResponse<?> response = future.toCompletableFuture().join();

        assertTrue(response.isOk(), "probe-admitted account must be callable again");
        assertEquals(1, fake.requests.size());
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-test:gw-model-1"),
                "probe success (recordSuccess) must restore CLOSED");
        assertEquals(CircuitState.HALF_OPEN, breaker.getState("gw-test2:gw-model-2"),
                "probed-but-not-called candidate stays HALF_OPEN");
        assertEquals(0, registry.currentCount("gw-test", null));
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
