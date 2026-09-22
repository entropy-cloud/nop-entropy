package io.nop.lint.core.pattern;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintNode;

import java.util.Map;

/**
 * The {@code matches} reference to a shared util rule (design 04 §6,
 * roadmap item 24): delegates to the util's compiled node matcher, evaluated
 * on the same candidate node. The util reference graph was validated
 * cycle-free at parse time, so expansion terminates; the per-evaluation
 * depth cap is the belt-and-suspenders backstop that turns a pathological
 * reference DAG into a loud failure instead of a stack overflow.
 */
public final class ReferentMatcher implements NodeMatcher {

    /**
     * The expansion depth a single match evaluation may reach through
     * chained {@code matches} references (a cycle-free DAG unrolls to a
     * finite tree; this cap bounds the pathological-exponential corner).
     */
    public static final int MAX_EXPANSION_DEPTH = 64;

    private static final ThreadLocal<int[]> EXPANSION_DEPTH =
            ThreadLocal.withInitial(() -> new int[1]);

    private final String ruleId;
    private final String utilId;
    private final Map<String, NodeMatcher> utils;

    public ReferentMatcher(String ruleId, String utilId, Map<String, NodeMatcher> utils) {
        this.ruleId = ruleId;
        this.utilId = utilId;
        // the registry map is captured by reference on purpose: compilation
        // of mutually-referencing utils fills it in declaration order, and
        // resolution only happens once matching starts (the map is complete
        // and unmodifiable by then)
        this.utils = utils;
    }

    @Override
    public boolean matches(LintNode node, MetaVarEnv env) {
        NodeMatcher target = utils.get(utilId);
        if (target == null) {
            // the parse-time reference validation makes this unreachable;
            // reaching it means the registry and the validation disagree
            throw new NopLintException("Rule '" + ruleId + "' references util '" + utilId
                    + "' which the compiled registry does not contain (invariant broken; "
                    + "fail-closed)");
        }
        int[] depth = EXPANSION_DEPTH.get();
        depth[0]++;
        try {
            if (depth[0] > MAX_EXPANSION_DEPTH) {
                throw new NopLintException("Rule '" + ruleId + "' exceeded the matches expansion "
                        + "depth cap of " + MAX_EXPANSION_DEPTH + " at util '" + utilId
                        + "' (the reference graph should have been rejected as circular at "
                        + "parse time; fail-closed)");
            }
            return target.matches(node, env);
        } finally {
            depth[0]--;
        }
    }
}
