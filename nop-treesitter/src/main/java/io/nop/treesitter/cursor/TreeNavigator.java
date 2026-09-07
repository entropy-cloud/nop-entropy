package io.nop.treesitter.cursor;

import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.subtree.Subtree;
import io.nop.treesitter.subtree.SubtreeArena;

/**
 * Read-only navigation helpers over an immutable {@link TSTree} snapshot.
 *
 * <p>A subtree stores at most {@link Subtree#MAX_CHILDREN} inline children; when
 * the parser reduces a node with more children the overflow is stored in an
 * invisible container chain (reserved container symbol =
 * {@code symbolCount + aliasCount}). This navigator exposes the <em>flattened</em>
 * child sequence: chain containers are transparent, so the children of a node
 * (including chain contents) behave exactly as if they were direct children —
 * the same model {@code TSTree.toSExpression} renders with.</p>
 *
 * <p>All methods are arena-index based: navigation never allocates per-node heap
 * objects and never materialises a second tree representation.</p>
 */
final class TreeNavigator {

    private final TSTree tree;
    private final Language language;
    private final SubtreeArena arena;

    TreeNavigator(TSTree tree) {
        this.tree = tree;
        this.language = tree.language();
        this.arena = tree.arena();
    }

    TSTree tree() {
        return tree;
    }

    Language language() {
        return language;
    }

    SubtreeArena arena() {
        return arena;
    }

    Subtree node(int id) {
        return arena.get(id);
    }

    int symbolOf(int id) {
        return arena.get(id).symbol();
    }

    /**
     * The reduce production id stored in the node's {@code state} slot, used to
     * resolve alias sequences and field maps (0 = no production).
     */
    int productionId(int nodeId) {
        return arena.get(nodeId).state();
    }

    /**
     * True when {@code id} is an invisible container-chain node (an arena-only
     * artifact, never a grammar node).
     */
    boolean isChain(int id) {
        return arena.get(id).symbol() == language.symbolCount() + language.aliasCount();
    }

    /**
     * The logical parent of {@code nodeId}: its direct arena parent with any
     * container-chain nodes crossed transparently, or {@link Subtree#NO_ID} when
     * the node is a tree root.
     */
    int logicalParent(int nodeId) {
        int parent = arena.parentOf(nodeId);
        while (parent != Subtree.NO_ID && isChain(parent)) {
            parent = arena.parentOf(parent);
        }
        return parent;
    }

    /**
     * The flattened child index of {@code childId} within {@code nodeId}, or -1
     * when the node is not a child.
     */
    int flattenedIndexOf(int nodeId, int childId) {
        int count = flattenedChildCount(nodeId);
        for (int i = 0; i < count; i++) {
            if (childRef(nodeId, i).id() == childId) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Number of children of {@code nodeId} with chain containers expanded
     * transparently.
     */
    int flattenedChildCount(int nodeId) {
        Subtree n = arena.get(nodeId);
        int count = n.childCount();
        if (count == 0) {
            return 0;
        }
        int last = n.child(count - 1);
        if (!isChain(last)) {
            return count;
        }
        return count - 1 + chainWidth(last);
    }

    private int chainWidth(int containerId) {
        Subtree c = arena.get(containerId);
        int count = c.childCount();
        if (count == 0) {
            return 0;
        }
        int last = c.child(count - 1);
        if (!isChain(last)) {
            return count;
        }
        return count - 1 + chainWidth(last);
    }

    /**
     * The {@code index}-th child of {@code nodeId} in flattened order, or null
     * when the index is out of range. The returned ref carries the child's
     * structural index (number of preceding non-extra children within
     * {@code nodeId}) and the alias symbol the child renders with (from
     * {@code nodeId}'s production), matching the C runtime's frame setup.
     */
    ChildRef childRef(int nodeId, int index) {
        int[] state = new int[]{0, 0};
        return scan(nodeId, nodeId, index, state);
    }

    /**
     * Recursive scan over a node's children, expanding chain containers while
     * carrying the flattened position ({@code state[0]}) and structural index
     * ({@code state[1]}) across container boundaries. {@code contextId} is the
     * logical parent whose production provides alias/field data.
     */
    private ChildRef scan(int contextId, int nodeId, int target, int[] state) {
        Subtree n = arena.get(nodeId);
        for (int i = 0; i < n.childCount(); i++) {
            int childId = n.child(i);
            if (isChain(childId)) {
                ChildRef found = scan(contextId, childId, target, state);
                if (found != null) {
                    return found;
                }
                continue;
            }
            if (state[0] == target) {
                boolean extra = arena.get(childId).extra() != 0;
                int alias = 0;
                if (!extra) {
                    int productionId = productionId(contextId);
                    if (productionId != 0) {
                        alias = language.aliasAt(productionId, state[1]);
                    }
                }
                return new ChildRef(childId, state[1], extra, alias);
            }
            if (arena.get(childId).extra() == 0) {
                state[1]++;
            }
            state[0]++;
        }
        return null;
    }

    /**
     * Number of visible children of {@code nodeId}, counting through hidden
     * descendants the way the C runtime's stored {@code visible_child_count}
     * does: a non-extra child with an alias counts as one, else a child with a
     * visible symbol counts as one, else its own visible count is added.
     */
    int visibleChildCount(int nodeId) {
        int count = 0;
        int n = flattenedChildCount(nodeId);
        for (int i = 0; i < n; i++) {
            ChildRef ref = childRef(nodeId, i);
            int symbol = symbolOf(ref.id());
            if (!ref.extra() && ref.alias() != 0) {
                count++;
            } else if (language.symbolVisible(symbol)) {
                count++;
            } else {
                count += visibleChildCount(ref.id());
            }
        }
        return count;
    }

    /**
     * Number of named children of {@code nodeId}, mirroring the C runtime's
     * {@code named_child_count} (alias-aware, hidden descent included).
     */
    int namedChildCount(int nodeId) {
        int count = 0;
        int n = flattenedChildCount(nodeId);
        for (int i = 0; i < n; i++) {
            ChildRef ref = childRef(nodeId, i);
            int symbol = symbolOf(ref.id());
            if (!ref.extra() && ref.alias() != 0) {
                if (language.symbolNamed(ref.alias())) {
                    count++;
                }
            } else if (language.symbolVisible(symbol)) {
                if (language.symbolNamed(symbol)) {
                    count++;
                }
            } else {
                count += namedChildCount(ref.id());
            }
        }
        return count;
    }

    /**
     * Cursor step visibility (C {@code ts_tree_cursor_child_iterator_next}):
     * the raw symbol is visible, or a non-extra child carries an alias at its
     * structural index.
     */
    boolean stepVisible(ChildRef ref) {
        if (ref.extra()) {
            return language.symbolVisible(symbolOf(ref.id()));
        }
        return ref.alias() != 0 || language.symbolVisible(symbolOf(ref.id()));
    }

    /**
     * Named relevance (C {@code ts_node__is_relevant} with
     * {@code include_anonymous=false}): a named alias, or a visible-and-named
     * raw symbol.
     */
    boolean namedRelevant(ChildRef ref) {
        if (ref.alias() != 0) {
            return language.symbolNamed(ref.alias());
        }
        int symbol = symbolOf(ref.id());
        return language.symbolVisible(symbol) && language.symbolNamed(symbol);
    }

    /**
     * The alias symbol the node {@code nodeId} renders with inside its logical
     * parent {@code contextId} at {@code structuralIndex}; 0 when not aliased.
     */
    int aliasAt(int contextId, int structuralIndex) {
        int productionId = productionId(contextId);
        if (productionId == 0) {
            return 0;
        }
        return language.aliasAt(productionId, structuralIndex);
    }

    record ChildRef(int id, int structuralIndex, boolean extra, int alias) {
    }
}