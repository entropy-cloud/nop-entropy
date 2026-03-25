package io.nop.ai.toolkit.api;

import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IToolManager {
    CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context);

    CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context);

    List<AiToolModel> listTools();

    AiToolModel loadTool(String toolName);
}
