package io.nop.ai.toolkit.executor;

import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.api.IToolExecutorProvider;
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

    @Override
    public IToolExecutor getExecutor(String toolName) {
        return executors.get(toolName);
    }

    @Override
    public Collection<String> getToolNames() {
        return Collections.unmodifiableSet(executors.keySet());
    }
}
