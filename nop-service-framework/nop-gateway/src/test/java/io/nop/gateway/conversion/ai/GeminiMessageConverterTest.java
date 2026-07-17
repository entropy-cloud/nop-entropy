package io.nop.gateway.conversion.ai;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.conversion.IBackendMessageConverter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GeminiMessageConverterTest {

    private IBackendMessageConverter converter() {
        return new GeminiMessageConverter();
    }

    @Test
    void toBackendRequest_mapsMessagesToContentsAndSystemInstruction() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", "gemini-pro");
        data.put("messages", List.of(
                Map.of("role", "system", "content", "be helpful"),
                Map.of("role", "user", "content", "hello"),
                Map.of("role", "assistant", "content", "hi")));
        data.put("temperature", 0.7);
        data.put("max_tokens", 1000);
        data.put("top_p", 0.9);

        ApiRequest<Map<String, Object>> req = ApiRequest.build(data);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) converter().toBackendRequest(req).getData();

        // system 消息 → systemInstruction
        assertNotNull(body.get("systemInstruction"));
        @SuppressWarnings("unchecked")
        Map<String, Object> sysInstruction = (Map<String, Object>) body.get("systemInstruction");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sysParts = (List<Map<String, Object>>) sysInstruction.get("parts");
        assertEquals("be helpful", sysParts.get(0).get("text"));

        // contents：user 和 assistant（assistant→model）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");
        assertEquals(2, contents.size());
        assertEquals("user", contents.get(0).get("role"));
        assertEquals("model", contents.get(1).get("role"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> userParts = (List<Map<String, Object>>) contents.get(0).get("parts");
        assertEquals("hello", userParts.get(0).get("text"));

        // generationConfig 参数映射
        @SuppressWarnings("unchecked")
        Map<String, Object> genConfig = (Map<String, Object>) body.get("generationConfig");
        assertEquals(0.7, genConfig.get("temperature"));
        assertEquals(1000, genConfig.get("maxOutputTokens"));
        assertEquals(0.9, genConfig.get("topP"));
    }

    @Test
    void toFrontendResponse_mapsCandidatesToChoices() {
        Map<String, Object> gemini = new LinkedHashMap<>();
        gemini.put("candidates", List.of(Map.of(
                "content", Map.of("role", "model",
                        "parts", List.of(Map.of("text", "Hi there"))),
                "finishReason", "STOP")));

        ApiResponse<Map<String, Object>> backend = ApiResponse.success(gemini);
        backend.setHttpStatus(200);

        Map<String, Object> requestData = new LinkedHashMap<>();
        requestData.put("id", "req-1");
        requestData.put("model", "gemini-pro");
        ApiResponse<?> frontend = converter().toFrontendResponse(backend, ApiRequest.build(requestData));

        @SuppressWarnings("unchecked")
        Map<String, Object> openai = (Map<String, Object>) frontend.getData();
        assertEquals("chat.completion", openai.get("object"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) openai.get("choices");
        assertEquals(1, choices.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        assertEquals("assistant", message.get("role"));
        assertEquals("Hi there", message.get("content"));
        assertEquals("stop", choices.get(0).get("finish_reason"));
    }

    @Test
    void toFrontendResponse_concatenatesMultipleParts() {
        Map<String, Object> gemini = new LinkedHashMap<>();
        gemini.put("candidates", List.of(Map.of(
                "content", Map.of("role", "model",
                        "parts", List.of(
                                Map.of("text", "part1"),
                                Map.of("text", "part2"))),
                "finishReason", "MAX_TOKENS")));

        ApiResponse<Map<String, Object>> backend = ApiResponse.success(gemini);
        backend.setHttpStatus(200);
        ApiResponse<?> frontend = converter().toFrontendResponse(backend, ApiRequest.build(Map.of()));

        @SuppressWarnings("unchecked")
        Map<String, Object> openai = (Map<String, Object>) frontend.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) openai.get("choices");
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        assertEquals("part1\npart2", message.get("content"));
        assertEquals("length", choices.get(0).get("finish_reason"));
    }

    @Test
    void toFrontendStreamChunk_mapsDeltaToOpenAiChunk() {
        Map<String, Object> geminiDelta = new LinkedHashMap<>();
        geminiDelta.put("candidates", List.of(Map.of(
                "content", Map.of("role", "model",
                        "parts", List.of(Map.of("text", "tok"))),
                "finishReason", "STOP")));

        Map<String, Object> requestData = new LinkedHashMap<>();
        requestData.put("id", "req-2");
        requestData.put("model", "gemini-pro");
        Map<String, Object> chunk = converter().toFrontendStreamChunk(geminiDelta, ApiRequest.build(requestData));

        assertNotNull(chunk);
        assertEquals("chat.completion.chunk", chunk.get("object"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
        @SuppressWarnings("unchecked")
        Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
        assertEquals("tok", delta.get("content"));
    }
}
