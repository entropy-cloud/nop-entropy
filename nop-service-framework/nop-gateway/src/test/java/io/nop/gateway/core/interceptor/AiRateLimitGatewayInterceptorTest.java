package io.nop.gateway.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.GatewayRejectException;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.context.IGatewayContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiRateLimitGatewayInterceptorTest {

    private IGatewayContext createContext() {
        GatewayContextImpl ctx = new GatewayContextImpl();
        ctx.setRequest(ApiRequest.build(Map.of()));
        return ctx;
    }

    @Test
    void underLimit_requestsPass() {
        AiRateLimitGatewayInterceptor interceptor = new AiRateLimitGatewayInterceptor();
        interceptor.setCapacity(10);
        interceptor.setRefillRate(1);
        interceptor.setRefillIntervalMs(1000);

        ApiRequest<Map<String, String>> request = ApiRequest.build(Map.of());
        IGatewayContext ctx = createContext();

        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> interceptor.onRequest(request, ctx));
        }
    }

    @Test
    void overLimit_throwsRejection() {
        AiRateLimitGatewayInterceptor interceptor = new AiRateLimitGatewayInterceptor();
        interceptor.setCapacity(5);
        interceptor.setRefillRate(1);
        interceptor.setRefillIntervalMs(100000); // 不会在测试期间 refill

        ApiRequest<Map<String, String>> request = ApiRequest.build(Map.of());
        IGatewayContext ctx = createContext();

        // 前 5 次通过
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> interceptor.onRequest(request, ctx));
        }
        // 第 6 次被限流
        assertThrows(GatewayRejectException.class, () -> interceptor.onRequest(request, ctx));
    }

    // ======================= header key 约定（生产链路小写化） =======================

    private IGatewayContext createContextWithXff(String xff) {
        GatewayContextImpl ctx = new GatewayContextImpl();
        ApiRequest<Map<String, String>> request = ApiRequest.build(Map.of());
        // 生产链路 header key 已被 HTTP 层小写化（Vertx/Servlet 实现均如此），
        // 测试必须按小写 key 注入才与真实流量一致
        request.setHeaders(Map.of("x-forwarded-for", xff));
        ctx.setRequest(request);
        return ctx;
    }

    @Test
    void distinctClientIps_getIndependentBuckets() {
        // 两个不同客户端 IP 必须各自持有独立令牌桶，而不是塌缩到共享 "default" 桶
        AiRateLimitGatewayInterceptor interceptor = new AiRateLimitGatewayInterceptor();
        interceptor.setCapacity(1);
        interceptor.setRefillRate(1);
        interceptor.setRefillIntervalMs(100000); // 测试期间不 refill

        ApiRequest<Map<String, String>> request = ApiRequest.build(Map.of());

        // IP A 的第 1 次通过、第 2 次被限流
        IGatewayContext ctxA = createContextWithXff("203.0.113.10");
        assertDoesNotThrow(() -> interceptor.onRequest(request, ctxA));
        assertThrows(GatewayRejectException.class, () -> interceptor.onRequest(request, ctxA));

        // IP B 的第 1 次必须仍然通过（独立桶，未被 A 的耗尽影响）
        IGatewayContext ctxB = createContextWithXff("198.51.100.20");
        assertDoesNotThrow(() -> interceptor.onRequest(request, ctxB),
                "不同客户端 IP 必须使用独立的限流桶");
    }

    @Test
    void resolveKey_readsLowercaseXffHeader_firstEntry() {
        AiRateLimitGatewayInterceptor interceptor = new AiRateLimitGatewayInterceptor();

        // XFF: client, proxy1, proxy2 → 取第一项（客户端 IP）
        assertEquals("203.0.113.10",
                interceptor.resolveKey(createContextWithXff("203.0.113.10, 10.0.0.1")));
        assertEquals("198.51.100.20",
                interceptor.resolveKey(createContextWithXff("198.51.100.20")));
    }

    @Test
    void resolveKey_missingOrInvalidXff_fallsBackToDefault() {
        AiRateLimitGatewayInterceptor interceptor = new AiRateLimitGatewayInterceptor();

        // 无 header → default
        assertEquals("default", interceptor.resolveKey(createContext()));
        // 非法值（非 IP 形态文本，如注入垃圾串）→ default，不得进入桶 key 空间
        assertEquals("default", interceptor.resolveKey(createContextWithXff("<script>alert(1)</script>")));
        assertEquals("default", interceptor.resolveKey(createContextWithXff("unknown")));
    }

    // ======================= 桶缓存淘汰（内存有界） =======================

    private static IGatewayContext ctxWithXff(String xff) {
        GatewayContextImpl ctx = new GatewayContextImpl();
        ApiRequest<Map<String, String>> request = ApiRequest.build(Map.of());
        request.setHeaders(Map.of("x-forwarded-for", xff));
        ctx.setRequest(request);
        return ctx;
    }

    @Test
    void trackedKeys_cappedByMaxTrackedKeys() {
        // 伪造 XFF 轮换大量不同 key 时，桶缓存规模必须被 maxTrackedKeys 封顶（内存有界）
        AiRateLimitGatewayInterceptor interceptor = new AiRateLimitGatewayInterceptor();
        interceptor.setCapacity(100);
        interceptor.setMaxTrackedKeys(50);

        ApiRequest<Map<String, String>> request = ApiRequest.build(Map.of());
        for (int i = 0; i < 300; i++) {
            interceptor.onRequest(request, ctxWithXff("10." + (i / 256) + "." + (i % 256) + ".1"));
        }

        interceptor.cleanUpBuckets();
        assertTrue(interceptor.trackedKeyCount() <= 50,
                "桶数量必须被 maxTrackedKeys 封顶，实际=" + interceptor.trackedKeyCount());
    }

    @Test
    void idleBuckets_expiredAfterIdleTtl() {
        // TTL = ceil(capacity/refillRate)*refillIntervalMs；此处 1ms，静置后应被清扫回收
        AiRateLimitGatewayInterceptor interceptor = new AiRateLimitGatewayInterceptor();
        interceptor.setCapacity(1);
        interceptor.setRefillRate(1000);
        interceptor.setRefillIntervalMs(1);

        ApiRequest<Map<String, String>> request = ApiRequest.build(Map.of());
        interceptor.onRequest(request, ctxWithXff("203.0.113.30"));
        interceptor.onRequest(request, ctxWithXff("203.0.113.31"));
        assertTrue(interceptor.trackedKeyCount() > 0);

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        interceptor.cleanUpBuckets();
        assertEquals(0, interceptor.trackedKeyCount(), "空闲超过 TTL 的桶必须被淘汰回收");
    }
}
