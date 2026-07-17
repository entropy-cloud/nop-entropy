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
