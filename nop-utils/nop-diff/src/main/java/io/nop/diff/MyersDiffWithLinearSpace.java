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
 * 线性空间优化的 Myers Diff 算法实现
 * <p>
 * 使用分治策略将空间复杂度从 O((N+M)D) 降低到 O(D)，
 * 适用于大文件 diff 场景。
 * <p>
 * 时间复杂度: O((N+M)D)
 * 空间复杂度: O(D)
 *
 * @see <a href="http://www.xmailserver.org/diff2.pdf">An O(ND) Difference Algorithm and Its Variations</a>
 */
public class MyersDiffWithLinearSpace<T> {

    private final BiPredicate<? super T, ? super T> equalizer;

    public MyersDiffWithLinearSpace() {
        this.equalizer = Objects::equals;
    }

    public MyersDiffWithLinearSpace(BiPredicate<? super T, ? super T> equalizer) {
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

        DiffData data = new DiffData(source, target);
        buildScript(data, 0, source.size(), 0, target.size());
        return data.script;
    }

    /**
     * 递归构建 diff 脚本
     */
    private void buildScript(DiffData data, int start1, int end1, int start2, int end2) {
        final Snake middle = getMiddleSnake(data, start1, end1, start2, end2);

        if (middle == null
                || middle.start == end1 && middle.diag == end1 - end2
                || middle.end == start1 && middle.diag == start1 - start2) {
            // 没有中间 snake，直接处理所有差异
            int i = start1;
            int j = start2;
            while (i < end1 || j < end2) {
                if (i < end1 && j < end2 && equalizer.test(data.source.get(i), data.target.get(j))) {
                    // 相等，跳过
                    ++i;
                    ++j;
                } else {
                    // 有差异
                    if (end1 - start1 > end2 - start2) {
                        // 删除操作
                        addChange(data, DiffDeltaType.DELETE, i, i + 1, j, j);
                        ++i;
                    } else {
                        // 插入操作
                        addChange(data, DiffDeltaType.INSERT, i, i, j, j + 1);
                        ++j;
                    }
                }
            }
        } else {
            // 递归处理前半部分
            buildScript(data, start1, middle.start, start2, middle.start - middle.diag);
            // 递归处理后半部分
            buildScript(data, middle.end, end1, middle.end - middle.diag, end2);
        }
    }

    /**
     * 添加变更，尝试合并相邻的同类型变更
     */
    private void addChange(DiffData data, DiffDeltaType type, int startOrig, int endOrig, int startRev, int endRev) {
        if (data.script.isEmpty()) {
            data.script.add(new DiffChange(type, startOrig, endOrig, startRev, endRev));
            return;
        }

        DiffChange last = data.script.get(data.script.size() - 1);
        // 尝试合并相邻的同类型变更
        if (last.getDeltaType() == type && last.getEndOriginal() == startOrig && last.getEndRevised() == startRev) {
            data.script.set(data.script.size() - 1,
                    new DiffChange(type, last.getStartOriginal(), endOrig, last.getStartRevised(), endRev));
        } else {
            data.script.add(new DiffChange(type, startOrig, endOrig, startRev, endRev));
        }
    }

    /**
     * 使用双向搜索找到中间 snake
     */
    private Snake getMiddleSnake(DiffData data, int start1, int end1, int start2, int end2) {
        final int m = end1 - start1;
        final int n = end2 - start2;

        if (m == 0 || n == 0) {
            return null;
        }

        final int delta = m - n;
        final int sum = n + m;
        final int offset = (sum % 2 == 0 ? sum : sum + 1) / 2;

        data.vDown[1 + offset] = start1;
        data.vUp[1 + offset] = end1 + 1;

        for (int d = 0; d <= offset; ++d) {
            // 向前搜索
            for (int k = -d; k <= d; k += 2) {
                final int i = k + offset;
                if (k == -d || k != d && data.vDown[i - 1] < data.vDown[i + 1]) {
                    data.vDown[i] = data.vDown[i + 1];
                } else {
                    data.vDown[i] = data.vDown[i - 1] + 1;
                }

                int x = data.vDown[i];
                int y = x - start1 + start2 - k;

                while (x < end1 && y < end2 && equalizer.test(data.source.get(x), data.target.get(y))) {
                    data.vDown[i] = ++x;
                    ++y;
                }

                // 检查是否与反向搜索相遇
                if (delta % 2 != 0 && delta - d <= k && k <= delta + d) {
                    if (data.vUp[i - delta] <= data.vDown[i]) {
                        return buildSnake(data, data.vUp[i - delta], k + start1 - start2, end1, end2);
                    }
                }
            }

            // 向后搜索
            for (int k = delta - d; k <= delta + d; k += 2) {
                final int i = k + offset - delta;
                if (k == delta - d || k != delta + d && data.vUp[i + 1] <= data.vUp[i - 1]) {
                    data.vUp[i] = data.vUp[i + 1] - 1;
                } else {
                    data.vUp[i] = data.vUp[i - 1];
                }

                int x = data.vUp[i] - 1;
                int y = x - start1 + start2 - k;
                while (x >= start1 && y >= start2 && equalizer.test(data.source.get(x), data.target.get(y))) {
                    data.vUp[i] = x--;
                    y--;
                }

                // 检查是否与正向搜索相遇
                if (delta % 2 == 0 && -d <= k && k <= d) {
                    if (data.vUp[i] <= data.vDown[i + delta]) {
                        return buildSnake(data, data.vUp[i], k + start1 - start2, end1, end2);
                    }
                }
            }
        }

        // 根据算法，这种情况不应该发生
        throw new IllegalStateException("Could not find a diff path");
    }

    /**
     * 构建 snake 对象
     */
    private Snake buildSnake(DiffData data, final int start, final int diag, final int end1, final int end2) {
        int end = start;
        while (end - diag < end2 && end < end1
                && equalizer.test(data.source.get(end), data.target.get(end - diag))) {
            ++end;
        }
        return new Snake(start, end, diag);
    }

    /**
     * 内部数据结构
     */
    private class DiffData {
        final int size;
        final int[] vDown;
        final int[] vUp;
        final List<DiffChange> script;
        final List<? extends T> source;
        final List<? extends T> target;

        DiffData(List<? extends T> source, List<? extends T> target) {
            this.source = source;
            this.target = target;
            this.size = source.size() + target.size() + 2;
            this.vDown = new int[size];
            this.vUp = new int[size];
            this.script = new ArrayList<>();
        }
    }

    /**
     * Snake 表示一条对角线上的连续相等元素
     */
    private static class Snake {
        final int start;
        final int end;
        final int diag;

        Snake(int start, int end, int diag) {
            this.start = start;
            this.end = end;
            this.diag = diag;
        }
    }
}
