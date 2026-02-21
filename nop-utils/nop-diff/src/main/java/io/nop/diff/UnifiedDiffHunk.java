/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.diff;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.Guard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 表示 unified diff 中的一个 hunk（代码块）
 * <p>
 * Hunk 格式示例:
 * @@ -1,2 +1,3 @@
 *  Hello
 * -World
 * +Git
 * +World
 */
@DataBean
public class UnifiedDiffHunk {
    /**
     * 旧文件起始行号（1-based）
     */
    private final int oldStartLine;

    /**
     * 旧文件行数
     */
    private final int oldLineCount;

    /**
     * 新文件起始行号（1-based）
     */
    private final int newStartLine;

    /**
     * 新文件行数
     */
    private final int newLineCount;

    /**
     * 可选的上下文信息（@@ 行后面可能有的文字）
     */
    private final String sectionHeading;

    /**
     * 该 hunk 中的所有行
     */
    private final List<UnifiedDiffLine> lines;

    public UnifiedDiffHunk(@JsonProperty("oldStartLine") int oldStartLine,
                           @JsonProperty("oldLineCount") int oldLineCount,
                           @JsonProperty("newStartLine") int newStartLine,
                           @JsonProperty("newLineCount") int newLineCount,
                           @JsonProperty("sectionHeading") String sectionHeading,
                           @JsonProperty("lines") List<UnifiedDiffLine> lines) {
        this.oldStartLine = oldStartLine;
        this.oldLineCount = oldLineCount;
        this.newStartLine = newStartLine;
        this.newLineCount = newLineCount;
        this.sectionHeading = sectionHeading;
        this.lines = Collections.unmodifiableList(new ArrayList<>(Guard.notEmpty(lines, "lines")));
    }

    public int getOldStartLine() {
        return oldStartLine;
    }

    public int getOldLineCount() {
        return oldLineCount;
    }

    public int getNewStartLine() {
        return newStartLine;
    }

    public int getNewLineCount() {
        return newLineCount;
    }

    public String getSectionHeading() {
        return sectionHeading;
    }

    public List<UnifiedDiffLine> getLines() {
        return lines;
    }

    /**
     * 获取旧文件结束行号
     */
    public int getOldEndLine() {
        return oldStartLine + oldLineCount - 1;
    }

    /**
     * 获取新文件结束行号
     */
    public int getNewEndLine() {
        return newStartLine + newLineCount - 1;
    }

    /**
     * 转换为 unified diff 格式的字符串
     */
    public String toDiffString() {
        StringBuilder sb = new StringBuilder();
        toDiffString(sb);
        return sb.toString();
    }

    /**
     * 将 hunk 内容写入 StringBuilder
     *
     * @param sb 目标 StringBuilder
     */
    public void toDiffString(StringBuilder sb) {
        sb.append("@@ -").append(oldStartLine);
        if (oldLineCount != 1) {
            sb.append(",").append(oldLineCount);
        }
        sb.append(" +").append(newStartLine);
        if (newLineCount != 1) {
            sb.append(",").append(newLineCount);
        }
        sb.append(" @@");
        if (sectionHeading != null && !sectionHeading.isEmpty()) {
            sb.append(" ").append(sectionHeading);
        }
        sb.append("\n");

        for (UnifiedDiffLine line : lines) {
            line.toDiffString(sb);
            sb.append("\n");
        }
    }

    @Override
    public String toString() {
        return toDiffString();
    }

    /**
     * 解析 hunk 头部行
     *
     * @param header hunk 头部行，格式如 "@@ -1,2 +1,3 @@ text"
     * @return 解析后的数组 [oldStartLine, oldLineCount, newStartLine, newLineCount, sectionHeading]
     * @throws IllegalArgumentException 如果格式无效
     */
    public static Object[] parseHeader(String header) {
        if (header == null || !header.startsWith("@@") || !header.contains("@@")) {
            throw new IllegalArgumentException("Invalid hunk header: " + header);
        }

        // 找到第二个 @@ 的位置
        int secondAt = header.indexOf("@@", 2);
        if (secondAt < 0) {
            throw new IllegalArgumentException("Invalid hunk header: " + header);
        }

        String rangePart = header.substring(2, secondAt).trim();
        String sectionHeading = header.substring(secondAt + 2).trim();
        if (sectionHeading.isEmpty()) {
            sectionHeading = null;
        }

        // 解析范围: -1,2 +1,3
        String[] parts = rangePart.split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid hunk header: " + header);
        }

        int[] oldRange = parseRange(parts[0]);
        int[] newRange = parseRange(parts[1]);

        return new Object[]{oldRange[0], oldRange[1], newRange[0], newRange[1], sectionHeading};
    }

    private static int[] parseRange(String range) {
        // 格式: -1,2 或 -1
        if (!range.startsWith("-") && !range.startsWith("+")) {
            throw new IllegalArgumentException("Invalid range: " + range);
        }

        String numPart = range.substring(1);
        int start, count;

        int commaPos = numPart.indexOf(',');
        if (commaPos > 0) {
            start = Integer.parseInt(numPart.substring(0, commaPos));
            count = Integer.parseInt(numPart.substring(commaPos + 1));
        } else {
            start = Integer.parseInt(numPart);
            count = 1;
        }

        return new int[]{start, count};
    }

    /**
     * 创建 Builder 用于构建 Hunk
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int oldStartLine;
        private int oldLineCount;
        private int newStartLine;
        private int newLineCount;
        private String sectionHeading;
        private final List<UnifiedDiffLine> lines = new ArrayList<>();

        public Builder oldStartLine(int oldStartLine) {
            this.oldStartLine = oldStartLine;
            return this;
        }

        public Builder oldLineCount(int oldLineCount) {
            this.oldLineCount = oldLineCount;
            return this;
        }

        public Builder newStartLine(int newStartLine) {
            this.newStartLine = newStartLine;
            return this;
        }

        public Builder newLineCount(int newLineCount) {
            this.newLineCount = newLineCount;
            return this;
        }

        public Builder sectionHeading(String sectionHeading) {
            this.sectionHeading = sectionHeading;
            return this;
        }

        public Builder addLine(UnifiedDiffLine line) {
            this.lines.add(line);
            return this;
        }

        public Builder addContextLine(String content) {
            return addLine(UnifiedDiffLine.context(content));
        }

        public Builder addDeleteLine(String content) {
            return addLine(UnifiedDiffLine.delete(content));
        }

        public Builder addAddLine(String content) {
            return addLine(UnifiedDiffLine.add(content));
        }

        /**
         * 根据已添加的行自动计算 oldLineCount 和 newLineCount
         */
        public Builder autoCalculateCounts() {
            int oldCount = 0;
            int newCount = 0;
            for (UnifiedDiffLine line : lines) {
                if (line.isContext() || line.isDelete()) {
                    oldCount++;
                }
                if (line.isContext() || line.isAdd()) {
                    newCount++;
                }
            }
            this.oldLineCount = oldCount;
            this.newLineCount = newCount;
            return this;
        }

        public UnifiedDiffHunk build() {
            return new UnifiedDiffHunk(oldStartLine, oldLineCount,
                    newStartLine, newLineCount, sectionHeading, lines);
        }
    }
}
