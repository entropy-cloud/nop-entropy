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

        int lineIndex = 0;  // 当前在原始文本中的行索引 (0-based)
        int lastOldEnd = 0; // 上一个 hunk 结束的原始行号

        for (UnifiedDiffHunk hunk : diff.getHunks()) {
            int oldStartLine = hunk.getOldStartLine();
            int oldLineCount = hunk.getOldLineCount();

            // hunk 开始于 oldStartLine (1-based)
            // 需要复制的未变更行范围: [lastOldEnd, oldStartLine - 1)
            for (int i = lastOldEnd; i < oldStartLine - 1 && i < lines.size(); i++) {
                result.append(lines.get(i)).append('\n');
            }

            // 验证上下文
            if (config.strictContext) {
                validateContext(lines, hunk, oldStartLine);
            }

            // 应用 hunk 内容
            for (UnifiedDiffLine diffLine : hunk.getLines()) {
                if (diffLine.isContext()) {
                    // 上下文行：从原始文本复制
                    if (oldStartLine - 1 + (lineIndex - (oldStartLine - 1)) < lines.size()) {
                        result.append(diffLine.getContent()).append('\n');
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

            lastOldEnd = oldStartLine - 1 + oldLineCount;
        }

        // 添加最后一个 hunk 之后的所有未变更行
        for (int i = lastOldEnd; i < lines.size(); i++) {
            result.append(lines.get(i)).append('\n');
        }

        return result.toString();
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

                String actualLine = lines.get(lineIndex);
                String expectedLine = diffLine.getContent();

                if (config.ignoreTrailingWhitespace) {
                    actualLine = trimTrailingWhitespace(actualLine);
                    expectedLine = trimTrailingWhitespace(expectedLine);
                }

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
