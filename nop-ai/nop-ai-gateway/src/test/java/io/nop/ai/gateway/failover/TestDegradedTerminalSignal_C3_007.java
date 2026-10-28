package io.nop.ai.gateway.failover;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.config.AppConfig;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.gateway.core.context.IGatewayContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static io.nop.ai.gateway.failover.FailoverTestSupport.nonTransientBody;
import static io.nop.ai.gateway.failover.FailoverTestSupport.quotaBody;
import static io.nop.ai.gateway.failover.FailoverTestSupport.streamError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * plan 2509 R2-06 成员 3.3-C3-007（C10-006 面）回归钉：降级信号显式化（裁定⑦(a)）——
 * 3.3 侧（nop-ai 域）半边。
 *
 * <p><b>裁定⑦(a) 契约</b>：流式 onError 只做重试性分类——可切换分类（含 CACHE_STATE_LOST）
 * 一律 rethrow（重试决策唯一归属缓冲层/重执行回调，降级不进数据元素通路的<b>宿主半边</b>由
 * nop-gateway {@code StreamingProcessor} 承载，归组 A 触面）；仅 NON_TRANSIENT 族返回显式
 * 降级终止响应（{@code onDegradedTermination} 计数）——降级终态不伪装成功、不进重试。
 *
 * <p><b>偏差登记（Phase 2 组 B 执行时点）</b>：裁定⑦(a) 的窗口内清缓冲 + 降级 wrapper
 * 唯一交付落点在 nop-gateway 宿主链（{@code StreamingProcessor.createMappedPublisher}
 * onError 分支），组 A 提交（41508cde）未覆盖该半边，且组 B 纪律禁触
 * nop-service-framework——本钉固定 3.3 侧降级终态契约面（NON_TRANSIENT 显式降级 /
 * 可切换显式 rethrow 双腿），宿主半边（窗口内零前缀泄漏 + 降级 wrapper 唯一交付）留待
 * 协调方在 nop-gateway 侧补落地。
 */
class TestDegradedTerminalSignal_C3_007 {

    private AiGatewayFailoverInterceptor interceptor;

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
        interceptor = new AiGatewayFailoverInterceptor();
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.gw-test.api-key", "key-gw-main");
    }

    @AfterEach
    void tearDown() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.gw-test.api-key", null);
        LlmConfigHelper.reset();
    }

    private IGatewayContext streamingContext(ApiRequest<?> request) {
        return (IGatewayContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IGatewayContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isStreamingMode" -> true;
                    case "getRequest" -> request;
                    default -> method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                });
    }

    private static ApiRequest<Object> activeRequest() {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setProperty("nop.ai.gateway.failover.active", true);
        request.setProperty("nop.ai.gateway.failover.provider", "gw-test");
        request.setProperty("nop.ai.gateway.failover.model", "gw-model-1");
        request.setProperty("nop.ai.gateway.failover.accountKey", "key-a");
        request.setProperty("nop.ai.gateway.failover.attempt", 1);
        return request;
    }

    @Test
    void nonTransientStreamFailureReturnsExplicitDegradedTerminalResponse() {
        // NON_TRANSIENT 族（400 invalid_request_error）→ 显式降级终止响应（非 rethrow、非重试）。
        ApiResponse<?> degraded = interceptor.onError(
                streamError(400, nonTransientBody()), streamingContext(activeRequest()));
        assertNotNull(degraded, "NON_TRANSIENT stream failure must surface the explicit degraded terminal");
        assertEquals(Boolean.TRUE, degraded.isWrapper(), "degraded terminal must be the wrapper response");
        assertEquals(400, degraded.getHttpStatus(),
                "degraded terminal must carry the upstream http status");
    }

    @Test
    void switchableStreamFailureIsRethrownForBufferLayerRetryDecision() {
        // 可切换分类（429 RATE_LIMITED）→ 一律 rethrow（重试决策唯一归属缓冲层，降级不可达）。
        assertThrows(io.nop.api.core.exceptions.NopException.class,
                () -> interceptor.onError(streamError(429, quotaBody()), streamingContext(activeRequest())),
                "switchable stream failure must be rethrown for the buffer-layer retry decision");
    }
}
