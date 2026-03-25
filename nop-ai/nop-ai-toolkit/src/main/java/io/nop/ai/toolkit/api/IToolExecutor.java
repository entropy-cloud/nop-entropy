package io.nop.ai.toolkit.api;

import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import java.util.concurrent.CompletionStage;

public interface IToolExecutor {
    String getToolName();

    CompletionStage<AiToolCallResult> executeAsync(AiToolCall request, IToolExecuteContext context);
}
