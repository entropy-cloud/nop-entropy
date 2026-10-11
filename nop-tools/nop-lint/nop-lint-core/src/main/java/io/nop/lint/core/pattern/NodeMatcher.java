package io.nop.lint.core.pattern;

import io.nop.lint.core.node.LintNode;

/**
 * A node-level structural matcher (design 04 §5–§6): the composition unit
 * the relational operators ({@code inside}/{@code has}/{@code follows}/
 * {@code precedes}) and the composite operators ({@code all}/{@code not})
 * are built from.
 *
 * <p>Environment contract: a {@code false} return leaves {@code env}
 * untouched — implementations must probe into a cloned environment and only
 * commit bindings on success, so a failed partial match can never leak
 * captures into the enclosing match.</p>
 */
public interface NodeMatcher {

    /**
     * Tests this matcher against one candidate node, binding captures into
     * {@code env} only on success.
     */
    boolean matches(LintNode node, MetaVarEnv env);
}
