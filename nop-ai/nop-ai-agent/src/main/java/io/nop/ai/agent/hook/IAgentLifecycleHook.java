package io.nop.ai.agent.hook;

public interface IAgentLifecycleHook {
    HookResult onEvent(HookContext ctx);
}
