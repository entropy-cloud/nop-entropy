package io.nop.ai.toolkit.executor;

import io.nop.ai.api.exceptions.NopAiException;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.api.IToolExecutorProvider;
import io.nop.ai.toolkit.model.AiToolModel;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DefaultToolExecutorProvider implements IToolExecutorProvider {
    private Map<String, IToolExecutor> executors = new HashMap<>();

    public void setExecutors(Collection<IToolExecutor> executors) {
        if (executors != null) {
            for (IToolExecutor executor : executors) {
                this.executors.put(executor.getToolName(), executor);
            }
        }
    }

    /**
     * Plan 304: register a tool executor from an {@link AiToolModel} that has
     * a {@code class} attribute. When the tool model declares a class name,
     * the corresponding {@link IToolExecutor} is instantiated and registered.
     * This eliminates the need for an explicit {@code beans.xml} bean declaration.
     *
     * @return {@code true} if the tool was registered from its class attribute;
     *         {@code false} if no class attribute was set (caller should rely
     *         on the existing beans.xml registration).
     */
    public boolean registerTool(AiToolModel toolModel) {
        String className = toolModel.getClassName();
        if (className == null || className.isEmpty()) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (!(instance instanceof IToolExecutor)) {
                throw new IllegalStateException(
                        "Tool class " + className + " for tool " + toolModel.getName()
                                + " must implement " + IToolExecutor.class.getName());
            }
            IToolExecutor executor = (IToolExecutor) instance;
            executors.put(executor.getToolName() != null ? executor.getToolName() : toolModel.getName(), executor);
            return true;
        } catch (Exception e) {
            throw new NopAiException("Failed to register tool " + toolModel.getName()
                    + " from class " + className, e);
        }
    }

    @Override
    public IToolExecutor getExecutor(String toolName) {
        return executors.get(toolName);
    }

    @Override
    public Collection<String> getToolNames() {
        return Collections.unmodifiableSet(executors.keySet());
    }
}
