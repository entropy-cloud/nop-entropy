package io.nop.ai.agent.engine;

import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2284 (WI3 invariant 1): placeholders are derived-state rendered only at
 * request-construction time — one per open callId, never mutating the session
 * message list. The placeholder text guides the wait/parallel semantics
 * (design nop-ai-agent-async-tool-execution.md §三.2/§三.3).
 */
public class TestToolCallPlaceholderRenderer {

    private static ChatToolCallMessage call(String id, String name) {
        return new ChatToolCallMessage(id, name, Map.of());
    }

    @Test
    void onePlaceholderPerOpenCall() {
        List<ChatMessage> messages = List.of(
                new ChatUserMessage("run"),
                new ChatAssistantMessage(""),
                call("1", "bash"),
                call("2", "read-file"));
        List<ChatToolResponseMessage> placeholders = ToolCallPlaceholderRenderer.renderPlaceholders(messages);
        assertEquals(2, placeholders.size());
        assertEquals("1", placeholders.get(0).getCallId());
        assertEquals("bash", placeholders.get(0).getName());
        assertEquals("2", placeholders.get(1).getCallId());
    }

    @Test
    void closedCallsGetNoPlaceholder() {
        List<ChatMessage> messages = List.of(
                call("1", "read-file"),
                new ChatToolResponseMessage("1", "read-file", "done"));
        assertTrue(ToolCallPlaceholderRenderer.renderPlaceholders(messages).isEmpty());
    }

    @Test
    void placeholderTextGuidesWaitAndParallelSemantics() {
        ChatToolResponseMessage placeholder = ToolCallPlaceholderRenderer.renderPlaceholder(
                call("7", "bash"));
        String text = placeholder.getContent();
        assertTrue(text.contains("bash"), "text names the tool: " + text);
        assertTrue(text.contains("7"), "text carries the callId: " + text);
        assertTrue(text.toLowerCase().contains("still running"), "running state: " + text);
        assertTrue(text.toLowerCase().contains("do not repeat"), "anti-repeat guidance: " + text);
        assertTrue(text.toLowerCase().contains("wait"), "wait guidance: " + text);
        assertTrue(text.toLowerCase().contains("parallel"), "parallel-work guidance: " + text);
    }

    @Test
    void renderingDoesNotMutateInputMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatUserMessage("run"));
        messages.add(new ChatAssistantMessage(""));
        messages.add(call("1", "bash"));
        messages.add(new ChatUserMessage("[steering] also do X"));
        List<ChatMessage> snapshot = new ArrayList<>(messages);

        ToolCallPlaceholderRenderer.renderPlaceholders(messages);

        assertEquals(snapshot, messages);
        assertEquals(4, messages.size());
    }
}
