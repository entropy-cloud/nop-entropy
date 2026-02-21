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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.nop.diff.DiffErrors.*;

/**
 * Unified Diff 解析器
 * <p>
 * 支持解析标准 git unified diff 格式，包括：
 * - diff --git 头部
 * - index 行
 * - --- / +++ 头部
 * - @@ hunk 头部
 * - 上下文行、删除行、新增行
 * <p>
 * 示例:
 * diff --git a/file.txt b/file.txt
 * index 1234567..abcdef 100644
 * --- a/file.txt
 * +++ b/file.txt
 * @@ -1,2 +1,3 @@
 *  Hello
 * -World
 * +Git
 * +World
 */
public class UnifiedDiffParser {

    /**
     * 解析 unified diff 字符串
     *
     * @param diffText diff 文本
     * @return 解析后的 UnifiedDiffFile
     * @throws NopException 如果解析失败
     */
    public static UnifiedDiffFile parse(String diffText) {
        if (StringHelper.isEmpty(diffText)) {
            return new UnifiedDiffFile(null, Collections.emptyList());
        }

        String[] lines = StringHelper.splitToLines(diffText);
        return parseLines(Arrays.asList(lines));
    }

    /**
     * 解析行列表
     */
    private static UnifiedDiffFile parseLines(List<String> lines) {
        UnifiedDiffFile.Builder fileBuilder = UnifiedDiffFile.builder();
        List<UnifiedDiff> diffs = new ArrayList<>();
        UnifiedDiff.Builder diffBuilder = null;
        UnifiedDiffHunk.Builder hunkBuilder = null;
        List<String> extendedHeaders = new ArrayList<>();

        int lineNum = 0;
        for (String line : lines) {
            lineNum++;
            String trimmedLine = line;

            // 处理换行符
            if (trimmedLine.endsWith("\r")) {
                trimmedLine = trimmedLine.substring(0, trimmedLine.length() - 1);
            }

            // 空行或仅包含 diff --git 分隔的行
            if (trimmedLine.isEmpty()) {
                continue;
            }

            // diff --git 行：新文件的开始
            if (trimmedLine.startsWith("diff --git ")) {
                // 保存之前的 diff
                if (diffBuilder != null) {
                    if (hunkBuilder != null) {
                        diffBuilder.addHunk(hunkBuilder.build());
                        hunkBuilder = null;
                    }
                    diffs.add(diffBuilder.build());
                    diffBuilder = null;
                    extendedHeaders.clear();
                }
                // 保存 git diff source
                fileBuilder.gitDiffSource(trimmedLine.substring("diff --git ".length()));
                continue;
            }

            // --- 行：旧文件路径
            if (trimmedLine.startsWith("--- ")) {
                if (diffBuilder != null && hunkBuilder != null) {
                    diffBuilder.addHunk(hunkBuilder.build());
                    hunkBuilder = null;
                }
                if (diffBuilder == null) {
                    diffBuilder = UnifiedDiff.builder();
                    for (String header : extendedHeaders) {
                        diffBuilder.addExtendedHeader(header);
                    }
                }

                String rest = trimmedLine.substring(4);
                int tabPos = rest.indexOf('\t');
                if (tabPos > 0) {
                    diffBuilder.oldPath(rest.substring(0, tabPos));
                    diffBuilder.oldTimestamp(rest.substring(tabPos + 1));
                } else {
                    diffBuilder.oldPath(rest);
                }
                continue;
            }

            // +++ 行：新文件路径
            if (trimmedLine.startsWith("+++ ")) {
                String rest = trimmedLine.substring(4);
                int tabPos = rest.indexOf('\t');
                if (tabPos > 0) {
                    diffBuilder.newPath(rest.substring(0, tabPos));
                    diffBuilder.newTimestamp(rest.substring(tabPos + 1));
                } else {
                    diffBuilder.newPath(rest);
                }
                continue;
            }

            // @@ 行：hunk 头部
            if (trimmedLine.startsWith("@@ ")) {
                if (diffBuilder == null) {
                    // 可能没有 --- 和 +++ 头部，直接以 @@ 开始
                    diffBuilder = UnifiedDiff.builder();
                }

                if (hunkBuilder != null) {
                    diffBuilder.addHunk(hunkBuilder.build());
                }

                hunkBuilder = UnifiedDiffHunk.builder();
                try {
                    Object[] headerParts = UnifiedDiffHunk.parseHeader(trimmedLine);
                    hunkBuilder.oldStartLine((Integer) headerParts[0])
                            .oldLineCount((Integer) headerParts[1])
                            .newStartLine((Integer) headerParts[2])
                            .newLineCount((Integer) headerParts[3])
                            .sectionHeading((String) headerParts[4]);
                } catch (IllegalArgumentException e) {
                    throw new NopException(ERR_DIFF_PARSE_INVALID_HUNK_HEADER)
                            .param(ARG_LINE, trimmedLine)
                            .param(ARG_LINE_NUM, lineNum);
                }
                continue;
            }

            // index, new file mode, deleted file mode, rename from, rename to, similarity index 等
            if (trimmedLine.startsWith("index ") ||
                    trimmedLine.startsWith("new file mode ") ||
                    trimmedLine.startsWith("deleted file mode ") ||
                    trimmedLine.startsWith("rename from ") ||
                    trimmedLine.startsWith("rename to ") ||
                    trimmedLine.startsWith("similarity index ") ||
                    trimmedLine.startsWith("dissimilarity index ") ||
                    trimmedLine.startsWith("copy from ") ||
                    trimmedLine.startsWith("copy to ")) {

                if (diffBuilder == null) {
                    extendedHeaders.add(trimmedLine);
                } else {
                    diffBuilder.addExtendedHeader(trimmedLine);
                }
                continue;
            }

            // diff 内容行（以空格、+、- 开头）
            if (trimmedLine.length() > 0 && UnifiedDiffLineType.isValidPrefix(trimmedLine.charAt(0))) {
                if (hunkBuilder == null) {
                    throw new NopException(ERR_DIFF_PARSE_MISSING_HUNK_HEADER)
                            .param(ARG_LINE, trimmedLine)
                            .param(ARG_LINE_NUM, lineNum);
                }

                // 处理行内容（可能包含空格）
                UnifiedDiffLine diffLine = UnifiedDiffLine.fromDiffString(trimmedLine);
                if (diffLine != null) {
                    hunkBuilder.addLine(diffLine);
                }
                continue;
            }

            // Binary files differ
            if (trimmedLine.startsWith("Binary files ")) {
                if (diffBuilder == null) {
                    extendedHeaders.add(trimmedLine);
                } else {
                    diffBuilder.addExtendedHeader(trimmedLine);
                }
                continue;
            }

            // 其他行作为扩展头部处理（如果在 diff 头部之前）或忽略
            if (diffBuilder == null) {
                extendedHeaders.add(trimmedLine);
            }
        }

        // 保存最后一个 diff
        if (diffBuilder != null) {
            if (hunkBuilder != null) {
                diffBuilder.addHunk(hunkBuilder.build());
            }
            diffs.add(diffBuilder.build());
        }

        for (UnifiedDiff diff : diffs) {
            fileBuilder.addDiff(diff);
        }
        return fileBuilder.build();
    }

    /**
     * 仅解析单个文件的 unified diff
     *
     * @param diffText 单个文件的 diff 文本
     * @return 解析后的 UnifiedDiff
     * @throws NopException 如果解析失败
     */
    public static UnifiedDiff parseSingleDiff(String diffText) {
        UnifiedDiffFile file = parse(diffText);
        if (file.getDiffs().isEmpty()) {
            return null;
        }
        return file.getDiffs().get(0);
    }

    /**
     * 解析 hunk 字符串
     *
     * @param hunkText hunk 文本，包含 @@ 头部
     * @return 解析后的 UnifiedDiffHunk
     */
    public static UnifiedDiffHunk parseHunk(String hunkText) {
        if (StringHelper.isEmpty(hunkText)) {
            return null;
        }

        String[] lines = StringHelper.splitToLines(hunkText);
        if (lines.length == 0) {
            return null;
        }

        UnifiedDiffHunk.Builder builder = UnifiedDiffHunk.builder();

        // 解析头部
        String headerLine = lines[0];
        if (!headerLine.startsWith("@@")) {
            throw new NopException(ERR_DIFF_PARSE_MISSING_HUNK_HEADER)
                    .param(ARG_LINE, headerLine);
        }

        Object[] headerParts = UnifiedDiffHunk.parseHeader(headerLine);
        builder.oldStartLine((Integer) headerParts[0])
                .oldLineCount((Integer) headerParts[1])
                .newStartLine((Integer) headerParts[2])
                .newLineCount((Integer) headerParts[3])
                .sectionHeading((String) headerParts[4]);

        // 解析内容行
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty()) {
                continue;
            }

            UnifiedDiffLine diffLine = UnifiedDiffLine.fromDiffString(line);
            if (diffLine != null) {
                builder.addLine(diffLine);
            }
        }

        return builder.build();
    }
}
