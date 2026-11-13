package io.nop.code.core.analyzer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

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
import io.nop.core.resource.VirtualFileSystem;/**
 * 项目级代码分析器（语言无关）
 * 扫描整个项目的源文件，构建全局符号表，并解析跨文件的调用关系。
 * 通过 LanguageAdapterRegistry 支持多种编程语言。
 */
public class ProjectAnalyzer implements IProjectAnalyzer {

    private final List<ISemanticEdgeExtractor> semanticExtractors = new ArrayList<>();
    private static final Logger LOG = LoggerFactory.getLogger(ProjectAnalyzer.class);

    private IResourceLoader resourceLoader;

    public void registerSemanticExtractor(ISemanticEdgeExtractor extractor) {
        semanticExtractors.add(extractor);
    }

    private static final int DEFAULT_BATCH_SIZE = 1000;

    /**
     * Input-envelope defaults (6.3-W-02 activation): oversized files are skipped and traversal
     * stops at the file-count cap — both observable via {@link ProjectAnalysisResult#isTruncated()}
     * and structured WARN. Test seam: instance fields + package-visible setters, production
     * default = these compile-time constants. DEFAULT_BATCH_SIZE batching is unrelated and
     * untouched.
     */
    static final long DEFAULT_MAX_FILE_SIZE_BYTES = 1024 * 1024;
    static final int DEFAULT_MAX_FILE_COUNT = 50000;

    private long maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE_BYTES;
    private int maxFileCount = DEFAULT_MAX_FILE_COUNT;

    /** Test seam (TP-002 inject-small-threshold) — package-visible by design: ProjectAnalyzer is
     * direct-new assembled (CodeIndexService.setRegistry / test channels), never an IoC bean. */
    void setMaxFileSizeBytes(long maxFileSizeBytes) {
        if (maxFileSizeBytes <= 0)
            throw new IllegalArgumentException("maxFileSizeBytes must be positive, got: " + maxFileSizeBytes);
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /** Test seam (TP-002 inject-small-threshold) — package-visible by design, see setMaxFileSizeBytes. */
    void setMaxFileCount(int maxFileCount) {
        if (maxFileCount <= 0)
            throw new IllegalArgumentException("maxFileCount must be positive, got: " + maxFileCount);
        this.maxFileCount = maxFileCount;
    }

    private final LanguageAdapterRegistry registry;
    /**
     * Reserved for future parallel analysis. Currently unused.
     */
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
    public ProjectAnalysisResult analyzeProject(String projectRoot) {
        return analyzeProject(VirtualFileSystem.instance(), projectRoot, (String) null);
    }

    /**
     * @param languages Ignored; all registered languages are analyzed regardless.
     */
    @Override
    public ProjectAnalysisResult analyzeProject(String projectRoot, Set<CodeLanguage> languages) {
        return analyzeProject(projectRoot);
    }

    /**
     * 分析整个项目（带进度回调）
     *
     * @param projectRoot      项目根目录
     * @param progressCallback 进度回调 (current, total, message)
     * @return 项目分析结果
     */
    /**
     * @param progressCallback Ignored; no progress reporting implemented yet.
     */
    public ProjectAnalysisResult analyzeProject(String projectRoot, ProgressCallback progressCallback) {
        return analyzeProject(VirtualFileSystem.instance(), projectRoot, (String) null);
    }

    /**
     * 基于 VFS 资源加载器的项目分析。depthIterator 惰性遍历文件系统；BatchQueue 仅缓冲符号注册
     * 节奏（满 batchSize 个文件后注册到全局符号表），分析结果本身仍全量累积。输入面包络由
     * maxFileSizeBytes/maxFileCount 守卫：超限文件 skip、计数超限停止遍历（truncated 标志 + WARN）。
     */
    public ProjectAnalysisResult analyzeProject(IResourceLoader resourceLoader, String vfsPath, String filePattern) {
        LOG.info("Starting project analysis from VFS: {}", vfsPath);

        Set<String> allExtensions = collectExtensions();

        List<CodeFileAnalysisResult> fileResults = new ArrayList<>();
        SymbolTable globalSymbolTable = new SymbolTable();
        int[] fileCount = {0};
        InputEnvelopeState envelope = new InputEnvelopeState();

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

        IterableIterator<IResource> it = resourceLoader.depthIterator(vfsPath, false, resource -> {
            if (resource.isDirectory()) return true;
            if (filePattern != null && !filePattern.isEmpty() && !"*".equals(filePattern)) {
                String ext = filePattern;
                int starSlash = ext.lastIndexOf('/');
                if (starSlash >= 0)
                    ext = ext.substring(starSlash + 1);
                if (ext.startsWith("*."))
                    ext = ext.substring(1);
                return resource.getName().endsWith(ext);
            }
            for (String ext : allExtensions) {
                if (resource.getName().endsWith(ext)) return true;
            }
            return false;
        });

        while (it.hasNext()) {
            IResource resource = it.next();
            if (resource.isDirectory()) continue;

            String relativePath = resource.getStdPath();
            if (relativePath.startsWith(vfsPath)) {
                relativePath = relativePath.substring(vfsPath.length());
                if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
            }

            ICodeFileAnalyzer fileAnalyzer = registry.getAnalyzer(relativePath);
            if (fileAnalyzer == null) continue;

            if (fileCountCapReached(fileCount[0], envelope, "project-scan")) {
                break;
            }

            try {
                String sourceCode = readSourceChecked(resource, relativePath, envelope);
                if (sourceCode == null) {
                    continue; // oversized skip — explicit WARN emitted inside the read checkpoint
                }
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

        LOG.info("Analyzed {} files from VFS, collected {} symbols, oversizedSkipped={}",
                fileCount[0], globalSymbolTable.size(), envelope.oversizedSkipped);

        int[] callCounts = resolveCalls(fileResults, globalSymbolTable);
        ProjectStats stats = buildStats(fileResults, globalSymbolTable, callCounts[0], callCounts[1]);

        ProjectAnalysisResult result = new ProjectAnalysisResult(fileResults, globalSymbolTable, stats);
        result.setTruncated(envelope.truncated);
        List<CodeSemanticEdge> semanticEdges = new ArrayList<>();
        runSemanticExtractors(globalSymbolTable, result.buildCallGraph(), semanticEdges);
        runFileResultExtractors(fileResults, globalSymbolTable, semanticEdges);
        result.setSemanticEdges(semanticEdges);
        LOG.info("VFS analysis: extracted {} semantic edges", semanticEdges.size());

        return result;
    }

    public ProjectAnalysisResult analyzeProject(IResourceLoader resourceLoader, String vfsPath,
                                                  String filePattern, BatchCallback batchCallback) {
        LOG.info("Starting project analysis from VFS: {} (BatchQueue paces symbol registration;"
                + " results are fully accumulated)", vfsPath);

        Set<String> allExtensions = collectExtensions();

        List<CodeFileAnalysisResult> allFileResults = new ArrayList<>();
        SymbolTable globalSymbolTable = new SymbolTable();
        int[] fileCount = {0};
        InputEnvelopeState envelope = new InputEnvelopeState();

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
            if (resource.isDirectory()) return true;
            if (filePattern != null && !filePattern.isEmpty() && !"*".equals(filePattern)) {
                String ext = filePattern;
                int starSlash = ext.lastIndexOf('/');
                if (starSlash >= 0)
                    ext = ext.substring(starSlash + 1);
                if (ext.startsWith("*."))
                    ext = ext.substring(1);
                return resource.getName().endsWith(ext);
            }
            for (String ext : allExtensions) {
                if (resource.getName().endsWith(ext)) return true;
            }
            return false;
        });

        while (it.hasNext()) {
            IResource resource = it.next();
            if (resource.isDirectory()) continue;

            String relativePath = resource.getStdPath();
            if (relativePath.startsWith(vfsPath)) {
                relativePath = relativePath.substring(vfsPath.length());
                if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
            }

            ICodeFileAnalyzer fileAnalyzer = registry.getAnalyzer(relativePath);
            if (fileAnalyzer == null) continue;

            if (fileCountCapReached(fileCount[0], envelope, "streaming-scan")) {
                break;
            }

            try {
                String sourceCode = readSourceChecked(resource, relativePath, envelope);
                if (sourceCode == null) {
                    continue; // oversized skip — explicit WARN emitted inside the read checkpoint
                }
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

        LOG.info("Batched analysis: {} files, {} symbols, oversizedSkipped={}",
                fileCount[0], globalSymbolTable.size(), envelope.oversizedSkipped);

        int[] callCounts = resolveCalls(allFileResults, globalSymbolTable);
        ProjectStats stats = buildStats(allFileResults, globalSymbolTable, callCounts[0], callCounts[1]);

        ProjectAnalysisResult result = new ProjectAnalysisResult(allFileResults, globalSymbolTable, stats);
        result.setTruncated(envelope.truncated);
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

    /** Per-run truncation tracking for the input-envelope guards (skip + truncated flag + WARN). */
    private static final class InputEnvelopeState {
        boolean truncated;
        int oversizedSkipped;
    }

    /**
     * Unified read checkpoint: every source-read site (two full-scan variants, incremental
     * re-analysis, explicit file list, batch helper) enforces the file-size guard here, so
     * oversized files are skipped with an explicit WARN — distinguishable from the silent
     * analyzer-null continue branch — instead of being read whole into the heap, and the run
     * is marked truncated.
     */
    private String readSourceChecked(IResource resource, String relativePath, InputEnvelopeState state) {
        long length = resource.length();
        if (length > maxFileSizeBytes) {
            state.truncated = true;
            state.oversizedSkipped++;
            LOG.warn("nop.code.input-file-skipped-oversized:path={},size={},maxFileSizeBytes={} (oversized"
                    + " file skipped, result set is partial)", relativePath, length, maxFileSizeBytes);
            return null;
        }
        return resource.readText();
    }

    /**
     * File-count guard: once the analyzed-file count reaches the cap, traversal stops (WARN +
     * truncated flag) so the result set stays bounded.
     */
    private boolean fileCountCapReached(int analyzedCount, InputEnvelopeState state, String context) {
        if (analyzedCount >= maxFileCount) {
            state.truncated = true;
            LOG.warn("nop.code.input-file-count-capped:analyzed={},maxFileCount={},context={} (file-count"
                    + " cap reached, remaining files skipped, result set is partial)",
                    analyzedCount, maxFileCount, context);
            return true;
        }
        return false;
    }

    private int[] resolveCalls(List<CodeFileAnalysisResult> fileResults, SymbolTable globalSymbolTable) {
        int resolvedCalls = 0;
        int unresolvedCalls = 0;

        for (CodeFileAnalysisResult file : fileResults) {
            for (CodeMethodCall call : file.getCalls()) {
                String calleeQn = call.getCalleeQualifiedName();
                if (calleeQn != null && !calleeQn.isEmpty()) {
                    if (CallReferenceResolver.resolveCall(call, globalSymbolTable::getByQualifiedName)) {
                        resolvedCalls++;
                    } else {
                        unresolvedCalls++;
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
    public ProjectAnalysisResult analyzeIncremental(String projectRoot, List<String> changedFilePaths) {
        throw new UnsupportedOperationException("Use analyzeIncremental(String, ProjectAnalysisResult) instead");
    }

    /**
     * 增量分析：基于上一次分析结果和显式变更文件列表。
     * 重新分析变更文件，保留未变更文件的分析结果，重建全局符号表和调用引用。
     *
     * @param projectRoot    项目根目录
     * @param previousResult 上一次的完整分析结果
     * @return 增量分析结果
     */
    public ProjectAnalysisResult analyzeIncremental(String projectRoot,
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
        List<IResource> sourceResources = findSourceFiles(projectRoot);
        LOG.info("Found {} source files for incremental analysis", sourceResources.size());

        // 确定哪些文件需要重新分析（通过指纹对比）
        IncrementalDetector detector = new IncrementalDetector();
        List<FileFingerprint> previousFingerprints = buildFingerprintsFromResults(projectRoot, previousResult);
        ChangeSet changes = detector.detectResourceChanges(previousFingerprints, sourceResources);

        List<String> filesToReanalyze = changes.getAddedAndModified();
        Set<String> deletedPaths = new HashSet<>();
        for (String dp : changes.getDeletedFiles()) {
            String rel = dp;
            if (dp.startsWith(projectRoot)) {
                rel = dp.substring(projectRoot.length());
                if (rel.startsWith("/")) rel = rel.substring(1);
            }
            deletedPaths.add(rel);
        }

        LOG.info("Incremental changes: {} added/modified, {} deleted, {} unchanged",
                filesToReanalyze.size(), deletedPaths.size(), changes.getUnchangedFiles().size());

        // 第一阶段：重新分析变更文件
        Map<String, CodeFileAnalysisResult> updatedFileMap = new LinkedHashMap<>(previousFileMap);

        // 移除已删除的文件
        for (String deletedPath : deletedPaths) {
            updatedFileMap.remove(deletedPath);
        }

        InputEnvelopeState envelope = new InputEnvelopeState();
        int analyzedCount = 0;

        // 重新分析变更文件
        for (String filePath : filesToReanalyze) {
            try {
                IResource res = VirtualFileSystem.instance().getResource(filePath);
                String relativePath = filePath;
                if (relativePath.startsWith(projectRoot)) {
                    relativePath = relativePath.substring(projectRoot.length());
                    if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
                }
                ICodeFileAnalyzer analyzer = registry.getAnalyzer(relativePath);
                if (analyzer == null) {
                    continue;
                }
                if (fileCountCapReached(analyzedCount, envelope, "incremental-reanalysis")) {
                    break;
                }
                String sourceCode = readSourceChecked(res, relativePath, envelope);
                if (sourceCode == null) {
                    continue; // oversized skip — explicit WARN emitted inside the read checkpoint
                }
                CodeFileAnalysisResult result = analyzer.analyze(relativePath, sourceCode);
                if (result != null) {
                    updatedFileMap.put(relativePath, result);
                    analyzedCount++;
                }
            } catch (Exception e) {
                LOG.warn("Failed to re-analyze file: {}", filePath, e);
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

        ProjectAnalysisResult incrementalResult = new ProjectAnalysisResult(fileResults, globalSymbolTable, stats);
        incrementalResult.setTruncated(envelope.truncated);
        return incrementalResult;
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
    public ProjectAnalysisResult analyzeIncremental(String projectRoot,
                                                    String indexId,
                                                    IFingerprintStore fingerprintStore) throws IOException {
        LOG.info("Starting store-based incremental analysis: {}", projectRoot);
        List<FileFingerprint> previousFingerprints = fingerprintStore.loadFingerprints(indexId);

        List<IResource> sourceResources = findSourceFiles(projectRoot);

        IncrementalDetector detector = new IncrementalDetector();
        ChangeSet changes = detector.detectResourceChanges(previousFingerprints, sourceResources);

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

        List<String> changedFiles = changes.getAddedAndModified();
        LOG.info("Incremental analysis: analysing {} changed files (no merge with history)", changedFiles.size());

        ProjectAnalysisResult result = analyzeFiles(projectRoot, changedFiles);

        List<FileFingerprint> newFingerprints = detector.computeResourceFingerprints(sourceResources);
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
    public ProjectAnalysisResult analyzeIncremental(String projectRoot,
                                                    String manifestPath) throws IOException {
        LOG.info("Starting manifest-based incremental analysis: {}", projectRoot);

        ManifestStore manifestStore = new ManifestStore();
        List<FileFingerprint> previousFingerprints = manifestStore.load(manifestPath);

        List<IResource> sourceResources = findSourceFiles(projectRoot);

        IncrementalDetector detector = new IncrementalDetector();
        ChangeSet changes = detector.detectResourceChanges(previousFingerprints, sourceResources);

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

        List<String> changedFiles = changes.getAddedAndModified();
        LOG.info("Incremental analysis: analysing {} changed files (no merge with history)", changedFiles.size());

        ProjectAnalysisResult result = analyzeFiles(projectRoot, changedFiles);

        List<FileFingerprint> newFingerprints = detector.computeResourceFingerprints(sourceResources);
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
    private ProjectAnalysisResult analyzeFiles(String projectRoot, List<String> files) throws IOException {
        List<CodeFileAnalysisResult> fileResults = new ArrayList<>();
        SymbolTable globalSymbolTable = new SymbolTable();
        InputEnvelopeState envelope = new InputEnvelopeState();
        int analyzedCount = 0;

        for (String filePath : files) {
            try {
                IResource res = VirtualFileSystem.instance().getResource(filePath);
                String relativePath = filePath;
                if (relativePath.startsWith(projectRoot)) {
                    relativePath = relativePath.substring(projectRoot.length());
                    if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
                }
                ICodeFileAnalyzer analyzer = registry.getAnalyzer(relativePath);
                if (analyzer == null) {
                    continue;
                }
                if (fileCountCapReached(analyzedCount, envelope, "analyze-files")) {
                    break;
                }
                String sourceCode = readSourceChecked(res, relativePath, envelope);
                if (sourceCode == null) {
                    continue; // oversized skip — explicit WARN emitted inside the read checkpoint
                }
                CodeFileAnalysisResult result = analyzer.analyze(relativePath, sourceCode);
                if (result != null) {
                    fileResults.add(result);
                    analyzedCount++;
                    for (CodeSymbol symbol : result.getSymbols()) {
                        String qn = symbol.getQualifiedName();
                        if (qn != null && !qn.isEmpty()) {
                            globalSymbolTable.add(symbol);
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to analyze file: {}", filePath, e);
            }
        }

        int[] callCounts = resolveCalls(fileResults, globalSymbolTable);
        ProjectStats stats = buildStats(fileResults, globalSymbolTable, callCounts[0], callCounts[1]);

        LOG.info("analyzeFiles: {} files, {} symbols, {} resolved calls",
                fileResults.size(), globalSymbolTable.size(), callCounts[0]);

        ProjectAnalysisResult result = new ProjectAnalysisResult(fileResults, globalSymbolTable, stats);
        result.setTruncated(envelope.truncated);
        return result;
    }

    /**
     * 从分析结果构建指纹列表（用于增量对比）
     */
    private List<FileFingerprint> buildFingerprintsFromResults(String projectRoot, ProjectAnalysisResult result) {
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

            // 构建完整 VFS 路径以匹配 IResource.getPath()
            String fullPath = filePath;
            if (!filePath.startsWith(projectRoot)) {
                fullPath = projectRoot;
                if (!fullPath.endsWith("/")) fullPath += "/";
                fullPath += filePath;
            }

            fingerprints.add(new FileFingerprint(fullPath, contentHash, 0, 0));
        }
        return fingerprints;
    }

    /**
     * 分析一批源文件，返回分析结果列表。
     */
    private List<CodeFileAnalysisResult> analyzeBatch(List<String> files, String projectRoot) {
        List<CodeFileAnalysisResult> results = new ArrayList<>();
        InputEnvelopeState envelope = new InputEnvelopeState();
        int analyzedCount = 0;
        for (String filePath : files) {
            try {
                IResource res = VirtualFileSystem.instance().getResource(filePath);
                String relativePath = filePath;
                if (relativePath.startsWith(projectRoot)) {
                    relativePath = relativePath.substring(projectRoot.length());
                    if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
                }
                ICodeFileAnalyzer analyzer = registry.getAnalyzer(relativePath);
                if (analyzer == null) {
                    continue;
                }
                if (fileCountCapReached(analyzedCount, envelope, "analyze-batch")) {
                    break;
                }
                String sourceCode = readSourceChecked(res, relativePath, envelope);
                if (sourceCode == null) {
                    continue; // oversized skip — explicit WARN emitted inside the read checkpoint
                }
                CodeFileAnalysisResult result = analyzer.analyze(relativePath, sourceCode);
                if (result != null) {
                    results.add(result);
                    analyzedCount++;
                }
            } catch (Exception e) {
                LOG.warn("Failed to analyze file: {}", filePath, e);
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
    private List<IResource> findSourceFiles(String projectRoot) {
        List<String> allExtensions = new ArrayList<>();
        Set<String> excludePatterns = new HashSet<>();

        for (CodeLanguage lang : registry.getSupportedLanguages()) {
            ILanguageAdapter adapter = registry.getAdapter(lang);
            allExtensions.addAll(adapter.getFileExtensions());
            excludePatterns.addAll(adapter.getExcludePatterns());
        }

        List<IResource> result = new ArrayList<>();
        IterableIterator<IResource> it = VirtualFileSystem.instance().depthIterator(projectRoot, false, resource -> {
            if (resource.isDirectory()) return false;
            String name = resource.getName();
            for (String ext : allExtensions) {
                if (name.endsWith(ext)) {
                    return !shouldExclude(resource.getPath(), excludePatterns);
                }
            }
            return false;
        });

        while (it.hasNext()) {
            result.add(it.next());
        }
        return result;
    }

    /**
     * 判断文件是否应该被排除
     */
    private boolean shouldExclude(String path, Set<String> excludePatterns) {
        for (String pattern : excludePatterns) {
            String dir = pattern.replace("**/", "").replace("/**", "");
            if (dir.startsWith("/")) dir = dir.substring(1);
            if (dir.endsWith("/")) dir = dir.substring(0, dir.length() - 1);
            String[] segments = path.split("/");
            for (String segment : segments) {
                if (segment.equals(dir)) {
                    return true;
                }
            }
        }
        return false;
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
    private boolean matchesFilePattern(String name, String filePattern) {
        if (filePattern == null || filePattern.isEmpty() || "*".equals(filePattern))
            return false;
        String suffix = filePattern;
        if (suffix.startsWith("**/")) suffix = suffix.substring(2);
        if (suffix.startsWith("*")) suffix = suffix.substring(1);
        if (suffix.startsWith(".")) suffix = suffix.substring(1);
        return name.endsWith(suffix);
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int current, int total, String message);
    }

    @FunctionalInterface
    public interface BatchCallback {
        void onBatchComplete(List<CodeFileAnalysisResult> batchResults);
    }

}
