package io.nop.ai.core.mcp;

import io.nop.ai.core.api.messages.ToolResponse;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface IMcpClient {
    List<ToolSpecification> listTools();

    String executeTool(ToolResponse toolRequest);

    CompletionStage<String> executeToolAsync(ToolResponse toolRequest);
}
