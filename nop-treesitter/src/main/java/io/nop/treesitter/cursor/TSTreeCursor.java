package io.nop.treesitter.cursor;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.subtree.Subtree;

/**
 * Stateful, O(1)-per-step tree navigation over an immutable {@link TSTree}
 * snapshot (C {@code TSTreeCursor} semantics, adapted to the int-indexed
 * {@code SubtreeArena}).
 *
 * <p>The cursor keeps a stack of frames held in parallel int arrays (reused
 * across {@link #resetTo} calls), so navigation allocates nothing per step;
 * child location goes through {@link TreeNavigator#locateChild}, the
 * allocation-free primitive scan. Invisible container-chain nodes (the arena's
 * >{@code MAX_CHILDREN} overflow artifact) are fully transparent: their
 * children iterate exactly as if they were direct children of the logical
 * parent, and the logical parent's production keeps providing the alias
 * sequence and field map.</p>
 *
 * <p>Motion semantics match the C runtime:</p>
 * <ul>
 * <li>{@link #gotoFirstChild} / {@link #gotoNextSibling} move among <em>visible</em>
 * children (a child is visible when its raw symbol is visible or it carries an
 * alias); invisible children with visible descendants are crossed transparently
 * and stay on the stack as hidden frames, so {@link #gotoParent} climbs back to
 * the nearest visible ancestor.</li>
 * <li>{@link #gotoFirstNamedChild} / {@link #gotoNextNamedChild} do the same for
 * <em>named</em> children (visible-and-named, or a named alias).</li>
 * <li>{@link #gotoChild} / {@link #gotoNamedChild} address the
 * {@code index}-th visible / named child in C {@code ts_node_child} order.</li>
 * <li>Extras (comments) are ordinary children of unfiltered iteration; they are
 * never excluded and never carry a field name.</li>
 * </ul>
 *
 * <p>No silent skip: motion past the end of the sibling list, above the root, or
 * into a childless node returns {@code false} and leaves the cursor exactly
 * where it was; {@link #currentFieldId()} returns 0 and
 * {@link #currentFieldName()} returns null when the current node occupies no
 * field slot.</p>
 */
public final class TSTreeCursor {

    private TreeNavigator nav;
    private TSTree tree;
    private int[] fNode = new int[8];
    private int[] fCtx = new int[8];
    private int[] fIdx = new int[8];
    private int[] fStruct = new int[8];
    private int[] fAlias = new int[8];
    private int[] ancestors = new int[16];
    private int size;

    /**
     * Creates a cursor rooted at the tree's root node.
     */
    public TSTreeCursor(TSTree tree) {
        this(new TSNode(tree, tree.root(), 0));
    }

    /**
     * Creates a cursor rooted at {@code node}.
     *
     * <p>The ancestor chain is reconstructed from the arena's parent links
     * (invisible container chains crossed transparently), so sibling and parent
     * motion work even when the cursor is rooted at a non-root node — the same
     * stack the corresponding descent would have built.</p>
     */
    public TSTreeCursor(TSNode node) {
        resetTo(node);
    }

    /**
     * Releases the cursor's references to its tree, keeping the internal
     * buffers for reuse. Pooled cursors (e.g. {@link TSNode}'s scratch cursor)
     * call this when returned to their pool, so a long-lived thread does not
     * pin the last parsed tree's arena and source bytes.
     *
     * <p>Contract: after this call the cursor must be re-anchored with
     * {@link #resetTo(TSNode)} before any navigation; calling any other method
     * on a released cursor is undefined (the internal navigator is absent).
     */
    public void release() {
        tree = null;
        nav = null;
        size = 0;
    }

    /**
     * Re-points the cursor at {@code node}, reusing all internal buffers. The
     * navigator is rebuilt only when the cursor moves to a different tree
     * (each tree owns its arena). Cursor state from before the call is
     * discarded exactly as in a fresh construction.
     */
    public void resetTo(TSNode node) {
        if (nav == null || node.tree() != tree) {
            tree = node.tree();
            nav = new TreeNavigator(tree);
        }
        size = 0;
        int count = 0;
        for (int id = node.id(); ; id = nav.logicalParent(id)) {
            if (count == ancestors.length) {
                int[] grown = new int[ancestors.length * 2];
                System.arraycopy(ancestors, 0, grown, 0, ancestors.length);
                ancestors = grown;
            }
            ancestors[count++] = id;
            if (count > 5000) {
                throw new IllegalStateException("ancestor climb does not reach the root at node "
                        + node.id());
            }
            if (nav.logicalParent(id) == Subtree.NO_ID) {
                break;
            }
        }
        for (int k = count - 1; k >= 0; k--) {
            int currentId = ancestors[k];
            if (k == count - 1) {
                int topAlias = k == 0 ? node.aliasSymbol() : 0;
                pushFrame(currentId, Subtree.NO_ID, 0, 0, topAlias);
            } else {
                int parentId = ancestors[k + 1];
                int index = nav.flattenedIndexOf(parentId, currentId);
                // flattenedIndexOf ran locateChild for the matching index, so
                // the navigator's found* fields carry this child's data.
                pushFrame(currentId, parentId, index, nav.foundStructuralIndex(), nav.foundAlias());
            }
        }
    }

    /**
     * The node the cursor is currently positioned at.
     */
    public TSNode currentNode() {
        return new TSNode(nav.tree(), fNode[size - 1], fAlias[size - 1]);
    }

    /**
     * Moves to the first visible child of the current node; invisible children
     * with visible descendants are crossed transparently. Returns false when
     * the current node has no visible children (cursor unchanged).
     */
    public boolean gotoFirstChild() {
        while (true) {
            int topNode = fNode[size - 1];
            int found = findNext(topNode, -1, false);
            if (found < 0) {
                return false;
            }
            pushFrame(nav.foundId(), topNode, found, nav.foundStructuralIndex(), nav.foundAlias());
            if (nav.foundStepVisible()) {
                return true;
            }
        }
    }

    /**
     * Moves to the next visible sibling of the current node, bubbling up through
     * invisible intermediate parents the way the C runtime does: when the current
     * node is the last relevant child of an invisible parent, the search continues
     * at the parent's own sibling level. Invisible siblings with visible
     * descendants are crossed transparently. Returns false when the cursor is at
     * the root or no visible sibling follows (cursor unchanged).
     */
    public boolean gotoNextSibling() {
        if (size < 2) {
            return false;
        }
        int initialSize = size;
        while (size > 1) {
            size--;
            int poppedCtx = fCtx[size];
            int poppedIdx = fIdx[size];
            int found = findNext(poppedCtx, poppedIdx, false);
            if (found >= 0) {
                pushFrame(nav.foundId(), poppedCtx, found, nav.foundStructuralIndex(), nav.foundAlias());
                if (nav.foundStepVisible()) {
                    return true;
                }
                gotoFirstChild();
                return true;
            }
        }
        size = initialSize;
        return false;
    }

    /**
     * Moves to the nearest visible ancestor of the current node, popping hidden
     * frames crossed during descent. Returns false at the cursor root (cursor
     * unchanged).
     */
    public boolean gotoParent() {
        for (int i = size - 2; i >= 0; i--) {
            if (entryVisible(i)) {
                size = i + 1;
                return true;
            }
        }
        return false;
    }

    /**
     * Moves to the first named child of the current node. Returns false when
     * the current node has no named children (cursor unchanged).
     */
    public boolean gotoFirstNamedChild() {
        while (true) {
            int topNode = fNode[size - 1];
            int found = findNext(topNode, -1, true);
            if (found < 0) {
                return false;
            }
            pushFrame(nav.foundId(), topNode, found, nav.foundStructuralIndex(), nav.foundAlias());
            if (nav.foundNamedRelevant()) {
                return true;
            }
        }
    }

    /**
     * Moves to the next named sibling of the current node, bubbling up through
     * invisible intermediate parents (see {@link #gotoNextSibling}). Returns
     * false when the cursor is at the root or no named sibling follows (cursor
     * unchanged).
     */
    public boolean gotoNextNamedChild() {
        if (size < 2) {
            return false;
        }
        int initialSize = size;
        while (size > 1) {
            size--;
            int poppedCtx = fCtx[size];
            int poppedIdx = fIdx[size];
            int found = findNext(poppedCtx, poppedIdx, true);
            if (found >= 0) {
                pushFrame(nav.foundId(), poppedCtx, found, nav.foundStructuralIndex(), nav.foundAlias());
                if (nav.foundNamedRelevant()) {
                    return true;
                }
                gotoFirstNamedChild();
                return true;
            }
        }
        size = initialSize;
        return false;
    }

    /**
     * Moves to the {@code index}-th visible child of the current node (C
     * {@code ts_node_child} order; hidden intermediates are crossed and left on
     * the stack as hidden frames). Returns false when the index is out of range
     * (cursor unchanged).
     */
    public boolean gotoChild(int index) {
        if (index < 0) {
            return false;
        }
        int entrySize = size;
        int context = fNode[size - 1];
        int remaining = index;
        while (true) {
            boolean descended = false;
            int count = nav.flattenedChildCount(context);
            for (int i = 0; i < count; i++) {
                if (!nav.locateChild(context, i)) {
                    size = entrySize;
                    return false;
                }
                if (nav.foundStepVisible()) {
                    if (remaining == 0) {
                        pushFrame(nav.foundId(), context, i, nav.foundStructuralIndex(), nav.foundAlias());
                        return true;
                    }
                    remaining--;
                } else {
                    int id = nav.foundId();
                    int structIndex = nav.foundStructuralIndex();
                    int alias = nav.foundAlias();
                    int grandchildCount = nav.visibleChildCount(id);
                    if (remaining < grandchildCount) {
                        pushFrame(id, context, i, structIndex, alias);
                        context = id;
                        descended = true;
                        break;
                    }
                    remaining -= grandchildCount;
                }
            }
            if (!descended) {
                size = entrySize;
                return false;
            }
        }
    }

    /**
     * Moves to the {@code index}-th named child of the current node (C
     * {@code ts_node_named_child} order). Returns false when the index is out of
     * range (cursor unchanged).
     */
    public boolean gotoNamedChild(int index) {
        if (index < 0) {
            return false;
        }
        int entrySize = size;
        int context = fNode[size - 1];
        int remaining = index;
        while (true) {
            boolean descended = false;
            int count = nav.flattenedChildCount(context);
            for (int i = 0; i < count; i++) {
                if (!nav.locateChild(context, i)) {
                    size = entrySize;
                    return false;
                }
                if (nav.foundNamedRelevant()) {
                    if (remaining == 0) {
                        pushFrame(nav.foundId(), context, i, nav.foundStructuralIndex(), nav.foundAlias());
                        return true;
                    }
                    remaining--;
                } else {
                    int id = nav.foundId();
                    int structIndex = nav.foundStructuralIndex();
                    int alias = nav.foundAlias();
                    int grandchildCount = nav.namedChildCount(id);
                    if (remaining < grandchildCount) {
                        pushFrame(id, context, i, structIndex, alias);
                        context = id;
                        descended = true;
                        break;
                    }
                    remaining -= grandchildCount;
                }
            }
            if (!descended) {
                size = entrySize;
                return false;
            }
        }
    }

    /**
     * Moves to the first child that occupies the given field slot in the current
     * node's production (C {@code ts_node_child_by_field_id}; inherited entries
     * are followed through hidden wrappers). Returns false when the current node
     * has no child under {@code fieldId} (cursor unchanged).
     */
    public boolean gotoChildByFieldId(int fieldId) {
        if (fieldId == 0) {
            return false;
        }
        return fieldSearch(fNode[size - 1], fieldId);
    }

    /**
     * Moves to the first child under the named field slot (see
     * {@link #gotoChildByFieldId}). Returns false when the field name is unknown
     * or the current node has no child under it (cursor unchanged).
     */
    public boolean gotoChildByFieldName(String name) {
        int fieldId = nav.language().fieldId(name);
        if (fieldId == 0) {
            return false;
        }
        return gotoChildByFieldId(fieldId);
    }

    /**
     * The field id of the current node within its parent, walking invisible
     * ancestors like the C runtime; 0 at the cursor root or when the node
     * occupies no field slot.
     */
    public int currentFieldId() {
        for (int i = size - 1; i > 0; i--) {
            if (i != size - 1 && entryVisible(i)) {
                break;
            }
            if (nav.node(fNode[i]).extra() != 0) {
                break;
            }
            for (Language.FieldMapEntry map : nav.language().fieldMap(nav.productionId(fCtx[i]))) {
                if (!map.inherited() && map.childIndex() == fStruct[i]) {
                    return map.fieldId();
                }
            }
        }
        return 0;
    }

    /**
     * The grammar field name of the current node within its parent, or null
     * when the node occupies no field slot.
     */
    public String currentFieldName() {
        int fieldId = currentFieldId();
        if (fieldId == 0) {
            return null;
        }
        return nav.language().fieldName(fieldId);
    }

    /**
     * Number of visible children of the current node (C {@code ts_node_child_count}).
     */
    public int childCount() {
        return nav.visibleChildCount(fNode[size - 1]);
    }

    /**
     * Number of named children of the current node (C {@code ts_node_named_child_count}).
     */
    public int namedChildCount() {
        return nav.namedChildCount(fNode[size - 1]);
    }

    /**
     * The cursor depth: number of visible entries above the root frame (0 at
     * the root), matching C {@code ts_tree_cursor_current_depth}.
     */
    public int depth() {
        int depth = 0;
        for (int i = 1; i < size; i++) {
            if (entryVisible(i)) {
                depth++;
            }
        }
        return depth;
    }

    private int findNext(int contextId, int startIndex, boolean named) {
        int count = nav.flattenedChildCount(contextId);
        for (int i = startIndex + 1; i < count; i++) {
            if (!nav.locateChild(contextId, i)) {
                return -1;
            }
            boolean relevant = named
                    ? nav.foundNamedRelevant() || nav.namedChildCount(nav.foundId()) > 0
                    : nav.foundStepVisible() || nav.visibleChildCount(nav.foundId()) > 0;
            if (relevant) {
                // The relevance probe may have recursed through the navigator's
                // scan state — re-locate so the found* fields describe slot i.
                nav.locateChild(contextId, i);
                return i;
            }
        }
        return -1;
    }

    /**
     * C {@code ts_tree_cursor_is_entry_visible}: the root frame is always
     * visible; other frames are visible when their raw symbol is visible.
     * Container-chain frames carry the reserved out-of-range container symbol
     * and ERROR / ERROR_REPEAT recovery frames carry builtin ids beyond the
     * grammar tables — chains count as invisible, builtins resolve through
     * {@code symbolVisibleOrBuiltin}.
     */
    private boolean entryVisible(int i) {
        return i == 0 || !nav.isChain(fNode[i])
                && nav.language().symbolVisibleOrBuiltin(nav.symbolOf(fNode[i]));
    }

    private boolean fieldSearch(int nodeId, int fieldId) {
        Language.FieldMapEntry[] map = nav.language().fieldMap(nav.productionId(nodeId));
        if (map.length == 0) {
            return false;
        }
        int lo = 0;
        while (lo < map.length && map[lo].fieldId() < fieldId) {
            lo++;
        }
        if (lo == map.length) {
            return false;
        }
        int hi = map.length;
        while (hi > lo && map[hi - 1].fieldId() > fieldId) {
            hi--;
        }
        if (lo == hi) {
            return false;
        }

        int entryIndex = lo;
        int count = nav.flattenedChildCount(nodeId);
        for (int i = 0; i < count; i++) {
            if (!nav.locateChild(nodeId, i)) {
                return false;
            }
            int refId = nav.foundId();
            boolean refExtra = nav.foundExtra();
            int refStructural = nav.foundStructuralIndex();
            int refAlias = nav.foundAlias();
            if (refExtra) {
                continue;
            }
            int index = refStructural;
            if (index < map[entryIndex].childIndex()) {
                continue;
            }
            Language.FieldMapEntry entry = map[entryIndex];
            if (entry.inherited()) {
                if (entryIndex + 1 == hi) {
                    pushFrame(refId, nodeId, i, index, refAlias);
                    boolean found = fieldSearch(refId, fieldId);
                    if (!found) {
                        size--;
                    }
                    return found;
                }
                pushFrame(refId, nodeId, i, index, refAlias);
                boolean found = fieldSearch(refId, fieldId);
                if (found) {
                    return true;
                }
                size--;
                entryIndex++;
                if (entryIndex == hi) {
                    return false;
                }
                continue;
            }
            if (nav.foundStepVisible()) {
                pushFrame(refId, nodeId, i, index, refAlias);
                return true;
            }
            if (nav.visibleChildCount(refId) > 0) {
                pushFrame(refId, nodeId, i, index, refAlias);
                boolean found = gotoChild(0);
                if (found) {
                    return true;
                }
                size--;
                return false;
            }
            entryIndex++;
            if (entryIndex == hi) {
                return false;
            }
        }
        return false;
    }

    private void pushFrame(int nodeId, int contextId, int childIndex, int structuralIndex, int alias) {
        if (size == fNode.length) {
            fNode = grow(fNode);
            fCtx = grow(fCtx);
            fIdx = grow(fIdx);
            fStruct = grow(fStruct);
            fAlias = grow(fAlias);
        }
        fNode[size] = nodeId;
        fCtx[size] = contextId;
        fIdx[size] = childIndex;
        fStruct[size] = structuralIndex;
        fAlias[size] = alias;
        size++;
    }

    private static int[] grow(int[] array) {
        int[] grown = new int[array.length * 2];
        System.arraycopy(array, 0, grown, 0, array.length);
        return grown;
    }
}
