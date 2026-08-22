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

class TestUnifiedDiffParser {

    @Test
    void testParseSimpleDiff() {
        String diffText = "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -1,2 +1,3 @@\n" +
                " Hello\n" +
                "-World\n" +
                "+Git\n" +
                "+World\n";

        UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffText);

        assertNotNull(diff);
        assertEquals("a/file.txt", diff.getOldPath());
        assertEquals("b/file.txt", diff.getNewPath());
        assertEquals(1, diff.getHunks().size());

        UnifiedDiffHunk hunk = diff.getHunks().get(0);
        assertEquals(1, hunk.getOldStartLine());
        assertEquals(2, hunk.getOldLineCount());
        assertEquals(1, hunk.getNewStartLine());
        assertEquals(3, hunk.getNewLineCount());
        assertEquals(4, hunk.getLines().size());

        // 检查行内容
        assertTrue(hunk.getLines().get(0).isContext());
        assertEquals("Hello", hunk.getLines().get(0).getContent());

        assertTrue(hunk.getLines().get(1).isDelete());
        assertEquals("World", hunk.getLines().get(1).getContent());

        assertTrue(hunk.getLines().get(2).isAdd());
        assertEquals("Git", hunk.getLines().get(2).getContent());

        assertTrue(hunk.getLines().get(3).isAdd());
        assertEquals("World", hunk.getLines().get(3).getContent());
    }

    @Test
    void testParseDiffWithGitHeader() {
        String diffText = "diff --git a/file.txt b/file.txt\n" +
                "index 1234567..abcdef 100644\n" +
                "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -1,2 +1,3 @@\n" +
                " Hello\n" +
                "-World\n" +
                "+Git\n" +
                "+World\n";

        UnifiedDiffFile file = UnifiedDiffParser.parse(diffText);

        assertNotNull(file);
        assertEquals("a/file.txt b/file.txt", file.getGitDiffSource());
        assertEquals(1, file.getFileCount());

        UnifiedDiff diff = file.getDiffs().get(0);
        assertEquals("a/file.txt", diff.getOldPath());
        assertEquals("b/file.txt", diff.getNewPath());
    }

    @Test
    void testParseNewFile() {
        String diffText = "--- /dev/null\n" +
                "+++ b/newfile.txt\n" +
                "@@ -0,0 +1,3 @@\n" +
                "+Hello\n" +
                "+World\n" +
                "+!\n";

        UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffText);

        assertNotNull(diff);
        assertTrue(diff.isNewFile());
        assertFalse(diff.isDeletedFile());

        UnifiedDiffHunk hunk = diff.getHunks().get(0);
        assertEquals(0, hunk.getOldStartLine());
        assertEquals(0, hunk.getOldLineCount());
        assertEquals(1, hunk.getNewStartLine());
        assertEquals(3, hunk.getNewLineCount());
    }

    @Test
    void testParseDeletedFile() {
        String diffText = "--- a/oldfile.txt\n" +
                "+++ /dev/null\n" +
                "@@ -1,2 +0,0 @@\n" +
                "-Hello\n" +
                "-World\n";

        UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffText);

        assertNotNull(diff);
        assertFalse(diff.isNewFile());
        assertTrue(diff.isDeletedFile());
    }

    @Test
    void testParseHunk() {
        String hunkText = "@@ -1,2 +1,3 @@ some context\n" +
                " Hello\n" +
                "-World\n" +
                "+Git\n" +
                "+World\n";

        UnifiedDiffHunk hunk = UnifiedDiffParser.parseHunk(hunkText);

        assertNotNull(hunk);
        assertEquals(1, hunk.getOldStartLine());
        assertEquals(2, hunk.getOldLineCount());
        assertEquals(1, hunk.getNewStartLine());
        assertEquals(3, hunk.getNewLineCount());
        assertEquals("some context", hunk.getSectionHeading());
        assertEquals(4, hunk.getLines().size());
    }

    @Test
    void testParseMultipleFiles() {
        String diffText = "diff --git a/file1.txt b/file1.txt\n" +
                "--- a/file1.txt\n" +
                "+++ b/file1.txt\n" +
                "@@ -1 +1 @@\n" +
                "-a\n" +
                "+b\n" +
                "diff --git a/file2.txt b/file2.txt\n" +
                "--- a/file2.txt\n" +
                "+++ b/file2.txt\n" +
                "@@ -1 +1 @@\n" +
                "-c\n" +
                "+d\n";

        UnifiedDiffFile file = UnifiedDiffParser.parse(diffText);

        assertNotNull(file);
        assertEquals(2, file.getFileCount());

        assertEquals("a/file1.txt", file.getDiffs().get(0).getOldPath());
        assertEquals("a/file2.txt", file.getDiffs().get(1).getOldPath());
    }

    @Test
    void testParseEmpty() {
        UnifiedDiffFile file = UnifiedDiffParser.parse("");
        assertTrue(file.isEmpty());

        UnifiedDiffFile file2 = UnifiedDiffParser.parse(null);
        assertTrue(file2.isEmpty());
    }

    @Test
    void testParseDiffWithBlankLines() {
        // 空行在 unified diff 中表示为单个前缀字符：context 行为 " "，add 行为 "+"，delete 行为 "-"
        String diffText = "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -1,5 +1,5 @@\n" +
                " first\n" +
                " \n" +
                "-\n" +
                "-old\n" +
                "+\n" +
                "+new\n";

        UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffText);

        assertNotNull(diff);
        UnifiedDiffHunk hunk = diff.getHunks().get(0);
        assertEquals(6, hunk.getLines().size());

        assertTrue(hunk.getLines().get(0).isContext());
        assertEquals("first", hunk.getLines().get(0).getContent());

        assertTrue(hunk.getLines().get(1).isContext());
        assertEquals("", hunk.getLines().get(1).getContent());

        assertTrue(hunk.getLines().get(2).isDelete());
        assertEquals("", hunk.getLines().get(2).getContent());

        assertTrue(hunk.getLines().get(3).isDelete());
        assertEquals("old", hunk.getLines().get(3).getContent());

        assertTrue(hunk.getLines().get(4).isAdd());
        assertEquals("", hunk.getLines().get(4).getContent());

        assertTrue(hunk.getLines().get(5).isAdd());
        assertEquals("new", hunk.getLines().get(5).getContent());

        // 空行 round-trip 后仍为单个前缀字符
        assertEquals(" ", hunk.getLines().get(1).toDiffString());
        assertEquals("+", hunk.getLines().get(4).toDiffString());
    }

    @Test
    void testFromDiffStringBlankLine() {
        UnifiedDiffLine contextLine = UnifiedDiffLine.fromDiffString(" ");
        assertNotNull(contextLine);
        assertTrue(contextLine.isContext());
        assertEquals("", contextLine.getContent());

        UnifiedDiffLine addLine = UnifiedDiffLine.fromDiffString("+");
        assertNotNull(addLine);
        assertTrue(addLine.isAdd());
        assertEquals("", addLine.getContent());

        UnifiedDiffLine deleteLine = UnifiedDiffLine.fromDiffString("-");
        assertNotNull(deleteLine);
        assertTrue(deleteLine.isDelete());
        assertEquals("", deleteLine.getContent());
    }
}
