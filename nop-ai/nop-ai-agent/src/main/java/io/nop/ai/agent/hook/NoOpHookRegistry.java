package io.nop.ai.agent.hook;

import io.nop.ai.agent.NopAiAgentErrors;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.middleware.ExecutionPoint;
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
        throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_HOOK_REGISTRY_REGISTER_NOT_SUPPORTED);
    }

    @Override
    public List<IAgentMiddleware> getMiddlewares(AgentLifecyclePoint point, String agentName) {
        return Collections.emptyList();
    }

    @Override
    public void registerMiddleware(AgentLifecyclePoint point, IAgentMiddleware middleware) {
        throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_HOOK_REGISTRY_MIDDLEWARE_NOT_SUPPORTED);
    }

    @Override
    public List<IAgentMiddleware> getExecutionMiddlewares(ExecutionPoint point, String agentName) {
        return Collections.emptyList();
    }

    @Override
    public void registerExecutionMiddleware(ExecutionPoint point, IAgentMiddleware middleware) {
        throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_HOOK_REGISTRY_MIDDLEWARE_NOT_SUPPORTED);
    }
}
