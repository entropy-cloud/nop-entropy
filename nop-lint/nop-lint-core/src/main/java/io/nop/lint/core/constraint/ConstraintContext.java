package io.nop.lint.core.constraint;

import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.pattern.MetaVarEnv;

/**
 * The evaluation input of one match's constraints (design 01 §5
 * MatchContext, adjudicated form — plan 2026-09-22-1045-3): the matched node
 * and the capture environment the matcher produced. The source text is
 * reached through the node facade's slicing ({@link LintNode#text()}), so no
 * separate source handle rides along.
 */
public record ConstraintContext(LintNode matchNode, MetaVarEnv env) {

    public ConstraintContext {
        if (matchNode == null)
            throw new IllegalArgumentException("matchNode must not be null");
        if (env == null)
            throw new IllegalArgumentException("env must not be null");
    }

    /**
     * The source text of the node bound to {@code name}, or null when the
     * name is unbound in this match's environment.
     */
    public String captureText(String name) {
        LintNode node = env.getCapture(name);
        return node == null ? null : node.text();
    }
}
