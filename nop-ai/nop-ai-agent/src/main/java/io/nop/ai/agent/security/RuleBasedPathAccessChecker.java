package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.PathRuleModel;
import io.nop.commons.path.AntPathMatcher;
import io.nop.commons.path.IPathMatcher;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Path access checker enforcing an agent's own declared glob path-rules using
 * first-match-wins semantics (design §4.3: "glob 模式，从上到下匹配，第一条命中生效").
 *
 * <p>The agent declares an ordered list of {@link PathRuleModel} rules
 * ({@code <path-rules>} in {@code agent.xdef}), each specifying a glob pattern
 * and an access decision ({@code allow} or {@code deny}). On each
 * {@link #checkAccess(String, AgentExecutionContext)} call:
 * <ol>
 *   <li>The requested path is normalized and (when relative) resolved against
 *       the agent's declared {@code workDir} (mirroring
 *       {@link ParentConstrainedPathAccessChecker} resolution).</li>
 *   <li>Rules are evaluated top-to-bottom; the first rule whose glob pattern
 *       matches the resolved path decides:
 *     <ul>
 *       <li><b>DENY</b> → denied with an explicit reason identifying the matched
 *           rule pattern and a {@code matchedRule} token
 *           {@value #MATCHED_RULE_DENY}.</li>
 *       <li><b>ALLOW</b> → delegates to the wrapped checker (the global
 *           deny-list still applies on top — an agent cannot ALLOW a path the
 *           global deny-list denies, e.g. {@code ~/.ssh/**}).</li>
 *     </ul></li>
 *   <li>No rule matches → delegates to the wrapped checker (the agent does not
 *       restrict this path beyond the global deny-list).</li>
 * </ol>
 *
 * <p><b>Path-form handling</b>: rule patterns and the resolved path must share
 * the same form (both absolute or both relative) for a match — this is enforced
 * by {@link AntPathMatcher}'s built-in form guard. A relative literal pattern
 * like {@code src/&#42;&#42;} only matches relative resolved paths (when workDir is
 * ABSENT); an absolute pattern like {@code /workspace/&#42;&#42;} matches absolute
 * paths. As a usability refinement, {@code &#42;&#42;}-leading patterns (which are
 * intended to be workDir-independent, per design §4.3) are form-adjusted: a
 * pattern such as <code>&#42;&#42;&#47;secrets&#47;&#42;&#42;</code> is treated
 * as if it had a leading slash when matching against an absolute resolved path.
 *
 * <p>This checker wraps another {@link IPathAccessChecker} (typically the global
 * {@link DefaultPathAccessChecker}). An empty rule list makes this checker a
 * pure pass-through (no restriction beyond the wrapped checker).
 *
 * <p>Design contract (security-and-permissions.md §4.3): per-agent rules are
 * additive restrictions ON TOP of the global deny-list, not a replacement.
 */
public final class RuleBasedPathAccessChecker implements IPathAccessChecker {

    /**
     * Stable rule token tagging denials produced by a DENY rule within the
     * agent's own rule-set.
     */
    public static final String MATCHED_RULE_DENY = "agent_path_rule_deny";

    private static final IPathMatcher MATCHER = new AntPathMatcher();

    private final List<PathRuleModel> rules;
    private final IPathAccessChecker delegate;

    /**
     * @param rules    the agent's own ordered glob path-rules; must not be null
     *                 (use an empty list for "no rules"). An empty list makes
     *                 this checker a pure pass-through.
     * @param delegate the wrapped checker (typically the global
     *                 {@link DefaultPathAccessChecker}) consulted on ALLOW and
     *                 no-match; must not be null
     */
    public RuleBasedPathAccessChecker(List<PathRuleModel> rules, IPathAccessChecker delegate) {
        if (rules == null) {
            throw new IllegalArgumentException(
                    "RuleBasedPathAccessChecker: rules must not be null (use empty list for no rules)");
        }
        if (delegate == null) {
            throw new IllegalArgumentException(
                    "RuleBasedPathAccessChecker: delegate checker must not be null");
        }
        this.rules = Collections.unmodifiableList(rules);
        this.delegate = delegate;
    }

    public List<PathRuleModel> getRules() {
        return rules;
    }

    public IPathAccessChecker getDelegate() {
        return delegate;
    }

    @Override
    public PathAccessResult checkAccess(String path, AgentExecutionContext ctx) {
        // null/blank path → delegate to wrapped checker (same as
        // DefaultPathAccessChecker which allows null/blank paths)
        if (path == null || path.trim().isEmpty()) {
            return delegate.checkAccess(path, ctx);
        }

        // Empty rule list → pure pass-through (no restriction)
        if (rules.isEmpty()) {
            return delegate.checkAccess(path, ctx);
        }

        String resolved = resolveAndNormalize(path, ctx);

        // Normalization failed → delegate to wrapped checker, which also denies
        // such paths (fail-closed at the global level). We do not invent a
        // rule-based denial here because rule patterns are glob-based, not
        // normalization-based.
        if (resolved == null) {
            return delegate.checkAccess(path, ctx);
        }

        for (PathRuleModel rule : rules) {
            String pattern = rule.getPattern();
            if (pattern == null || pattern.trim().isEmpty()) {
                continue;
            }

            // Adjust the pattern form so that **-leading patterns (which are
            // intended to be workDir-independent) match absolute resolved paths.
            // E.g. "**/secrets/**" → "/**/secrets/**" to match "/workspace/secrets/key".
            // Non-** patterns rely on AntPathMatcher's built-in form guard: a
            // relative pattern + absolute path (or vice versa) → no match.
            String matchPattern = adjustPatternForm(pattern, resolved);

            if (MATCHER.match(matchPattern, resolved)) {
                PathAccessDecision decision = rule.getAccessDecision();
                if (decision == PathAccessDecision.DENY) {
                    return PathAccessResult.deny(
                            "denied by agent path-rule: pattern '" + pattern
                                    + "' (access=deny) matched path '" + path + "' (resolved: '" + resolved + "')",
                            MATCHED_RULE_DENY);
                }
                // ALLOW → delegate to wrapped checker (global deny-list still applies)
                return delegate.checkAccess(path, ctx);
            }
        }

        // No rule matched → delegate to wrapped checker
        return delegate.checkAccess(path, ctx);
    }

    /**
     * Resolve a relative path against the agent's declared {@code workDir}
     * (from the execution context's agent model), then normalize using the
     * shared {@link DefaultPathAccessChecker#normalizePathStatic(String)}
     * algorithm. Tilde paths and absolute paths are normalized as-is. Mirrors
     * {@link ParentConstrainedPathAccessChecker} resolution so that within-agent
     * rules and cross-level parent rules see the same resolved form.
     */
    private String resolveAndNormalize(String path, AgentExecutionContext ctx) {
        String p = path.replace("\\", "/");

        boolean isTilde = p.startsWith("~");
        boolean isAbsolute = p.startsWith("/")
                || (p.length() >= 2 && p.charAt(1) == ':');

        if (!isTilde && !isAbsolute) {
            String workDir = resolveWorkDir(ctx);
            if (workDir != null && !workDir.trim().isEmpty()) {
                String wd = workDir.replace("\\", "/");
                p = wd.endsWith("/") ? wd + p : wd + "/" + p;
            }
        }

        return DefaultPathAccessChecker.normalizePathStatic(p);
    }

    private String resolveWorkDir(AgentExecutionContext ctx) {
        if (ctx == null || ctx.getAgentModel() == null) {
            return null;
        }
        return ctx.getAgentModel().getWorkDir();
    }

    /**
     * Adjust the pattern form so that {@code &#42;&#42;}-leading patterns
     * (intended to be workDir-independent) match absolute resolved paths. A
     * pattern starting with {@code &#42;&#42;} is prepended with {@code /} when
     * the resolved path is absolute, so that e.g.
     * <code>&#42;&#42;&#47;secrets&#47;&#42;&#42;</code> becomes
     * <code>&#47;&#42;&#42;&#47;secrets&#47;&#42;&#42;</code> to match
     * {@code /workspace/secrets/key}.
     *
     * <p>Non-{@code &#42;&#42;} patterns are returned unchanged: they rely on
     * {@link AntPathMatcher}'s built-in form guard (a relative pattern combined
     * with an absolute path returns no match). This means a relative literal
     * pattern like {@code src/&#42;&#42;} only matches relative resolved paths
     * (when workDir is ABSENT), while an absolute pattern like
     * {@code /workspace/src/&#42;&#42;} matches absolute paths.
     */
    private static String adjustPatternForm(String pattern, String resolvedPath) {
        boolean pathAbsolute = resolvedPath.startsWith("/")
                || (resolvedPath.length() >= 2 && resolvedPath.charAt(1) == ':');
        if (pathAbsolute && !pattern.startsWith("/") && pattern.startsWith("**")) {
            return "/" + pattern;
        }
        return pattern;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuleBasedPathAccessChecker that = (RuleBasedPathAccessChecker) o;
        return Objects.equals(rules, that.rules)
                && Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rules, delegate);
    }

    @Override
    public String toString() {
        return "RuleBasedPathAccessChecker{rules=" + rules.size()
                + ", delegate=" + delegate + '}';
    }
}
