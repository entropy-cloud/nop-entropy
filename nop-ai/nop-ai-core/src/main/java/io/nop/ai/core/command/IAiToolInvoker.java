package io.nop.ai.core.command;

import io.nop.ai.api.chat.messages.ChatToolDefinition;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Tool invocation seam for {@link AiCommand}: resolves a tool's wire
 * specification and executes a tool call by name.
 *
 * <p>This is a transitional contract introduced while the deprecated chat stack
 * is retired (roadmap WI1). nop-ai-core cannot see nop-ai-toolkit, so the final
 * unification with the toolkit {@code IToolManager} / {@code IToolExecutor}
 * runtime is adjudicated by roadmap WI2; adapters live in modules above core.
 */
public interface IAiToolInvoker {
    /**
     * Returns the tool definition for {@code toolName}, or null when the tool
     * is unknown to this invoker.
     */
    ChatToolDefinition findTool(String toolName);

    /**
     * Executes the tool call asynchronously. The returned value is serialized
     * to JSON text before being appended to the conversation as the tool
     * response message.
     */
    CompletionStage<Object> callTool(String toolName, Map<String, Object> args);
}
