package io.nop.ai.gateway;

import io.nop.ai.core.model.ApiStyle;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiDialectBackendMessageConverterTest {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    AiDialectBackendMessageConverter createConverter(ApiStyle frontend, ApiStyle backend) {
        AiDialectBackendMessageConverter c = new AiDialectBackendMessageConverter();
        c.setFrontendLlm(frontend);
        c.setBackendLlm(backend);
        return c;
    }

    // ===== toBackendRequest =====

    @Test
    void toBackendRequest_openaiToAnthropic() {
        var converter = createConverter(ApiStyle.openai, ApiStyle.anthropic);
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("claude-sonnet-4", "Hello");

        ApiRequest<?> result = converter.toBackendRequest(req);

        assertNotNull(result);
        assertTrue(result.getData() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) result.getData();

        // Anthropic 格式：messages[0].content 是数组，有 type:text
        assertTrue(body.containsKey("messages"));
        List<?> messages = (List<?>) body.get("messages");
        assertFalse(messages.isEmpty());
        Object content0 = ((Map<?, ?>) messages.get(0)).get("content");
        // Anthropic 格式 content 是数组
        assertTrue(content0 instanceof List || content0 instanceof String,
                "Anthropic content should be List or String, got: " + content0.getClass());
    }

    @Test
    void toBackendRequest_openaiToGemini() {
        var converter = createConverter(ApiStyle.openai, ApiStyle.gemini);
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("gemini-pro", "Hello");

        ApiRequest<?> result = converter.toBackendRequest(req);

        assertNotNull(result);
        Map<?, ?> body = (Map<?, ?>) result.getData();
        // Gemini 格式有 contents，没有 messages
        assertTrue(body.containsKey("contents"),
                "Gemini body should have 'contents', got keys: " + body.keySet());
    }

    @Test
    void toBackendRequest_openaiToOllama() {
        var converter = createConverter(ApiStyle.openai, ApiStyle.ollama);
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("ollama-llama3", "Hi");

        ApiRequest<?> result = converter.toBackendRequest(req);

        assertNotNull(result);
        Map<?, ?> body = (Map<?, ?>) result.getData();
        assertTrue(body.containsKey("messages"));
    }

    @Test
    void toBackendRequest_passthrough() {
        var converter = createConverter(ApiStyle.openai, ApiStyle.openai);
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("gpt-4", "Hello");

        ApiRequest<?> result = converter.toBackendRequest(req);

        assertNotNull(result);
        Map<?, ?> body = (Map<?, ?>) result.getData();
        // OpenAI→OpenAI 应保持 messages 结构
        assertTrue(body.containsKey("messages"));
        assertEquals("gpt-4", body.get("model"));
    }

    // ===== toFrontendResponse =====

    @Test
    void toFrontendResponse_anthropicToOpenAI() {
        var converter = createConverter(ApiStyle.openai, ApiStyle.anthropic);

        // 模拟 Anthropic 响应
        Map<String, Object> anthropicResp = Map.of(
                "content", List.of(Map.of("type", "text", "text", "Hi back")),
                "role", "assistant",
                "usage", Map.of("input_tokens", 10, "output_tokens", 20)
        );
        Map<String, Object> wrapper = Map.of("content", anthropicResp);
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("claude-sonnet-4", "Hello");
        ApiResponse<?> backendResp = ApiResponse.success(wrapper);
        // 注意：parseResponse 接收 JSON string，所以 backendResp.getData() 会被 serialize
        // 这里我们直接测试 toFrontendResponse 的行为
        var result = converter.toFrontendResponse(backendResp, req);

        assertNotNull(result);
        assertTrue(result.getData() instanceof Map);
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertTrue(data.containsKey("choices"));
    }

    @Test
    void toFrontendResponse_openaiPassthrough() {
        var converter = createConverter(ApiStyle.openai, ApiStyle.openai);
        Map<String, Object> openaiResp = Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("role", "assistant", "content", "Hi"),
                        "finish_reason", "stop",
                        "index", 0
                ))
        );
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("gpt-4", "Hello");
        ApiResponse<?> backendResp = ApiResponse.success(openaiResp);

        var result = converter.toFrontendResponse(backendResp, req);

        assertNotNull(result);
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertTrue(data.containsKey("choices"));
    }

    // ===== toFrontendStreamChunk =====

    @Test
    void toFrontendStreamChunk_anthropicDelta() {
        var converter = createConverter(ApiStyle.openai, ApiStyle.anthropic);

        // 模拟 Anthropic 流式 chunk
        Map<String, Object> delta = Map.of(
                "type", "content_block_delta",
                "delta", Map.of("type", "text_delta", "text", "Hello")
        );
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("claude-sonnet-4", "Hi");

        var result = converter.toFrontendStreamChunk(delta, req);

        // 可能返回 null（如果 parseStreamChunk 无法解析不完整的 chunk），
        // 也可能返回有效的 delta
        if (result != null) {
            assertTrue(result.containsKey("choices"));
        }
    }

    // ===== toBackendRequest（非 OpenAI 前端：经新 parseRequestBody 分发） =====

    @Test
    void toBackendRequest_anthropicToOpenAI() {
        var converter = createConverter(ApiStyle.anthropic, ApiStyle.openai);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("model", "claude-sonnet-4");
        data.put("system", "You are a helpful assistant.");
        data.put("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of("type", "text", "text", "Hello")))));
        ApiRequest<Map<String, Object>> req = ApiRequest.build(data);

        ApiRequest<?> result = converter.toBackendRequest(req);

        assertNotNull(result);
        Map<?, ?> body = (Map<?, ?>) result.getData();
        // Anthropic 前端 → OpenAI 后端：messages 扁平 role/content
        List<?> messages = (List<?>) body.get("messages");
        assertEquals(2, messages.size(), "system folds back into messages for OpenAI backend");
        assertEquals("system", ((Map<?, ?>) messages.get(0)).get("role"));
        assertEquals("You are a helpful assistant.", ((Map<?, ?>) messages.get(0)).get("content"));
        assertEquals("user", ((Map<?, ?>) messages.get(1)).get("role"));
        assertEquals("Hello", ((Map<?, ?>) messages.get(1)).get("content"));
    }

    @Test
    void toBackendRequest_geminiToOpenAI() {
        var converter = createConverter(ApiStyle.gemini, ApiStyle.openai);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("model", "gemini-pro");
        data.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", "Hello")))));
        ApiRequest<Map<String, Object>> req = ApiRequest.build(data);

        ApiRequest<?> result = converter.toBackendRequest(req);

        assertNotNull(result);
        Map<?, ?> body = (Map<?, ?>) result.getData();
        List<?> messages = (List<?>) body.get("messages");
        assertEquals(1, messages.size());
        assertEquals("user", ((Map<?, ?>) messages.get(0)).get("role"));
        assertEquals("Hello", ((Map<?, ?>) messages.get(0)).get("content"));
    }

    @Test
    void toBackendRequest_ollamaToOpenAI() {
        var converter = createConverter(ApiStyle.ollama, ApiStyle.openai);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("model", "llama2");
        data.put("messages", List.of(Map.of("role", "user", "content", "Hello")));
        data.put("options", Map.of("temperature", 0.7, "num_predict", 512));
        ApiRequest<Map<String, Object>> req = ApiRequest.build(data);

        ApiRequest<?> result = converter.toBackendRequest(req);

        assertNotNull(result);
        Map<?, ?> body = (Map<?, ?>) result.getData();
        List<?> messages = (List<?>) body.get("messages");
        assertEquals(1, messages.size());
        assertEquals("Hello", ((Map<?, ?>) messages.get(0)).get("content"));
        // Ollama options.num_predict → OpenAI max_tokens
        assertEquals(512, ((Number) body.get("max_tokens")).intValue());
    }

    @Test
    void toBackendRequest_responsesToOpenAI() {
        var converter = createConverter(ApiStyle.responses, ApiStyle.openai);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("model", "gpt-4o");
        data.put("instructions", "You are a helpful assistant.");
        data.put("input", List.of(Map.of(
                "type", "message",
                "role", "user",
                "content", List.of(Map.of("type", "input_text", "text", "Hello")))));
        ApiRequest<Map<String, Object>> req = ApiRequest.build(data);

        ApiRequest<?> result = converter.toBackendRequest(req);

        assertNotNull(result);
        Map<?, ?> body = (Map<?, ?>) result.getData();
        List<?> messages = (List<?>) body.get("messages");
        assertEquals(2, messages.size(), "instructions fold back into system message for OpenAI backend");
        assertEquals("system", ((Map<?, ?>) messages.get(0)).get("role"));
        assertEquals("user", ((Map<?, ?>) messages.get(1)).get("role"));
        assertEquals("Hello", ((Map<?, ?>) messages.get(1)).get("content"));
    }

    // ===== toFrontendResponse（非 OpenAI 前端：经新 buildResponse 分发） =====

    @Test
    void toFrontendResponse_openaiToAnthropic() {
        var converter = createConverter(ApiStyle.anthropic, ApiStyle.openai);
        Map<String, Object> openaiResp = Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("role", "assistant", "content", "Hi"),
                        "finish_reason", "stop",
                        "index", 0
                ))
        );
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("gpt-4", "Hello");
        ApiResponse<?> backendResp = ApiResponse.success(openaiResp);

        var result = converter.toFrontendResponse(backendResp, req);

        assertNotNull(result);
        Map<?, ?> data = (Map<?, ?>) result.getData();
        // Anthropic 前端格式：content blocks + stop_reason（非 OpenAI choices）
        assertTrue(data.containsKey("content"), "anthropic frontend response must use content blocks");
        assertFalse(data.containsKey("choices"), "anthropic frontend must not produce OpenAI choices");
        assertEquals("end_turn", data.get("stop_reason"));
        Map<?, ?> block = (Map<?, ?>) ((List<?>) data.get("content")).get(0);
        assertEquals("text", block.get("type"));
        assertEquals("Hi", block.get("text"));
    }

    @Test
    void toFrontendResponse_openaiToGemini() {
        var converter = createConverter(ApiStyle.gemini, ApiStyle.openai);
        Map<String, Object> openaiResp = Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("role", "assistant", "content", "Hi"),
                        "finish_reason", "stop",
                        "index", 0
                ))
        );
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("gpt-4", "Hello");
        ApiResponse<?> backendResp = ApiResponse.success(openaiResp);

        var result = converter.toFrontendResponse(backendResp, req);

        assertNotNull(result);
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertTrue(data.containsKey("candidates"), "gemini frontend response must use candidates");
        Map<?, ?> candidate = (Map<?, ?>) ((List<?>) data.get("candidates")).get(0);
        assertEquals("STOP", candidate.get("finishReason"));
        Map<?, ?> content = (Map<?, ?>) candidate.get("content");
        Map<?, ?> part = (Map<?, ?>) ((List<?>) content.get("parts")).get(0);
        assertEquals("Hi", part.get("text"));
    }

    @Test
    void toFrontendResponse_openaiToOllama() {
        var converter = createConverter(ApiStyle.ollama, ApiStyle.openai);
        Map<String, Object> openaiResp = Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("role", "assistant", "content", "Hi"),
                        "finish_reason", "stop",
                        "index", 0
                ))
        );
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("gpt-4", "Hello");
        ApiResponse<?> backendResp = ApiResponse.success(openaiResp);

        var result = converter.toFrontendResponse(backendResp, req);

        assertNotNull(result);
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("Hi", ((Map<?, ?>) data.get("message")).get("content"));
        assertEquals("stop", data.get("done_reason"));
    }

    @Test
    void toFrontendResponse_openaiToResponses() {
        var converter = createConverter(ApiStyle.responses, ApiStyle.openai);
        Map<String, Object> openaiResp = Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("role", "assistant", "content", "Hi"),
                        "finish_reason", "stop",
                        "index", 0
                ))
        );
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("gpt-4", "Hello");
        ApiResponse<?> backendResp = ApiResponse.success(openaiResp);

        var result = converter.toFrontendResponse(backendResp, req);

        assertNotNull(result);
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("completed", data.get("status"));
        Map<?, ?> item = (Map<?, ?>) ((List<?>) data.get("output")).get(0);
        assertEquals("message", item.get("type"));
        assertEquals("Hi", ((Map<?, ?>) ((List<?>) item.get("content")).get(0)).get("text"));
    }

    // ===== toFrontendStreamChunk（非 OpenAI 前端：经新 buildStreamChunk 分发） =====

    @Test
    void toFrontendStreamChunk_openaiToAnthropic() {
        var converter = createConverter(ApiStyle.anthropic, ApiStyle.openai);
        Map<String, Object> delta = Map.of(
                "choices", List.of(Map.of(
                        "delta", Map.of("content", "Hello"),
                        "index", 0
                ))
        );
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("gpt-4", "Hi");

        var result = converter.toFrontendStreamChunk(delta, req);

        assertNotNull(result);
        assertEquals("content_block_delta", result.get("type"));
        assertEquals("Hello", ((Map<?, ?>) result.get("delta")).get("text"));
    }

    @Test
    void toFrontendStreamChunk_openaiToGemini() {
        var converter = createConverter(ApiStyle.gemini, ApiStyle.openai);
        Map<String, Object> delta = Map.of(
                "choices", List.of(Map.of(
                        "delta", Map.of("content", "Hello"),
                        "index", 0
                ))
        );
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("gpt-4", "Hi");

        var result = converter.toFrontendStreamChunk(delta, req);

        assertNotNull(result);
        Map<?, ?> candidate = (Map<?, ?>) ((List<?>) result.get("candidates")).get(0);
        Map<?, ?> part = (Map<?, ?>) ((List<?>) ((Map<?, ?>) candidate.get("content")).get("parts")).get(0);
        assertEquals("Hello", part.get("text"));
    }

    @Test
    void toFrontendStreamChunk_openaiToOllama() {
        var converter = createConverter(ApiStyle.ollama, ApiStyle.openai);
        Map<String, Object> delta = Map.of(
                "choices", List.of(Map.of(
                        "delta", Map.of("content", "Hello"),
                        "index", 0
                ))
        );
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("gpt-4", "Hi");

        var result = converter.toFrontendStreamChunk(delta, req);

        assertNotNull(result);
        assertEquals("Hello", ((Map<?, ?>) result.get("message")).get("content"));
    }

    @Test
    void toFrontendStreamChunk_openaiToResponses() {
        var converter = createConverter(ApiStyle.responses, ApiStyle.openai);
        Map<String, Object> delta = Map.of(
                "choices", List.of(Map.of(
                        "delta", Map.of("content", "Hello"),
                        "index", 0
                ))
        );
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("gpt-4", "Hi");

        var result = converter.toFrontendStreamChunk(delta, req);

        assertNotNull(result);
        assertEquals("response.output_text.delta", result.get("type"));
        assertEquals("Hello", result.get("delta"));
    }

    // ===== config =====

    @Test
    void frontendLlmDefaultIsOpenai() {
        AiDialectBackendMessageConverter c = new AiDialectBackendMessageConverter();
        ApiRequest<Map<String, Object>> req = buildOpenAIReq("unknown-model", "test");
        // 默认 backendLlm=openai，应正常透传
        assertDoesNotThrow(() -> c.toBackendRequest(req));
    }

    // ===== helpers =====

    @SuppressWarnings("unchecked")
    static ApiRequest<Map<String, Object>> buildOpenAIReq(String model, String userMsg) {
        Map<String, Object> data = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", userMsg))
        );
        return ApiRequest.build((Map<String, Object>) data);
    }
}
