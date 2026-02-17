package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        assertEquals("Hello! How can I help you?", response.getMessage().getContent());
        assertEquals("stop", response.getFinishReason());

        assertNotNull(response.getUsage());
        assertEquals(10, response.getUsage().getPromptTokens().intValue());
        assertEquals(20, response.getUsage().getCompletionTokens().intValue());
        assertEquals(30, response.getUsage().getTotalTokens().intValue());
    }

    @Test
    public void testParseStreamChunk() {
        OpenAiDialect dialect = new OpenAiDialect();

        String chunkJson = "{\"id\":\"chatcmpl-123\",\"choices\":[{\"delta\":" +
                "{\"content\":\"Hello\"},\"finish_reason\":null}]}";

        var chunk = dialect.parseStreamChunk(chunkJson);

        assertNotNull(chunk);
        assertEquals("chatcmpl-123", chunk.getId());
        assertEquals("Hello", chunk.getContent());
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
        assertEquals("Let me think about this...", chunk.getThinking());
    }

    @Test
    public void testParseStreamChunkDone() {
        OpenAiDialect dialect = new OpenAiDialect();

        assertNull(dialect.parseStreamChunk(null));
        assertNull(dialect.parseStreamChunk(""));
        assertNull(dialect.parseStreamChunk("[DONE]"));
    }
}
