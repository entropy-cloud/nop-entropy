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
 * Test fixture factories for building {@link ChatResponse} instances and
 * session-history message lists in the post-plan-329 single split model.
 *
 * <p>Plan 329：寄居字段 {@code ChatAssistantMessage.toolCalls} 已删除，工具调用一律
 * 以独立 {@link ChatToolCallMessage} 承载。所有工厂产出的 {@code messages} 列表遵循
 * dialect 排序约定：{@code [reasoning?] → assistant text → tool_call*}。
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
     * <p>Plan 329：工具调用以独立 {@link ChatToolCallMessage} 承载（messages 序列含
     * assistant text → tool_call*），与 dialect parseResponse 产出同构。
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
     * Build a session-history message list representing one assistant tool-call
     * turn in the canonical split form: {@code [ChatAssistantMessage(text),
     * ChatToolCallMessage...]}. Use this to construct {@code ctx.getMessages()}
     * / session history for compaction, session-store, and checkpoint tests.
     *
     * @param text  the assistant content (may be empty)
     * @param calls the tool calls the assistant requested
     * @return the canonical message list for this turn (NOT wrapped in a ChatResponse)
     */
    public static List<ChatMessage> foldedAssistantWithToolCalls(
            String text, ChatToolCall... calls) {
        List<ChatMessage> turn = new ArrayList<>();
        turn.add(new ChatAssistantMessage(text));
        List<ChatToolCall> toolCalls = calls != null && calls.length > 0
                ? Arrays.asList(calls)
                : new ArrayList<>();
        for (ChatToolCall call : toolCalls) {
            turn.add(ChatToolCallMessage.fromChatToolCall(call));
        }
        return turn;
    }
}
