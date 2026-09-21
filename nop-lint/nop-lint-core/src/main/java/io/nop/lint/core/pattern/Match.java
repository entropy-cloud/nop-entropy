package io.nop.lint.core.pattern;

import io.nop.lint.core.node.LintNode;

/**
 * One pattern match: the candidate node that matched the pattern root plus
 * the capture environment it produced. Environments are per-match and never
 * shared between matches.
 */
public record Match(LintNode node, MetaVarEnv env) {
}
