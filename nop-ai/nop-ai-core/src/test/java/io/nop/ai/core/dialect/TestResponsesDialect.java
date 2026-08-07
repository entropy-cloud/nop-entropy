package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ResponseFormat;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.api.chat.stream.StreamItemPhase;
import io.nop.ai.api.chat.stream.StreamItemType;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ResponsesDialect 单元测试（plan 330）。
 * <p>
 * 覆盖非流式 buildBody/parseResponse/convertMessage round-trip，以及 hosted tools 剥离。
 */
public class TestResponsesDialect extends JunitBaseTestCase {

    private ResponsesDialect dialect = new ResponsesDialect();

    private LlmModel newConfig() {
        LlmModel config = new LlmModel();
        config.setApiStyle(ApiStyle.responses);
        return config;
    }

    // ==================== buildBody ====================

    @Test
    void testBuildBodySystemMessageToInstructions() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("You are a helpful assistant."));
        messages.add(new ChatUserMessage("Hello!"));

        ChatRequest request = new ChatRequest();
        request.setMessages(messages);
        request.setOptions(new ChatOptions());

        Map<String, Object> body = dialect.buildBody(request, newConfig(), null, "gpt-4o", false);

        assertEquals("gpt-4o", body.get("model"));
        assertEquals(false, body.get("stream"));
        assertEquals(false, body.get("store"), "store must be false (stateless, design #10)");
        assertEquals("You are a helpful assistant.", body.get("instructions"),
                "system messages map to top-level instructions");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> input = (List<Map<String, Object>>) body.get("input");
        assertNotNull(input);
        assertEquals(1, input.size(), "only non-system messages go to input[]");

        Map<String, Object> userItem = input.get(0);
        assertEquals("message", userItem.get("type"));
        assertEquals("user", userItem.get("role"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) userItem.get("content");
        assertEquals("input_text", content.get(0).get("type"));
        assertEquals("Hello!", content.get(0).get("text"));
    }

    @Test
    void testBuildBodyWithOptions() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatUserMessage("Hi"));

        ChatOptions options = ChatOptions.builder()
                .temperature(0.7f)
                .topP(0.9f)
                .maxTokens(1000)
                .build();

        ChatRequest request = new ChatRequest(messages, options);

        Map<String, Object> body = dialect.buildBody(request, newConfig(), null, "gpt-4o", true);

        assertEquals(0.7f, body.get("temperature"));
        assertEquals(0.9f, body.get("top_p"));
        assertEquals(1000, body.get("max_output_tokens"), "maxTokens → max_output_tokens");
        assertEquals(true, body.get("stream"));
    }

    @Test
    void testBuildBodyResponseFormat() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatUserMessage("Return JSON"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        ChatOptions options = ChatOptions.builder()
                .responseFormatConfig(new ResponseFormat(ResponseFormat.TYPE_JSON_SCHEMA, schema))
                .build();

        ChatRequest request = new ChatRequest(messages, options);

        Map<String, Object> body = dialect.buildBody(request, newConfig(), null, "gpt-4o", false);

        @SuppressWarnings("unchecked")
        Map<String, Object> text = (Map<String, Object>) body.get("text");
        assertNotNull(text, "responseFormat → text field");
        @SuppressWarnings("unchecked")
        Map<String, Object> format = (Map<String, Object>) text.get("format");
        assertEquals(ResponseFormat.TYPE_JSON_SCHEMA, format.get("type"));
        assertNotNull(format.get("schema"));
    }

    @Test
    void testBuildBodyStripsHostedTools() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatUserMessage("Search the web"));

        ChatToolDefinition functionTool = ChatToolDefinition.of("get_weather", "Get weather");

        ChatToolDefinition webSearch = new ChatToolDefinition();
        webSearch.setType("web_search");
        webSearch.setName("web_search");

        ChatToolDefinition fileSearch = new ChatToolDefinition();
        fileSearch.setType("file_search");
        fileSearch.setName("file_search");

        ChatToolDefinition codeInterpreter = new ChatToolDefinition();
        codeInterpreter.setType("code_interpreter");
        codeInterpreter.setName("code_interpreter");

        ChatOptions options = ChatOptions.builder()
                .addTool(functionTool)
                .addTool(webSearch)
                .addTool(fileSearch)
                .addTool(codeInterpreter)
                .build();

        ChatRequest request = new ChatRequest(messages, options);

        Map<String, Object> body = dialect.buildBody(request, newConfig(), null, "gpt-4o", false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) body.get("tools");
        assertNotNull(tools, "function tools must be present");
        assertEquals(1, tools.size(), "hosted tools (web_search/file_search/code_interpreter) must be stripped");
        assertEquals("function", tools.get(0).get("type"));
        assertEquals("get_weather", tools.get(0).get("name"));
    }

    @Test
    void testBuildBodyToolsUseFlatFormat() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatUserMessage("What's the weather?"));

        ChatOptions options = ChatOptions.builder()
                .addTool("get_weather", "Get weather for a location")
                .build();

        ChatRequest request = new ChatRequest(messages, options);

        Map<String, Object> body = dialect.buildBody(request, newConfig(), null, "gpt-4o", false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) body.get("tools");
        assertNotNull(tools);
        Map<String, Object> tool = tools.get(0);
        assertEquals("function", tool.get("type"));
        assertEquals("get_weather", tool.get("name"), "flat format: name at top level");
        assertEquals("Get weather for a location", tool.get("description"));
        assertNull(tool.get("function"), "must NOT use nested function wrapper like Chat Completions");
    }

    // ==================== convertMessage ====================

    @Test
    void testConvertUserMessage() {
        Map<String, Object> item = dialect.convertMessage(new ChatUserMessage("hello"), null, null);
        assertEquals("message", item.get("type"));
        assertEquals("user", item.get("role"));
        assertTextPart(item, "content", "input_text", "hello");
    }

    @Test
    void testConvertAssistantMessage() {
        Map<String, Object> item = dialect.convertMessage(new ChatAssistantMessage("hi there"), null, null);
        assertEquals("message", item.get("type"));
        assertEquals("assistant", item.get("role"));
        assertTextPart(item, "content", "output_text", "hi there");
    }

    @Test
    void testConvertReasoningMessage() {
        Map<String, Object> item = dialect.convertMessage(new ChatReasoningMessage("thinking..."), null, null);
        assertEquals("reasoning", item.get("type"));
        assertTextPart(item, "summary", "summary_text", "thinking...");
    }

    @Test
    void testConvertToolCallMessage() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("city", "SF");
        ChatToolCallMessage msg = new ChatToolCallMessage("call_1", "get_weather", args);

        Map<String, Object> item = dialect.convertMessage(msg, null, null);
        assertEquals("function_call", item.get("type"));
        assertEquals("call_1", item.get("call_id"));
        assertEquals("get_weather", item.get("name"));
        assertEquals("{\"city\":\"SF\"}", item.get("arguments"), "arguments serialized as JSON string");
    }

    @Test
    void testConvertToolResponseMessageWithStringContent() {
        ChatToolResponseMessage msg = new ChatToolResponseMessage("call_1", "get_weather", "sunny");

        Map<String, Object> item = dialect.convertMessage(msg, null, null);
        assertEquals("function_call_output", item.get("type"));
        assertEquals("call_1", item.get("call_id"));
        assertEquals("sunny", item.get("output"));
    }

    @Test
    void testConvertToolResponseMessageWithStructuredResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("temp", 72);
        ChatToolResponseMessage msg = ChatToolResponseMessage.structured("call_1", "get_weather", "ok", result);

        Map<String, Object> item = dialect.convertMessage(msg, null, null);
        assertEquals("function_call_output", item.get("type"));
        String output = (String) item.get("output");
        assertTrue(output.contains("\"temp\"") && output.contains("72"),
                "structured result JSON-serialized to output string");
    }

    // ==================== parseResponse ====================

    @Test
    void testParseResponsePlainText() {
        String json = "{\"id\":\"resp_1\",\"model\":\"gpt-4o\"," +
                "\"output\":[{\"type\":\"message\",\"role\":\"assistant\"," +
                "\"content\":[{\"type\":\"output_text\",\"text\":\"Hello!\"}]}]," +
                "\"status\":\"completed\"," +
                "\"usage\":{\"input_tokens\":10,\"output_tokens\":20,\"total_tokens\":30}}";

        ChatResponse response = dialect.parseResponse(json, newConfig());

        assertEquals("resp_1", response.getId());
        assertEquals("gpt-4o", response.getModel());
        assertEquals("stop", response.getFinishReason(), "completed → stop");
        assertEquals("Hello!", response.outputText());

        assertNotNull(response.getUsage());
        assertEquals(10, response.getUsage().getPromptTokens());
        assertEquals(20, response.getUsage().getCompletionTokens());
        assertEquals(30, response.getUsage().getTotalTokens());

        assertNotNull(response.getMessages());
        assertEquals(1, response.getMessages().size());
        assertTrue(response.getMessages().get(0) instanceof ChatAssistantMessage);
    }

    @Test
    void testParseResponseWithReasoning() {
        String json = "{\"id\":\"resp_2\",\"model\":\"o1\"," +
                "\"output\":[" +
                "  {\"type\":\"reasoning\",\"summary\":[{\"type\":\"summary_text\",\"text\":\"let me think\"}]}," +
                "  {\"type\":\"message\",\"role\":\"assistant\"," +
                "   \"content\":[{\"type\":\"output_text\",\"text\":\"answer\"}]}" +
                "]," +
                "\"status\":\"completed\"}";

        ChatResponse response = dialect.parseResponse(json, newConfig());

        assertEquals(2, response.getMessages().size());
        assertTrue(response.getMessages().get(0) instanceof ChatReasoningMessage,
                "reasoning item → ChatReasoningMessage");
        assertEquals("let me think", response.getMessages().get(0).getContent());
        assertTrue(response.getMessages().get(1) instanceof ChatAssistantMessage);
        assertEquals("answer", response.getMessages().get(1).getContent());
    }

    @Test
    void testParseResponseWithFunctionCall() {
        String json = "{\"id\":\"resp_3\",\"model\":\"gpt-4o\"," +
                "\"output\":[{\"type\":\"function_call\",\"call_id\":\"call_abc\"," +
                "\"name\":\"get_weather\",\"arguments\":\"{\\\"city\\\":\\\"SF\\\"}\"}]," +
                "\"status\":\"completed\"}";

        ChatResponse response = dialect.parseResponse(json, newConfig());

        assertEquals(1, response.getMessages().size());
        ChatToolCallMessage tcm = (ChatToolCallMessage) response.getMessages().get(0);
        assertEquals("call_abc", tcm.getCallId());
        assertEquals("get_weather", tcm.getName());
        assertEquals("SF", tcm.getArguments().get("city"));
    }

    @Test
    void testParseResponseStatusIncomplete() {
        String json = "{\"id\":\"resp_4\",\"output\":[],\"status\":\"incomplete\"}";
        ChatResponse response = dialect.parseResponse(json, newConfig());
        assertEquals("length", response.getFinishReason(), "incomplete → length");
    }

    @Test
    void testParseResponseEmptyReturnsError() {
        ChatResponse response = dialect.parseResponse("", newConfig());
        assertFalse(response.isSuccess());
        assertEquals("NULL_RESPONSE", response.getErrorCode());
    }

    @Test
    void testParseResponseEmptyOutput() {
        String json = "{\"id\":\"resp_5\",\"status\":\"completed\"}";
        ChatResponse response = dialect.parseResponse(json, newConfig());
        assertNotNull(response.getMessages());
        assertTrue(response.getMessages().isEmpty());
    }

    // ==================== LlmDialectFactory registration ====================

    @Test
    void testFactoryReturnsResponsesDialect() {
        ILlmDialect d = LlmDialectFactory.getDialect(ApiStyle.responses);
        assertNotNull(d);
        assertTrue(d instanceof ResponsesDialect,
                "ApiStyle.responses must route to ResponsesDialect");
        assertEquals("responses", d.getName());
    }

    // ==================== No silent skip (parseRequestBody front-end not supported) ====================

    @Test
    void testParseRequestBodyThrowsUnsupported() {
        assertThrowsUOE(() -> dialect.parseRequestBody(new LinkedHashMap<>()));
    }

    // ==================== parseStreamChunk（流式方向） ====================

    @Test
    void testParseStreamChunkCreated() {
        String data = "{\"type\":\"response.created\"," +
                "\"response\":{\"id\":\"resp_1\",\"model\":\"gpt-4o\"}}";
        ChatStreamChunk chunk = dialect.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals("resp_1", chunk.getId());
        assertEquals("gpt-4o", chunk.getModel());
        assertNull(chunk.getItemType(), "created event carries no item delta");
    }

    @Test
    void testParseStreamChunkOutputTextDelta() {
        String data = "{\"type\":\"response.output_text.delta\"," +
                "\"output_index\":0,\"content_index\":0,\"delta\":\"Hello\"}";
        ChatStreamChunk chunk = dialect.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals(StreamItemType.text, chunk.getItemType());
        assertEquals(0, chunk.getItemIndex());
        assertEquals(StreamItemPhase.DELTA, chunk.getPhase());
        assertEquals("Hello", chunk.getDelta());
    }

    @Test
    void testParseStreamChunkReasoningDelta() {
        String data = "{\"type\":\"response.reasoning_summary_text.delta\"," +
                "\"output_index\":1,\"summary_index\":0,\"delta\":\"thinking\"}";
        ChatStreamChunk chunk = dialect.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals(StreamItemType.reasoning, chunk.getItemType());
        assertEquals(1, chunk.getItemIndex());
        assertEquals(StreamItemPhase.DELTA, chunk.getPhase());
        assertEquals("thinking", chunk.getDelta());
    }

    @Test
    void testParseStreamChunkFunctionCallAdded() {
        String data = "{\"type\":\"response.output_item.added\"," +
                "\"output_index\":2," +
                "\"item\":{\"type\":\"function_call\",\"call_id\":\"call_abc\"," +
                "\"name\":\"get_weather\",\"arguments\":\"\"}}";
        ChatStreamChunk chunk = dialect.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals(StreamItemType.tool_call, chunk.getItemType());
        assertEquals(2, chunk.getItemIndex());
        assertEquals(StreamItemPhase.ADDED, chunk.getPhase());
        assertEquals("call_abc", chunk.getCallId());
        assertEquals("get_weather", chunk.getDelta(), "ADDED delta carries the function name");
    }

    @Test
    void testParseStreamChunkFunctionCallArgsDelta() {
        String data = "{\"type\":\"response.function_call_arguments.delta\"," +
                "\"output_index\":2,\"item_id\":\"fc_1\",\"delta\":\"{\\\"city\\\"\"}";
        ChatStreamChunk chunk = dialect.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals(StreamItemType.tool_call, chunk.getItemType());
        assertEquals(2, chunk.getItemIndex());
        assertEquals(StreamItemPhase.DELTA, chunk.getPhase());
        assertEquals("{\"city\"", chunk.getDelta(), "DELTA delta carries arguments fragment");
    }

    @Test
    void testParseStreamChunkMessageItemAdded() {
        String data = "{\"type\":\"response.output_item.added\"," +
                "\"output_index\":0," +
                "\"item\":{\"type\":\"message\",\"role\":\"assistant\",\"content\":[]}}";
        ChatStreamChunk chunk = dialect.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals(StreamItemType.text, chunk.getItemType());
        assertEquals(StreamItemPhase.ADDED, chunk.getPhase());
    }

    @Test
    void testParseStreamChunkCompleted() {
        String data = "{\"type\":\"response.completed\"," +
                "\"response\":{\"id\":\"resp_1\",\"model\":\"gpt-4o\",\"status\":\"completed\"," +
                "\"usage\":{\"input_tokens\":10,\"output_tokens\":20,\"total_tokens\":30}}}";
        ChatStreamChunk chunk = dialect.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals(StreamItemPhase.DONE, chunk.getPhase());
        assertEquals("stop", chunk.getFinishReason(), "completed → stop");
        assertEquals("resp_1", chunk.getId());
        assertNotNull(chunk.getUsage());
        assertEquals(10, chunk.getUsage().getPromptTokens());
        assertEquals(20, chunk.getUsage().getCompletionTokens());
        assertEquals(30, chunk.getUsage().getTotalTokens());
    }

    @Test
    void testParseStreamChunkDone() {
        assertNull(dialect.parseStreamChunk(null));
        assertNull(dialect.parseStreamChunk(""));
        assertNull(dialect.parseStreamChunk("[DONE]"));
    }

    @Test
    void testParseStreamChunkUnknownEventReturnsNull() {
        String data = "{\"type\":\"response.content_part.added\",\"output_index\":0}";
        assertNull(dialect.parseStreamChunk(data),
                "unhandled event types return null (no spurious chunk)");
    }

    /**
     * 完整事件序列端到端（单元级）：
     * created → output_item.added(message) → output_text.delta×2 →
     * output_item.added(function_call) → function_call_arguments.delta×2 → completed。
     * 断言每个事件产出的 item chunk 三段式（ADDED/DELTA/DONE）。
     */
    @Test
    void testParseStreamChunkFullSequence() {
        // 1. created
        ChatStreamChunk c1 = dialect.parseStreamChunk(
                "{\"type\":\"response.created\",\"response\":{\"id\":\"resp_seq\",\"model\":\"gpt-4o\"}}");
        assertNotNull(c1);
        assertEquals("resp_seq", c1.getId());

        // 2. message item added → text ADDED
        ChatStreamChunk c2 = dialect.parseStreamChunk(
                "{\"type\":\"response.output_item.added\",\"output_index\":0," +
                        "\"item\":{\"type\":\"message\",\"role\":\"assistant\",\"content\":[]}}");
        assertEquals(StreamItemType.text, c2.getItemType());
        assertEquals(0, c2.getItemIndex());
        assertEquals(StreamItemPhase.ADDED, c2.getPhase());

        // 3. text delta
        ChatStreamChunk c3 = dialect.parseStreamChunk(
                "{\"type\":\"response.output_text.delta\",\"output_index\":0,\"delta\":\"Hel\"}");
        assertEquals(StreamItemType.text, c3.getItemType());
        assertEquals(StreamItemPhase.DELTA, c3.getPhase());
        assertEquals("Hel", c3.getDelta());

        // 4. text delta (continued)
        ChatStreamChunk c4 = dialect.parseStreamChunk(
                "{\"type\":\"response.output_text.delta\",\"output_index\":0,\"delta\":\"lo\"}");
        assertEquals("lo", c4.getDelta());

        // 5. function_call item added → tool_call ADDED
        ChatStreamChunk c5 = dialect.parseStreamChunk(
                "{\"type\":\"response.output_item.added\",\"output_index\":1," +
                        "\"item\":{\"type\":\"function_call\",\"call_id\":\"call_1\"," +
                        "\"name\":\"get_weather\",\"arguments\":\"\"}}");
        assertEquals(StreamItemType.tool_call, c5.getItemType());
        assertEquals(1, c5.getItemIndex());
        assertEquals(StreamItemPhase.ADDED, c5.getPhase());
        assertEquals("call_1", c5.getCallId());
        assertEquals("get_weather", c5.getDelta());

        // 6. function_call args delta
        ChatStreamChunk c6 = dialect.parseStreamChunk(
                "{\"type\":\"response.function_call_arguments.delta\",\"output_index\":1," +
                        "\"item_id\":\"fc_1\",\"delta\":\"{\\\"city\\\":\\\"SF\\\"}\"}");
        assertEquals(StreamItemType.tool_call, c6.getItemType());
        assertEquals(StreamItemPhase.DELTA, c6.getPhase());
        assertEquals("{\"city\":\"SF\"}", c6.getDelta());

        // 7. completed → DONE
        ChatStreamChunk c7 = dialect.parseStreamChunk(
                "{\"type\":\"response.completed\"," +
                        "\"response\":{\"id\":\"resp_seq\",\"model\":\"gpt-4o\",\"status\":\"completed\"}}");
        assertEquals(StreamItemPhase.DONE, c7.getPhase());
        assertEquals("stop", c7.getFinishReason());
    }

    // ==================== helpers ====================

    @SuppressWarnings("unchecked")
    private void assertTextPart(Map<String, Object> item, String fieldName, String expectedType, String expectedText) {
        Object partsObj = item.get(fieldName);
        assertNotNull(partsObj, fieldName + " must be present");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) partsObj;
        assertEquals(1, parts.size());
        assertEquals(expectedType, parts.get(0).get("type"));
        assertEquals(expectedText, parts.get(0).get("text"));
    }

    private static void assertThrowsUOE(Runnable r) {
        try {
            r.run();
        } catch (UnsupportedOperationException e) {
            return;
        }
        throw new AssertionError("Expected UnsupportedOperationException");
    }
}
