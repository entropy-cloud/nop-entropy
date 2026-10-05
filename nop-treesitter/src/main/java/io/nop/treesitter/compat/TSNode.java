package io.nop.treesitter.compat;

import io.nop.treesitter.language.Language;
import io.nop.treesitter.subtree.Subtree;

/**
 * Migration bridge for the JNI-embedded tree-sitter API: mirrors the
 * {@code org.treesitter.TSNode} accessor surface over this module's pure-Java
 * node. A missing node (field lookup miss, out-of-range child) is the shared
 * {@link #NULL} instance with {@code isNull() == true}, matching the C
 * runtime's null-object convention.
 */
public final class TSNode {

    /**
     * The shared "no node" instance ({@code isNull()} is {@code true}).
     */
    public static final TSNode NULL = new TSNode(null, null);

    /**
     * Structural node identity ({@code org.treesitter.TSNode#eq}): two nodes
     * are the same when they wrap the same arena slot of the same tree (the
     * underlying io.nop node is a record keyed by tree + id + alias).
     */
    public static boolean eq(TSNode a, TSNode b) {
        if (a == null || b == null || a.node == null || b.node == null) {
            return a == b;
        }
        return a.node.equals(b.node);
    }

    private final TSTree tree;
    private final io.nop.treesitter.TSNode node;

    TSNode(TSTree tree, io.nop.treesitter.TSNode node) {
        this.tree = tree;
        this.node = node;
    }

    TSTree tree() {
        return tree;
    }

    io.nop.treesitter.TSNode real() {
        return node;
    }

    public boolean isNull() {
        return node == null;
    }

    public String getType() {
        return node.type();
    }

    public int getSymbol() {
        return node.effectiveSymbol();
    }

    public boolean isNamed() {
        return node.named();
    }

    public boolean isMissing() {
        return tree.arena().isMissing(node.id());
    }

    public boolean isExtra() {
        return node.isExtra();
    }

    /**
     * True when the subtree contains an ERROR / MISSING node anywhere
     * (C {@code ts_node_has_error}).
     *
     * <p>Delegates to the primary {@link io.nop.treesitter.TSNode#hasError()} so
     * the two API surfaces cannot drift: this class exists so migrating off the
     * JNI bindings is an import change, not a behaviour change. The shared
     * implementation also counts zero-width MISSING nodes, which the previous
     * local copy here did not — a missing token is a syntax error in the C
     * runtime's accounting, and the fail-closed reading is the correct one.</p>
     */
    public boolean hasError() {
        return node.hasError();
    }

    public int getStartByte() {
        return node.startByte();
    }

    public int getEndByte() {
        return node.endByte();
    }

    public TSPoint getStartPoint() {
        return point(node.startByte());
    }

    public TSPoint getEndPoint() {
        return point(node.endByte());
    }

    public int getChildCount() {
        return node.childCount();
    }

    public TSNode getChild(int index) {
        return wrapOrNull(node.child(index));
    }

    public int getNamedChildCount() {
        return node.namedChildCount();
    }

    public TSNode getNamedChild(int index) {
        return wrapOrNull(node.namedChild(index));
    }

    public TSNode getParent() {
        return wrapOrNull(node.parent());
    }

    public TSNode getNextSibling() {
        return wrapOrNull(node.nextSibling());
    }

    /**
     * The first child sitting in the given field slot of this node's production
     * (C {@code ts_node_child_by_field_name}), or {@link #NULL}.
     *
     * <p>Delegates to the primary
     * {@link io.nop.treesitter.TSNode#childByFieldName(String)} for the same
     * no-drift reason as {@link #hasError()}.</p>
     */
    public TSNode getChildByFieldName(String fieldName) {
        if (node == null) {
            return NULL;
        }
        return wrapOrNull(node.childByFieldName(fieldName));
    }

    /**
     * The subtree rendered in the C {@code ts_node_string} form: named visible
     * nodes only, space-separated s-expression.
     */
    @Override
    public String toString() {
        if (node == null) {
            return "(NULL)";
        }
        StringBuilder sb = new StringBuilder();
        renderNamed(node.id(), sb);
        return sb.toString();
    }

    private void renderNamed(int id, StringBuilder sb) {
        Subtree subtree = tree.arena().get(id);
        Language language = tree.language();
        boolean visible = subtree.symbol() < language.symbolCount() + language.aliasCount()
                && language.symbolVisible(subtree.symbol()) && language.symbolNamed(subtree.symbol());
        if (!visible) {
            for (int i = 0; i < subtree.childCount(); i++) {
                renderNamed(subtree.child(i), sb);
            }
            return;
        }
        sb.append('(').append(language.symbolName(subtree.symbol()));
        for (int i = 0; i < subtree.childCount(); i++) {
            sb.append(' ');
            renderNamed(subtree.child(i), sb);
        }
        sb.append(')');
    }

    private TSPoint point(int byteOffset) {
        byte[] source = tree.source();
        int row = 0;
        int lineStart = 0;
        for (int i = 0; i < byteOffset && i < source.length; i++) {
            if (source[i] == '\n') {
                row++;
                lineStart = i + 1;
            }
        }
        return new TSPoint(row, byteOffset - lineStart);
    }

    private TSNode wrapOrNull(io.nop.treesitter.TSNode inner) {
        return inner == null ? NULL : new TSNode(tree, inner);
    }
}
