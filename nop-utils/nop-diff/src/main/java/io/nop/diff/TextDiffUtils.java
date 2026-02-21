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

        UnifiedDiff.Builder diffBuilder = UnifiedDiff.builder()
                .oldPath(oldPath)
                .newPath(newPath);

        // 生成 hunks
        List<UnifiedDiffHunk> hunks = generateHunks(originalLines, revisedLines, changes, contextLines);
        for (UnifiedDiffHunk hunk : hunks) {
            diffBuilder.addHunk(hunk);
        }

        return diffBuilder.build();
    }

    /**
     * 生成 hunks
     */
    private static List<UnifiedDiffHunk> generateHunks(List<String> originalLines, List<String> revisedLines,
                                                       List<DiffChange> changes, int contextLines) {
        List<UnifiedDiffHunk> hunks = new ArrayList<>();

        int lastEndOriginal = 0;
        int lastEndRevised = 0;

        for (DiffChange change : changes) {
            int contextStart = Math.max(lastEndOriginal, change.getStartOriginal() - contextLines);
            int contextEnd = Math.min(originalLines.size(), change.getEndOriginal() + contextLines);

            // 检查是否可以与上一个 hunk 合并
            if (!hunks.isEmpty()) {
                UnifiedDiffHunk lastHunk = hunks.get(hunks.size() - 1);
                int lastHunkEnd = lastHunk.getOldEndLine();

                // 如果两个 hunk 的上下文重叠，合并它们
                if (contextStart <= lastHunkEnd + contextLines) {
                    // 移除最后一个 hunk，稍后重新创建
                    hunks.remove(hunks.size() - 1);
                    contextStart = lastHunk.getOldStartLine() - 1;
                }
            }

            UnifiedDiffHunk hunk = buildHunk(originalLines, revisedLines, change, contextStart, contextEnd);
            hunks.add(hunk);

            lastEndOriginal = change.getEndOriginal();
            lastEndRevised = change.getEndRevised();
        }

        return hunks;
    }

    /**
     * 构建单个 hunk
     */
    private static UnifiedDiffHunk buildHunk(List<String> originalLines, List<String> revisedLines,
                                             DiffChange change, int contextStart, int contextEnd) {
        UnifiedDiffHunk.Builder hunkBuilder = UnifiedDiffHunk.builder();

        // 计算行号信息
        int oldStartLine = contextStart + 1; // 1-based
        int oldLineCount = 0;
        int newStartLine = mapToRevisedLine(change, contextStart) + 1;
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
     */
    private static int mapToRevisedLine(DiffChange change, int originalLine) {
        if (originalLine < change.getStartOriginal()) {
            return originalLine;
        }
        // 简化映射：假设变更之前的行一一对应
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
