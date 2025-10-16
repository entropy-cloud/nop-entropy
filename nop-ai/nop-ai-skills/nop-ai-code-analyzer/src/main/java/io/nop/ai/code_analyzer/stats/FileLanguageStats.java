package io.nop.ai.code_analyzer.stats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;


/**
 * 文件语言统计器
 * 支持统计：字节数、行数、文件数、代码行数等
 */
public class FileLanguageStats {

    // 需要忽略的目录和文件扩展名
    private static final Set<String> IGNORED_DIRECTORIES = new HashSet<>();
    private static final Set<String> IGNORED_EXTENSIONS = new HashSet<>();

    // 局部扩展名映射（优先级高于全局映射）
    private final Map<String, String> localExtensionMapping = new HashMap<>();

    static {
        initializeIgnoredItems();
    }

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

    /**
     * 行统计内部类
     */
    private static class LineStats {
        long totalLines = 0;
        long codeLines = 0;
        long blankLines = 0;
        long commentLines = 0;
    }

    public FileLanguageStats() {
        initializeLocalMapping();
    }

    /**
     * 初始化局部映射（可以覆盖全局映射）
     */
    private void initializeLocalMapping() {
        // 这里可以设置一些特殊的映射，优先级高于全局映射
        // 例如：项目特定的文件类型识别
        localExtensionMapping.put("dockerfile", "Dockerfile");
        localExtensionMapping.put("makefile", "Makefile");
        localExtensionMapping.put("cmake", "CMake");
        localExtensionMapping.put("gradle", "Gradle");
        localExtensionMapping.put("pom", "Maven");
    }

    /**
     * 添加自定义本地映射
     */
    public void addLocalMapping(String extension, String language) {
        localExtensionMapping.put(extension.toLowerCase(), language);
    }

    /**
     * 批量添加自定义本地映射
     */
    public void addLocalMappings(Map<String, String> customMappings) {
        customMappings.forEach((ext, lang) ->
                localExtensionMapping.put(ext.toLowerCase(), lang));
    }

    /**
     * 根据扩展名获取语言（优先使用本地映射）
     */
    private String getLanguageByExtension(String extension) {
        String lowercaseExt = extension.toLowerCase();

        // 优先检查本地映射
        String language = localExtensionMapping.get(lowercaseExt);
        if (language != null) {
            return language;
        }

        // 再检查全局映射
        return ExtensionLanguageMapper.getLanguage(lowercaseExt);
    }

    private static void initializeIgnoredItems() {
        // 忽略的目录
        IGNORED_DIRECTORIES.add(".git");
        IGNORED_DIRECTORIES.add(".svn");
        IGNORED_DIRECTORIES.add(".hg");
        IGNORED_DIRECTORIES.add("node_modules");
        IGNORED_DIRECTORIES.add("target");
        IGNORED_DIRECTORIES.add("build");
        IGNORED_DIRECTORIES.add("dist");
        IGNORED_DIRECTORIES.add("out");
        IGNORED_DIRECTORIES.add("bin");
        IGNORED_DIRECTORIES.add(".idea");
        IGNORED_DIRECTORIES.add(".vscode");
        IGNORED_DIRECTORIES.add("__pycache__");
        IGNORED_DIRECTORIES.add(".gradle");
        IGNORED_DIRECTORIES.add("vendor");
        IGNORED_DIRECTORIES.add("coverage");
        IGNORED_DIRECTORIES.add(".next");
        IGNORED_DIRECTORIES.add(".nuxt");

        // 忽略的文件扩展名
        IGNORED_EXTENSIONS.add("class");
        IGNORED_EXTENSIONS.add("jar");
        IGNORED_EXTENSIONS.add("war");
        IGNORED_EXTENSIONS.add("ear");
        IGNORED_EXTENSIONS.add("exe");
        IGNORED_EXTENSIONS.add("dll");
        IGNORED_EXTENSIONS.add("so");
        IGNORED_EXTENSIONS.add("dylib");
        IGNORED_EXTENSIONS.add("o");
        IGNORED_EXTENSIONS.add("obj");
        IGNORED_EXTENSIONS.add("pyc");
        IGNORED_EXTENSIONS.add("pyo");
        IGNORED_EXTENSIONS.add("log");
        IGNORED_EXTENSIONS.add("tmp");
        IGNORED_EXTENSIONS.add("temp");
        IGNORED_EXTENSIONS.add("bak");
        IGNORED_EXTENSIONS.add("swp");
        IGNORED_EXTENSIONS.add("DS_Store");
        IGNORED_EXTENSIONS.add("zip");
        IGNORED_EXTENSIONS.add("tar");
        IGNORED_EXTENSIONS.add("gz");
        IGNORED_EXTENSIONS.add("rar");
        IGNORED_EXTENSIONS.add("7z");
    }

    /**
     * 获取与GitHub Languages API完全兼容的字节数统计字符串（紧凑格式）
     */
    public String getLanguagesApiString(String directoryPath) {
        Map<String, Long> stats = getByteStats(directoryPath);
        return toGitHubLanguagesApiString(stats);
    }

    /**
     * 将统计结果转换为GitHub Languages API完全兼容的JSON字符串
     * GitHub API返回格式：{"language1": bytes, "language2": bytes, ...}
     * 按字节数降序排列
     */
    public String toGitHubLanguagesApiString(Map<String, Long> stats) {
        if (stats == null || stats.isEmpty()) {
            return "{}";
        }

        // 按字节数降序排序（与GitHub API一致）
        List<Map.Entry<String, Long>> sortedEntries = stats.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("{");

        for (int i = 0; i < sortedEntries.size(); i++) {
            Map.Entry<String, Long> entry = sortedEntries.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * 获取全面的语言统计信息
     */
    public Map<String, LanguageStats> getComprehensiveStats(String path) {
        return getComprehensiveStats(Path.of(path));
    }

    public Map<String, LanguageStats> getComprehensiveStats(Path directoryPath) {
        Map<String, LanguageStats> languageStats = new TreeMap<>();

        try {
            Files.walkFileTree(directoryPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    // 忽略隐藏目录和指定目录
                    if (dirName.startsWith(".") && !dirName.equals(".") ||
                            IGNORED_DIRECTORIES.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        String fileName = file.getFileName().toString();

                        // 忽略隐藏文件
                        if (fileName.startsWith(".")) {
                            return FileVisitResult.CONTINUE;
                        }

                        String extension = getFileExtension(fileName).toLowerCase();

                        if (IGNORED_EXTENSIONS.contains(extension)) {
                            return FileVisitResult.CONTINUE;
                        }

                        String language = getLanguageByExtension(extension);
                        if (language != null) {
                            long fileBytes = Files.size(file);
                            LineStats lineStats = analyzeFileLines(file, extension);

                            languageStats.computeIfAbsent(language, k -> new LanguageStats())
                                    .addFile(fileBytes, lineStats.totalLines, lineStats.codeLines,
                                            lineStats.blankLines, lineStats.commentLines);
                        }
                    } catch (IOException e) {
                        // 静默跳过无法读取的文件
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // 静默跳过访问失败的文件
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Error walking file tree: " + e.getMessage(), e);
        }

        return languageStats;
    }

    /**
     * 获取指定类型的统计结果（类似GitHub API格式）
     */
    public Map<String, Long> getStatsByType(String directoryPath, StatType type) {
        Map<String, LanguageStats> stats = getComprehensiveStats(directoryPath);

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

    /**
     * 获取字节数统计（GitHub API风格）
     */
    public Map<String, Long> getByteStats(String directoryPath) {
        return getStatsByType(directoryPath, StatType.BYTES);
    }

    /**
     * 获取行数统计
     */
    public Map<String, Long> getLineStats(String directoryPath) {
        return getStatsByType(directoryPath, StatType.LINES);
    }

    /**
     * 获取文件数统计
     */
    public Map<String, Long> getFileStats(String directoryPath) {
        return getStatsByType(directoryPath, StatType.FILES);
    }

    /**
     * 获取代码行数统计
     */
    public Map<String, Long> getCodeLineStats(String directoryPath) {
        return getStatsByType(directoryPath, StatType.CODE_LINES);
    }

    private long getStatValue(LanguageStats stats, StatType type) {
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

    /**
     * 分析文件行数
     */
    private LineStats analyzeFileLines(Path file, String extension) throws IOException {
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

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
}