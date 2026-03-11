package io.nop.gateway.conversion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nop.gateway.conversion.ai.AiBackendMessageConverter;
import io.nop.gateway.conversion.ai.AiBackendType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiBackendMessageConverterTest {

    @Test
    void convertsClaudeRequestMessagesWithTools() throws IOException {
        Map<String, Object> request = readJson("fixtures/openai_request_with_tools.json");
        Map<String, Object> expected = readJson("fixtures/claude_backend_request_expected.json");

        Map<String, Object> backendRequest = AiBackendMessageConverter.toBackendRequest(request, AiBackendType.CLAUDE);

        assertEquals(expected, backendRequest);
    }

    @Test
    void convertsClaudeResponseToOpenAI() throws IOException {
        Map<String, Object> backendResponse = readJson("fixtures/claude_backend_response.json");
        Map<String, Object> expected = readJson("fixtures/openai_response_from_claude_expected.json");
        Map<String, Object> request = buildRequest("req-1", "claude-3");

        Map<String, Object> response = AiBackendMessageConverter.toOpenAIResponse(backendResponse, AiBackendType.CLAUDE, request);

        assertEquals(expected, response);
    }

    @Test
    void convertsClaudeDeltaToOpenAIChunk() throws IOException {
        Map<String, Object> backendDelta = readJson("fixtures/claude_delta.json");
        Map<String, Object> expected = readJson("fixtures/openai_chunk_from_claude_expected.json");
        Map<String, Object> request = buildRequest("req-2", "claude-3");

        Map<String, Object> chunk = AiBackendMessageConverter.toOpenAIStreamChunk(backendDelta, AiBackendType.CLAUDE, request);

        assertEquals(expected, chunk);
    }

    @Test
    void convertsClaudeDeltaWithToolCalls() throws IOException {
        Map<String, Object> backendDelta = readJson("fixtures/claude_delta_with_tool_calls.json");
        Map<String, Object> expected = readJson("fixtures/openai_chunk_from_claude_with_tool_calls_expected.json");
        Map<String, Object> request = buildRequest("req-5", "claude-3");

        Map<String, Object> chunk = AiBackendMessageConverter.toOpenAIStreamChunk(backendDelta, AiBackendType.CLAUDE, request);

        assertEquals(expected, chunk);
    }

    @Test
    void convertsOllamaDoneDelta() throws IOException {
        Map<String, Object> backendDelta = readJson("fixtures/ollama_delta.json");
        Map<String, Object> expected = readJson("fixtures/openai_chunk_from_ollama_expected.json");
        Map<String, Object> request = buildRequest("req-3", "llama3");

        Map<String, Object> chunk = AiBackendMessageConverter.toOpenAIStreamChunk(backendDelta, AiBackendType.OLLAMA, request);

        assertEquals(expected, chunk);
    }

    @Test
    void convertsOllamaResponseWithMessageExtras() throws IOException {
        Map<String, Object> backendResponse = readJson("fixtures/ollama_backend_response_with_message.json");
        Map<String, Object> expected = readJson("fixtures/openai_response_from_ollama_expected.json");
        Map<String, Object> request = buildRequest("req-4", "llama3");

        Map<String, Object> response = AiBackendMessageConverter.toOpenAIResponse(backendResponse, AiBackendType.OLLAMA, request);

        assertEquals(expected, response);
    }

    @Test
    void convertsOllamaResponseWithLogprobs() throws IOException {
        Map<String, Object> backendResponse = readJson("fixtures/ollama_backend_response_with_logprobs.json");
        Map<String, Object> expected = readJson("fixtures/openai_response_from_ollama_with_logprobs_expected.json");
        Map<String, Object> request = buildRequest("req-6", "llama3");

        Map<String, Object> response = AiBackendMessageConverter.toOpenAIResponse(backendResponse, AiBackendType.OLLAMA, request);

        assertEquals(expected, response);
    }

    @Test
    void convertsOpenAIBackendResponseWithContentBlocks() throws IOException {
        Map<String, Object> backendResponse = readJson("fixtures/openai_backend_response_block_content.json");
        Map<String, Object> expected = readJson("fixtures/openai_response_from_openai_block_content_expected.json");
        Map<String, Object> request = buildRequest("req-7", "gpt-4o");

        Map<String, Object> response = AiBackendMessageConverter.toOpenAIResponse(backendResponse, AiBackendType.OPENAI, request);

        assertEquals(expected, response);
    }

    private static Map<String, Object> buildRequest(String requestId, String model) {
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("id", requestId);
        request.put("model", model);
        return request;
    }

    private static Map<String, Object> readJson(String resource) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream stream = AiBackendMessageConverterTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Missing resource: " + resource);
            }
            return mapper.readValue(stream, new TypeReference<Map<String, Object>>() {
            });
        }
    }
}
