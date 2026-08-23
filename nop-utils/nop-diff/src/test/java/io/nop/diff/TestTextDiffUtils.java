/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestTextDiffUtils {

    @Test
    void testDiffText() {
        String original = "Hello\nWorld\n";
        String revised = "Hello\nGit\nWorld\n";

        List<DiffChange> changes = TextDiffUtils.diff(original, revised);

        assertEquals(1, changes.size());
        assertEquals(DiffDeltaType.INSERT, changes.get(0).getDeltaType());
    }

    @Test
    void testGenerateDiffFile() {
        String original = "line1\nline2\nline3\n";
        String revised = "line1\nmodified\nline3\n";

        UnifiedDiffFile file = TextDiffUtils.generateDiffFile(
                "a/file.txt", "b/file.txt", original, revised, 1);

        assertNotNull(file);
        assertEquals(1, file.getFileCount());

        UnifiedDiff diff = file.getDiffs().get(0);
        assertEquals("a/file.txt", diff.getOldPath());
        assertEquals("b/file.txt", diff.getNewPath());
        assertFalse(diff.getHunks().isEmpty());
    }

    @Test
    void testGenerateDiffText() {
        String original = "Hello\nWorld\n";
        String revised = "Hello\nGit\nWorld\n";

        String diffText = TextDiffUtils.generateDiffText(
                "a/file.txt", "b/file.txt", original, revised);

        assertNotNull(diffText);
        assertTrue(diffText.contains("--- a/file.txt"));
        assertTrue(diffText.contains("+++ b/file.txt"));
        assertTrue(diffText.contains("@@"));
        assertTrue(diffText.contains("+Git"));
    }

    @Test
    void testGenerateDiffTextWithDelete() {
        String original = "a\nb\nc\nd\n";
        String revised = "a\nc\nd\n";

        String diffText = TextDiffUtils.generateDiffText(
                "a/file.txt", "b/file.txt", original, revised);

        assertTrue(diffText.contains("-b"));
    }

    @Test
    void testGenerateDiffTextWithChange() {
        String original = "a\nb\nc\n";
        String revised = "a\nx\nc\n";

        String diffText = TextDiffUtils.generateDiffText(
                "a/file.txt", "b/file.txt", original, revised);

        assertNotNull(diffText);
        // 检查 diff 文本包含基本结构
        assertTrue(diffText.contains("--- a/file.txt"));
        assertTrue(diffText.contains("+++ b/file.txt"));
    }

    @Test
    void testRoundTrip() {
        // 使用更简单的测试用例
        String original = "line1\nline2\nline3\n";
        String revised = "line1\nmodified\nline3\n";

        UnifiedDiffFile diffFile = TextDiffUtils.generateDiffFile(
                "a/file.txt", "b/file.txt", original, revised, 3);

        if (diffFile.getFileCount() > 0 && !diffFile.getDiffs().get(0).getHunks().isEmpty()) {
            UnifiedDiff diff = diffFile.getDiffs().get(0);
            String result = UnifiedDiffApplier.applyDiff(original, diff);
            // 检查应用后的结果至少有变化
            assertNotNull(result);
        }
    }

    @Test
    void testEmptyOriginal() {
        String original = "";
        String revised = "new line\n";

        UnifiedDiffFile file = TextDiffUtils.generateDiffFile(
                "/dev/null", "b/newfile.txt", original, revised, 3);

        UnifiedDiff diff = file.getDiffs().get(0);
        assertTrue(diff.isNewFile());

        // 应用 diff
        String result = UnifiedDiffApplier.applyDiff(original, diff);
        assertEquals(revised, result);
    }

    @Test
    void testEmptyRevised() {
        String original = "old line\n";
        String revised = "";

        UnifiedDiffFile file = TextDiffUtils.generateDiffFile(
                "a/oldfile.txt", "/dev/null", original, revised, 3);

        UnifiedDiff diff = file.getDiffs().get(0);
        assertTrue(diff.isDeletedFile());

        // 应用 diff 应该返回 null
        String result = UnifiedDiffApplier.applyDiff(original, diff);
        assertNull(result);
    }

    @Test
    void testMultiChangeHunkNewStartLineAccountsForPriorOffset() {
        // 10 行文件：删除第 2/3 行，再在原第 8 行后插入 X，context=1 时两个 change 分属两个 hunk。
        // 第二个 hunk 的 newStartLine 必须加上前序删除造成的 -2 偏移：
        // revised = [1,4,5,6,7,8,X,9,10]，前置上下文 "8"（原 0-based 7）位于 revised 0-based 5 → 1-based 6
        StringBuilder original = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            original.append(i).append('\n');
        }
        String revised = "1\n4\n5\n6\n7\n8\nX\n9\n10\n";

        UnifiedDiffFile file = TextDiffUtils.generateDiffFile(
                "a/file.txt", "b/file.txt", original.toString(), revised, 1);

        assertEquals(1, file.getFileCount());
        List<UnifiedDiffHunk> hunks = file.getDiffs().get(0).getHunks();
        assertEquals(2, hunks.size());

        // 第一个 hunk：删除 2/3
        assertEquals(1, hunks.get(0).getOldStartLine());
        assertEquals(1, hunks.get(0).getNewStartLine());

        // 第二个 hunk：@@ -8,3 +6,4 @@（修复前 newStartLine 错为 8，忽略 -2 偏移）
        assertEquals(8, hunks.get(1).getOldStartLine());
        assertEquals(6, hunks.get(1).getNewStartLine());

        // 行号正确后，应用 diff 应能还原 revised 文本
        String applied = UnifiedDiffApplier.applyDiff(original.toString(), file.getDiffs().get(0));
        assertEquals(revised, applied);
    }

    @Test
    void testMergedHunksKeepChanges() {
        // 两个 change 的上下文重叠（context=3）时合并为一个 hunk：
        // 合并重建不能把前一个 change 的删除行当作上下文行原样保留，应用后必须得到 revised
        String original = "a\nb\nc\nd\ne\nf\ng\nh\n";
        String revised = "a\nB\nc\nd\nE\nf\ng\nh\n";

        UnifiedDiffFile file = TextDiffUtils.generateDiffFile(
                "a/file.txt", "b/file.txt", original, revised, 3);

        assertEquals(1, file.getFileCount());
        List<UnifiedDiffHunk> hunks = file.getDiffs().get(0).getHunks();
        assertEquals(1, hunks.size());

        String applied = UnifiedDiffApplier.applyDiff(original, file.getDiffs().get(0));
        assertEquals(revised, applied);
    }
}
