package io.nop.ai.code_analyzer.stats;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 语言统计聚合器（从 FileLanguageStats 中提取）：按语言聚合字节数/行数/文件数/代码行数，
 * 并提供按统计类型排序输出的能力。
 */
public class LanguageStatsAggregator {

    /**
     * 语言统计数据类
     */
    public static class LanguageStats {
        private long bytes;           // 字节数
        private long lines;           // 总行数
        private long files;           // 文件数
        private long codeLines;       // 代码行数（排除空行和注释）
        private long blankLines;      // 空行数
        private long commentLines;    // 注释行数

        public LanguageStats() {
            this.bytes = 0;
            this.lines = 0;
            this.files = 0;
            this.codeLines = 0;
            this.blankLines = 0;
            this.commentLines = 0;
        }

        public void addFile(long fileBytes, long fileLines, long fileCodeLines,
                            long fileBlankLines, long fileCommentLines) {
            this.bytes += fileBytes;
            this.lines += fileLines;
            this.files += 1;
            this.codeLines += fileCodeLines;
            this.blankLines += fileBlankLines;
            this.commentLines += fileCommentLines;
        }

        // Getters
        public long getBytes() {
            return bytes;
        }

        public long getLines() {
            return lines;
        }

        public long getFiles() {
            return files;
        }

        public long getCodeLines() {
            return codeLines;
        }

        public long getBlankLines() {
            return blankLines;
        }

        public long getCommentLines() {
            return commentLines;
        }

        @Override
        public String toString() {
            return String.format("Files: %d, Lines: %d (Code: %d, Blank: %d, Comment: %d), Bytes: %d",
                    files, lines, codeLines, blankLines, commentLines, bytes);
        }
    }

    /**
     * 统计结果类型枚举
     */
    public enum StatType {
        BYTES,      // 按字节数统计（类似GitHub API）
        LINES,      // 按总行数统计
        FILES,      // 按文件数统计
        CODE_LINES  // 按代码行数统计
    }

    private final Map<String, LanguageStats> languageStats = new TreeMap<>();

    public void addFile(String language, long fileBytes, long fileLines, long fileCodeLines,
                        long fileBlankLines, long fileCommentLines) {
        languageStats.computeIfAbsent(language, k -> new LanguageStats())
                .addFile(fileBytes, fileLines, fileCodeLines, fileBlankLines, fileCommentLines);
    }

    public Map<String, LanguageStats> getStats() {
        return languageStats;
    }

    /**
     * 从给定统计结果中按类型提取排序后的数值映射（类似GitHub API格式）
     */
    public static Map<String, Long> getStatsByType(Map<String, LanguageStats> stats, StatType type) {
        Map<String, Long> result = new LinkedHashMap<>();
        stats.entrySet().stream()
                .sorted((e1, e2) -> {
                    long value1 = getStatValue(e1.getValue(), type);
                    long value2 = getStatValue(e2.getValue(), type);
                    return Long.compare(value2, value1); // 降序排序
                })
                .forEach(entry -> {
                    long value = getStatValue(entry.getValue(), type);
                    if (value > 0) { // 只包含有值的语言
                        result.put(entry.getKey(), value);
                    }
                });

        return result;
    }

    private static long getStatValue(LanguageStats stats, StatType type) {
        switch (type) {
            case BYTES:
                return stats.getBytes();
            case LINES:
                return stats.getLines();
            case FILES:
                return stats.getFiles();
            case CODE_LINES:
                return stats.getCodeLines();
            default:
                return stats.getBytes();
        }
    }
}
