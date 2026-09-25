package io.nop.lint.core.lang;

import io.nop.lint.core.NopLintException;
import io.nop.treesitter.parser.incremental.TSInputEdit;
import io.nop.treesitter.parser.incremental.TSPoint;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edit matrix for {@link EditCalculator#diff} (plan Phase 1): every cell
 * asserts the exact {@link TSInputEdit} fields, and the global transformation
 * identity — applying the produced hunks to the old source reproduces the new
 * source byte for byte — is asserted for every case, which is the legality
 * contract {@code TSParser.parseIncremental} relies on.
 */
public class TestEditCalculator {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Reconstructs the new source from the old source plus the edit sequence.
     * Byte coordinates follow the {@link TSInputEdit} record contract:
     * {@code newEndByte} is anchored at {@code startByte + insertedLength},
     * so the inserted bytes live at {@code startByte + shift} in the new
     * source, with {@code shift} the accumulated {@code newEndByte -
     * oldEndByte} of the preceding edits. Also asserts the anchoring bounds
     * and the non-overlap / ordering contract of the sequence.
     */
    private static byte[] applyEdits(byte[] oldSource, byte[] newSource, List<TSInputEdit> edits) {
        List<TSInputEdit> sorted = new ArrayList<>(edits);
        sorted.sort(Comparator.comparingInt(TSInputEdit::startByte));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pos = 0;
        int shift = 0;
        for (TSInputEdit edit : sorted) {
            assertTrue(edit.startByte() >= pos,
                    "hunks must be pairwise non-overlapping and ascending: " + edits);
            assertTrue(edit.newEndByte() >= edit.startByte(),
                    "anchored newEndByte must never precede startByte: " + edit);
            assertTrue(edit.newEndByte() <= newSource.length,
                    "anchored newEndByte must stay within the new source: " + edit);
            out.write(oldSource, pos, edit.startByte() - pos);
            int inserted = edit.newEndByte() - edit.startByte();
            out.write(newSource, edit.startByte() + shift, inserted);
            shift += edit.newEndByte() - edit.oldEndByte();
            pos = edit.oldEndByte();
        }
        out.write(oldSource, pos, oldSource.length - pos);
        return out.toByteArray();
    }

    private static void assertTransforms(byte[] oldSource, byte[] newSource) {
        List<TSInputEdit> edits = EditCalculator.diff(oldSource, newSource);
        assertTrue(Arrays.equals(newSource, applyEdits(oldSource, newSource, edits)),
                "applying the hunks must reproduce the new source; edits=" + edits
                        + " old=" + StandardCharsets.UTF_8.decode(java.nio.ByteBuffer.wrap(oldSource))
                        + " new=" + StandardCharsets.UTF_8.decode(java.nio.ByteBuffer.wrap(newSource)));
    }

    // ==================== identical inputs → empty ====================

    @Test
    public void identicalInputsYieldEmptyList() {
        assertTrue(EditCalculator.diff(bytes(""), bytes("")).isEmpty());
        assertTrue(EditCalculator.diff(bytes("class A {}"), bytes("class A {}")).isEmpty());
        assertTrue(EditCalculator.diff(bytes(" André\n"), bytes(" André\n")).isEmpty());
        assertTrue(EditCalculator.diff(bytes("a\r\nb\r\n"), bytes("a\r\nb\r\n")).isEmpty());
    }

    // ==================== empty → non-empty / non-empty → empty ====================

    @Test
    public void emptyToNonEmptyIsSingleInsertAtOrigin() {
        TSInputEdit edit = EditCalculator.diff(bytes(""), bytes("hello")).get(0);
        assertEquals(1, EditCalculator.diff(bytes(""), bytes("hello")).size());
        assertEquals(0, edit.startByte());
        assertEquals(0, edit.oldEndByte());
        assertEquals(5, edit.newEndByte());
        assertEquals(TSPoint.ZERO, edit.startPoint());
        assertEquals(TSPoint.ZERO, edit.oldEndPoint());
        assertEquals(new TSPoint(0, 5), edit.newEndPoint());
        assertTransforms(bytes(""), bytes("hello"));
    }

    @Test
    public void nonEmptyToEmptyIsSingleWholeDelete() {
        List<TSInputEdit> edits = EditCalculator.diff(bytes("hello"), bytes(""));
        assertEquals(1, edits.size());
        TSInputEdit edit = edits.get(0);
        assertEquals(0, edit.startByte());
        assertEquals(5, edit.oldEndByte());
        assertEquals(0, edit.newEndByte());
        assertEquals(new TSPoint(0, 5), edit.oldEndPoint());
        assertEquals(TSPoint.ZERO, edit.newEndPoint());
        assertTransforms(bytes("hello"), bytes(""));
    }

    // ==================== pure insert / delete / replace ====================

    @Test
    public void pureInsertIsZeroWidthInOldSource() {
        byte[] oldSource = bytes("abXc");
        byte[] newSource = bytes("abXYc");
        List<TSInputEdit> edits = EditCalculator.diff(oldSource, newSource);
        assertEquals(1, edits.size());
        TSInputEdit edit = edits.get(0);
        // The greedy common prefix consumes 'X', so the insertion point sits
        // before 'c' — a minimal single-edit decomposition.
        assertEquals(3, edit.startByte());
        assertEquals(3, edit.oldEndByte());
        assertEquals(4, edit.newEndByte());
        assertEquals(new TSPoint(0, 3), edit.startPoint());
        assertEquals(new TSPoint(0, 3), edit.oldEndPoint());
        assertEquals(new TSPoint(0, 4), edit.newEndPoint());
        assertTransforms(oldSource, newSource);
    }

    @Test
    public void pureDeleteIsZeroWidthInNewSource() {
        byte[] oldSource = bytes("abXYc");
        byte[] newSource = bytes("abXc");
        List<TSInputEdit> edits = EditCalculator.diff(oldSource, newSource);
        assertEquals(1, edits.size());
        TSInputEdit edit = edits.get(0);
        assertEquals(3, edit.startByte());
        assertEquals(4, edit.oldEndByte());
        assertEquals(3, edit.newEndByte());
        assertTransforms(oldSource, newSource);
    }

    @Test
    public void pureReplaceKeepsCommonShoulders() {
        byte[] oldSource = bytes("ab[OLD]c");
        byte[] newSource = bytes("ab[MUCH-LONGER-NEW]c");
        List<TSInputEdit> edits = EditCalculator.diff(oldSource, newSource);
        assertEquals(1, edits.size());
        TSInputEdit edit = edits.get(0);
        // The '[' bracket is common prefix content (byte 2), the ']' and 'c'
        // are common suffix content: the hunk covers only "OLD" → "MUCH-LONGER-NEW".
        assertEquals(3, edit.startByte());
        assertEquals(6, edit.oldEndByte());
        assertEquals(18, edit.newEndByte());
        assertEquals(new TSPoint(0, 3), edit.startPoint());
        assertEquals(new TSPoint(0, 6), edit.oldEndPoint());
        assertEquals(new TSPoint(0, 18), edit.newEndPoint());
        assertTransforms(oldSource, newSource);
    }

    // ==================== multi-hunk ====================

    @Test
    public void multiHunkSplitsAtUnchangedLines() {
        byte[] oldSource = bytes("a\nb\nc\nd\ne\n");
        byte[] newSource = bytes("a\nX\nc\nd\nY\n");
        List<TSInputEdit> edits = EditCalculator.diff(oldSource, newSource);
        assertEquals(2, edits.size());

        TSInputEdit first = edits.get(0);
        assertEquals(2, first.startByte());
        assertEquals(3, first.oldEndByte());
        assertEquals(3, first.newEndByte());
        assertEquals(new TSPoint(1, 0), first.startPoint());
        assertEquals(new TSPoint(1, 1), first.oldEndPoint());
        assertEquals(new TSPoint(1, 1), first.newEndPoint());

        TSInputEdit second = edits.get(1);
        assertEquals(8, second.startByte());
        assertEquals(9, second.oldEndByte());
        assertEquals(9, second.newEndByte());
        assertEquals(new TSPoint(4, 0), second.startPoint());
        assertEquals(new TSPoint(4, 1), second.oldEndPoint());
        assertEquals(new TSPoint(4, 1), second.newEndPoint());
        assertTransforms(oldSource, newSource);
    }

    @Test
    public void hunksAreAscendingAndNonOverlapping() {
        byte[] oldSource = bytes("one\ntwo\nthree\nfour\nfive\nsix\nseven\n");
        byte[] newSource = bytes("ONE\ntwo\nTHREE\nfour\nfive\nSIX\nseven\n");
        List<TSInputEdit> edits = EditCalculator.diff(oldSource, newSource);
        assertEquals(3, edits.size());
        for (int i = 1; i < edits.size(); i++) {
            assertTrue(edits.get(i).startByte() >= edits.get(i - 1).oldEndByte());
        }
        assertTransforms(oldSource, newSource);
    }

    @Test
    public void hunksSeparatedBySingleUnchangedByte() {
        byte[] oldSource = bytes("a\n\nb\n");
        byte[] newSource = bytes("X\n\nY\n");
        List<TSInputEdit> edits = EditCalculator.diff(oldSource, newSource);
        assertEquals(2, edits.size());
        assertEquals(2, edits.get(1).startByte() - edits.get(0).oldEndByte(),
                "the trimmed '\\n' plus the matched empty line separate the two hunks");
        assertTransforms(oldSource, newSource);
    }

    // ==================== large rewrite with preserved shoulders ====================

    @Test
    public void largeRewriteKeepsPrefixAndSuffixOut() {
        byte[] prefix = bytes("public class Big {\n");
        byte[] suffix = bytes("\n}\n");
        byte[] oldBody = bytes("  void a() {}\n  void b() {}\n  void c() {}\n");
        byte[] newBody = bytes("  // everything replaced\n  int field = 42;\n  void z() { if (x) { y(); } }\n");
        ByteArrayOutputStream oldOut = new ByteArrayOutputStream();
        oldOut.writeBytes(prefix);
        oldOut.writeBytes(oldBody);
        oldOut.writeBytes(suffix);
        ByteArrayOutputStream newOut = new ByteArrayOutputStream();
        newOut.writeBytes(prefix);
        newOut.writeBytes(newBody);
        newOut.writeBytes(suffix);
        byte[] oldSource = oldOut.toByteArray();
        byte[] newSource = newOut.toByteArray();

        List<TSInputEdit> edits = EditCalculator.diff(oldSource, newSource);
        assertEquals(1, edits.size());
        TSInputEdit edit = edits.get(0);
        // The greedy byte trim crosses the line boundary: the hunk keeps the
        // "public class Big {" header out and consumes only the two-space
        // indent (start = 19 + 2), plus the tail down to "c() {}" / "y(); } }".
        assertEquals(21, edit.startByte(), "common prefix must stay outside the hunk");
        assertEquals(59, edit.oldEndByte());
        assertEquals(91, edit.newEndByte());
        assertTransforms(oldSource, newSource);
    }

    // ==================== CRLF and multibyte boundaries ====================

    @Test
    public void crlfLineEndingsCountCarriageReturnIntoColumns() {
        byte[] oldSource = bytes("first\r\nsecond\r\nthird\r\n");
        byte[] newSource = bytes("first\r\nSECOND\r\nthird\r\n");
        List<TSInputEdit> edits = EditCalculator.diff(oldSource, newSource);
        assertEquals(1, edits.size());
        TSInputEdit edit = edits.get(0);
        // "first\r\n" (7 bytes) is common prefix, "d\r\nthird\r\n" common suffix:
        // the byte-trimmed hunk covers exactly "secon" → "SECON" (row 1).
        assertEquals(7, edit.startByte());
        assertEquals(13, edit.oldEndByte());
        assertEquals(13, edit.newEndByte());
        assertEquals(new TSPoint(1, 0), edit.startPoint());
        assertEquals(new TSPoint(1, 6), edit.oldEndPoint());
        assertEquals(new TSPoint(1, 6), edit.newEndPoint());
        assertTransforms(oldSource, newSource);
    }

    @Test
    public void multibyteCharactersKeepByteAccurateColumns() {
        byte[] oldSource = bytes("String s = \"中文\"; // éà\nint x = 1;\n");
        byte[] newSource = bytes("String s = \"中文!\"; // éà\nint x = 1;\n");
        List<TSInputEdit> edits = EditCalculator.diff(oldSource, newSource);
        assertEquals(1, edits.size());
        TSInputEdit edit = edits.get(0);
        // '中'/'文' are 3 bytes each (offsets 12..17); the greedy prefix consumes
        // them, so the '!' insertion point is byte column 18, row 0.
        assertEquals(18, edit.startByte());
        assertEquals(18, edit.oldEndByte());
        assertEquals(19, edit.newEndByte());
        assertEquals(new TSPoint(0, 18), edit.startPoint());
        assertEquals(new TSPoint(0, 18), edit.oldEndPoint());
        assertEquals(new TSPoint(0, 19), edit.newEndPoint());
        assertTransforms(oldSource, newSource);
    }

    @Test
    public void editInsideMultibyteRunStaysByteExact() {
        byte[] oldSource = bytes("a中文b");
        byte[] newSource = bytes("a中X文b");
        List<TSInputEdit> edits = EditCalculator.diff(oldSource, newSource);
        assertEquals(1, edits.size());
        assertTransforms(oldSource, newSource);
    }

    // ==================== delete-heavy multi-hunk anchoring ====================

    @Test
    public void deleteHeavySecondHunkMergesToStayWithinNewSource() {
        // A large deletion early in the file shifts every later hunk's
        // old-coord start past the new source's end; an absolute-offset
        // encoding of the trailing hunk would violate the TSInputEdit
        // invariants (newEndByte < startByte) or leave the new source
        // entirely. The anchored encoding must merge the hunks so the
        // sequence stays legal while the total delta stays exact.
        byte[] oldSource = bytes("AAAAAAAAAA\nM\nB\n");
        byte[] newSource = bytes("X\nM\nY\n");
        List<TSInputEdit> edits = EditCalculator.diff(oldSource, newSource);
        assertEquals(1, edits.size(), "delete-heavy hunks must merge into one legal edit");
        TSInputEdit edit = edits.get(0);
        assertEquals(0, edit.startByte());
        assertEquals(14, edit.oldEndByte());
        assertEquals(5, edit.newEndByte(), "anchored end = startByte + inserted length");
        assertTransforms(oldSource, newSource);
    }

    @Test
    public void insertionHeavySecondHunkKeepsMinimalMultiHunks() {
        byte[] oldSource = bytes("A\nM\nB\n");
        byte[] newSource = bytes("XXXXXXXXXX\nM\nY\n");
        List<TSInputEdit> edits = EditCalculator.diff(oldSource, newSource);
        assertEquals(2, edits.size(), "legal hunks must stay minimal");
        TSInputEdit first = edits.get(0);
        assertEquals(0, first.startByte());
        assertEquals(1, first.oldEndByte());
        assertEquals(10, first.newEndByte(),
                "anchored end = startByte + inserted length; the common '\\n' tail is trimmed");
        TSInputEdit second = edits.get(1);
        assertEquals(4, second.startByte());
        assertEquals(5, second.oldEndByte());
        assertEquals(5, second.newEndByte(),
                "the 'B' → 'Y' replace anchors newEndByte at startByte + 1");
        assertTransforms(oldSource, newSource);
    }

    // ==================== failure paths are explicit ====================

    @Test
    public void nullOldSourceFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> EditCalculator.diff(null, bytes("x")));
        assertTrue(ex.getMessage().contains("oldSource"));
    }

    @Test
    public void nullNewSourceFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> EditCalculator.diff(bytes("x"), null));
        assertTrue(ex.getMessage().contains("newSource"));
    }

    @Test
    void pathologicalRewriteFallsBackToOneHunkAndStaysByteCorrect() {
        // plan 11 Phase 3 (audit M3-A): a ~1000-line full replacement drives
        // the Myers trace over its ceiling — the diff falls back to ONE
        // whole-middle hunk and the result stays byte-correct (apply the
        // edit back onto the old source and the new source reappears)
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            a.append("old line ").append(i).append("\n");
            b.append("completely new line ").append(i).append("\n");
        }
        byte[] oldSource = a.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] newSource = b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        List<io.nop.treesitter.parser.incremental.TSInputEdit> edits =
                EditCalculator.diff(oldSource, newSource);

        assertEquals(1, edits.size(),
                "the over-budget diff degrades to a single whole-middle hunk");
        var edit = edits.get(0);
        byte[] rebuilt = new byte[(int) (edit.startByte()
                + (edit.newEndByte() - edit.startByte())
                + (oldSource.length - edit.oldEndByte()))];
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(rebuilt);
        buf.put(java.nio.ByteBuffer.wrap(oldSource, 0, edit.startByte()));
        buf.put(java.nio.ByteBuffer.wrap(newSource, edit.startByte(),
                edit.newEndByte() - edit.startByte()));
        buf.put(java.nio.ByteBuffer.wrap(oldSource, edit.oldEndByte(),
                oldSource.length - edit.oldEndByte()));
        org.junit.jupiter.api.Assertions.assertArrayEquals(newSource, rebuilt,
                "applying the fallback hunk must rebuild the new source byte for byte");
    }
}
