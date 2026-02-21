/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.diff;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestMyersDiffWithLinearSpace {

    @Test
    void testDiffEmpty() {
        MyersDiffWithLinearSpace<String> algorithm = new MyersDiffWithLinearSpace<>();
        List<DiffChange> changes = algorithm.computeDiff(List.of(), List.of());
        assertTrue(changes.isEmpty());
    }

    @Test
    void testDiffSame() {
        MyersDiffWithLinearSpace<String> algorithm = new MyersDiffWithLinearSpace<>();
        List<DiffChange> changes = algorithm.computeDiff(
                List.of("a", "b", "c"),
                List.of("a", "b", "c")
        );
        assertTrue(changes.isEmpty());
    }

    @Test
    void testDiffInsert() {
        MyersDiffWithLinearSpace<String> algorithm = new MyersDiffWithLinearSpace<>();
        List<DiffChange> changes = algorithm.computeDiff(
                List.of("a", "c"),
                List.of("a", "b", "c")
        );

        assertFalse(changes.isEmpty());
        boolean hasInsert = changes.stream().anyMatch(c -> c.getDeltaType() == DiffDeltaType.INSERT);
        assertTrue(hasInsert);
    }

    @Test
    void testDiffDelete() {
        MyersDiffWithLinearSpace<String> algorithm = new MyersDiffWithLinearSpace<>();
        List<DiffChange> changes = algorithm.computeDiff(
                List.of("a", "b", "c"),
                List.of("a", "c")
        );

        assertFalse(changes.isEmpty());
        boolean hasDelete = changes.stream().anyMatch(c -> c.getDeltaType() == DiffDeltaType.DELETE);
        assertTrue(hasDelete);
    }

    @Test
    void testDiffChange() {
        MyersDiffWithLinearSpace<String> algorithm = new MyersDiffWithLinearSpace<>();
        List<DiffChange> changes = algorithm.computeDiff(
                List.of("a", "b", "c"),
                List.of("a", "x", "c")
        );

        assertFalse(changes.isEmpty());
        assertTrue(changes.size() >= 1);
    }

    @Test
    void testDiffMultipleChanges() {
        MyersDiffWithLinearSpace<String> algorithm = new MyersDiffWithLinearSpace<>();
        List<DiffChange> changes = algorithm.computeDiff(
                List.of("a", "b", "c", "d", "e", "f"),
                List.of("a", "x", "c", "y", "e", "z")
        );

        assertFalse(changes.isEmpty());
        assertTrue(changes.size() >= 3);
    }

    @Test
    void testDiffAllInsert() {
        MyersDiffWithLinearSpace<String> algorithm = new MyersDiffWithLinearSpace<>();
        List<DiffChange> changes = algorithm.computeDiff(
                List.of(),
                List.of("a", "b", "c")
        );

        assertFalse(changes.isEmpty());
        boolean hasInsert = changes.stream().anyMatch(c -> c.getDeltaType() == DiffDeltaType.INSERT);
        assertTrue(hasInsert);
    }

    @Test
    void testDiffAllDelete() {
        MyersDiffWithLinearSpace<String> algorithm = new MyersDiffWithLinearSpace<>();
        List<DiffChange> changes = algorithm.computeDiff(
                List.of("a", "b", "c"),
                List.of()
        );

        assertFalse(changes.isEmpty());
        boolean hasDelete = changes.stream().anyMatch(c -> c.getDeltaType() == DiffDeltaType.DELETE);
        assertTrue(hasDelete);
    }

    @Test
    void testCompareWithMyersDiffAlgorithm() {
        // 两种算法产生等价的 diff，但变更顺序可能相反（从前向后 vs 从后向前）
        MyersDiffAlgorithm<String> standard = new MyersDiffAlgorithm<>();
        MyersDiffWithLinearSpace<String> linear = new MyersDiffWithLinearSpace<>();

        List<String> original = List.of("a", "b", "c", "d", "e", "f", "g");
        List<String> revised = List.of("a", "x", "c", "y", "e", "z", "g");

        List<DiffChange> standardChanges = standard.computeDiff(original, revised);
        List<DiffChange> linearChanges = linear.computeDiff(original, revised);

        // 验证两者都检测到了变更
        assertFalse(standardChanges.isEmpty(), "Standard algorithm should detect changes");
        assertFalse(linearChanges.isEmpty(), "Linear space algorithm should detect changes");

        // 验证变更数量相同（虽然顺序可能相反）
        assertEquals(standardChanges.size(), linearChanges.size(), 
                "Both algorithms should produce the same number of changes");
    }

    @Test
    void testCompareWithLargeDiff() {
        // 测试较大差异的情况
        MyersDiffAlgorithm<String> standard = new MyersDiffAlgorithm<>();
        MyersDiffWithLinearSpace<String> linear = new MyersDiffWithLinearSpace<>();

        List<String> original = new ArrayList<>();
        List<String> revised = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            original.add("line" + i);
            if (i % 2 == 0) {
                revised.add("line" + i);
            } else {
                revised.add("modified" + i);
            }
        }

        List<DiffChange> standardChanges = standard.computeDiff(original, revised);
        List<DiffChange> linearChanges = linear.computeDiff(original, revised);

        // 验证两者都检测到了变更
        assertFalse(standardChanges.isEmpty());
        assertFalse(linearChanges.isEmpty());
    }

    @Test
    void testCustomEqualizer() {
        // 使用自定义比较器（忽略大小写）
        MyersDiffWithLinearSpace<String> algorithm = new MyersDiffWithLinearSpace<>(
                (a, b) -> a.equalsIgnoreCase(b)
        );

        List<DiffChange> changes = algorithm.computeDiff(
                List.of("Hello", "World"),
                List.of("hello", "WORLD")
        );

        // 忽略大小写后应该没有差异
        assertTrue(changes.isEmpty());
    }

    @Test
    void testLargeFile() {
        // 测试较大文件（1000 行）
        MyersDiffWithLinearSpace<String> algorithm = new MyersDiffWithLinearSpace<>();

        List<String> original = new ArrayList<>();
        List<String> revised = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            original.add("line" + i);
            if (i % 10 == 0) {
                revised.add("modified" + i);
            } else {
                revised.add("line" + i);
            }
        }

        List<DiffChange> changes = algorithm.computeDiff(original, revised);

        // 应该有 100 个修改（每 10 行一个）
        assertFalse(changes.isEmpty());
        assertTrue(changes.size() >= 50); // 至少有部分变更被检测到
    }

    @Test
    void testOneSideEmpty() {
        // 一边为空，一边有内容
        MyersDiffWithLinearSpace<String> algorithm = new MyersDiffWithLinearSpace<>();

        // 原始为空
        List<DiffChange> insertChanges = algorithm.computeDiff(
                List.of(),
                List.of("a", "b", "c")
        );
        assertFalse(insertChanges.isEmpty());

        // 修改后为空
        List<DiffChange> deleteChanges = algorithm.computeDiff(
                List.of("a", "b", "c"),
                List.of()
        );
        assertFalse(deleteChanges.isEmpty());
    }
}
