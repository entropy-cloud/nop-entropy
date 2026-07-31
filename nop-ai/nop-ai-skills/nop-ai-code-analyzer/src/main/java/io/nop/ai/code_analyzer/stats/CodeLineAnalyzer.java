package io.nop.ai.code_analyzer.stats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 代码行分析器（从 FileLanguageStats 中提取）：统计行数、空行、注释行、代码行，
 * 支持单行/块注释检测。
 */
public class CodeLineAnalyzer {

    /**
     * 行统计内部类
     */
    public static class LineStats {
        long totalLines = 0;
        long codeLines = 0;
        long blankLines = 0;
        long commentLines = 0;
    }

    /**
     * 分析文件行数
     */
    public LineStats analyzeFileLines(Path file, String extension) throws IOException {
        LineStats stats = new LineStats();

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            boolean inBlockComment = false;

            for (String line : lines) {
                String trimmedLine = line.trim();
                stats.totalLines++;

                if (trimmedLine.isEmpty()) {
                    stats.blankLines++;
                    continue;
                }

                boolean isComment = detectComment(trimmedLine, extension, inBlockComment);

                // 更新块注释状态
                if (isBlockCommentLanguage(extension)) {
                    if (trimmedLine.contains("/*") && !isStringLiteral(line, "/*")) {
                        inBlockComment = true;
                    }
                    if (trimmedLine.contains("*/") && inBlockComment) {
                        inBlockComment = false;
                        // 如果这行在 */ 后面还有代码，则不算纯注释行
                        String afterComment = trimmedLine.substring(trimmedLine.indexOf("*/") + 2).trim();
                        if (!afterComment.isEmpty()) {
                            isComment = false;
                        }
                    }
                }

                if (isComment || inBlockComment) {
                    stats.commentLines++;
                } else {
                    stats.codeLines++;
                }
            }
        } catch (Exception e) {
            // 如果读取失败（如二进制文件），返回基本统计
            stats.totalLines = 1;
            stats.codeLines = 1;
        }

        return stats;
    }

    /**
     * 检测是否为注释行
     */
    private boolean detectComment(String trimmedLine, String extension, boolean inBlockComment) {
        if (inBlockComment) {
            return true;
        }

        // 单行注释检测
        switch (extension.toLowerCase()) {
            case "java":
            case "js":
            case "ts":
            case "cpp":
            case "c":
            case "cs":
            case "go":
            case "rs":
            case "swift":
            case "kt":
            case "scala":
            case "dart":
                return trimmedLine.startsWith("//");

            case "py":
            case "sh":
            case "bash":
            case "rb":
            case "r":
                return trimmedLine.startsWith("#");

            case "html":
            case "xml":
            case "xlang":
                return trimmedLine.startsWith("<!--");

            case "css":
            case "scss":
            case "less":
                return trimmedLine.startsWith("/*");

            case "sql":
                return trimmedLine.startsWith("--") || trimmedLine.startsWith("#");

            default:
                return false;
        }
    }

    /**
     * 检查是否为支持块注释的语言
     */
    private boolean isBlockCommentLanguage(String extension) {
        return Arrays.asList("java", "js", "ts", "cpp", "c", "cs", "css", "scss", "less",
                "go", "rs", "swift", "kt", "scala", "dart").contains(extension.toLowerCase());
    }

    /**
     * 简单检测是否在字符串字面量中
     */
    private boolean isStringLiteral(String line, String target) {
        String beforeTarget = line.substring(0, line.indexOf(target));
        long singleQuotes = beforeTarget.chars().filter(ch -> ch == '\'').count();
        long doubleQuotes = beforeTarget.chars().filter(ch -> ch == '"').count();

        return (singleQuotes % 2 != 0) || (doubleQuotes % 2 != 0);
    }
}
