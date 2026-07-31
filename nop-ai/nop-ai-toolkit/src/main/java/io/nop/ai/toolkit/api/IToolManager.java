package io.nop.ai.toolkit.api;

import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Central manager for AI tool execution, discovery, and the interceptor chain.
 * <p>
 * {@link #callTool} routes a single tool call through the registered
 * {@link IToolCallInterceptor} chain and then to the {@link IToolExecutor}
 * resolved for the tool name. {@link #callTools} is a convenience facade that
 * dispatches a batch of calls (parallel or sequential depending on the request).
 * {@link #listTools} / {@link #loadTool} provide tool discovery from the VFS
 * tool definitions.
 * <p>
 * Interceptor rejection (a {@code beforeCall} returning false) and unknown tool
 * names do not fail the returned future — they complete normally with a result
 * whose status/error carries the rejection reason. Executor-level failures are
 * propagated through the returned future.
 */
public interface IToolManager {
    /**
     * Executes a single tool call through the interceptor chain and the
     * executor resolved for {@code toolName}.
     * <p>
     * If an interceptor's {@code beforeCall} rejects the call, or no executor is
     * registered for {@code toolName}, the returned future completes normally
     * with a failure result (status/error populated). Otherwise the future
     * completes with the executor's result after {@code afterCall} hooks run.
     *
     * @param toolName the tool to invoke (must match {@code AiToolModel.getName()})
     * @param call     the tool call payload
     * @param context  the execution context
     * @return a future that completes with the tool call result
     */
    CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context);

    /**
     * Executes a batch of tool calls, dispatching them in parallel (bounded by
     * the request's maxConcurrency) or sequentially depending on the request
     * flags. Each call goes through {@link #callTool} individually.
     *
     * @param calls   the batch of tool calls to execute
     * @param context the execution context
     * @return a future that completes when every call in the batch has completed
     */
    CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context);

    /**
     * Discovers all registered tool definitions (from the VFS
     * {@code /nop/ai/tools} directory).
     *
     * @return the list of tool models; empty list if none are defined
     */
    List<AiToolModel> listTools();

    /**
     * Loads a single tool definition by name (cached through the component
     * model registry).
     *
     * @param toolName the tool name
     * @return the tool model, or null if no definition exists
     */
    AiToolModel loadTool(String toolName);
}
