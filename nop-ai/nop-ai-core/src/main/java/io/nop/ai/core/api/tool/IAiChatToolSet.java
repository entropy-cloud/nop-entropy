package io.nop.ai.core.api.tool;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Legacy AI chat tool set interface of the {@code IAiChat*} pipeline.
 * <p>
 * <b>Deprecation semantics (P2-MA3-04 ruling):</b> still the active tool-set
 * contract of the legacy chat path in nop-ai-core (consumed by
 * {@code AiCommand} and the task engine via {@code ai:toolSet}, produced by
 * {@code GraphQLToolSetFactoryBean}). Retained
 * {@code @Deprecated(forRemoval = true)} because the new tool contracts
 * ({@code IToolDefinition} in nop-ai-api, {@code IToolExecutor}/{@code IToolManager}
 * in nop-ai-toolkit) supersede it; full migration is future major-version work.
 * Do not remove while legacy callers remain.
 */
@Deprecated(forRemoval = true)
public interface IAiChatToolSet {
    Set<String> getToolNames();

    IAiChatFunctionTool getFunctionTool(String toolName);

    List<IAiChatFunctionTool> getFunctionTools();

    List<IAiChatFunctionTool> getFunctionTools(Set<String> toolNames);

    IAiChatToolSet addFunction(IAiChatFunctionTool func);

    IAiChatToolSet addFunctions(Collection<? extends IAiChatFunctionTool> funcs);

    IAiChatToolSet addToolSet(IAiChatToolSet toolSet);
}
