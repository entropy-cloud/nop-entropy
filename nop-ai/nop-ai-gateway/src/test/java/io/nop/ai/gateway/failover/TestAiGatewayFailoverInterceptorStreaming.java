package io.nop.ai.gateway.failover;

import io.nop.ai.gateway.AiDialectBackendMessageConverter;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.routing.ConcurrencyRegistry;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.config.AppConfig;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.executor.BufferedStreamingPublisher;
import io.nop.gateway.core.streaming.GatewayStreamingConstants;
import io.nop.gateway.core.streaming.StreamingResponse;
import io.nop.gateway.impl.GatewayHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.ai.gateway.failover.FailoverTestSupport.StreamScenario;
import static io.nop.ai.gateway.failover.FailoverTestSupport.nonTransientBody;
import static io.nop.ai.gateway.failover.FailoverTestSupport.quotaBody;
import static io.nop.ai.gateway.failover.FailoverTestSupport.streamChunkJson;
import static io.nop.ai.gateway.failover.FailoverTestSupport.streamError;
import static io.nop.ai.gateway.failover.W7GatewayTestSupport.StreamCollector;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W7 Phase 5 流式全链端到端测试（plan 2026-08-15-1116-3，GW-04 流式部分）。
 *
 * <p>路径：路由入口（{@code GatewayHandler.handle}，gateway.xml {@code <interceptors>}
 * 挂载形态）→ 拦截器 onRequest（首次选择 + 转换 + properties 下沉 + 回调/监听器经 context
 * attribute 注入）→ {@code StreamingProcessor} 缓冲层（B-12 运行时接线）→ 首次 fetch 窗口内
 * 失败 → 重执行回调被调用（接线断言）→ 新候选重订阅（wrapper 指向新 sub）→ 越窗转发
 * （onStreamElement 反向转换，attempt 2 用新 dialect——B-10）→ 客户端输出（流式收集）。
 */
class TestAiGatewayFailoverInterceptorStreaming {

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
        interceptor.setPrimaryProvider("gw-test2");
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

    private static String anthropicChunk(String text) {
        return "{\"type\":\"content_block_delta\",\"index\":0,"
                + "\"delta\":{\"type\":\"text_delta\",\"text\":\"" + text + "\"}}";
    }

    // ======================= 端到端全链（Minimum Rules #22）+ B-10 + B-12 + B-14 =======================

    @Test
    void streamingEndToEndWindowInFailureCrossDialectResubscribe() {
        // attempt 1（gw-test2, openai）：窗口内配额失败
        fake.queueStream(StreamScenario.failing(streamError(429, quotaBody())));
        // attempt 2（gw-anthropic, anthropic）：成功流（anthropic 格式 chunk）
        fake.queueStream(StreamScenario.success(anthropicChunk("hi"), anthropicChunk(" there")));

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-switch-model", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/stream");
        CompletionStage<ApiResponse<?>> future = handler.handle(request, ctx);
        ApiResponse<?> response = future.toCompletableFuture().join();
        assertTrue(response.isOk(), "路由执行期必须成功（流式数据经 attribute 传递）");

        StreamingResponse streamingResponse = (StreamingResponse) ctx.getAttribute(StreamingResponse.class.getName());
        assertNotNull(streamingResponse, "StreamingResponse attribute 必须写入");
        assertInstanceOf(BufferedStreamingPublisher.class, streamingResponse.getPublisher(),
                "B-12 运行时接线：回调/监听器经 context attribute 注入后缓冲层必须 engage");

        StreamCollector collector = new StreamCollector();
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();

        // 接线断言：重执行回调被缓冲层运行时调用 → 两次 fetch
        assertEquals(2, fake.requests.size(), "重执行回调必须被缓冲层运行时调用（Minimum Rules #23）");
        // attempt 1：首次选择 gw-test2 → openai 格式 + stream=true + 表达式 URL（无 baseUrl 覆盖）
        assertEquals("https://gw-test2.example.com/v1/chat/completions", fake.requests.get(0).getUrl());
        @SuppressWarnings("unchecked")
        Map<String, Object> body1 = (Map<String, Object>) fake.requests.get(0).getBody();
        assertEquals("gw-model-2", body1.get("model"), "Q2 路由覆盖：选中候选 model 下沉");
        assertEquals(Boolean.TRUE, body1.get("stream"), "流式请求体 stream=true");
        // attempt 2：重选 gw-anthropic → anthropic 格式 + dialect.buildUrl base 替换语义（B-14）
        assertEquals("https://gw-anthropic.example.com/v1/messages", fake.requests.get(1).getUrl(),
                "重试 URL = dialect.buildUrl(provider base, chatUrl, apiKey)");
        @SuppressWarnings("unchecked")
        Map<String, Object> body2 = (Map<String, Object>) fake.requests.get(1).getBody();
        assertEquals("gw-claude-model", body2.get("model"));
        assertEquals(Boolean.TRUE, body2.get("stream"));

        // B-10：attempt 2 的 chunk 用 attempt 2 的 dialect 反向转换（anthropic → openai 前端）
        assertTrue(collector.completed, "重订阅后必须正常完成");
        assertNull(collector.error, "重订阅后不得报错: " + collector.error);
        assertEquals(List.of("hi", " there"), collector.texts,
                "客户端输出 = attempt 2 内容（attempt 1 缓冲元素丢弃，Major-1）");
        assertTrue(collector.degraded.isEmpty());

        // 并发计数 +1/-1 配对（B-2）：全终止路径释放
        assertEquals(0, registry.currentCount("gw-test2", null));
        assertEquals(0, registry.currentCount("gw-anthropic", null));
    }

    // ======================= 缓冲关闭仍计数（计数与缓冲解耦） =======================

    @Test
    void streamingWithoutBufferStillCountsAndRetries() {
        fake.queueStream(StreamScenario.failing(streamError(429, quotaBody())));
        fake.queueStream(StreamScenario.success(anthropicChunk("hi")));

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-switch-model", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/stream-nobuffer");
        handler.handle(request, ctx).toCompletableFuture().join();

        StreamingResponse streamingResponse = (StreamingResponse) ctx.getAttribute(StreamingResponse.class.getName());
        assertInstanceOf(BufferedStreamingPublisher.class, streamingResponse.getPublisher(),
                "监听器注入即 engage（缓冲关闭不解除接线）");
        StreamCollector collector = new StreamCollector();
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();

        assertEquals(2, fake.requests.size(), "缓冲关闭不阻止窗口内重订阅");
        assertTrue(collector.completed);
        assertEquals(List.of("hi"), collector.texts);
        assertEquals(0, registry.currentCount("gw-test2", null), "缓冲关闭仍计数且配对释放");
        assertEquals(0, registry.currentCount("gw-anthropic", null));
    }

    // ======================= onError 次序契约（B-17） =======================

    @Test
    void nonTransientStreamingErrorDegradesToTerminalResponse() {
        fake.queueStream(StreamScenario.failing(streamError(400, nonTransientBody())));

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-switch-model", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/stream");
        handler.handle(request, ctx).toCompletableFuture().join();

        StreamingResponse streamingResponse = (StreamingResponse) ctx.getAttribute(StreamingResponse.class.getName());
        StreamCollector collector = new StreamCollector();
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();

        assertEquals(1, fake.requests.size(), "NON_TRANSIENT 不重试（不发起新 fetch）");
        assertTrue(collector.completed, "降级终止 = onNext+onComplete 正常终止");
        assertNull(collector.error);
        assertEquals(1, collector.degraded.size(), "客户端收到降级终止响应元素");
        assertNotNull(collector.degraded.get(0).getMsg());
        assertEquals(0, registry.currentCount("gw-test2", null));
    }

    // ======================= 窗口外失败断流（B-17：窗口内外判定唯一归属缓冲层） =======================

    @Test
    void windowOutFailureStreamsErrorWithoutSwitch() {
        // bufferSize=1：第 2 个元素触发越窗（首元素缓冲 + 第 2 元素透传）→ 后续错误 = 窗口外
        fake.queueStream(StreamScenario.success(streamChunkJson("a1"), streamChunkJson("a2"))
                .error(streamError(429, quotaBody())));

        // w7-stream-window1：bufferSize=1
        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-switch-model", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/stream-window1");
        handler.handle(request, ctx).toCompletableFuture().join();

        StreamingResponse streamingResponse = (StreamingResponse) ctx.getAttribute(StreamingResponse.class.getName());
        StreamCollector collector = new StreamCollector();
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();

        assertEquals(1, fake.requests.size(), "窗口外失败不切换（不 consult 回调）");
        assertEquals(List.of("a1", "a2"), collector.texts, "已转发元素照常交付");
        assertNotNull(collector.error, "窗口外失败断流报错");
    }

    // ======================= 预算耗尽断流（fail-loud） =======================

    @Test
    void retryBudgetExhaustedStreamsError() {
        interceptor.setRetryBudget(1);
        fake.queueStream(StreamScenario.failing(streamError(429, quotaBody())));
        fake.queueStream(StreamScenario.failing(streamError(429, quotaBody())));

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-switch-model", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/stream");
        handler.handle(request, ctx).toCompletableFuture().join();

        StreamingResponse streamingResponse = (StreamingResponse) ctx.getAttribute(StreamingResponse.class.getName());
        StreamCollector collector = new StreamCollector();
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();

        assertEquals(2, fake.requests.size(), "budget=1：1 次重订阅后预算耗尽");
        assertNotNull(collector.error, "预算耗尽断流报错（fail-loud，不静默）");
        assertEquals(0, registry.currentCount("gw-test2", null));
        assertEquals(0, registry.currentCount("gw-anthropic", null));
    }

    // ======================= 全池饱和 fail-loud（并发饱和，B-13 预检语义） =======================

    @Test
    void concurrencySaturationFailsLoud() {
        // 预占满 tier-gw-sat 全部候选：主账号 limit 2 + s1/s2 limit 1
        registry.acquire("gw-sat", null);
        registry.acquire("gw-sat", null);
        registry.acquire("gw-sat", null);
        registry.acquire("gw-sat", "key-sat-s1");
        registry.acquire("gw-sat", "key-sat-s1");
        registry.acquire("gw-sat", "key-sat-s2");
        registry.acquire("gw-sat", "key-sat-s2");

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-sat-model", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/stream");
        CompletionStage<ApiResponse<?>> future = handler.handle(request, ctx);
        ApiResponse<?> response = future.toCompletableFuture().join();

        assertEquals(NopAiCoreErrors.ERR_AI_MODEL_CLASS_SATURATED.getErrorCode(), response.getCode(),
                "全池并发饱和必须 fail-loud（ERR_AI_MODEL_CLASS_SATURATED 透传）");
        assertEquals(0, fake.requests.size(), "饱和时不得发起任何 fetch");
    }

    // ======================= 全池饱和 fail-loud（健康度饱和） =======================

    @Test
    void breakerSaturationFailsLoud() {
        // 冷却 10s：OPEN 候选探活不通过 → 保持全池饱和 fail-loud（若冷却 0，探活恢复会
        // HALF_OPEN 放行选中——那是 W6 D5b 的恢复语义，非本测试场景）。
        breaker = new ThresholdBreaker(3, 10_000L);
        interceptor.setBreaker(breaker);
        // 全部候选熔断 OPEN（阈值 3）：gw-test2 主 + gw-anthropic 主
        breaker.recordFailure("gw-test2:gw-model-2");
        breaker.recordFailure("gw-test2:gw-model-2");
        breaker.recordFailure("gw-test2:gw-model-2");
        breaker.recordFailure("gw-anthropic:gw-claude-model");
        breaker.recordFailure("gw-anthropic:gw-claude-model");
        breaker.recordFailure("gw-anthropic:gw-claude-model");

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-switch-model", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/stream");
        CompletionStage<ApiResponse<?>> future = handler.handle(request, ctx);
        ApiResponse<?> response = future.toCompletableFuture().join();

        assertEquals(NopAiCoreErrors.ERR_AI_MODEL_CLASS_SATURATED.getErrorCode(), response.getCode(),
                "全池健康度饱和必须 fail-loud（ERR_AI_MODEL_CLASS_SATURATED 透传）");
        assertEquals(0, fake.requests.size(), "饱和时不得发起任何 fetch");
    }

    // ======================= 无路由组直通零回归 =======================

    @Test
    void noRoutingGroupStreamingPassthrough() {
        fake.queueStream(StreamScenario.success(streamChunkJson("a1"), streamChunkJson("a2")));

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("unknown-model", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/stream");
        handler.handle(request, ctx).toCompletableFuture().join();

        StreamingResponse streamingResponse = (StreamingResponse) ctx.getAttribute(StreamingResponse.class.getName());
        assertNotNull(streamingResponse);
        StreamCollector collector = new StreamCollector();
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();

        assertEquals(1, fake.requests.size());
        assertTrue(collector.completed);
        assertEquals(List.of("a1", "a2"), collector.texts, "无路由组直通：原始 chunk 原样到达（不转换）");
        assertEquals(0, registry.currentCount("gw-test2", null), "直通路径不计数");
        assertNull(ctx.getAttribute(GatewayStreamingConstants.ATTR_RETRY_CALLBACK), "直通路径不挂载回调");
    }
}
