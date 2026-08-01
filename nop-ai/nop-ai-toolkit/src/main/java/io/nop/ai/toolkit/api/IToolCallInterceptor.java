package io.nop.ai.toolkit.api;

import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;

/**
 * Interceptor hook for the tool execution pipeline.
 * <p>
 * Registered interceptors are invoked by {@link IToolManager} around every tool
 * call: {@link #beforeCall} may reject the call (returning false) before any
 * executor runs, and {@link #afterCall} observes the result after execution.
 * Both default methods are no-ops so implementations only override what they need.
 */
public interface IToolCallInterceptor {
    /**
     * Invoked before a tool is executed.
     *
     * @param toolName the tool being invoked
     * @param request  the tool call payload
     * @param context  the execution context
     * @return true to proceed with execution, false to reject the call
     */
    default boolean beforeCall(String toolName, AiToolCall request, IToolExecuteContext context) {
        return true;
    }

    /**
     * Invoked after a tool has been executed.
     *
     * @param toolName the tool that was invoked
     * @param request  the tool call payload
     * @param context  the execution context
     * @param result   the tool call result
     */
    default void afterCall(String toolName, AiToolCall request, IToolExecuteContext context,
                           AiToolCallResult result) {
    }
}
