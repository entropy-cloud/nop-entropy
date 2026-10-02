package io.nop.ai.agent.engine;

import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders placeholder tool results for open (running) tool calls
 * (roadmap WI3 invariant 1: the placeholder exists only at request-construction
 * time and never enters session history — design
 * {@code nop-ai-agent-async-tool-execution.md} §三.2).
 *
 * <p>Consumers are the wait-phase wakeup request builders (WI4 heartbeat /
 * WI5 steering wakeup): before dispatching a transient LLM request while tools
 * are still running, the builder inserts one placeholder per open callId so
 * the request satisfies the provider tool_result-adjacency contract. The
 * returned messages are value snapshots — callers must not add them to
 * {@code AgentExecutionContext}; the real results are later committed
 * in-place by the tool dispatcher (WI3 invariant 2).
 *
 * <p>The placeholder text deliberately guides the model toward the wait /
 * parallel-work semantics adjudicated for wakeup turns: do not re-issue the
 * running call; keep waiting or do independent work in parallel.
 */
public final class ToolCallPlaceholderRenderer {

    private ToolCallPlaceholderRenderer() {
    }

    /**
     * Renders one placeholder tool result per open tool call in the given
     * message list (order follows the message list). Does not mutate the
     * input list.
     */
    public static List<ChatToolResponseMessage> renderPlaceholders(List<ChatMessage> messages) {
        List<ChatToolCallMessage> openCalls = OpenToolCallResolver.findOpenToolCalls(messages);
        List<ChatToolResponseMessage> placeholders = new ArrayList<>(openCalls.size());
        for (ChatToolCallMessage call : openCalls) {
            placeholders.add(renderPlaceholder(call));
        }
        return placeholders;
    }

    /**
     * Builds the placeholder tool result for a single running tool call.
     * Package-visible for tests and wakeup builders that already hold the
     * resolved open call.
     */
    static ChatToolResponseMessage renderPlaceholder(ChatToolCallMessage call) {
        String text = "Tool \"" + call.getName() + "\" (callId=" + call.getCallId()
                + ") is still running and has not produced a result yet. Do not repeat this call. "
                + "You may keep waiting for it to finish, or do other independent work in parallel "
                + "by calling different tools.";
        return new ChatToolResponseMessage(call.getCallId(), call.getName(), text);
    }
}
