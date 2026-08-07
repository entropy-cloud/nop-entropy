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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 329 — 验证 {@link ChatResponse} 的单一拆分模型：内容统一由 {@code messages}
 * 序列承载，聚合访问器 {@link ChatResponse#outputText()} / {@link ChatResponse#outputToolCalls()}
 * 与 {@link ChatResponse#getFullContent()} 基于 messages 工作。
 */
public class TestChatResponse {

    @Test
    public void outputText_concatenatesAssistantTextMessages() {
        ChatReasoningMessage reasoning = new ChatReasoningMessage("thinking...");
        ChatAssistantMessage assistant = new ChatAssistantMessage("Hello!");
        ChatToolCallMessage toolCall = new ChatToolCallMessage("call_1", "get_weather", null);

        ChatResponse response = new ChatResponse();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(reasoning);
        messages.add(assistant);
        messages.add(toolCall);
        response.setMessages(messages);

        // outputText 跳过 reasoning / tool_call，仅聚合 assistant 文本
        assertEquals("Hello!", response.outputText(),
                "outputText must return the assistant text, skipping reasoning and tool_call");
    }

    @Test
    public void outputText_nullWhenNoAssistantMessage() {
        ChatResponse response = new ChatResponse();
        assertNull(response.outputText());
        assertNull(response.getMessages());

        ChatResponse onlyReasoning = new ChatResponse();
        onlyReasoning.addMessage(new ChatReasoningMessage("hmm"));
        assertNull(onlyReasoning.outputText(), "no assistant message → outputText null");
    }

    @Test
    public void outputToolCalls_collectsChatToolCallMessageItems() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("city", "Beijing");
        ChatToolCallMessage toolCall = new ChatToolCallMessage("call_1", "get_weather", args);
        ChatAssistantMessage assistant = new ChatAssistantMessage("answer");

        ChatResponse response = new ChatResponse();
        response.addMessage(assistant);
        response.addMessage(toolCall);

        List<ChatToolCall> calls = response.outputToolCalls();
        assertEquals(1, calls.size());
        assertEquals("call_1", calls.get(0).getId());
        assertEquals("get_weather", calls.get(0).getName());
        assertEquals("Beijing", calls.get(0).getArguments().get("city"));
    }

    @Test
    public void outputToolCalls_emptyWhenNone() {
        ChatResponse response = new ChatResponse();
        response.addMessage(new ChatAssistantMessage("hi"));
        assertTrue(response.outputToolCalls().isEmpty());
    }

    @Test
    public void successFactory_populatesMessages() {
        ChatReasoningMessage reasoning = new ChatReasoningMessage("hmm");
        ChatAssistantMessage assistant = new ChatAssistantMessage("answer");

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(reasoning);
        messages.add(assistant);

        ChatResponse response = ChatResponse.success(messages);

        assertNotNull(response.getMessages());
        assertEquals(2, response.getMessages().size());
        assertEquals("answer", response.outputText(),
                "success(List) must populate messages and expose assistant text via outputText");
    }

    @Test
    public void successFactory_singleMessageStillWorks() {
        ChatAssistantMessage assistant = new ChatAssistantMessage("hi");
        ChatResponse response = ChatResponse.success(assistant);

        assertEquals("hi", response.outputText());
    }

    @Test
    public void getFullContent_delegatesToOutputText() {
        ChatAssistantMessage assistant = new ChatAssistantMessage("text");
        ChatResponse response = ChatResponse.success(assistant);

        assertEquals("text", response.getFullContent(),
                "getFullContent must aggregate assistant text from messages");
    }

    @Test
    public void copy_syncsMessages() {
        ChatReasoningMessage reasoning = new ChatReasoningMessage("think");
        ChatAssistantMessage assistant = new ChatAssistantMessage("text");

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
        assertEquals("text", copy.outputText(),
                "copied response must aggregate assistant text from copied messages");
        // deep copy: mutating original must not affect copy
        assertFalse(copy.getMessages().get(1) == assistant);
    }

    @Test
    public void addMessage_appendsToMessages() {
        ChatResponse response = new ChatResponse();
        response.addMessage(new ChatAssistantMessage("a"));
        response.addMessage(new ChatReasoningMessage("b"));

        assertEquals(2, response.getMessages().size());
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
