package io.nop.code.core.analyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.exceptions.NopException;
import io.nop.code.core.NopCodeCoreErrors;
import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.incremental.ChangeSet;
import io.nop.code.core.incremental.FileFingerprint;
import io.nop.code.core.incremental.IFingerprintStore;
import io.nop.code.core.incremental.IncrementalDetector;
import io.nop.code.core.incremental.ManifestStore;
import io.nop.code.core.incremental.PathFingerprintStore;
import io.nop.code.core.model.*;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.LanguageFamily;
import io.nop.code.core.semantic.CodeSemanticEdge;
import io.nop.code.core.semantic.EdgeConfidence;
import io.nop.code.core.semantic.ISemanticEdgeExtractor;
import io.nop.code.core.util.DigestHelper;
import io.nop.commons.batch.BatchQueue;
import io.nop.commons.collections.IterableIterator;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceLoader;
/**
 * 项目级代码分析器（语言无关）
 * 扫描整个项目的源文件，构建全局符号表，并解析跨文件的调用关系。
 * 通过 LanguageAdapterRegistry 支持多种编程语言。
 */
public class ProjectAnalyzer implements IProjectAnalyzer {

    private final List<ISemanticEdgeExtractor> semanticExtractors = new ArrayList<>();
    private static final Logger LOG = LoggerFactory.getLogger(ProjectAnalyzer.class);

    public void registerSemanticExtractor(ISemanticEdgeExtractor extractor) {
        semanticExtractors.add(extractor);
    }

    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final long MAX_FILE_SIZE_BYTES = 1024 * 1024;
    private static final int MAX_FILE_COUNT = 50000;

    private final LanguageAdapterRegistry registry;
    private final ExecutorService executor;
    private final int batchSize;

    public ProjectAnalyzer(LanguageAdapterRegistry registry) {
        this.registry = registry;
        this.executor = null;
        this.batchSize = DEFAULT_BATCH_SIZE;
    }

    public ProjectAnalyzer(LanguageAdapterRegistry registry, ExecutorService executor) {
        this(registry, executor, DEFAULT_BATCH_SIZE);
    }

    public ProjectAnalyzer(LanguageAdapterRegistry registry, ExecutorService executor, int batchSize) {
        this.registry = registry;
        this.executor = executor;
        this.batchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
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
    @Override
    public ProjectAnalysisResult analyzeProject(Path projectRoot) {
        try {
            return analyzeProject(projectRoot, (ProgressCallback) null);
        } catch (IOException e) {
            throw new NopException(NopCodeCoreErrors.ERR_CODE_ANALYZE_PROJECT_FAILED)
                    .param(NopCodeCoreErrors.ARG_PATH, projectRoot).cause(e);
        }
    }

    @Override
    public ProjectAnalysisResult analyzeProject(Path projectRoot, Set<CodeLanguage> languages) {
        try {
            return analyzeProject(projectRoot, (ProgressCallback) null);
        } catch (IOException e) {
            throw new NopException(NopCodeCoreErrors.ERR_CODE_ANALYZE_PROJECT_FAILED)
                    .param(NopCodeCoreErrors.ARG_PATH, projectRoot).cause(e);
        }
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

        // Phase 1: 收集所有源文件
        List<Path> sourceFiles = findSourceFiles(projectRoot);
        LOG.info("Found {} source files", sourceFiles.size());

        if (progressCallback != null) {
            progressCallback.onProgress(0, sourceFiles.size(), "Scanning files...");
        }

        // Phase 2: 分析所有文件，收集符号
        List<CodeFileAnalysisResult> fileResults = new ArrayList<>();

        if (executor != null && sourceFiles.size() > batchSize) {
            // 并行批量分析
            List<List<Path>> batches = partition(sourceFiles, batchSize);
            List<Future<List<CodeFileAnalysisResult>>> futures = new ArrayList<>();

            for (List<Path> batch : batches) {
                futures.add(executor.submit(() -> analyzeBatch(batch, projectRoot)));
            }

            int completedBatches = 0;
            for (Future<List<CodeFileAnalysisResult>> future : futures) {
                try {
                    fileResults.addAll(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted during parallel analysis", e);
                } catch (ExecutionException e) {
                    throw new IOException("Failed during parallel analysis", e.getCause());
                }
                completedBatches++;
                if (progressCallback != null) {
                    int processed = Math.min(completedBatches * batchSize, sourceFiles.size());
                    progressCallback.onProgress(processed, sourceFiles.size(),
                            "Analyzed batch " + completedBatches + "/" + batches.size());
                }
            }
        } else {
            // 单线程 fallback（原有逻辑）
            int processed = 0;
            for (Path file : sourceFiles) {
                if (fileResults.size() >= MAX_FILE_COUNT) {
                    LOG.warn("Exceeded maximum file count {}, skipping remaining files", MAX_FILE_COUNT);
                    break;
                }
                processed++;

                if (progressCallback != null) {
                    progressCallback.onProgress(processed, sourceFiles.size(),
                            "Analyzing: " + projectRoot.relativize(file));
                }

                try {
                    long fileSize = Files.size(file);
                    if (fileSize > MAX_FILE_SIZE_BYTES) {
                        LOG.warn("Skipping large file ({} bytes): {}", fileSize, file);
                        continue;
                    }

                    String sourceCode = Files.readString(file);
                    String relativePath = projectRoot.relativize(file).toString();

                    ICodeFileAnalyzer analyzer = registry.getAnalyzer(relativePath);
                    if (analyzer == null) {
                        continue;
                    }

                    CodeFileAnalysisResult result = analyzer.analyze(relativePath, sourceCode);

                    if (result != null) {
                        fileResults.add(result);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to analyze file: {}", file, e);
                }
            }
        }

        // Phase 3: 构建全局符号表
        SymbolTable globalSymbolTable = new SymbolTable();
        for (CodeFileAnalysisResult file : fileResults) {
            for (CodeSymbol symbol : file.getSymbols()) {
                String qualifiedName = symbol.getQualifiedName();
                if (qualifiedName != null && !qualifiedName.isEmpty()) {
                    globalSymbolTable.add(symbol);
                }
            }
        }

        LOG.info("Collected {} symbols from {} files", globalSymbolTable.size(), fileResults.size());

        // Phase 4: 跨文件调用解析
        int[] callCounts = resolveCalls(fileResults, globalSymbolTable);
        int resolvedCalls = callCounts[0];
        int unresolvedCalls = callCounts[1];

        // 构建统计信息
        ProjectStats stats = buildStats(fileResults, globalSymbolTable, resolvedCalls, unresolvedCalls);

        ProjectAnalysisResult result = new ProjectAnalysisResult(fileResults, globalSymbolTable, stats);

        // Phase 5: Run semantic edge extractors
        CallGraph callGraph = result.buildCallGraph();
        List<CodeSemanticEdge> semanticEdges = new ArrayList<>();
        runSemanticExtractors(globalSymbolTable, callGraph, semanticEdges);
        runFileResultExtractors(fileResults, globalSymbolTable, semanticEdges);
        result.setSemanticEdges(semanticEdges);
        LOG.info("Extracted {} semantic edges", semanticEdges.size());

        return result;
    }

    /**
     * 基于 VFS 资源加载器的项目分析。使用 depthIterator 惰性遍历文件系统，
     * 用 BatchQueue 缓冲分析结果，避免一次性加载所有文件到内存。
     */
    public ProjectAnalysisResult analyzeProject(IResourceLoader resourceLoader, String vfsPath, String filePattern) {
        LOG.info("Starting project analysis from VFS: {}", vfsPath);

        Set<String> allExtensions = collectExtensions();

        List<CodeFileAnalysisResult> fileResults = new ArrayList<>();
        SymbolTable globalSymbolTable = new SymbolTable();
        int[] fileCount = {0};

        // BatchQueue 缓冲：满 batchSize 个文件后自动将符号注册到全局符号表
        BatchQueue<CodeFileAnalysisResult> batchQueue = new BatchQueue<>(batchSize, batch -> {
            for (CodeFileAnalysisResult result : batch) {
                fileResults.add(result);
                for (CodeSymbol symbol : result.getSymbols()) {
                    String qn = symbol.getQualifiedName();
                    if (qn != null && !qn.isEmpty()) {
                        globalSymbolTable.add(symbol);
                    }
                }
            }
        });

        // 用 depthIterator 惰性遍历 VFS，边遍历边分析
        IterableIterator<IResource> it = resourceLoader.depthIterator(vfsPath, false, resource -> {
            if (resource.isDirectory()) return false;
            if (filePattern != null && !filePattern.isEmpty() && !"*".equals(filePattern)) {
                String ext = filePattern.startsWith("*.") ? filePattern.substring(1) : filePattern;
                return resource.getName().endsWith(ext);
            }
            for (String ext : allExtensions) {
                if (resource.getName().endsWith(ext)) return true;
            }
            return false;
        });

        while (it.hasNext()) {
            IResource resource = it.next();
            String relativePath = resource.getStdPath();
            if (relativePath.startsWith(vfsPath)) {
                relativePath = relativePath.substring(vfsPath.length());
                if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
            }

            ICodeFileAnalyzer fileAnalyzer = registry.getAnalyzer(relativePath);
            if (fileAnalyzer == null) continue;

            try {
                String sourceCode = resource.readText();
                CodeFileAnalysisResult result = fileAnalyzer.analyze(relativePath, sourceCode);
                if (result != null) {
                    batchQueue.add(result);
                    fileCount[0]++;
                }
            } catch (Exception e) {
                LOG.warn("Failed to analyze resource: {}", relativePath, e);
            }
        }
        batchQueue.flush();

        LOG.info("Analyzed {} files from VFS, collected {} symbols", fileCount[0], globalSymbolTable.size());

        int[] callCounts = resolveCalls(fileResults, globalSymbolTable);
        ProjectStats stats = buildStats(fileResults, globalSymbolTable, callCounts[0], callCounts[1]);

        ProjectAnalysisResult result = new ProjectAnalysisResult(fileResults, globalSymbolTable, stats);
        List<CodeSemanticEdge> semanticEdges = new ArrayList<>();
        runSemanticExtractors(globalSymbolTable, result.buildCallGraph(), semanticEdges);
        runFileResultExtractors(fileResults, globalSymbolTable, semanticEdges);
        result.setSemanticEdges(semanticEdges);
        LOG.info("VFS analysis: extracted {} semantic edges", semanticEdges.size());

        return result;
    }

    public ProjectAnalysisResult analyzeProject(IResourceLoader resourceLoader, String vfsPath,
                                                 String filePattern, BatchCallback batchCallback) {
        LOG.info("Starting streaming project analysis from VFS: {}", vfsPath);

        Set<String> allExtensions = collectExtensions();

        List<CodeFileAnalysisResult> allFileResults = new ArrayList<>();
        SymbolTable globalSymbolTable = new SymbolTable();
        int[] fileCount = {0};

        BatchQueue<CodeFileAnalysisResult> batchQueue = new BatchQueue<>(batchSize, batch -> {
            for (CodeFileAnalysisResult result : batch) {
                allFileResults.add(result);
                for (CodeSymbol symbol : result.getSymbols()) {
                    String qn = symbol.getQualifiedName();
                    if (qn != null && !qn.isEmpty()) {
                        globalSymbolTable.add(symbol);
                    }
                }
            }
            if (batchCallback != null) {
                batchCallback.onBatchComplete(batch);
            }
        });

        IterableIterator<IResource> it = resourceLoader.depthIterator(vfsPath, false, resource -> {
            if (resource.isDirectory()) return false;
            if (filePattern != null && !filePattern.isEmpty() && !"*".equals(filePattern)) {
                String ext = filePattern.startsWith("*.") ? filePattern.substring(1) : filePattern;
                return resource.getName().endsWith(ext);
            }
            for (String ext : allExtensions) {
                if (resource.getName().endsWith(ext)) return true;
            }
            return false;
        });

        while (it.hasNext()) {
            IResource resource = it.next();
            String relativePath = resource.getStdPath();
            if (relativePath.startsWith(vfsPath)) {
                relativePath = relativePath.substring(vfsPath.length());
                if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
            }

            ICodeFileAnalyzer fileAnalyzer = registry.getAnalyzer(relativePath);
            if (fileAnalyzer == null) continue;

            try {
                String sourceCode = resource.readText();
                CodeFileAnalysisResult result = fileAnalyzer.analyze(relativePath, sourceCode);
                if (result != null) {
                    batchQueue.add(result);
                    fileCount[0]++;
                }
            } catch (Exception e) {
                LOG.warn("Failed to analyze resource: {}", relativePath, e);
            }
        }
        batchQueue.flush();

        LOG.info("Streaming analysis: {} files, {} symbols", fileCount[0], globalSymbolTable.size());

        int[] callCounts = resolveCalls(allFileResults, globalSymbolTable);
        ProjectStats stats = buildStats(allFileResults, globalSymbolTable, callCounts[0], callCounts[1]);

        ProjectAnalysisResult result = new ProjectAnalysisResult(allFileResults, globalSymbolTable, stats);
        List<CodeSemanticEdge> semanticEdges = new ArrayList<>();
        runSemanticExtractors(globalSymbolTable, result.buildCallGraph(), semanticEdges);
        runFileResultExtractors(allFileResults, globalSymbolTable, semanticEdges);
        result.setSemanticEdges(semanticEdges);
        LOG.info("Streaming VFS analysis: extracted {} semantic edges", semanticEdges.size());

        return result;
    }

    private void runSemanticExtractors(SymbolTable symbolTable, CallGraph callGraph,
                                        List<CodeSemanticEdge> semanticEdges) {
        for (ISemanticEdgeExtractor extractor : semanticExtractors) {
            if (!extractor.requiresLlm()) {
                try {
                    List<CodeSemanticEdge> extracted = extractor.extract(symbolTable, callGraph);
                    if (extracted != null) {
                        semanticEdges.addAll(extracted);
                    }
                } catch (Exception e) {
                    LOG.warn("Semantic extractor {} failed", extractor.getExtractorId(), e);
                }
            }
        }
    }

    private void runFileResultExtractors(List<CodeFileAnalysisResult> fileResults,
                                          SymbolTable symbolTable,
                                          List<CodeSemanticEdge> semanticEdges) {
        for (ISemanticEdgeExtractor extractor : semanticExtractors) {
            if (!extractor.requiresLlm()) {
                try {
                    List<CodeSemanticEdge> extracted = extractor.extractFromFileResults(fileResults, symbolTable);
                    if (extracted != null && !extracted.isEmpty()) {
                        semanticEdges.addAll(extracted);
                    }
                } catch (Exception e) {
                    LOG.warn("File-result extractor {} failed", extractor.getExtractorId(), e);
                }
            }
        }
    }

    private Set<String> collectExtensions() {
        Set<String> allExtensions = new HashSet<>();
        for (CodeLanguage lang : registry.getSupportedLanguages()) {
            ILanguageAdapter adapter = registry.getAdapter(lang);
            allExtensions.addAll(adapter.getFileExtensions());
        }
        return allExtensions;
    }

    private int[] resolveCalls(List<CodeFileAnalysisResult> fileResults, SymbolTable globalSymbolTable) {
        int resolvedCalls = 0;
        int unresolvedCalls = 0;

        for (CodeFileAnalysisResult file : fileResults) {
            for (CodeMethodCall call : file.getCalls()) {
                String calleeQn = call.getCalleeQualifiedName();
                if (calleeQn != null && !calleeQn.isEmpty()) {
                    CodeSymbol callee = globalSymbolTable.getByQualifiedName(calleeQn);
                    if (callee != null) {
                        call.setCalleeId(callee.getId());
                        call.setConfidence(EdgeConfidence.EXTRACTED);
                        resolvedCalls++;
                    } else {
                        callee = fuzzyMatchSymbol(calleeQn, globalSymbolTable);
                        if (callee != null) {
                            call.setCalleeId(callee.getId());
                            call.setConfidence(EdgeConfidence.EXTRACTED);
                            resolvedCalls++;
                        } else {
                            call.setConfidence(EdgeConfidence.INFERRED);
                            unresolvedCalls++;
                        }
                    }
                }
            }
        }

        LOG.info("Call resolution complete: {} resolved, {} unresolved", resolvedCalls, unresolvedCalls);
        return new int[]{resolvedCalls, unresolvedCalls};
    }

    private ProjectStats buildStats(List<CodeFileAnalysisResult> fileResults,
                                    SymbolTable globalSymbolTable, int resolvedCalls, int unresolvedCalls) {
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

        return stats;
    }

    @Override
    public ProjectAnalysisResult analyzeIncremental(Path projectRoot, List<String> changedFilePaths) {
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

        List<String> filesToReanalyze = changes.getAddedAndModified();
        Set<String> deletedPaths = changes.getDeletedFiles().stream().collect(Collectors.toSet());

        LOG.info("Incremental changes: {} added/modified, {} deleted, {} unchanged",
                filesToReanalyze.size(), deletedPaths.size(), changes.getUnchangedFiles().size());

        // 第一阶段：重新分析变更文件
        Map<String, CodeFileAnalysisResult> updatedFileMap = new LinkedHashMap<>(previousFileMap);

        // 移除已删除的文件
        for (String deletedPath : deletedPaths) {
            updatedFileMap.remove(deletedPath);
        }

        // 重新分析变更文件
        for (String filePath : filesToReanalyze) {
            try {
                Path file = Path.of(filePath);
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
                LOG.warn("Failed to re-analyze file: {} - {}", filePath, e.getMessage());
                LOG.warn("Failed to re-analyze file: {}", file, e);
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
        int[] callCounts = resolveCalls(fileResults, globalSymbolTable);

        LOG.info("Incremental analysis complete: {} files, {} resolved, {} unresolved",
                fileResults.size(), callCounts[0], callCounts[1]);

        ProjectStats stats = buildStats(fileResults, globalSymbolTable, callCounts[0], callCounts[1]);

        return new ProjectAnalysisResult(fileResults, globalSymbolTable, stats);
    }

    /**
     * Store-based incremental analysis.
     * <p>
     * This method only re-analyses changed files (added + modified) and returns the
     * result as a complete result set. It does NOT merge with historical results.
     * Callers should treat the returned result as the complete result set.
     *
     * @param projectRoot      project root directory
     * @param indexId          index identifier for fingerprint storage
     * @param fingerprintStore fingerprint store
     * @return analysis result for changed files only
     */
    public ProjectAnalysisResult analyzeIncremental(Path projectRoot,
                                                    String indexId,
                                                    IFingerprintStore fingerprintStore) throws IOException {
        LOG.info("Starting store-based incremental analysis: {}", projectRoot);

        List<FileFingerprint> previousFingerprints = fingerprintStore.loadFingerprints(indexId);

        List<Path> sourceFiles = findSourceFiles(projectRoot);

        IncrementalDetector detector = new IncrementalDetector();
        ChangeSet changes = detector.detectChanges(previousFingerprints, sourceFiles);

        LOG.info("Store changes: added={}, modified={}, deleted={}, unchanged={}",
                changes.getAddedFiles().size(),
                changes.getModifiedFiles().size(),
                changes.getDeletedFiles().size(),
                changes.getUnchangedFiles().size());

        if (changes.getAddedFiles().isEmpty()
                && changes.getModifiedFiles().isEmpty()
                && changes.getDeletedFiles().isEmpty()) {
            LOG.info("No changes detected, performing full analysis");
            return analyzeProject(projectRoot);
        }

        List<Path> changedFiles = changes.getAddedAndModified();
        LOG.info("Incremental analysis: analysing {} changed files (no merge with history)", changedFiles.size());

        ProjectAnalysisResult result = analyzeFiles(projectRoot, changedFiles);

        List<FileFingerprint> newFingerprints = detector.computeFingerprints(sourceFiles);
        fingerprintStore.saveFingerprints(indexId, newFingerprints);

        return result;
    }

    /**
     * Manifest-based incremental analysis.
     * <p>
     * This method only re-analyses changed files (added + modified) and returns the
     * result as a complete result set. It does NOT merge with historical results.
     * Callers should treat the returned result as the complete result set.
     *
     * @param projectRoot  project root directory
     * @param manifestPath path to the manifest file
     * @return analysis result for changed files only
     */
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

        if (changes.getAddedFiles().isEmpty()
                && changes.getModifiedFiles().isEmpty()
                && changes.getDeletedFiles().isEmpty()) {
            LOG.info("No changes detected, performing full analysis");
            return analyzeProject(projectRoot);
        }

        List<Path> changedFiles = changes.getAddedAndModified();
        LOG.info("Incremental analysis: analysing {} changed files (no merge with history)", changedFiles.size());

        ProjectAnalysisResult result = analyzeFiles(projectRoot, changedFiles);

        List<FileFingerprint> newFingerprints = detector.computeFingerprints(sourceFiles);
        manifestStore.save(manifestPath, newFingerprints);

        return result;
    }

    /**
     * Analyse only the given list of files under projectRoot.
     * Returns a {@link ProjectAnalysisResult} containing results for those files only,
     * with a rebuilt global symbol table and call resolution.
     * <p>
     * This method does NOT merge with any historical results.
     */
    private ProjectAnalysisResult analyzeFiles(Path projectRoot, List<Path> files) throws IOException {
        List<CodeFileAnalysisResult> fileResults = new ArrayList<>();
        SymbolTable globalSymbolTable = new SymbolTable();

        for (Path file : files) {
            try {
                String relativePath = projectRoot.relativize(file).toString();
                ICodeFileAnalyzer analyzer = registry.getAnalyzer(relativePath);
                if (analyzer == null) {
                    continue;
                }
                String sourceCode = Files.readString(file);
                CodeFileAnalysisResult result = analyzer.analyze(relativePath, sourceCode);
                if (result != null) {
                    fileResults.add(result);
                    for (CodeSymbol symbol : result.getSymbols()) {
                        String qn = symbol.getQualifiedName();
                        if (qn != null && !qn.isEmpty()) {
                            globalSymbolTable.add(symbol);
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to analyze file: {}", file, e);
            }
        }

        int[] callCounts = resolveCalls(fileResults, globalSymbolTable);
        ProjectStats stats = buildStats(fileResults, globalSymbolTable, callCounts[0], callCounts[1]);

        LOG.info("analyzeFiles: {} files, {} symbols, {} resolved calls",
                fileResults.size(), globalSymbolTable.size(), callCounts[0]);

        return new ProjectAnalysisResult(fileResults, globalSymbolTable, stats);
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
                contentHash = DigestHelper.sha256Hex(sourceCode.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            fingerprints.add(new FileFingerprint(filePath, contentHash, 0, 0));
        }
        return fingerprints;
    }

    /**
     * 分析一批源文件，返回分析结果列表。
     */
    private List<CodeFileAnalysisResult> analyzeBatch(List<Path> files, Path projectRoot) {
        List<CodeFileAnalysisResult> results = new ArrayList<>();
        for (Path file : files) {
            try {
                String sourceCode = Files.readString(file);
                String relativePath = projectRoot.relativize(file).toString();
                ICodeFileAnalyzer analyzer = registry.getAnalyzer(relativePath);
                if (analyzer == null) {
                    continue;
                }
                CodeFileAnalysisResult result = analyzer.analyze(relativePath, sourceCode);
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                LOG.warn("Failed to analyze file: {}", file, e);
            }
        }
        return results;
    }

    /**
     * 将列表按指定大小拆分为多个子列表。
     */
    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return parts;
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
            String dir = pattern.replace("**/", "").replace("/**", "");
            if (dir.startsWith("/")) dir = dir.substring(1);
            if (dir.endsWith("/")) dir = dir.substring(0, dir.length() - 1);
            for (int i = 0; i < path.getNameCount(); i++) {
                if (path.getName(i).toString().equals(dir)) {
                    return true;
                }
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

    @FunctionalInterface
    public interface BatchCallback {
        void onBatchComplete(List<CodeFileAnalysisResult> batchResults);
    }

}
