package io.nop.ai.gateway.failover;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.routing.ConcurrencyRegistry;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.ai.gateway.AiDialectBackendMessageConverter;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.config.AppConfig;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.streaming.StreamingResponse;
import io.nop.gateway.impl.GatewayHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;

import static io.nop.ai.gateway.failover.FailoverTestSupport.StreamScenario;
import static io.nop.ai.gateway.failover.FailoverTestSupport.nonTransientBody;
import static io.nop.ai.gateway.failover.FailoverTestSupport.quotaBody;
import static io.nop.ai.gateway.failover.FailoverTestSupport.streamError;
import static io.nop.ai.gateway.failover.W7GatewayTestSupport.StreamCollector;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W8 OBS-01 网关形态指标 focused 测试（plan 2026-08-15-1615-1 Phase 3）：接管 / 切换与重订阅
 * （重执行回调）/ 降级终止 / 反向转换 / 生命周期并发配对 / 非流式切换与耗时，经真实事件路径
 * 触发（复用 {@link W7GatewayTestSupport} 基建，路由入口 → 拦截器 → 缓冲层 → 客户端输出）。
 *
 * <p>指标实例 = {@code FailoverMetricsImpl(SimpleMeterRegistry)}（每测试独立 registry，
 * 与进程级 {@code GlobalMeterRegistry} 隔离）。
 */
class TestFailoverMetricsGateway {

    private static final String SWITCH = "nop.ai.gateway.failover.switch.total";
    private static final String RESUBSCRIBE = "nop.ai.gateway.failover.resubscribe.total";
    private static final String TRANSITION = "nop.ai.gateway.failover.circuit-transition.total";
    private static final String COOLDOWN = "nop.ai.gateway.failover.cooldown.total";
    private static final String SUCCESS = "nop.ai.gateway.failover.request-success.total";
    private static final String FAILURE = "nop.ai.gateway.failover.request-failure.total";
    private static final String DURATION = "nop.ai.gateway.failover.request.duration";
    private static final String ACQUIRE = "nop.ai.gateway.failover.concurrency-acquire.total";
    private static final String RELEASE = "nop.ai.gateway.failover.concurrency-release.total";
    private static final String TAKEOVER = "nop.ai.gateway.failover.takeover.total";
    private static final String DEGRADED = "nop.ai.gateway.failover.degraded.total";
    private static final String STREAM_ELEMENT = "nop.ai.gateway.failover.stream-element.total";

    private FailoverTestSupport.FakeHttpClient fake;
    private ThresholdBreaker breaker;
    private ConcurrencyRegistry registry;
    private AiGatewayFailoverInterceptor interceptor;
    private AiDialectBackendMessageConverter converter;
    private GatewayHandler handler;
    private SimpleMeterRegistry meterRegistry;
    private FailoverMetricsImpl metrics;

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
        meterRegistry = new SimpleMeterRegistry();
        metrics = new FailoverMetricsImpl(meterRegistry);
        converter = new AiDialectBackendMessageConverter();
        interceptor = new AiGatewayFailoverInterceptor();
        interceptor.setBreaker(breaker);
        interceptor.setRegistry(registry);
        interceptor.setConverter(converter);
        interceptor.setMetrics(metrics);
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

    // ======================= 流式：接管 + 切换/重订阅（重执行回调）+ 反向转换 + 生命周期配对 =======================

    @Test
    void streamingTakeoverResubscribeSwitchAndElementMetrics() {
        // 阈值 1：attempt1 单次失败即 CLOSED→OPEN（熔断迁移断言需要）。
        breaker = new ThresholdBreaker(1, 0);
        interceptor.setBreaker(breaker);
        fake.queueStream(StreamScenario.failing(streamError(429, quotaBody())))
                .queueStream(StreamScenario.success(anthropicChunk("hi"), anthropicChunk(" there")));

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-switch-model", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/stream");
        CompletionStage<ApiResponse<?>> future = handler.handle(request, ctx);
        ApiResponse<?> response = future.toCompletableFuture().join();
        assertTrue(response.isOk(), "路由执行期必须成功");

        StreamingResponse streamingResponse = (StreamingResponse) ctx.getAttribute(StreamingResponse.class.getName());
        assertNotNull(streamingResponse);
        StreamCollector collector = new StreamCollector();
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();

        assertEquals(2, fake.requests.size(), "重执行回调必须被缓冲层运行时调用（接线）");
        assertNull(collector.error);
        assertTrue(collector.completed);
        // 接管计数：流式首次候选下沉（attempt1 = gw-test2 主账号）。
        assertEquals(1L, counter(TAKEOVER, "provider", "gw-test2", "model", "gw-model-2", "account", ""));
        // 重执行回调切换计数（attempt2 = gw-anthropic）+ 重订阅计数。
        assertEquals(1L, counter(SWITCH, "provider", "gw-anthropic", "model", "gw-claude-model", "account", ""));
        assertEquals(1L, counter(RESUBSCRIBE, "provider", "gw-anthropic", "model", "gw-claude-model", "account", ""));
        // 熔断迁移：attempt1 失败 recordFailure → CLOSED→OPEN（breaker 阈值 1）+ 冷却启动。
        assertEquals(1L, counter(TRANSITION, "provider", "gw-test2", "model", "gw-model-2", "to-state", "OPEN"));
        assertEquals(1L, counter(COOLDOWN, "provider", "gw-test2", "model", "gw-model-2", "type", "started"));
        // onStreamElement per-attempt 反向转换（attempt2 两个 chunk）。
        assertEquals(2L, counter(STREAM_ELEMENT, "provider", "gw-anthropic", "model", "gw-claude-model", "account", ""));
        // 生命周期回调并发配对：两次 fetch（attempt1 + attempt2）acquire == release。
        assertEquals(counterTotal(ACQUIRE), counterTotal(RELEASE), "生命周期并发配对必须平衡");
        assertTrue(counterTotal(ACQUIRE) >= 2);
        // 非流式路径指标不触发；无降级终止。
        assertEquals(0L, counter(SUCCESS));
        assertEquals(0L, counter(FAILURE));
        assertEquals(0L, counter(DEGRADED));
    }

    // ======================= 非流式：切换 + 成功率/延迟 Timer =======================

    @Test
    void nonStreamingSwitchAndOutcomeMetrics() {
        interceptor.setPrimaryProvider("gw-test");
        fake.queueFailure(networkError());
        fake.queueResponse(okResponse());

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-model-1", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/nonstream");
        ApiResponse<?> response = handler.handle(request, ctx).toCompletableFuture().join();

        assertTrue(response.isOk());
        assertEquals(2, fake.requests.size());
        // 切换计数（invoke 内 attempt>0 非固定候选）：维度 = 新候选。
        assertEquals(1L, counter(SWITCH, "provider", "gw-test", "model", "gw-model-1", "account", "key-***(8)"));
        // 成功率/延迟（attempt 级）：attempt1 网络错误失败（主账号）、attempt2 成功（备份账号）。
        assertEquals(1L, counter(FAILURE, "provider", "gw-test", "model", "gw-model-1", "account", ""));
        assertEquals(1L, counter(SUCCESS, "provider", "gw-test", "model", "gw-model-1", "account", "key-***(8)"));
        assertEquals(1L, timerCount(DURATION, "outcome", "failure"));
        assertEquals(1L, timerCount(DURATION, "outcome", "success"));
        // 并发配对 + 非流式不触发接管/流式指标。
        assertEquals(counterTotal(ACQUIRE), counterTotal(RELEASE));
        assertEquals(0L, counter(TAKEOVER));
        assertEquals(0L, counter(STREAM_ELEMENT));
    }

    // ======================= 流式 NON_TRANSIENT 降级终止 =======================

    @Test
    void nonTransientStreamingErrorCountsDegradedTermination() {
        fake.queueStream(StreamScenario.failing(streamError(400, nonTransientBody())));

        ApiRequest<Object> request = W7GatewayTestSupport.aiRequest("gw-switch-model", "hello");
        IGatewayContext ctx = W7GatewayTestSupport.context(request, "/chat/stream");
        handler.handle(request, ctx).toCompletableFuture().join();

        StreamingResponse streamingResponse = (StreamingResponse) ctx.getAttribute(StreamingResponse.class.getName());
        assertNotNull(streamingResponse);
        StreamCollector collector = new StreamCollector();
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();

        // NON_TRANSIENT → 不重试（1 次 fetch）→ onError 降级终止响应计数。
        assertEquals(1, fake.requests.size());
        assertEquals(1L, counter(DEGRADED, "provider", "gw-test2", "model", "gw-model-2", "account", ""));
        assertEquals(0L, counter(RESUBSCRIBE));
        assertEquals(0L, counter(SWITCH));
        assertEquals(counterTotal(ACQUIRE), counterTotal(RELEASE));
    }

    // ======================= 辅助 =======================

    private static RuntimeException networkError() {
        return new RuntimeException("connection refused");
    }

    private long counter(String name, String... tags) {
        // 求和：同名称同标签子集可能命中多个 meter。
        return meterRegistry.find(name).tags(tags).counters().stream()
                .mapToLong(c -> (long) c.count()).sum();
    }

    private long timerCount(String name, String... tags) {
        Timer timer = meterRegistry.find(name).tags(tags).timer();
        return timer != null ? timer.count() : 0L;
    }

    private long counterTotal(String name) {
        return meterRegistry.get(name).counters().stream()
                .mapToLong(c -> (long) c.count()).sum();
    }

    private static io.nop.http.api.client.IHttpResponse okResponse() {
        return new io.nop.http.api.client.IHttpResponse() {
            @Override
            public int getHttpStatus() {
                return 200;
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
                return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }

            @Override
            public String getBodyAsString() {
                return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}";
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getBodyAsBean(Class<T> beanClass) {
                if (beanClass == io.nop.api.core.beans.ApiResponse.class) {
                    io.nop.api.core.beans.ApiResponse<Object> resp = new io.nop.api.core.beans.ApiResponse<>();
                    resp.setData(io.nop.core.lang.json.JsonTool.parseMap(
                            "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}"));
                    return (T) resp;
                }
                throw new UnsupportedOperationException("getBodyAsBean: " + beanClass);
            }

            @Override
            public Object getBody() {
                return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}";
            }

            @Override
            public java.util.Map<String, String> getHeaders() {
                return new java.util.LinkedHashMap<>();
            }
        };
    }
}
