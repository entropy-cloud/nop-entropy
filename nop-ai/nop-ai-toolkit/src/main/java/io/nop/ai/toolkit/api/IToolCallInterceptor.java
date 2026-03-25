package io.nop.ai.toolkit.api;

import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;

public interface IToolCallInterceptor {
    default boolean beforeCall(String toolName, AiToolCall request, IToolExecuteContext context) {
        return true;
    }

    default void afterCall(String toolName, AiToolCall request, IToolExecuteContext context,
                           AiToolCallResult result) {
    }
}
