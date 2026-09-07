package io.nop.treesitter;

import io.nop.treesitter.cursor.TSTreeCursor;

/**
 * Immutable value handle for a single parse-tree node: an owning {@link TSTree}
 * snapshot plus the node's arena id (and the alias symbol the node renders with
 * inside its parent, 0 when none).
 *
 * <p>Like the C {@code TSNode}, this is a tiny stack-friendly value — it holds
 * only ints and references, and navigation allocates no per-node heap objects.
 * All navigation entry points delegate to a fresh {@link TSTreeCursor} over the
 * same tree snapshot, so there is exactly one navigation implementation.</p>
 *
 * <p>Node facts ({@link #type()}, {@link #named()}, {@link #isVisible()},
 * {@link #isExtra()}) follow the C runtime's effective-symbol semantics: an
 * aliased node reports the alias symbol's metadata.</p>
 */
public record TSNode(TSTree tree, int id, int aliasSymbol) {

    /**
     * The node's raw grammar symbol id (the arena-stored symbol, before alias
     * resolution).
     */
    public int symbol() {
        return tree.arena().get(id).symbol();
    }

    /**
     * The symbol the node renders as: its alias symbol when aliased, else its
     * own symbol.
     */
    public int effectiveSymbol() {
        return aliasSymbol != 0 ? aliasSymbol : symbol();
    }

    /**
     * The node type name (alias-aware), e.g. {@code pair} or {@code method_declaration}.
     */
    public String type() {
        return tree.language().symbolName(effectiveSymbol());
    }

    /**
     * True for named nodes (C {@code ts_node_is_named}): a named alias, or a
     * visible-and-named raw symbol.
     */
    public boolean named() {
        if (aliasSymbol != 0) {
            return tree.language().symbolNamed(aliasSymbol);
        }
        int sym = symbol();
        return tree.language().symbolVisible(sym) && tree.language().symbolNamed(sym);
    }

    /**
     * True when the node is visible in the tree (C {@code ts_node_is_visible}):
     * the raw symbol is visible, or the node carries an alias.
     */
    public boolean isVisible() {
        return aliasSymbol != 0 || tree.language().symbolVisible(symbol());
    }

    /**
     * True when the node is an extra token (e.g. a comment).
     */
    public boolean isExtra() {
        return tree.arena().get(id).extra() != 0;
    }

    /**
     * Byte offset of the node's first character in the source (the arena's
     * padding column).
     */
    public int startByte() {
        return tree.arena().get(id).padding();
    }

    /**
     * A cursor rooted at this node, sharing the same tree snapshot.
     */
    public TSTreeCursor cursor() {
        return new TSTreeCursor(this);
    }

    /**
     * The nearest visible ancestor of this node, or null at the tree root.
     */
    public TSNode parent() {
        TSTreeCursor cursor = cursor();
        return cursor.gotoParent() ? cursor.currentNode() : null;
    }

    /**
     * The next visible sibling of this node, or null when none.
     */
    public TSNode nextSibling() {
        TSTreeCursor cursor = cursor();
        return cursor.gotoNextSibling() ? cursor.currentNode() : null;
    }

    /**
     * Number of visible children (C {@code ts_node_child_count}).
     */
    public int childCount() {
        return cursor().childCount();
    }

    /**
     * Number of named children (C {@code ts_node_named_child_count}).
     */
    public int namedChildCount() {
        return cursor().namedChildCount();
    }

    /**
     * The {@code index}-th visible child, or null when out of range.
     */
    public TSNode child(int index) {
        TSTreeCursor cursor = cursor();
        return cursor.gotoChild(index) ? cursor.currentNode() : null;
    }

    /**
     * The {@code index}-th named child, or null when out of range.
     */
    public TSNode namedChild(int index) {
        TSTreeCursor cursor = cursor();
        return cursor.gotoNamedChild(index) ? cursor.currentNode() : null;
    }
}