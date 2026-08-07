package io.nop.ai.agent.support;

import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 329 proof: verify {@link ChatResponseFixtures} produces the single
 * split-model form where tool calls are carried exclusively as discrete
 * {@link ChatToolCallMessage} items in {@code response.getMessages()}
 * (the legacy {@code assistant.toolCalls} folded field has been removed).
 */
public class TestChatResponseFixtures {

    private ChatToolCall toolCall(String id, String name) {
        ChatToolCall call = new ChatToolCall();
        call.setId(id);
        call.setName(name);
        return call;
    }

    @Test
    void assistantWithToolCallsPopulatesCanonicalTrack() {
        ChatToolCall a = toolCall("call_1", "search");
        ChatToolCall b = toolCall("call_2", "calc");

        ChatResponse response = ChatResponseFixtures.assistantWithToolCalls("thinking", a, b);

        // Plan 329：单一拆分模型——工具调用由 outputToolCalls() 聚合访问器暴露
        List<ChatToolCall> aggregatedCalls = response.outputToolCalls();
        assertNotNull(aggregatedCalls, "outputToolCalls() should return the tool calls");
        assertEquals(2, aggregatedCalls.size(), "should carry 2 tool calls");

        // --- Canonical track: response.getMessages() contains ChatToolCallMessage items ---
        List<ChatMessage> messages = response.getMessages();
        assertNotNull(messages, "canonical track: response.messages must be populated");
        assertTrue(messages.size() >= 3, "canonical track: should contain assistant + 2 tool_call messages");

        Set<String> canonicalCallIds = new HashSet<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof ChatToolCallMessage) {
                canonicalCallIds.add(((ChatToolCallMessage) msg).getCallId());
            }
        }
        Set<String> aggregatedCallIds = new HashSet<>();
        for (ChatToolCall tc : aggregatedCalls) {
            aggregatedCallIds.add(tc.getId());
        }
        assertEquals(aggregatedCallIds, canonicalCallIds,
                "outputToolCalls() id set must equal canonical track callId set");
    }

    @Test
    void assistantWithToolCallsProducesCanonicalOrderingAssistantThenToolCalls() {
        ChatToolCall a = toolCall("call_1", "search");
        ChatResponse response = ChatResponseFixtures.assistantWithToolCalls("", a);

        List<ChatMessage> messages = response.getMessages();
        assertNotNull(messages);
        // First message is the assistant text; second is the tool_call
        assertTrue(messages.get(0) instanceof ChatAssistantMessage,
                "canonical ordering: first message must be ChatAssistantMessage");
        assertTrue(messages.get(1) instanceof ChatToolCallMessage,
                "canonical ordering: second message must be ChatToolCallMessage");
    }

    @Test
    void assistantTextProducesNoToolCalls() {
        ChatResponse response = ChatResponseFixtures.assistantText("hello");

        assertTrue(response.outputToolCalls().isEmpty(),
                "no tool calls: outputToolCalls() must be empty");

        boolean hasToolCallMsg = false;
        for (ChatMessage msg : response.getMessages()) {
            if (msg instanceof ChatToolCallMessage) {
                hasToolCallMsg = true;
                break;
            }
        }
        assertTrue(!hasToolCallMsg, "no tool calls: canonical track must contain no ChatToolCallMessage");
    }

    @Test
    void assistantWithReasoningProducesReasoningBeforeAssistant() {
        ChatResponse response = ChatResponseFixtures.assistantWithReasoning("thinking hard", "answer");

        List<ChatMessage> messages = response.getMessages();
        assertNotNull(messages);
        assertTrue(messages.get(0) instanceof ChatReasoningMessage,
                "reasoning ordering: first message must be ChatReasoningMessage");
        assertTrue(messages.get(1) instanceof ChatAssistantMessage,
                "reasoning ordering: second message must be ChatAssistantMessage");
    }

    @Test
    void callIdPairingIntactBetweenTracks() {
        ChatToolCall a = toolCall("call_42", "lookup");
        ChatResponse response = ChatResponseFixtures.assistantWithToolCalls("x", a);

        // The ChatToolCallMessage callId must equal the ChatToolCall id
        // (this is the invariant AgentToolDispatcher relies on for pairing
        // with ChatToolResponseMessage).
        ChatToolCallMessage tcm = null;
        for (ChatMessage msg : response.getMessages()) {
            if (msg instanceof ChatToolCallMessage) {
                tcm = (ChatToolCallMessage) msg;
                break;
            }
        }
        assertNotNull(tcm, "must contain a ChatToolCallMessage");
        assertEquals("call_42", tcm.getCallId(), "callId must match the ChatToolCall.id");
    }
}
