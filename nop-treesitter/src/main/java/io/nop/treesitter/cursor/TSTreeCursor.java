package io.nop.treesitter.cursor;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.subtree.Subtree;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateful, O(1)-per-step tree navigation over an immutable {@link TSTree}
 * snapshot (C {@code TSTreeCursor} semantics, adapted to the int-indexed
 * {@code SubtreeArena}).
 *
 * <p>The cursor keeps a stack of frames; each frame is a
 * {@code (nodeId, contextId, childIndex, structuralIndex, aliasSymbol)} tuple
 * of plain ints — navigation never allocates per-node heap objects. Invisible
 * container-chain nodes (the arena's >{@code MAX_CHILDREN} overflow artifact)
 * are fully transparent: their children iterate exactly as if they were direct
 * children of the logical parent, and the logical parent's production keeps
 * providing the alias sequence and field map.</p>
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

    private final TreeNavigator nav;
    private final List<Frame> stack;
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
        this.nav = new TreeNavigator(node.tree());
        this.stack = new ArrayList<>();
        this.size = 0;

        List<Integer> ids = new ArrayList<>();
        for (int id = node.id(); ; id = nav.logicalParent(id)) {
            ids.add(id);
            if (nav.logicalParent(id) == Subtree.NO_ID) {
                break;
            }
        }
        for (int k = ids.size() - 1; k >= 0; k--) {
            int currentId = ids.get(k);
            if (k == ids.size() - 1) {
                int topAlias = k == 0 ? node.aliasSymbol() : 0;
                push(new Frame(currentId, Subtree.NO_ID, 0, 0, topAlias));
            } else {
                int parentId = ids.get(k + 1);
                int index = nav.flattenedIndexOf(parentId, currentId);
                TreeNavigator.ChildRef ref = nav.childRef(parentId, index);
                push(new Frame(currentId, parentId, index, ref.structuralIndex(), ref.alias()));
            }
        }
    }

    /**
     * The node the cursor is currently positioned at.
     */
    public TSNode currentNode() {
        Frame top = top();
        return new TSNode(nav.tree(), top.nodeId(), top.aliasSymbol());
    }

    /**
     * Moves to the first visible child of the current node; invisible children
     * with visible descendants are crossed transparently. Returns false when
     * the current node has no visible children (cursor unchanged).
     */
    public boolean gotoFirstChild() {
        while (true) {
            Frame top = top();
            int found = findNext(top.nodeId(), -1, false);
            if (found < 0) {
                return false;
            }
            TreeNavigator.ChildRef ref = nav.childRef(top.nodeId(), found);
            push(new Frame(ref.id(), top.nodeId(), found, ref.structuralIndex(), ref.alias()));
            if (nav.stepVisible(ref)) {
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
            Frame popped = pop();
            int found = findNext(popped.contextId(), popped.childIndex(), false);
            if (found >= 0) {
                TreeNavigator.ChildRef ref = nav.childRef(popped.contextId(), found);
                push(new Frame(ref.id(), popped.contextId(), found, ref.structuralIndex(), ref.alias()));
                if (nav.stepVisible(ref)) {
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
            Frame top = top();
            int found = findNext(top.nodeId(), -1, true);
            if (found < 0) {
                return false;
            }
            TreeNavigator.ChildRef ref = nav.childRef(top.nodeId(), found);
            push(new Frame(ref.id(), top.nodeId(), found, ref.structuralIndex(), ref.alias()));
            if (nav.namedRelevant(ref)) {
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
            Frame popped = pop();
            int found = findNext(popped.contextId(), popped.childIndex(), true);
            if (found >= 0) {
                TreeNavigator.ChildRef ref = nav.childRef(popped.contextId(), found);
                push(new Frame(ref.id(), popped.contextId(), found, ref.structuralIndex(), ref.alias()));
                if (nav.namedRelevant(ref)) {
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
        List<Frame> descent = new ArrayList<>();
        int context = top().nodeId();
        int remaining = index;
        while (true) {
            boolean descended = false;
            int count = nav.flattenedChildCount(context);
            for (int i = 0; i < count; i++) {
                TreeNavigator.ChildRef ref = nav.childRef(context, i);
                if (nav.stepVisible(ref)) {
                    if (remaining == 0) {
                        descent.add(new Frame(ref.id(), context, i, ref.structuralIndex(), ref.alias()));
                        pushAll(descent);
                        return true;
                    }
                    remaining--;
                } else {
                    int grandchildCount = nav.visibleChildCount(ref.id());
                    if (remaining < grandchildCount) {
                        descent.add(new Frame(ref.id(), context, i, ref.structuralIndex(), ref.alias()));
                        context = ref.id();
                        descended = true;
                        break;
                    }
                    remaining -= grandchildCount;
                }
            }
            if (!descended) {
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
        List<Frame> descent = new ArrayList<>();
        int context = top().nodeId();
        int remaining = index;
        while (true) {
            boolean descended = false;
            int count = nav.flattenedChildCount(context);
            for (int i = 0; i < count; i++) {
                TreeNavigator.ChildRef ref = nav.childRef(context, i);
                if (nav.namedRelevant(ref)) {
                    if (remaining == 0) {
                        descent.add(new Frame(ref.id(), context, i, ref.structuralIndex(), ref.alias()));
                        pushAll(descent);
                        return true;
                    }
                    remaining--;
                } else {
                    int grandchildCount = nav.namedChildCount(ref.id());
                    if (remaining < grandchildCount) {
                        descent.add(new Frame(ref.id(), context, i, ref.structuralIndex(), ref.alias()));
                        context = ref.id();
                        descended = true;
                        break;
                    }
                    remaining -= grandchildCount;
                }
            }
            if (!descended) {
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
        return fieldSearch(top().nodeId(), fieldId);
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
            Frame entry = stack.get(i);
            if (i != size - 1 && entryVisible(i)) {
                break;
            }
            if (nav.node(entry.nodeId()).extra() != 0) {
                break;
            }
            for (Language.FieldMapEntry map : nav.language().fieldMap(nav.productionId(entry.contextId()))) {
                if (!map.inherited() && map.childIndex() == entry.structuralIndex()) {
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
        return nav.visibleChildCount(top().nodeId());
    }

    /**
     * Number of named children of the current node (C {@code ts_node_named_child_count}).
     */
    public int namedChildCount() {
        return nav.namedChildCount(top().nodeId());
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
            TreeNavigator.ChildRef ref = nav.childRef(contextId, i);
            if (named) {
                if (nav.namedRelevant(ref) || nav.namedChildCount(ref.id()) > 0) {
                    return i;
                }
            } else if (nav.stepVisible(ref) || nav.visibleChildCount(ref.id()) > 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * C {@code ts_tree_cursor_is_entry_visible}: the root frame is always
     * visible; other frames are visible when their raw symbol is visible.
     */
    private boolean entryVisible(int i) {
        return i == 0 || nav.language().symbolVisible(nav.symbolOf(stack.get(i).nodeId()));
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
            TreeNavigator.ChildRef ref = nav.childRef(nodeId, i);
            if (ref.extra()) {
                continue;
            }
            int index = ref.structuralIndex();
            if (index < map[entryIndex].childIndex()) {
                continue;
            }
            Language.FieldMapEntry entry = map[entryIndex];
            if (entry.inherited()) {
                if (entryIndex + 1 == hi) {
                    push(new Frame(ref.id(), nodeId, i, index, ref.alias()));
                    boolean found = fieldSearch(ref.id(), fieldId);
                    if (!found) {
                        pop();
                    }
                    return found;
                }
                push(new Frame(ref.id(), nodeId, i, index, ref.alias()));
                boolean found = fieldSearch(ref.id(), fieldId);
                if (found) {
                    return true;
                }
                pop();
                entryIndex++;
                if (entryIndex == hi) {
                    return false;
                }
                continue;
            }
            if (nav.stepVisible(ref)) {
                push(new Frame(ref.id(), nodeId, i, index, ref.alias()));
                return true;
            }
            if (nav.visibleChildCount(ref.id()) > 0) {
                push(new Frame(ref.id(), nodeId, i, index, ref.alias()));
                boolean found = gotoChild(0);
                if (found) {
                    return true;
                }
                pop();
                return false;
            }
            entryIndex++;
            if (entryIndex == hi) {
                return false;
            }
        }
        return false;
    }

    private Frame top() {
        return stack.get(size - 1);
    }

    private void push(Frame frame) {
        if (size == stack.size()) {
            stack.add(frame);
        } else {
            stack.set(size, frame);
        }
        size++;
    }

    private Frame pop() {
        size--;
        return stack.get(size);
    }

    private void pushAll(List<Frame> frames) {
        for (Frame frame : frames) {
            push(frame);
        }
    }

    /**
     * A cursor frame: the current node, its logical parent ({@code contextId},
     * the node whose production provides alias/field data — never a chain
     * container), the flattened child index within the parent, the structural
     * index within the parent, and the alias symbol the node renders with.
     */
    private record Frame(int nodeId, int contextId, int childIndex, int structuralIndex, int aliasSymbol) {
    }
}