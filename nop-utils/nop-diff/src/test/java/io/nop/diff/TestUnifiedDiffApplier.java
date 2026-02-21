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

    // ========== Fuzzy Match Tests ==========

    @Test
    void testFuzzyMatchSuccess() {
        // 原始文本多了一行，导致行号偏移
        String originalText = "header\nline1\nline2\nline3\n";
        // diff 是基于没有 header 的版本生成的，行号从 1 开始
        String diffText = "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -1,3 +1,3 @@\n" +
                " line1\n" +
                "-line2\n" +
                "+LINE2\n" +
                " line3\n";

        UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffText);
        // 启用 fuzzy 匹配
        UnifiedDiffApplier.Config config = new UnifiedDiffApplier.Config()
                .fuzzyMatch(true);
        UnifiedDiffApplier applier = new UnifiedDiffApplier(config);

        // fuzzy 模式下通过 context（line1, line3）定位
        String result = applier.apply(originalText, diff);
        assertEquals("header\nline1\nLINE2\nline3\n", result);
    }

    @Test
    void testFuzzyMatchWithTrim() {
        // 原始文本有额外的空白
        String originalText = "  line1  \n  line2  \n  line3  \n";
        String diffText = "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -1,3 +1,3 @@\n" +
                " line1\n" +
                "-line2\n" +
                "+LINE2\n" +
                " line3\n";

        UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffText);
        // 启用 fuzzy 匹配（默认会 trim）
        UnifiedDiffApplier.Config config = new UnifiedDiffApplier.Config()
                .fuzzyMatch(true);
        UnifiedDiffApplier applier = new UnifiedDiffApplier(config);

        // trim 后匹配，但输出保留原文件的空白（context 行从 diff 获取，所以没有空白）
        String result = applier.apply(originalText, diff);
        assertEquals("  line1  \nLINE2\n  line3  \n", result);
    }

    @Test
    void testFuzzyMatchContextNotUnique() {
        // context 不唯一：两组 [a, c] 都匹配
        // diff 声明的行号是 5，但文件只有 6 行，所以行号不匹配，触发 fuzzy 匹配
        String originalText = "a\nb\nc\na\nb\nc\n";
        String diffText = "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -5,3 +5,3 @@\n" +  // 声明从第 5 行开始，但文件只有 6 行，会触发 fuzzy
                " a\n" +
                "-b\n" +
                "+B\n" +
                " c\n";

        UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffText);
        UnifiedDiffApplier.Config config = new UnifiedDiffApplier.Config()
                .fuzzyMatch(true);
        UnifiedDiffApplier applier = new UnifiedDiffApplier(config);

        // context [a, c] 在文件中出现两次，应该抛出异常
        assertThrows(Exception.class, () -> applier.apply(originalText, diff));
    }

    @Test
    void testFuzzyMatchDisabled() {
        String originalText = "header\nline1\nline2\nline3\n";
        String diffText = "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -1,3 +1,3 @@\n" +
                " line1\n" +
                "-line2\n" +
                "+LINE2\n" +
                " line3\n";

        UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffText);
        // 不启用 fuzzy 匹配（默认）
        UnifiedDiffApplier applier = new UnifiedDiffApplier();

        // 行号不匹配，应该抛出异常
        assertThrows(Exception.class, () -> applier.apply(originalText, diff));
    }

    @Test
    void testFuzzyMatchExactLineNoStillWorks() {
        // 行号正确匹配时，fuzzy 模式也应该正常工作
        String originalText = "line1\nline2\nline3\n";
        String diffText = "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -1,3 +1,3 @@\n" +
                " line1\n" +
                "-line2\n" +
                "+LINE2\n" +
                " line3\n";

        UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffText);
        UnifiedDiffApplier.Config config = new UnifiedDiffApplier.Config()
                .fuzzyMatch(true);
        UnifiedDiffApplier applier = new UnifiedDiffApplier(config);

        String result = applier.apply(originalText, diff);
        assertEquals("line1\nLINE2\nline3\n", result);
    }

    @Test
    void testFuzzyMatchMultipleHunks() {
        // 多个 hunk，每个独立计算偏移
        String originalText = "header\na\nb\nc\nmiddle\nd\ne\nf\n";
        String diffText = "--- a/file.txt\n" +
                "+++ b/file.txt\n" +
                "@@ -1,3 +1,3 @@\n" +
                " a\n" +
                "-b\n" +
                "+B\n" +
                " c\n" +
                "@@ -5,3 +5,3 @@\n" +
                " d\n" +
                "-e\n" +
                "+E\n" +
                " f\n";

        UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffText);
        UnifiedDiffApplier.Config config = new UnifiedDiffApplier.Config()
                .fuzzyMatch(true);
        UnifiedDiffApplier applier = new UnifiedDiffApplier(config);

        String result = applier.apply(originalText, diff);
        assertEquals("header\na\nB\nc\nmiddle\nd\nE\nf\n", result);
    }
}
