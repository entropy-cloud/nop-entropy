package io.nop.treesitter.compat;

/**
 * Migration bridge for the JNI-embedded tree-sitter API: the parse result.
 * {@code delete()} is a no-op here — the tree is an immutable Java object
 * reclaimed by the GC (no native memory to release).
 */
public final class TSTree {

    private final io.nop.treesitter.TSTree tree;

    TSTree(io.nop.treesitter.TSTree tree) {
        this.tree = tree;
    }

    io.nop.treesitter.TSTree tree() {
        return tree;
    }

    io.nop.treesitter.subtree.SubtreeArena arena() {
        return tree.arena();
    }

    io.nop.treesitter.language.Language language() {
        return tree.language();
    }

    byte[] source() {
        return tree.source();
    }

    public io.nop.treesitter.language.Language getLanguage() {
        return tree.language();
    }

    public TSNode getRootNode() {
        return new TSNode(this, tree.rootNode());
    }

    /**
     * Source-compatibility only: the tree is immutable and GC-managed, so this
     * returns {@code this} instead of a native copy.
     */
    public TSTree copy() {
        return this;
    }

    public void delete() {
        // no native resources; GC reclaims
    }
}
