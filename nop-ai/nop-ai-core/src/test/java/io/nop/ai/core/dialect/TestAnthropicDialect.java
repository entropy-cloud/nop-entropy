package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Anthropic (Claude) 方言测试
 */
public class TestAnthropicDialect extends JunitBaseTestCase {

    @Test
    public void testBuildBodyWithSystemMessage() {
        AnthropicDialect dialect = new AnthropicDialect();
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("You are a helpful assistant."));
        messages.add(new ChatUserMessage("Hello!"));
        
        ChatRequest request = new ChatRequest();
        request.setMessages(messages);
        request.setOptions(new ChatOptions());
        
        LlmModel config = new LlmModel();
        config.setApiStyle(ApiStyle.anthropic);
        
        Map<String, Object> body = dialect.buildBody(request, config, null, "claude-3-5-sonnet-20241022", false);
        
        // system 消息应该在单独的字段中
        assertEquals("You are a helpful assistant.", body.get("system"));
        
        // messages 中不应该包含 system 消息
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messageList = (List<Map<String, Object>>) body.get("messages");
        assertNotNull(messageList);
        assertEquals(1, messageList.size(), "Messages should only contain user message, not system");
        
        assertEquals("user", messageList.get(0).get("role"));
    }

    @Test
    public void testParseResponseWithThinking() {
        AnthropicDialect dialect = new AnthropicDialect();
        
        String responseJson = "{\"id\":\"msg_123\",\"model\":\"claude-3-5-sonnet-20241022\"," +
            "\"content\":[{\"type\":\"thinking\",\"thinking\":\"Let me analyze this question...\"}," +
            "{\"type\":\"text\",\"text\":\"Hello! How can I help you?\"}]," +
            "\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":10,\"output_tokens\":20}}";
        
        LlmModel config = new LlmModel();
        config.setApiStyle(ApiStyle.anthropic);
        
        ChatResponse response = dialect.parseResponse(responseJson, config);
        
        assertEquals("msg_123", response.getId());
        assertEquals("claude-3-5-sonnet-20241022", response.getModel());
        assertEquals("Hello! How can I help you?", response.getMessage().getContent());
        assertEquals("Let me analyze this question...", response.getMessage().getThink());
        assertEquals("stop", response.getFinishReason());
    }

    @Test
    public void testParseResponseWithPromptCaching() {
        AnthropicDialect dialect = new AnthropicDialect();
        
        String responseJson = "{\"id\":\"msg_123\",\"model\":\"claude-3-5-sonnet-20241022\"," +
            "\"content\":[{\"type\":\"text\",\"text\":\"Response text\"}]," +
            "\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":100,\"output_tokens\":50," +
            "\"cache_creation_input_tokens\":500,\"cache_read_input_tokens\":200}}";
        
        LlmModel config = new LlmModel();
        config.setApiStyle(ApiStyle.anthropic);
        
        ChatResponse response = dialect.parseResponse(responseJson, config);
        
        ChatUsage usage = response.getUsage();
        assertNotNull(usage);
        assertEquals(100, usage.getPromptTokens().intValue());
        assertEquals(50, usage.getCompletionTokens().intValue());
        
        // 验证 Prompt Caching 字段
        assertEquals(200, usage.getCacheHitTokens().intValue(), "cache_read_input_tokens should map to cacheHitTokens");
        assertEquals(500, usage.getCacheCreationTokens().intValue(), "cache_creation_input_tokens should map to cacheCreationTokens");
    }

    @Test
    public void testParseStreamChunkWithThinking() {
        AnthropicDialect dialect = new AnthropicDialect();
        
        // 思考内容增量
        String thinkingDeltaJson = "{\"type\":\"content_block_delta\",\"index\":0," +
            "\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"Let me think...\"}}";
        
        ChatStreamChunk deltaChunk = dialect.parseStreamChunk(thinkingDeltaJson);
        assertNotNull(deltaChunk);
        assertEquals("Let me think...", deltaChunk.getThinking());
    }

    @Test
    public void testParseStreamChunkMessageDelta() {
        AnthropicDialect dialect = new AnthropicDialect();
        
        String messageDeltaJson = "{\"type\":\"message_delta\"," +
            "\"delta\":{\"stop_reason\":\"end_turn\"}," +
            "\"usage\":{\"output_tokens\":50,\"cache_read_input_tokens\":100," +
            "\"cache_creation_input_tokens\":0}}";
        
        ChatStreamChunk chunk = dialect.parseStreamChunk(messageDeltaJson);
        
        assertNotNull(chunk);
        assertEquals("stop", chunk.getFinishReason());
        
        assertNotNull(chunk.getUsage());
        assertEquals(50, chunk.getUsage().getCompletionTokens().intValue());
        assertEquals(100, chunk.getUsage().getCacheHitTokens().intValue());
        assertEquals(0, chunk.getUsage().getCacheCreationTokens().intValue());
    }

    @Test
    public void testSetHeaders() {
        AnthropicDialect dialect = new AnthropicDialect();
        
        io.nop.http.api.client.HttpRequest httpRequest = new io.nop.http.api.client.HttpRequest();
        dialect.setHeaders(httpRequest, "test-api-key", null);
        
        assertEquals("application/json", httpRequest.getHeaders().get("Content-Type"));
        assertEquals("test-api-key", httpRequest.getHeaders().get("x-api-key"));
        assertEquals("2023-06-01", httpRequest.getHeaders().get("anthropic-version"));
    }

    @Test
    public void testConvertToolDefinitions() {
        AnthropicDialect dialect = new AnthropicDialect();
        
        List<io.nop.ai.api.chat.messages.ChatToolDefinition> tools = new ArrayList<>();
        
        io.nop.ai.api.chat.messages.ChatToolDefinition tool = new io.nop.ai.api.chat.messages.ChatToolDefinition();
        tool.setName("get_weather");
        tool.setDescription("Get the current weather");
        tool.setParameters(Map.of(
            "type", "object",
            "properties", Map.of(
                "location", Map.of("type", "string")
            )
        ));
        tools.add(tool);
        
        List<Map<String, Object>> converted = dialect.convertToolDefinitions(tools);
        
        assertNotNull(converted);
        assertEquals(1, converted.size());
        
        Map<String, Object> toolMap = converted.get(0);
        assertEquals("get_weather", toolMap.get("name"));
        assertEquals("Get the current weather", toolMap.get("description"));
        // Anthropic 使用 input_schema 而不是 parameters
        assertNotNull(toolMap.get("input_schema"));
    }
}
