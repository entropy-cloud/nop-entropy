package io.nop.lint.core.node;

import io.nop.treesitter.TSTree;

/**
 * Owner facade of one parsed source tree: the entry point from which
 * {@link LintNode} handles are derived. Wraps an immutable backend tree
 * together with the source bytes the node text slices come from.
 */
public final class LintTree {

    private final TSTree tree;
    private final LintNode root;

    private LintTree(TSTree tree) {
        this.tree = tree;
        this.root = new TreeSitterLintNode(tree, tree.rootNode());
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
     * The root node of the tree.
     */
    public LintNode root() {
        return root;
    }

    /**
     * The backend tree this facade wraps.
     */
    public TSTree tree() {
        return tree;
    }
}
