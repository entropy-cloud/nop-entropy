package io.nop.lint.core.lang;

import io.nop.lint.core.NopLintException;
import io.nop.treesitter.parser.incremental.TSInputEdit;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the {@link TSInputEdit} sequence that transforms one UTF-8 source
 * into another, for consumption by {@code TSParser.parseIncremental} (design
 * 03 §1.2). The two sources play the roles of the text before and after one
 * editing step; no {@code TSTree} participates in the computation.
 *
 * <p><b>Edit granularity (design 03 §1.2 裁定)</b>: the output is a minimized
 * multi-hunk sequence. Common byte prefix and suffix are stripped, the
 * differing middle is split at the line level with a Myers diff, and every
 * changed line-run is then shrunk to the byte level. A single merged hunk
 * spanning from the first to the last changed byte was rejected: the
 * incremental parser can only reuse old-tree leaves outside edited regions,
 * so one merged hunk over distant small edits would force a re-lex of the
 * entire span between them and destroy the reuse rate that motivates
 * incremental parsing (design 11 §6 editor scenarios). The finer split costs
 * an O((N+M)·D) Myers pass over the changed middle only, and correctness is
 * independent of the decomposition — the backend guarantees that any legal,
 * non-overlapping edit sequence yields a tree byte-identical to a full
 * reparse.</p>
 *
 * <p>The sequence is ordered by {@code startByte}, pairwise non-overlapping
 * in old-source coordinates, and jointly covers every changed byte. Byte
 * coordinates follow the {@link TSInputEdit} record contract:
 * {@code startByte}/{@code oldEndByte} are old-source offsets, and
 * {@code newEndByte} is <b>anchored</b> — {@code startByte + insertedLength},
 * so deletions have {@code newEndByte == startByte}. That anchoring is what
 * makes the backend's old→new shift fold ({@code newEndByte - oldEndByte} per
 * edit) equal the true local delta for every hunk, insertions and deletions
 * alike; expressing {@code newEndByte} as an absolute new-source offset would
 * corrupt the shift for every hunk after a net deletion. When a hunk's
 * anchored end would still fall outside the new source (possible only after a
 * large net deletion), it is merged backward into the preceding hunk: the
 * merged span keeps the exact same total delta, so the mapping stays correct
 * and only the reuse rate inside the merged span is given up. Identical
 * inputs produce an empty list; illegal inputs fail closed with a
 * {@link NopLintException}.</p>
 */
public final class EditCalculator {

    private static final int OP_MATCH = 0;
    private static final int OP_DELETE = 1;
    private static final int OP_INSERT = 2;

    private EditCalculator() {
    }

    /**
     * Diffs two UTF-8 sources into the minimal multi-hunk edit sequence that
     * transforms {@code oldSource} into {@code newSource}.
     *
     * @throws NopLintException when either array is null (fail-closed; no
     *                          silent empty-diff fallback)
     */
    public static List<TSInputEdit> diff(byte[] oldSource, byte[] newSource) {
        if (oldSource == null) {
            throw new NopLintException("oldSource must not be null");
        }
        if (newSource == null) {
            throw new NopLintException("newSource must not be null");
        }

        int prefix = commonPrefixLength(oldSource, newSource);
        int suffix = commonSuffixLength(oldSource, newSource, prefix);
        int oldStart = prefix;
        int oldEnd = oldSource.length - suffix;
        int newStart = prefix;
        int newEnd = newSource.length - suffix;
        if (oldStart >= oldEnd && newStart >= newEnd) {
            return List.of();
        }

        List<Hunk> hunks = lineDiffHunks(oldSource, newSource, oldStart, oldEnd, newStart, newEnd);
        // Group state {oldStart, oldEnd, newStart, newEnd}; a group's anchored
        // newEndByte is oldStart + (newEnd - newStart). Hunks stay minimal; a
        // hunk whose anchored end would leave the new source (possible only
        // after a large net deletion) is merged backward into the preceding
        // group — repeatedly, through already-emitted groups — until legal.
        // That always terminates: the anchored end is the group's absolute new
        // end minus the net deletion strictly before the group's first hunk,
        // which shrinks as the group extends backward and reaches its minimum
        // (the plain new end, in bounds by construction) at the first hunk.
        List<int[]> groups = new ArrayList<>(hunks.size());
        for (Hunk hunk : hunks) {
            int[] g = {hunk.oldStart, hunk.oldEnd, hunk.newStart, hunk.newEnd};
            while (!groups.isEmpty() && !legalAnchored(g, newSource)) {
                int[] prev = groups.remove(groups.size() - 1);
                g = new int[]{prev[0], g[1], prev[2], g[3]};
            }
            groups.add(g);
        }
        List<TSInputEdit> edits = new ArrayList<>(groups.size());
        for (int[] g : groups) {
            if (!legalAnchored(g, newSource)) {
                throw new NopLintException("unencodable edit group: anchored newEndByte "
                        + anchoredEnd(g) + " exceeds new source length " + newSource.length);
            }
            edits.add(toEdit(g, oldSource, newSource));
        }
        return List.copyOf(edits);
    }

    private static boolean legalAnchored(int[] group, byte[] newSource) {
        return anchoredEnd(group) <= newSource.length;
    }

    private static int anchoredEnd(int[] group) {
        return group[0] + (group[3] - group[2]);
    }

    private static TSInputEdit toEdit(int[] group, byte[] oldSource, byte[] newSource) {
        // TSInputEdit.of derives all three TSPoints from the sources with the
        // sanctioned TSPoint.fromByteOffset semantics; newEndByte is anchored
        // at startByte + inserted length per the record contract.
        return TSInputEdit.of(oldSource, newSource, group[0], group[1], anchoredEnd(group));
    }

    private static int commonPrefixLength(byte[] a, byte[] b) {
        int min = Math.min(a.length, b.length);
        int i = 0;
        while (i < min && a[i] == b[i]) {
            i++;
        }
        return i;
    }

    private static int commonSuffixLength(byte[] a, byte[] b, int prefix) {
        int max = Math.min(a.length, b.length) - prefix;
        int suffix = 0;
        while (suffix < max && a[a.length - 1 - suffix] == b[b.length - 1 - suffix]) {
            suffix++;
        }
        return suffix;
    }

    // ==================== line-level Myers diff over the changed middle ====================

    private static final class Hunk {
        int oldStart;
        int oldEnd;
        int newStart;
        int newEnd;

        Hunk(int oldStart, int newStart) {
            this.oldStart = oldStart;
            this.oldEnd = oldStart;
            this.newStart = newStart;
            this.newEnd = newStart;
        }
    }

    /**
     * One line of the region {@code [from, to)}: the bytes up to and including
     * the next {@code '\n'}, or up to {@code to} for the final unterminated line.
     */
    private record LineTable(int[] starts, int[] ends) {

        int length(int line) {
            return ends[line] - starts[line];
        }

        int count() {
            return starts.length;
        }
    }

    private static LineTable lineTable(byte[] src, int from, int to) {
        int lines = 0;
        for (int i = from; i < to; i++) {
            if (src[i] == '\n') {
                lines++;
            }
        }
        if (from < to) {
            lines++;
        }
        int[] starts = new int[lines];
        int[] ends = new int[lines];
        int index = 0;
        int lineStart = from;
        for (int i = from; i < to; i++) {
            if (src[i] == '\n') {
                starts[index] = lineStart;
                ends[index] = i + 1;
                index++;
                lineStart = i + 1;
            }
        }
        if (lineStart < to) {
            starts[index] = lineStart;
            ends[index] = to;
        }
        return new LineTable(starts, ends);
    }

    /**
     * Maps each line to a dense content id so the Myers pass compares ints;
     * line identity is full byte equality of the line (terminator included).
     * Both sides share one map, so equal ids mean equal lines across sides.
     */
    private static int[] lineIds(byte[] src, LineTable table, Map<String, Integer> sharedIds) {
        int[] out = new int[table.count()];
        for (int i = 0; i < out.length; i++) {
            String key = new String(src, table.starts()[i], table.length(i), StandardCharsets.UTF_8);
            Integer id = sharedIds.get(key);
            if (id == null) {
                id = sharedIds.size();
                sharedIds.put(key, id);
            }
            out[i] = id;
        }
        return out;
    }

    private static List<Hunk> lineDiffHunks(byte[] oldSource, byte[] newSource,
                                            int oldStart, int oldEnd, int newStart, int newEnd) {
        LineTable oldLines = lineTable(oldSource, oldStart, oldEnd);
        LineTable newLines = lineTable(newSource, newStart, newEnd);
        // One shared id map: line identity is only comparable across the two
        // sides when both number their lines from the same table.
        Map<String, Integer> sharedIds = new HashMap<>();
        int[] oldIds = lineIds(oldSource, oldLines, sharedIds);
        int[] newIds = lineIds(newSource, newLines, sharedIds);
        List<int[]> ops = myersOps(oldIds, newIds);
        if (ops == null) {
            // the trace budget would blow (pathologically large D): fall back
            // to ONE whole-middle replace hunk. Byte-identical result by
            // construction — the middle of the new source replaces the middle
            // of the old, everything outside the stripped prefix/suffix is
            // shared (plan 11; the UnifiedDiff.MAX_LCS_CELLS discipline and
            // design 03 §1.2's "correct regardless of decomposition").
            Hunk whole = new Hunk(oldStart, newStart);
            whole.oldEnd = oldEnd;
            whole.newEnd = newEnd;
            return List.of(whole);
        }

        List<Hunk> hunks = new ArrayList<>();
        Hunk current = null;
        int oldOff = oldStart;
        int newOff = newStart;
        int bIndex = 0;
        for (int[] op : ops) {
            if (op[0] == OP_MATCH) {
                if (current != null) {
                    hunks.add(current);
                    current = null;
                }
                int len = oldLines.length(op[1]);
                oldOff += len;
                newOff += len;
                bIndex++;
            } else if (op[0] == OP_DELETE) {
                if (current == null) {
                    current = new Hunk(oldOff, newOff);
                }
                int len = oldLines.length(op[1]);
                current.oldEnd += len;
                oldOff += len;
            } else {
                if (current == null) {
                    current = new Hunk(oldOff, newOff);
                }
                int len = newLines.length(bIndex);
                current.newEnd += len;
                newOff += len;
                bIndex++;
            }
        }
        if (current != null) {
            hunks.add(current);
        }

        List<Hunk> trimmed = new ArrayList<>(hunks.size());
        for (Hunk hunk : hunks) {
            trim(hunk, oldSource, newSource);
            // A minimal edit script cannot contain a replace-with-identical-
            // content run, so the trimmed hunk keeps at least one changed
            // byte; a zero-width residue is dropped by construction (and
            // would be a legal no-op edit anyway).
            if (hunk.oldStart < hunk.oldEnd || hunk.newStart < hunk.newEnd) {
                trimmed.add(hunk);
            }
        }
        return trimmed;
    }

    /**
     * Shrinks a line-granular hunk to the byte level: common head and tail
     * bytes of the replaced old span and the replacing new span are excluded,
     * which is what keeps keystroke-scale edits to keystroke-scale hunks.
     */
    private static void trim(Hunk hunk, byte[] oldSource, byte[] newSource) {
        while (hunk.oldEnd > hunk.oldStart && hunk.newEnd > hunk.newStart
                && oldSource[hunk.oldEnd - 1] == newSource[hunk.newEnd - 1]) {
            hunk.oldEnd--;
            hunk.newEnd--;
        }
        while (hunk.oldStart < hunk.oldEnd && hunk.newStart < hunk.newEnd
                && oldSource[hunk.oldStart] == newSource[hunk.newStart]) {
            hunk.oldStart++;
            hunk.newStart++;
        }
    }

    /**
     * Total trace-int ceiling, aligned with {@code UnifiedDiff.MAX_LCS_CELLS}
     * (plan 11): the Myers backtrack keeps one v-array snapshot per step, so
     * a pathologically large D would cost O(D·(N+M)) memory — a 5000-line
     * whole-file rewrite is hundreds of MB. Exceeding the ceiling makes
     * {@code myersOps} return null and the caller fall back to a single
     * whole-middle hunk; the diff stays CORRECT, only non-minimal.
     */
    private static final int MAX_TRACE_CELLS = 4_000_000;

    /**
     * Classic O((N+M)·D) Myers diff over line ids. Returns the edit script in
     * forward order; match ops carry the old line index, insert ops the new
     * line index (delete ops likewise the old line index). Returns
     * {@code null} when the backtrack trace would exceed the memory ceiling —
     * the caller falls back (never a partial or wrong script).
     */
    private static List<int[]> myersOps(int[] a, int[] b) {
        int n = a.length;
        int m = b.length;
        if (n == 0 && m == 0) {
            return List.of();
        }
        int max = n + m;
        int[] v = new int[2 * max + 1];
        int offset = max;
        long cells = 0;
        List<int[]> trace = new ArrayList<>();
        whileLoop:
        for (int d = 0; d <= max; d++) {
            if (cells + v.length > MAX_TRACE_CELLS) {
                return null;
            }
            trace.add(v.clone());
            cells += v.length;
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                    x = v[offset + k + 1];
                } else {
                    x = v[offset + k - 1] + 1;
                }
                int y = x - k;
                while (x < n && y < m && a[x] == b[y]) {
                    x++;
                    y++;
                }
                v[offset + k] = x;
                if (x >= n && y >= m) {
                    break whileLoop;
                }
            }
        }

        List<int[]> reversed = new ArrayList<>();
        int x = n;
        int y = m;
        for (int step = trace.size() - 1; step >= 1; step--) {
            int[] snapshot = trace.get(step);
            int k = x - y;
            boolean fromInsertion = k == -step || (k != step && snapshot[offset + k - 1] < snapshot[offset + k + 1]);
            int prevK = fromInsertion ? k + 1 : k - 1;
            int prevX = snapshot[offset + prevK];
            int prevY = prevX - prevK;
            if (fromInsertion) {
                while (x > prevX && y > prevY + 1) {
                    x--;
                    y--;
                    reversed.add(new int[]{OP_MATCH, x});
                }
                reversed.add(new int[]{OP_INSERT, prevY});
            } else {
                while (x > prevX + 1 && y > prevY) {
                    x--;
                    y--;
                    reversed.add(new int[]{OP_MATCH, x});
                }
                reversed.add(new int[]{OP_DELETE, prevX});
            }
            x = prevX;
            y = prevY;
        }
        while (x > 0) {
            x--;
            y--;
            reversed.add(new int[]{OP_MATCH, x});
        }

        List<int[]> ops = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            ops.add(reversed.get(i));
        }
        return ops;
    }
}
