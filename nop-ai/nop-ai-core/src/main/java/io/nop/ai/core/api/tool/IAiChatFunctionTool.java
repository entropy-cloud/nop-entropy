package io.nop.ai.core.api.tool;

import io.nop.api.core.json.JsonSchema;
import io.nop.api.core.util.FutureHelper;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Legacy AI chat function tool interface of the {@code IAiChat*} pipeline.
 * <p>
 * <b>Deprecation semantics (P2-MA3-04 ruling):</b> still the active tool
 * contract of the legacy chat path in nop-ai-core (implemented by
 * {@code DefaultAiChatFunctionTool}, consumed by {@code AiCommand} and
 * {@code GraphQLToolProvider}). Retained {@code @Deprecated(forRemoval = true)}
 * because the new tool contracts ({@code IToolDefinition} in nop-ai-api,
 * {@code IToolExecutor}/{@code IToolManager} in nop-ai-toolkit) supersede it;
 * full migration is future major-version work. Do not remove while legacy
 * callers remain.
 */
@Deprecated(forRemoval = true)
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
