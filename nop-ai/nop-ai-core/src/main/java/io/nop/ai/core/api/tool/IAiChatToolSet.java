package io.nop.ai.core.api.tool;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface IAiChatToolSet {
    IAiChatFunctionTool getFunctionTool(String toolName);

    List<IAiChatFunctionTool> getFunctionTools();

    List<IAiChatFunctionTool> getFunctionTools(Set<String> toolNames);

    IAiChatToolSet addFunction(IAiChatFunctionTool func);

    IAiChatToolSet addFunctions(Collection<? extends IAiChatFunctionTool> funcs);

    IAiChatToolSet addToolSet(IAiChatToolSet toolSet);
}
