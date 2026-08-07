package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.StreamItemPhase;
import io.nop.ai.api.chat.stream.StreamItemType;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI 方言测试
 */
public class TestOpenAiDialect extends JunitBaseTestCase {

    @Test
    public void testSystemMessageIncluded() {
        OpenAiDialect dialect = new OpenAiDialect();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("You are a helpful assistant."));
        messages.add(new ChatUserMessage("Hello!"));

        ChatRequest request = new ChatRequest();
        request.setMessages(messages);
        request.setOptions(new ChatOptions());

        LlmModel config = new LlmModel();
        config.setApiStyle(ApiStyle.openai);

        Map<String, Object> body = dialect.buildBody(request, config, null, "gpt-4", false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messageList = (List<Map<String, Object>>) body.get("messages");

        // 验证 system 消息被包含在 messages 中
        assertNotNull(messageList);
        assertEquals(2, messageList.size(), "Should have 2 messages (system + user)");

        Map<String, Object> firstMsg = messageList.get(0);
        assertEquals("system", firstMsg.get("role"), "First message should be system");
        assertEquals("You are a helpful assistant.", firstMsg.get("content"));

        Map<String, Object> secondMsg = messageList.get(1);
        assertEquals("user", secondMsg.get("role"), "Second message should be user");
        assertEquals("Hello!", secondMsg.get("content"));
    }

    @Test
    public void testBuildBodyWithTools() {
        OpenAiDialect dialect = new OpenAiDialect();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatUserMessage("What is the weather?"));

        ChatOptions options = new ChatOptions();
        options.setTemperature(0.7f);
        options.setMaxTokens(1000);

        ChatRequest request = new ChatRequest();
        request.setMessages(messages);
        request.setOptions(options);

        LlmModel config = new LlmModel();
        config.setApiStyle(ApiStyle.openai);

        Map<String, Object> body = dialect.buildBody(request, config, null, "gpt-4", true);

        assertEquals("gpt-4", body.get("model"));
        assertEquals(true, body.get("stream"));
        assertEquals(0.7, ((Float) body.get("temperature")).doubleValue(), 0.001);
        assertEquals(1000, body.get("max_tokens"));
        assertNotNull(body.get("messages"));
    }

    @Test
    public void testParseResponse() {
        OpenAiDialect dialect = new OpenAiDialect();

        String responseJson = "{\"id\":\"chatcmpl-123\",\"model\":\"gpt-4\"," +
                "\"choices\":[{\"message\":{\"content\":\"Hello! How can I help you?\"}," +
                "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":10," +
                "\"completion_tokens\":20,\"total_tokens\":30}}";

        LlmModel config = new LlmModel();
        config.setApiStyle(ApiStyle.openai);

        var response = dialect.parseResponse(responseJson, config);

        assertEquals("chatcmpl-123", response.getId());
        assertEquals("gpt-4", response.getModel());
        assertEquals("Hello! How can I help you?", response.outputText());
        assertEquals("stop", response.getFinishReason());

        assertNotNull(response.getUsage());
        assertEquals(10, response.getUsage().getPromptTokens().intValue());
        assertEquals(20, response.getUsage().getCompletionTokens().intValue());
        assertEquals(30, response.getUsage().getTotalTokens().intValue());

        // Plan 329：单一拆分模型——messages 序列就绪（无 reasoning 时仅含 assistant 文本）。
        assertNotNull(response.getMessages(), "parseResponse must populate messages");
        assertEquals(1, response.getMessages().size());
        assertTrue(response.getMessages().get(0) instanceof ChatAssistantMessage,
                "messages must contain the assistant text message");
        assertEquals("Hello! How can I help you?", response.getMessages().get(0).getContent());
    }

    @Test
    public void testParseResponseProducesMessagesWithReasoning() {
        OpenAiDialect dialect = new OpenAiDialect();

        String responseJson = "{\"id\":\"chatcmpl-1\",\"model\":\"deepseek-r1\"," +
                "\"choices\":[{\"message\":{\"content\":\"answer\",\"reasoning_content\":\"let me think\"}," +
                "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2}}";

        LlmModel config = new LlmModel();
        config.setApiStyle(ApiStyle.openai);

        ChatResponse response = dialect.parseResponse(responseJson, config);

        // Plan 329：assistant 文本与推理由独立消息承载
        assertEquals("answer", response.outputText());

        ChatReasoningMessage reasoningMsg = response.getMessages().stream()
                .filter(m -> m instanceof ChatReasoningMessage)
                .map(m -> (ChatReasoningMessage) m)
                .findFirst().orElse(null);
        assertNotNull(reasoningMsg, "reasoning_content must produce a ChatReasoningMessage");
        assertEquals("let me think", reasoningMsg.getSummary());

        // 新 messages：reasoning → assistant text
        assertNotNull(response.getMessages());
        assertEquals(2, response.getMessages().size());
        assertTrue(response.getMessages().get(0) instanceof ChatReasoningMessage);
        assertEquals("let me think", response.getMessages().get(0).getContent());
        assertTrue(response.getMessages().get(1) instanceof ChatAssistantMessage);
        assertEquals("answer", response.getMessages().get(1).getContent());
    }

    @Test
    public void testParseStreamChunk() {
        OpenAiDialect dialect = new OpenAiDialect();

        String chunkJson = "{\"id\":\"chatcmpl-123\",\"choices\":[{\"delta\":" +
                "{\"content\":\"Hello\"},\"finish_reason\":null}]}";

        var chunk = dialect.parseStreamChunk(chunkJson);

        assertNotNull(chunk);
        assertEquals("chatcmpl-123", chunk.getId());
        assertEquals(StreamItemType.text, chunk.getItemType());
        assertEquals("Hello", chunk.getDelta());
        assertNull(chunk.getFinishReason());
    }

    @Test
    public void testParseStreamChunkWithThinking() {
        OpenAiDialect dialect = new OpenAiDialect();

        String chunkJson = "{\"id\":\"chatcmpl-123\",\"choices\":[{\"delta\":" +
                "{\"reasoning_content\":\"Let me think about this...\",\"content\":\"\"}," +
                "\"finish_reason\":null}]}";

        var chunk = dialect.parseStreamChunk(chunkJson);

        assertNotNull(chunk);
        assertEquals(StreamItemType.reasoning, chunk.getItemType());
        assertEquals("Let me think about this...", chunk.getDelta());
    }

    @Test
    public void testParseStreamChunkToolCallsAdded() {
        // Plan 328 Phase 2：补齐 OpenAI 流式 tool_calls 解析缺口。
        // 首个 delta（per index）：index + id + function.name → ADDED
        OpenAiDialect dialect = new OpenAiDialect();

        String chunkJson = "{\"id\":\"chatcmpl-1\",\"choices\":[{\"delta\":{" +
                "\"tool_calls\":[{\"index\":0,\"id\":\"call_abc\",\"type\":\"function\"," +
                "\"function\":{\"name\":\"get_weather\",\"arguments\":\"\"}}]}," +
                "\"finish_reason\":null}]}";

        var chunk = dialect.parseStreamChunk(chunkJson);

        assertNotNull(chunk);
        assertEquals(StreamItemType.tool_call, chunk.getItemType());
        assertEquals(0, chunk.getItemIndex());
        assertEquals("call_abc", chunk.getCallId());
        assertEquals(StreamItemPhase.ADDED, chunk.getPhase());
        assertEquals("get_weather", chunk.getDelta(), "ADDED delta carries the function name");
    }

    @Test
    public void testParseStreamChunkToolCallsDelta() {
        // 后续 delta（per index）：仅 arguments 片段 → DELTA
        OpenAiDialect dialect = new OpenAiDialect();

        String chunkJson = "{\"id\":\"chatcmpl-1\",\"choices\":[{\"delta\":{" +
                "\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"loc\"}}]}," +
                "\"finish_reason\":null}]}";

        var chunk = dialect.parseStreamChunk(chunkJson);

        assertNotNull(chunk);
        assertEquals(StreamItemType.tool_call, chunk.getItemType());
        assertEquals(0, chunk.getItemIndex());
        assertEquals(StreamItemPhase.DELTA, chunk.getPhase());
        assertEquals("{\"loc", chunk.getDelta(), "DELTA delta carries the arguments fragment");
        assertNull(chunk.getCallId(), "subsequent deltas carry no id");
    }

    @Test
    public void testParseStreamChunkDone() {
        OpenAiDialect dialect = new OpenAiDialect();

        assertNull(dialect.parseStreamChunk(null));
        assertNull(dialect.parseStreamChunk(""));
        assertNull(dialect.parseStreamChunk("[DONE]"));
    }

    @Test
    public void testParseResponseEmptyReturnsError() {
        OpenAiDialect dialect = new OpenAiDialect();
        LlmModel config = new LlmModel();
        config.setApiStyle(ApiStyle.openai);

        ChatResponse response = dialect.parseResponse("", config);

        assertFalse(response.isSuccess());
        assertEquals("NULL_RESPONSE", response.getErrorCode());
        assertEquals("Empty response body", response.getError());
    }

    @Test
    public void testParseResponseMalformedJsonFails() {
        OpenAiDialect dialect = new OpenAiDialect();
        LlmModel config = new LlmModel();
        config.setApiStyle(ApiStyle.openai);

        assertThrows(io.nop.api.core.exceptions.NopException.class,
                () -> dialect.parseResponse("{bad json", config));
    }

    @Test
    public void testParseStreamChunkMalformedJsonFails() {
        OpenAiDialect dialect = new OpenAiDialect();

        assertThrows(io.nop.api.core.exceptions.NopException.class,
                () -> dialect.parseStreamChunk("{bad json"));
    }

    @Test
    public void testParseResponseErrorBodyYieldsNoContent() {
        OpenAiDialect dialect = new OpenAiDialect();
        LlmModel config = new LlmModel();
        config.setApiStyle(ApiStyle.openai);

        String responseJson = "{\"error\":{\"message\":\"Invalid API key\",\"code\":\"invalid_api_key\"}}";
        ChatResponse response = dialect.parseResponse(responseJson, config);

        // Plan 329：错误响应无 assistant 文本（outputText 为 null）
        assertNull(response.outputText(), "error responses should not contain assistant content");
    }
}
