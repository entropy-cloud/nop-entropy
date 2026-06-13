package io.nop.ai.agent.hook;

import java.util.List;

public interface IHookRegistry {

    List<IAgentLifecycleHook> getHooks(AgentLifecyclePoint point, String agentName);

    void register(AgentLifecyclePoint point, IAgentLifecycleHook hook);

    static IHookRegistry empty() {
        return NoOpHookRegistry.INSTANCE;
    }
}
