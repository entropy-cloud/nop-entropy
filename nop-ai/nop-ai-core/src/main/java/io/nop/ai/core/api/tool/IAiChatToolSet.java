package io.nop.ai.core.api.tool;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * AI chat tool set interface. Part of the legacy {@code IAiChat*} naming convention.
 * @deprecated This internal AI core interface is deprecated and will be removed in future versions.
 * Please use the new AI API instead.
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
