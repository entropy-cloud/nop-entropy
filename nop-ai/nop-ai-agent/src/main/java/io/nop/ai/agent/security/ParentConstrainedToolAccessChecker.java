package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.NopAiAgentException;

import java.util.Objects;

/**
 * Enforcement wrapper that intersects a parent agent's effective (clamped)
 * allowed tool set with the sub-agent's own tool access pipeline.
 *
 * <p>When a parent constraint ({@link ParentPermissionConstraint}) is present,
 * any tool NOT in the parent's effective allowed set is <b>denied</b>
 * (fail-closed) with a reason that explicitly identifies "parent permission
 * constraint" — before the wrapped checker is ever consulted. When the
 * requested tool IS in the parent's set, the check delegates to the wrapped
 * checker (the sub-agent's own rules still apply on top). When no constraint
 * is present (single-agent execution), the wrapper is a no-op pass-through —
 * backward compatible with all existing engine construction paths.
 *
 * <p>Design contract (security-and-permissions.md §4.4):
 * "工具权限 = 父权限 ∩ 子配置". "未明确授权的提升行为一律拒绝".
 *
 * <p>This wrapper is applied at executor-resolution time in
 * {@link io.nop.ai.agent.engine.DefaultAgentEngine} — it does NOT mutate the
 * engine's own {@code toolAccessChecker} field. The wrapping is scoped to the
 * sub-agent execution only.
 */
public final class ParentConstrainedToolAccessChecker implements IToolAccessChecker {

    private final ParentPermissionConstraint constraint;
    private final IToolAccessChecker delegate;

    /**
     * @param constraint the parent permission constraint; must not be null
     * @param delegate   the wrapped (sub-agent's own) tool access checker;
     *                   must not be null
     */
    public ParentConstrainedToolAccessChecker(ParentPermissionConstraint constraint, IToolAccessChecker delegate) {
        if (constraint == null) {
            throw new NopAiAgentException(
                    "ParentConstrainedToolAccessChecker: constraint must not be null");
        }
        if (delegate == null) {
            throw new NopAiAgentException(
                    "ParentConstrainedToolAccessChecker: delegate checker must not be null");
        }
        this.constraint = constraint;
        this.delegate = delegate;
    }

    public ParentPermissionConstraint getConstraint() {
        return constraint;
    }

    public IToolAccessChecker getDelegate() {
        return delegate;
    }

    @Override
    public ToolAccessResult checkAccess(String toolName, AgentExecutionContext ctx) {
        Objects.requireNonNull(constraint, "parent permission constraint must not be null");
        if (!constraint.allows(toolName)) {
            String parentAgent = constraint.getParentAgentName();
            return ToolAccessResult.deny(
                    "denied by parent permission constraint: tool '" + toolName
                            + "' not in parent agent '" + parentAgent + "' allowed set");
        }
        return delegate.checkAccess(toolName, ctx);
    }
}
