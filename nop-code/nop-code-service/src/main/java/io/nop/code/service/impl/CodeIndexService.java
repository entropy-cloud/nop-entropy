package io.nop.code.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.time.CoreMetrics;
import io.nop.code.core.NopCodeCoreErrors;
import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.analyzer.CallReferenceResolver;
import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.analyzer.ILanguageAdapter;
import io.nop.code.core.analyzer.ProjectAnalysisResult;
import io.nop.code.core.analyzer.ProjectAnalyzer;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.incremental.ChangeSet;
import io.nop.code.core.incremental.DependencyPropagator;
import io.nop.code.core.incremental.FileFingerprint;
import io.nop.code.core.incremental.IFingerprintStore;
import io.nop.code.core.incremental.IncrementalDetector;
import io.nop.code.core.model.*;
import io.nop.code.core.model.HeuristicContext;
import io.nop.code.core.model.IHeuristicEdgeSynthesizer;
import io.nop.code.core.model.EdgeProvenance;
import io.nop.code.core.model.CodeRouteInfo;
import io.nop.code.core.resolver.IImportResolver;
import io.nop.code.lang.go.GoImportResolver;
import io.nop.code.lang.java.JavaImportResolver;
import io.nop.code.lang.python.PythonImportResolver;
import io.nop.code.lang.typescript.TypeScriptImportResolver;
import io.nop.code.core.semantic.CodeSemanticEdge;
import io.nop.code.core.semantic.ISemanticEdgeExtractor;
import io.nop.code.core.util.DigestHelper;
import io.nop.code.core.util.ExtDataHelper;
import io.nop.code.dao.entity.NopCodeAnnotationUsage;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.dao.entity.NopCodeDependency;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.code.dao.entity.NopCodeFlow;
import io.nop.code.dao.entity.NopCodeFlowMembership;
import io.nop.code.dao.entity.NopCodeIndex;
import io.nop.code.dao.entity.NopCodeInheritance;
import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.code.dao.entity.NopCodeSemanticEdge;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.dao.entity.NopCodeUsage;
import io.nop.code.flow.ChangeAnalysisResult;
import io.nop.code.flow.ChangeAnalyzer;
import io.nop.code.flow.DeadCodeReport;
import io.nop.code.flow.ExecutionFlow;
import io.nop.code.flow.FlowDetector;
import io.nop.code.flow.IChangeAnalyzer;
import io.nop.code.flow.IDeadCodeDetector;
import io.nop.code.flow.IFlowDetector;
import io.nop.code.service.graph.AnnotationPatternExtractor;
import io.nop.code.service.graph.DocKeywordExtractor;
import io.nop.code.service.graph.NameSimilarityExtractor;
import io.nop.code.service.graph.InterfaceImplSynthesizer;
import io.nop.code.service.graph.SpringEventSynthesizer;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.api.dto.*;
import io.nop.code.service.incremental.OrmFingerprintStore;
import io.nop.code.service.util.CodeSymbolConverter;
import io.nop.commons.batch.BatchQueue;
import io.nop.commons.collections.IterableIterator;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchableDoc;
import static io.nop.code.service.NopCodeErrors.*;
public class CodeIndexService implements ICodeIndexService {

    private static final Logger LOG = LoggerFactory.getLogger(CodeIndexService.class);

    static final int MAX_QUERY_RESULTS = 10000;
    private static final int BATCH_SIZE = 1000;

    private final CodeCacheManager cacheManager = new CodeCacheManager();
    private CodeSearchService searchService;
    private CodeGraphService graphService;
    private CodeQueryService queryService;
    private GraphMetricMaterializer graphMetricMaterializer;

    private final ConcurrentHashMap<String, ReentrantLock> indexLocks = new ConcurrentHashMap<>();

    private <T> T withIndexLock(String indexId, java.util.function.Supplier<T> action) {
        ReentrantLock lock = indexLocks.computeIfAbsent(indexId, k -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private void withIndexLock(String indexId, Runnable action) {
        ReentrantLock lock = indexLocks.computeIfAbsent(indexId, k -> new ReentrantLock());
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    protected LanguageAdapterRegistry registry;
    protected ProjectAnalyzer analyzer;
    protected final Map<String, IImportResolver> importResolvers = new HashMap<>();
    protected final List<IHeuristicEdgeSynthesizer> heuristicSynthesizers = new ArrayList<>();

    @Inject
    protected IDaoProvider daoProvider;

    @Inject
    protected IOrmTemplate ormTemplate;

    @Inject
    protected ITransactionTemplate transactionTemplate;

    private final java.util.concurrent.ConcurrentHashMap<String, Long> rebuildDebounceMap =
            new java.util.concurrent.ConcurrentHashMap<>();

    // bounded snapshot of per-index affected-file lists (N3.1 propagation readback)
    private final java.util.concurrent.ConcurrentHashMap<String, List<String>> incrementalAffectedFilesMap =
            new java.util.concurrent.ConcurrentHashMap<>();

    private long debounceMillis = 30_000L;

    @InjectValue("@cfg:nop.code.rebuild.debounce-millis|30000")
    public void setDebounceMillis(long debounceMillis) {
        this.debounceMillis = debounceMillis;
    }

    /** Test-visible reset so idempotency verification can bypass the debounce window. */
    public void resetRebuildDebounce() {
        rebuildDebounceMap.clear();
    }

    private synchronized void ensureSubServices() {
        if (searchService == null && daoProvider != null) {
            searchService = new CodeSearchService(daoProvider, searchEngine, cacheManager);
            graphMetricMaterializer = new GraphMetricMaterializer(daoProvider, cacheManager,
                    transactionTemplate, ormTemplate);
            graphService = new CodeGraphService(daoProvider, cacheManager, graphMetricMaterializer);
            queryService = new CodeQueryService(daoProvider, cacheManager, ormTemplate);
        }
    }

    protected ISearchEngine searchEngine;

    @Inject
    public void setSearchEngine(@Nullable ISearchEngine searchEngine) {
        this.searchEngine = searchEngine;
    }

    protected IFlowDetector flowDetector;

    @Inject
    public void setFlowDetector(@Nullable IFlowDetector flowDetector) {
        this.flowDetector = flowDetector;
        if (flowDetector != null && this.changeAnalyzer != null) {
            this.changeAnalyzer.setFlowDetector(flowDetector);
        }
    }

    protected IChangeAnalyzer changeAnalyzer;

    @Inject
    public void setChangeAnalyzer(@Nullable IChangeAnalyzer changeAnalyzer) {
        this.changeAnalyzer = changeAnalyzer;
        if (changeAnalyzer != null && this.flowDetector != null) {
            changeAnalyzer.setFlowDetector(this.flowDetector);
        }
    }

    protected IDeadCodeDetector deadCodeDetector;

    @Inject
    public void setDeadCodeDetector(@Nullable IDeadCodeDetector deadCodeDetector) {
        this.deadCodeDetector = deadCodeDetector;
    }

    protected IFingerprintStore fingerprintStore;

    // N6.5: per-index access policy — enforced at every ICodeIndexService public entry;
    // default permissive (backward compatible), deployment injects the tenant policy.
    private io.nop.code.service.cluster.IndexAccessPolicy accessPolicy =
            io.nop.code.service.cluster.PermissiveIndexAccessPolicy.INSTANCE;

    @Inject
    public void setAccessPolicy(@Nullable io.nop.code.service.cluster.IndexAccessPolicy accessPolicy) {
        this.accessPolicy = accessPolicy != null
                ? accessPolicy
                : io.nop.code.service.cluster.PermissiveIndexAccessPolicy.INSTANCE;
    }

    private void checkReadAccess(String indexId) {
        accessPolicy.checkReadAccess(indexId);
    }

    private void checkWriteAccess(String indexId) {
        accessPolicy.checkWriteAccess(indexId);
    }

    public void setFingerprintStore(IFingerprintStore fingerprintStore) {
        this.fingerprintStore = fingerprintStore;
    }

    protected IFingerprintStore getFingerprintStore() {
        if (fingerprintStore == null) {
            fingerprintStore = new OrmFingerprintStore(daoProvider, ormTemplate);
        }
        return fingerprintStore;
    }

    public CodeIndexService() {
    }

    @Inject
    public void setRegistry(LanguageAdapterRegistry registry) {
        this.registry = registry;
        Map<String, ILanguageAdapter> adapterMap = BeanContainer.instance().getBeansOfType(ILanguageAdapter.class);
        for (ILanguageAdapter adapter : adapterMap.values()) {
            registry.registerAdapter(adapter);
        }
        this.analyzer = new ProjectAnalyzer(registry);
        registerSemanticExtractors();
        registerImportResolvers();
        registerHeuristicSynthesizers();
    }

    public CodeIndexService(LanguageAdapterRegistry registry, ProjectAnalyzer analyzer) {
        this.registry = registry;
        this.analyzer = analyzer;
        registerSemanticExtractors();
        registerImportResolvers();
        registerHeuristicSynthesizers();
    }

    private void registerSemanticExtractors() {
        for (ISemanticEdgeExtractor extractor : discoverSemanticExtractors()) {
            analyzer.registerSemanticExtractor(extractor);
        }
    }

    private List<ISemanticEdgeExtractor> discoverSemanticExtractors() {
        List<ISemanticEdgeExtractor> extractors = new ArrayList<>();
        extractors.add(new NameSimilarityExtractor());
        extractors.add(new DocKeywordExtractor());
        extractors.add(new AnnotationPatternExtractor());
        return extractors;
    }

    private void registerImportResolvers() {
        IImportResolver[] resolvers = {
                new JavaImportResolver(),
                new PythonImportResolver(),
                new TypeScriptImportResolver(),
                new GoImportResolver()
        };
        for (IImportResolver resolver : resolvers) {
            importResolvers.put(resolver.getLanguage(), resolver);
        }
    }

    private void registerHeuristicSynthesizers() {
        heuristicSynthesizers.add(new InterfaceImplSynthesizer());
        heuristicSynthesizers.add(new SpringEventSynthesizer());
    }

    private CodeInheritance entityToInheritance(NopCodeInheritance entity) {
        CodeInheritance inh = new CodeInheritance();
        inh.setId(entity.getId());
        inh.setSubTypeId(entity.getSubTypeId());
        String superTypeQN = null;
        if (entity.getSuperTypeId() != null && entity.getSuperType() != null) {
            superTypeQN = entity.getSuperType().getQualifiedName();
        }
        inh.setSuperTypeQualifiedName(superTypeQN != null ? superTypeQN : entity.getSuperTypeId());
        inh.setRelationType(entity.getRelationType() != null
                ? CodeRelationType.valueOf(entity.getRelationType()) : null);
        return inh;
    }


    // ====== Rebuild-from-DB Helpers ======

    private SymbolTable getOrRebuildSymbolTable(String indexId) {
        ensureSubServices();
        return cacheManager.getOrRebuildSymbolTable(indexId, daoProvider, CodeSymbolConverter::toCodeSymbol);
    }

    private CallGraph getOrRebuildCallGraph(String indexId) {
        ensureSubServices();
        return cacheManager.getOrRebuildCallGraph(indexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
    }

    private void invalidateAnalysisCache(String indexId) {
        cacheManager.invalidateAnalysisCache(indexId, flowDetector);
    }

    // ====== Indexing ======

    @Override
    public int indexDirectory(String indexId, String vfsPath, String filePattern) {
        checkWriteAccess(indexId);
        validatePath(vfsPath);
        invalidateAnalysisCache(indexId);
        return withIndexLock(indexId, () -> {
            String resolvedPath = resolveVfsPath(vfsPath);
            validateLocalPath(indexId, resolvedPath);
            ProjectAnalysisResult result = analyzer.analyzeProject(
                    VirtualFileSystem.instance(), resolvedPath, filePattern,
                    batch -> {});
            final ProjectAnalysisResult finalResult = result;
            return transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn ->
                    ormTemplate.runInSession(session -> {
                        ensureIndexEntity(indexId, resolvedPath, session);
                        // Delete existing records for the files being re-indexed so a retry over the
                        // same directory is idempotent. Symbols/relational rows have non-deterministic
                        // IDs and must be cleared before re-saving; the file row is also cleared and
                        // re-inserted. Mirrors triggerIncrementalIndex's delete-before-reindex pattern.
                        for (CodeFileAnalysisResult fr : finalResult.getFileResults()) {
                            deleteFileRecords(indexId, Collections.singletonList(fr.getFilePath()));
                        }
                        // re-index changes content: materialized global metrics become stale and
                        // are invalidated here (next query self-heals by re-materializing)
                        ensureSubServices();
                        graphMetricMaterializer.deleteByIndex(session, indexId);
                        persistInSession(indexId, resolvedPath, finalResult, session);
                        return finalResult.getFileResults().size();
                    }));
        });
    }

    @Override
    public CodeFileAnalysisResult indexFile(String indexId, String filePath, String sourceCode) {
        checkWriteAccess(indexId);
        ICodeFileAnalyzer fileAnalyzer = registry.getAnalyzer(filePath);
        if (fileAnalyzer == null) {
            throw new NopException(ERR_NO_ANALYZER_FOR_FILE).param(ARG_INDEX_ID, indexId).param(ARG_FILE_PATH, filePath);
        }
        CodeFileAnalysisResult result = fileAnalyzer.analyze(filePath, sourceCode);

        withIndexLock(indexId, () -> {
            transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn ->
                    ormTemplate.runInSession(session -> {
                        ensureIndexEntity(indexId, null, session);
                        // N3.1-s: snapshot the dependent call edges BEFORE deleteFileRecords
                        // destroys them (single-file form of the incremental snapshot).
                        Map<String, String> oldQnBySymbolId = snapshotSymbolQualifiedNames(indexId,
                                Collections.singletonList(filePath));
                        List<CallSnapshot> dependentCalls = snapshotDependentCalls(indexId,
                                oldQnBySymbolId.keySet(),
                                Collections.singleton(generateFileId(indexId, filePath)));
                        // Delete existing records for this file so a retry is idempotent: symbols and
                        // other relational rows carry non-deterministic IDs, so they must be cleared
                        // before re-saving (mirrors triggerIncrementalIndex's delete-before-reindex).
                        deleteFileRecords(indexId, Collections.singletonList(filePath));
                        // Content change invalidates materialized global metrics; deleteByIndex
                        // contains session.clear() so it must run before the persist below.
                        ensureSubServices();
                        graphMetricMaterializer.deleteByIndex(session, indexId);
                        // N3.1-s: resolve this file's call references before persist (single-file
                        // form of the incremental two-stage flow; the file's own pre-delete rows
                        // are excluded from the DB lookup).
                        SymbolTable ownSymbols = new SymbolTable();
                        if (result.getSymbols() != null) {
                            for (CodeSymbol symbol : result.getSymbols()) {
                                String qn = symbol.getQualifiedName();
                                if (qn != null && !qn.isEmpty()) {
                                    ownSymbols.add(symbol);
                                }
                            }
                        }
                        resolveReanalyzedCalls(indexId, Collections.singletonList(result), ownSymbols,
                                Collections.singleton(filePath));
                        persistSingleFileInSession(indexId, result, session);
                        restoreDependentCalls(indexId, session, dependentCalls, oldQnBySymbolId, ownSymbols);
                        return null;
                    }));
            updateIndexStats(indexId);
            invalidateAnalysisCache(indexId);
        });
        return result;
    }

    // ====== Cluster Shard Execution (N6.3) ======

    @Override
    public io.nop.code.api.dto.IndexFileSetResult indexFileSet(String indexId, String vfsPath,
                                                               List<String> relativePaths) {
        checkWriteAccess(indexId);
        validatePath(vfsPath);
        invalidateAnalysisCache(indexId);
        io.nop.code.api.dto.IndexFileSetResult result = new io.nop.code.api.dto.IndexFileSetResult();
        if (relativePaths == null || relativePaths.isEmpty()) {
            return result;
        }
        IResourceLoader vfs = VirtualFileSystem.instance();
        return withIndexLock(indexId, () -> transactionTemplate.runInTransaction(null,
                TransactionPropagation.REQUIRED, txn ->
                        ormTemplate.runInSession(session -> {
                            ensureIndexEntity(indexId, vfsPath, session);
                            ensureSubServices();
                            graphMetricMaterializer.deleteByIndex(session, indexId);
                            List<io.nop.code.api.dto.IndexFileSetResult> holder = new ArrayList<>(1);
                            holder.add(result);
                            for (String relativePath : relativePaths) {
                                // N2.4 quirk: the VFS resolves raw absolute paths to zero
                                // resources; the file: URI form is required
                                String absolute = vfsPath.endsWith("/")
                                        ? vfsPath + relativePath : vfsPath + "/" + relativePath;
                                String mapped = absolute.startsWith("file:") ? absolute : "file:" + absolute;
                                ICodeFileAnalyzer fileAnalyzer = registry.getAnalyzer(relativePath);
                                if (fileAnalyzer == null) {
                                    result.getSkippedPaths().add(relativePath);
                                    continue;
                                }
                                try {
                                    IResource resource = vfs.getResource(mapped);
                                    if (resource == null || !resource.exists()) {
                                        result.getSkippedPaths().add(relativePath);
                                        continue;
                                    }
                                    String sourceCode = resource.readText();
                                    deleteFileRecords(indexId, Collections.singletonList(relativePath));
                                    CodeFileAnalysisResult fileResult = fileAnalyzer.analyze(relativePath, sourceCode);
                                    if (fileResult == null) {
                                        result.getSkippedPaths().add(relativePath);
                                        continue;
                                    }
                                    // N3.1-s semantics: resolve before persist (indexId-scoped DB
                                    // symbols + this file's own symbols)
                                    SymbolTable ownSymbols = new SymbolTable();
                                    for (CodeSymbol symbol : fileResult.getSymbols()) {
                                        String qn = symbol.getQualifiedName();
                                        if (qn != null && !qn.isEmpty()) {
                                            ownSymbols.add(symbol);
                                        }
                                    }
                                    resolveReanalyzedCalls(indexId, Collections.singletonList(fileResult),
                                            ownSymbols, Collections.singleton(relativePath));
                                    persistSingleFileInSession(indexId, fileResult, session);
                                    result.setIndexedCount(result.getIndexedCount() + 1);
                                } catch (Exception e) {
                                    LOG.warn("Failed to index shard file {}", relativePath, e);
                                    result.getSkippedPaths().add(relativePath);
                                }
                            }
                            updateIndexStats(indexId);
                            return holder.get(0);
                        })));
    }

    // ====== File Queries ======

    @Override
    public List<CodeFileAnalysisResult> getFiles(String indexId) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.getFiles(indexId);
    }

    @Override
    public CodeFileAnalysisResult getFile(String indexId, String filePath) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.getFile(indexId, filePath);
    }

    @Override
    public String getFileSourceCode(String indexId, String filePath) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.getFileSourceCode(indexId, filePath);
    }

    @Override
    public List<CodeSymbol> getFileSymbols(String indexId, String filePath) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.getFileSymbols(indexId, filePath);
    }

    @Override
    public List<CodeSymbol> getFileTypes(String indexId, String filePath) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.getFileTypes(indexId, filePath);
    }

    @Override
    public FileOutlineDTO getFileOutline(String indexId, String filePath) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.getFileOutline(indexId, filePath);
    }

    @Override
    public List<FileTreeNode> getFileTree(String indexId) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.getFileTree(indexId);
    }

    @Override
    public List<ModuleDigestDTO> getModuleDigest(String indexId, String dirPath, boolean includePrivate) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.getModuleDigest(indexId, dirPath, includePrivate);
    }

    @Override
    public List<PublicAPIDTO> getPublicSurface(String indexId, String dirPath) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.getPublicSurface(indexId, dirPath);
    }

    // ====== Symbol Queries ======

    @Override
    public CodeSymbol getSymbolById(String indexId, String symbolId) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.getSymbolById(indexId, symbolId);
    }

    @Override
    public CodeSymbol findSymbolByQualifiedName(String indexId, String qualifiedName) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.findSymbolByQualifiedName(indexId, qualifiedName);
    }

    @Override
    public List<CodeSymbol> findSymbols(String indexId, String query, List<CodeSymbolKind> kinds,
                                        String packageName, int limit) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.findSymbols(indexId, query, kinds, packageName, limit);
    }

    @Override
    public PageBean<CodeSymbol> findSymbolsPage(String indexId, String query, List<CodeSymbolKind> kinds,
                                                 String packageName, long offset, int limit) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.findSymbolsPage(indexId, query, kinds, packageName, offset, limit);
    }

    @Override
    public List<CodeAnnotationUsage> getSymbolUsages(String indexId, String symbolId, int limit) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.getSymbolUsages(indexId, symbolId, limit);
    }

    @Override
    public List<ReferenceDTO> findReferencedBy(String indexId, String qualifiedName, String kind, int limit) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.findReferencedBy(indexId, qualifiedName, kind, limit);
    }

    @Override
    public String getSymbolSourceCode(String indexId, String symbolId, int linesBefore, int linesAfter) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.getSymbolSourceCode(indexId, symbolId, linesBefore, linesAfter);
    }

    @Override
    public SymbolSourceDTO showSymbolSource(String indexId, String qualifiedName, boolean includeBody) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.showSymbolSource(indexId, qualifiedName, includeBody);
    }

    // ====== Type Queries ======

    @Override
    public TypeOutlineDTO getTypeOutline(String indexId, String qualifiedName) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.getTypeOutline(indexId, qualifiedName);
    }

    @Override
    public List<TypeOutlineDTO> batchGetTypeOutlines(String indexId, List<String> qualifiedNames) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.batchGetTypeOutlines(indexId, qualifiedNames);
    }

    @Override
    public List<CodeSearchResultDTO> searchCode(String indexId, String query, String searchType,
                                                 String language, String filePattern, int limit) {
        checkReadAccess(indexId);
        ensureSubServices();
        return searchService.searchCode(indexId, query, searchType, language, filePattern, limit);
    }

    // ====== Hierarchy Queries ======

    @Override
    public TypeHierarchyDTO getTypeHierarchy(String indexId, String qualifiedName,
                                             String direction, int maxDepth) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.getTypeHierarchy(indexId, qualifiedName, direction, maxDepth);
    }

    @Override
    public CallHierarchyDTO getCallHierarchy(String indexId, String qualifiedName,
                                             String direction, int maxDepth) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.getCallHierarchy(indexId, qualifiedName, direction, maxDepth);
    }

    // ====== Index Management ======

    @Override
    public IndexStatsDTO getIndexStats(String indexId) {
        checkReadAccess(indexId);
        if (daoProvider == null) {
            IndexStatsDTO stats = new IndexStatsDTO();
            stats.setIndexId(indexId);
            return stats;
        }

        IndexStatsDTO stats = new IndexStatsDTO();
        stats.setIndexId(indexId);

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean fileQuery = new QueryBean();
        fileQuery.addFilter(FilterBeans.eq("indexId", indexId));
        stats.setFileCount((int) fileDao.countByQuery(fileQuery));

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean symbolQuery = new QueryBean();
        symbolQuery.addFilter(FilterBeans.eq("indexId", indexId));
        stats.setSymbolCount((int) symbolDao.countByQuery(symbolQuery));

        QueryBean kindQuery = new QueryBean();
        kindQuery.addFilter(FilterBeans.eq("indexId", indexId));
        kindQuery.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("kind"));
        Map<String, Integer> kindCounts = new LinkedHashMap<>();
        long kindOffset = 0;
        while (true) {
            kindQuery.setOffset(kindOffset);
            kindQuery.setLimit(BATCH_SIZE);
            List<Map<String, Object>> kindResults = symbolDao.selectFieldsByQuery(kindQuery);
            for (Map<String, Object> row : kindResults) {
                String kind = row.get("kind") != null ? row.get("kind").toString() : "UNKNOWN";
                kindCounts.merge(kind, 1, Integer::sum);
            }
            if (kindResults.size() < BATCH_SIZE) break;
            kindOffset += BATCH_SIZE;
        }
        if (!kindCounts.isEmpty()) {
            stats.setSymbolCounts(kindCounts);
        }
        return stats;
    }

    @Override
    public List<String> getIndexIds() {
        if (daoProvider == null) return Collections.emptyList();
        IEntityDao<NopCodeIndex> indexDao = daoProvider.daoFor(NopCodeIndex.class);
        return indexDao.findAll().stream()
                .map(NopCodeIndex::getId)
                .collect(Collectors.toList());
    }

    private static final int DELETE_BATCH_SIZE = 500;

    @Override
    public void materializeGraphMetrics(String indexId) {
        checkWriteAccess(indexId);
        ensureSubServices();
        graphMetricMaterializer.materialize(indexId);
    }

    @Override
    public List<io.nop.code.api.dto.SurprisingConnectionDTO> getSurprisingConnections(String indexId, int topN,
                                                                                      Integer minScore) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.getSurprisingConnections(indexId, topN, minScore);
    }

    @Override
    public List<io.nop.code.api.dto.ExplorationQuestionDTO> getExplorationQuestions(String indexId, int topN) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.getExplorationQuestions(indexId, topN);
    }

    @Override
    public List<String> getLastIncrementalAffectedFiles(String indexId) {
        checkReadAccess(indexId);
        List<String> snapshot = incrementalAffectedFilesMap.get(indexId);
        return snapshot != null ? new ArrayList<>(snapshot) : new ArrayList<>();
    }

    @Override
    public io.nop.code.api.dto.RebuildFromCommitResult triggerRebuildFromCommit(String indexId, String projectPath,
                                                                                String baselineCommitish,
                                                                                String targetCommitish) {
        checkWriteAccess(indexId);
        io.nop.code.api.dto.RebuildFromCommitResult result = new io.nop.code.api.dto.RebuildFromCommitResult();
        long now = System.currentTimeMillis();
        Long last = rebuildDebounceMap.get(indexId);
        if (last != null && now - last < debounceMillis) {
            result.setDebounced(true);
            result.setStatusMessage("debounced: rebuild for this index was triggered within "
                    + debounceMillis + "ms window");
            return result;
        }

        String workdir = normalizeWorkdir(projectPath);
        validateGitRef(baselineCommitish);
        validateGitRef(targetCommitish);
        requireRepoRoot(workdir);
        requireHeadAt(workdir, targetCommitish);

        List<String> changed = gitDiffNames(workdir, baselineCommitish, targetCommitish);
        if (changed.isEmpty()) {
            result.setSkippedNoChanges(true);
            result.setChangedCount(0);
            result.setStatusMessage("git diff reports no changed files between commits");
            rebuildDebounceMap.put(indexId, now);
            return result;
        }

        // triggerIncrementalIndex's VFS resource scan requires the file: URI form; the raw
        // absolute path yields zero resources (fingerprint no-op). manifestPath is deprecated.
        int changedCount = triggerIncrementalIndex(indexId, "file:" + workdir, null);
        result.setChangedCount(changedCount);
        if (changedCount == 0) {
            result.setStatusMessage("worktree already in sync with the index (fingerprint no-op)");
        }
        rebuildDebounceMap.put(indexId, now);
        return result;
    }

    private static String normalizeWorkdir(String projectPath) {
        String path = projectPath != null && projectPath.startsWith("file:")
                ? projectPath.substring("file:".length()) : projectPath;
        try {
            return new java.io.File(path).getCanonicalPath();
        } catch (java.io.IOException e) {
            throw new NopException(ERR_CODE_REBUILD_NOT_GIT_REPO).param(ARG_PATH, path);
        }
    }

    private static void validateGitRef(String ref) {
        if (ref == null || !GIT_REF_PATTERN.matcher(ref).matches()) {
            throw new NopException(ERR_CODE_REBUILD_INVALID_GIT_REF)
                    .param("gitRef", String.valueOf(ref));
        }
    }

    private static final java.util.regex.Pattern GIT_REF_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9._/\\-~]{1,256}$");
    private static final int GIT_TIMEOUT_MILLIS = 30_000;

    private static void requireRepoRoot(String workdir) {
        String toplevel = gitOutput(workdir, "rev-parse", "--show-toplevel");
        try {
            if (!new java.io.File(toplevel).getCanonicalFile()
                    .equals(new java.io.File(workdir).getCanonicalFile())) {
                throw new NopException(ERR_CODE_REBUILD_NOT_GIT_REPO).param(ARG_PATH, workdir);
            }
        } catch (java.io.IOException e) {
            throw new NopException(ERR_CODE_REBUILD_NOT_GIT_REPO).param(ARG_PATH, workdir).cause(e);
        }
    }

    private static void requireHeadAt(String workdir, String targetCommitish) {
        String head = gitOutput(workdir, "rev-parse", "HEAD");
        String target = gitOutput(workdir, "rev-parse", targetCommitish);
        if (!head.equals(target)) {
            throw new NopException(ERR_CODE_REBUILD_HEAD_MISMATCH)
                    .param("head", head).param("target", target);
        }
    }

    private static List<String> gitDiffNames(String workdir, String baseline, String target) {
        String output = gitOutput(workdir, "diff", baseline + ".." + target, "--name-only");
        List<String> files = new ArrayList<>();
        for (String line : output.split("\n")) {
            if (!line.isEmpty()) {
                files.add(line);
            }
        }
        return files;
    }

    private static String gitOutput(String workdir, String... gitArgs) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "-C", workdir);
            for (String arg : gitArgs) {
                pb.command().add(arg);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output;
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }
            if (!process.waitFor(GIT_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new NopException(ERR_CODE_REBUILD_NOT_GIT_REPO).param(ARG_PATH, workdir);
            }
            if (process.exitValue() != 0) {
                throw new NopException(ERR_CODE_REBUILD_NOT_GIT_REPO)
                        .param(ARG_PATH, workdir + ": " + output);
            }
            return output;
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new NopException(ERR_CODE_REBUILD_NOT_GIT_REPO).param(ARG_PATH, workdir).cause(e);
        }
    }

    @Override
    public io.nop.code.api.dto.GraphWikiDTO exportGraphWiki(String indexId, Integer maxCommunities,
                                                            Integer maxHubNodes) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.exportGraphWiki(indexId, maxCommunities, maxHubNodes);
    }

    @Override
    public void deleteIndex(String indexId) {
        checkWriteAccess(indexId);
        withIndexLock(indexId, () -> {
            invalidateAnalysisCache(indexId);

            if (searchEngine != null) {
                try {
                    searchEngine.removeTopic("nop-code-" + indexId);
                } catch (Exception e) {
                    LOG.warn("Failed to remove search topic for index {}", indexId, e);
                }
            }

            transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn ->
                    ormTemplate.runInSession(session -> {
                        deleteEntitiesPaged(session, NopCodeUsage.class, "indexId", indexId);
                        deleteEntitiesPaged(session, NopCodeFlowMembership.class, "indexId", indexId);
                        deleteEntitiesPaged(session, NopCodeFlow.class, "indexId", indexId);
                        deleteEntitiesPaged(session, NopCodeAnnotationUsage.class, "indexId", indexId);
                        deleteEntitiesPaged(session, NopCodeInheritance.class, "indexId", indexId);
                        deleteEntitiesPaged(session, NopCodeCall.class, "indexId", indexId);
                        deleteEntitiesPaged(session, NopCodeSymbol.class, "indexId", indexId);
                        deleteEntitiesPaged(session, NopCodeFile.class, "indexId", indexId);
                        deleteEntitiesPaged(session, NopCodeDependency.class, "indexId", indexId);
                        deleteEntitiesPaged(session, NopCodeSemanticEdge.class, "indexId", indexId);
                        deleteEntitiesPaged(session, NopCodeGraphMetric.class, "indexId", indexId);
                        incrementalAffectedFilesMap.remove(indexId);

                        daoProvider.daoFor(NopCodeIndex.class).deleteEntityById(indexId);
                        return null;
                    }));

            indexLocks.remove(indexId);
        });
    }

    private <T extends IDaoEntity> void deleteEntitiesPaged(IOrmSession session,
                                                              Class<T> entityClass,
                                                              String filterField,
                                                              String filterValue) {
        IEntityDao<T> dao = daoProvider.daoFor(entityClass);
        String entityName = entityClass.getName();
        while (true) {
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq(filterField, filterValue));
            query.setLimit(DELETE_BATCH_SIZE);
            List<T> batch = dao.findAllByQuery(query);
            if (batch.isEmpty()) break;
            dao.batchDeleteEntities(batch);
            session.flush();
            session.evictAll(entityName);
        }
    }

    /**
     * Delete all flows for an index and their flow-memberships, paged.
     * Flows are loaded by projection (id only) so memberships can be deleted by filter,
     * avoiding full-entity load when only the id is needed.
     */
    private void deleteExistingFlowsByIndex(IOrmSession session, String indexId) {
        IEntityDao<NopCodeFlow> flowDao = daoProvider.daoFor(NopCodeFlow.class);
        while (true) {
            QueryBean q = new QueryBean();
            q.addFilter(FilterBeans.eq("indexId", indexId));
            q.setLimit(DELETE_BATCH_SIZE);
            q.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("id"));
            List<Map<String, Object>> rows = flowDao.selectFieldsByQuery(q);
            if (rows.isEmpty()) break;
            for (Map<String, Object> row : rows) {
                Object id = row.get("id");
                if (id != null) {
                    deleteEntitiesPaged(session, NopCodeFlowMembership.class, "flowId", id.toString());
                }
            }
            // delete the fetched flows by their ids
            List<String> ids = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                Object id = row.get("id");
                if (id != null) ids.add(id.toString());
            }
            QueryBean delQ = new QueryBean();
            delQ.addFilter(FilterBeans.in("id", ids));
            flowDao.deleteByQuery(delQ);
            session.flush();
            session.evictAll(NopCodeFlow.class.getName());
        }
    }

    // ====== Graph Analysis ======

    @Override
    public CommunityDetectionResultDTO detectCommunities(String indexId) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.detectCommunities(indexId);
    }

    @Override
    public GraphAnalysisResultDTO getGraphAnalysis(String indexId, int topN) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.getGraphAnalysis(indexId, topN);
    }

    @Override
    public ImpactResultDTO getImpactAnalysis(String indexId, String symbolId, int depth) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.getImpactAnalysis(indexId, symbolId, depth);
    }

    @Override
    public CriticalNodeResultDTO getCriticalNodes(String indexId, int topN) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.getCriticalNodes(indexId, topN);
    }

    @Override
    public KnowledgeGapResultDTO getKnowledgeGaps(String indexId) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.getKnowledgeGaps(indexId);
    }

    @Override
    public String exportGraph(String indexId, String format, boolean communityView) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.exportGraph(indexId, format, communityView);
    }

    @Override
    public GraphDiffDTO diffGraph(String baselineIndexId, String targetIndexId) {
        checkReadAccess(baselineIndexId);
        checkReadAccess(targetIndexId);
        ensureSubServices();
        return graphService.diffGraph(baselineIndexId, targetIndexId);
    }

    @Override
    public DepGraphDTO getDeps(String indexId, String filePath, int depth) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.getDeps(indexId, filePath, depth);
    }

    @Override
    public DepGraphDTO getReverseDeps(String indexId, String filePath, int depth, int limit) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.getReverseDeps(indexId, filePath, depth, limit);
    }

    @Override
    public List<List<String>> findCycles(String indexId, int minSize) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.findCycles(indexId, minSize);
    }

    @Override
    public DepGraphDTO getDepGraph(String indexId, boolean includeExternal) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.getDepGraph(indexId, includeExternal);
    }

    // ====== Incremental Indexing ======

    @Override
    public int triggerIncrementalIndex(String indexId, String vfsPath, String manifestPath) {
        checkWriteAccess(indexId);
        validatePath(vfsPath);
        IResource rootResource = VirtualFileSystem.instance().getResource(vfsPath);
        if (rootResource.isDirectory()) {
            validateLocalPath(indexId, vfsPath);
        }
        // NOTE: no invalidation here — a no-op incremental (0 changes) must keep the analysis
        // cache and materialized metric rows; the actual-change branch invalidates at its end.
        Function<String, String> pathMapper = buildPathMapper(vfsPath);

        return withIndexLock(indexId, () -> transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn ->
                ormTemplate.runInSession(session -> {
            try {
                IFingerprintStore store = new OrmFingerprintStore(daoProvider, ormTemplate, pathMapper);
                List<FileFingerprint> previousFingerprints = store.loadFingerprints(indexId);

                IResourceLoader vfs = VirtualFileSystem.instance();
                List<IResource> currentResources = collectResourcesFromVfs(vfs, vfsPath);

                List<IResource> mappedResources = new ArrayList<>(currentResources.size());
                for (IResource res : currentResources) {
                    String mappedPath = pathMapper.apply(res.getPath());
                    mappedResources.add(new MappedPathResource(res, mappedPath));
                }

                IncrementalDetector detector = new IncrementalDetector();
                ChangeSet changes = detector.detectResourceChanges(previousFingerprints, mappedResources);

                List<String> changedFiles = changes.getAddedAndModified();
                List<String> deletedFiles = changes.getDeletedFiles();

                LOG.info("Incremental index for {}: {} changed, {} deleted, {} unchanged",
                        indexId, changedFiles.size(), deletedFiles.size(),
                        changes.getUnchangedFiles().size());

                if (changedFiles.isEmpty() && deletedFiles.isEmpty()) {
                    return 0;
                }

                // actual changes invalidate the materialized global metrics (stale until next full index)
                ensureSubServices();
                graphMetricMaterializer.deleteByIndex(session, indexId);

                // N3.1: snapshot seed symbols and 2-hop affected surface BEFORE edges are deleted
                List<String> affectedFiles = computeAffectedFiles(indexId, session,
                        changedFiles, deletedFiles);
                incrementalAffectedFilesMap.put(indexId, affectedFiles);

                // N3.1-s: snapshot the dependent call edges BEFORE deleteFileRecords destroys
                // them, so they can be re-targeted onto the re-analyzed symbols afterwards.
                Map<String, String> oldQnBySymbolId = snapshotSymbolQualifiedNames(indexId, changedFiles);
                Set<String> excludeFileIds = new HashSet<>();
                for (String path : changedFiles) {
                    excludeFileIds.add(generateFileId(indexId, path));
                }
                for (String path : deletedFiles) {
                    excludeFileIds.add(generateFileId(indexId, path));
                }
                List<CallSnapshot> dependentCalls = snapshotDependentCalls(indexId,
                        oldQnBySymbolId.keySet(), excludeFileIds);

                deleteFileRecords(indexId, deletedFiles);
                deleteFileRecords(indexId, changedFiles);

                Map<String, IResource> resourceByPath = new HashMap<>();
                for (IResource res : currentResources) {
                    resourceByPath.put(pathMapper.apply(res.getPath()), res);
                }

                // N3.1-s: two-stage structure — analyze ALL changed files first, resolve their
                // call references, and only then persist. Per-file analyze+persist interleaving
                // would lose changed→changed edges: at a file's persist time its callees' new
                // symbols are neither in memory nor in the DB (old rows already deleted).
                List<CodeFileAnalysisResult> changedResults = new ArrayList<>(changedFiles.size());
                SymbolTable reanalyzedSymbols = new SymbolTable();
                for (String changedFile : changedFiles) {
                    try {
                        String relativePath = pathMapper.apply(changedFile);
                        ICodeFileAnalyzer fileAnalyzer = registry.getAnalyzer(relativePath);
                        if (fileAnalyzer == null) continue;

                        IResource resource = resourceByPath.get(changedFile);
                        if (resource == null) {
                            LOG.warn("Resource not found for path: {}", relativePath);
                            continue;
                        }
                        String sourceCode = resource.readText();
                        CodeFileAnalysisResult fileResult = fileAnalyzer.analyze(relativePath, sourceCode);
                        if (fileResult != null) {
                            changedResults.add(fileResult);
                            for (CodeSymbol symbol : fileResult.getSymbols()) {
                                String qn = symbol.getQualifiedName();
                                if (qn != null && !qn.isEmpty()) {
                                    reanalyzedSymbols.add(symbol);
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOG.warn("Failed to re-analyze file: {}", changedFile, e);
                    }
                }

                Set<String> excludeFilePaths = new HashSet<>(changedFiles);
                excludeFilePaths.addAll(deletedFiles);
                resolveReanalyzedCalls(indexId, changedResults, reanalyzedSymbols, excludeFilePaths);

                BatchQueue<CodeFileAnalysisResult> batchQueue = new BatchQueue<>(BATCH_SIZE, batch -> {
                    for (CodeFileAnalysisResult result : batch) {
                        persistSingleFileInSession(indexId, result, session);
                    }
                    LOG.debug("Flushed batch of {} analysis results for index {}", batch.size(), indexId);
                });

                for (CodeFileAnalysisResult fileResult : changedResults) {
                    batchQueue.add(fileResult);
                }
                batchQueue.flush();

                // N3.1-s: dependent edges re-targeted onto the new symbols once they are persisted
                restoreDependentCalls(indexId, session, dependentCalls, oldQnBySymbolId, reanalyzedSymbols);

                List<FileFingerprint> newFingerprints = detector.computeResourceFingerprints(mappedResources);
                updateIndexStats(indexId);

                store.saveFingerprints(indexId, newFingerprints);

                // Actual changes invalidate at the branch end: earlier invalidation would be
                // undone by persistSingleFileInSession re-creating cache entries mid-flight.
                // Memory-only op: if the transaction rolls back this is over-invalidation
                // (harmless rebuild); missing invalidation after commit would be the harmful case.
                invalidateAnalysisCache(indexId);

                return changedFiles.size();
            } catch (IOException e) {
                throw new NopException(ERR_INCREMENTAL_FAILED).param(ARG_INDEX_ID, indexId).cause(e);
            }
        })));
    }

    // ====== File Page Query ======

    @Override
    public PageBean<CodeFileAnalysisResult> findFilesPage(String indexId, String packageName, long offset, int limit) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.findFilesPage(indexId, packageName, offset, limit);
    }

    // ====== ORM Persistence ======

    private void persistInSession(String indexId, String rootPath, ProjectAnalysisResult result,
                                  IOrmSession session) {
        NopCodeIndex indexEntity = (NopCodeIndex) session.get(
                NopCodeIndex.class.getName(), indexId);
        if (indexEntity != null) {
            indexEntity.setName(indexId);
            indexEntity.setRootPath(rootPath != null ? rootPath : "/");
            indexEntity.setFileCount(result.getFileResults().size());
            indexEntity.setSymbolCount(result.getGlobalSymbolTable().size());
            indexEntity.setStatus("COMPLETED");
            indexEntity.setLastIndexed(CoreMetrics.currentTimeMillis());
        } else {
            indexEntity = (NopCodeIndex) ormTemplate.newEntity(NopCodeIndex.class.getName());
            indexEntity.setId(indexId);
            indexEntity.setName(indexId);
            indexEntity.setRootPath(rootPath != null ? rootPath : "/");
            indexEntity.setLanguage(detectIndexLanguage(result));
            indexEntity.setFileCount(result.getFileResults().size());
            indexEntity.setSymbolCount(result.getGlobalSymbolTable().size());
            indexEntity.setStatus("COMPLETED");
            indexEntity.setLastIndexed(CoreMetrics.currentTimeMillis());
            session.save(indexEntity);
        }

        BatchQueue<CodeFileAnalysisResult> queue = new BatchQueue<>(500, batch -> {
            session.flush();
            session.evictAll(NopCodeFile.class.getName());
            session.evictAll(NopCodeSymbol.class.getName());
            LOG.debug("Flushed batch of {} file results for index {}", batch.size(), indexId);
        });

        SymbolTable globalSymbolTable = result.getGlobalSymbolTable();
        for (CodeFileAnalysisResult file : result.getFileResults()) {
            saveFileResultInSession(indexId, file, session, globalSymbolTable);
            queue.add(file);
        }
        queue.flush();

        // Persist semantic edges
        if (result.getSemanticEdges() != null && !result.getSemanticEdges().isEmpty()) {
            for (CodeSemanticEdge edge : result.getSemanticEdges()) {
                NopCodeSemanticEdge edgeEntity = (NopCodeSemanticEdge) ormTemplate.newEntity(
                        NopCodeSemanticEdge.class.getName());
                edgeEntity.setId(edge.getId());
                edgeEntity.setIndexId(indexId);
                edgeEntity.setSourceSymbolId(edge.getSourceSymbolId());
                edgeEntity.setTargetSymbolId(edge.getTargetSymbolId());
                edgeEntity.setDirected(edge.isDirected());
                edgeEntity.setRelationType(fitColumn(NopCodeSemanticEdge.class.getName(), "relationType",
                        edge.getRelationType() != null ? edge.getRelationType().name() : null));
                edgeEntity.setConfidence(edge.getConfidence() != null ? edge.getConfidence().getValue() : 0);
                edgeEntity.setConfidenceScore(edge.getConfidenceScore());
                edgeEntity.setRationale(fitColumn(NopCodeSemanticEdge.class.getName(), "rationale", edge.getRationale()));
                edgeEntity.setExtractorId(fitColumn(NopCodeSemanticEdge.class.getName(), "extractorId", edge.getExtractorId()));
                edgeEntity.setExtData(fitColumn(NopCodeSemanticEdge.class.getName(), "extData", edge.getExtData()));
                edgeEntity.setProvenance(fitColumn(NopCodeSemanticEdge.class.getName(), "provenance",
                        edge.getProvenance() != null ? edge.getProvenance().name() : null));
                session.save(edgeEntity);
            }
            LOG.info("Persisted {} semantic edges for index {}", result.getSemanticEdges().size(), indexId);
        }

        // 占位 qualified name 已在 saveFileResultInSession 构建实体时就地解析（写入即解析），
        // 不再回读 DB：全量索引在单个巨事务内先写十万级实体行，任何读回（尤其分页扫描）
        // 都会在 H2 版本链上付出数量级的代价。
        synthesizeAndPersistHeuristicEdges(indexId, result, session);
    }

    private void synthesizeAndPersistHeuristicEdges(String indexId, ProjectAnalysisResult result,
                                                      IOrmSession session) {
        if (heuristicSynthesizers.isEmpty()) return;

        SymbolTable symbolTable = result.getGlobalSymbolTable();
        CallGraph callGraph = result.buildCallGraph();
        Map<String, Set<String>> inheritanceIndex = buildInheritanceIndexFromResult(result, symbolTable);

        HeuristicContext context = new HeuristicContext(symbolTable, inheritanceIndex, callGraph, indexId);

        Set<String> existingEdgeKeys = collectExistingEdgeKeys(result, indexId);

        int totalSynthesized = 0;
        for (IHeuristicEdgeSynthesizer synthesizer : heuristicSynthesizers) {
            try {
                List<CodeMethodCall> synthesized = synthesizer.synthesize(context);
                for (CodeMethodCall call : synthesized) {
                    String edgeKey = indexId + ":" + call.getCallerId() + ":" + call.getCalleeId();
                    if (existingEdgeKeys.contains(edgeKey)) continue;

                    CodeSymbol caller = symbolTable.getById(call.getCallerId());
                    String fileId = null;
                    if (caller != null && caller.getFilePath() != null) {
                        // 与 saveFileResultInSession 的 fileEntityId 生成规则一致（确定性），
                        // 避免在巨事务内逐个符号做 PK 读
                        fileId = generateFileId(indexId, caller.getFilePath());
                    }

                    NopCodeCall callEntity = (NopCodeCall) ormTemplate.newEntity(NopCodeCall.class.getName());
                    callEntity.setId(call.getId());
                    callEntity.setIndexId(indexId);
                    callEntity.setCallerId(call.getCallerId());
                    callEntity.setCalleeId(call.getCalleeId());
                    callEntity.setFileId(fileId != null ? fileId : generateDummyFileId(indexId));
                    callEntity.setLine(-1);
                    callEntity.setColumn(0);
                    callEntity.setCallType(fitColumn(NopCodeCall.class.getName(), "callType", call.getCallType()));
                    callEntity.setContext(fitColumn(NopCodeCall.class.getName(), "context", call.getContext()));
                    callEntity.setProvenance(fitColumn(NopCodeCall.class.getName(), "provenance", EdgeProvenance.HEURISTIC.name()));
                    callEntity.setMetadata(fitColumn(NopCodeCall.class.getName(), "metadata", call.getMetadata()));
                    session.save(callEntity);

                    existingEdgeKeys.add(edgeKey);
                    totalSynthesized++;
                }
            } catch (Exception e) {
                LOG.warn("Heuristic synthesizer {} failed, skipping", synthesizer.getSynthesizerId(), e);
            }
        }
        if (totalSynthesized > 0) {
            LOG.info("Synthesized {} heuristic edges for index {}", totalSynthesized, indexId);
        }
    }

    /**
     * 全量索引路径专用：直接从内存分析结果构建 IMPLEMENTS 继承索引，
     * 等价于旧的 DB 读回版本（后者在巨事务内逐行 getEntityById，代价不可接受）。
     * 与旧行为一致：父类型无法在全局符号表中解析的继承边被跳过。
     */
    private static Map<String, Set<String>> buildInheritanceIndexFromResult(ProjectAnalysisResult result,
                                                                            SymbolTable symbolTable) {
        Map<String, Set<String>> index = new HashMap<>();
        for (CodeFileAnalysisResult file : result.getFileResults()) {
            if (file.getInheritances() == null) continue;
            for (CodeInheritance inh : file.getInheritances()) {
                if (inh.getRelationType() != CodeRelationType.IMPLEMENTS)
                    continue;
                String superRef = inh.getSuperTypeQualifiedName();
                if (superRef == null) continue;
                CodeSymbol superSymbol = symbolTable.getByQualifiedName(superRef);
                if (superSymbol == null && isLikelyResolvedId(superRef))
                    superSymbol = symbolTable.getById(superRef);
                if (superSymbol != null && superSymbol.getQualifiedName() != null) {
                    index.computeIfAbsent(superSymbol.getQualifiedName(), k -> new HashSet<>())
                            .add(inh.getSubTypeId());
                }
            }
        }
        return index;
    }

    /**
     * 全量索引路径专用：从内存分析结果收集已有调用边键，等价于旧的 DB 读回版本
     * （saveFileResultInSession 对 callerId/calleeId 为 null 的调用不落库，此处同样过滤）。
     */
    private static Set<String> collectExistingEdgeKeys(ProjectAnalysisResult result, String indexId) {
        Set<String> keys = new HashSet<>();
        for (CodeFileAnalysisResult file : result.getFileResults()) {
            if (file.getCalls() == null) continue;
            for (CodeMethodCall call : file.getCalls()) {
                if (call.getCallerId() == null || call.getCalleeId() == null)
                    continue;
                keys.add(indexId + ":" + call.getCallerId() + ":" + call.getCalleeId());
            }
        }
        return keys;
    }

    private String generateDummyFileId(String indexId) {
        return generateFileId(indexId, "__heuristic__");
    }

    private void resolveQualifiedNamesToIds(String indexId, SymbolTable symbolTable, IOrmSession session) {
        IEntityDao<NopCodeInheritance> inhDao = daoProvider.daoFor(NopCodeInheritance.class);
        long inhOffset = 0;
        while (true) {
            QueryBean inhQuery = new QueryBean();
            inhQuery.addFilter(FilterBeans.eq("indexId", indexId));
            inhQuery.setOffset(inhOffset);
            inhQuery.setLimit(BATCH_SIZE);
            List<NopCodeInheritance> inhBatch = inhDao.findAllByQuery(inhQuery);
            if (inhBatch.isEmpty()) break;
            for (NopCodeInheritance inh : inhBatch) {
                String superTypeId = inh.getSuperTypeId();
                if (superTypeId != null && !isLikelyResolvedId(superTypeId)) {
                    CodeSymbol resolved = symbolTable.getByQualifiedName(superTypeId);
                    if (resolved != null) {
                        inh.setSuperTypeId(resolved.getId());
                    }
                }
            }
            session.flush();
            session.evictAll(NopCodeInheritance.class.getName());
            if (inhBatch.size() < BATCH_SIZE) break;
            inhOffset += BATCH_SIZE;
        }

        IEntityDao<NopCodeAnnotationUsage> annotDao = daoProvider.daoFor(NopCodeAnnotationUsage.class);
        long annotOffset = 0;
        while (true) {
            QueryBean annotQuery = new QueryBean();
            annotQuery.addFilter(FilterBeans.eq("indexId", indexId));
            annotQuery.setOffset(annotOffset);
            annotQuery.setLimit(BATCH_SIZE);
            List<NopCodeAnnotationUsage> annotBatch = annotDao.findAllByQuery(annotQuery);
            if (annotBatch.isEmpty()) break;
            for (NopCodeAnnotationUsage annot : annotBatch) {
                String annotationTypeId = annot.getAnnotationTypeId();
                if (annotationTypeId != null && !isLikelyResolvedId(annotationTypeId)) {
                    CodeSymbol resolved = symbolTable.getByQualifiedName(annotationTypeId);
                    if (resolved != null) {
                        annot.setAnnotationTypeId(resolved.getId());
                    }
                }
            }
            session.flush();
            session.evictAll(NopCodeAnnotationUsage.class.getName());
            if (annotBatch.size() < BATCH_SIZE) break;
            annotOffset += BATCH_SIZE;
        }
    }

    /**
     * 全量索引路径的写入即解析：用分析期构建的全局符号表把占位类型名映射为符号 ID。
     * 分析器记录的是源码书写形式（常为简单名），因此依次尝试 全限定名 → 显式 import →
     * 同包 → 通配 import → java.lang。解析失败时保留原值（与旧读回解析的兜底行为一致）。
     */
    private static String resolveSymbolId(SymbolTable resolveTable, CodeFileAnalysisResult file,
                                          String rawName) {
        if (resolveTable == null || rawName == null || isLikelyResolvedId(rawName))
            return rawName;
        CodeSymbol resolved = resolveTable.getByQualifiedName(rawName);
        if (resolved != null)
            return resolved.getId();
        for (String candidate : qualifiedNameCandidates(file, rawName)) {
            resolved = resolveTable.getByQualifiedName(candidate);
            if (resolved != null)
                return resolved.getId();
        }
        return rawName;
    }

    private static List<String> qualifiedNameCandidates(CodeFileAnalysisResult file, String simpleName) {
        List<String> candidates = new ArrayList<>(4);
        if (file.getImports() != null) {
            String suffix = "." + simpleName;
            for (String imp : file.getImports()) {
                if (imp.endsWith(suffix)) {
                    candidates.add(imp);
                    break;
                }
            }
        }
        String packageName = file.getPackageName();
        if (packageName != null && !packageName.isEmpty())
            candidates.add(packageName + "." + simpleName);
        if (file.getImports() != null) {
            for (String imp : file.getImports()) {
                if (imp.endsWith(".*"))
                    candidates.add(imp.substring(0, imp.length() - 1) + simpleName);
            }
        }
        candidates.add("java.lang." + simpleName);
        return candidates;
    }

    private static boolean isLikelyResolvedId(String value) {
        if (value == null || value.isEmpty()) return false;
        if (value.length() == 32 || value.length() == 36) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '-') continue;
                if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private void ensureIndexEntity(String indexId, String rootPath, IOrmSession session) {
        NopCodeIndex indexEntity = (NopCodeIndex) session.get(
                NopCodeIndex.class.getName(), indexId);
        if (indexEntity == null) {
            indexEntity = (NopCodeIndex) ormTemplate.newEntity(NopCodeIndex.class.getName());
            indexEntity.setId(indexId);
            indexEntity.setName(indexId);
            indexEntity.setRootPath(rootPath != null ? rootPath : "/");
            indexEntity.setLanguage("MIXED");
            indexEntity.setStatus("INDEXING");
            indexEntity.setLastIndexed(CoreMetrics.currentTimeMillis());
            session.save(indexEntity);
        }
    }

    private void updateIndexStats(String indexId, ProjectAnalysisResult result) {
        IEntityDao<NopCodeIndex> indexDao = daoProvider.daoFor(NopCodeIndex.class);
        NopCodeIndex indexEntity = indexDao.getEntityById(indexId);
        if (indexEntity != null) {
            indexEntity.setFileCount(result.getFileResults().size());
            indexEntity.setSymbolCount(result.getGlobalSymbolTable().size());
            indexEntity.setStatus("COMPLETED");
            indexEntity.setLastIndexed(CoreMetrics.currentTimeMillis());
        }
    }

    private String detectIndexLanguage(ProjectAnalysisResult result) {
        if (result.getFileResults() == null || result.getFileResults().isEmpty()) {
            return "Java";
        }
        Set<String> languages = new HashSet<>();
        for (CodeFileAnalysisResult file : result.getFileResults()) {
            if (file.getLanguage() != null) {
                languages.add(file.getLanguage().name());
            }
        }
        if (languages.size() == 1) {
            return languages.iterator().next();
        }
        if (languages.isEmpty()) {
            return "Java";
        }
        return "MIXED";
    }

    private void persistSingleFileInSession(String indexId, CodeFileAnalysisResult result,
                                            IOrmSession session) {
        saveFileResultInSession(indexId, result, session);
        if (result.getSemanticEdges() != null && !result.getSemanticEdges().isEmpty()) {
            for (CodeSemanticEdge edge : result.getSemanticEdges()) {
                NopCodeSemanticEdge edgeEntity = (NopCodeSemanticEdge) ormTemplate.newEntity(
                        NopCodeSemanticEdge.class.getName());
                edgeEntity.setId(edge.getId());
                edgeEntity.setIndexId(indexId);
                edgeEntity.setSourceSymbolId(edge.getSourceSymbolId());
                edgeEntity.setTargetSymbolId(edge.getTargetSymbolId());
                edgeEntity.setDirected(edge.isDirected());
                edgeEntity.setRelationType(fitColumn(NopCodeSemanticEdge.class.getName(), "relationType",
                        edge.getRelationType() != null ? edge.getRelationType().name() : null));
                edgeEntity.setConfidence(edge.getConfidence() != null ? edge.getConfidence().getValue() : 0);
                edgeEntity.setConfidenceScore(edge.getConfidenceScore());
                edgeEntity.setRationale(fitColumn(NopCodeSemanticEdge.class.getName(), "rationale", edge.getRationale()));
                edgeEntity.setExtractorId(fitColumn(NopCodeSemanticEdge.class.getName(), "extractorId", edge.getExtractorId()));
                edgeEntity.setExtData(fitColumn(NopCodeSemanticEdge.class.getName(), "extData", edge.getExtData()));
                edgeEntity.setProvenance(fitColumn(NopCodeSemanticEdge.class.getName(), "provenance",
                        edge.getProvenance() != null ? edge.getProvenance().name() : null));
                session.save(edgeEntity);
            }
        }
        SymbolTable fileSymbolTable = buildSymbolTableFromResult(result);
        SymbolTable globalTable = getOrRebuildSymbolTable(indexId);
        if (globalTable != null) {
            SymbolTable mergedTable = new SymbolTable();
            for (CodeSymbol sym : globalTable.getAll()) {
                mergedTable.add(sym);
            }
            for (CodeSymbol sym : fileSymbolTable.getAll()) {
                if (sym.getQualifiedName() != null) {
                    CodeSymbol existing = mergedTable.getByQualifiedName(sym.getQualifiedName());
                    if (existing == null) {
                        mergedTable.add(sym);
                    }
                }
            }
            resolveQualifiedNamesToIds(indexId, mergedTable, session);
            cacheManager.addToSymbolTableCache(indexId, fileSymbolTable);
        } else {
            resolveQualifiedNamesToIds(indexId, fileSymbolTable, session);
            cacheManager.addToSymbolTableCache(indexId, fileSymbolTable);
        }
    }

    private SymbolTable buildSymbolTableFromResult(CodeFileAnalysisResult result) {
        SymbolTable table = new SymbolTable();
        if (result.getSymbols() != null) {
            for (CodeSymbol sym : result.getSymbols()) {
                table.add(sym);
            }
        }
        return table;
    }

    /**
     * Truncate a free-text field to the effective ORM column precision before
     * persistence. Analyzer-produced strings (call contexts, metadata JSON,
     * signatures, ...) otherwise abort the whole index transaction with a
     * data-integrity violation (sqlState 22001). The width is read from the ORM
     * model so it can never drift from the schema; columns without an explicit
     * or domain-derived precision are passed through unchanged.
     */
    private String fitColumn(String entityName, String columnCode, String value) {
        if (value == null || value.isEmpty())
            return value;
        IEntityModel entity = ormTemplate.getOrmModel().getEntityModel(entityName);
        if (entity == null)
            return value;
        for (IColumnModel column : entity.getColumns()) {
            if (columnCode.equals(column.getName())
                    || columnCode.equalsIgnoreCase(column.getCode())) {
                Integer precision = column.getPrecision();
                if (precision != null && value.length() > precision) {
                    LOG.debug("Truncate {}.{}: {} -> {} chars", entityName, columnCode,
                            value.length(), precision);
                    if (value.charAt(0) == '[' || value.charAt(0) == '{') {
                        return fitJsonToPrecision(value, precision);
                    }
                    return value.substring(0, precision);
                }
                return value;
            }
        }
        return value;
    }

    /**
     * JSON 列值超宽时缩减内容直到序列化结果可容纳，保证落库的始终是合法 JSON。
     * 硬截断会产生无法解析的残串，读路径（如 entityToFileResult 的 imports 解析）
     * 会直接抛错。缩减策略：先截短超长的字符串值/元素（保留键名与条目数），
     * 仍超宽再从尾部丢条目。
     */
    private static String fitJsonToPrecision(String json, int precision) {
        Object parsed;
        try {
            parsed = JsonTool.parse(json);
        } catch (Exception e) {
            return json.substring(0, precision);
        }
        int trimTo = Math.max(16, precision / 4);
        String out;
        if (parsed instanceof List) {
            List<Object> items = new ArrayList<>((List<?>) parsed);
            for (int i = 0; i < items.size(); i++) {
                Object item = items.get(i);
                if (item instanceof String s && s.length() > trimTo)
                    items.set(i, s.substring(0, trimTo));
            }
            while (items.size() > 1 && JsonTool.stringify(items).length() > precision) {
                items.remove(items.size() - 1);
            }
            out = JsonTool.stringify(items);
            if (out.length() > precision)
                out = "[]";
        } else if (parsed instanceof Map) {
            Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) parsed);
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (entry.getValue() instanceof String s && s.length() > trimTo)
                    entry.setValue(s.substring(0, trimTo));
            }
            while (!map.isEmpty() && JsonTool.stringify(map).length() > precision) {
                map.remove(map.keySet().iterator().next());
            }
            out = JsonTool.stringify(map);
            if (out.length() > precision)
                out = "{}";
        } else {
            out = json.substring(0, precision);
        }
        return out;
    }

    /**
     * 增量路径入口：不传解析表，占位 qualified name 由 {@link #resolveQualifiedNamesToIds}
     * 在单文件小事务内读回解析（增量时全局表尚不完整，需依赖库内已索引符号）。
     */
    private void saveFileResultInSession(String indexId, CodeFileAnalysisResult file,
                                         IOrmSession session) {
        saveFileResultInSession(indexId, file, session, null);
    }

    private void saveFileResultInSession(String indexId, CodeFileAnalysisResult file,
                                         IOrmSession session, SymbolTable resolveTable) {
        Set<String> cachedProjectFilePaths = null;
        String fileEntityId = generateFileId(indexId, file.getFilePath());

        // WP-7: purge this file's OLD symbol docs from the search engine BEFORE writing the new
        // data. A re-index (e.g. a symbol was removed) otherwise leaves ghost search results.
        // This runs before any new symbol is staged, so removeDocs targets the prior symbol IDs,
        // never the freshly written ones.
        removeStaleSymbolDocsForFile(indexId, fileEntityId);

        NopCodeFile fileEntity = (NopCodeFile) ormTemplate.newEntity(NopCodeFile.class.getName());
        fileEntity.setId(fileEntityId);
        fileEntity.setIndexId(indexId);
        fileEntity.setFilePath(fitColumn(NopCodeFile.class.getName(), "filePath", file.getFilePath()));
        fileEntity.setPackageName(fitColumn(NopCodeFile.class.getName(), "packageName", file.getPackageName()));
        fileEntity.setLanguage(fitColumn(NopCodeFile.class.getName(), "language",
                file.getLanguage() != null ? file.getLanguage().name() : null));
        fileEntity.setLineCount(file.getLineCount());
        String sourceCode = file.getSourceCode();
        if (sourceCode != null) {
            fileEntity.setFileHash(DigestHelper.sha256Hex(sourceCode.getBytes(StandardCharsets.UTF_8)));
            fileEntity.setFileSize((long) sourceCode.length());
        }
        if (sourceCode != null) {
            fileEntity.setSourceCode(fitColumn(NopCodeFile.class.getName(), "sourceCode", sourceCode));
        }
        if (file.getImports() != null && !file.getImports().isEmpty()) {
            fileEntity.setImports(fitColumn(NopCodeFile.class.getName(), "imports", JsonTool.stringify(file.getImports())));
        }
        fileEntity.setLastModified(CoreMetrics.currentTimeMillis());
        saveReplacingExisting(session, fileEntity);

        // Enrich symbols with annotation short names in extData before persisting
        enrichSymbolsWithAnnotations(file);

        String fileLanguage = file.getLanguage() != null ? file.getLanguage().name() : null;

        if (file.getSymbols() != null) {
            for (CodeSymbol sym : file.getSymbols()) {
                sym.setExtData(ExtDataHelper.setFilePath(sym.getExtData(), file.getFilePath()));
                sym.setFilePath(file.getFilePath());
                sym.setLanguage(fileLanguage);
            }
        }

        if (file.getSymbols() != null) {
            for (CodeSymbol sym : file.getSymbols()) {
                NopCodeSymbol symEntity = (NopCodeSymbol) ormTemplate.newEntity(NopCodeSymbol.class.getName());
                symEntity.setId(sym.getId());
                symEntity.setIndexId(indexId);
                symEntity.setFileId(fileEntityId);
                symEntity.setKind(fitColumn(NopCodeSymbol.class.getName(), "kind", sym.getKind() != null ? sym.getKind().name() : null));
                symEntity.setName(fitColumn(NopCodeSymbol.class.getName(), "name", sym.getName()));
                symEntity.setQualifiedName(fitColumn(NopCodeSymbol.class.getName(), "qualifiedName", sym.getQualifiedName()));
                symEntity.setAccessModifier(fitColumn(NopCodeSymbol.class.getName(), "accessModifier",
                        sym.getAccessModifier() != null ? sym.getAccessModifier().name() : null));
                symEntity.setDeprecated(sym.isDeprecated());
                symEntity.setDocumentation(fitColumn(NopCodeSymbol.class.getName(), "documentation", sym.getDocumentation()));
                symEntity.setLine(sym.getLine());
                symEntity.setColumn(sym.getColumn());
                symEntity.setEndLine(sym.getEndLine());
                symEntity.setEndColumn(sym.getEndColumn());
                symEntity.setParentId(sym.getParentId());
                symEntity.setDeclaringSymbolId(sym.getDeclaringSymbolId());
                symEntity.setSuperClassName(fitColumn(NopCodeSymbol.class.getName(), "superClassName", sym.getSuperClassName()));
                symEntity.setModifiers(sym.getModifiers());
                symEntity.setSignature(fitColumn(NopCodeSymbol.class.getName(), "signature", sym.getSignature()));
                symEntity.setReturnType(fitColumn(NopCodeSymbol.class.getName(), "returnType", sym.getReturnType()));
                symEntity.setFieldType(fitColumn(NopCodeSymbol.class.getName(), "fieldType", sym.getFieldType()));
                symEntity.setRawReturnType(fitColumn(NopCodeSymbol.class.getName(), "rawReturnType", sym.getRawReturnType()));
                symEntity.setRawFieldType(fitColumn(NopCodeSymbol.class.getName(), "rawFieldType", sym.getRawFieldType()));
                symEntity.setExtData(fitColumn(NopCodeSymbol.class.getName(), "extData", sym.getExtData()));
                symEntity.setFilePath(fitColumn(NopCodeSymbol.class.getName(), "filePath", file.getFilePath()));
                symEntity.setLanguage(fitColumn(NopCodeSymbol.class.getName(), "language", fileLanguage));
                saveReplacingExisting(session, symEntity);
            }

            if (searchEngine != null) {
                String topic = "nop-code-" + indexId;
                for (CodeSymbol sym : file.getSymbols()) {
                    SearchableDoc doc = new SearchableDoc();
                    doc.setId(sym.getId());
                    doc.setTitle(sym.getQualifiedName());
                    doc.setName(sym.getName());
                    doc.setPath(file.getFilePath());
                    StringBuilder content = new StringBuilder();
                    if (sym.getDocumentation() != null) {
                        content.append(sym.getDocumentation());
                    }
                    if (sym.getSignature() != null) {
                        if (content.length() > 0) content.append(' ');
                        content.append(sym.getSignature());
                    }
                    doc.setContent(content.toString());
                    Set<String> tagSet = new HashSet<>();
                    if (sym.getKind() != null) {
                        tagSet.add(sym.getKind().name());
                    }
                    if (file.getLanguage() != null) {
                        tagSet.add(file.getLanguage().name());
                    }
                    doc.setTagSet(tagSet);
                    doc.setAutoGenerateEmbedding(true);
                    try {
                        searchEngine.addDoc(topic, doc);
                    } catch (Exception e) {
                        LOG.warn("Failed to sync symbol {} to search engine", sym.getId(), e);
                    }
                }
            }
        }

        if (file.getCalls() != null) {
            for (CodeMethodCall call : file.getCalls()) {
                if (call.getCalleeId() == null || call.getCallerId() == null)
                    continue;

                NopCodeCall callEntity = (NopCodeCall) ormTemplate.newEntity(NopCodeCall.class.getName());
                callEntity.setId(call.getId());
                callEntity.setIndexId(indexId);
                callEntity.setCallerId(call.getCallerId());
                callEntity.setCalleeId(call.getCalleeId());
                callEntity.setFileId(fileEntityId);
                callEntity.setLine(call.getLine());
                callEntity.setColumn(call.getColumn());
                callEntity.setCallType(fitColumn(NopCodeCall.class.getName(), "callType", call.getCallType()));
                callEntity.setContext(fitColumn(NopCodeCall.class.getName(), "context", call.getContext()));
                callEntity.setProvenance(fitColumn(NopCodeCall.class.getName(), "provenance",
                        call.getProvenance() != null ? call.getProvenance().name() : null));
                callEntity.setMetadata(fitColumn(NopCodeCall.class.getName(), "metadata", call.getMetadata()));
                saveReplacingExisting(session, callEntity);
            }
        }

        if (file.getInheritances() != null) {
            for (CodeInheritance inh : file.getInheritances()) {
                NopCodeInheritance inhEntity = (NopCodeInheritance) ormTemplate.newEntity(NopCodeInheritance.class.getName());
                inhEntity.setId(inh.getId());
                inhEntity.setIndexId(indexId);
                inhEntity.setSubTypeId(fitColumn(NopCodeInheritance.class.getName(), "subTypeId", inh.getSubTypeId()));
                inhEntity.setSuperTypeId(fitColumn(NopCodeInheritance.class.getName(), "superTypeId",
                        resolveSymbolId(resolveTable, file, inh.getSuperTypeQualifiedName())));
                inhEntity.setRelationType(fitColumn(NopCodeInheritance.class.getName(), "relationType",
                        inh.getRelationType() != null ? inh.getRelationType().name() : null));
                inhEntity.setProvenance(fitColumn(NopCodeInheritance.class.getName(), "provenance",
                        inh.getProvenance() != null ? inh.getProvenance().name() : null));
                saveReplacingExisting(session, inhEntity);
            }
        }

        if (file.getAnnotationUsages() != null) {
            for (CodeAnnotationUsage annot : file.getAnnotationUsages()) {
                NopCodeAnnotationUsage annotEntity = (NopCodeAnnotationUsage) ormTemplate.newEntity(NopCodeAnnotationUsage.class.getName());
                annotEntity.setId(annot.getId());
                annotEntity.setIndexId(indexId);
                annotEntity.setAnnotationTypeId(fitColumn(NopCodeAnnotationUsage.class.getName(), "annotationTypeId",
                        resolveSymbolId(resolveTable, file, annot.getAnnotationTypeQualifiedName())));
                annotEntity.setAnnotatedSymbolId(fitColumn(NopCodeAnnotationUsage.class.getName(), "annotatedSymbolId", annot.getAnnotatedSymbolId()));
                annotEntity.setLine(annot.getLine());
                annotEntity.setColumn(annot.getColumn());
                annotEntity.setAttributes(fitColumn(NopCodeAnnotationUsage.class.getName(), "attributes", annot.getAttributes()));
                annotEntity.setProvenance(fitColumn(NopCodeAnnotationUsage.class.getName(), "provenance",
                        annot.getProvenance() != null ? annot.getProvenance().name() : null));
                saveReplacingExisting(session, annotEntity);
            }
        }

        if (file.getRoutes() != null && !file.getRoutes().isEmpty()) {
            for (CodeRouteInfo route : file.getRoutes()) {
                String routeName = route.getHttpMethod() + " " + route.getRoutePath();
                String routeId = DigestHelper.sha256Hex(
                        (indexId + ":ROUTE:" + routeName).getBytes(StandardCharsets.UTF_8)).substring(0, 36);
                NopCodeSymbol routeSymbol = (NopCodeSymbol) ormTemplate.newEntity(NopCodeSymbol.class.getName());
                routeSymbol.setId(routeId);
                routeSymbol.setIndexId(indexId);
                routeSymbol.setFileId(fileEntityId);
                routeSymbol.setKind(CodeSymbolKind.ROUTE.name());
                routeSymbol.setName(routeName);
                routeSymbol.setQualifiedName(route.getHandlerQualifiedName() != null
                        ? route.getHandlerQualifiedName() + ":" + routeName : routeName);
                routeSymbol.setFilePath(file.getFilePath());
                routeSymbol.setLanguage(fileLanguage);
                Map<String, Object> routeExt = new LinkedHashMap<>();
                routeExt.put("httpMethod", route.getHttpMethod());
                routeExt.put("routePath", route.getRoutePath());
                if (route.getHandlerSymbolId() != null) {
                    routeExt.put("handlerSymbolId", route.getHandlerSymbolId());
                }
                routeSymbol.setExtData(fitColumn(NopCodeSymbol.class.getName(), "extData",
                        JsonTool.stringify(routeExt)));
                saveReplacingExisting(session, routeSymbol);
            }
        }

        if (file.getCalls() != null) {
            for (CodeMethodCall call : file.getCalls()) {
                if (call.getCallerId() == null)
                    continue;
                String usageKind = "CALL";
                String usageId = DigestHelper.sha256Hex(
                        (indexId + ":" + usageKind + ":" + call.getCallerId() + ":" + fileEntityId + ":" + call.getLine())
                                .getBytes(StandardCharsets.UTF_8)).substring(0, 36);
                NopCodeUsage usageEntity = (NopCodeUsage) ormTemplate.newEntity(NopCodeUsage.class.getName());
                usageEntity.setId(usageId);
                usageEntity.setIndexId(indexId);
                usageEntity.setSymbolId(call.getCallerId());
                usageEntity.setFileId(fileEntityId);
                usageEntity.setKind(usageKind);
                usageEntity.setLine(call.getLine());
                usageEntity.setColumn(call.getColumn());
                usageEntity.setEnclosingSymbolId(call.getCallerId());
                saveReplacingExisting(session, usageEntity);
            }
        }

        if (file.getAnnotationUsages() != null) {
            for (CodeAnnotationUsage annot : file.getAnnotationUsages()) {
                if (annot.getAnnotatedSymbolId() == null)
                    continue;
                String usageKind = "ANNOTATES";
                String usageId = DigestHelper.sha256Hex(
                        (indexId + ":" + usageKind + ":" + annot.getAnnotatedSymbolId() + ":" + fileEntityId + ":" + annot.getLine())
                                .getBytes(StandardCharsets.UTF_8)).substring(0, 36);
                NopCodeUsage usageEntity = (NopCodeUsage) ormTemplate.newEntity(NopCodeUsage.class.getName());
                usageEntity.setId(usageId);
                usageEntity.setIndexId(indexId);
                usageEntity.setSymbolId(annot.getAnnotatedSymbolId());
                usageEntity.setFileId(fileEntityId);
                usageEntity.setKind(usageKind);
                usageEntity.setLine(annot.getLine());
                usageEntity.setColumn(annot.getColumn());
                saveReplacingExisting(session, usageEntity);
            }
        }

        if (file.getInheritances() != null) {
            for (CodeInheritance inh : file.getInheritances()) {
                if (inh.getSubTypeId() == null)
                    continue;
                String usageKind = inh.getRelationType() != null ? inh.getRelationType().name() : "EXTENDS";
                String usageId = DigestHelper.sha256Hex(
                        (indexId + ":" + usageKind + ":" + inh.getSubTypeId() + ":" + fileEntityId)
                                .getBytes(StandardCharsets.UTF_8)).substring(0, 36);
                NopCodeUsage usageEntity = (NopCodeUsage) ormTemplate.newEntity(NopCodeUsage.class.getName());
                usageEntity.setId(usageId);
                usageEntity.setIndexId(indexId);
                usageEntity.setSymbolId(inh.getSubTypeId());
                usageEntity.setFileId(fileEntityId);
                usageEntity.setKind(usageKind);
                usageEntity.setLine(0);
                usageEntity.setColumn(0);
                usageEntity.setEnclosingSymbolId(inh.getSubTypeId());
                saveReplacingExisting(session, usageEntity);
            }
        }

        if (file.getSymbols() != null && file.getFilePath() != null) {
            String fp = file.getFilePath();
            boolean isTestFile = fp.contains("Test.java")
                    || fp.contains("/test/")
                    || fp.contains("Test.kt")
                    || fp.endsWith("_test.py")
                    || fp.contains("test_") && fp.endsWith(".py")
                    || fp.endsWith(".spec.ts")
                    || fp.endsWith(".test.ts");
            if (isTestFile) {
                for (CodeSymbol sym : file.getSymbols()) {
                    if (sym.getName() == null) continue;
                    String testUsageKind = "TESTED_BY";
                    String usageId = DigestHelper.sha256Hex(
                            (indexId + ":" + testUsageKind + ":" + sym.getId() + ":" + fileEntityId)
                                    .getBytes(StandardCharsets.UTF_8)).substring(0, 36);
                    NopCodeUsage usageEntity = (NopCodeUsage) ormTemplate.newEntity(NopCodeUsage.class.getName());
                    usageEntity.setId(usageId);
                    usageEntity.setIndexId(indexId);
                    usageEntity.setSymbolId(sym.getId());
                    usageEntity.setFileId(fileEntityId);
                    usageEntity.setKind(testUsageKind);
                    usageEntity.setLine(sym.getLine());
                    usageEntity.setColumn(sym.getColumn());
                    usageEntity.setEnclosingSymbolId(sym.getId());
                    saveReplacingExisting(session, usageEntity);
                }
            }
        }

        if (file.getImports() != null && !file.getImports().isEmpty() && file.getLanguage() != null) {
            IImportResolver resolver = importResolvers.get(file.getLanguage().name());
            if (resolver != null) {
                Set<String> projectFiles = cachedProjectFilePaths != null ? cachedProjectFilePaths : getProjectFilePaths(indexId);
                if (cachedProjectFilePaths == null) {
                    cachedProjectFilePaths = projectFiles;
                }
                List<CodeFileDependency> deps = resolver.resolveImports(
                        file.getFilePath(), file.getImports(), projectFiles);
                Set<String> usedDepIds = new HashSet<>();
                for (CodeFileDependency dep : deps) {
                    String depId = DigestHelper.sha256Hex(
                            (indexId + ":" + dep.getSourceFilePath() + ":" + dep.getTargetFilePath() + ":" + dep.getImportStatement())
                                    .getBytes(StandardCharsets.UTF_8)).substring(0, 36);
                    int suffix = 1;
                    while (!usedDepIds.add(depId)) {
                        depId = DigestHelper.sha256Hex(
                                (indexId + ":" + dep.getSourceFilePath() + ":" + dep.getTargetFilePath() + ":" + dep.getImportStatement() + ":" + suffix)
                                        .getBytes(StandardCharsets.UTF_8)).substring(0, 36);
                        suffix++;
                    }
                    NopCodeDependency depEntity = (NopCodeDependency) ormTemplate.newEntity(
                            NopCodeDependency.class.getName());
                    depEntity.setId(depId);
                    depEntity.setIndexId(indexId);
                    depEntity.setSourceFilePath(dep.getSourceFilePath());
                    depEntity.setTargetFilePath(dep.getTargetFilePath());
                    depEntity.setImportStatement(dep.getImportStatement());
                    depEntity.setDependencyKeyHash(
                            DigestHelper.sha256Hex(
                                    (dep.getSourceFilePath() + "\0" + dep.getTargetFilePath() + "\0" + dep.getImportStatement())
                                            .getBytes(StandardCharsets.UTF_8)));
                    depEntity.setResolved(dep.isResolved());
                    session.save(depEntity);
                }
            }
        }
    }

    // ====== Incremental Indexing Helpers ======

    static final int RESOLVE_QUERY_CHUNK = 500;

    /**
     * N3.1-s: pure-data snapshot of one dependent call row, taken BEFORE deleteFileRecords
     * destroys it. Held as plain values so later session state changes (clears, flushes)
     * cannot touch it.
     */
    private record CallSnapshot(String id, String callerId, String calleeId, String fileId,
                                Integer line, Integer column, String callType, String context,
                                String provenance, String metadata) {
    }

    /**
     * N3.1-s: id → qualifiedName snapshot of the given files' symbols, taken BEFORE deletion,
     * so dependent call rows can be re-targeted onto the re-analyzed symbols.
     */
    private Map<String, String> snapshotSymbolQualifiedNames(String indexId, List<String> filePaths) {
        IEntityDao<NopCodeSymbol> dao = daoProvider.daoFor(NopCodeSymbol.class);
        Map<String, String> qnBySymbolId = new HashMap<>();
        for (String filePath : filePaths) {
            String fileId = generateFileId(indexId, filePath);
            long offset = 0;
            while (true) {
                QueryBean query = new QueryBean();
                query.addFilter(FilterBeans.eq("fileId", fileId));
                query.setOffset(offset);
                query.setLimit(DELETE_BATCH_SIZE);
                List<NopCodeSymbol> rows = dao.findAllByQuery(query);
                for (NopCodeSymbol row : rows) {
                    if (row.getId() != null && row.getQualifiedName() != null) {
                        qnBySymbolId.put(row.getId(), row.getQualifiedName());
                    }
                }
                if (rows.size() < DELETE_BATCH_SIZE) break;
                offset += DELETE_BATCH_SIZE;
            }
        }
        return qnBySymbolId;
    }

    /**
     * N3.1-s: full read of the call rows whose callee belongs to the changed files' symbols,
     * excluding rows of the changed/deleted files themselves (those are re-analyzed and
     * re-persisted anyway). Paginated to exhaustion — no limit truncation.
     */
    private List<CallSnapshot> snapshotDependentCalls(String indexId, Set<String> targetSymbolIds,
                                                      Set<String> excludeFileIds) {
        if (targetSymbolIds.isEmpty()) return List.of();
        IEntityDao<NopCodeCall> dao = daoProvider.daoFor(NopCodeCall.class);
        List<CallSnapshot> snapshots = new ArrayList<>();
        List<String> targets = new ArrayList<>(targetSymbolIds);
        for (int start = 0; start < targets.size(); start += RESOLVE_QUERY_CHUNK) {
            List<String> chunk = targets.subList(start, Math.min(start + RESOLVE_QUERY_CHUNK, targets.size()));
            QueryBean query = new QueryBean();
            if (excludeFileIds.isEmpty()) {
                query.addFilter(FilterBeans.in("calleeId", chunk));
            } else {
                query.addFilter(FilterBeans.and(
                        FilterBeans.in("calleeId", chunk),
                        FilterBeans.notIn("fileId", excludeFileIds)));
            }
            long offset = 0;
            while (true) {
                query.setOffset(offset);
                query.setLimit(DELETE_BATCH_SIZE);
                List<NopCodeCall> rows = dao.findAllByQuery(query);
                for (NopCodeCall row : rows) {
                    snapshots.add(new CallSnapshot(row.getId(), row.getCallerId(), row.getCalleeId(),
                            row.getFileId(), row.getLine(), row.getColumn(), row.getCallType(),
                            row.getContext(), row.getProvenance(), row.getMetadata()));
                }
                if (rows.size() < DELETE_BATCH_SIZE) break;
                offset += DELETE_BATCH_SIZE;
            }
        }
        return snapshots;
    }

    /**
     * N3.1-s: re-inserts the dependent call rows whose callee symbols were deleted along with
     * the changed files. Rows whose old qualified name still exists among the re-analyzed
     * symbols are re-targeted to the new symbol id; the rest are dropped — the callee is
     * genuinely gone and the full-index flow would not rebuild that edge either.
     */
    private int restoreDependentCalls(String indexId, IOrmSession session, List<CallSnapshot> snapshots,
                                      Map<String, String> oldQnBySymbolId, SymbolTable reanalyzedSymbols) {
        if (snapshots.isEmpty()) return 0;
        int restored = 0;
        int dropped = 0;
        for (CallSnapshot snapshot : snapshots) {
            String oldQn = oldQnBySymbolId.get(snapshot.calleeId());
            CodeSymbol newCallee = oldQn != null ? reanalyzedSymbols.getByQualifiedName(oldQn) : null;
            if (newCallee == null) {
                dropped++;
                continue;
            }
            NopCodeCall fresh = (NopCodeCall) ormTemplate.newEntity(NopCodeCall.class.getName());
            fresh.setId(snapshot.id());
            fresh.setIndexId(indexId);
            fresh.setCallerId(snapshot.callerId());
            fresh.setCalleeId(newCallee.getId());
            fresh.setFileId(snapshot.fileId());
            fresh.setLine(snapshot.line());
            fresh.setColumn(snapshot.column());
            fresh.setCallType(fitColumn(NopCodeCall.class.getName(), "callType", snapshot.callType()));
            fresh.setContext(fitColumn(NopCodeCall.class.getName(), "context", snapshot.context()));
            fresh.setProvenance(fitColumn(NopCodeCall.class.getName(), "provenance", snapshot.provenance()));
            fresh.setMetadata(fitColumn(NopCodeCall.class.getName(), "metadata", snapshot.metadata()));
            saveReplacingExisting(session, fresh);
            restored++;
        }
        LOG.info("N3.1-s restored {} dependent call edges for index {} ({} dropped: callee removed)",
                restored, indexId, dropped);
        return restored;
    }

    /**
     * N3.1-s: resolves the re-analyzed files' method-call edges before persistence, with the
     * same exact-then-fuzzy semantics as the full-index flow (shared CallReferenceResolver).
     * Lookup order: in-memory symbols of the re-analyzed files first (the new truth), then DB
     * symbols of untouched files via targeted batch queries. Rows of the changed/deleted files
     * are excluded in the query itself, so neither stale pre-delete rows nor in-transaction
     * flush visibility can resolve a reference. Unresolved calls stay INFERRED and are skipped
     * by the persist filter — the same degradation contract as the full-index flow.
     */
    private void resolveReanalyzedCalls(String indexId, List<CodeFileAnalysisResult> results,
                                        SymbolTable reanalyzedSymbols, Set<String> excludeFilePaths) {
        Set<String> unresolvedQns = new HashSet<>();
        for (CodeFileAnalysisResult result : results) {
            if (result.getCalls() == null) continue;
            for (CodeMethodCall call : result.getCalls()) {
                if (call.getCalleeId() != null) continue;
                String qn = call.getCalleeQualifiedName();
                if (qn != null && !qn.isEmpty()) {
                    unresolvedQns.add(qn);
                }
            }
        }

        Map<String, CodeSymbol> dbSymbols = unresolvedQns.isEmpty() ? Collections.emptyMap()
                : querySymbolsByQualifiedNames(indexId, unresolvedQns, excludeFilePaths);

        int resolved = 0;
        for (CodeFileAnalysisResult result : results) {
            if (result.getCalls() == null) continue;
            for (CodeMethodCall call : result.getCalls()) {
                boolean ok = CallReferenceResolver.resolveCall(call, qn -> {
                    CodeSymbol symbol = reanalyzedSymbols.getByQualifiedName(qn);
                    return symbol != null ? symbol : dbSymbols.get(qn);
                });
                if (ok) {
                    resolved++;
                }
            }
        }
        LOG.info("N3.1-s resolved {} call references for index {} ({} db lookup names)",
                resolved, indexId, dbSymbols.size());
    }

    /**
     * Batched qualifiedName → symbol lookup over nop_code_symbol, restricted to untouched
     * files. Overload-sharing qualified names resolve deterministically to the smallest
     * (kind, id) row — the full-index flow's HashMap last-write-wins is arbitrary for
     * overloads; edge count is identical either way.
     */
    private Map<String, CodeSymbol> querySymbolsByQualifiedNames(String indexId, Set<String> qualifiedNames,
                                                                 Set<String> excludeFilePaths) {
        IEntityDao<NopCodeSymbol> dao = daoProvider.daoFor(NopCodeSymbol.class);
        Map<String, CodeSymbol> result = new HashMap<>();
        List<String> qnList = new ArrayList<>(qualifiedNames);
        for (int start = 0; start < qnList.size(); start += RESOLVE_QUERY_CHUNK) {
            List<String> chunk = qnList.subList(start, Math.min(start + RESOLVE_QUERY_CHUNK, qnList.size()));
            QueryBean query = new QueryBean();
            if (excludeFilePaths.isEmpty()) {
                query.addFilter(FilterBeans.and(
                        FilterBeans.eq("indexId", indexId),
                        FilterBeans.in("qualifiedName", chunk)));
            } else {
                query.addFilter(FilterBeans.and(
                        FilterBeans.eq("indexId", indexId),
                        FilterBeans.in("qualifiedName", chunk),
                        FilterBeans.notIn("filePath", excludeFilePaths)));
            }
            long offset = 0;
            while (true) {
                query.setOffset(offset);
                query.setLimit(DELETE_BATCH_SIZE);
                List<NopCodeSymbol> rows = dao.findAllByQuery(query);
                for (NopCodeSymbol row : rows) {
                    mergeSymbolDeterministic(result, row);
                }
                if (rows.size() < DELETE_BATCH_SIZE) break;
                offset += DELETE_BATCH_SIZE;
            }
        }
        return result;
    }

    private static void mergeSymbolDeterministic(Map<String, CodeSymbol> map, NopCodeSymbol row) {
        String qn = row.getQualifiedName();
        if (qn == null) return;
        CodeSymbol candidate = CodeSymbolConverter.toCodeSymbol(row);
        CodeSymbol existing = map.get(qn);
        if (existing == null || compareSymbolsForTieBreak(candidate, existing) < 0) {
            map.put(qn, candidate);
        }
    }

    private static int compareSymbolsForTieBreak(CodeSymbol a, CodeSymbol b) {
        String kindA = a.getKind() != null ? a.getKind().name() : "";
        String kindB = b.getKind() != null ? b.getKind().name() : "";
        int byKind = kindA.compareTo(kindB);
        if (byKind != 0) return byKind;
        String idA = a.getId() != null ? a.getId() : "";
        String idB = b.getId() != null ? b.getId() : "";
        return idA.compareTo(idB);
    }

    private Set<String> getProjectFilePaths(String indexId) {
        if (daoProvider == null) return Collections.emptySet();
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq("indexId", indexId));
        q.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("filePath"));
        Set<String> paths = new HashSet<>();
        long offset = 0;
        while (true) {
            q.setOffset(offset);
            q.setLimit(BATCH_SIZE);
            List<Map<String, Object>> rows = fileDao.selectFieldsByQuery(q);
            for (Map<String, Object> row : rows) {
                Object path = row.get("filePath");
                if (path != null) {
                    paths.add(path.toString());
                }
            }
            if (rows.size() < BATCH_SIZE) break;
            offset += BATCH_SIZE;
        }
        return paths;
    }

    private List<IResource> collectResourcesFromVfs(IResourceLoader resourceLoader, String vfsPath) {
        Set<String> allExtensions = new LinkedHashSet<>();
        for (CodeLanguage lang : registry.getSupportedLanguages()) {
            ILanguageAdapter adapter = registry.getAdapter(lang);
            allExtensions.addAll(adapter.getFileExtensions());
        }

        List<IResource> result = new ArrayList<>();
        IterableIterator<IResource> it = resourceLoader.depthIterator(vfsPath, false, resource -> {
            if (resource.isDirectory()) return true;
            for (String ext : allExtensions) {
                if (resource.getName().endsWith(ext)) return true;
            }
            return false;
        });

        while (it.hasNext()) {
            IResource res = it.next();
            if (!res.isDirectory()) {
                result.add(res);
            }
        }
        return result;
    }

    static final int PROPAGATION_HOPS = 2;
    private static final int PROPAGATION_QUERY_LIMIT = 10000;

    /**
     * N3.1: snapshots seed symbols of the changed/deleted files and propagates 2 hops along
     * call edges (bidirectional) BEFORE any edge deletion. Returns deduplicated file paths of
     * affected symbols, excluding the changed/deleted files themselves. Over-limit hop results
     * throw (no silent truncation).
     */
    List<String> computeAffectedFiles(String indexId, IOrmSession session,
                                      List<String> changedFiles, List<String> deletedFiles) {
        Set<String> seedFiles = new HashSet<>();
        seedFiles.addAll(changedFiles);
        seedFiles.addAll(deletedFiles);

        Set<String> seeds = new HashSet<>();
        for (String filePath : seedFiles) {
            seeds.addAll(findSymbolIdsByFileId(generateFileId(indexId, filePath)));
        }
        if (seeds.isEmpty()) {
                return new ArrayList<>();
        }

        // File-level propagation over nop_code_dependency (import graph): a changed file
        // affects files that import it (reverse deps), 2 hops out. Symbol-level call edges
        // are unsuitable here because cross-file calleeId is only resolved by the full-index
        // flow (Deferred successor: incremental callee resolution).
        Set<String> affectedFiles = DependencyPropagator.propagate(seedFiles,
                ids -> {
                    Set<String> neighbours = new java.util.LinkedHashSet<>();
                    for (String id : ids) {
                        for (NopCodeDependency dep : queryDependencies(indexId, "targetFilePath", id)) {
                            if (dep.getSourceFilePath() != null) neighbours.add(dep.getSourceFilePath());
                        }
                        for (NopCodeDependency dep : queryDependencies(indexId, "sourceFilePath", id)) {
                            if (dep.getTargetFilePath() != null) neighbours.add(dep.getTargetFilePath());
                        }
                    }
                    return neighbours;
                }, PROPAGATION_HOPS);

        affectedFiles.removeAll(seedFiles);
        return new ArrayList<>(affectedFiles);
    }

    private List<NopCodeDependency> queryDependencies(String indexId, String filterField, String filePath) {
        IEntityDao<NopCodeDependency> dao = daoProvider.daoFor(NopCodeDependency.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.and(
                FilterBeans.eq("indexId", indexId),
                FilterBeans.eq(filterField, filePath)));
        query.setLimit(PROPAGATION_QUERY_LIMIT + 1);
        List<NopCodeDependency> rows = dao.findPageByQuery(query);
        if (rows.size() > PROPAGATION_QUERY_LIMIT) {
            throw new NopException(ERR_INCREMENTAL_FAILED)
                    .param(ARG_INDEX_ID, indexId)
                    .param("depField", filterField + "=" + filePath);
        }
        return rows;
    }

    private List<NopCodeCall> queryCalls(String indexId, String filterField, String symbolId) {
        IEntityDao<NopCodeCall> dao = daoProvider.daoFor(NopCodeCall.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.and(
                FilterBeans.eq("indexId", indexId),
                FilterBeans.eq(filterField, symbolId)));
        query.setLimit(PROPAGATION_QUERY_LIMIT + 1);
        List<NopCodeCall> rows = dao.findPageByQuery(query);
        if (rows.size() > PROPAGATION_QUERY_LIMIT) {
            throw new NopException(ERR_INCREMENTAL_FAILED)
                    .param(ARG_INDEX_ID, indexId)
                    .param("hop", filterField + "=" + symbolId);
        }
        return rows;
    }

    private void deleteFileRecords(String indexId, List<String> filePaths) {
        if (daoProvider == null || filePaths.isEmpty()) return;

        String topic = "nop-code-" + indexId;
        for (String filePath : filePaths) {
            String fileId = generateFileId(indexId, filePath);

            List<String> symbolIds = findSymbolIdsByFileId(fileId);

            if (searchEngine != null && !symbolIds.isEmpty()) {
                try {
                    searchEngine.removeDocs(topic, symbolIds);
                } catch (Exception e) {
                    LOG.warn("Failed to remove search docs for file {}", filePath, e);
                }
            }

            deleteEntitiesByFilter(NopCodeCall.class, "fileId", fileId);
            deleteEntitiesByFilter(NopCodeSymbol.class, "fileId", fileId);
            deleteEntitiesByFilter(NopCodeUsage.class, "fileId", fileId);
            deleteEntitiesByFilter(NopCodeDependency.class, "sourceFilePath", filePath);
            deleteRelationalBySymbolIds(NopCodeAnnotationUsage.class, "annotatedSymbolId", symbolIds);
            deleteRelationalBySymbolIds(NopCodeInheritance.class, "subTypeId", symbolIds);
            deleteRelationalBySymbolIds(NopCodeSemanticEdge.class, "sourceSymbolId", symbolIds);
            deleteRelationalBySymbolIds(NopCodeSemanticEdge.class, "targetSymbolId", symbolIds);
            deleteRelationalBySymbolIds(NopCodeFlowMembership.class, "symbolId", symbolIds);
            // WP-4 AR-30/66/149/150: clean CROSS-FILE references to this file's symbols so no
            // dangling rows survive in OTHER files (calls in file B calling A's symbols, file B
            // classes inheriting A's symbols, file B usages referencing A's symbols). The ORM
            // cascadeDelete on NopCodeSymbol.callers/callees/superTypes/subTypes/usages only fires
            // through ORM-relation navigation; this bulk path bypasses it, so mirror it explicitly.
            // This is the service-layer degradation for AR-149/150 (usages cascadeDelete gap).
            deleteRelationalBySymbolIds(NopCodeCall.class, "calleeId", symbolIds);
            deleteRelationalBySymbolIds(NopCodeCall.class, "callerId", symbolIds);
            deleteRelationalBySymbolIds(NopCodeInheritance.class, "superTypeId", symbolIds);
            deleteRelationalBySymbolIds(NopCodeUsage.class, "symbolId", symbolIds);

            IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
            QueryBean q = new QueryBean();
            q.addFilter(FilterBeans.eq("indexId", indexId));
            q.addFilter(FilterBeans.eq("id", fileId));
            q.setLimit(1);
            fileDao.batchDeleteEntities(fileDao.findAllByQuery(q));
            // Flush the file delete so a following re-save (retry) inserts into an empty slot
            // instead of conflicting with the staged-but-unflushed delete.
            fileDao.flushSession();
        }
    }

    private void saveReplacingExisting(IOrmSession session, IOrmEntity entity) {
        // Query-first upsert so indexDirectory/indexFile are retry-safe. A retry re-saves entities
        // whose deterministic IDs already exist in the DB; a plain session.save() stages an INSERT
        // whose duplicate-key (UK_NOP_CODE_FILE_PATH) only surfaces at the deferred batch flush in
        // persistInSession — outside any try/catch, so it cannot be caught there. By loading the
        // existing row first the ORM tracks it as MANAGED; copying the new field values turns the
        // flush into an UPDATE instead of a constraint-violating INSERT.
        IOrmEntity existing = (IOrmEntity) session.get(entity.orm_entityName(), entity.orm_id());
        if (existing != null) {
            Map<String, Object> initedValues = entity.orm_initedValues();
            for (Map.Entry<String, Object> entry : initedValues.entrySet()) {
                String propName = entry.getKey();
                int propId = existing.orm_propId(propName);
                if (propId >= 0 && !existing.orm_isPrimary(propId)) {
                    try {
                        existing.orm_propValue(propId, entry.getValue());
                    } catch (Exception ex) {
                        LOG.trace("Skipping prop {} during entity update", propName, ex);
                    }
                }
            }
            return;
        }
        session.save(entity);
    }

    private List<String> findSymbolIdsByFileId(String fileId) {
        IEntityDao<NopCodeSymbol> dao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq("fileId", fileId));
        q.setLimit(MAX_QUERY_RESULTS);
        return dao.findAllByQuery(q).stream()
                .map(NopCodeSymbol::getId)
                .collect(Collectors.toList());
    }

    private void removeStaleSymbolDocsForFile(String indexId, String fileId) {
        if (searchEngine == null) return;
        List<String> oldSymbolIds = findSymbolIdsByFileId(fileId);
        if (oldSymbolIds.isEmpty()) return;
        String topic = "nop-code-" + indexId;
        try {
            searchEngine.removeDocs(topic, oldSymbolIds);
        } catch (Exception e) {
            LOG.warn("Failed to remove stale search docs for file id {}", fileId, e);
        }
    }

    private <T extends IDaoEntity> void deleteRelationalBySymbolIds(Class<T> entityClass, String field, List<String> symbolIds) {
        if (symbolIds.isEmpty()) return;
        IEntityDao<T> dao = daoProvider.daoFor(entityClass);
        // Paginated delete to exhaust all matching rows
        while (true) {
            QueryBean q = new QueryBean();
            q.addFilter(FilterBeans.in(field, symbolIds));
            q.setLimit(DELETE_BATCH_SIZE);
            List<T> entities = dao.findAllByQuery(q);
            if (entities.isEmpty()) break;
            dao.batchDeleteEntities(entities);
            // Flush staged deletes before the next query, otherwise the re-query keeps finding
            // the not-yet-flushed rows and the loop never terminates (matches deleteEntitiesPaged).
            dao.flushSession();
        }
    }

    private <T extends IDaoEntity> void deleteEntitiesByFilter(Class<T> entityClass, String field, String value) {
        IEntityDao<T> dao = daoProvider.daoFor(entityClass);
        // Paginated delete to exhaust all matching rows
        while (true) {
            QueryBean q = new QueryBean();
            q.addFilter(FilterBeans.eq(field, value));
            q.setLimit(DELETE_BATCH_SIZE);
            List<T> entities = dao.findAllByQuery(q);
            if (entities.isEmpty()) break;
            dao.batchDeleteEntities(entities);
            // Flush staged deletes before the next query, otherwise the re-query keeps finding
            // the not-yet-flushed rows and the loop never terminates (matches deleteEntitiesPaged).
            dao.flushSession();
        }
    }

    private Function<String, String> buildPathMapper(String vfsPath) {
        String normalizedPrefix = vfsPath;
        if (!normalizedPrefix.endsWith("/")) {
            normalizedPrefix = normalizedPrefix + "/";
        }
        // Strip VFS prefix: "file:/abs/dir/" + "file:/abs/dir/com/Foo.java" → "com/Foo.java"
        String prefix = normalizedPrefix;
        return path -> {
            if (path.startsWith(prefix)) {
                return path.substring(prefix.length());
            }
            return path;
        };
    }

    private void updateIndexStats(String indexId) {
        if (daoProvider == null) return;
        try {
            IEntityDao<NopCodeIndex> indexDao = daoProvider.daoFor(NopCodeIndex.class);
            NopCodeIndex index = indexDao.getEntityById(indexId);
            if (index != null) {
                IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
                QueryBean fq = new QueryBean();
                fq.addFilter(FilterBeans.eq("indexId", indexId));
                index.setFileCount((int) fileDao.countByQuery(fq));

                IEntityDao<NopCodeSymbol> symDao = daoProvider.daoFor(NopCodeSymbol.class);
                QueryBean sq = new QueryBean();
                sq.addFilter(FilterBeans.eq("indexId", indexId));
                index.setSymbolCount((int) symDao.countByQuery(sq));
                index.setLastIndexed(CoreMetrics.currentTimeMillis());
            }
        } catch (Exception e) {
            LOG.warn("Failed to update index stats for {}", indexId, e);
        }
    }

    // ====== Flow Analysis ======

    @Override
    public List<ExecutionFlow> detectFlows(String indexId) {
        checkReadAccess(indexId);
        if (daoProvider == null) return Collections.emptyList();

        SymbolTable symbolTable = getOrRebuildSymbolTable(indexId);
        CallGraph callGraph = getOrRebuildCallGraph(indexId);

        IFlowDetector detector = flowDetector;
        if (detector == null) {
            throw new NopException(ERR_CODE_FLOW_DETECTOR_NOT_AVAILABLE).param(ARG_INDEX_ID, indexId);
        }

        List<ExecutionFlow> flows = detector.detectFlows(indexId, symbolTable, callGraph);

        persistFlows(indexId, flows);

        return flows;
    }

    @Override
    public List<ExecutionFlow> listFlows(String indexId) {
        checkReadAccess(indexId);
        if (daoProvider == null) return Collections.emptyList();

        IEntityDao<NopCodeFlow> flowDao = daoProvider.daoFor(NopCodeFlow.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.setLimit(MAX_QUERY_RESULTS);
        List<NopCodeFlow> entities = flowDao.findAllByQuery(query);

        return entities.stream()
                .map(this::entityToExecutionFlow)
                .collect(Collectors.toList());
    }

    @Override
    public ExecutionFlow getFlow(String indexId, String flowId) {
        checkReadAccess(indexId);
        if (daoProvider == null) return null;

        IEntityDao<NopCodeFlow> flowDao = daoProvider.daoFor(NopCodeFlow.class);
        NopCodeFlow flowEntity = flowDao.getEntityById(flowId);
        if (flowEntity == null || !indexId.equals(flowEntity.getIndexId())) {
            return null;
        }

        ExecutionFlow flow = entityToExecutionFlow(flowEntity);

        IEntityDao<NopCodeFlowMembership> membershipDao = daoProvider.daoFor(NopCodeFlowMembership.class);
        QueryBean membershipQuery = new QueryBean();
        membershipQuery.addFilter(FilterBeans.eq("flowId", flowId));
        membershipQuery.setLimit(MAX_QUERY_RESULTS);
        membershipQuery.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("symbolId"));
        List<Map<String, Object>> membershipRows = membershipDao.selectFieldsByQuery(membershipQuery);
        flow.setPathNodeIds(membershipRows.stream()
                .map(row -> row.get("symbolId"))
                .filter(sid -> sid != null)
                .map(Object::toString)
                .collect(Collectors.toList()));

        return flow;
    }

    @Override
    public List<ExecutionFlow> getAffectedFlows(String indexId, List<String> changedFilePaths) {
        checkReadAccess(indexId);
        if (daoProvider == null) return Collections.emptyList();

        IFlowDetector detector = flowDetector;
        if (detector == null) {
            throw new NopException(ERR_CODE_FLOW_DETECTOR_NOT_AVAILABLE).param(ARG_INDEX_ID, indexId);
        }

        return detector.getAffectedFlows(indexId, changedFilePaths);
    }

    @Override
    public ChangeAnalysisResult analyzeChanges(String indexId, String baselineCommitish, String targetCommitish) {
        checkReadAccess(indexId);
        if (daoProvider == null) return null;

        SymbolTable symbolTable = getOrRebuildSymbolTable(indexId);
        CallGraph callGraph = getOrRebuildCallGraph(indexId);

        IChangeAnalyzer analyzer = changeAnalyzer;
        if (analyzer == null) {
            throw new NopException(ERR_CODE_CHANGE_ANALYZER_NOT_AVAILABLE).param(ARG_INDEX_ID, indexId);
        }

        String workingDirectory = null;
        IEntityDao<NopCodeIndex> indexDao = daoProvider.daoFor(NopCodeIndex.class);
        NopCodeIndex indexEntity = indexDao.getEntityById(indexId);
        if (indexEntity != null) {
            workingDirectory = indexEntity.getRootPath();
        }

        return analyzer.analyzeChanges(indexId, baselineCommitish, targetCommitish, symbolTable, callGraph, workingDirectory);
    }

    @Override
    public DeadCodeReport detectDeadCode(String indexId) {
        checkReadAccess(indexId);
        if (daoProvider == null) return null;

        SymbolTable symbolTable = getOrRebuildSymbolTable(indexId);
        CallGraph callGraph = getOrRebuildCallGraph(indexId);

        IDeadCodeDetector detector = deadCodeDetector;
        if (detector == null) {
            throw new NopException(ERR_CODE_DEAD_CODE_DETECTOR_NOT_AVAILABLE).param(ARG_INDEX_ID, indexId);
        }

        return detector.detectDeadCode(indexId, symbolTable, callGraph);
    }

    private static final int MAX_FLOWS_PER_INDEX = 5000;

    private void persistFlows(String indexId, List<ExecutionFlow> flows) {
        List<ExecutionFlow> limitedFlows = flows;
        if (flows.size() > MAX_FLOWS_PER_INDEX) {
            LOG.warn("Truncating flows from {} to {} for index {}",
                    flows.size(), MAX_FLOWS_PER_INDEX, indexId);
            limitedFlows = flows.subList(0, MAX_FLOWS_PER_INDEX);
        }
        List<ExecutionFlow> finalFlows = limitedFlows;
        transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn ->
                ormTemplate.runInSession(session -> {
            // Paged delete of existing flows + their memberships via filter-based helpers
            // (deleteEntitiesPaged loads+deletes in pages; avoid entity-field-min by not
            //  accessing getters on the result list)
            deleteExistingFlowsByIndex(session, indexId);

            for (ExecutionFlow flow : finalFlows) {
                NopCodeFlow flowEntity = (NopCodeFlow) ormTemplate.newEntity(NopCodeFlow.class.getName());
                flowEntity.setId(flow.getId());
                flowEntity.setIndexId(indexId);
                flowEntity.setName(flow.getName());
                flowEntity.setEntryPointId(flow.getEntryPointSymbolId());
                flowEntity.setEntryPointQualifiedName(flow.getEntryPointQualifiedName());
                flowEntity.setDepth(flow.getDepth());
                flowEntity.setOverallScore(flow.getCriticality());
                flowEntity.setStatus("DETECTED");
                flowEntity.setCreateTime(CoreMetrics.currentTimestamp());
                session.save(flowEntity);

                if (flow.getPathNodeIds() != null) {
                    int depth = 0;
                    for (String nodeId : flow.getPathNodeIds()) {
                        NopCodeFlowMembership membership = (NopCodeFlowMembership) ormTemplate.newEntity(
                                NopCodeFlowMembership.class.getName());
                        membership.setId(flow.getId() + "_" + nodeId);
                        membership.setFlowId(flow.getId());
                        membership.setSymbolId(nodeId);
                        membership.setIndexId(indexId);
                        membership.setDepth(depth++);
                        membership.setIsEntry(nodeId.equals(flow.getEntryPointSymbolId()));
                        membership.setCreateTime(CoreMetrics.currentTimestamp());
                        session.save(membership);
                    }
                }
            }
            return null;
        }));
    }

    private ExecutionFlow entityToExecutionFlow(NopCodeFlow entity) {
        ExecutionFlow flow = new ExecutionFlow();
        flow.setId(entity.getId());
        flow.setName(entity.getName());
        flow.setIndexId(entity.getIndexId());
        // Field mapping: NopCodeFlow.entryPointId <-> ExecutionFlow.entryPointSymbolId
        flow.setEntryPointSymbolId(entity.getEntryPointId());
        flow.setEntryPointQualifiedName(entity.getEntryPointQualifiedName());
        flow.setDepth(entity.getDepth() != null ? entity.getDepth() : 0);
        // Field mapping: NopCodeFlow.overallScore <-> ExecutionFlow.criticality
        flow.setCriticality(entity.getOverallScore() != null ? entity.getOverallScore() : 0.0);
        return flow;
    }

    @Override
    public List<CodeSymbol> findByAnnotation(String indexId, String annotationName) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.findByAnnotation(indexId, annotationName);
    }

    @Override
    public List<CodeSymbol> findImplementations(String indexId, String qualifiedName, boolean directOnly, int maxDepth) {
        checkReadAccess(indexId);
        ensureSubServices();
        return queryService.findImplementations(indexId, qualifiedName, directOnly, maxDepth);
    }

    @Override
    public List<String> findDependentFiles(String indexId, String filePath) {
        checkReadAccess(indexId);
        ensureSubServices();
        return graphService.findDependentFiles(indexId, filePath);
    }

    // ====== Batch File Records ======

    @Override
    public void batchSaveFileRecords(String indexId, List<FileFingerprint> fingerprints) {
        checkWriteAccess(indexId);
        if (daoProvider == null || fingerprints == null || fingerprints.isEmpty()) return;

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);

        for (FileFingerprint fp : fingerprints) {
            String fileId = generateFileId(indexId, fp.getFilePath());

            // Try to find existing
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq("indexId", indexId));
            query.addFilter(FilterBeans.eq("filePath", fp.getFilePath()));
            query.setLimit(1);
            List<NopCodeFile> existing = fileDao.findAllByQuery(query);

            NopCodeFile fileEntity;
            if (!existing.isEmpty()) {
                fileEntity = existing.get(0);
            } else {
                fileEntity = (NopCodeFile) ormTemplate.newEntity(NopCodeFile.class.getName());
                fileEntity.setId(fileId);
                fileEntity.setIndexId(indexId);
                fileEntity.setFilePath(fp.getFilePath());
            }

            fileEntity.setFileHash(fp.getContentHash());
            fileEntity.setLastModified(fp.getLastModified());
            fileEntity.setFileSize(fp.getFileSize());

            if (existing.isEmpty()) {
                fileDao.saveEntity(fileEntity);
            }
        }
    }

    @Override
    public List<FileFingerprint> batchLoadFileRecords(String indexId) {
        checkReadAccess(indexId);
        if (daoProvider == null) return new ArrayList<>();

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("filePath"));
        query.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("fileHash"));
        query.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("lastModified"));
        query.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("fileSize"));

        // Projection (avoid CLOB sourceCode load) + paginated to exhaust all rows
        List<FileFingerprint> fingerprints = new ArrayList<>();
        long offset = 0;
        while (true) {
            query.setOffset(offset);
            query.setLimit(BATCH_SIZE);
            List<Map<String, Object>> rows = fileDao.selectFieldsByQuery(query);
            for (Map<String, Object> row : rows) {
                FileFingerprint fp = new FileFingerprint();
                Object path = row.get("filePath");
                fp.setFilePath(path != null ? path.toString() : null);
                Object hash = row.get("fileHash");
                fp.setContentHash(hash != null ? hash.toString() : null);
                Object lm = row.get("lastModified");
                fp.setLastModified(lm != null ? ((Number) lm).longValue() : 0L);
                Object sz = row.get("fileSize");
                fp.setFileSize(sz != null ? ((Number) sz).longValue() : 0L);
                fingerprints.add(fp);
            }
            if (rows.size() < BATCH_SIZE) break;
            offset += BATCH_SIZE;
        }

        return fingerprints;
    }

    @Override
    public void batchDeleteFileRecords(String indexId, List<String> filePaths) {
        checkWriteAccess(indexId);
        // Wrap in a transaction+session (under the per-index lock) so the paged load/delete inside
        // deleteFileRecords shares one ORM session — entities loaded by findAllByQuery must belong
        // to the session that later deletes them. The other deleteFileRecords callers already wrap
        // in runInSession; this public entry point must too, otherwise it is unsafe standalone.
        withIndexLock(indexId, () -> {
            invalidateAnalysisCache(indexId);
            transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn ->
                    ormTemplate.runInSession(session -> {
                        // Deleted files change content: materialized global metrics are stale
                        ensureSubServices();
                        graphMetricMaterializer.deleteByIndex(session, indexId);
                        deleteFileRecords(indexId, filePaths);
                        return null;
                    }));
        });
    }

    private String generateFileId(String indexId, String filePath) {
        return DigestHelper.sha256Hex((indexId + ":" + filePath).getBytes(StandardCharsets.UTF_8)).substring(0, 36);
    }

    /**
     * Enrich each symbol's extData with annotation short names derived from the file's annotationUsages.
     * This makes annotations available for in-memory checks (e.g. dead code detection) without
     * requiring a separate DB query.
     */
    private void enrichSymbolsWithAnnotations(CodeFileAnalysisResult file) {
        if (file.getAnnotationUsages() == null || file.getSymbols() == null) return;

        Map<String, List<String>> symbolAnnotations = new HashMap<>();
        for (CodeAnnotationUsage usage : file.getAnnotationUsages()) {
            if (usage.getAnnotatedSymbolId() != null && usage.getAnnotationTypeQualifiedName() != null) {
                String shortName = usage.getAnnotationTypeQualifiedName();
                int dotIdx = shortName.lastIndexOf('.');
                if (dotIdx >= 0) {
                    shortName = shortName.substring(dotIdx + 1);
                }
                symbolAnnotations.computeIfAbsent(usage.getAnnotatedSymbolId(), k -> new ArrayList<>())
                        .add(shortName);
            }
        }

        for (CodeSymbol sym : file.getSymbols()) {
            List<String> annots = symbolAnnotations.get(sym.getId());
            if (annots != null && !annots.isEmpty()) {
                sym.setExtData(ExtDataHelper.setAnnotations(sym.getExtData(), annots));
            }
        }
    }


    private List<CodeSearchResultDTO> filterByLanguage(List<CodeSearchResultDTO> results,
                                                         String indexId, String language,
                                                         Map<String, String> filePathCache) {
        if (language == null || language.isEmpty()) return results;
        // Build reverse cache: filePath -> fileId, then filter by file language
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean fq = new QueryBean();
        fq.addFilter(FilterBeans.eq("indexId", indexId));
        fq.addFilter(FilterBeans.eq("language", language));
        fq.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("filePath"));
        Set<String> matchingPaths = new HashSet<>();
        long offset = 0;
        while (true) {
            fq.setOffset(offset);
            fq.setLimit(BATCH_SIZE);
            List<Map<String, Object>> rows = fileDao.selectFieldsByQuery(fq);
            for (Map<String, Object> row : rows) {
                Object path = row.get("filePath");
                if (path != null) matchingPaths.add(path.toString());
            }
            if (rows.size() < BATCH_SIZE) break;
            offset += BATCH_SIZE;
        }
        if (matchingPaths.isEmpty()) return Collections.emptyList();
        // WP-5 AR-42: do not mutate the caller's list — filter a defensive copy.
        List<CodeSearchResultDTO> filtered = new ArrayList<>(results);
        filtered.removeIf(dto -> !matchingPaths.contains(dto.getFilePath()));
        return filtered;
    }

    private String extractLines(String source, int startLine, int endLine) {
        if (source == null || startLine < 1 || endLine < startLine) return null;
        String[] lines = source.split("\n", -1);
        int start = Math.max(1, startLine) - 1; // 0-based
        int end = Math.min(lines.length, endLine); // exclusive
        if (start >= end) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) sb.append("\n");
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private String allowedLocalRoot;

    public void setAllowedLocalRoot(String allowedLocalRoot) {
        this.allowedLocalRoot = allowedLocalRoot;
        if (allowedLocalRoot == null || allowedLocalRoot.isEmpty()) {
            LOG.warn("allowedLocalRoot is not configured; path validation will only check for '..' patterns. "
                    + "Configure allowedLocalRoot to restrict indexing to specific directories.");
        }
    }

    private void validatePath(String path) {
        if (path == null || path.isEmpty())
            return;
        if (path.contains(".."))
            throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
    }

    private void validateLocalPath(String indexId, String path) {
        String perIndexRoot = accessPolicy.getAllowedLocalRoot(indexId);
        validateLocalPathAgainstRoot(perIndexRoot != null && !perIndexRoot.isEmpty() ? perIndexRoot : allowedLocalRoot,
                path);
    }

    private void validateLocalPathAgainstRoot(String localRoot, String path) {
        if (path == null || path.isEmpty())
            return;
        // N2.4 quirk: VFS paths arrive in file: URI form — normalize before the root check,
        // otherwise the whole check is silently bypassed for incremental-style callers
        String normalized = path.startsWith("file:") ? path.substring("file:".length()) : path;
        if (normalized.contains(".."))
            throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
        boolean absoluteLike = normalized.startsWith("/")
                || (normalized.length() >= 2 && normalized.charAt(1) == ':');
        java.io.File localFile = new java.io.File(normalized);
        if (!absoluteLike || !localFile.isDirectory())
            return; // relative and non-directory paths are out of the root-check scope
        if (localRoot == null || localRoot.isEmpty())
            return; // no root configured: allow (global default semantics)
        try {
            String canonical = localFile.toPath().toRealPath().toString();
            String allowedCanonical = new java.io.File(localRoot).toPath().toRealPath().toString();
            if (!canonical.startsWith(allowedCanonical)) {
                throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
            }
        } catch (IOException e) {
            throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path).cause(e);
        }
    }

    private String resolveVfsPath(String path) {
        if (path == null || path.isEmpty())
            return path;
        if (path.startsWith("file:")) {
            String filePart = path.substring(5).replace('\\', '/');
            if (filePart.length() >= 2 && filePart.charAt(1) == ':' && !filePart.startsWith("/")) {
                filePart = "/" + filePart;
            }
            return "file:" + filePart;
        }
        if (path.startsWith("/"))
            return "file:" + path;
        java.io.File f = new java.io.File(path);
        String absPath = f.getAbsolutePath().replace('\\', '/');
        if (absPath.length() >= 2 && absPath.charAt(1) == ':') {
            absPath = "/" + absPath;
        }
        return "file:" + absPath;
    }

    private static class MappedPathResource implements IResource {
        private final IResource delegate;
        private final String mappedPath;

        MappedPathResource(IResource delegate, String mappedPath) {
            this.delegate = delegate;
            this.mappedPath = mappedPath;
        }

        @Override public String getPath() { return mappedPath; }
        @Override public String getStdPath() { return delegate.getStdPath(); }
        @Override public String getExternalPath() { return delegate.getExternalPath(); }
        @Override public String getName() { return delegate.getName(); }
        @Override public long length() { return delegate.length(); }
        @Override public long lastModified() { return delegate.lastModified(); }
        @Override public void setLastModified(long time) { delegate.setLastModified(time); }
        @Override public boolean exists() { return delegate.exists(); }
        @Override public boolean delete() { return delegate.delete(); }
        @Override public boolean isReadOnly() { return delegate.isReadOnly(); }
        @Override public boolean isDirectory() { return delegate.isDirectory(); }
        @Override public java.io.InputStream getInputStream() { return delegate.getInputStream(); }
        @Override public java.io.OutputStream getOutputStream(boolean append) { return delegate.getOutputStream(append); }
        @Override public java.io.File toFile() { return delegate.toFile(); }
        @Override public java.net.URL toURL() { return delegate.toURL(); }
        @Override public void saveToFile(java.io.File file) { delegate.saveToFile(file); }
        @Override public void saveToResource(IResource resource, io.nop.api.core.util.progress.IStepProgressListener listener) { delegate.saveToResource(resource, listener); }
        @Override public void writeToStream(java.io.OutputStream os, io.nop.api.core.util.progress.IStepProgressListener listener) { delegate.writeToStream(os, listener); }
        @Override public io.nop.core.resource.IResourceRegion getResourceRegion(io.nop.api.core.beans.LongRangeBean range) { return delegate.getResourceRegion(range); }
        @Override public String toString() { return mappedPath; }
    }
}
