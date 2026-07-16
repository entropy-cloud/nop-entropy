package io.nop.ai.agent.hook;

import io.nop.ai.agent.middleware.IAgentMiddleware;

import java.util.Collections;
import java.util.List;

public final class NoOpHookRegistry implements IHookRegistry {

    public static final NoOpHookRegistry INSTANCE = new NoOpHookRegistry();

    private NoOpHookRegistry() {
    }

    @Override
    public List<IAgentLifecycleHook> getHooks(AgentLifecyclePoint point, String agentName) {
        return Collections.emptyList();
    }

    @Override
    public void register(AgentLifecyclePoint point, IAgentLifecycleHook hook) {
        throw new UnsupportedOperationException("NoOpHookRegistry does not support hook registration");
    }

    @Override
    public List<IAgentMiddleware> getMiddlewares(AgentLifecyclePoint point, String agentName) {
        return Collections.emptyList();
    }

    @Override
    public void registerMiddleware(AgentLifecyclePoint point, IAgentMiddleware middleware) {
        throw new UnsupportedOperationException("NoOpHookRegistry does not support middleware registration");
    }
}
