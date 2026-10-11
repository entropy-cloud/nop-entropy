package io.nop.lint.core.fix;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unified diff formatter matrix (roadmap item 25 Phase 2): no-change →
 * empty output, single-line replacement, insertion and deletion hunks,
 * multi-hunk output with correct headers, the no-newline-at-eof marker, and
 * empty-file boundaries.
 */
public class TestUnifiedDiff {

    @Test
    public void identicalTextsRenderNoDiff() {
        assertEquals("", UnifiedDiff.of("a.java", "class A {\n}\n", "class A {\n}\n"));
    }

    @Test
    public void singleLineReplacementRendersOneHunk() {
        String original = "class A {\n    void m() {\n        foo();\n    }\n}\n";
        String fixed = "class A {\n    void m() {\n        bar();\n    }\n}\n";

        String diff = UnifiedDiff.of("a.java", original, fixed);

        // a 5-line file with 3 context lines: the hunk spans the whole file
        assertEquals("""
                --- a/a.java
                +++ b/a.java
                @@ -1,5 +1,5 @@
                 class A {
                     void m() {
                -        foo();
                +        bar();
                     }
                 }
                """, diff);
    }

    @Test
    public void insertionRendersPlusLinesWithSurroundingContext() {
        String original = "one\ntwo\nfour\n";
        String fixed = "one\ntwo\nthree\nfour\n";

        String diff = UnifiedDiff.of("f.txt", original, fixed);

        // the short file's context padding spans it entirely
        assertEquals("""
                --- a/f.txt
                +++ b/f.txt
                @@ -1,3 +1,4 @@
                 one
                 two
                +three
                 four
                """, diff);
    }

    @Test
    public void deletionRendersMinusLinesWithSurroundingContext() {
        String original = "one\ntwo\nthree\nfour\n";
        String fixed = "one\ntwo\nfour\n";

        String diff = UnifiedDiff.of("f.txt", original, fixed);

        assertEquals("""
                --- a/f.txt
                +++ b/f.txt
                @@ -1,4 +1,3 @@
                 one
                 two
                -three
                 four
                """, diff);
    }

    @Test
    public void distantChangesRenderAsSeparateHunks() {
        String original = "l1\nl2\nl3\nl4\nl5\nl6\nl7\nl8\nl9\nl10\nl11\nl12\nl13\nl14\n";
        String fixed = "L1\nl2\nl3\nl4\nl5\nl6\nl7\nl8\nl9\nl10\nl11\nl12\nl13\nL14\n";

        String diff = UnifiedDiff.of("f.txt", original, fixed);

        // each change carries its 3-line context; the 9-line gap in the
        // middle keeps the hunks separate
        assertTrue(diff.contains("@@ -1,4 +1,4 @@"), diff);
        assertTrue(diff.contains("-l1\n+L1\n"), diff);
        assertTrue(diff.contains("@@ -11,4 +11,4 @@"), diff);
        assertTrue(diff.contains("-l14\n+L14\n"), diff);
    }

    @Test
    public void missingFinalNewlineCarriesTheMarker() {
        String diff = UnifiedDiff.of("f.txt", "a\nb", "a\nB");

        assertEquals("""
                --- a/f.txt
                +++ b/f.txt
                @@ -1,2 +1,2 @@
                 a
                -b
                \\ No newline at end of file
                +B
                \\ No newline at end of file
                """, diff);
    }

    @Test
    public void emptyOriginalRendersWholeFileAsAdded() {
        String diff = UnifiedDiff.of("f.txt", "", "one\ntwo\n");

        assertEquals("""
                --- a/f.txt
                +++ b/f.txt
                @@ -0,0 +1,2 @@
                +one
                +two
                """, diff);
    }

    @Test
    public void emptyFixedRendersWholeFileAsRemoved() {
        String diff = UnifiedDiff.of("f.txt", "one\ntwo\n", "");

        assertEquals("""
                --- a/f.txt
                +++ b/f.txt
                @@ -1,2 +0,0 @@
                -one
                -two
                """, diff);
    }
}
