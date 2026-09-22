package io.nop.lint.core.pattern;

import io.nop.lint.core.node.LintNode;

/**
 * The negation composite ({@code not}, design 04 §6): the candidate must
 * <em>not</em> match the inner matcher. The inner match runs in a probe
 * clone that is always discarded — the inner matcher's bindings can never
 * leak into the real environment (xor semantics: inner matched → false,
 * inner unmatched → true).
 */
public final class NotMatcher implements NodeMatcher {

    private final NodeMatcher inner;

    public NotMatcher(NodeMatcher inner) {
        if (inner == null) {
            throw new IllegalArgumentException("not matcher requires an inner matcher");
        }
        this.inner = inner;
    }

    public NodeMatcher inner() {
        return inner;
    }

    @Override
    public boolean matches(LintNode node, MetaVarEnv env) {
        MetaVarEnv probe = env.clone();
        return !inner.matches(node, probe);
    }
}
