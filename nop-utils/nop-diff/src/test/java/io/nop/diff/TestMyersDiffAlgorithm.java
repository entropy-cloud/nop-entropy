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

class TestMyersDiffAlgorithm {

    @Test
    void testDiffEmpty() {
        MyersDiffAlgorithm<String> algorithm = new MyersDiffAlgorithm<>();
        List<DiffChange> changes = algorithm.computeDiff(List.of(), List.of());
        assertTrue(changes.isEmpty());
    }

    @Test
    void testDiffSame() {
        MyersDiffAlgorithm<String> algorithm = new MyersDiffAlgorithm<>();
        List<DiffChange> changes = algorithm.computeDiff(
                List.of("a", "b", "c"),
                List.of("a", "b", "c")
        );
        assertTrue(changes.isEmpty());
    }

    @Test
    void testDiffInsert() {
        MyersDiffAlgorithm<String> algorithm = new MyersDiffAlgorithm<>();
        List<DiffChange> changes = algorithm.computeDiff(
                List.of("a", "c"),
                List.of("a", "b", "c")
        );

        // 应该检测到插入
        assertFalse(changes.isEmpty());
        // 检查第一个变更是否是插入类型
        boolean hasInsert = changes.stream().anyMatch(c -> c.getDeltaType() == DiffDeltaType.INSERT);
        assertTrue(hasInsert);
    }

    @Test
    void testDiffDelete() {
        MyersDiffAlgorithm<String> algorithm = new MyersDiffAlgorithm<>();
        List<DiffChange> changes = algorithm.computeDiff(
                List.of("a", "b", "c"),
                List.of("a", "c")
        );

        // 应该检测到删除
        assertFalse(changes.isEmpty());
        boolean hasDelete = changes.stream().anyMatch(c -> c.getDeltaType() == DiffDeltaType.DELETE);
        assertTrue(hasDelete);
    }

    @Test
    void testDiffChange() {
        MyersDiffAlgorithm<String> algorithm = new MyersDiffAlgorithm<>();
        List<DiffChange> changes = algorithm.computeDiff(
                List.of("a", "b", "c"),
                List.of("a", "x", "c")
        );

        // 应该检测到变更
        assertFalse(changes.isEmpty());
        // 变更可能是 DELETE + INSERT 或 CHANGE
        assertTrue(changes.size() >= 1);
    }

    @Test
    void testDiffMultipleChanges() {
        MyersDiffAlgorithm<String> algorithm = new MyersDiffAlgorithm<>();
        List<DiffChange> changes = algorithm.computeDiff(
                List.of("a", "b", "c", "d", "e", "f"),
                List.of("a", "x", "c", "y", "e", "z")
        );

        // 应该检测到多个变更
        assertFalse(changes.isEmpty());
        // 检查是否覆盖了所有变更位置
        assertTrue(changes.size() >= 3);
    }

    @Test
    void testDiffAllInsert() {
        MyersDiffAlgorithm<String> algorithm = new MyersDiffAlgorithm<>();
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
        MyersDiffAlgorithm<String> algorithm = new MyersDiffAlgorithm<>();
        List<DiffChange> changes = algorithm.computeDiff(
                List.of("a", "b", "c"),
                List.of()
        );

        assertFalse(changes.isEmpty());
        boolean hasDelete = changes.stream().anyMatch(c -> c.getDeltaType() == DiffDeltaType.DELETE);
        assertTrue(hasDelete);
    }
}
