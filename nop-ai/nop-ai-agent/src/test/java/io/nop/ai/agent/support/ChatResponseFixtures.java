package io.nop.ai.agent.support;

import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Test fixture factories for building {@link ChatResponse} instances that
 * simulate LLM outputs in the post-plan-327 message-sequence form.
 *
 * <p><b>Dual-track production</b> (plan 327 Phase 1 risk-mitigation): every
 * factory that involves tool calls populates BOTH the legacy
 * {@code ChatAssistantMessage.toolCalls} field AND the canonical
 * {@code ChatResponse.messages} sequence with discrete
 * {@link ChatToolCallMessage} items. This keeps any not-yet-migrated call
 * site compiling/running during the transition window; plan 329 will
 * collapse the legacy field and leave a single canonical form.
 *
 * <p>The produced {@code messages} list follows the dialect (plan 326)
 * ordering convention: {@code [reasoning?] → assistant text → tool_call*}.
 */
public final class ChatResponseFixtures {

    private ChatResponseFixtures() {
    }

    /**
     * Build a response carrying an assistant text message with no tool calls.
     */
    public static ChatResponse assistantText(String text) {
        ChatAssistantMessage assistant = new ChatAssistantMessage(text);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(assistant);
        return ChatResponse.success(messages);
    }

    /**
     * Build a response carrying an assistant text message preceded by a
     * reasoning message (for reasoning-model scenarios).
     */
    public static ChatResponse assistantWithReasoning(String reasoning, String text) {
        ChatReasoningMessage reasoningMsg = new ChatReasoningMessage(reasoning);
        ChatAssistantMessage assistant = new ChatAssistantMessage(text);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(reasoningMsg);
        messages.add(assistant);
        return ChatResponse.success(messages);
    }

    /**
     * Build a response carrying an assistant message that requests tool calls.
     *
     * <p>Dual-track: the returned {@link ChatAssistantMessage} (reachable via
     * {@link ChatResponse#getMessage()}) has its {@code toolCalls} field
     * populated with the given calls, AND the canonical
     * {@link ChatResponse#getMessages()} sequence contains discrete
     * {@link ChatToolCallMessage} items (one per call, in order). Both views
     * describe the same tool-call set so consumers reading either path
     * observe identical semantics.
     *
     * @param text  the assistant text content (may be empty when the LLM only
     *              emitted tool calls)
     * @param calls the tool calls the mock LLM is requesting
     */
    public static ChatResponse assistantWithToolCalls(String text, ChatToolCall... calls) {
        List<ChatToolCall> toolCalls = calls != null && calls.length > 0
                ? Arrays.asList(calls)
                : new ArrayList<>();

        ChatAssistantMessage assistant = new ChatAssistantMessage(text);
        assistant.setToolCalls(toolCalls);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(assistant);
        for (ChatToolCall call : toolCalls) {
            messages.add(ChatToolCallMessage.fromChatToolCall(call));
        }
        return ChatResponse.success(messages);
    }

    /**
     * Build a response carrying an assistant message that requests tool calls,
     * accepting a {@link List} (convenience overload for batch test migration).
     *
     * @see #assistantWithToolCalls(String, ChatToolCall...)
     */
    public static ChatResponse assistantWithToolCalls(String text, List<ChatToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return assistantWithToolCalls(text);
        }
        return assistantWithToolCalls(text, toolCalls.toArray(new ChatToolCall[0]));
    }

    /**
     * Build a response carrying an assistant message with reasoning followed
     * by tool calls (reasoning-model + tool-use scenario).
     */
    public static ChatResponse assistantWithReasoningAndToolCalls(
            String reasoning, String text, ChatToolCall... calls) {
        List<ChatToolCall> toolCalls = calls != null && calls.length > 0
                ? Arrays.asList(calls)
                : new ArrayList<>();

        ChatAssistantMessage assistant = new ChatAssistantMessage(text);
        assistant.setToolCalls(toolCalls);

        List<ChatMessage> messages = new ArrayList<>();
        if (reasoning != null && !reasoning.isEmpty()) {
            messages.add(new ChatReasoningMessage(reasoning));
        }
        messages.add(assistant);
        for (ChatToolCall call : toolCalls) {
            messages.add(ChatToolCallMessage.fromChatToolCall(call));
        }
        return ChatResponse.success(messages);
    }

    /**
     * Build an error response (non-LLM error, e.g. transport failure).
     */
    public static ChatResponse error(String errorCode, String errorMessage) {
        return ChatResponse.error(errorCode, errorMessage);
    }

    /**
     * Build a folded {@link ChatAssistantMessage} with embedded tool calls.
     * <p>
     * For tests that construct <b>session history</b> (message lists for
     * compaction, session-store, checkpoint tests, etc.) — these simulate what
     * the engine actually writes into {@code ctx.getMessages()} (the folded
     * assistant message with embedded {@code toolCalls}). Using this factory
     * keeps the test's session-history format aligned with the engine's actual
     * output while avoiding direct {@code setToolCalls} calls in test code
     * (plan 327 grep target).
     *
     * @param text  the assistant content (may be empty)
     * @param calls the tool calls to embed in the folded assistant message
     * @return the folded assistant message (NOT wrapped in a ChatResponse)
     */
    public static ChatAssistantMessage foldedAssistantWithToolCalls(
            String text, ChatToolCall... calls) {
        ChatAssistantMessage assistant = new ChatAssistantMessage(text);
        List<ChatToolCall> toolCalls = calls != null && calls.length > 0
                ? Arrays.asList(calls)
                : new ArrayList<>();
        assistant.setToolCalls(toolCalls);
        return assistant;
    }
}
