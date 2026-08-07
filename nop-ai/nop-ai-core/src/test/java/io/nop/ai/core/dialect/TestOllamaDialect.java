package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.api.chat.stream.StreamItemPhase;
import io.nop.ai.api.chat.stream.StreamItemType;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOllamaDialect extends JunitBaseTestCase {

    private LlmModel newConfig() {
        LlmModel config = new LlmModel();
        config.setApiStyle(ApiStyle.ollama);
        return config;
    }

    @Test
    public void testBuildBodyStructure() {
        OllamaDialect dialect = new OllamaDialect();
        ChatRequest request = new ChatRequest();
        request.addMessage(new ChatUserMessage("Hello"));
        ChatOptions options = new ChatOptions();
        options.setTemperature(0.5f);
        options.setMaxTokens(200);
        request.setOptions(options);

        Map<String, Object> body = dialect.buildBody(request, newConfig(), null, "qwen3", false);

        assertEquals("qwen3", body.get("model"));
        assertEquals(false, body.get("stream"));
        List<?> messages = (List<?>) body.get("messages");
        assertEquals(1, messages.size());
        Map<String, Object> optionsMap = (Map<String, Object>) body.get("options");
        assertNotNull(optionsMap);
        assertEquals(200, optionsMap.get("num_predict"));
        assertEquals(0.5f, ((Number) optionsMap.get("temperature")).floatValue(), 0.001);
    }

    @Test
    public void testParseResponse() {
        OllamaDialect dialect = new OllamaDialect();
        String responseJson = "{\"model\":\"llama2\",\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"}," +
                "\"done_reason\":\"stop\",\"prompt_eval_count\":10,\"eval_count\":20}";

        ChatResponse response = dialect.parseResponse(responseJson, newConfig());

        assertNotNull(response.getMessage());
        assertEquals("Hello", response.getMessage().getContent());
        assertEquals("stop", response.getFinishReason());
        assertEquals("llama2", response.getModel());
        assertNotNull(response.getUsage());
        assertEquals(10, response.getUsage().getPromptTokens().intValue());
        assertEquals(20, response.getUsage().getCompletionTokens().intValue());
    }

    @Test
    public void testParseResponseThinking() {
        OllamaDialect dialect = new OllamaDialect();
        String responseJson = "{\"message\":{\"role\":\"assistant\",\"content\":\"answer\",\"thinking\":\"hmm\"}}";

        ChatResponse response = dialect.parseResponse(responseJson, newConfig());

        assertEquals("answer", response.getMessage().getContent());
        assertEquals("hmm", response.getMessage().getThink());

        // Plan 326 双轨：thinking 产出 ChatReasoningMessage → assistant text。
        assertNotNull(response.getMessages());
        assertEquals(2, response.getMessages().size());
        assertTrue(response.getMessages().get(0) instanceof ChatReasoningMessage);
        assertEquals("hmm", response.getMessages().get(0).getContent());
        assertTrue(response.getMessages().get(1) instanceof ChatAssistantMessage);
        assertEquals("answer", response.getMessages().get(1).getContent());
    }

    @Test
    public void testParseResponseToolCallsProducesMessages() {
        OllamaDialect dialect = new OllamaDialect();
        String responseJson = "{\"model\":\"qwen3\"," +
                "\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[" +
                "{\"id\":\"call_42\",\"type\":\"function\",\"function\":{" +
                "\"name\":\"get_weather\",\"arguments\":{\"location\":\"beijing\"}}}]}," +
                "\"done_reason\":\"stop\"}";

        ChatResponse response = dialect.parseResponse(responseJson, newConfig());

        // 旧行为：旧 message 携带 toolCalls
        assertNotNull(response.getMessage().getToolCalls());
        assertEquals("call_42", response.getMessage().getToolCalls().get(0).getId());

        // Plan 326 双轨 + Anti-Hollow：messages 含 ChatToolCallMessage 且 callId 与旧 toolCalls 的 id 一致。
        assertNotNull(response.getMessages());
        ChatToolCallMessage msgToolCall = response.getMessages().stream()
                .filter(m -> m instanceof ChatToolCallMessage)
                .map(m -> (ChatToolCallMessage) m)
                .findFirst().orElse(null);
        assertNotNull(msgToolCall, "messages must contain a ChatToolCallMessage for each tool_call");
        assertEquals("call_42", msgToolCall.getCallId(),
                "ChatToolCallMessage.callId must match the legacy toolCalls id (consistency risk #1)");
        assertEquals("get_weather", msgToolCall.getName());
        assertEquals("beijing", msgToolCall.getArguments().get("location"));
    }

    @Test
    public void testParseResponseEmptyReturnsError() {
        OllamaDialect dialect = new OllamaDialect();
        ChatResponse response = dialect.parseResponse("", newConfig());
        assertFalse(response.isSuccess());
        assertEquals("NULL_RESPONSE", response.getErrorCode());
    }

    @Test
    public void testParseResponseMalformedJsonFails() {
        OllamaDialect dialect = new OllamaDialect();
        assertThrows(NopException.class, () -> dialect.parseResponse("{bad json", newConfig()));
    }

    @Test
    public void testParseStreamChunk() {
        OllamaDialect dialect = new OllamaDialect();
        String data = "{\"model\":\"llama2\",\"message\":{\"content\":\"Hi\"},\"done_reason\":null}";

        ChatStreamChunk chunk = dialect.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals(StreamItemType.text, chunk.getItemType());
        assertEquals(StreamItemPhase.DELTA, chunk.getPhase());
        assertEquals("Hi", chunk.getDelta());
        assertEquals("llama2", chunk.getModel());
    }

    @Test
    public void testParseStreamChunkThinking() {
        OllamaDialect dialect = new OllamaDialect();
        String data = "{\"model\":\"llama2\",\"message\":{\"thinking\":\"hmm\"}}";

        ChatStreamChunk chunk = dialect.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals(StreamItemType.reasoning, chunk.getItemType());
        assertEquals("hmm", chunk.getDelta());
    }

    @Test
    public void testParseStreamChunkMalformedJsonFails() {
        OllamaDialect dialect = new OllamaDialect();
        assertThrows(NopException.class, () -> dialect.parseStreamChunk("{bad json"));
    }

    @Test
    public void testParseStreamChunkDoneReturnsNull() {
        OllamaDialect dialect = new OllamaDialect();
        assertNull(dialect.parseStreamChunk(null));
        assertNull(dialect.parseStreamChunk(""));
        assertNull(dialect.parseStreamChunk("[DONE]"));
    }
}
