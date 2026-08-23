/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.diff;

import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * 文本 Diff 工具类
 * <p>
 * 提供计算文本差异并生成 UnifiedDiff 的便捷方法
 */
public final class TextDiffUtils {

    private TextDiffUtils() {
    }

    /**
     * 计算两个文本之间的差异
     *
     * @param originalText 原始文本
     * @param revisedText  修改后文本
     * @return 变更列表
     */
    public static List<DiffChange> diff(String originalText, String revisedText) {
        return diff(originalText, revisedText, null);
    }

    /**
     * 计算两个文本之间的差异
     *
     * @param originalText 原始文本
     * @param revisedText  修改后文本
     * @param equalizer    自定义相等比较器（可为 null）
     * @return 变更列表
     */
    public static List<DiffChange> diff(String originalText, String revisedText,
                                        BiPredicate<String, String> equalizer) {
        List<String> originalLines = splitLines(originalText);
        List<String> revisedLines = splitLines(revisedText);

        MyersDiffAlgorithm<String> algorithm = equalizer != null
                ? new MyersDiffAlgorithm<>(equalizer)
                : new MyersDiffAlgorithm<>();

        return algorithm.computeDiff(originalLines, revisedLines);
    }

    /**
     * 计算两个行列表之间的差异
     *
     * @param originalLines 原始行列表
     * @param revisedLines  修改后行列表
     * @return 变更列表
     */
    public static List<DiffChange> diff(List<String> originalLines, List<String> revisedLines) {
        return new MyersDiffAlgorithm<String>().computeDiff(originalLines, revisedLines);
    }

    /**
     * 生成 UnifiedDiffFile
     *
     * @param oldPath 原始文件路径
     * @param newPath 新文件路径
     * @param originalText 原始文本
     * @param revisedText 修改后文本
     * @return UnifiedDiffFile
     */
    public static UnifiedDiffFile generateDiffFile(String oldPath, String newPath,
                                                    String originalText, String revisedText) {
        return generateDiffFile(oldPath, newPath, originalText, revisedText, 3);
    }

    /**
     * 生成 UnifiedDiffFile
     *
     * @param oldPath 原始文件路径
     * @param newPath 新文件路径
     * @param originalText 原始文本
     * @param revisedText 修改后文本
     * @param contextLines 上下文行数
     * @return UnifiedDiffFile
     */
    public static UnifiedDiffFile generateDiffFile(String oldPath, String newPath,
                                                    String originalText, String revisedText,
                                                    int contextLines) {
        List<String> originalLines = splitLines(originalText);
        List<String> revisedLines = splitLines(revisedText);
        return generateDiffFile(oldPath, newPath, originalLines, revisedLines, contextLines);
    }

    /**
     * 生成 UnifiedDiffFile
     *
     * @param oldPath 原始文件路径
     * @param newPath 新文件路径
     * @param originalLines 原始行列表
     * @param revisedLines 修改后行列表
     * @param contextLines 上下文行数
     * @return UnifiedDiffFile
     */
    public static UnifiedDiffFile generateDiffFile(String oldPath, String newPath,
                                                    List<String> originalLines, List<String> revisedLines,
                                                    int contextLines) {
        List<DiffChange> changes = diff(originalLines, revisedLines);
        UnifiedDiff diff = generateUnifiedDiff(oldPath, newPath, originalLines, revisedLines, changes, contextLines);
        return UnifiedDiffFile.of(diff);
    }

    /**
     * 生成 UnifiedDiff
     *
     * @param oldPath 原始文件路径
     * @param newPath 新文件路径
     * @param originalLines 原始行列表
     * @param revisedLines 修改后行列表
     * @param changes 变更列表
     * @param contextLines 上下文行数
     * @return UnifiedDiff
     */
    public static UnifiedDiff generateUnifiedDiff(String oldPath, String newPath,
                                                   List<String> originalLines, List<String> revisedLines,
                                                   List<DiffChange> changes, int contextLines) {
        if (changes.isEmpty()) {
            // 没有变更，返回空 diff
            return UnifiedDiff.builder()
                    .oldPath(oldPath)
                    .newPath(newPath)
                    .build();
        }

        // Myers 算法按编辑路径回溯产出 change，顺序是文档逆序。生成 hunks 要求按原文位置正序遍历
        // （lastEndOriginal 单调递增、hunk 合并、revised 偏移累计都依赖这一点），先排序再生成
        List<DiffChange> sortedChanges = new ArrayList<>(changes);
        sortedChanges.sort((a, b) -> {
            int c = Integer.compare(a.getStartOriginal(), b.getStartOriginal());
            if (c != 0)
                return c;
            return Integer.compare(a.getStartRevised(), b.getStartRevised());
        });

        UnifiedDiff.Builder diffBuilder = UnifiedDiff.builder()
                .oldPath(oldPath)
                .newPath(newPath);

        // 生成 hunks
        List<UnifiedDiffHunk> hunks = generateHunks(originalLines, revisedLines, sortedChanges, contextLines);
        for (UnifiedDiffHunk hunk : hunks) {
            diffBuilder.addHunk(hunk);
        }

        return diffBuilder.build();
    }

    /**
     * 生成 hunks
     * <p>
     * 多 change 场景下，当前 hunk 前置上下文的 revised 行号需要加上前序 change 累计的净偏移
     * （各 change 的 revisedSize - originalSize 之和），否则 @@ 头部的新文件起始行号错误。
     * 注意不能用 endRevised - endOriginal 逐个累加：Myers 产出的相邻 change 的 revised 坐标
     * 已互相折算，直接相减会重复计算。
     * 上下文重叠的相邻 change 合并为一个 hunk 时，按一个横跨两端的 CHANGE 块重建，
     * 避免前一个 change 的增删行被当作上下文行重复输出。
     */
    private static List<UnifiedDiffHunk> generateHunks(List<String> originalLines, List<String> revisedLines,
                                                       List<DiffChange> changes, int contextLines) {
        List<UnifiedDiffHunk> hunks = new ArrayList<>();

        // prefixOffset[k] = changes[0..k) 的 revised 侧累计净偏移（按尺寸差求和）
        int[] prefixOffset = new int[changes.size() + 1];
        for (int k = 0; k < changes.size(); k++) {
            DiffChange c = changes.get(k);
            prefixOffset[k + 1] = prefixOffset[k]
                    + (c.getEndRevised() - c.getStartRevised()) - (c.getEndOriginal() - c.getStartOriginal());
        }

        int lastEndOriginal = 0;
        // 当前 hunk 覆盖的第一个 change 在 changes 中的下标（合并时保持不变）
        int lastHunkFirstIndex = -1;

        for (int i = 0; i < changes.size(); i++) {
            DiffChange change = changes.get(i);
            int contextStart = Math.max(lastEndOriginal, change.getStartOriginal() - contextLines);
            int contextEnd = Math.min(originalLines.size(), change.getEndOriginal() + contextLines);

            // 检查是否可以与上一个 hunk 合并
            if (!hunks.isEmpty()) {
                UnifiedDiffHunk lastHunk = hunks.get(hunks.size() - 1);
                int lastHunkEnd = lastHunk.getOldEndLine();

                // 如果两个 hunk 的上下文重叠，合并它们
                if (contextStart <= lastHunkEnd + contextLines) {
                    // 移除最后一个 hunk，将两个 change 的范围合并成一个 CHANGE 块重新构建。
                    // lastHunkFirstIndex 不变，新 hunk 的起始偏移仍取该下标之前的前缀累计
                    DiffChange first = changes.get(lastHunkFirstIndex);
                    hunks.remove(hunks.size() - 1);
                    contextStart = lastHunk.getOldStartLine() - 1;
                    change = new DiffChange(DiffDeltaType.CHANGE,
                            first.getStartOriginal(), change.getEndOriginal(),
                            first.getStartRevised(), change.getEndRevised());
                } else {
                    lastHunkFirstIndex = i;
                }
            } else {
                lastHunkFirstIndex = i;
            }

            UnifiedDiffHunk hunk = buildHunk(originalLines, revisedLines, change, contextStart, contextEnd,
                    prefixOffset[lastHunkFirstIndex]);
            hunks.add(hunk);

            lastEndOriginal = change.getEndOriginal();
        }

        return hunks;
    }

    /**
     * 构建单个 hunk
     */
    private static UnifiedDiffHunk buildHunk(List<String> originalLines, List<String> revisedLines,
                                             DiffChange change, int contextStart, int contextEnd, int revisedOffset) {
        UnifiedDiffHunk.Builder hunkBuilder = UnifiedDiffHunk.builder();

        // 计算行号信息
        int oldStartLine = contextStart + 1; // 1-based
        int oldLineCount = 0;
        int newStartLine = mapToRevisedLine(change, contextStart, revisedOffset) + 1;
        int newLineCount = 0;

        // 添加前置上下文
        for (int i = contextStart; i < change.getStartOriginal(); i++) {
            hunkBuilder.addContextLine(originalLines.get(i));
            oldLineCount++;
            newLineCount++;
        }

        // 添加变更内容
        if (change.getDeltaType() == DiffDeltaType.DELETE || change.getDeltaType() == DiffDeltaType.CHANGE) {
            for (int i = change.getStartOriginal(); i < change.getEndOriginal(); i++) {
                hunkBuilder.addDeleteLine(originalLines.get(i));
                oldLineCount++;
            }
        }

        if (change.getDeltaType() == DiffDeltaType.INSERT || change.getDeltaType() == DiffDeltaType.CHANGE) {
            for (int i = change.getStartRevised(); i < change.getEndRevised(); i++) {
                hunkBuilder.addAddLine(revisedLines.get(i));
                newLineCount++;
            }
        }

        // 添加后置上下文
        for (int i = change.getEndOriginal(); i < contextEnd; i++) {
            hunkBuilder.addContextLine(originalLines.get(i));
            oldLineCount++;
            newLineCount++;
        }

        return hunkBuilder
                .oldStartLine(oldStartLine)
                .oldLineCount(oldLineCount)
                .newStartLine(newStartLine)
                .newLineCount(newLineCount)
                .build();
    }

    /**
     * 将原始行号映射到修改后行号
     *
     * @param change       当前 change
     * @param originalLine 原始行号（0-based）
     * @param revisedOffset 前序 change 累计的 revised 侧净偏移（当前 change 之前的所有 change 的
     *                      endRevised - endOriginal 之和）
     */
    private static int mapToRevisedLine(DiffChange change, int originalLine, int revisedOffset) {
        if (originalLine < change.getStartOriginal()) {
            return originalLine + revisedOffset;
        }
        return change.getStartRevised() + (originalLine - change.getStartOriginal());
    }

    /**
     * 分割文本为行列表
     */
    private static List<String> splitLines(String text) {
        if (StringHelper.isEmpty(text)) {
            return Collections.emptyList();
        }
        String[] lines = StringHelper.splitToLines(text);
        List<String> result = new ArrayList<>(lines.length);
        for (String line : lines) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            result.add(line);
        }
        return result;
    }

    /**
     * 快速生成 diff 文本
     *
     * @param oldPath 原始文件路径
     * @param newPath 新文件路径
     * @param originalText 原始文本
     * @param revisedText 修改后文本
     * @return unified diff 格式的文本
     */
    public static String generateDiffText(String oldPath, String newPath,
                                           String originalText, String revisedText) {
        UnifiedDiffFile file = generateDiffFile(oldPath, newPath, originalText, revisedText);
        return file.toDiffString();
    }
}
