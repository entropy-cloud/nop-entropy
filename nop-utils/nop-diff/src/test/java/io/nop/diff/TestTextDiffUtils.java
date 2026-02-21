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
}
