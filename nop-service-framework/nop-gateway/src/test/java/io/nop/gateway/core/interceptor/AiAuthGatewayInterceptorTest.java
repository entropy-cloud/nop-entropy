package io.nop.gateway.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.gateway.GatewayRejectException;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.context.IGatewayContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiAuthGatewayInterceptorTest {

    private IGatewayContext createContext(String path) {
        GatewayContextImpl ctx = new GatewayContextImpl();
        ctx.setRequestPath(path);
        ctx.setRequest(ApiRequest.build(Map.of()));
        return ctx;
    }

    @Test
    void validKey_passes() {
        AiAuthGatewayInterceptor interceptor = new AiAuthGatewayInterceptor();
        interceptor.setValidKeys(List.of("sk-test-key"));

        ApiRequest<Map<String, String>> request = ApiRequest.build(Map.of());
        request.setHeaders(Map.of("Authorization", "Bearer sk-test-key"));
        IGatewayContext ctx = createContext("/v1/chat/completions");

        assertDoesNotThrow(() -> interceptor.onRequest(request, ctx));
    }

    @Test
    void invalidKey_rejects() {
        AiAuthGatewayInterceptor interceptor = new AiAuthGatewayInterceptor();
        interceptor.setValidKeys(List.of("sk-valid-key"));

        ApiRequest<Map<String, String>> request = ApiRequest.build(Map.of());
        request.setHeaders(Map.of("Authorization", "Bearer sk-invalid-key"));
        IGatewayContext ctx = createContext("/v1/chat/completions");

        assertThrows(GatewayRejectException.class, () -> interceptor.onRequest(request, ctx));
    }

    @Test
    void missingAuthHeader_rejects() {
        AiAuthGatewayInterceptor interceptor = new AiAuthGatewayInterceptor();
        interceptor.setValidKeys(List.of("sk-test-key"));

        ApiRequest<Map<String, String>> request = ApiRequest.build(Map.of());
        IGatewayContext ctx = createContext("/v1/chat/completions");

        assertThrows(GatewayRejectException.class, () -> interceptor.onRequest(request, ctx));
    }
}
