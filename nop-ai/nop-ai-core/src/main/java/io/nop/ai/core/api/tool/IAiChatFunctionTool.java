package io.nop.ai.core.api.tool;

import io.nop.api.core.json.JsonSchema;
import io.nop.api.core.util.FutureHelper;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * @deprecated This internal AI core interface is deprecated and will be removed in future versions.
 * Please use the new AI API instead.
 */
@Deprecated
public interface IAiChatFunctionTool {
    String getName();

    String getDescription();

    JsonSchema getInputSchema();

    JsonSchema getOutputSchema();

    Boolean getReturnDirect();

    Object callTool(Map<String, Object> args);

    default CompletionStage<Object> callToolAsync(Map<String, Object> args) {
        return FutureHelper.futureCall(() -> callTool(args));
    }

    ToolSpecification toSpec();
}
