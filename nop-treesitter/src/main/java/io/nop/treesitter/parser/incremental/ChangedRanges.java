package io.nop.treesitter.parser.incremental;

import io.nop.treesitter.TSTree;
import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.subtree.Subtree;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the changed ranges between two syntax trees — the Java counterpart
 * of C {@code ts_tree_get_changed_ranges}.
 *
 * <p>Unlike C, which mutates the old tree into the new source's coordinates
 * ({@code ts_subtree_edit}) and marks {@code has_changes} bits along the edited
 * path, this runtime keeps trees immutable, so the comparison is
 * <b>position-independent</b> and operates on each tree's <b>leaf spine</b>:
 * the pre-order leaves with invisible parents and chain containers flattened
 * through and alias-aware effective symbols, exactly the view the
 * s-expression renderer uses. Two leaves match when their effective symbol,
 * size and content bytes — each read from the node's own tree's retained
 * source — are identical. A two-pointer walk emits the union span of every
 * mismatched leaf pair, advances past the earlier end, and finally covers
 * whichever spine has a trailing remainder; ranges are coalesced with the
 * upstream {@code ts_range_array_add} semantics (overlapping or touching
 * merge, empty ranges dropped, identical trees yield none).</p>
 *
 * <p>Leaf granularity bounds the comparison: structural re-groupings that keep
 * every leaf's symbol and content (repeating-rule re-nesting, production-id
 * changes that only affect field names of invisible levels) are not reported.
 * Content changes, insertions, deletions and alias changes are.</p>
 */
public final class ChangedRanges {

    private ChangedRanges() {
    }

    /**
     * The ranges whose tree content differs between the two trees. Both trees
     * must come from the same {@link Language} instance. Ranges are in each
     * tree's own coordinates: content equality is position-independent, so a
     * region appears only where its tokens actually changed.
     */
    public static List<TSRange> getChangedRanges(TSTree oldTree, TSTree newTree) {
        if (oldTree == null || newTree == null) {
            throw new TreeSitterException("changed ranges require two non-null trees");
        }
        if (oldTree.language() != newTree.language()) {
            throw new TreeSitterException("changed ranges require trees of the same language instance");
        }
        List<LeafEntry> oldSpine = leafSpine(oldTree);
        List<LeafEntry> newSpine = leafSpine(newTree);
        List<TSRange> ranges = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < oldSpine.size() && j < newSpine.size()) {
            LeafEntry o = oldSpine.get(i);
            LeafEntry n = newSpine.get(j);
            if (o.matches(n, oldTree, newTree)) {
                i++;
                j++;
                continue;
            }
            emitUnionRange(oldTree, o, newTree, n, ranges);
            int limit = Math.min(o.end, n.end);
            while (i < oldSpine.size() && oldSpine.get(i).end <= limit) {
                i++;
            }
            while (j < newSpine.size() && newSpine.get(j).end <= limit) {
                j++;
            }
        }
        if (i < oldSpine.size() || j < newSpine.size()) {
            emitSpineTail(oldTree, oldSpine, i, newTree, newSpine, j, ranges);
        }
        return coalesce(ranges);
    }

    private static final class LeafEntry {
        final int id;
        final int start;
        final int end;
        final int effectiveSymbol;

        LeafEntry(int id, int start, int end, int effectiveSymbol) {
            this.id = id;
            this.start = start;
            this.end = end;
            this.effectiveSymbol = effectiveSymbol;
        }

        boolean matches(LeafEntry other, TSTree oldTree, TSTree newTree) {
            if (effectiveSymbol != other.effectiveSymbol || end - start != other.end - other.start) {
                return false;
            }
            byte[] oldSource = oldTree.source();
            byte[] newSource = newTree.source();
            for (int k = 0; k < end - start; k++) {
                if (oldSource[start + k] != newSource[other.start + k]) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * The tree's leaves in source order: invisible composites and chain
     * containers are flattened through; each leaf carries the alias symbol its
     * direct parent's reduce production assigns at its structural index (the
     * effective symbol the renderer displays), which lets alias changes show
     * up as content changes.
     */
    private static List<LeafEntry> leafSpine(TSTree tree) {
        List<LeafEntry> out = new ArrayList<>();
        collectLeaves(tree, tree.root(), 0, 0, false, out);
        return out;
    }

    private static void collectLeaves(TSTree tree, int id, int parentProduction, int indexInParent,
                                      boolean forceNoAlias, List<LeafEntry> out) {
        Subtree node = tree.arena().get(id);
        if (node.childCount() == 0) {
            if (node.symbol() > 0 && tree.arena().sizeOf(id) > 0) {
                int effective = node.symbol();
                if (!forceNoAlias && node.extra() == 0 && parentProduction != 0) {
                    int alias = tree.language().aliasAt(parentProduction, indexInParent);
                    if (alias != 0) {
                        effective = alias;
                    }
                }
                out.add(new LeafEntry(id, node.padding(),
                        node.padding() + tree.arena().sizeOf(id), effective));
            }
            return;
        }
        int structuralIndex = 0;
        for (int k = 0; k < node.childCount(); k++) {
            int childId = node.child(k);
            Subtree child = tree.arena().get(childId);
            if (isChainContainer(tree, child)) {
                for (int m = 0; m < child.childCount(); m++) {
                    int grandId = child.child(m);
                    Subtree grand = tree.arena().get(grandId);
                    collectLeaves(tree, grandId, parentProduction, structuralIndex, false, out);
                    if (grand.extra() == 0) {
                        structuralIndex++;
                    }
                }
                continue;
            }
            collectLeaves(tree, childId, node.state(), structuralIndex,
                    forceNoAlias || node.state() == 0, out);
            if (child.extra() == 0) {
                structuralIndex++;
            }
        }
    }

    private static boolean isChainContainer(TSTree tree, Subtree node) {
        Language language = tree.language();
        return node.symbol() == language.symbolCount() + language.aliasCount();
    }

    private static void emitUnionRange(TSTree oldTree, LeafEntry o, TSTree newTree, LeafEntry n, List<TSRange> out) {
        int startByte = Math.min(o.start, n.start);
        int endByte = Math.max(o.end, n.end);
        TSPoint startPoint = o.start <= n.start
                ? TSPoint.fromByteOffset(oldTree.source(), startByte)
                : TSPoint.fromByteOffset(newTree.source(), startByte);
        TSPoint endPoint = o.end >= n.end
                ? TSPoint.fromByteOffset(oldTree.source(), endByte)
                : TSPoint.fromByteOffset(newTree.source(), endByte);
        out.add(new TSRange(startPoint, endPoint, startByte, endByte));
    }

    private static void emitSpineTail(TSTree oldTree, List<LeafEntry> oldSpine, int i,
                                      TSTree newTree, List<LeafEntry> newSpine, int j, List<TSRange> out) {
        List<LeafEntry> spine = i < oldSpine.size() ? oldSpine : newSpine;
        int from = i < oldSpine.size() ? i : j;
        TSTree owner = i < oldSpine.size() ? oldTree : newTree;
        int startByte = spine.get(from).start;
        int endByte = spine.get(spine.size() - 1).end;
        if (endByte <= startByte) {
            return;
        }
        out.add(new TSRange(
                TSPoint.fromByteOffset(owner.source(), startByte),
                TSPoint.fromByteOffset(owner.source(), endByte),
                startByte, endByte));
    }

    private static List<TSRange> coalesce(List<TSRange> ranges) {
        ranges.sort((a, b) -> a.startByte() != b.startByte()
                ? Integer.compare(a.startByte(), b.startByte())
                : Integer.compare(a.endByte(), b.endByte()));
        List<TSRange> merged = new ArrayList<>();
        for (TSRange range : ranges) {
            if (!merged.isEmpty() && TSRange.overlapsOrTouches(merged.get(merged.size() - 1), range)) {
                TSRange last = merged.get(merged.size() - 1);
                if (range.endByte() > last.endByte()) {
                    merged.set(merged.size() - 1,
                            new TSRange(last.startPoint(), range.endPoint(), last.startByte(), range.endByte()));
                }
                continue;
            }
            merged.add(range);
        }
        return merged;
    }
}
