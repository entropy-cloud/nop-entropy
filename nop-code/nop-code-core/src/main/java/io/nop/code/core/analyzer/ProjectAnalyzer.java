package io.nop.code.core.analyzer;

import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 项目级代码分析器（语言无关）
 * 扫描整个项目的源文件，构建全局符号表，并解析跨文件的调用关系。
 * 通过 LanguageAdapterRegistry 支持多种编程语言。
 */
public class ProjectAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectAnalyzer.class);

    private final LanguageAdapterRegistry registry;

    public ProjectAnalyzer(LanguageAdapterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 获取语言适配器注册中心
     */
    public LanguageAdapterRegistry getRegistry() {
        return registry;
    }

    /**
     * 分析整个项目
     *
     * @param projectRoot 项目根目录
     * @return 项目分析结果
     */
    public ProjectAnalysisResult analyzeProject(Path projectRoot) throws IOException {
        return analyzeProject(projectRoot, null);
    }

    /**
     * 分析整个项目（带进度回调）
     *
     * @param projectRoot      项目根目录
     * @param progressCallback 进度回调 (current, total, message)
     * @return 项目分析结果
     */
    public ProjectAnalysisResult analyzeProject(Path projectRoot, ProgressCallback progressCallback) throws IOException {
        LOG.info("Starting project analysis: {}", projectRoot);

        // 第一阶段：收集所有源文件
        List<Path> sourceFiles = findSourceFiles(projectRoot);
        LOG.info("Found {} source files", sourceFiles.size());

        if (progressCallback != null) {
            progressCallback.onProgress(0, sourceFiles.size(), "Scanning files...");
        }

        // 第二阶段：分析所有文件，收集符号
        SymbolTable globalSymbolTable = new SymbolTable();
        List<CodeFileAnalysisResult> fileResults = new ArrayList<>();

        int processed = 0;
        for (Path file : sourceFiles) {
            processed++;

            if (progressCallback != null) {
                progressCallback.onProgress(processed, sourceFiles.size(),
                        "Analyzing: " + projectRoot.relativize(file));
            }

            try {
                String sourceCode = Files.readString(file);
                String relativePath = projectRoot.relativize(file).toString();

                ICodeFileAnalyzer analyzer = registry.getAnalyzer(relativePath);
                if (analyzer == null) {
                    continue;
                }

                CodeFileAnalysisResult result = analyzer.analyze(relativePath, sourceCode);

                if (result != null) {
                    fileResults.add(result);

                    // 注册符号到全局表
                    for (CodeSymbol symbol : result.getSymbols()) {
                        String qualifiedName = symbol.getQualifiedName();
                        if (qualifiedName != null && !qualifiedName.isEmpty()) {
                            globalSymbolTable.add(symbol);
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to analyze file: {} - {}", file, e.getMessage());
            }
        }

        LOG.info("Collected {} symbols from {} files", globalSymbolTable.size(), fileResults.size());

        // 第三阶段：填充 calleeId
        int resolvedCalls = 0;
        int unresolvedCalls = 0;

        for (CodeFileAnalysisResult file : fileResults) {
            for (CodeMethodCall call : file.getCalls()) {
                String calleeQualifiedName = call.getCalleeQualifiedName();
                if (calleeQualifiedName != null && !calleeQualifiedName.isEmpty()) {
                    CodeSymbol callee = globalSymbolTable.getByQualifiedName(calleeQualifiedName);
                    if (callee != null) {
                        call.setCalleeId(callee.getId());
                        resolvedCalls++;
                    } else {
                        // 尝试模糊匹配（方法重载等情况）
                        callee = fuzzyMatchSymbol(calleeQualifiedName, globalSymbolTable);
                        if (callee != null) {
                            call.setCalleeId(callee.getId());
                            resolvedCalls++;
                        } else {
                            unresolvedCalls++;
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Unresolved callee: {} in file {}",
                                        calleeQualifiedName, file.getFilePath());
                            }
                        }
                    }
                }
            }
        }

        LOG.info("Call resolution complete: {} resolved, {} unresolved",
                resolvedCalls, unresolvedCalls);

        // 构建统计信息
        ProjectStats stats = new ProjectStats();
        stats.setTotalFiles(fileResults.size());
        stats.setTotalSymbols(globalSymbolTable.size());
        stats.setTotalCalls(countTotalCalls(fileResults));
        stats.setResolvedCalls(resolvedCalls);
        stats.setUnresolvedCalls(unresolvedCalls);

        // 按符号类型统计
        Map<CodeSymbolKind, Integer> symbolCounts = new HashMap<>();
        for (CodeSymbol symbol : globalSymbolTable.getAll()) {
            symbolCounts.merge(symbol.getKind(), 1, Integer::sum);
        }
        stats.setSymbolCounts(symbolCounts);

        return new ProjectAnalysisResult(fileResults, globalSymbolTable, stats);
    }

    /**
     * 查找项目中的所有源文件（根据注册的语言适配器确定文件扩展名和排除模式）
     */
    private List<Path> findSourceFiles(Path projectRoot) throws IOException {
        List<String> allExtensions = new ArrayList<>();
        Set<String> excludePatterns = new HashSet<>();

        for (CodeLanguage lang : registry.getSupportedLanguages()) {
            ILanguageAdapter adapter = registry.getAdapter(lang);
            allExtensions.addAll(adapter.getFileExtensions());
            excludePatterns.addAll(adapter.getExcludePatterns());
        }

        try (Stream<Path> stream = Files.walk(projectRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> allExtensions.stream().anyMatch(ext -> p.toString().endsWith(ext)))
                    .filter(p -> !shouldExclude(p, excludePatterns))
                    .collect(Collectors.toList());
        }
    }

    /**
     * 判断文件是否应该被排除
     */
    private boolean shouldExclude(Path path, Set<String> excludePatterns) {
        String pathStr = path.toString();
        for (String pattern : excludePatterns) {
            // 简单 glob 匹配，支持 **/dir/** 模式
            String dir = pattern.replace("**/", "/").replace("/**", "/");
            if (pathStr.contains(dir)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 模糊匹配符号
     * 用于处理方法重载等情况，例如：
     * "com.example.UserService.save(User)" -> "com.example.UserService.save"
     */
    private CodeSymbol fuzzyMatchSymbol(String calleeQualifiedName, SymbolTable symbolTable) {
        // 尝试移除方法签名中的参数部分
        int parenIndex = calleeQualifiedName.indexOf('(');
        if (parenIndex > 0) {
            String withoutParams = calleeQualifiedName.substring(0, parenIndex);
            return symbolTable.getByQualifiedName(withoutParams);
        }
        return null;
    }

    /**
     * 统计总调用数
     */
    private int countTotalCalls(List<CodeFileAnalysisResult> fileResults) {
        int total = 0;
        for (CodeFileAnalysisResult file : fileResults) {
            total += file.getCalls().size();
        }
        return total;
    }

    /**
     * 进度回调接口
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int current, int total, String message);
    }

    /**
     * 项目分析结果
     */
    public static class ProjectAnalysisResult {
        private final List<CodeFileAnalysisResult> fileResults;
        private final SymbolTable globalSymbolTable;
        private final ProjectStats stats;

        public ProjectAnalysisResult(List<CodeFileAnalysisResult> fileResults,
                                     SymbolTable globalSymbolTable,
                                     ProjectStats stats) {
            this.fileResults = fileResults;
            this.globalSymbolTable = globalSymbolTable;
            this.stats = stats;
        }

        /**
         * 获取所有文件的分析结果
         */
        public List<CodeFileAnalysisResult> getFileResults() {
            return fileResults;
        }

        /**
         * 获取全局符号表
         */
        public SymbolTable getGlobalSymbolTable() {
            return globalSymbolTable;
        }

        /**
         * 根据全限定名查找符号
         */
        public CodeSymbol findSymbol(String qualifiedName) {
            return globalSymbolTable.getByQualifiedName(qualifiedName);
        }

        /**
         * 获取项目统计信息
         */
        public ProjectStats getStats() {
            return stats;
        }

        /**
         * 构建调用图
         */
        public CallGraph buildCallGraph() {
            CallGraph callGraph = new CallGraph();

            for (CodeFileAnalysisResult file : fileResults) {
                for (CodeMethodCall call : file.getCalls()) {
                    String callerId = call.getCallerId();
                    String calleeId = call.getCalleeId();

                    if (callerId != null && calleeId != null) {
                        callGraph.addEdge(callerId, calleeId);
                    }
                }
            }

            return callGraph;
        }
    }

    /**
     * 项目统计信息
     */
    public static class ProjectStats {
        private int totalFiles;
        private int totalSymbols;
        private int totalCalls;
        private int resolvedCalls;
        private int unresolvedCalls;
        private Map<CodeSymbolKind, Integer> symbolCounts;

        public int getTotalFiles() {
            return totalFiles;
        }

        public void setTotalFiles(int totalFiles) {
            this.totalFiles = totalFiles;
        }

        public int getTotalSymbols() {
            return totalSymbols;
        }

        public void setTotalSymbols(int totalSymbols) {
            this.totalSymbols = totalSymbols;
        }

        public int getTotalCalls() {
            return totalCalls;
        }

        public void setTotalCalls(int totalCalls) {
            this.totalCalls = totalCalls;
        }

        public int getResolvedCalls() {
            return resolvedCalls;
        }

        public void setResolvedCalls(int resolvedCalls) {
            this.resolvedCalls = resolvedCalls;
        }

        public int getUnresolvedCalls() {
            return unresolvedCalls;
        }

        public void setUnresolvedCalls(int unresolvedCalls) {
            this.unresolvedCalls = unresolvedCalls;
        }

        public Map<CodeSymbolKind, Integer> getSymbolCounts() {
            return symbolCounts;
        }

        public void setSymbolCounts(Map<CodeSymbolKind, Integer> symbolCounts) {
            this.symbolCounts = symbolCounts;
        }

        public double getResolutionRate() {
            if (totalCalls == 0) return 0;
            return (double) resolvedCalls / totalCalls * 100;
        }

        @Override
        public String toString() {
            return "ProjectStats{" +
                    "totalFiles=" + totalFiles +
                    ", totalSymbols=" + totalSymbols +
                    ", totalCalls=" + totalCalls +
                    ", resolvedCalls=" + resolvedCalls +
                    ", unresolvedCalls=" + unresolvedCalls +
                    ", resolutionRate=" + String.format("%.1f%%", getResolutionRate()) +
                    '}';
        }
    }
}
