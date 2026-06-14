package io.nop.ai.agent.security;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable value object carrying the parent agent's <b>effective (clamped)</b>
 * allowed tool set plus audit metadata, propagated from a parent agent to a
 * sub-agent when the parent invokes the sub-agent via {@code call-agent}.
 *
 * <p>The constraint is the parent agent's <b>effective</b> tool set — i.e. the
 * set of tool names the parent can actually invoke in the current execution —
 * NOT merely the names it declares in its DSL. For a top-level agent (no
 * incoming parent constraint), the effective set equals its declared set
 * ({@code AgentModel.getTools()}). For a nested agent, the effective set is the
 * intersection of the incoming parent constraint and the agent's own declared
 * tool set. Propagating the effective (clamped) set — rather than the declared
 * set — is what makes nested delegation safe: a middle agent B that was
 * clamped by A propagates A's constraint onward to C, so C cannot regain tools
 * that A's constraint removed from B.
 *
 * <p>Design contract (security-and-permissions.md §4.4):
 * "工具权限 = 父权限 ∩ 子配置（交集或收缩）". The enforcement wrapper
 * {@link ParentConstrainedToolAccessChecker} intersects this constraint with
 * the sub-agent's own tool access pipeline using fail-closed semantics.
 *
 * <p>This object is propagated through {@link io.nop.ai.agent.engine.AgentMessageRequest#getMetadata()}
 * under the well-known key {@link #METADATA_KEY}. It is an immutable snapshot
 * captured at call-agent invocation time.
 */
public final class ParentPermissionConstraint {

    /**
     * Well-known metadata key under which a {@link ParentPermissionConstraint}
     * is propagated from {@code call-agent} to the sub-agent execution via
     * {@link io.nop.ai.agent.engine.AgentMessageRequest#getMetadata()}.
     */
    public static final String METADATA_KEY = "parentPermissionConstraint";

    private final Set<String> allowedTools;
    private final String parentAgentName;
    private final String parentSessionId;

    /**
     * @param allowedTools    the parent agent's effective (clamped) allowed
     *                        tool name set; never null. An empty set means the
     *                        parent allows no tools — the sub-agent will be
     *                        denied every tool (maximum restriction).
     * @param parentAgentName the parent agent's name, for audit traceability
     * @param parentSessionId the parent agent's session ID, for audit traceability
     */
    public ParentPermissionConstraint(Set<String> allowedTools, String parentAgentName, String parentSessionId) {
        if (allowedTools == null) {
            throw new IllegalArgumentException(
                    "ParentPermissionConstraint: allowedTools must not be null (use empty set for max restriction)");
        }
        this.allowedTools = Set.copyOf(allowedTools);
        this.parentAgentName = parentAgentName;
        this.parentSessionId = parentSessionId;
    }

    /**
     * The parent agent's effective (clamped) allowed tool name set. Immutable.
     * A tool not in this set is denied for the sub-agent regardless of the
     * sub-agent's own configuration (fail-closed).
     */
    public Set<String> getAllowedTools() {
        return Collections.unmodifiableSet(allowedTools);
    }

    public String getParentAgentName() {
        return parentAgentName;
    }

    public String getParentSessionId() {
        return parentSessionId;
    }

    /**
     * Whether the given tool name is permitted by this parent constraint.
     */
    public boolean allows(String toolName) {
        return toolName != null && allowedTools.contains(toolName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParentPermissionConstraint that = (ParentPermissionConstraint) o;
        return Objects.equals(allowedTools, that.allowedTools)
                && Objects.equals(parentAgentName, that.parentAgentName)
                && Objects.equals(parentSessionId, that.parentSessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowedTools, parentAgentName, parentSessionId);
    }

    @Override
    public String toString() {
        return "ParentPermissionConstraint{"
                + "allowedTools=" + allowedTools
                + ", parentAgentName='" + parentAgentName + '\''
                + ", parentSessionId='" + parentSessionId + '\''
                + '}';
    }
}
