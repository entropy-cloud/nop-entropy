package io.nop.gateway.conversion;

import io.nop.api.core.beans.ApiRequest;
import io.nop.gateway.conversion.ai.AiBackendMessageConverter;
import io.nop.gateway.conversion.ai.AiBackendType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiBackendMessageConverterTest {

    @Test
    void throws_whenNoConverterRegistered() {
        // CLAUDE 没有 fallback 转换器，IoC 不可用时抛异常
        // 用户应通过 IoC 注册 nopBackendMessageConverter_CLAUDE 或使用 AI_DIALECT bean
        assertThrows(IllegalArgumentException.class, () ->
                AiBackendMessageConverter.getConverter(AiBackendType.CLAUDE));
    }

    @Test
    void toBackendRequest_deepseek_returnsNonNull() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", "deepseek-chat");
        data.put("messages", List.of(Map.of("role", "user", "content", "hi")));
        ApiRequest<Map<String, Object>> req = new ApiRequest<>();
        req.setData(data);

        ApiRequest<?> backend = AiBackendMessageConverter.toBackendRequest(req, AiBackendType.DEEPSEEK);
        assertNotNull(backend, "DEEPSEEK converter should produce a non-null backend request");
        assertNotNull(backend.getData(), "DEEPSEEK backend request data should be non-null");
    }

    @Test
    void toBackendRequest_gemini_returnsNonNullWithContents() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", "gemini-pro");
        data.put("messages", List.of(Map.of("role", "user", "content", "hi")));
        ApiRequest<Map<String, Object>> req = new ApiRequest<>();
        req.setData(data);

        ApiRequest<?> backend = AiBackendMessageConverter.toBackendRequest(req, AiBackendType.GEMINI);
        assertNotNull(backend, "GEMINI converter should produce a non-null backend request");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) backend.getData();
        assertNotNull(body.get("contents"), "GEMINI backend request should contain contents");
    }
}
