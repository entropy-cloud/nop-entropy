package io.nop.ai.toolkit.api;

import java.util.Collection;

public interface IToolExecutorProvider {
    IToolExecutor getExecutor(String toolName);

    Collection<String> getToolNames();
}
