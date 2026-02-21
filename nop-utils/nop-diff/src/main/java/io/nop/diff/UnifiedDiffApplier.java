/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.diff;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.List;

import static io.nop.diff.DiffErrors.*;

/**
 * Unified Diff 应用器
 * <p>
 * 将 unified diff 应用到原始文本，生成新文本。
 * <p>
 * 支持功能：
 * - 应用完整的 UnifiedDiff（包含多个 hunks）
 * - 应用单个 UnifiedDiffHunk
 * - 严格模式：验证上下文行是否匹配
 * - 宽松模式：跳过上下文验证
 */
public class UnifiedDiffApplier {

    /**
     * 应用配置
     */
    public static class Config {
        private boolean strictContext = true;
        private boolean ignoreTrailingWhitespace = false;
        private boolean failOnConflict = true;
        private boolean fuzzyMatch = false;

        public Config strictContext(boolean strictContext) {
            this.strictContext = strictContext;
            return this;
        }

        public Config ignoreTrailingWhitespace(boolean ignoreTrailingWhitespace) {
            this.ignoreTrailingWhitespace = ignoreTrailingWhitespace;
            return this;
        }

        public Config failOnConflict(boolean failOnConflict) {
            this.failOnConflict = failOnConflict;
            return this;
        }

        /**
         * 启用容错匹配模式
         * <p>
         * 当行号不匹配时，尝试通过 context 行在文件中定位。
         * 要求 context 行必须不为空且在文件中唯一（trim 后匹配）。
         */
        public Config fuzzyMatch(boolean fuzzyMatch) {
            this.fuzzyMatch = fuzzyMatch;
            return this;
        }

        public static Config defaultConfig() {
            return new Config();
        }
    }

    private final Config config;

    public UnifiedDiffApplier() {
        this(Config.defaultConfig());
    }

    public UnifiedDiffApplier(Config config) {
        this.config = config;
    }

    /**
     * 应用 diff 到原始文本
     *
     * @param originalText 原始文本
     * @param diff         要应用的 diff
     * @return 应用 diff 后的新文本
     * @throws NopException 如果应用失败
     */
    public String apply(String originalText, UnifiedDiff diff) {
        if (diff == null || diff.getHunks().isEmpty()) {
            return originalText;
        }

        if (diff.isNewFile()) {
            if (StringHelper.isNotEmpty(originalText)) {
                throw new NopException(ERR_DIFF_APPLY_FAILED)
                        .param(ARG_REASON, "Expected empty original text for new file");
            }
            return buildNewFileContent(diff.getHunks());
        }

        if (diff.isDeletedFile()) {
            return null;
        }

        List<String> lines = splitLines(originalText);
        StringBuilder result = new StringBuilder();

        int lastOldEnd = 0; // 上一个 hunk 结束的原始行号 (0-based)

        for (UnifiedDiffHunk hunk : diff.getHunks()) {
            int declaredStartLine = hunk.getOldStartLine(); // 1-based
            int oldLineCount = hunk.getOldLineCount();

            // 解析实际的起始行号（可能通过 fuzzy 匹配）
            int actualStartLine = resolveActualStartLine(lines, hunk, declaredStartLine);

            // 需要复制的未变更行范围: [lastOldEnd, actualStartLine - 1)
            for (int i = lastOldEnd; i < actualStartLine - 1 && i < lines.size(); i++) {
                result.append(lines.get(i)).append('\n');
            }

            // 验证上下文
            if (config.strictContext) {
                validateContext(lines, hunk, actualStartLine);
            }

            // 应用 hunk 内容
            int lineIndex = actualStartLine - 1; // 0-based
            for (UnifiedDiffLine diffLine : hunk.getLines()) {
                if (diffLine.isContext()) {
                    // 上下文行：从原始文本复制（保留原文件的空白）
                    if (lineIndex < lines.size()) {
                        result.append(lines.get(lineIndex)).append('\n');
                    }
                    lineIndex++;
                } else if (diffLine.isDelete()) {
                    // 删除行：跳过，不添加到结果
                    lineIndex++;
                } else if (diffLine.isAdd()) {
                    // 新增行：添加到结果，不增加 lineIndex
                    result.append(diffLine.getContent()).append('\n');
                }
            }

            lastOldEnd = actualStartLine - 1 + oldLineCount;
        }

        // 添加最后一个 hunk 之后的所有未变更行
        for (int i = lastOldEnd; i < lines.size(); i++) {
            result.append(lines.get(i)).append('\n');
        }

        return result.toString();
    }

    /**
     * 解析 hunk 的实际起始行号
     * <p>
     * 1. 首先尝试按声明的行号匹配
     * 2. 如果不匹配且启用了 fuzzyMatch，则通过 context 行定位
     *
     * @param lines            文件行列表
     * @param hunk             hunk 对象
     * @param declaredStartLine 声明的起始行号 (1-based)
     * @return 实际的起始行号 (1-based)
     */
    private int resolveActualStartLine(List<String> lines, UnifiedDiffHunk hunk, int declaredStartLine) {
        // 先尝试按声明的行号匹配
        if (tryMatchAtLine(lines, hunk, declaredStartLine)) {
            return declaredStartLine;
        }

        // 行号匹配失败，检查是否启用 fuzzy 匹配
        if (!config.fuzzyMatch) {
            return declaredStartLine; // 不启用 fuzzy，返回原行号（后续 validateContext 会报错）
        }

        // 启用 fuzzy 匹配，通过 context 定位
        return fuzzyLocateHunk(lines, hunk);
    }

    /**
     * 尝试在指定行号匹配 hunk
     */
    private boolean tryMatchAtLine(List<String> lines, UnifiedDiffHunk hunk, int startLine) {
        int lineIndex = startLine - 1;
        for (UnifiedDiffLine diffLine : hunk.getLines()) {
            if (diffLine.isContext() || diffLine.isDelete()) {
                if (lineIndex >= lines.size()) {
                    return false;
                }
                String actualLine = normalizeLine(lines.get(lineIndex));
                String expectedLine = normalizeLine(diffLine.getContent());
                if (!actualLine.equals(expectedLine)) {
                    return false;
                }
                lineIndex++;
            }
        }
        return true;
    }

    /**
     * 通过 context 行在文件中定位 hunk
     */
    private int fuzzyLocateHunk(List<String> lines, UnifiedDiffHunk hunk) {
        // 提取所有 context 行
        List<String> contextLines = extractContextLines(hunk);
        if (contextLines.isEmpty()) {
            throw new NopException(ERR_DIFF_FUZZY_CONTEXT_EMPTY);
        }

        // 在文件中查找匹配位置
        List<Integer> matchPositions = findContextPositions(lines, hunk, contextLines);

        if (matchPositions.isEmpty()) {
            throw new NopException(ERR_DIFF_FUZZY_CONTEXT_NOT_FOUND)
                    .param(ARG_CONTEXT, formatContextForError(contextLines));
        }

        if (matchPositions.size() > 1) {
            throw new NopException(ERR_DIFF_FUZZY_CONTEXT_NOT_UNIQUE)
                    .param(ARG_CONTEXT, formatContextForError(contextLines))
                    .param(ARG_MATCH_COUNT, matchPositions.size());
        }

        // 返回 hunk 的起始行号 (1-based)
        return matchPositions.get(0);
    }

    /**
     * 提取 hunk 中所有 context 行
     */
    private List<String> extractContextLines(UnifiedDiffHunk hunk) {
        List<String> contextLines = new ArrayList<>();
        for (UnifiedDiffLine line : hunk.getLines()) {
            if (line.isContext()) {
                contextLines.add(normalizeLine(line.getContent()));
            }
        }
        return contextLines;
    }

    /**
     * 在文件中查找 hunk 的匹配位置
     * <p>
     * 查找逻辑：
     * 1. 遍历文件，找到第一个 context 行的匹配位置
     * 2. 验证所有 context 行是否按 hunk 中的相对顺序出现在文件中
     *
     * @return 匹配的 hunk 起始行号列表 (1-based)
     */
    private List<Integer> findContextPositions(List<String> fileLines, UnifiedDiffHunk hunk, List<String> contextLines) {
        List<Integer> positions = new ArrayList<>();
        String firstContext = contextLines.get(0);

        // 遍历文件，找到第一个 context 行的所有可能位置
        for (int i = 0; i < fileLines.size(); i++) {
            if (normalizeLine(fileLines.get(i)).equals(firstContext)) {
                // 验证从这个位置开始，所有 context 行是否匹配
                int hunkStartLine = tryMatchHunkFromFirstContext(fileLines, hunk, contextLines, i);
                if (hunkStartLine > 0) {
                    positions.add(hunkStartLine);
                }
            }
        }

        return positions;
    }

    /**
     * 从第一个 context 行的位置开始验证整个 hunk 是否匹配
     *
     * @param firstContextPos 第一个 context 行在文件中的位置 (0-based)
     * @return hunk 起始行号 (1-based)，如果不匹配返回 -1
     */
    private int tryMatchHunkFromFirstContext(List<String> fileLines, UnifiedDiffHunk hunk,
                                              List<String> contextLines, int firstContextPos) {
        List<UnifiedDiffLine> hunkLines = hunk.getLines();
        int contextIndex = 0;
        int fileLineIndex = firstContextPos;

        // 首先回退到 hunk 的起始位置（处理第一个 context 之前有 delete 行的情况）
        int firstContextIndex = -1;
        for (int i = 0; i < hunkLines.size(); i++) {
            if (hunkLines.get(i).isContext()) {
                firstContextIndex = i;
                break;
            }
        }

        if (firstContextIndex > 0) {
            // 第一个 context 之前有 delete 行，需要回退
            int deleteCount = 0;
            for (int i = 0; i < firstContextIndex; i++) {
                if (hunkLines.get(i).isDelete()) {
                    deleteCount++;
                }
            }
            fileLineIndex = firstContextPos - deleteCount;
            if (fileLineIndex < 0) {
                return -1;
            }
        }

        // 遍历 hunk 的所有行，验证与文件的匹配
        for (UnifiedDiffLine diffLine : hunkLines) {
            if (diffLine.isContext()) {
                // context 行：必须在文件中匹配
                if (fileLineIndex >= fileLines.size()) {
                    return -1;
                }
                if (!normalizeLine(fileLines.get(fileLineIndex)).equals(contextLines.get(contextIndex))) {
                    return -1;
                }
                contextIndex++;
                fileLineIndex++;
            } else if (diffLine.isDelete()) {
                // delete 行：内容也必须匹配（这样才能确定是正确的位置）
                if (fileLineIndex >= fileLines.size()) {
                    return -1;
                }
                if (!normalizeLine(fileLines.get(fileLineIndex)).equals(normalizeLine(diffLine.getContent()))) {
                    return -1;
                }
                fileLineIndex++;
            }
            // add 行：不对应文件中的行，跳过
        }

        // 所有行都匹配，计算 hunk 起始行（1-based）
        if (firstContextIndex > 0) {
            int deleteCount = 0;
            for (int i = 0; i < firstContextIndex; i++) {
                if (hunkLines.get(i).isDelete()) {
                    deleteCount++;
                }
            }
            return firstContextPos - deleteCount + 1;
        }
        return firstContextPos + 1;
    }

    /**
     * 根据 context 匹配位置计算 hunk 的实际起始行号
     *
     * @param firstContextPos 第一个 context 行在文件中的位置 (0-based)
     * @return hunk 起始行号 (1-based)
     */
    private int calculateHunkStartLine(UnifiedDiffHunk hunk, int firstContextPos) {
        // 找到第一个 context 行在 hunk.lines 中的索引
        List<UnifiedDiffLine> lines = hunk.getLines();
        int firstContextIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).isContext()) {
                firstContextIndex = i;
                break;
            }
        }

        if (firstContextIndex == -1) {
            return firstContextPos + 1;
        }

        // hunk 起始行 = 第一个 context 在文件中的位置 - 第一个 context 之前有多少个 delete 行
        int deletesBeforeFirstContext = 0;
        for (int i = 0; i < firstContextIndex; i++) {
            if (lines.get(i).isDelete()) {
                deletesBeforeFirstContext++;
            }
        }

        // 转换为 1-based
        return firstContextPos - deletesBeforeFirstContext + 1;
    }

    /**
     * 标准化行内容（用于比较）
     */
    private String normalizeLine(String line) {
        if (config.ignoreTrailingWhitespace) {
            return trimTrailingWhitespace(line).trim();
        }
        return line.trim();
    }

    /**
     * 格式化 context 用于错误信息
     */
    private String formatContextForError(List<String> contextLines) {
        if (contextLines.size() <= 3) {
            return String.join(" | ", contextLines);
        }
        return contextLines.get(0) + " | ... | " + contextLines.get(contextLines.size() - 1);
    }

    /**
     * 应用单个 hunk 到文本
     */
    public String applyHunkToText(String originalText, UnifiedDiffHunk hunk) {
        if (hunk == null) {
            return originalText;
        }

        List<String> lines = splitLines(originalText);
        StringBuilder result = new StringBuilder();

        int oldStartLine = hunk.getOldStartLine();
        int oldLineCount = hunk.getOldLineCount();

        // 复制 hunk 之前的所有行
        for (int i = 0; i < oldStartLine - 1 && i < lines.size(); i++) {
            result.append(lines.get(i)).append('\n');
        }

        // 验证上下文
        if (config.strictContext) {
            validateContext(lines, hunk, oldStartLine);
        }

        // 应用 hunk 内容
        for (UnifiedDiffLine diffLine : hunk.getLines()) {
            if (diffLine.isContext() || diffLine.isDelete()) {
                // 跳过原始行（delete 或 context 都对应原始行）
            }
            if (diffLine.isContext() || diffLine.isAdd()) {
                result.append(diffLine.getContent()).append('\n');
            }
        }

        // 复制 hunk 之后的所有行
        int skipCount = oldLineCount;
        for (int i = oldStartLine - 1 + skipCount; i < lines.size(); i++) {
            result.append(lines.get(i)).append('\n');
        }

        return result.toString();
    }

    /**
     * 验证上下文行是否匹配
     */
    private void validateContext(List<String> lines, UnifiedDiffHunk hunk, int startLineNum) {
        int lineIndex = startLineNum - 1;

        for (UnifiedDiffLine diffLine : hunk.getLines()) {
            if (diffLine.isContext() || diffLine.isDelete()) {
                if (lineIndex >= lines.size()) {
                    if (config.failOnConflict) {
                        throw new NopException(ERR_DIFF_APPLY_OLD_LINE_MISMATCH)
                                .param(ARG_OLD_LINE, lineIndex + 1)
                                .param(ARG_EXPECTED, diffLine.getContent())
                                .param(ARG_ACTUAL, "<end of file>");
                    }
                    return;
                }

                String actualLine = normalizeLine(lines.get(lineIndex));
                String expectedLine = normalizeLine(diffLine.getContent());

                if (!actualLine.equals(expectedLine)) {
                    if (config.failOnConflict) {
                        throw new NopException(ERR_DIFF_APPLY_CONTEXT_MISMATCH)
                                .param(ARG_LINE_NUM, lineIndex + 1)
                                .param(ARG_EXPECTED, expectedLine)
                                .param(ARG_ACTUAL, actualLine);
                    }
                    return;
                }

                lineIndex++;
            }
        }
    }

    /**
     * 从 hunks 构建新文件内容
     */
    private String buildNewFileContent(List<UnifiedDiffHunk> hunks) {
        StringBuilder result = new StringBuilder();
        for (UnifiedDiffHunk hunk : hunks) {
            for (UnifiedDiffLine line : hunk.getLines()) {
                if (line.isAdd()) {
                    result.append(line.getContent()).append('\n');
                }
            }
        }
        return result.toString();
    }

    private List<String> splitLines(String text) {
        if (StringHelper.isEmpty(text)) {
            return new ArrayList<>();
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

    private String trimTrailingWhitespace(String str) {
        if (str == null || str.isEmpty()) return str;
        int end = str.length();
        while (end > 0 && Character.isWhitespace(str.charAt(end - 1))) {
            end--;
        }
        return str.substring(0, end);
    }

    /**
     * 反向应用 diff
     */
    public String reverse(String newText, UnifiedDiff diff) {
        if (diff == null || diff.getHunks().isEmpty()) {
            return newText;
        }
        UnifiedDiff reverseDiff = reverseDiff(diff);
        return apply(newText, reverseDiff);
    }

    private UnifiedDiff reverseDiff(UnifiedDiff diff) {
        UnifiedDiff.Builder builder = UnifiedDiff.builder()
                .oldPath(diff.getNewPath())
                .newPath(diff.getOldPath())
                .oldTimestamp(diff.getNewTimestamp())
                .newTimestamp(diff.getOldTimestamp());

        for (UnifiedDiffHunk hunk : diff.getHunks()) {
            builder.addHunk(reverseHunk(hunk));
        }
        return builder.build();
    }

    private UnifiedDiffHunk reverseHunk(UnifiedDiffHunk hunk) {
        UnifiedDiffHunk.Builder builder = UnifiedDiffHunk.builder()
                .oldStartLine(hunk.getNewStartLine())
                .oldLineCount(hunk.getNewLineCount())
                .newStartLine(hunk.getOldStartLine())
                .newLineCount(hunk.getOldLineCount())
                .sectionHeading(hunk.getSectionHeading());

        for (UnifiedDiffLine line : hunk.getLines()) {
            if (line.isDelete()) {
                builder.addLine(UnifiedDiffLine.add(line.getContent()));
            } else if (line.isAdd()) {
                builder.addLine(UnifiedDiffLine.delete(line.getContent()));
            } else {
                builder.addLine(line);
            }
        }
        return builder.build();
    }

    // ========== 静态便捷方法 ==========

    public static String applyDiff(String originalText, UnifiedDiff diff) {
        return new UnifiedDiffApplier().apply(originalText, diff);
    }

    public static String applyHunk(String originalText, UnifiedDiffHunk hunk) {
        return new UnifiedDiffApplier().applyHunkToText(originalText, hunk);
    }

    public static String parseAndApply(String originalText, String diffText) {
        UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffText);
        if (diff == null) {
            return originalText;
        }
        return applyDiff(originalText, diff);
    }
}
