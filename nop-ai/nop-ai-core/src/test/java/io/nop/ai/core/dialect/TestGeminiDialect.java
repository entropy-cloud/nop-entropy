package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
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

        assertNotNull(response.getMessage());
        assertEquals("Final answer", response.getMessage().getContent());
        assertEquals("Let me think", response.getMessage().getThink());
        assertEquals("gemini-1.5-pro", response.getModel());
    }

    @Test
    public void testParseResponseNoCandidates() {
        GeminiDialect dialect = new GeminiDialect();
        String responseJson = "{\"model\":\"gemini-1.5-pro\"}";

        ChatResponse response = dialect.parseResponse(responseJson, newConfig());

        assertNotNull(response.getMessage());
        assertNull(response.getMessage().getContent());
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
        assertEquals("Hi", chunk.getContent());
    }

    @Test
    public void testParseStreamChunkMalformedJsonFails() {
        GeminiDialect dialect = new GeminiDialect();
        assertThrows(NopException.class, () -> dialect.parseStreamChunk("{bad json"));
    }
}
