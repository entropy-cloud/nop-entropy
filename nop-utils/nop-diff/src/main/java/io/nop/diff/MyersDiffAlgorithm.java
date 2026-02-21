/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.diff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Myers Diff 算法实现
 * <p>
 * 基于 Eugene Myers 的贪心差分算法，计算两个序列之间的最小编辑距离。
 * <p>
 * 时间复杂度: O((N+M)D)，其中 D 是差异数量
 * 空间复杂度: O((N+M)D)
 *
 * @see <a href="http://www.xmailserver.org/diff2.pdf">An O(ND) Difference Algorithm and Its Variations</a>
 */
public class MyersDiffAlgorithm<T> {

    private final BiPredicate<? super T, ? super T> equalizer;

    public MyersDiffAlgorithm() {
        this.equalizer = Objects::equals;
    }

    public MyersDiffAlgorithm(BiPredicate<? super T, ? super T> equalizer) {
        Objects.requireNonNull(equalizer, "equalizer must not be null");
        this.equalizer = equalizer;
    }

    /**
     * 计算两个序列之间的差异
     *
     * @param source 原始序列
     * @param target 目标序列
     * @return 变更列表
     */
    public List<DiffChange> computeDiff(List<? extends T> source, List<? extends T> target) {
        Objects.requireNonNull(source, "source list must not be null");
        Objects.requireNonNull(target, "target list must not be null");

        if (source.isEmpty() && target.isEmpty()) {
            return Collections.emptyList();
        }

        PathNode path = buildPath(source, target);
        return buildChanges(path, source, target);
    }

    /**
     * 构建编辑路径
     */
    private PathNode buildPath(List<? extends T> orig, List<? extends T> rev) {
        final int n = orig.size();
        final int m = rev.size();
        final int max = n + m + 1;
        final int size = 1 + 2 * max;
        final int middle = size / 2;

        final PathNode[] diagonal = new PathNode[size];
        diagonal[middle + 1] = new PathNode(0, -1, true, true, null);

        for (int d = 0; d < max; d++) {
            for (int k = -d; k <= d; k += 2) {
                final int kmiddle = middle + k;
                final int kplus = kmiddle + 1;
                final int kminus = kmiddle - 1;
                PathNode prev;
                int i;

                if ((k == -d) || (k != d && diagonal[kminus].i < diagonal[kplus].i)) {
                    i = diagonal[kplus].i;
                    prev = diagonal[kplus];
                } else {
                    i = diagonal[kminus].i + 1;
                    prev = diagonal[kminus];
                }

                diagonal[kminus] = null;

                int j = i - k;

                PathNode node = new PathNode(i, j, false, false, prev);

                // 沿对角线延伸（跳过相等元素）
                while (i < n && j < m && equalizer.test(orig.get(i), rev.get(j))) {
                    i++;
                    j++;
                }

                if (i != node.i) {
                    node = new PathNode(i, j, true, false, node);
                }

                diagonal[kmiddle] = node;

                if (i >= n && j >= m) {
                    return diagonal[kmiddle];
                }
            }
            diagonal[middle + d - 1] = null;
        }

        throw new IllegalStateException("Could not find a diff path");
    }

    /**
     * 从路径构建变更列表
     */
    private List<DiffChange> buildChanges(PathNode path, List<? extends T> orig, List<? extends T> rev) {
        List<DiffChange> rawChanges = new ArrayList<>();

        if (path.isSnake) {
            path = path.prev;
        }

        while (path != null && path.prev != null && path.prev.j >= 0) {
            if (path.isSnake) {
                throw new IllegalStateException("Bad diffpath: found snake when looking for diff");
            }

            int i = path.i;
            int j = path.j;

            path = path.prev;
            int ianchor = path.i;
            int janchor = path.j;

            DiffDeltaType type;
            if (ianchor == i && janchor != j) {
                type = DiffDeltaType.INSERT;
            } else if (ianchor != i && janchor == j) {
                type = DiffDeltaType.DELETE;
            } else {
                type = DiffDeltaType.CHANGE;
            }

            rawChanges.add(new DiffChange(type, ianchor, i, janchor, j));

            if (path.isSnake) {
                path = path.prev;
            }
        }

        // 合并连续的相同类型变更
        return mergeChanges(rawChanges);
    }

    /**
     * 合并连续的相同类型变更
     */
    private List<DiffChange> mergeChanges(List<DiffChange> rawChanges) {
        if (rawChanges.isEmpty()) {
            return rawChanges;
        }

        List<DiffChange> merged = new ArrayList<>();
        DiffChange current = rawChanges.get(0);

        for (int i = 1; i < rawChanges.size(); i++) {
            DiffChange next = rawChanges.get(i);

            // 检查是否可以合并
            if (canMerge(current, next)) {
                // 合并
                current = new DiffChange(
                        current.getDeltaType(),
                        current.getStartOriginal(),
                        next.getEndOriginal(),
                        current.getStartRevised(),
                        next.getEndRevised()
                );
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        return merged;
    }

    /**
     * 检查两个变更是否可以合并
     */
    private boolean canMerge(DiffChange a, DiffChange b) {
        // 只有相同类型且连续的变更才能合并
        if (a.getDeltaType() != b.getDeltaType()) {
            return false;
        }

        // 对于删除操作，检查原始位置是否连续
        if (a.getDeltaType() == DiffDeltaType.DELETE) {
            return a.getEndOriginal() == b.getStartOriginal() && a.getEndRevised() == b.getStartRevised();
        }

        // 对于插入操作，检查修改后位置是否连续
        if (a.getDeltaType() == DiffDeltaType.INSERT) {
            return a.getEndOriginal() == b.getStartOriginal() && a.getEndRevised() == b.getStartRevised();
        }

        // CHANGE 类型不合并
        return false;
    }

    /**
     * 路径节点
     */
    private static class PathNode {
        final int i;
        final int j;
        final boolean isSnake;
        final boolean isBoundary;
        final PathNode prev;

        PathNode(int i, int j, boolean isSnake, boolean isBoundary, PathNode prev) {
            this.i = i;
            this.j = j;
            this.isSnake = isSnake;
            this.isBoundary = isBoundary;
            this.prev = prev;
        }
    }
}
