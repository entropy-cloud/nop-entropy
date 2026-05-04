package io.nop.code.core.analyzer;

import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.incremental.ChangeSet;
import io.nop.code.core.incremental.FileFingerprint;
import io.nop.code.core.incremental.IncrementalDetector;
import io.nop.code.core.incremental.ManifestStore;
import io.nop.code.core.model.*;
import io.nop.code.core.model.LanguageFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
                        call.setConfidence("EXTRACTED");
                        resolvedCalls++;
                    } else {
                        callee = fuzzyMatchSymbol(calleeQualifiedName, globalSymbolTable);
                        if (callee != null) {
                            call.setCalleeId(callee.getId());
                            call.setConfidence("EXTRACTED");
                            resolvedCalls++;
                        } else {
                            call.setConfidence("INFERRED");
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

        Map<LanguageFamily, Integer> languageFamilyCounts = new HashMap<>();
        for (CodeFileAnalysisResult fr : fileResults) {
            languageFamilyCounts.merge(LanguageFamily.fromLanguage(fr.getLanguage()), 1, Integer::sum);
        }
        stats.setLanguageFamilyCounts(languageFamilyCounts);

        return new ProjectAnalysisResult(fileResults, globalSymbolTable, stats);
    }

    /**
     * IProjectAnalyzer.analyzeProject(Path) 的适配实现。
     * 返回 Object 以匹配接口签名，内部委托给 analyzeProject(Path, ProgressCallback)。
     */
    public Object analyzeProjectAdapted(Path projectRoot) {
        try {
            return analyzeProject(projectRoot, (ProgressCallback) null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to analyze project: " + projectRoot, e);
        }
    }

    /**
     * IProjectAnalyzer.analyzeProject(Path, Set) 的适配实现。
     */
    public Object analyzeProjectAdapted(Path projectRoot, Set<CodeLanguage> languages) {
        try {
            return analyzeProject(projectRoot, (ProgressCallback) null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to analyze project: " + projectRoot, e);
        }
    }

    /**
     * IProjectAnalyzer.analyzeIncremental(Path, List) 的适配实现。
     */
    public Object analyzeIncrementalAdapted(Path projectRoot, List<String> changedFilePaths) {
        throw new UnsupportedOperationException(
                "Use analyzeIncremental(Path, ProjectAnalysisResult) or analyzeIncremental(Path, Path) instead");
    }

    /**
     * 增量分析：基于上一次分析结果和显式变更文件列表。
     * 重新分析变更文件，保留未变更文件的分析结果，重建全局符号表和调用引用。
     *
     * @param projectRoot    项目根目录
     * @param previousResult 上一次的完整分析结果
     * @return 增量分析结果
     */
    public ProjectAnalysisResult analyzeIncremental(Path projectRoot,
                                                    ProjectAnalysisResult previousResult) throws IOException {
        LOG.info("Starting incremental analysis: {}", projectRoot);

        // 从上一次结果构建 filePath -> CodeFileAnalysisResult 映射
        Map<String, CodeFileAnalysisResult> previousFileMap = new LinkedHashMap<>();
        if (previousResult != null && previousResult.getFileResults() != null) {
            for (CodeFileAnalysisResult fr : previousResult.getFileResults()) {
                if (fr.getFilePath() != null) {
                    previousFileMap.put(fr.getFilePath(), fr);
                }
            }
        }

        // 收集当前所有源文件
        List<Path> sourceFiles = findSourceFiles(projectRoot);
        LOG.info("Found {} source files for incremental analysis", sourceFiles.size());

        // 确定哪些文件需要重新分析（通过指纹对比）
        IncrementalDetector detector = new IncrementalDetector();
        List<FileFingerprint> previousFingerprints = buildFingerprintsFromResults(previousResult);
        ChangeSet changes = detector.detectChanges(previousFingerprints, sourceFiles);

        List<Path> filesToReanalyze = changes.getAddedAndModified();
        Set<String> deletedPaths = changes.getDeletedFiles().stream()
                .map(Path::toString)
                .collect(Collectors.toSet());

        LOG.info("Incremental changes: {} added/modified, {} deleted, {} unchanged",
                filesToReanalyze.size(), deletedPaths.size(), changes.getUnchangedFiles().size());

        // 第一阶段：重新分析变更文件
        Map<String, CodeFileAnalysisResult> updatedFileMap = new LinkedHashMap<>(previousFileMap);

        // 移除已删除的文件
        for (String deletedPath : deletedPaths) {
            updatedFileMap.remove(deletedPath);
        }

        // 重新分析变更文件
        for (Path file : filesToReanalyze) {
            try {
                String relativePath = projectRoot.relativize(file).toString();
                ICodeFileAnalyzer analyzer = registry.getAnalyzer(relativePath);
                if (analyzer == null) {
                    continue;
                }
                String sourceCode = Files.readString(file);
                CodeFileAnalysisResult result = analyzer.analyze(relativePath, sourceCode);
                if (result != null) {
                    updatedFileMap.put(relativePath, result);
                }
            } catch (Exception e) {
                LOG.warn("Failed to re-analyze file: {} - {}", file, e.getMessage());
            }
        }

        // 第二阶段：重建全局符号表
        SymbolTable globalSymbolTable = new SymbolTable();
        List<CodeFileAnalysisResult> fileResults = new ArrayList<>(updatedFileMap.values());

        for (CodeFileAnalysisResult file : fileResults) {
            for (CodeSymbol symbol : file.getSymbols()) {
                String qualifiedName = symbol.getQualifiedName();
                if (qualifiedName != null && !qualifiedName.isEmpty()) {
                    globalSymbolTable.add(symbol);
                }
            }
        }

        // 第三阶段：重新解析跨文件调用引用
        int resolvedCalls = 0;
        int unresolvedCalls = 0;

        for (CodeFileAnalysisResult file : fileResults) {
            for (CodeMethodCall call : file.getCalls()) {
                String calleeQualifiedName = call.getCalleeQualifiedName();
                if (calleeQualifiedName != null && !calleeQualifiedName.isEmpty()) {
                    CodeSymbol callee = globalSymbolTable.getByQualifiedName(calleeQualifiedName);
                    if (callee != null) {
                        call.setCalleeId(callee.getId());
                        call.setConfidence("EXTRACTED");
                        resolvedCalls++;
                    } else {
                        callee = fuzzyMatchSymbol(calleeQualifiedName, globalSymbolTable);
                        if (callee != null) {
                            call.setCalleeId(callee.getId());
                            call.setConfidence("EXTRACTED");
                            resolvedCalls++;
                        } else {
                            call.setConfidence("INFERRED");
                            unresolvedCalls++;
                        }
                    }
                }
            }
        }

        LOG.info("Incremental analysis complete: {} files, {} resolved, {} unresolved",
                fileResults.size(), resolvedCalls, unresolvedCalls);

        // 构建统计信息
        ProjectStats stats = new ProjectStats();
        stats.setTotalFiles(fileResults.size());
        stats.setTotalSymbols(globalSymbolTable.size());
        stats.setTotalCalls(countTotalCalls(fileResults));
        stats.setResolvedCalls(resolvedCalls);
        stats.setUnresolvedCalls(unresolvedCalls);

        Map<CodeSymbolKind, Integer> symbolCounts = new HashMap<>();
        for (CodeSymbol symbol : globalSymbolTable.getAll()) {
            symbolCounts.merge(symbol.getKind(), 1, Integer::sum);
        }
        stats.setSymbolCounts(symbolCounts);

        Map<LanguageFamily, Integer> languageFamilyCounts = new HashMap<>();
        for (CodeFileAnalysisResult fr : fileResults) {
            languageFamilyCounts.merge(LanguageFamily.fromLanguage(fr.getLanguage()), 1, Integer::sum);
        }
        stats.setLanguageFamilyCounts(languageFamilyCounts);

        return new ProjectAnalysisResult(fileResults, globalSymbolTable, stats);
    }

    public ProjectAnalysisResult analyzeIncremental(Path projectRoot,
                                                    Path manifestPath) throws IOException {
        LOG.info("Starting manifest-based incremental analysis: {}", projectRoot);

        ManifestStore manifestStore = new ManifestStore();
        List<FileFingerprint> previousFingerprints = manifestStore.load(manifestPath);

        List<Path> sourceFiles = findSourceFiles(projectRoot);

        IncrementalDetector detector = new IncrementalDetector();
        ChangeSet changes = detector.detectChanges(previousFingerprints, sourceFiles);

        LOG.info("Manifest changes: added={}, modified={}, deleted={}, unchanged={}",
                changes.getAddedFiles().size(),
                changes.getModifiedFiles().size(),
                changes.getDeletedFiles().size(),
                changes.getUnchangedFiles().size());

        // 如果没有变更，尝试返回之前的结果（如果有的话）
        if (changes.getAddedFiles().isEmpty()
                && changes.getModifiedFiles().isEmpty()
                && changes.getDeletedFiles().isEmpty()) {
            LOG.info("No changes detected, performing full analysis");
            return analyzeProject(projectRoot);
        }

        ProjectAnalysisResult result = analyzeIncremental(projectRoot,
                (ProjectAnalysisResult) null);

        List<FileFingerprint> newFingerprints = detector.computeFingerprints(sourceFiles);
        manifestStore.save(manifestPath, newFingerprints);

        return result;
    }

    /**
     * 从分析结果构建指纹列表（用于增量对比）
     */
    private List<FileFingerprint> buildFingerprintsFromResults(ProjectAnalysisResult result) throws IOException {
        List<FileFingerprint> fingerprints = new ArrayList<>();
        if (result == null || result.getFileResults() == null) {
            return fingerprints;
        }

        for (CodeFileAnalysisResult fileResult : result.getFileResults()) {
            String filePath = fileResult.getFilePath();
            if (filePath == null) continue;

            // 使用 sourceCode 的 hash 作为内容指纹
            String sourceCode = fileResult.getSourceCode();
            String contentHash = "";
            if (sourceCode != null) {
                contentHash = sha256Hex(sourceCode.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            fingerprints.add(new FileFingerprint(filePath, contentHash, 0, 0));
        }
        return fingerprints;
    }

    /**
     * 计算字节数组的 SHA-256 十六进制字符串
     */
    private static String sha256Hex(byte[] data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            char[] hexChars = "0123456789abcdef".toCharArray();
            for (byte b : hash) {
                sb.append(hexChars[(b >> 4) & 0x0f]);
                sb.append(hexChars[b & 0x0f]);
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
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
        private Map<LanguageFamily, Integer> languageFamilyCounts;

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

        public Map<LanguageFamily, Integer> getLanguageFamilyCounts() {
            return languageFamilyCounts;
        }

        public void setLanguageFamilyCounts(Map<LanguageFamily, Integer> languageFamilyCounts) {
            this.languageFamilyCounts = languageFamilyCounts;
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
