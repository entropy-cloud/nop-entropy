package io.nop.treesitter.parser.incremental;

import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.subtree.Subtree;

import java.util.List;

/**
 * Walks the leaves of the previous parse tree in source order and offers the
 * candidate at the parser's current position for reuse — the Java counterpart
 * of the C runtime's {@code ReusableNode} cursor ({@code reusable_node.h}).
 *
 * <p>Reuse granularity is the <b>leaf</b>: the C runtime breaks a reused
 * composite back down ({@code ts_parser__breakdown_lookahead}) and shifts only
 * its first leaf, re-reducing every parent; this cursor therefore flattens the
 * old tree to leaves up front and lets the GLR reduces rebuild all composites,
 * which keeps shape, production ids and dynamic precedence identical to a
 * fresh parse by construction.</p>
 *
 * <p>A candidate is offered only when every gate holds:</p>
 * <ul>
 *   <li>its old start maps to exactly the parser's current position through
 *       the edit list's old→new coordinate shift (positions inside an edited
 *       old range map to "no coordinate");</li>
 *   <li>its old byte range intersects no edit's old range — the immutable-tree
 *       equivalent of the C runtime's per-subtree {@code has_changes} bits;</li>
 *   <li>it is non-empty, a real grammar symbol, and not the keyword-capture
 *       token (no stored leaf parse state — keyword resolution is
 *       parse-state dependent, so such tokens are re-lexed);</li>
 *   <li>(enforced by the caller, mirroring C {@code can_reuse_first_leaf})
 *       the current state has no external lex mode, and the cell for the
 *       candidate symbol is safe either because the leaf was produced under
 *       the same internal lex state or because the blob's generator-computed
 *       {@code reusable} bit marks it lexically unambiguous.</li>
 * </ul>
 */
public final class ReuseCursor {

    /**
     * A reusable old-tree leaf in new-source coordinates. {@code lexState} is
     * the internal lex state the leaf was produced under (the arena state slot
     * of shifted leaves), or {@code -1} when the leaf came from an
     * external-scanner lex state and must never be reused.
     */
    public record Candidate(int symbol, boolean extra, int size, int lexState) {
    }

    private final int[] starts;
    private final int[] ends;
    private final int[] symbols;
    private final boolean[] extras;
    private final int[] lexStates;
    private final List<TSInputEdit> edits;
    private int cursor;

    public ReuseCursor(TSTree oldTree, List<TSInputEdit> sortedEdits) {
        this.edits = sortedEdits;
        Language language = oldTree.language();
        int limit = language.symbolCount() + language.aliasCount();
        int[] idStack = new int[64];
        int depth = 0;
        idStack[depth++] = oldTree.root();
        int count = 0;
        int[] ids = new int[16];
        while (depth > 0) {
            int id = idStack[--depth];
            Subtree node = oldTree.arena().get(id);
            if (node.childCount() == 0) {
                int symbol = node.symbol();
                if (symbol > 0 && symbol < limit && oldTree.arena().sizeOf(id) > 0) {
                    if (count == ids.length) {
                        ids = java.util.Arrays.copyOf(ids, ids.length * 2);
                    }
                    ids[count++] = id;
                }
                continue;
            }
            if (depth + node.childCount() > idStack.length) {
                idStack = java.util.Arrays.copyOf(idStack, Math.max(idStack.length * 2, depth + node.childCount()));
            }
            for (int i = node.childCount() - 1; i >= 0; i--) {
                idStack[depth++] = node.child(i);
            }
        }
        this.starts = new int[count];
        this.ends = new int[count];
        this.symbols = new int[count];
        this.extras = new boolean[count];
        this.lexStates = new int[count];
        for (int i = 0; i < count; i++) {
            Subtree node = oldTree.arena().get(ids[i]);
            starts[i] = node.padding();
            ends[i] = node.padding() + oldTree.arena().sizeOf(ids[i]);
            symbols[i] = node.symbol();
            extras[i] = node.extra() != 0;
            lexStates[i] = node.state();
        }
    }

    /**
     * The reusable candidate starting exactly at {@code position} in new-source
     * coordinates, or {@code null}. Monotonically advances past candidates that
     * end at or before {@code position} or intersect an edit.
     */
    public Candidate candidateAt(int position) {
        while (cursor < symbols.length) {
            if (intersectsEdit(starts[cursor], ends[cursor])) {
                cursor++;
                continue;
            }
            int mappedEnd = mapOldToNew(ends[cursor]);
            if (mappedEnd >= 0 && mappedEnd <= position) {
                cursor++;
                continue;
            }
            break;
        }
        if (cursor >= symbols.length) {
            return null;
        }
        int start = starts[cursor];
        if (start > position || mapOldToNew(start) != position) {
            return null;
        }
        return new Candidate(symbols[cursor], extras[cursor], ends[cursor] - start, lexStates[cursor]);
    }

    private boolean intersectsEdit(int start, int end) {
        for (TSInputEdit edit : edits) {
            if (start < edit.oldEndByte() && edit.startByte() < end) {
                return true;
            }
        }
        return false;
    }

    /**
     * Maps an old-source byte offset to new-source coordinates by folding in
     * every edit entirely before it; returns {@code -1} for offsets inside an
     * edited old range, which have no new-source coordinate. Offsets at an
     * edit's {@code startByte} map unshifted — such candidates are rejected by
     * the intersect gate anyway, and the boundary itself is edit-invariant.
     */
    private int mapOldToNew(int offset) {
        int mapped = offset;
        for (TSInputEdit edit : edits) {
            if (offset >= edit.oldEndByte()) {
                mapped += edit.newEndByte() - edit.oldEndByte();
            } else if (offset > edit.startByte()) {
                return -1;
            } else {
                break;
            }
        }
        return mapped;
    }
}
