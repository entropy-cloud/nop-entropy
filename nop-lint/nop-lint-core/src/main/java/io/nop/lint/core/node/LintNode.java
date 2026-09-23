package io.nop.lint.core.node;

import java.util.List;

/**
 * Language-agnostic node facade over a parsed source tree: the single node
 * access surface the pattern compiler and matcher kernel use. Concrete
 * implementations wrap a parser backend (tree-sitter); callers never touch
 * backend types.
 *
 * <p>Node identity is value identity: two {@code LintNode} handles referring
 * to the same tree position compare equal (see the implementation's
 * equals/hashCode contract). Children are the backend's <em>visible</em>
 * children in source order; extras such as comments participate in that
 * order and are skipped only by explicit matcher policy.</p>
 */
public interface LintNode extends Iterable<LintNode> {

    /**
     * The node's kind name, alias-aware (an aliased node reports the alias
     * kind, e.g. Java's {@code type_identifier} in generic contexts).
     */
    String kind();

    /**
     * The node's kind as an int (the backend's effective symbol). Comparing
     * kind ids is the fast path for kind matching; resolve names to ids via
     * the language binding, never by interning {@link #kind()} strings.
     */
    int kindId();

    /**
     * True for named nodes (C {@code ts_node_is_named}); unnamed punctuation
     * and keyword tokens are the trivial nodes strictness policies skip.
     */
    boolean isNamed();

    /**
     * True when the node is an extra token (a comment in most grammars).
     */
    boolean isExtra();

    /**
     * True when the backend inserted this node as an error-recovery
     * placeholder for missing source.
     */
    boolean isMissing();

    /**
     * The node's byte range in the parsed source.
     */
    SourceRange range();

    /**
     * The node's source text: the UTF-8 slice of the parsed source covered by
     * {@link #range()}.
     */
    String text();

    /**
     * The value of the named attribute, or null when the node kind carries
     * no attributes or the attribute is absent (the XNode facade's
     * attribute dimension, roadmap item 27/29 consumers via the xscript
     * node surface). Attribute-less backends return null for every name.
     */
    default String attrValue(String name) {
        return null;
    }

    /**
     * The nearest visible ancestor, or null at the tree root.
     */
    LintNode parent();

    /**
     * The visible children in source order; empty (never null) for leaves.
     */
    List<LintNode> children();

    /**
     * The named children in source order; empty (never null) for leaves.
     */
    List<LintNode> namedChildren();

    /**
     * The first child occupying the given grammar field slot in this node's
     * production, or null when the field is unknown to the language or empty
     * in this production.
     */
    LintNode childByField(String fieldName);

    /**
     * Pre-order depth-first traversal of this subtree, starting with this
     * node; each node is visited exactly once.
     */
    @Override
    NodeIterator iterator();
}
