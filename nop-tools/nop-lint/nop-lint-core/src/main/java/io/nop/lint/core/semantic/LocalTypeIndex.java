package io.nop.lint.core.semantic;

import io.nop.lint.core.node.LintNode;

/**
 * The single-file local type index (the "in-file quick type marking" tier):
 * a per-tree inference pass that binds declared names — local variables,
 * fields, method parameters, same-file method return types — to their
 * declared or trivially inferred types. It needs no project root, no
 * external resolver and no cross-file information, so a language binding
 * that can walk its own declarations serves it on every run: this is the
 * {@code LTYPE} capability's substrate (distinct from {@code L2}, which
 * resolves through a project-wired {@link TypeResolver} and degrades when
 * the production CLI wires none).
 *
 * <p>Answers are exact when the binding exists in the file and null when
 * they do not — never a guess. Consumers treat null as "unknown" and
 * adjudicate (typically: don't flag, or fall back to a declared name-based
 * face).</p>
 */
public interface LocalTypeIndex {

    /**
     * The type text bound to the expression node, or null when this index
     * cannot answer (unknown name, unsupported expression shape). The text
     * is whatever the file declares — a simple name ({@code String},
     * {@code Pattern}) or a qualified one; consumers compare by simple-name
     * suffix to stay robust against import form.
     *
     * @param receiver the expression node whose static type is queried
     *                 (typically an identifier or field access captured by
     *                 a pattern meta-variable)
     */
    String typeOf(LintNode receiver);
}
