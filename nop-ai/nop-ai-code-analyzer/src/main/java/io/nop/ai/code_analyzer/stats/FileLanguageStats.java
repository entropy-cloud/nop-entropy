package io.nop.ai.code_analyzer.stats;

import io.nop.ai.code_analyzer.NopAiCodeAnalyzerErrors;
import io.nop.api.core.exceptions.NopException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件语言统计器
 * 支持统计：字节数、行数、文件数、代码行数等
 * <p>
 * 职责划分（P2-MA1-005 拆分裁定，2026-08-01）：
 * <ul>
 *   <li>语言映射：{@link ExtensionLanguageMapper} + 本类局部映射（优先级高于全局）</li>
 *   <li>忽略目录/扩展名：{@link IgnoredItems}</li>
 *   <li>文件系统遍历：{@link FileTreeWalker}</li>
 *   <li>注释检测/行统计：{@link CodeLineAnalyzer}</li>
 *   <li>统计聚合：{@link LanguageStatsAggregator}</li>
 * </ul>
 * 本类仅保留编排入口。零生产调用者裁定：保留能力（GitHub Languages API 兼容工具），
 * 由 TestFileLanguageStats 直接调用测试提供消费者证据。
 */
public class FileLanguageStats {

    // 局部扩展名映射（优先级高于全局映射）
    private final Map<String, String> localExtensionMapping = new HashMap<>();

    private final FileTreeWalker treeWalker = new FileTreeWalker();
    private final CodeLineAnalyzer lineAnalyzer = new CodeLineAnalyzer();

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
    public Map<String, LanguageStatsAggregator.LanguageStats> getComprehensiveStats(String path) {
        return getComprehensiveStats(Path.of(path));
    }

    public Map<String, LanguageStatsAggregator.LanguageStats> getComprehensiveStats(Path directoryPath) {
        LanguageStatsAggregator aggregator = new LanguageStatsAggregator();

        try {
            treeWalker.walk(directoryPath, (file, extension) -> {
                String language = getLanguageByExtension(extension);
                if (language != null) {
                    long fileBytes = Files.size(file);
                    CodeLineAnalyzer.LineStats lineStats = lineAnalyzer.analyzeFileLines(file, extension);

                    aggregator.addFile(language, fileBytes, lineStats.totalLines, lineStats.codeLines,
                            lineStats.blankLines, lineStats.commentLines);
                }
            });
        } catch (IOException e) {
            throw new NopException(NopAiCodeAnalyzerErrors.ERR_STATS_IO_FAILED)
                    .param(NopAiCodeAnalyzerErrors.ARG_MSG, "Error walking file tree: " + e.getMessage())
                    .cause(e);
        }

        return aggregator.getStats();
    }

    /**
     * 获取指定类型的统计结果（类似GitHub API格式）
     */
    public Map<String, Long> getStatsByType(String directoryPath, LanguageStatsAggregator.StatType type) {
        Map<String, LanguageStatsAggregator.LanguageStats> stats = getComprehensiveStats(directoryPath);
        return LanguageStatsAggregator.getStatsByType(stats, type);
    }

    /**
     * 获取字节数统计（GitHub API风格）
     */
    public Map<String, Long> getByteStats(String directoryPath) {
        return getStatsByType(directoryPath, LanguageStatsAggregator.StatType.BYTES);
    }

    /**
     * 获取行数统计
     */
    public Map<String, Long> getLineStats(String directoryPath) {
        return getStatsByType(directoryPath, LanguageStatsAggregator.StatType.LINES);
    }

    /**
     * 获取文件数统计
     */
    public Map<String, Long> getFileStats(String directoryPath) {
        return getStatsByType(directoryPath, LanguageStatsAggregator.StatType.FILES);
    }

    /**
     * 获取代码行数统计
     */
    public Map<String, Long> getCodeLineStats(String directoryPath) {
        return getStatsByType(directoryPath, LanguageStatsAggregator.StatType.CODE_LINES);
    }
}
