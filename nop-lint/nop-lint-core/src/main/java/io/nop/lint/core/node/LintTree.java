package io.nop.lint.core.node;

import io.nop.treesitter.TSTree;

/**
 * Owner facade of one parsed source tree: the entry point from which
 * {@link LintNode} handles are derived. Wraps an immutable backend tree
 * together with the source bytes the node text slices come from.
 *
 * <p>Two construction paths exist: the tree-sitter path wraps a backend
 * {@link TSTree}; the facade path ({@link #ofFacade(LintNode, byte[])})
 * carries a pre-built node facade from a non-tree-sitter parser — the XNode
 * XML path (design 01 §1/§3.5) — with no backend tree behind it. Both are
 * indistinguishable downstream: the kind filter, matchers, suppression
 * scanner, and stats consume the {@link LintNode} surface only.</p>
 */
public final class LintTree {

    private final TSTree tree;
    private final LintNode root;
    private final byte[] facadeSource;

    private LintTree(TSTree tree) {
        this.tree = tree;
        // one cache per tree: the cache is an instance field, so a dropped
        // tree takes its cache with it — no static/GC-root path exists
        // (plan 09 review F1 adjudication)
        this.root = new TreeSitterLintNode(tree, tree.rootNode(), new TreeCache());
        this.facadeSource = null;
    }

    private LintTree(LintNode facadeRoot, byte[] source) {
        this.tree = null;
        this.root = facadeRoot;
        this.facadeSource = source;
    }

    /**
     * Wraps a backend parse tree. The tree must come from the backend this
     * facade implementation supports (tree-sitter); a null tree is rejected.
     */
    public static LintTree of(TSTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("tree must not be null");
        }
        return new LintTree(tree);
    }

    /**
     * Wraps a pre-built node facade from a non-tree-sitter parser (the XNode
     * XML path) together with the source bytes the node ranges slice. A null
     * root or source is rejected.
     */
    public static LintTree ofFacade(LintNode root, byte[] source) {
        if (root == null) {
            throw new IllegalArgumentException("facade root must not be null");
        }
        if (source == null) {
            throw new IllegalArgumentException("facade source must not be null");
        }
        return new LintTree(root, source);
    }

    /**
     * The root node of the tree.
     */
    public LintNode root() {
        return root;
    }

    /**
     * The backend tree this facade wraps, or null when this is a facade tree
     * from a non-tree-sitter parser (the XML path) — there is no backend tree
     * behind it. Incremental parsing is unavailable on facade trees; the XML
     * binding rejects it fail-closed.
     */
    public TSTree tree() {
        return tree;
    }

    /**
     * The UTF-8 source bytes this tree was parsed from (the slices
     * {@link LintNode#text()} reads from, and the input the xscript layer's
     * byte-to-line/column conversion needs).
     */
    public byte[] source() {
        return facadeSource != null ? facadeSource : tree.source();
    }
}
