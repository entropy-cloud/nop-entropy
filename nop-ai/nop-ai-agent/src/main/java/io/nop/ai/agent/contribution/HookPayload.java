package io.nop.ai.agent.contribution;

import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.IAgentLifecycleHook;

import java.util.Objects;

/**
 * Typed payload for a {@link ContributionType#HOOK} contribution. Carries the
 * target {@link AgentLifecyclePoint} and the {@link IAgentLifecycleHook}
 * instance that the engine registers into {@code IHookRegistry} at assembly
 * time (design {@code nop-ai-agent-hook-skill-engine.md} §8, plan 217 裁定 5).
 *
 * <p>Both fields are mandatory. The engine's assembly-time HOOK resolution
 * skips (with a WARN) any HOOK contribution whose payload is not an instance
 * of this class — fail-visible, not a silent no-op (plan 217 Minimum Rules #24).
 */
public final class HookPayload {

    private final AgentLifecyclePoint point;
    private final IAgentLifecycleHook hook;

    public HookPayload(AgentLifecyclePoint point, IAgentLifecycleHook hook) {
        if (point == null) {
            throw new IllegalArgumentException("HookPayload: point must not be null");
        }
        if (hook == null) {
            throw new IllegalArgumentException("HookPayload: hook must not be null");
        }
        this.point = point;
        this.hook = hook;
    }

    public AgentLifecyclePoint getPoint() {
        return point;
    }

    public IAgentLifecycleHook getHook() {
        return hook;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HookPayload)) return false;
        HookPayload that = (HookPayload) o;
        return point == that.point && Objects.equals(hook, that.hook);
    }

    @Override
    public int hashCode() {
        return Objects.hash(point, hook);
    }

    @Override
    public String toString() {
        return "HookPayload{point=" + point + ", hook=" + hook + '}';
    }
}
