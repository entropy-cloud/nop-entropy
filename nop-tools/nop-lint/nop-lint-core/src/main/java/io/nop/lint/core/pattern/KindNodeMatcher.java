package io.nop.lint.core.pattern;

import io.nop.lint.core.node.LintNode;

/**
 * Matches a candidate by its kind id alone (the {@code kind:} matcher as a
 * node-level composition unit). Kind ids come from the language binding's
 * resolution space, never from interning kind-name strings.
 */
public final class KindNodeMatcher implements NodeMatcher {

    private final int kindId;

    public KindNodeMatcher(int kindId) {
        this.kindId = kindId;
    }

    public int kindId() {
        return kindId;
    }

    @Override
    public boolean matches(LintNode node, MetaVarEnv env) {
        return node.kindId() == kindId;
    }
}
