package io.nop.ai.core.api.tool;

import io.nop.core.lang.eval.IEvalScope;

import java.util.concurrent.CompletionStage;

public interface IToolCaller {
    CompletionStage<CallToolResult> callToolAsync(ToolSpecification toolSpec, CallToolRequest request, IEvalScope scope);
}
