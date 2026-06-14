package io.nop.ai.agent.security;

import io.nop.ai.agent.model.PathRuleModel;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable value object carrying the parent agent's <b>effective (clamped)</b>
 * allowed tool set, <b>effective (clamped)</b> allowed path roots, and
 * <b>effective (clamped)</b> allowed path rules, plus audit metadata,
 * propagated from a parent agent to a sub-agent when the parent invokes the
 * sub-agent via {@code call-agent}.
 *
 * <p>The constraint carries three dimensions of the parent agent's
 * <b>effective</b> security scope:
 * <ul>
 *   <li><b>allowedTools</b> — the set of tool names the parent can actually
 *       invoke in the current execution (NOT merely the names it declares in
 *       its DSL). For a top-level agent, this equals its declared set
 *       ({@code AgentModel.getTools()}). For a nested agent, this is the
 *       intersection of the incoming parent constraint and the agent's own
 *       declared tool set.</li>
 *   <li><b>allowedPathRoots</b> — the set of normalized absolute directory
 *       roots the parent is confined to for file access (derived from the
 *       parent agent's {@code workDir}). {@code null} means ABSENT (no
 *       declared path scope → no path confinement, backward compatible with
 *       plan 169). A non-null Set (including an empty set) means PRESENT:
 *       confinement is active, a path outside these roots is denied
 *       (fail-closed). PRESENT with an empty set = deny all paths (maximum
 *       restriction, e.g. when clamping collapses to nothing).</li>
 *   <li><b>allowedPathRules</b> — the accumulated chain of glob path-rules
 *       the parent is confined to (derived from the parent agent's
 *       {@code <path-rules>} DSL declarations). {@code null} means ABSENT (no
 *       declared path-rules → no rule confinement). A non-null List (including
 *       an empty list) means PRESENT: the rules are evaluated by
 *       {@link ParentConstrainedPathAccessChecker} with deny-wins cross-level
 *       semantics (any parent DENY rule matching → denied).</li>
 * </ul>
 *
 * <p>Propagating the effective (clamped) sets — rather than the declared sets
 * — is what makes nested delegation safe: a middle agent B that was clamped
 * by A propagates A's constraint onward to C, so C cannot regain tools or
 * path scope that A's constraint removed from B.
 *
 * <p>Design contract (security-and-permissions.md §4.4):
 * "工具权限 = 父权限 ∩ 子配置（交集或收缩）" and
 * "文件权限 = 父权限 ∩ 子配置（交集或收缩）". The enforcement wrappers
 * {@link ParentConstrainedToolAccessChecker} and
 * {@link ParentConstrainedPathAccessChecker} intersect this constraint with
 * the sub-agent's own access pipelines using fail-closed semantics.
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
    private final Set<String> allowedPathRoots;
    private final List<PathRuleModel> allowedPathRules;
    private final String parentAgentName;
    private final String parentSessionId;

    /**
     * Backward-compatible tool-only constructor (plan 169). Delegates with
     * {@code allowedPathRoots = null} and {@code allowedPathRules = null}
     * (ABSENT — no path confinement).
     */
    public ParentPermissionConstraint(Set<String> allowedTools, String parentAgentName, String parentSessionId) {
        this(allowedTools, null, null, parentAgentName, parentSessionId);
    }

    /**
     * Constructor carrying both the tool set and the path roots (plan 170).
     * Delegates with {@code allowedPathRules = null} (ABSENT).
     */
    public ParentPermissionConstraint(Set<String> allowedTools, Set<String> allowedPathRoots,
                                      String parentAgentName, String parentSessionId) {
        this(allowedTools, allowedPathRoots, null, parentAgentName, parentSessionId);
    }

    /**
     * Full constructor carrying the tool set, path roots, AND path rules
     * (plan 174).
     *
     * @param allowedTools     the parent agent's effective (clamped) allowed
     *                         tool name set; never null. An empty set means the
     *                         parent allows no tools (maximum restriction).
     * @param allowedPathRoots the parent agent's effective (clamped) allowed
     *                         path roots; {@code null} means ABSENT. A non-null
     *                         Set (including empty) means PRESENT.
     * @param allowedPathRules the parent agent's effective (clamped) accumulated
     *                         path-rule chain; {@code null} means ABSENT. A
     *                         non-null List (including empty) means PRESENT.
     * @param parentAgentName  the parent agent's name, for audit traceability
     * @param parentSessionId  the parent agent's session ID, for audit traceability
     */
    public ParentPermissionConstraint(Set<String> allowedTools, Set<String> allowedPathRoots,
                                      List<PathRuleModel> allowedPathRules,
                                      String parentAgentName, String parentSessionId) {
        if (allowedTools == null) {
            throw new IllegalArgumentException(
                    "ParentPermissionConstraint: allowedTools must not be null (use empty set for max restriction)");
        }
        this.allowedTools = Set.copyOf(allowedTools);
        this.allowedPathRoots = allowedPathRoots == null ? null : Set.copyOf(allowedPathRoots);
        this.allowedPathRules = allowedPathRules == null ? null : List.copyOf(allowedPathRules);
        this.parentAgentName = parentAgentName;
        this.parentSessionId = parentSessionId;
    }

    /**
     * The parent agent's effective (clamped) allowed tool name set. Immutable.
     */
    public Set<String> getAllowedTools() {
        return Collections.unmodifiableSet(allowedTools);
    }

    /**
     * The parent agent's effective (clamped) allowed path roots (normalized
     * absolute directory roots). Immutable.
     *
     * @return {@code null} (ABSENT) or a non-null Set (PRESENT, possibly empty)
     */
    public Set<String> getAllowedPathRoots() {
        return allowedPathRoots == null ? null : Collections.unmodifiableSet(allowedPathRoots);
    }

    /**
     * Whether the path roots are PRESENT (non-null, confinement active).
     */
    public boolean hasPathRoots() {
        return allowedPathRoots != null;
    }

    /**
     * The parent agent's effective (clamped) accumulated path-rule chain.
     * Immutable.
     *
     * @return {@code null} (ABSENT) when the parent has no declared path-rules
     *         (no rule confinement); a non-null List (PRESENT) when rule
     *         confinement is active. The list is the accumulated chain of
     *         incoming parent rules + own declared rules.
     */
    public List<PathRuleModel> getAllowedPathRules() {
        return allowedPathRules == null ? null : Collections.unmodifiableList(allowedPathRules);
    }

    /**
     * Whether the path rules are PRESENT (non-null, rule confinement active).
     */
    public boolean hasPathRules() {
        return allowedPathRules != null;
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
                && Objects.equals(allowedPathRoots, that.allowedPathRoots)
                && Objects.equals(allowedPathRules, that.allowedPathRules)
                && Objects.equals(parentAgentName, that.parentAgentName)
                && Objects.equals(parentSessionId, that.parentSessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowedTools, allowedPathRoots, allowedPathRules, parentAgentName, parentSessionId);
    }

    @Override
    public String toString() {
        return "ParentPermissionConstraint{"
                + "allowedTools=" + allowedTools
                + ", allowedPathRoots=" + allowedPathRoots
                + ", allowedPathRules=" + (allowedPathRules == null ? "null" : allowedPathRules.size() + " rules")
                + ", parentAgentName='" + parentAgentName + '\''
                + ", parentSessionId='" + parentSessionId + '\''
                + '}';
    }
}
