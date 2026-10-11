package io.nop.lint.core.pattern;

/**
 * A node of a compiled source pattern: the shape the matcher kernel
 * (meta-var matchers, lockstep child matcher, strictness) consumes.
 *
 * <p>Three variants exist: {@link InternalNode} for named constructs,
 * {@link TerminalNode} for unnamed tokens, and {@link MetaVarNode} for
 * meta-variable occurrences.</p>
 */
public sealed interface PatternNode permits InternalNode, TerminalNode, MetaVarNode {
}
