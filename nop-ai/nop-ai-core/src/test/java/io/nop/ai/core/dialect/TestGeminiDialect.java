package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.api.chat.stream.StreamItemPhase;
import io.nop.ai.api.chat.stream.StreamItemType;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGeminiDialect extends JunitBaseTestCase {

    private LlmModel newConfig() {
        LlmModel config = new LlmModel();
        config.setApiStyle(ApiStyle.gemini);
        return config;
    }

    @Test
    public void testBuildBodyStructure() {
        GeminiDialect dialect = new GeminiDialect();
        ChatRequest request = new ChatRequest();
        request.addMessage(new ChatUserMessage("Hello"));
        ChatOptions options = new ChatOptions();
        options.setTemperature(0.7f);
        request.setOptions(options);

        Map<String, Object> body = dialect.buildBody(request, newConfig(), null, "gemini-1.5-pro", false);

        assertNotNull(body.get("contents"));
        assertNotNull(body.get("generationConfig"));
    }

    @Test
    public void testParseResponseWithThinking() {
        GeminiDialect dialect = new GeminiDialect();
        String responseJson = "{\"candidates\":[{\"content\":{\"parts\":[" +
                "{\"text\":\"Let me think\",\"thought\":true}," +
                "{\"text\":\"Final answer\"}]},\"finishReason\":\"STOP\"}]," +
                "\"model\":\"gemini-1.5-pro\"}";

        ChatResponse response = dialect.parseResponse(responseJson, newConfig());

        assertEquals("Final answer", response.outputText());
        assertEquals("gemini-1.5-pro", response.getModel());

        // Plan 329：推理由独立 ChatReasoningMessage 承载
        assertNotNull(response.getMessages().stream()
                .filter(m -> m instanceof ChatReasoningMessage)
                .findFirst().orElse(null));

        // Plan 329：thought:true parts 产出 ChatReasoningMessage，其余 text parts 产出 assistant 文本。
        assertNotNull(response.getMessages());
        assertEquals(2, response.getMessages().size());
        assertTrue(response.getMessages().get(0) instanceof ChatReasoningMessage,
                "thought:true parts must produce ChatReasoningMessage first");
        assertEquals("Let me think", response.getMessages().get(0).getContent());
        assertTrue(response.getMessages().get(1) instanceof ChatAssistantMessage);
        assertEquals("Final answer", response.getMessages().get(1).getContent());
    }

    @Test
    public void testParseResponseNoCandidates() {
        GeminiDialect dialect = new GeminiDialect();
        String responseJson = "{\"model\":\"gemini-1.5-pro\"}";

        ChatResponse response = dialect.parseResponse(responseJson, newConfig());

        assertNull(response.outputText());
    }

    @Test
    public void testParseResponseEmptyReturnsError() {
        GeminiDialect dialect = new GeminiDialect();
        ChatResponse response = dialect.parseResponse("", newConfig());
        assertFalse(response.isSuccess());
        assertEquals("NULL_RESPONSE", response.getErrorCode());
    }

    @Test
    public void testParseResponseMalformedJsonFails() {
        GeminiDialect dialect = new GeminiDialect();
        assertThrows(NopException.class, () -> dialect.parseResponse("{bad json", newConfig()));
    }

    @Test
    public void testParseStreamChunk() {
        GeminiDialect dialect = new GeminiDialect();
        String data = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hi\"}]}}]}";

        ChatStreamChunk chunk = dialect.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals(StreamItemType.text, chunk.getItemType());
        assertEquals(StreamItemPhase.DELTA, chunk.getPhase());
        assertEquals("Hi", chunk.getDelta());
    }

    @Test
    public void testParseStreamChunkThought() {
        GeminiDialect dialect = new GeminiDialect();
        String data = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hmm\",\"thought\":true}]}}]}";

        ChatStreamChunk chunk = dialect.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals(StreamItemType.reasoning, chunk.getItemType());
        assertEquals("hmm", chunk.getDelta());
    }

    @Test
    public void testParseStreamChunkMalformedJsonFails() {
        GeminiDialect dialect = new GeminiDialect();
        assertThrows(NopException.class, () -> dialect.parseStreamChunk("{bad json"));
    }
}
