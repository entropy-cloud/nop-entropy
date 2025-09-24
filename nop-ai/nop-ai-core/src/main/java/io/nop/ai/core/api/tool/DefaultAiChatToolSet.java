package io.nop.ai.core.api.tool;

import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.ai.core.api.tool.DefaultAiChatFunctionTool.fromMethod;

public class DefaultAiChatToolSet implements IAiChatToolSet {
    protected final Map<String, IAiChatFunctionTool> toolMap;

    public DefaultAiChatToolSet() {
        this.toolMap = new LinkedHashMap<>();
    }

    // 内部私有构造用于复制Map
    private DefaultAiChatToolSet(Map<String, IAiChatFunctionTool> toolMap) {
        this.toolMap = toolMap;
    }

    public static DefaultAiChatToolSet staticMethodsToolSet(Class<?> clazz) {
        IClassModel classModel = ReflectionManager.instance().getClassModel(clazz);
        Map<String, IAiChatFunctionTool> tools = new LinkedHashMap<>();

        for (IFunctionModel func : classModel.getStaticMethods()) {
            String description = func.getDescription();
            if (description == null)
                continue;

            IAiChatFunctionTool funcTool = fromMethod(func);
            tools.put(funcTool.getName(), funcTool);
        }
        return new DefaultAiChatToolSet(tools);
    }

    @Override
    public Set<String> getToolNames() {
        return toolMap.keySet();
    }

    @Override
    public IAiChatFunctionTool getFunctionTool(String toolName) {
        return toolMap.get(toolName);
    }

    @Override
    public List<IAiChatFunctionTool> getFunctionTools() {
        return new ArrayList<>(toolMap.values());
    }

    @Override
    public List<IAiChatFunctionTool> getFunctionTools(Set<String> toolNames) {
        List<IAiChatFunctionTool> result = new ArrayList<>();
        for (String name : toolNames) {
            IAiChatFunctionTool tool = toolMap.get(name);
            if (tool != null) {
                result.add(tool);
            }
        }
        return result;
    }

    @Override
    public IAiChatToolSet addFunction(IAiChatFunctionTool func) {
        if (func == null) return this;
        this.toolMap.put(func.getName(), func);
        return this;
    }

    @Override
    public IAiChatToolSet addFunctions(Collection<? extends IAiChatFunctionTool> funcs) {
        if (funcs == null || funcs.isEmpty()) return this;
        for (IAiChatFunctionTool func : funcs) {
            addFunction(func);
        }
        return this;
    }

    @Override
    public IAiChatToolSet addToolSet(IAiChatToolSet toolSet) {
        if (toolSet == null) return this;
        return addFunctions(toolSet.getFunctionTools());
    }
}