package io.nop.ai.agent.hook;

import io.nop.ai.agent.middleware.IAgentMiddleware;

import java.util.Collections;
import java.util.List;

public interface IHookRegistry {

    List<IAgentLifecycleHook> getHooks(AgentLifecyclePoint point, String agentName);

    void register(AgentLifecyclePoint point, IAgentLifecycleHook hook);

    /**
     * Return the registered onion-style middlewares for the given lifecycle
     * point, in outer-to-inner order. An empty list means no middleware
     * wrapping at this point — the executor falls through to the hook-only
     * core path.
     *
     * <p>Default returns an empty list (no middleware support) so existing
     * implementations (e.g. {@link NoOpHookRegistry}) remain backward
     * compatible without changes beyond overriding this default.
     */
    default List<IAgentMiddleware> getMiddlewares(AgentLifecyclePoint point, String agentName) {
        return Collections.emptyList();
    }

    /**
     * Register a middleware at the given lifecycle point. Implementations that
     * do not support middleware registration throw
     * {@link UnsupportedOperationException}.
     *
     * <p>Default throws {@link UnsupportedOperationException} so read-only /
     * no-op registries fail fast rather than silently dropping the middleware
     * (Minimum Rules #24 — No Silent No-Op).
     */
    default void registerMiddleware(AgentLifecyclePoint point, IAgentMiddleware middleware) {
        throw new UnsupportedOperationException(
                "This IHookRegistry implementation does not support middleware registration");
    }

    static IHookRegistry empty() {
        return NoOpHookRegistry.INSTANCE;
    }
}
