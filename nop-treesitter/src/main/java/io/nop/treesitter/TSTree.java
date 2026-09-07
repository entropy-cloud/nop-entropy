package io.nop.treesitter;

import io.nop.treesitter.language.Language;
import io.nop.treesitter.subtree.Subtree;
import io.nop.treesitter.subtree.SubtreeArena;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable parse tree: an owned snapshot of the parse-time arena plus the
 * root subtree id.
 *
 * <p>The wrapper owns a private {@link SubtreeArena} that was populated by
 * copying the reachable nodes of the parse arena; after {@link #snapshot} the
 * parse arena can be mutated or discarded freely. Navigation happens through
 * {@link #root()} and the arena API; {@link #toSExpression()} renders the tree
 * in the canonical upstream corpus form — only named and visible nodes appear,
 * and invisible / unnamed nodes are flattened through transparently.</p>
 */
public final class TSTree {

    private final Language language;
    private final SubtreeArena arena;
    private final int rootId;

    private TSTree(Language language, SubtreeArena arena, int rootId) {
        this.language = language;
        this.arena = arena;
        this.rootId = rootId;
    }

    /**
     * Deep-copies the tree rooted at {@code rootId} from {@code parseArena} into
     * a fresh arena owned by the returned tree.
     */
    public static TSTree snapshot(Language language, SubtreeArena parseArena, int rootId) {
        SubtreeArena own = new SubtreeArena();
        int copyRoot = copy(parseArena, own, rootId);
        return new TSTree(language, own, copyRoot);
    }

    private static int copy(SubtreeArena src, SubtreeArena dst, int id) {
        Subtree node = src.get(id);
        int[] children = new int[node.childCount()];
        for (int i = 0; i < children.length; i++) {
            children[i] = copy(src, dst, node.child(i));
        }
        return dst.allocate(node.state(), node.symbol(), node.extra(), node.padding(), children);
    }

    public Language language() {
        return language;
    }

    /**
     * Id of the root subtree in the tree's own arena.
     */
    public int root() {
        return rootId;
    }

    /**
     * The tree's arena. Read-only by convention: the tree snapshot must not be
     * mutated while it is in use.
     */
    public SubtreeArena arena() {
        return arena;
    }

    /**
     * Canonical s-expression in the upstream corpus format: named visible nodes
     * only, two-space indentation per level, invisible / unnamed nodes flattened
     * through.
     */
    public String toSExpression() {
        StringBuilder sb = new StringBuilder();
        writeNode(rootId, 0, sb);
        return sb.toString();
    }

    private void writeNode(int id, int depth, StringBuilder sb) {
        Subtree node = arena.get(id);
        int symbol = node.symbol();
        if (!isVisible(symbol)) {
            for (int i = 0; i < node.childCount(); i++) {
                writeNode(node.child(i), depth, sb);
            }
            return;
        }

        List<Integer> visibleChildren = new ArrayList<>();
        collectVisible(node, visibleChildren);
        if (visibleChildren.isEmpty()) {
            sb.append('(').append(language.symbolName(symbol)).append(')');
            return;
        }
        sb.append('(').append(language.symbolName(symbol));
        for (int child : visibleChildren) {
            sb.append('\n');
            for (int i = 0; i <= depth; i++) {
                sb.append("  ");
            }
            writeNode(child, depth + 1, sb);
        }
        sb.append(')');
    }

    private void collectVisible(Subtree node, List<Integer> out) {
        for (int i = 0; i < node.childCount(); i++) {
            int child = node.child(i);
            Subtree childNode = arena.get(child);
            if (isVisible(childNode.symbol())) {
                out.add(child);
            } else {
                collectVisible(childNode, out);
            }
        }
    }

    private boolean isVisible(int symbol) {
        return symbol < language.symbolCount()
                && language.symbolVisible(symbol)
                && language.symbolNamed(symbol);
    }
}