package io.nop.ai.core.api.tool;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface IToolProvider {

    List<ToolSpecification> listTools();

    CallToolResult callTool(CallToolRequest toolRequest);

    CompletionStage<CallToolResult> callToolAsync(CallToolRequest toolRequest);
}