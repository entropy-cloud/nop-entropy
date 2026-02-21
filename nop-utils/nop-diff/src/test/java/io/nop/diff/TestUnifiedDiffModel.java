/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.diff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestUnifiedDiffModel {

    @Test
    void testDiffLine() {
        UnifiedDiffLine contextLine = UnifiedDiffLine.context("Hello");
        assertTrue(contextLine.isContext());
        assertFalse(contextLine.isAdd());
        assertFalse(contextLine.isDelete());
        assertEquals(" Hello", contextLine.toDiffString());

        UnifiedDiffLine addLine = UnifiedDiffLine.add("World");
        assertTrue(addLine.isAdd());
        assertEquals("+World", addLine.toDiffString());

        UnifiedDiffLine deleteLine = UnifiedDiffLine.delete("Old");
        assertTrue(deleteLine.isDelete());
        assertEquals("-Old", deleteLine.toDiffString());
    }

    @Test
    void testDiffLineFromDiffString() {
        UnifiedDiffLine line1 = UnifiedDiffLine.fromDiffString(" Hello");
        assertNotNull(line1);
        assertTrue(line1.isContext());
        assertEquals("Hello", line1.getContent());

        UnifiedDiffLine line2 = UnifiedDiffLine.fromDiffString("+World");
        assertNotNull(line2);
        assertTrue(line2.isAdd());
        assertEquals("World", line2.getContent());

        UnifiedDiffLine line3 = UnifiedDiffLine.fromDiffString("-Old");
        assertNotNull(line3);
        assertTrue(line3.isDelete());
        assertEquals("Old", line3.getContent());

        // 无效的行
        UnifiedDiffLine line4 = UnifiedDiffLine.fromDiffString("invalid");
        assertNull(line4);
    }

    @Test
    void testDiffHunkBuilder() {
        UnifiedDiffHunk hunk = UnifiedDiffHunk.builder()
                .oldStartLine(1)
                .newStartLine(1)
                .addContextLine("Hello")
                .addDeleteLine("World")
                .addAddLine("Git")
                .addAddLine("World")
                .autoCalculateCounts()
                .build();

        assertEquals(1, hunk.getOldStartLine());
        assertEquals(2, hunk.getOldLineCount());  // context + delete
        assertEquals(3, hunk.getNewLineCount());  // context + add + add
        assertEquals(4, hunk.getLines().size());
    }

    @Test
    void testDiffHunkToDiffString() {
        UnifiedDiffHunk hunk = UnifiedDiffHunk.builder()
                .oldStartLine(1)
                .oldLineCount(2)
                .newStartLine(1)
                .newLineCount(3)
                .addContextLine("Hello")
                .addDeleteLine("World")
                .addAddLine("Git")
                .addAddLine("World")
                .build();

        String diffString = hunk.toDiffString();

        assertTrue(diffString.startsWith("@@ -1,2 +1,3 @@"));
        assertTrue(diffString.contains(" Hello\n"));
        assertTrue(diffString.contains("-World\n"));
        assertTrue(diffString.contains("+Git\n"));
        assertTrue(diffString.contains("+World\n"));
    }

    @Test
    void testDiffHunkParseHeader() {
        Object[] parts = UnifiedDiffHunk.parseHeader("@@ -1,2 +1,3 @@");
        assertEquals(1, parts[0]);  // oldStartLine
        assertEquals(2, parts[1]);  // oldLineCount
        assertEquals(1, parts[2]);  // newStartLine
        assertEquals(3, parts[3]);  // newLineCount
        assertNull(parts[4]);       // sectionHeading

        Object[] parts2 = UnifiedDiffHunk.parseHeader("@@ -10 +10,2 @@ some context");
        assertEquals(10, parts2[0]);
        assertEquals(1, parts2[1]);  // 没有逗号时默认为 1
        assertEquals(10, parts2[2]);
        assertEquals(2, parts2[3]);
        assertEquals("some context", parts2[4]);
    }

    @Test
    void testUnifiedDiffBuilder() {
        UnifiedDiff diff = UnifiedDiff.builder()
                .oldPath("a/file.txt")
                .newPath("b/file.txt")
                .addExtendedHeader("index 1234567..abcdef 100644")
                .addHunk(UnifiedDiffHunk.builder()
                        .oldStartLine(1)
                        .oldLineCount(1)
                        .newStartLine(1)
                        .newLineCount(1)
                        .addContextLine("line1")
                        .build())
                .build();

        assertEquals("a/file.txt", diff.getOldPath());
        assertEquals("b/file.txt", diff.getNewPath());
        assertFalse(diff.isNewFile());
        assertFalse(diff.isDeletedFile());
        assertFalse(diff.isRename());
        assertEquals(1, diff.getHunks().size());
    }

    @Test
    void testUnifiedDiffNewFile() {
        UnifiedDiff diff = UnifiedDiff.builder()
                .oldPath("/dev/null")
                .newPath("b/newfile.txt")
                .build();

        assertTrue(diff.isNewFile());
        assertFalse(diff.isDeletedFile());
    }

    @Test
    void testUnifiedDiffDeletedFile() {
        UnifiedDiff diff = UnifiedDiff.builder()
                .oldPath("a/oldfile.txt")
                .newPath("/dev/null")
                .build();

        assertFalse(diff.isNewFile());
        assertTrue(diff.isDeletedFile());
    }

    @Test
    void testUnifiedDiffRename() {
        UnifiedDiff diff = UnifiedDiff.builder()
                .oldPath("a/oldname.txt")
                .newPath("b/newname.txt")
                .build();

        assertTrue(diff.isRename());
    }

    @Test
    void testUnifiedDiffGetTargetPath() {
        UnifiedDiff diff1 = UnifiedDiff.builder()
                .oldPath("a/file.txt")
                .newPath("b/file.txt")
                .build();
        assertEquals("file.txt", diff1.getTargetPath());

        UnifiedDiff diff2 = UnifiedDiff.builder()
                .oldPath("/dev/null")
                .newPath("b/newfile.txt")
                .build();
        assertEquals("newfile.txt", diff2.getTargetPath());

        UnifiedDiff diff3 = UnifiedDiff.builder()
                .oldPath("a/oldfile.txt")
                .newPath("/dev/null")
                .build();
        assertEquals("oldfile.txt", diff3.getTargetPath());
    }

    @Test
    void testUnifiedDiffFile() {
        UnifiedDiff diff1 = UnifiedDiff.builder()
                .oldPath("a/file1.txt")
                .newPath("b/file1.txt")
                .build();

        UnifiedDiff diff2 = UnifiedDiff.builder()
                .oldPath("a/file2.txt")
                .newPath("b/file2.txt")
                .build();

        UnifiedDiffFile file = UnifiedDiffFile.builder()
                .gitDiffSource("a/file1.txt b/file1.txt")
                .addDiff(diff1)
                .addDiff(diff2)
                .build();

        assertEquals("a/file1.txt b/file1.txt", file.getGitDiffSource());
        assertEquals(2, file.getFileCount());
        assertFalse(file.isEmpty());
    }

    @Test
    void testUnifiedDiffFileOf() {
        UnifiedDiff diff = UnifiedDiff.builder()
                .oldPath("a/file.txt")
                .newPath("b/file.txt")
                .build();

        UnifiedDiffFile file = UnifiedDiffFile.of(diff);

        assertEquals(1, file.getFileCount());
        assertSame(diff, file.getDiffs().get(0));
    }
}
