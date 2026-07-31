package io.nop.ai.toolkit.api;

import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import java.util.concurrent.CompletionStage;

/**
 * Executes an AI tool call and returns the result.
 * <p>
 * Each executor is bound to exactly one tool: {@link #getToolName()} returns the
 * tool name that the executor serves (must match {@code AiToolModel.getName()}
 * of the tool definition it implements). Executors are resolved and invoked by
 * the {@link IToolManager} through the {@link IToolExecutorProvider}; the
 * executor itself never talks to the caller directly.
 */
public interface IToolExecutor {
    /**
     * Returns the name of the tool this executor implements.
     * <p>
     * The name must match the {@code AiToolModel.getName()} of the tool
     * definition, otherwise the executor will never be resolved for that tool.
     *
     * @return the tool name; never null
     */
    String getToolName();

    /**
     * Executes the tool call asynchronously and returns the result.
     * <p>
     * Implementations must not block the calling thread; long-running work must
     * be offloaded to a worker thread / virtual thread. Failures (validation
     * errors, tool backend errors) must be surfaced as a failed
     * {@code CompletionStage} or as a result whose status/error field carries
     * the failure — never as a thrown exception after the stage has been
     * returned.
     *
     * @param request the tool call to execute; never null
     * @param context the execution context (session, permissions, budget)
     * @return a stage that completes with the tool call result
     */
    CompletionStage<AiToolCallResult> executeAsync(AiToolCall request, IToolExecuteContext context);
}
