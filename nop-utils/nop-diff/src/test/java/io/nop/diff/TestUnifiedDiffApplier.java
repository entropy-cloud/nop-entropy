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

class TestUnifiedDiffApplier {

    @Test
    void testApplySimpleDiff() {
        String originalText = "Hello\nWorld\n";
        String diffText = "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -1,2 +1,3 @@\n" +
                " Hello\n" +
                "-World\n" +
                "+Git\n" +
                "+World\n";

        String result = UnifiedDiffApplier.parseAndApply(originalText, diffText);

        assertEquals("Hello\nGit\nWorld\n", result);
    }

    @Test
    void testApplyAddLines() {
        String originalText = "line1\nline2\n";
        String diffText = "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -1,2 +1,4 @@\n" +
                " line1\n" +
                "+newline\n" +
                " line2\n" +
                "+another\n";

        String result = UnifiedDiffApplier.parseAndApply(originalText, diffText);

        assertEquals("line1\nnewline\nline2\nanother\n", result);
    }

    @Test
    void testApplyDeleteLines() {
        String originalText = "line1\nline2\nline3\n";
        String diffText = "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -1,3 +1,2 @@\n" +
                " line1\n" +
                "-line2\n" +
                " line3\n";

        String result = UnifiedDiffApplier.parseAndApply(originalText, diffText);

        assertEquals("line1\nline3\n", result);
    }

    @Test
    void testApplyNewFile() {
        String diffText = "--- /dev/null\n" +
                "+++ b/newfile.txt\n" +
                "@@ -0,0 +1,3 @@\n" +
                "+Hello\n" +
                "+World\n" +
                "+!\n";

        UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffText);
        String result = UnifiedDiffApplier.applyDiff("", diff);

        assertEquals("Hello\nWorld\n!\n", result);
    }

    @Test
    void testApplyMultipleHunks() {
        String originalText = "a\nb\nc\nd\ne\n";
        String diffText = "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -1,2 +1,2 @@\n" +
                " a\n" +
                "-b\n" +
                "+B\n" +
                "@@ -4,2 +4,2 @@\n" +
                " d\n" +
                "-e\n" +
                "+E\n";

        String result = UnifiedDiffApplier.parseAndApply(originalText, diffText);

        assertEquals("a\nB\nc\nd\nE\n", result);
    }

    @Test
    void testApplyHunk() {
        String originalText = "line1\nline2\nline3\n";

        UnifiedDiffHunk hunk = UnifiedDiffHunk.builder()
                .oldStartLine(2)
                .oldLineCount(1)
                .newStartLine(2)
                .newLineCount(1)
                .addDeleteLine("line2")
                .addAddLine("LINE2")
                .build();

        String result = UnifiedDiffApplier.applyHunk(originalText, hunk);

        assertEquals("line1\nLINE2\nline3\n", result);
    }

    @Test
    void testReverseDiff() {
        String originalText = "Hello\nWorld\n";
        String diffText = "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -1,2 +1,3 @@\n" +
                " Hello\n" +
                "-World\n" +
                "+Git\n" +
                "+World\n";

        UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffText);
        UnifiedDiffApplier applier = new UnifiedDiffApplier();

        // 应用 diff
        String newText = applier.apply(originalText, diff);
        assertEquals("Hello\nGit\nWorld\n", newText);

        // 反向应用 diff
        String reversedText = applier.reverse(newText, diff);
        assertEquals("Hello\nWorld\n", reversedText);
    }

    @Test
    void testContextMismatchFails() {
        String originalText = "Hello\nDifferent\n";  // 不匹配 "World"
        String diffText = "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -1,2 +1,2 @@\n" +
                " Hello\n" +
                "-World\n" +
                "+Git\n";

        UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffText);
        UnifiedDiffApplier applier = new UnifiedDiffApplier();

        assertThrows(Exception.class, () -> applier.apply(originalText, diff));
    }

    @Test
    void testContextMismatchNonStrict() {
        String originalText = "Hello\nDifferent\n";  // 不匹配 "World"
        String diffText = "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -1,2 +1,2 @@\n" +
                " Hello\n" +
                "-World\n" +
                "+Git\n";

        UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffText);
        // 非严格模式：跳过上下文验证
        UnifiedDiffApplier.Config config = new UnifiedDiffApplier.Config()
                .strictContext(false);
        UnifiedDiffApplier applier = new UnifiedDiffApplier(config);

        // 非严格模式下不验证上下文，直接应用
        String result = applier.apply(originalText, diff);
        // 第一行 "Hello" 保留，第二行 "Different" 被删除（对应 -World），新增 "Git"
        assertEquals("Hello\nGit\n", result);
    }
}
