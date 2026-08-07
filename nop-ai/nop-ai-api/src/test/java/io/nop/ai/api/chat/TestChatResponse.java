package io.nop.ai.api.chat;

import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 326 Phase 1 — 验证 {@link ChatResponse} 的 messages 序列、{@link ChatResponse#getMessage()}
 * 弃用委托、{@link ChatResponse#success(List)} 工厂与 {@link ChatResponse#copy()} 同步。
 */
public class TestChatResponse {

    @Test
    public void getMessage_delegatesToFirstAssistantInMessages() {
        ChatReasoningMessage reasoning = new ChatReasoningMessage("thinking...");
        ChatAssistantMessage assistant = new ChatAssistantMessage("Hello!");
        ChatToolCallMessage toolCall = new ChatToolCallMessage("call_1", "get_weather", null);

        ChatResponse response = new ChatResponse();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(reasoning);
        messages.add(assistant);
        messages.add(toolCall);
        response.setMessages(messages);

        ChatAssistantMessage delegated = response.getMessage();
        assertNotNull(delegated, "getMessage() must return the first ChatAssistantMessage in messages");
        assertEquals("Hello!", delegated.getContent(),
                "delegation must skip preceding reasoning and return the assistant text message");
        assertSame(assistant, delegated, "must return the same instance stored in messages");
    }

    @Test
    public void getMessage_fallsBackToLegacyFieldWhenMessagesEmpty() {
        ChatAssistantMessage legacy = new ChatAssistantMessage("legacy");
        ChatResponse response = new ChatResponse();
        response.setMessage(legacy);

        assertSame(legacy, response.getMessage(),
                "when messages is null, getMessage() must return the legacy message field (behavior unchanged)");
    }

    @Test
    public void getMessage_returnsNullWhenNothingSet() {
        ChatResponse response = new ChatResponse();
        assertNull(response.getMessage());
        assertNull(response.getMessages());
    }

    @Test
    public void successFactory_populatesMessagesAndLegacyField() {
        ChatReasoningMessage reasoning = new ChatReasoningMessage("hmm");
        ChatAssistantMessage assistant = new ChatAssistantMessage("answer");

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(reasoning);
        messages.add(assistant);

        ChatResponse response = ChatResponse.success(messages);

        assertNotNull(response.getMessages());
        assertEquals(2, response.getMessages().size());
        assertSame(assistant, response.getMessage(),
                "success(List) must also seed the legacy message field with the first assistant message");
    }

    @Test
    public void successFactory_singleMessageStillWorks() {
        ChatAssistantMessage assistant = new ChatAssistantMessage("hi");
        ChatResponse response = ChatResponse.success(assistant);

        assertEquals("hi", response.getMessage().getContent());
    }

    @Test
    public void copy_syncsMessages() {
        ChatReasoningMessage reasoning = new ChatReasoningMessage("think");
        ChatAssistantMessage assistant = new ChatAssistantMessage("text");
        assistant.setToolCalls(List.of(new ChatToolCall()));

        ChatResponse response = new ChatResponse();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(reasoning);
        messages.add(assistant);
        response.setMessages(messages);

        ChatResponse copy = response.copy();

        assertNotNull(copy.getMessages());
        assertEquals(2, copy.getMessages().size());
        assertTrue(copy.getMessages().get(0) instanceof ChatReasoningMessage);
        assertEquals("think", copy.getMessages().get(0).getContent());
        assertEquals("text", copy.getMessage().getContent(),
                "copied response getMessage() delegation must still work");
    }

    @Test
    public void addMessage_appendsToMessages() {
        ChatResponse response = new ChatResponse();
        response.addMessage(new ChatAssistantMessage("a"));
        response.addMessage(new ChatReasoningMessage("b"));

        assertEquals(2, response.getMessages().size());
    }

    @Test
    public void dualTrack_getMessageConsistentWithLegacyField() {
        ChatAssistantMessage assistant = new ChatAssistantMessage("answer");
        ChatResponse response = new ChatResponse();
        response.setMessage(assistant);
        response.addMessage(assistant);

        assertSame(assistant, response.getMessage(),
                "when both message field and messages hold the same assistant instance, getMessage() is consistent");
    }

    @Test
    public void responseFormatObjectRoundTrip() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        ResponseFormat fmt = ResponseFormat.jsonSchema(schema);

        ChatResponse response = new ChatResponse();
        ChatOptions options = new ChatOptions();
        options.setResponseFormatConfig(fmt);
        response.setOptions(options);

        ResponseFormat stored = response.getOptions().getResponseFormatConfig();
        assertNotNull(stored);
        assertEquals(ResponseFormat.TYPE_JSON_SCHEMA, stored.getType());
        assertEquals("object", stored.getSchema().get("type"));
    }
}
