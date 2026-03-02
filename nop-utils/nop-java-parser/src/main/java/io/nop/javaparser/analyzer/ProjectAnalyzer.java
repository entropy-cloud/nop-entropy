package io.nop.javaparser.analyzer;

import io.nop.api.core.util.SourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 项目级 Java 代码分析器
 * 扫描整个项目的 Java 文件，构建全局符号表，并解析跨文件的调用关系
 */
public class ProjectAnalyzer {
    
    private static final Logger LOG = LoggerFactory.getLogger(ProjectAnalyzer.class);
    
    private final JavaFileAnalyzer fileAnalyzer;
    
    public ProjectAnalyzer() {
        this.fileAnalyzer = new JavaFileAnalyzer();
    }
    
    public ProjectAnalyzer(JavaFileAnalyzer fileAnalyzer) {
        this.fileAnalyzer = fileAnalyzer;
    }
    
    /**
     * 获取底层文件分析器，可用于配置 TypeSolver 等
     */
    public JavaFileAnalyzer getFileAnalyzer() {
        return fileAnalyzer;
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
     * @param projectRoot 项目根目录
     * @param progressCallback 进度回调 (current, total, message)
     * @return 项目分析结果
     */
    public ProjectAnalysisResult analyzeProject(Path projectRoot, ProgressCallback progressCallback) throws IOException {
        LOG.info("Starting project analysis: {}", projectRoot);
        
        // 第一阶段：收集所有 Java 文件
        List<Path> javaFiles = findJavaFiles(projectRoot);
        LOG.info("Found {} Java files", javaFiles.size());
        
        if (progressCallback != null) {
            progressCallback.onProgress(0, javaFiles.size(), "Scanning files...");
        }
        
        // 第二阶段：分析所有文件，收集符号
        Map<String, SymbolInfo> globalSymbolTable = new HashMap<>();
        List<JavaFileAnalysisResult> fileResults = new ArrayList<>();
        
        int processed = 0;
        for (Path file : javaFiles) {
            processed++;
            
            if (progressCallback != null) {
                progressCallback.onProgress(processed, javaFiles.size(), 
                        "Analyzing: " + projectRoot.relativize(file));
            }
            
            try {
                String sourceCode = Files.readString(file);
                String relativePath = projectRoot.relativize(file).toString();
                
                JavaFileAnalysisResult result = fileAnalyzer.analyze(
                        SourceLocation.fromPath(relativePath), 
                        sourceCode
                );
                
                if (result != null) {
                    fileResults.add(result);
                    
                    // 注册符号到全局表
                    for (SymbolInfo symbol : result.getSymbols()) {
                        String qualifiedName = symbol.getQualifiedName();
                        if (qualifiedName != null && !qualifiedName.isEmpty()) {
                            globalSymbolTable.put(qualifiedName, symbol);
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
        
        for (JavaFileAnalysisResult file : fileResults) {
            for (MethodCall call : file.getCalls()) {
                String calleeQualifiedName = call.getCalleeQualifiedName();
                if (calleeQualifiedName != null && !calleeQualifiedName.isEmpty()) {
                    SymbolInfo callee = globalSymbolTable.get(calleeQualifiedName);
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
        Map<SymbolKind, Integer> symbolCounts = new HashMap<>();
        for (SymbolInfo symbol : globalSymbolTable.values()) {
            symbolCounts.merge(symbol.getKind(), 1, Integer::sum);
        }
        stats.setSymbolCounts(symbolCounts);
        
        return new ProjectAnalysisResult(fileResults, globalSymbolTable, stats);
    }
    
    /**
     * 查找项目中的所有 Java 文件
     */
    private List<Path> findJavaFiles(Path projectRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(projectRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    // 排除常见的不需要分析的目录
                    .filter(p -> !shouldExclude(p))
                    .collect(Collectors.toList());
        }
    }
    
    /**
     * 判断文件是否应该被排除
     */
    private boolean shouldExclude(Path path) {
        String pathStr = path.toString();
        // 排除 target、build、.git 等目录
        return pathStr.contains("/target/") ||
               pathStr.contains("/build/") ||
               pathStr.contains("/.git/") ||
               pathStr.contains("/out/") ||
               pathStr.contains("/node_modules/") ||
               pathStr.contains("/.idea/");
    }
    
    /**
     * 模糊匹配符号
     * 用于处理方法重载等情况，例如：
     * - "com.example.UserService.save(User)" -> "com.example.UserService.save"
     */
    private SymbolInfo fuzzyMatchSymbol(String calleeQualifiedName, Map<String, SymbolInfo> symbolTable) {
        // 尝试移除方法签名中的参数部分
        int parenIndex = calleeQualifiedName.indexOf('(');
        if (parenIndex > 0) {
            String withoutParams = calleeQualifiedName.substring(0, parenIndex);
            return symbolTable.get(withoutParams);
        }
        
        // 尝试匹配内部类
        // 例如: "com.example.Outer.Inner.method" -> 查找 "com.example.Outer$Inner.method"
        String dollarName = calleeQualifiedName.replace(".Inner", "$Inner")
                                               .replace(".Nested", "$Nested");
        if (!dollarName.equals(calleeQualifiedName)) {
            SymbolInfo found = symbolTable.get(dollarName);
            if (found != null) {
                return found;
            }
        }
        
        return null;
    }
    
    /**
     * 统计总调用数
     */
    private int countTotalCalls(List<JavaFileAnalysisResult> fileResults) {
        int total = 0;
        for (JavaFileAnalysisResult file : fileResults) {
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
        private final List<JavaFileAnalysisResult> fileResults;
        private final Map<String, SymbolInfo> globalSymbolTable;
        private final ProjectStats stats;
        
        public ProjectAnalysisResult(List<JavaFileAnalysisResult> fileResults,
                                     Map<String, SymbolInfo> globalSymbolTable,
                                     ProjectStats stats) {
            this.fileResults = fileResults;
            this.globalSymbolTable = globalSymbolTable;
            this.stats = stats;
        }
        
        /**
         * 获取所有文件的分析结果
         */
        public List<JavaFileAnalysisResult> getFileResults() {
            return fileResults;
        }
        
        /**
         * 获取全局符号表 (qualifiedName -> SymbolInfo)
         */
        public Map<String, SymbolInfo> getGlobalSymbolTable() {
            return globalSymbolTable;
        }
        
        /**
         * 根据全限定名查找符号
         */
        public SymbolInfo findSymbol(String qualifiedName) {
            return globalSymbolTable.get(qualifiedName);
        }
        
        /**
         * 获取项目统计信息
         */
        public ProjectStats getStats() {
            return stats;
        }
        
        /**
         * 构建调用图（邻接表形式）
         * @return Map<callerId, List<calleeId>>
         */
        public Map<String, List<String>> buildCallGraph() {
            Map<String, List<String>> callGraph = new HashMap<>();
            
            for (JavaFileAnalysisResult file : fileResults) {
                for (MethodCall call : file.getCalls()) {
                    String callerId = call.getCallerId();
                    String calleeId = call.getCalleeId();
                    
                    if (callerId != null && calleeId != null) {
                        callGraph.computeIfAbsent(callerId, k -> new ArrayList<>())
                                 .add(calleeId);
                    }
                }
            }
            
            return callGraph;
        }
        
        /**
         * 构建反向调用图（被调用关系）
         * @return Map<calleeId, List<callerId>>
         */
        public Map<String, List<String>> buildReverseCallGraph() {
            Map<String, List<String>> reverseCallGraph = new HashMap<>();
            
            for (JavaFileAnalysisResult file : fileResults) {
                for (MethodCall call : file.getCalls()) {
                    String callerId = call.getCallerId();
                    String calleeId = call.getCalleeId();
                    
                    if (callerId != null && calleeId != null) {
                        reverseCallGraph.computeIfAbsent(calleeId, k -> new ArrayList<>())
                                        .add(callerId);
                    }
                }
            }
            
            return reverseCallGraph;
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
        private Map<SymbolKind, Integer> symbolCounts;
        
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
        
        public Map<SymbolKind, Integer> getSymbolCounts() {
            return symbolCounts;
        }
        
        public void setSymbolCounts(Map<SymbolKind, Integer> symbolCounts) {
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
