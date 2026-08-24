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

    // header key 统一小写：与生产链路一致（VertxHttpServerContext/ServletHttpServerContext
    // 均把 header key 小写化后放入 ApiMessage.headers，见 IHttpServerContext.HEADER_AUTHORIZATION）

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
        request.setHeaders(Map.of("authorization", "Bearer sk-test-key"));
        IGatewayContext ctx = createContext("/v1/chat/completions");

        assertDoesNotThrow(() -> interceptor.onRequest(request, ctx));
    }

    @Test
    void invalidKey_rejects() {
        AiAuthGatewayInterceptor interceptor = new AiAuthGatewayInterceptor();
        interceptor.setValidKeys(List.of("sk-valid-key"));

        ApiRequest<Map<String, String>> request = ApiRequest.build(Map.of());
        request.setHeaders(Map.of("authorization", "Bearer sk-invalid-key"));
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

    @Test
    void nullValidKeys_rejectsAllRequests() {
        // setValidKeys(null) 必须归一为空集合（fail-closed 拒绝），而不是 onRequest 中 NPE
        AiAuthGatewayInterceptor interceptor = new AiAuthGatewayInterceptor();
        interceptor.setValidKeys(null);

        ApiRequest<Map<String, String>> request = ApiRequest.build(Map.of());
        request.setHeaders(Map.of("authorization", "Bearer sk-test-key"));
        IGatewayContext ctx = createContext("/v1/chat/completions");

        assertThrows(GatewayRejectException.class, () -> interceptor.onRequest(request, ctx));
    }

    @Test
    void emptyValidKeys_rejectsAllRequests() {
        // 空集合 = 拒绝所有请求（fail-closed），默认装配未配置 validKeys 时即此行为
        AiAuthGatewayInterceptor interceptor = new AiAuthGatewayInterceptor();
        interceptor.setValidKeys(List.of());

        ApiRequest<Map<String, String>> request = ApiRequest.build(Map.of());
        request.setHeaders(Map.of("authorization", "Bearer sk-test-key"));
        IGatewayContext ctx = createContext("/v1/chat/completions");

        assertThrows(GatewayRejectException.class, () -> interceptor.onRequest(request, ctx));
    }

    @Test
    void maskKey_neverLeaksFullToken() {
        // 无效 key 的 warn 日志只允许携带前 4 位 + 长度，不得出现完整凭证原文
        assertEquals("sk-t***len=19", AiAuthGatewayInterceptor.maskKey("sk-test-key-1234567"));
        assertEquals("***len=8", AiAuthGatewayInterceptor.maskKey("sk-12345"));
        assertEquals("***", AiAuthGatewayInterceptor.maskKey(null));
        assertEquals("***", AiAuthGatewayInterceptor.maskKey(""));
        String masked = AiAuthGatewayInterceptor.maskKey("sk-abcdefghijklmnop");
        assertFalse(masked.contains("abcdefghijklmnop"), "脱敏输出不得包含完整凭证片段");
    }
}
