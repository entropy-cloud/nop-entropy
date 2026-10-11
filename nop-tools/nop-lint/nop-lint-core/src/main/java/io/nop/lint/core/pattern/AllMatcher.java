package io.nop.lint.core.pattern;

import io.nop.lint.core.node.LintNode;

import java.util.List;

/**
 * The conjunctive composite ({@code all}, design 04 §6): every child matcher
 * must match the same candidate node. Children match into a scratchpad
 * clone of the environment; the combined bindings commit only when all
 * children succeeded, so a partial conjunction never leaks captures.
 */
public final class AllMatcher implements NodeMatcher {

    private final List<NodeMatcher> children;

    public AllMatcher(List<NodeMatcher> children) {
        if (children == null || children.isEmpty()) {
            throw new IllegalArgumentException("all matcher requires at least one child");
        }
        this.children = List.copyOf(children);
    }

    public List<NodeMatcher> children() {
        return children;
    }

    @Override
    public boolean matches(LintNode node, MetaVarEnv env) {
        MetaVarEnv scratchpad = env.clone();
        for (NodeMatcher child : children) {
            if (!child.matches(node, scratchpad)) {
                return false;
            }
        }
        env.adopt(scratchpad);
        return true;
    }
}
