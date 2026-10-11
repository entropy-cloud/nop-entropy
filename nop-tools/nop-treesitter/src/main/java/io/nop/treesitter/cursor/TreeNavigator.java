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
            if (!locateChild(nodeId, i)) {
                return -1;
            }
            if (foundId == childId) {
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

    // Primitive (allocation-free) child location. One locate in flight per
    // navigator; recursive callers must copy foundId() to a local before
    // descending (scan state advances monotonically, so no save/restore).

    private int scanState0;
    private int scanState1;
    private int foundId;
    private int foundStructuralIndex;
    private int foundExtra;
    private int foundAlias;

    boolean locateChild(int contextId, int target) {
        scanState0 = 0;
        scanState1 = 0;
        return locateInto(contextId, contextId, target);
    }

    private int locateIters;
    private int locateCallNode = -1;

    private boolean locateInto(int contextId, int nodeId, int target) {
        if (locateCallNode != nodeId) {
            locateIters = 0;
            locateCallNode = nodeId;
        }
        if (++locateIters > 5000) {
            throw new IllegalStateException("child location revisits node " + nodeId
                    + " more than 5000 times (arena child graph is cyclic?)");
        }
        Subtree n = arena.get(nodeId);
        for (int i = 0; i < n.childCount(); i++) {
            int childId = n.child(i);
            if (isChain(childId)) {
                if (locateInto(contextId, childId, target)) {
                    return true;
                }
                continue;
            }
            if (scanState0 == target) {
                boolean extra = arena.get(childId).extra() != 0;
                int alias = 0;
                if (!extra) {
                    int productionId = productionId(contextId);
                    if (productionId != 0) {
                        alias = language.aliasAt(productionId, scanState1);
                    }
                }
                foundId = childId;
                foundStructuralIndex = scanState1;
                foundExtra = extra ? 1 : 0;
                foundAlias = alias;
                return true;
            }
            if (arena.get(childId).extra() == 0) {
                scanState1++;
            }
            scanState0++;
        }
        return false;
    }

    int foundId() {
        return foundId;
    }

    int foundStructuralIndex() {
        return foundStructuralIndex;
    }

    boolean foundExtra() {
        return foundExtra != 0;
    }

    int foundAlias() {
        return foundAlias;
    }

    boolean foundStepVisible() {
        if (foundExtra != 0) {
            return language.symbolVisibleOrBuiltin(symbolOf(foundId));
        }
        return foundAlias != 0 || language.symbolVisibleOrBuiltin(symbolOf(foundId));
    }

    boolean foundNamedRelevant() {
        if (foundAlias != 0) {
            return language.symbolNamedOrBuiltin(foundAlias);
        }
        int symbol = symbolOf(foundId);
        return language.symbolVisibleOrBuiltin(symbol) && language.symbolNamedOrBuiltin(symbol);
    }

    /**
     * Number of visible children of {@code nodeId}, counting through hidden
     * descendants the way the C runtime's stored {@code visible_child_count}
     * does: a non-extra child with an alias counts as one, else a child with a
     * visible symbol counts as one, else its own visible count is added.
     */
    int visibleChildCount(int nodeId) {
        int count = 0;
        int k = 0;
        while (locateChild(nodeId, k)) {
            int id = foundId;
            if (foundExtra == 0 && foundAlias != 0) {
                count++;
            } else if (language.symbolVisibleOrBuiltin(symbolOf(id))) {
                count++;
            } else {
                count += visibleChildCount(id);
            }
            k++;
        }
        return count;
    }

    /**
     * Number of named children of {@code nodeId}, mirroring the C runtime's
     * {@code named_child_count} (alias-aware, hidden descent included).
     */
    int namedChildCount(int nodeId) {
        int count = 0;
        int k = 0;
        while (locateChild(nodeId, k)) {
            int id = foundId;
            if (foundExtra == 0 && foundAlias != 0) {
                if (language.symbolNamedOrBuiltin(foundAlias)) {
                    count++;
                }
            } else if (language.symbolVisibleOrBuiltin(symbolOf(id))) {
                if (language.symbolNamedOrBuiltin(symbolOf(id))) {
                    count++;
                }
            } else {
                count += namedChildCount(id);
            }
            k++;
        }
        return count;
    }
}