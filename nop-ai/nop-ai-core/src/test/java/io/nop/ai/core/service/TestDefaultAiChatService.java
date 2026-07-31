package io.nop.ai.core.service;

import io.nop.ai.core.api.messages.ToolCall;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmResponseModel;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestDefaultAiChatService extends JunitBaseTestCase {

    private LlmModel newLlmModel() {
        LlmModel llmModel = new LlmModel();
        LlmResponseModel response = new LlmResponseModel();
        response.setToolCallsPath("toolCalls");
        llmModel.setResponse(response);
        return llmModel;
    }

    @Test
    public void testParseToolCallsValid() {
        DefaultAiChatService service = new DefaultAiChatService();

        Map<String, Object> fn = new HashMap<>();
        fn.put("name", "getWeather");
        fn.put("arguments", Map.of("location", "beijing"));
        Map<String, Object> call = new HashMap<>();
        call.put("index", 0);
        call.put("id", "call_1");
        call.put("function", fn);
        Map<String, Object> result = new HashMap<>();
        result.put("toolCalls", List.of(call));

        List<ToolCall> toolCalls = service.parseToolCalls(newLlmModel(), result);

        assertNotNull(toolCalls);
        assertEquals(1, toolCalls.size());
        assertEquals("getWeather", toolCalls.get(0).getName());
        assertEquals("beijing", toolCalls.get(0).getArguments().get("location"));
    }

    @Test
    public void testParseToolCallsStringArguments() {
        DefaultAiChatService service = new DefaultAiChatService();

        Map<String, Object> fn = new HashMap<>();
        fn.put("name", "getWeather");
        fn.put("arguments", "{\"location\":\"beijing\"}");
        Map<String, Object> call = new HashMap<>();
        call.put("index", 0);
        call.put("id", "call_1");
        call.put("function", fn);
        Map<String, Object> result = new HashMap<>();
        result.put("toolCalls", List.of(call));

        List<ToolCall> toolCalls = service.parseToolCalls(newLlmModel(), result);

        assertNotNull(toolCalls);
        assertEquals("beijing", toolCalls.get(0).getArguments().get("location"));
    }

    @Test
    public void testParseToolCallsInvalidStructureFails() {
        DefaultAiChatService service = new DefaultAiChatService();

        Map<String, Object> result = new HashMap<>();
        result.put("toolCalls", Map.of("unexpected", "structure"));

        assertThrows(NopException.class, () -> service.parseToolCalls(newLlmModel(), result));
    }

    @Test
    public void testParseToolCallsNullReturnsNull() {
        DefaultAiChatService service = new DefaultAiChatService();

        Map<String, Object> result = new HashMap<>();
        result.put("toolCalls", null);

        assertNull(service.parseToolCalls(newLlmModel(), result));
    }

    @Test
    public void testParseToolCallsEmptyReturnsNull() {
        DefaultAiChatService service = new DefaultAiChatService();

        Map<String, Object> result = new HashMap<>();
        result.put("toolCalls", List.of());

        assertNull(service.parseToolCalls(newLlmModel(), result));
    }
}
