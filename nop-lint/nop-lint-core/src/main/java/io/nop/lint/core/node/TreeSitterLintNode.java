package io.nop.lint.core.node;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.cursor.TSTreeCursor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tree-sitter backed {@link LintNode}: a thin, allocation-light wrapper over
 * the backend's immutable node handle plus the owning tree.
 *
 * <p>Contract notes:</p>
 * <ul>
 * <li><strong>Handle derivation is unique</strong>: child handles always come
 * from a node-rooted cursor's {@code currentNode()} and the root handle from
 * {@code rootNode()}, so the backend record's alias component — part of its
 * value identity — is set consistently and equals/hashCode are stable.</li>
 * <li><strong>Bounded enumeration</strong>: children lists are built from a
 * cursor rooted at this node; sibling navigation never bubbles past the
 * node's own subtree.</li>
 * <li><strong>Text is sliced lazily</strong> from the tree's source bytes and
 * cached; the parsed tree is immutable so the cache cannot go stale.</li>
 * </ul>
 */
final class TreeSitterLintNode implements LintNode {

    private final TSTree tree;
    private final TSNode node;
    private volatile String text;

    TreeSitterLintNode(TSTree tree, TSNode node) {
        this.tree = tree;
        this.node = node;
    }

    @Override
    public String kind() {
        return node.type();
    }

    @Override
    public int kindId() {
        return node.effectiveSymbol();
    }

    @Override
    public boolean isNamed() {
        return node.named();
    }

    @Override
    public boolean isExtra() {
        return node.isExtra();
    }

    @Override
    public boolean isMissing() {
        return tree.arena().isMissing(node.id());
    }

    @Override
    public SourceRange range() {
        return new SourceRange(node.startByte(), node.endByte());
    }

    @Override
    public String text() {
        String result = text;
        if (result == null) {
            byte[] source = tree.source();
            result = new String(source, node.startByte(), node.endByte() - node.startByte(),
                    StandardCharsets.UTF_8);
            text = result;
        }
        return result;
    }

    @Override
    public LintNode parent() {
        TSNode parent = node.parent();
        return parent == null ? null : new TreeSitterLintNode(tree, parent);
    }

    @Override
    public List<LintNode> children() {
        return collect(false);
    }

    @Override
    public List<LintNode> namedChildren() {
        return collect(true);
    }

    /**
     * Enumerates children from a cursor rooted at this node. The cursor's
     * frame stack reaches up to the tree root, so sibling motion past the
     * last child would bubble into this node's siblings; the loop is driven
     * by the backend's own child count and never advances that far.
     */
    private List<LintNode> collect(boolean named) {
        int count = named ? node.namedChildCount() : node.childCount();
        if (count == 0) {
            return List.of();
        }
        List<LintNode> result = new ArrayList<>(count);
        TSTreeCursor cursor = node.cursor();
        boolean advanced = named ? cursor.gotoFirstNamedChild() : cursor.gotoFirstChild();
        for (int i = 1; i < count && advanced; i++) {
            result.add(new TreeSitterLintNode(tree, cursor.currentNode()));
            advanced = named ? cursor.gotoNextNamedChild() : cursor.gotoNextSibling();
        }
        if (advanced) {
            result.add(new TreeSitterLintNode(tree, cursor.currentNode()));
        }
        return List.copyOf(result);
    }

    @Override
    public LintNode childByField(String fieldName) {
        TSTreeCursor cursor = node.cursor();
        if (!cursor.gotoChildByFieldName(fieldName)) {
            return null;
        }
        return new TreeSitterLintNode(tree, cursor.currentNode());
    }

    @Override
    public NodeIterator iterator() {
        return new NodeIterator(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TreeSitterLintNode other)) {
            return false;
        }
        return node.equals(other.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node);
    }

    @Override
    public String toString() {
        return kind() + range();
    }
}
