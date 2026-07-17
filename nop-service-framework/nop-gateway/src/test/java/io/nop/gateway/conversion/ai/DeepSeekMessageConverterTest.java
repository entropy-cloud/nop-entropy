package io.nop.gateway.conversion.ai;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.conversion.IBackendMessageConverter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DeepSeekMessageConverterTest {

    private IBackendMessageConverter converter() {
        return new DeepSeekMessageConverter();
    }

    private Map<String, Object> openAiRequest() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", "deepseek-chat");
        data.put("messages", List.of(
                Map.of("role", "user", "content", "hello"),
                Map.of("role", "assistant", "content", "hi there")));
        data.put("temperature", 0.5);
        return data;
    }

    @Test
    void toBackendRequest_passesDataThroughAsCopy() {
        ApiRequest<Map<String, Object>> req = ApiRequest.build(openAiRequest());
        ApiRequest<?> backend = converter().toBackendRequest(req);

        assertNotNull(backend.getData());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) backend.getData();
        // DeepSeek is OpenAI-compatible: data passes through unchanged
        assertEquals("deepseek-chat", body.get("model"));
        assertEquals(0.5, body.get("temperature"));
        assertNotNull(body.get("messages"));
        // defensive copy, not the same reference
        assertNotSame(req.getData(), body);
    }

    @Test
    void toFrontendResponse_passesDataThrough() {
        Map<String, Object> geminiLike = new LinkedHashMap<>();
        geminiLike.put("object", "chat.completion");
        geminiLike.put("choices", List.of(Map.of(
                "message", Map.of("role", "assistant", "content", "answer"))));

        ApiResponse<Map<String, Object>> backend = ApiResponse.success(geminiLike);
        backend.setHttpStatus(200);
        ApiResponse<?> frontend = converter().toFrontendResponse(backend, ApiRequest.build(Map.of()));

        assertEquals(200, frontend.getHttpStatus());
        assertNotNull(frontend.getData());
    }

    @Test
    void toFrontendStreamChunk_passesDeltaThrough() {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("object", "chat.completion.chunk");
        Map<String, Object> out = converter().toFrontendStreamChunk(delta, ApiRequest.build(Map.of()));
        assertNotNull(out);
        assertEquals("chat.completion.chunk", out.get("object"));
    }
}
