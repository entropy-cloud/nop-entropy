package io.nop.gateway.conversion;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.gateway.conversion.ai.AiBackendMessageConverter;
import io.nop.gateway.conversion.ai.AiBackendType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiBackendMessageConverterTest {

    @Test
    void convertsClaudeRequestMessagesWithTools() throws IOException {
        Map<String, Object> data = readJson("fixtures/openai_request_with_tools.json");
        Map<String, Object> expected = readJson("fixtures/claude_backend_request_expected.json");
        ApiRequest<?> request = ApiRequest.build(data);

        ApiRequest<?> backendRequest = AiBackendMessageConverter.toBackendRequest(request, AiBackendType.CLAUDE);

        assertEquals(expected, backendRequest.getData());
    }

    @Test
    void convertsClaudeResponseToOpenAI() throws IOException {
        Map<String, Object> backendData = readJson("fixtures/claude_backend_response.json");
        Map<String, Object> expected = readJson("fixtures/openai_response_from_claude_expected.json");
        ApiResponse<?> backendResponse = ApiResponse.success(backendData);
        ApiRequest<?> request = buildRequest("req-1", "claude-3");

        ApiResponse<?> response = AiBackendMessageConverter.toOpenAIResponse(backendResponse, AiBackendType.CLAUDE, request);

        assertEquals(expected, response.getData());
    }

    @Test
    void convertsClaudeDeltaToOpenAIChunk() throws IOException {
        Map<String, Object> backendDelta = readJson("fixtures/claude_delta.json");
        Map<String, Object> expected = readJson("fixtures/openai_chunk_from_claude_expected.json");
        ApiRequest<?> request = buildRequest("req-2", "claude-3");

        Map<String, Object> chunk = AiBackendMessageConverter.toOpenAIStreamChunk(backendDelta, AiBackendType.CLAUDE, request);

        assertEquals(expected, chunk);
    }

    @Test
    void convertsClaudeDeltaWithToolCalls() throws IOException {
        Map<String, Object> backendDelta = readJson("fixtures/claude_delta_with_tool_calls.json");
        Map<String, Object> expected = readJson("fixtures/openai_chunk_from_claude_with_tool_calls_expected.json");
        ApiRequest<?> request = buildRequest("req-5", "claude-3");

        Map<String, Object> chunk = AiBackendMessageConverter.toOpenAIStreamChunk(backendDelta, AiBackendType.CLAUDE, request);

        assertEquals(expected, chunk);
    }

    @Test
    void convertsOllamaDoneDelta() throws IOException {
        Map<String, Object> backendDelta = readJson("fixtures/ollama_delta.json");
        Map<String, Object> expected = readJson("fixtures/openai_chunk_from_ollama_expected.json");
        ApiRequest<?> request = buildRequest("req-3", "llama3");

        Map<String, Object> chunk = AiBackendMessageConverter.toOpenAIStreamChunk(backendDelta, AiBackendType.OLLAMA, request);

        assertEquals(expected, chunk);
    }

    @Test
    void convertsOllamaResponseWithMessageExtras() throws IOException {
        Map<String, Object> backendData = readJson("fixtures/ollama_backend_response_with_message.json");
        Map<String, Object> expected = readJson("fixtures/openai_response_from_ollama_expected.json");
        ApiResponse<?> backendResponse = ApiResponse.success(backendData);
        ApiRequest<?> request = buildRequest("req-4", "llama3");

        ApiResponse<?> response = AiBackendMessageConverter.toOpenAIResponse(backendResponse, AiBackendType.OLLAMA, request);

        assertEquals(expected, response.getData());
    }

    @Test
    void convertsOllamaResponseWithLogprobs() throws IOException {
        Map<String, Object> backendData = readJson("fixtures/ollama_backend_response_with_logprobs.json");
        Map<String, Object> expected = readJson("fixtures/openai_response_from_ollama_with_logprobs_expected.json");
        ApiResponse<?> backendResponse = ApiResponse.success(backendData);
        ApiRequest<?> request = buildRequest("req-6", "llama3");

        ApiResponse<?> response = AiBackendMessageConverter.toOpenAIResponse(backendResponse, AiBackendType.OLLAMA, request);

        assertEquals(expected, response.getData());
    }

    @Test
    void convertsOpenAIBackendResponseWithContentBlocks() throws IOException {
        Map<String, Object> backendData = readJson("fixtures/openai_backend_response_block_content.json");
        Map<String, Object> expected = readJson("fixtures/openai_response_from_openai_block_content_expected.json");
        ApiResponse<?> backendResponse = ApiResponse.success(backendData);
        ApiRequest<?> request = buildRequest("req-7", "gpt-4o");

        ApiResponse<?> response = AiBackendMessageConverter.toOpenAIResponse(backendResponse, AiBackendType.OPENAI, request);

        assertEquals(expected, response.getData());
    }

    private static ApiRequest<?> buildRequest(String requestId, String model) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", requestId);
        data.put("model", model);
        return ApiRequest.build(data);
    }

    private static Map<String, Object> readJson(String resource) throws IOException {
        return (Map<String, Object>) JsonTool.parseBeanFromResource(new ClassPathResource("classpath:" + resource));
    }
}
