package io.nop.lint.core.fix;

import java.util.ArrayList;
import java.util.List;

/**
 * The hand-written unified diff formatter of the fix dry-run (roadmap item
 * 25, design 03 §3: {@code --fix-dry-run} prints the raw-vs-final diff, no
 * third-party diff library). Line-based LCS over the common-prefix/suffix
 * trimmed middle — the region a lint fix touches is small, so the quadratic
 * table stays tiny; a pathological whole-file rewrite falls back to one
 * replace-everything hunk instead of allocating an unbounded table.
 *
 * <p>Output is the conventional unified format with three context lines:
 * {@code ---}/{@code +++} headers, {@code @@ -l,c +l,c @@} hunk headers, and
 * the {@code \ No newline at end of file} marker for a final line that lacks
 * its terminator. No change renders as the empty string.</p>
 */
public final class UnifiedDiff {

    /**
     * Context lines shown around each change hunk (the git default).
     */
    private static final int CONTEXT = 3;

    /**
     * LCS table cell bound for the trimmed middle; beyond it the middle is
     * emitted as one wholesale replacement rather than a minimally-diffed one.
     */
    private static final int MAX_LCS_CELLS = 4_000_000;

    private UnifiedDiff() {
    }

    /**
     * Renders the unified diff between the two texts, labelled with
     * {@code path} on both headers.
     */
    public static String of(String path, String original, String fixed) {
        return of("--- a/" + path, "+++ b/" + path, original, fixed);
    }

    /**
     * Renders the unified diff with explicit header labels.
     */
    public static String of(String oldLabel, String newLabel, String original, String fixed) {
        List<String> oldLines = splitLines(original);
        List<String> newLines = splitLines(fixed);
        if (oldLines.equals(newLines)) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        out.append(oldLabel).append('\n').append(newLabel).append('\n');
        for (String line : renderHunks(oldLines, newLines, computeOps(oldLines, newLines))) {
            out.append(line);
        }
        return out.toString();
    }

    /**
     * Splits the text into lines, each keeping its terminator; the final
     * element may be unterminated when the file does not end with a newline.
     * An empty text yields no lines (the empty-file side of a hunk header
     * reports count 0).
     */
    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(text.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        return lines;
    }

    /**
     * One edit-script step: {@code ' '} keeps a shared line, {@code '-'}
     * removes an old line, {@code '+'} inserts a new line.
     */
    private record Op(char type, String line) {
    }

    private static List<Op> computeOps(List<String> oldLines, List<String> newLines) {
        List<Op> ops = new ArrayList<>();
        int prefix = 0;
        while (prefix < oldLines.size() && prefix < newLines.size()
                && oldLines.get(prefix).equals(newLines.get(prefix))) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < oldLines.size() - prefix && suffix < newLines.size() - prefix
                && oldLines.get(oldLines.size() - 1 - suffix)
                        .equals(newLines.get(newLines.size() - 1 - suffix))) {
            suffix++;
        }

        for (int i = 0; i < prefix; i++) {
            ops.add(new Op(' ', oldLines.get(i)));
        }
        collectMiddleOps(ops, oldLines, newLines, prefix, suffix);
        for (int i = oldLines.size() - suffix; i < oldLines.size(); i++) {
            ops.add(new Op(' ', oldLines.get(i)));
        }
        return ops;
    }

    /**
     * Appends the trimmed middle as delete+insert ops (LCS-minimal when the
     * DP table fits its bound, wholesale replacement otherwise).
     */
    private static void collectMiddleOps(List<Op> ops, List<String> oldLines, List<String> newLines,
                                         int prefix, int suffix) {
        int oldEnd = oldLines.size() - suffix;
        int newEnd = newLines.size() - suffix;
        if (prefix >= oldEnd && prefix >= newEnd) {
            return;
        }

        int oldMiddle = oldEnd - prefix;
        int newMiddle = newEnd - prefix;
        if ((long) oldMiddle * newMiddle > MAX_LCS_CELLS) {
            for (int i = prefix; i < oldEnd; i++) {
                ops.add(new Op('-', oldLines.get(i)));
            }
            for (int i = prefix; i < newEnd; i++) {
                ops.add(new Op('+', newLines.get(i)));
            }
            return;
        }

        int[][] lcs = new int[oldMiddle + 1][newMiddle + 1];
        for (int i = oldMiddle - 1; i >= 0; i--) {
            for (int j = newMiddle - 1; j >= 0; j--) {
                if (oldLines.get(prefix + i).equals(newLines.get(prefix + j))) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }
        int i = 0;
        int j = 0;
        while (i < oldMiddle && j < newMiddle) {
            if (oldLines.get(prefix + i).equals(newLines.get(prefix + j))) {
                ops.add(new Op(' ', oldLines.get(prefix + i)));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                ops.add(new Op('-', oldLines.get(prefix + i)));
                i++;
            } else {
                ops.add(new Op('+', newLines.get(prefix + j)));
                j++;
            }
        }
        while (i < oldMiddle) {
            ops.add(new Op('-', oldLines.get(prefix + i)));
            i++;
        }
        while (j < newMiddle) {
            ops.add(new Op('+', newLines.get(prefix + j)));
            j++;
        }
    }

    /**
     * Groups the ops into hunks: every run of non-equal ops is padded with up
     * to {@link #CONTEXT} equal lines on each side, hunks whose separating
     * equal run does not exceed {@code 2 * CONTEXT} lines merge into one,
     * and each hunk emits its {@code @@} header followed by its body lines.
     */
    private static List<String> renderHunks(List<String> oldLines, List<String> newLines, List<Op> ops) {
        List<int[]> runs = new ArrayList<>();
        int index = 0;
        while (index < ops.size()) {
            if (ops.get(index).type() == ' ') {
                index++;
                continue;
            }
            int start = index;
            while (index < ops.size() && ops.get(index).type() != ' ') {
                index++;
            }
            runs.add(new int[]{start, index});
        }
        if (runs.isEmpty()) {
            return List.of();
        }

        List<int[]> hunks = new ArrayList<>(runs.size());
        for (int r = 0; r < runs.size(); r++) {
            int start = Math.max(runs.get(r)[0] - CONTEXT, 0);
            int end = Math.min(runs.get(r)[1] + CONTEXT, ops.size());
            int previous = hunks.size() - 1;
            if (previous >= 0 && runs.get(r)[0] - hunks.get(previous)[1] <= 2 * CONTEXT) {
                hunks.get(previous)[1] = end;
            } else {
                hunks.add(new int[]{start, end});
            }
        }

        List<String> out = new ArrayList<>();
        int oldLine = 1;
        int newLine = 1;
        int consumed = 0;
        for (int[] hunk : hunks) {
            for (int k = consumed; k < hunk[0]; k++) {
                Op skipped = ops.get(k);
                if (skipped.type() != '+') {
                    oldLine++;
                }
                if (skipped.type() != '-') {
                    newLine++;
                }
            }
            consumed = hunk[1];

            int hunkOldStart = oldLine;
            int hunkNewStart = newLine;
            List<String> body = new ArrayList<>(hunk[1] - hunk[0]);
            for (int k = hunk[0]; k < hunk[1]; k++) {
                Op op = ops.get(k);
                body.add(renderBodyLine(op));
                if (op.type() != '+') {
                    oldLine++;
                }
                if (op.type() != '-') {
                    newLine++;
                }
            }
            // the unified convention: an empty side reports position 0 (a
            // pure insertion happened after the previous line, a pure
            // deletion before the next)
            int oldCount = oldLine - hunkOldStart;
            int newCount = newLine - hunkNewStart;
            out.add("@@ -" + (oldCount == 0 ? hunkOldStart - 1 : hunkOldStart) + "," + oldCount
                    + " +" + (newCount == 0 ? hunkNewStart - 1 : hunkNewStart) + "," + newCount
                    + " @@\n");
            out.addAll(body);
        }
        return out;
    }

    /**
     * One body line of a hunk: the op marker and the line content without
     * its terminator; an unterminated final line carries the conventional
     * no-newline marker directly after it.
     */
    private static String renderBodyLine(Op op) {
        String line = op.line();
        if (line.endsWith("\n")) {
            return op.type() + line.substring(0, line.length() - 1) + "\n";
        }
        return op.type() + line + "\n\\ No newline at end of file\n";
    }
}
