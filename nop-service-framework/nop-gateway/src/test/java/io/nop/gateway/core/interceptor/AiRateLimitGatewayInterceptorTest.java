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
}
