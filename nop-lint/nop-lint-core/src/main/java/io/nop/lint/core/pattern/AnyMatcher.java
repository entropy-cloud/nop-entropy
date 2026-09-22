package io.nop.lint.core.pattern;

import io.nop.lint.core.node.LintNode;

import java.util.List;

/**
 * The disjunctive node matcher (design 04 §6): branches are tried in order,
 * each in a probe clone of the caller's environment, and the first success
 * commits its bindings — the same env-isolation contract the {@code all} /
 * {@code not} matchers follow, so a failed branch can never leak captures
 * into the enclosing match.
 */
public final class AnyMatcher implements NodeMatcher {

    private final List<NodeMatcher> branches;

    public AnyMatcher(List<NodeMatcher> branches) {
        if (branches.isEmpty()) {
            throw new IllegalArgumentException("AnyMatcher needs at least one branch");
        }
        this.branches = List.copyOf(branches);
    }

    @Override
    public boolean matches(LintNode node, MetaVarEnv env) {
        for (NodeMatcher branch : branches) {
            MetaVarEnv probe = env.clone();
            if (branch.matches(node, probe)) {
                env.adopt(probe);
                return true;
            }
        }
        return false;
    }
}
