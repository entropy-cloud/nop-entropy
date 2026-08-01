package io.nop.ai.toolkit.api;

import java.util.Collection;

/**
 * Resolves {@link IToolExecutor} instances by tool name.
 * <p>
 * Acts as the registry consulted by {@link IToolManager} to dispatch a tool
 * call: {@link #getExecutor} returns the executor registered for a tool name
 * (or null when none is registered), and {@link #getToolNames} exposes the
 * registered tool names for discovery.
 */
public interface IToolExecutorProvider {
    /**
     * @param toolName the tool name to resolve
     * @return the executor registered for the tool name, or null if none is registered
     */
    IToolExecutor getExecutor(String toolName);

    /**
     * @return the names of all tools this provider can execute
     */
    Collection<String> getToolNames();
}
