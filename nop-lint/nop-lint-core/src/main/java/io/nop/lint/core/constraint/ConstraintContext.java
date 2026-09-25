package io.nop.lint.core.constraint;

import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.pattern.MetaVarEnv;
import io.nop.lint.core.semantic.TypeQuerySupport;

/**
 * The evaluation input of one match's constraints (design 01 §5
 * MatchContext, adjudicated form — plan 2026-09-22-1045-3): the matched node
 * and the capture environment the matcher produced. The source text is
 * reached through the node facade's slicing ({@link LintNode#text()}), so no
 * separate source handle rides along.
 *
 * <p>The L2 query support rides here since plan 10 (compile-reuse): it is
 * per-file run state, injected at evaluation time by the runner — never
 * captured at rule-compile time, so one compiled rule can serve any number
 * of files. Null in runs whose gate keeps type-consuming rules out.</p>
 */
public record ConstraintContext(LintNode matchNode, MetaVarEnv env, TypeQuerySupport typeQueries) {

    /**
     * The L2-less form: constraints that never query types evaluate
     * identically; a typeOf constraint reaching a null support fails closed
     * (the guarded-fail contract).
     */
    public ConstraintContext(LintNode matchNode, MetaVarEnv env) {
        this(matchNode, env, null);
    }

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
