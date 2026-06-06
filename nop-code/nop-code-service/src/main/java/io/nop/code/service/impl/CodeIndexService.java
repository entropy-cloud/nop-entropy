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
import io.nop.api.core.time.CoreMetrics;
import io.nop.code.core.NopCodeCoreErrors;
import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.analyzer.ILanguageAdapter;
import io.nop.code.core.analyzer.ProjectAnalysisResult;
import io.nop.code.core.analyzer.ProjectAnalyzer;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.incremental.ChangeSet;
import io.nop.code.core.incremental.FileFingerprint;
import io.nop.code.core.incremental.IFingerprintStore;
import io.nop.code.core.incremental.IncrementalDetector;
import io.nop.code.core.model.*;
import io.nop.code.core.model.HeuristicContext;
import io.nop.code.core.model.IHeuristicEdgeSynthesizer;
import io.nop.code.core.model.EdgeProvenance;
import io.nop.code.core.model.CodeRouteInfo;
import io.nop.code.core.resolver.IImportResolver;
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
import io.nop.code.graph.semantic.AnnotationPatternExtractor;
import io.nop.code.graph.semantic.DocKeywordExtractor;
import io.nop.code.graph.semantic.NameSimilarityExtractor;
import io.nop.code.graph.heuristic.InterfaceImplSynthesizer;
import io.nop.code.graph.heuristic.SpringEventSynthesizer;
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
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.exceptions.OrmException;
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

    private synchronized void ensureSubServices() {
        if (searchService == null && daoProvider != null) {
            searchService = new CodeSearchService(daoProvider, searchEngine, cacheManager);
            graphService = new CodeGraphService(daoProvider, cacheManager);
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
                new TypeScriptImportResolver()
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
        validatePath(vfsPath);
        invalidateAnalysisCache(indexId);
        return withIndexLock(indexId, () -> {
            String resolvedPath = resolveVfsPath(vfsPath);
            validateLocalPath(resolvedPath);
            ProjectAnalysisResult result = analyzer.analyzeProject(
                    VirtualFileSystem.instance(), resolvedPath, filePattern,
                    batch -> {});
            final ProjectAnalysisResult finalResult = result;
            return transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn ->
                    ormTemplate.runInSession(session -> {
                        ensureIndexEntity(indexId, resolvedPath, session);
                        persistInSession(indexId, resolvedPath, finalResult, session);
                        return finalResult.getFileResults().size();
                    }));
        });
    }

    @Override
    public CodeFileAnalysisResult indexFile(String indexId, String filePath, String sourceCode) {
        ICodeFileAnalyzer fileAnalyzer = registry.getAnalyzer(filePath);
        if (fileAnalyzer == null) {
            throw new NopException(ERR_NO_ANALYZER_FOR_FILE).param(ARG_INDEX_ID, indexId).param(ARG_FILE_PATH, filePath);
        }
        CodeFileAnalysisResult result = fileAnalyzer.analyze(filePath, sourceCode);

        withIndexLock(indexId, () -> {
            transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn ->
                    ormTemplate.runInSession(session -> {
                        ensureIndexEntity(indexId, null, session);
                        persistSingleFileInSession(indexId, result, session);
                        return null;
                    }));
            updateIndexStats(indexId);
        });
        invalidateAnalysisCache(indexId);
        return result;
    }

    // ====== File Queries ======

    @Override
    public List<CodeFileAnalysisResult> getFiles(String indexId) {
        ensureSubServices();
        return queryService.getFiles(indexId);
    }

    @Override
    public CodeFileAnalysisResult getFile(String indexId, String filePath) {
        ensureSubServices();
        return queryService.getFile(indexId, filePath);
    }

    @Override
    public String getFileSourceCode(String indexId, String filePath) {
        ensureSubServices();
        return queryService.getFileSourceCode(indexId, filePath);
    }

    @Override
    public List<CodeSymbol> getFileSymbols(String indexId, String filePath) {
        ensureSubServices();
        return queryService.getFileSymbols(indexId, filePath);
    }

    @Override
    public List<CodeSymbol> getFileTypes(String indexId, String filePath) {
        ensureSubServices();
        return queryService.getFileTypes(indexId, filePath);
    }

    @Override
    public FileOutlineDTO getFileOutline(String indexId, String filePath) {
        ensureSubServices();
        return queryService.getFileOutline(indexId, filePath);
    }

    @Override
    public List<FileTreeNode> getFileTree(String indexId) {
        ensureSubServices();
        return queryService.getFileTree(indexId);
    }

    @Override
    public List<ModuleDigestDTO> getModuleDigest(String indexId, String dirPath, boolean includePrivate) {
        ensureSubServices();
        return queryService.getModuleDigest(indexId, dirPath, includePrivate);
    }

    @Override
    public List<PublicAPIDTO> getPublicSurface(String indexId, String dirPath) {
        ensureSubServices();
        return queryService.getPublicSurface(indexId, dirPath);
    }

    // ====== Symbol Queries ======

    @Override
    public CodeSymbol getSymbolById(String indexId, String symbolId) {
        ensureSubServices();
        return queryService.getSymbolById(indexId, symbolId);
    }

    @Override
    public CodeSymbol findSymbolByQualifiedName(String indexId, String qualifiedName) {
        ensureSubServices();
        return queryService.findSymbolByQualifiedName(indexId, qualifiedName);
    }

    @Override
    public List<CodeSymbol> findSymbols(String indexId, String query, List<CodeSymbolKind> kinds,
                                        String packageName, int limit) {
        ensureSubServices();
        return queryService.findSymbols(indexId, query, kinds, packageName, limit);
    }

    @Override
    public PageBean<CodeSymbol> findSymbolsPage(String indexId, String query, List<CodeSymbolKind> kinds,
                                                 String packageName, long offset, int limit) {
        ensureSubServices();
        return queryService.findSymbolsPage(indexId, query, kinds, packageName, offset, limit);
    }

    @Override
    public List<CodeAnnotationUsage> getSymbolUsages(String indexId, String symbolId, int limit) {
        ensureSubServices();
        return queryService.getSymbolUsages(indexId, symbolId, limit);
    }

    @Override
    public List<ReferenceDTO> findReferencedBy(String indexId, String qualifiedName, String kind, int limit) {
        ensureSubServices();
        return queryService.findReferencedBy(indexId, qualifiedName, kind, limit);
    }

    @Override
    public String getSymbolSourceCode(String indexId, String symbolId, int linesBefore, int linesAfter) {
        ensureSubServices();
        return queryService.getSymbolSourceCode(indexId, symbolId, linesBefore, linesAfter);
    }

    @Override
    public SymbolSourceDTO showSymbolSource(String indexId, String qualifiedName, boolean includeBody) {
        ensureSubServices();
        return queryService.showSymbolSource(indexId, qualifiedName, includeBody);
    }

    // ====== Type Queries ======

    @Override
    public TypeOutlineDTO getTypeOutline(String indexId, String qualifiedName) {
        ensureSubServices();
        return queryService.getTypeOutline(indexId, qualifiedName);
    }

    @Override
    public List<TypeOutlineDTO> batchGetTypeOutlines(String indexId, List<String> qualifiedNames) {
        ensureSubServices();
        return queryService.batchGetTypeOutlines(indexId, qualifiedNames);
    }

    @Override
    public List<CodeSearchResultDTO> searchCode(String indexId, String query, String searchType,
                                                 String language, String filePattern, int limit) {
        ensureSubServices();
        return searchService.searchCode(indexId, query, searchType, language, filePattern, limit);
    }

    // ====== Hierarchy Queries ======

    @Override
    public TypeHierarchyDTO getTypeHierarchy(String indexId, String qualifiedName,
                                             String direction, int maxDepth) {
        ensureSubServices();
        return graphService.getTypeHierarchy(indexId, qualifiedName, direction, maxDepth);
    }

    @Override
    public CallHierarchyDTO getCallHierarchy(String indexId, String qualifiedName,
                                             String direction, int maxDepth) {
        ensureSubServices();
        return graphService.getCallHierarchy(indexId, qualifiedName, direction, maxDepth);
    }

    // ====== Index Management ======

    @Override
    public IndexStatsDTO getIndexStats(String indexId) {
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
        List<Map<String, Object>> kindResults = symbolDao.selectFieldsByQuery(kindQuery);
        if (!kindResults.isEmpty()) {
            Map<String, Integer> kindCounts = new LinkedHashMap<>();
            for (Map<String, Object> row : kindResults) {
                String kind = row.get("kind") != null ? row.get("kind").toString() : "UNKNOWN";
                kindCounts.merge(kind, 1, Integer::sum);
            }
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
    public void deleteIndex(String indexId) {
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
                    IEntityDao<NopCodeFlow> flowDao = daoProvider.daoFor(NopCodeFlow.class);
                    QueryBean flowQuery = new QueryBean();
                    flowQuery.addFilter(FilterBeans.eq("indexId", indexId));
                    for (NopCodeFlow flow : flowDao.findAllByQuery(flowQuery)) {
                        deleteEntitiesPaged(session, NopCodeFlowMembership.class, "flowId", flow.getId());
                    }
                    deleteEntitiesPaged(session, NopCodeFlow.class, "indexId", indexId);
                    deleteEntitiesPaged(session, NopCodeAnnotationUsage.class, "indexId", indexId);
                    deleteEntitiesPaged(session, NopCodeInheritance.class, "indexId", indexId);
                    deleteEntitiesPaged(session, NopCodeCall.class, "indexId", indexId);
                    deleteEntitiesPaged(session, NopCodeSymbol.class, "indexId", indexId);
                    deleteEntitiesPaged(session, NopCodeFile.class, "indexId", indexId);
                    deleteEntitiesPaged(session, NopCodeDependency.class, "indexId", indexId);
                    deleteEntitiesPaged(session, NopCodeSemanticEdge.class, "indexId", indexId);

                    daoProvider.daoFor(NopCodeIndex.class).deleteEntityById(indexId);
                    return null;
                }));

        indexLocks.remove(indexId);
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

    // ====== Graph Analysis ======

    @Override
    public CommunityDetectionResultDTO detectCommunities(String indexId) {
        ensureSubServices();
        return graphService.detectCommunities(indexId);
    }

    @Override
    public GraphAnalysisResultDTO getGraphAnalysis(String indexId, int topN) {
        ensureSubServices();
        return graphService.getGraphAnalysis(indexId, topN);
    }

    @Override
    public ImpactResultDTO getImpactAnalysis(String indexId, String symbolId, int depth) {
        ensureSubServices();
        return graphService.getImpactAnalysis(indexId, symbolId, depth);
    }

    @Override
    public CriticalNodeResultDTO getCriticalNodes(String indexId, int topN) {
        ensureSubServices();
        return graphService.getCriticalNodes(indexId, topN);
    }

    @Override
    public KnowledgeGapResultDTO getKnowledgeGaps(String indexId) {
        ensureSubServices();
        return graphService.getKnowledgeGaps(indexId);
    }

    @Override
    public String exportGraph(String indexId, String format, boolean communityView) {
        ensureSubServices();
        return graphService.exportGraph(indexId, format, communityView);
    }

    @Override
    public GraphDiffDTO diffGraph(String baselineIndexId, String targetIndexId) {
        ensureSubServices();
        return graphService.diffGraph(baselineIndexId, targetIndexId);
    }

    @Override
    public DepGraphDTO getDeps(String indexId, String filePath, int depth) {
        ensureSubServices();
        return graphService.getDeps(indexId, filePath, depth);
    }

    @Override
    public DepGraphDTO getReverseDeps(String indexId, String filePath, int depth, int limit) {
        ensureSubServices();
        return graphService.getReverseDeps(indexId, filePath, depth, limit);
    }

    @Override
    public List<List<String>> findCycles(String indexId, int minSize) {
        ensureSubServices();
        return graphService.findCycles(indexId, minSize);
    }

    @Override
    public DepGraphDTO getDepGraph(String indexId, boolean includeExternal) {
        ensureSubServices();
        return graphService.getDepGraph(indexId, includeExternal);
    }

    // ====== Incremental Indexing ======

    @Override
    public int triggerIncrementalIndex(String indexId, String vfsPath, String manifestPath) {
        validatePath(vfsPath);
        IResource rootResource = VirtualFileSystem.instance().getResource(vfsPath);
        if (rootResource.isDirectory()) {
            validateLocalPath(vfsPath);
        }
        invalidateAnalysisCache(indexId);

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

                deleteFileRecords(indexId, deletedFiles);
                deleteFileRecords(indexId, changedFiles);

                Map<String, IResource> resourceByPath = new HashMap<>();
                for (IResource res : currentResources) {
                    resourceByPath.put(pathMapper.apply(res.getPath()), res);
                }

                BatchQueue<CodeFileAnalysisResult> batchQueue = new BatchQueue<>(BATCH_SIZE, batch -> {
                    for (CodeFileAnalysisResult result : batch) {
                        persistSingleFileInSession(indexId, result, session);
                    }
                    LOG.debug("Flushed batch of {} analysis results for index {}", batch.size(), indexId);
                });

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
                            batchQueue.add(fileResult);
                        }
                    } catch (Exception e) {
                        LOG.warn("Failed to re-analyze file: {}", changedFile, e);
                    }
                }
                batchQueue.flush();

                List<FileFingerprint> newFingerprints = detector.computeResourceFingerprints(mappedResources);
                updateIndexStats(indexId);

                store.saveFingerprints(indexId, newFingerprints);

                return changedFiles.size();
            } catch (IOException e) {
                throw new NopException(ERR_INCREMENTAL_FAILED).param(ARG_INDEX_ID, indexId).cause(e);
            }
        })));
    }

    // ====== File Page Query ======

    @Override
    public PageBean<CodeFileAnalysisResult> findFilesPage(String indexId, String packageName, long offset, int limit) {
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

        for (CodeFileAnalysisResult file : result.getFileResults()) {
            saveFileResultInSession(indexId, file, session);
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
                edgeEntity.setRelationType(edge.getRelationType() != null ? edge.getRelationType().name() : null);
                edgeEntity.setConfidence(edge.getConfidence() != null ? edge.getConfidence().getValue() : 0);
                edgeEntity.setConfidenceScore(edge.getConfidenceScore());
                edgeEntity.setRationale(edge.getRationale());
                edgeEntity.setExtractorId(edge.getExtractorId());
                edgeEntity.setExtData(edge.getExtData());
                edgeEntity.setProvenance(edge.getProvenance() != null ? edge.getProvenance().name() : null);
                session.save(edgeEntity);
            }
            LOG.info("Persisted {} semantic edges for index {}", result.getSemanticEdges().size(), indexId);
        }

        resolveQualifiedNamesToIds(indexId, result.getGlobalSymbolTable(), session);
        synthesizeAndPersistHeuristicEdges(indexId, result, session);
    }

    private void synthesizeAndPersistHeuristicEdges(String indexId, ProjectAnalysisResult result,
                                                      IOrmSession session) {
        if (heuristicSynthesizers.isEmpty()) return;

        SymbolTable symbolTable = result.getGlobalSymbolTable();
        CallGraph callGraph = result.buildCallGraph();
        Map<String, Set<String>> inheritanceIndex = buildInheritanceIndex(indexId);

        HeuristicContext context = new HeuristicContext(symbolTable, inheritanceIndex, callGraph, indexId);

        IEntityDao<NopCodeCall> callDao = daoProvider.daoFor(NopCodeCall.class);
        Set<String> existingEdgeKeys = loadExistingEdgeKeys(callDao, indexId);

        int totalSynthesized = 0;
        for (IHeuristicEdgeSynthesizer synthesizer : heuristicSynthesizers) {
            try {
                List<CodeMethodCall> synthesized = synthesizer.synthesize(context);
                for (CodeMethodCall call : synthesized) {
                    String edgeKey = indexId + ":" + call.getCallerId() + ":" + call.getCalleeId();
                    if (existingEdgeKeys.contains(edgeKey)) continue;

                    CodeSymbol caller = symbolTable.getById(call.getCallerId());
                    String fileId = caller != null ? findFileIdForSymbol(indexId, caller.getId()) : null;

                    NopCodeCall callEntity = (NopCodeCall) ormTemplate.newEntity(NopCodeCall.class.getName());
                    callEntity.setId(call.getId());
                    callEntity.setIndexId(indexId);
                    callEntity.setCallerId(call.getCallerId());
                    callEntity.setCalleeId(call.getCalleeId());
                    callEntity.setFileId(fileId != null ? fileId : generateDummyFileId(indexId));
                    callEntity.setLine(-1);
                    callEntity.setColumn(0);
                    callEntity.setCallType(call.getCallType());
                    callEntity.setContext(call.getContext());
                    callEntity.setProvenance(EdgeProvenance.HEURISTIC.name());
                    callEntity.setMetadata(call.getMetadata());
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

    private Map<String, Set<String>> buildInheritanceIndex(String indexId) {
        Map<String, Set<String>> index = new HashMap<>();
        IEntityDao<NopCodeInheritance> inhDao = daoProvider.daoFor(NopCodeInheritance.class);
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.setLimit(MAX_QUERY_RESULTS);
        List<NopCodeInheritance> inheritances = inhDao.findAllByQuery(query);

        for (NopCodeInheritance inh : inheritances) {
            if (inh.getRelationType() != null && inh.getRelationType().equals("IMPLEMENTS")) {
                NopCodeSymbol superType = symbolDao.getEntityById(inh.getSuperTypeId());
                if (superType != null && superType.getQualifiedName() != null) {
                    index.computeIfAbsent(superType.getQualifiedName(), k -> new HashSet<>())
                            .add(inh.getSubTypeId());
                }
            }
        }
        return index;
    }

    private Set<String> loadExistingEdgeKeys(IEntityDao<NopCodeCall> callDao, String indexId) {
        Set<String> keys = new HashSet<>();
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.setLimit(MAX_QUERY_RESULTS);
        for (NopCodeCall call : callDao.findAllByQuery(query)) {
            keys.add(indexId + ":" + call.getCallerId() + ":" + call.getCalleeId());
        }
        return keys;
    }

    private String findFileIdForSymbol(String indexId, String symbolId) {
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        NopCodeSymbol sym = symbolDao.getEntityById(symbolId);
        return sym != null ? sym.getFileId() : null;
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
                if (superTypeId != null) {
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
                if (annotationTypeId != null) {
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
                edgeEntity.setRelationType(edge.getRelationType() != null ? edge.getRelationType().name() : null);
                edgeEntity.setConfidence(edge.getConfidence() != null ? edge.getConfidence().getValue() : 0);
                edgeEntity.setConfidenceScore(edge.getConfidenceScore());
                edgeEntity.setRationale(edge.getRationale());
                edgeEntity.setExtractorId(edge.getExtractorId());
                edgeEntity.setExtData(edge.getExtData());
                edgeEntity.setProvenance(edge.getProvenance() != null ? edge.getProvenance().name() : null);
                session.save(edgeEntity);
            }
        }
        SymbolTable fileSymbolTable = buildSymbolTableFromResult(result);
        SymbolTable globalTable = getOrRebuildSymbolTable(indexId);
        if (globalTable != null) {
            for (CodeSymbol sym : fileSymbolTable.getAll()) {
                if (sym.getQualifiedName() != null) {
                    CodeSymbol existing = globalTable.getByQualifiedName(sym.getQualifiedName());
                    if (existing == null) {
                        globalTable.add(sym);
                    }
                }
            }
            resolveQualifiedNamesToIds(indexId, globalTable, session);
        } else {
            resolveQualifiedNamesToIds(indexId, fileSymbolTable, session);
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

    private void saveFileResultInSession(String indexId, CodeFileAnalysisResult file,
                                         IOrmSession session) {
        Set<String> cachedProjectFilePaths = null;
        String fileEntityId = generateFileId(indexId, file.getFilePath());

        NopCodeFile fileEntity = (NopCodeFile) ormTemplate.newEntity(NopCodeFile.class.getName());
        fileEntity.setId(fileEntityId);
        fileEntity.setIndexId(indexId);
        fileEntity.setFilePath(file.getFilePath());
        fileEntity.setPackageName(file.getPackageName());
        fileEntity.setLanguage(file.getLanguage() != null ? file.getLanguage().name() : null);
        fileEntity.setLineCount(file.getLineCount());
        String sourceCode = file.getSourceCode();
        if (sourceCode != null) {
            fileEntity.setFileHash(DigestHelper.sha256Hex(sourceCode.getBytes(StandardCharsets.UTF_8)));
            fileEntity.setFileSize((long) sourceCode.length());
        }
        if (sourceCode != null) {
            fileEntity.setSourceCode(sourceCode);
        }
        if (file.getImports() != null && !file.getImports().isEmpty()) {
            fileEntity.setImports(JsonTool.stringify(file.getImports()));
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
                symEntity.setKind(sym.getKind() != null ? sym.getKind().name() : null);
                symEntity.setName(sym.getName());
                symEntity.setQualifiedName(sym.getQualifiedName());
                symEntity.setAccessModifier(sym.getAccessModifier() != null ? sym.getAccessModifier().name() : null);
                symEntity.setDeprecated(sym.isDeprecated());
                symEntity.setDocumentation(sym.getDocumentation());
                symEntity.setLine(sym.getLine());
                symEntity.setColumn(sym.getColumn());
                symEntity.setEndLine(sym.getEndLine());
                symEntity.setEndColumn(sym.getEndColumn());
                symEntity.setParentId(sym.getParentId());
                symEntity.setDeclaringSymbolId(sym.getDeclaringSymbolId());
                symEntity.setSuperClassName(sym.getSuperClassName());
                symEntity.setModifiers(sym.getModifiers());
                symEntity.setSignature(sym.getSignature());
                symEntity.setReturnType(sym.getReturnType());
                symEntity.setFieldType(sym.getFieldType());
                symEntity.setRawReturnType(sym.getRawReturnType());
                symEntity.setRawFieldType(sym.getRawFieldType());
                symEntity.setExtData(sym.getExtData());
                symEntity.setFilePath(file.getFilePath());
                symEntity.setLanguage(fileLanguage);
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
                callEntity.setCallType(call.getCallType());
                callEntity.setContext(call.getContext());
                callEntity.setProvenance(call.getProvenance() != null ? call.getProvenance().name() : null);
                callEntity.setMetadata(call.getMetadata());
                saveReplacingExisting(session, callEntity);
            }
        }

        if (file.getInheritances() != null) {
            for (CodeInheritance inh : file.getInheritances()) {
                NopCodeInheritance inhEntity = (NopCodeInheritance) ormTemplate.newEntity(NopCodeInheritance.class.getName());
                inhEntity.setId(inh.getId());
                inhEntity.setIndexId(indexId);
                inhEntity.setSubTypeId(inh.getSubTypeId());
                inhEntity.setSuperTypeId(inh.getSuperTypeQualifiedName());
                inhEntity.setRelationType(inh.getRelationType() != null ? inh.getRelationType().name() : null);
                inhEntity.setProvenance(inh.getProvenance() != null ? inh.getProvenance().name() : null);
                saveReplacingExisting(session, inhEntity);
            }
        }

        if (file.getAnnotationUsages() != null) {
            for (CodeAnnotationUsage annot : file.getAnnotationUsages()) {
                NopCodeAnnotationUsage annotEntity = (NopCodeAnnotationUsage) ormTemplate.newEntity(NopCodeAnnotationUsage.class.getName());
                annotEntity.setId(annot.getId());
                annotEntity.setIndexId(indexId);
                annotEntity.setAnnotationTypeId(annot.getAnnotationTypeQualifiedName());
                annotEntity.setAnnotatedSymbolId(annot.getAnnotatedSymbolId());
                annotEntity.setLine(annot.getLine());
                annotEntity.setColumn(annot.getColumn());
                annotEntity.setAttributes(annot.getAttributes());
                annotEntity.setProvenance(annot.getProvenance() != null ? annot.getProvenance().name() : null);
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
                routeSymbol.setExtData(JsonTool.stringify(routeExt));
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

    private Set<String> getProjectFilePaths(String indexId) {
        if (daoProvider == null) return Collections.emptySet();
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq("indexId", indexId));
        List<NopCodeFile> files = fileDao.findAllByQuery(q);
        Set<String> paths = new HashSet<>();
        for (NopCodeFile f : files) {
            if (f.getFilePath() != null) {
                paths.add(f.getFilePath());
            }
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

    private void deleteFileRecords(String indexId, List<String> filePaths) {
        if (daoProvider == null || filePaths.isEmpty()) return;

        for (String filePath : filePaths) {
            String fileId = generateFileId(indexId, filePath);

            List<String> symbolIds = findSymbolIdsByFileId(fileId);

            deleteEntitiesByFilter(NopCodeCall.class, "fileId", fileId);
            deleteEntitiesByFilter(NopCodeSymbol.class, "fileId", fileId);
            deleteEntitiesByFilter(NopCodeUsage.class, "fileId", fileId);
            deleteEntitiesByFilter(NopCodeDependency.class, "sourceFilePath", filePath);
            deleteRelationalBySymbolIds(NopCodeAnnotationUsage.class, "annotatedSymbolId", symbolIds);
            deleteRelationalBySymbolIds(NopCodeInheritance.class, "subTypeId", symbolIds);
            deleteRelationalBySymbolIds(NopCodeSemanticEdge.class, "sourceSymbolId", symbolIds);
            deleteRelationalBySymbolIds(NopCodeSemanticEdge.class, "targetSymbolId", symbolIds);
            deleteRelationalBySymbolIds(NopCodeFlowMembership.class, "symbolId", symbolIds);

            IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
            QueryBean q = new QueryBean();
            q.addFilter(FilterBeans.eq("indexId", indexId));
            q.addFilter(FilterBeans.eq("id", fileId));
            fileDao.batchDeleteEntities(fileDao.findAllByQuery(q));
        }
    }

    private void saveReplacingExisting(IOrmSession session, IOrmEntity entity) {
        try {
            session.save(entity);
        } catch (OrmException e) {
            if ("nop.err.orm.save-entity-replace-existing-entity".equals(e.getErrorCode())) {
                session.flush();
                IOrmEntity existing = (IOrmEntity) session.get(entity.orm_entityName(), entity.orm_id());
                if (existing != null) {
                    existing.orm_clearDirty();
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
                    session.flush();
                    return;
                }
                session.evictAll(entity.orm_entityName());
                session.save(entity);
            } else {
                throw e;
            }
        }
    }

    private List<String> findSymbolIdsByFileId(String fileId) {
        IEntityDao<NopCodeSymbol> dao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq("fileId", fileId));
        return dao.findAllByQuery(q).stream()
                .map(NopCodeSymbol::getId)
                .collect(Collectors.toList());
    }

    private <T extends IDaoEntity> void deleteRelationalBySymbolIds(Class<T> entityClass, String field, List<String> symbolIds) {
        if (symbolIds.isEmpty()) return;
        IEntityDao<T> dao = daoProvider.daoFor(entityClass);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.in(field, symbolIds));
        List<T> entities = dao.findAllByQuery(q);
        if (!entities.isEmpty()) {
            dao.batchDeleteEntities(entities);
        }
    }

    private <T extends IDaoEntity> void deleteEntitiesByFilter(Class<T> entityClass, String field, String value) {
        IEntityDao<T> dao = daoProvider.daoFor(entityClass);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(field, value));
        List<T> entities = dao.findAllByQuery(q);
        if (!entities.isEmpty()) {
            dao.batchDeleteEntities(entities);
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
        if (daoProvider == null) return Collections.emptyList();

        IEntityDao<NopCodeFlow> flowDao = daoProvider.daoFor(NopCodeFlow.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        List<NopCodeFlow> entities = flowDao.findAllByQuery(query);

        return entities.stream()
                .map(this::entityToExecutionFlow)
                .collect(Collectors.toList());
    }

    @Override
    public ExecutionFlow getFlow(String indexId, String flowId) {
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
        List<NopCodeFlowMembership> memberships = membershipDao.findAllByQuery(membershipQuery);
        flow.setPathNodeIds(memberships.stream()
                .map(NopCodeFlowMembership::getSymbolId)
                .collect(Collectors.toList()));

        return flow;
    }

    @Override
    public List<ExecutionFlow> getAffectedFlows(String indexId, List<String> changedFilePaths) {
        if (daoProvider == null) return Collections.emptyList();

        IFlowDetector detector = flowDetector;
        if (detector == null) {
            throw new NopException(ERR_CODE_FLOW_DETECTOR_NOT_AVAILABLE).param(ARG_INDEX_ID, indexId);
        }

        return detector.getAffectedFlows(indexId, changedFilePaths);
    }

    @Override
    public ChangeAnalysisResult analyzeChanges(String indexId, String baselineCommitish, String targetCommitish) {
        if (daoProvider == null) return null;

        SymbolTable symbolTable = getOrRebuildSymbolTable(indexId);
        CallGraph callGraph = getOrRebuildCallGraph(indexId);

        IChangeAnalyzer analyzer = changeAnalyzer;
        if (analyzer == null) {
            throw new NopException(ERR_CODE_CHANGE_ANALYZER_NOT_AVAILABLE).param(ARG_INDEX_ID, indexId);
        }

        return analyzer.analyzeChanges(indexId, baselineCommitish, targetCommitish, symbolTable, callGraph);
    }

    @Override
    public DeadCodeReport detectDeadCode(String indexId) {
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
            IEntityDao<NopCodeFlow> flowDao = daoProvider.daoFor(NopCodeFlow.class);
            QueryBean deleteQuery = new QueryBean();
            deleteQuery.addFilter(FilterBeans.eq("indexId", indexId));
            deleteQuery.setLimit(DELETE_BATCH_SIZE);
            while (true) {
                List<NopCodeFlow> existing = flowDao.findAllByQuery(deleteQuery);
                if (existing.isEmpty()) break;
                for (NopCodeFlow existingFlow : existing) {
                    deleteEntitiesPaged(session, NopCodeFlowMembership.class, "flowId", existingFlow.getId());
                }
                flowDao.batchDeleteEntities(existing);
                session.flush();
                session.evictAll(NopCodeFlow.class.getName());
            }

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
        ensureSubServices();
        return queryService.findByAnnotation(indexId, annotationName);
    }

    @Override
    public List<CodeSymbol> findImplementations(String indexId, String qualifiedName, boolean directOnly, int maxDepth) {
        ensureSubServices();
        return queryService.findImplementations(indexId, qualifiedName, directOnly, maxDepth);
    }

    @Override
    public List<String> findDependentFiles(String indexId, String filePath) {
        ensureSubServices();
        return graphService.findDependentFiles(indexId, filePath);
    }

    // ====== Batch File Records ======

    @Override
    public void batchSaveFileRecords(String indexId, List<FileFingerprint> fingerprints) {
        if (daoProvider == null || fingerprints == null || fingerprints.isEmpty()) return;

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);

        for (FileFingerprint fp : fingerprints) {
            String fileId = generateFileId(indexId, fp.getFilePath());

            // Try to find existing
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq("indexId", indexId));
            query.addFilter(FilterBeans.eq("filePath", fp.getFilePath()));
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
        if (daoProvider == null) return new ArrayList<>();

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));

        List<NopCodeFile> entities = fileDao.findAllByQuery(query);
        List<FileFingerprint> fingerprints = new ArrayList<>(entities.size());

        for (NopCodeFile entity : entities) {
            FileFingerprint fp = new FileFingerprint();
            fp.setFilePath(entity.getFilePath());
            fp.setContentHash(entity.getFileHash());
            fp.setLastModified(entity.getLastModified() != null ? entity.getLastModified() : 0L);
            fp.setFileSize(entity.getFileSize() != null ? entity.getFileSize() : 0L);
            fingerprints.add(fp);
        }

        return fingerprints;
    }

    @Override
    public void batchDeleteFileRecords(String indexId, List<String> filePaths) {
        deleteFileRecords(indexId, filePaths);
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
        Set<String> matchingPaths = new HashSet<>();
        for (NopCodeFile f : fileDao.findAllByQuery(fq)) {
            matchingPaths.add(f.getFilePath());
        }
        if (matchingPaths.isEmpty()) return Collections.emptyList();
        results.removeIf(dto -> !matchingPaths.contains(dto.getFilePath()));
        return results;
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

    private void validateLocalPath(String path) {
        if (path == null || path.isEmpty())
            return;
        if (path.contains(".."))
            throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
        if (path.startsWith("/") || (path.length() >= 2 && path.charAt(1) == ':')) {
            throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
        }
        java.io.File localFile = new java.io.File(path);
        if (localFile.isDirectory()) {
            if (allowedLocalRoot != null && !allowedLocalRoot.isEmpty()) {
                try {
                    String canonical = localFile.toPath().toRealPath().toString();
                    String allowedCanonical = new java.io.File(allowedLocalRoot).toPath().toRealPath().toString();
                    if (!canonical.startsWith(allowedCanonical)) {
                        throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
                    }
                } catch (IOException e) {
                    throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path).cause(e);
                }
            }
        }
    }

    private String resolveVfsPath(String path) {
        if (path == null || path.isEmpty())
            return path;
        if (path.startsWith("file:") || path.startsWith("/"))
            return "file:" + (path.startsWith("file:") ? path.substring(5) : path);
        java.io.File f = new java.io.File(path);
        return "file:" + f.getAbsolutePath();
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
