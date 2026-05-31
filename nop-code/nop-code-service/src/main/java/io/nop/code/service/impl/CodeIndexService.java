package io.nop.code.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;import java.util.concurrent.locks.ReentrantLock;
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
import io.nop.code.lang.java.JavaLanguageAdapter;
import io.nop.code.lang.python.PythonLanguageAdapter;
import io.nop.code.lang.typescript.TypeScriptLanguageAdapter;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.*;
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

    protected final LanguageAdapterRegistry registry;
    protected final ProjectAnalyzer analyzer;
    protected final Map<String, IImportResolver> importResolvers = new HashMap<>();

    @Inject
    protected IDaoProvider daoProvider;

    @Inject
    protected IOrmTemplate ormTemplate;

    private void ensureSubServices() {
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
        this.registry = new LanguageAdapterRegistry();
        this.registry.registerAdapter(new JavaLanguageAdapter());
        this.registry.registerAdapter(new PythonLanguageAdapter());
        this.registry.registerAdapter(new TypeScriptLanguageAdapter());
        this.analyzer = new ProjectAnalyzer(registry);
        registerSemanticExtractors();
        registerImportResolvers();
    }

    public CodeIndexService(LanguageAdapterRegistry registry, ProjectAnalyzer analyzer) {
        this.registry = registry;
        this.analyzer = analyzer;
        registerSemanticExtractors();
        registerImportResolvers();
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

    // 

====== Entity-to-Model Conversion 

======

    private CodeSymbol entityToCodeSymbol(NopCodeSymbol entity) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(entity.getId());
        symbol.setName(entity.getName());
        symbol.setKind(entity.getKind() != null ? CodeSymbolKind.valueOf(entity.getKind()) : null);
        symbol.setQualifiedName(entity.getQualifiedName());
        symbol.setAccessModifier(entity.getAccessModifier() != null
                ? CodeAccessModifier.valueOf(entity.getAccessModifier()) : null);
        symbol.setDeprecated(Boolean.TRUE.equals(entity.getDeprecated()));
        symbol.setDocumentation(entity.getDocumentation());
        symbol.setLine(entity.getLine() != null ? entity.getLine() : 0);
        symbol.setColumn(entity.getColumn() != null ? entity.getColumn() : 0);
        symbol.setEndLine(entity.getEndLine() != null ? entity.getEndLine() : 0);
        symbol.setEndColumn(entity.getEndColumn() != null ? entity.getEndColumn() : 0);
        symbol.setParentId(entity.getParentId());
        symbol.setDeclaringSymbolId(entity.getDeclaringSymbolId());
        symbol.setSuperClassName(entity.getSuperClassName());
        symbol.setAbstractFlag(Boolean.TRUE.equals(entity.getIsAbstract()));
        symbol.setFinalFlag(Boolean.TRUE.equals(entity.getIsFinal()));
        symbol.setSignature(entity.getSignature());
        symbol.setReturnType(entity.getReturnType());
        symbol.setStaticFlag(Boolean.TRUE.equals(entity.getIsStatic()));
        symbol.setFieldType(entity.getFieldType());
        symbol.setRawReturnType(entity.getRawReturnType());
        symbol.setRawFieldType(entity.getRawFieldType());
        symbol.setAsyncFlag(Boolean.TRUE.equals(entity.getAsyncFlag()));
        symbol.setReadonlyFlag(Boolean.TRUE.equals(entity.getReadonlyFlag()));
        symbol.setExtData(entity.getExtData());
        return symbol;
    }

    private CodeInheritance entityToInheritance(NopCodeInheritance entity) {
        CodeInheritance inh = new CodeInheritance();
        inh.setId(entity.getId());
        inh.setSubTypeId(entity.getSubTypeId());
        inh.setSuperTypeQualifiedName(entity.getSuperTypeId());
        inh.setRelationType(entity.getRelationType() != null
                ? CodeRelationType.valueOf(entity.getRelationType()) : null);
        return inh;
    }

    // 

====== Rebuild-from-DB Helpers 

======
    // 

====== Rebuild-from-DB Helpers 

======

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

    // 

====== Indexing 

======

    @Override
    public int indexDirectory(String indexId, String vfsPath, String filePattern) {
        validatePath(vfsPath);
        invalidateAnalysisCache(indexId);
        ReentrantLock lock = indexLocks.computeIfAbsent(indexId, k -> new ReentrantLock());
        lock.lock();
        try {
            java.io.File localFile = new java.io.File(vfsPath);
            ProjectAnalysisResult result;
            if (localFile.isDirectory()) {
                validateLocalPath(vfsPath);
                result = analyzer.analyzeProject(localFile.toPath());
            } else {
                result = analyzer.analyzeProject(
                        VirtualFileSystem.instance(), vfsPath, filePattern,
                        batch -> {});
            }
            final ProjectAnalysisResult finalResult = result;
            return ormTemplate.runInSession(session -> {
                ensureIndexEntity(indexId, vfsPath, session);
                persistInSession(indexId, vfsPath, finalResult, session);
                return finalResult.getFileResults().size();
            });
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CodeFileAnalysisResult indexFile(String indexId, String filePath, String sourceCode) {
        ICodeFileAnalyzer fileAnalyzer = registry.getAnalyzer(filePath);
        if (fileAnalyzer == null) {
            throw new NopException(ERR_NO_ANALYZER_FOR_FILE).param(ARG_INDEX_ID, indexId).param(ARG_FILE_PATH, filePath);
        }
        CodeFileAnalysisResult result = fileAnalyzer.analyze(filePath, sourceCode);

        ormTemplate.runInSession(session -> {
            ensureIndexEntity(indexId, null, session);
            persistSingleFileInSession(indexId, result, session);
            return null;
        });
        invalidateAnalysisCache(indexId);
        return result;
    }

    // 

====== File Queries 

======

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

    // 

====== Symbol Queries 

======

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

    // 

====== Type Queries 

======

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

    // 

====== Hierarchy Queries 

======

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

    // 

====== Index Management 

======

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

        ormTemplate.runInSession(session -> {
            deleteEntitiesPaged(session, NopCodeUsage.class, "indexId", indexId);
            deleteEntitiesPaged(session, NopCodeFlowMembership.class, "flow.indexId", indexId);
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

    // 

====== Graph Analysis 

======

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

    // 

====== Incremental Indexing 

======

    @Override
    public int triggerIncrementalIndex(String indexId, String vfsPath, String manifestPath) {
        validatePath(vfsPath);
        java.io.File localFile = new java.io.File(vfsPath);
        if (localFile.isDirectory()) {
            validateLocalPath(vfsPath);
        }
        invalidateAnalysisCache(indexId);

        Function<String, String> pathMapper = buildPathMapper(vfsPath);

        List<FileFingerprint> previousFingerprints;
        try {
            previousFingerprints = ormTemplate.runInSession(session -> {
                try {
                    IFingerprintStore store = new OrmFingerprintStore(daoProvider, ormTemplate, pathMapper);
                    return store.loadFingerprints(indexId);
                } catch (IOException e) {
                    throw new NopException(ERR_INCREMENTAL_FAILED).param(ARG_INDEX_ID, indexId).cause(e);
                }
            });
        } catch (NopException e) {
            if (e.getErrorCode() != null && e.getErrorCode().equals(ERR_INCREMENTAL_FAILED))
                throw e;
            throw new NopException(ERR_INCREMENTAL_FAILED).param(ARG_INDEX_ID, indexId).cause(e);
        }

        IResourceLoader vfs = VirtualFileSystem.instance();
        List<IResource> currentResources = collectResourcesFromVfs(vfs, vfsPath);

        List<Path> currentPaths = currentResources.stream()
                .map(res -> Path.of(pathMapper.apply(res.getPath())))
                .collect(Collectors.toList());

        IncrementalDetector detector = new IncrementalDetector();
        ChangeSet changes;
        try {
            changes = detector.detectChanges(previousFingerprints, currentPaths);
        } catch (IOException e) {
            throw new NopException(ERR_INCREMENTAL_FAILED).param(ARG_INDEX_ID, indexId).cause(e);
        }

        List<Path> changedFiles = changes.getAddedAndModified();
        List<Path> deletedFiles = changes.getDeletedFiles();

        LOG.info("Incremental index for {}: {} changed, {} deleted, {} unchanged",
                indexId, changedFiles.size(), deletedFiles.size(),
                changes.getUnchangedFiles().size());

        if (changedFiles.isEmpty() && deletedFiles.isEmpty()) {
            return 0;
        }

        Map<String, IResource> resourceByPath = new HashMap<>();
        for (IResource res : currentResources) {
            resourceByPath.put(pathMapper.apply(res.getPath()), res);
        }

        List<CodeFileAnalysisResult> analysisResults = new ArrayList<>();
        for (Path changedFile : changedFiles) {
            try {
                String relativePath = changedFile.toString();
                ICodeFileAnalyzer fileAnalyzer = registry.getAnalyzer(relativePath);
                if (fileAnalyzer == null) continue;

                IResource resource = resourceByPath.get(relativePath);
                if (resource == null) {
                    LOG.warn("Resource not found for path: {}", relativePath);
                    continue;
                }
                String sourceCode = resource.readText();
                CodeFileAnalysisResult fileResult = fileAnalyzer.analyze(relativePath, sourceCode);
                if (fileResult != null) {
                    analysisResults.add(fileResult);
                }
            } catch (Exception e) {
                LOG.warn("Failed to re-analyze file: {}", changedFile, e);
            }
        }

        List<FileFingerprint> newFingerprints = new ArrayList<>(currentResources.size());
        try {
            for (IResource res : currentResources) {
                newFingerprints.add(detector.computeFingerprint(res));
            }
        } catch (IOException e) {
            throw new NopException(ERR_INCREMENTAL_FAILED).param(ARG_INDEX_ID, indexId).cause(e);
        }

        return ormTemplate.runInSession(session -> {
            try {
                deleteFileRecords(indexId, deletedFiles.stream().map(Path::toString).collect(Collectors.toList()));
                deleteFileRecords(indexId, changedFiles.stream().map(Path::toString).collect(Collectors.toList()));

                Function<String, String> pathMapper = buildPathMapper(vfsPath);
                IFingerprintStore store = new OrmFingerprintStore(daoProvider, ormTemplate, pathMapper);
                List<FileFingerprint> previousFingerprints = store.loadFingerprints(indexId);

                IResourceLoader vfs = VirtualFileSystem.instance();
                List<IResource> currentResources = collectResourcesFromVfs(vfs, vfsPath);

                ChangeSet changes = detector.detectResourceChanges(previousFingerprints, currentResources);

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

                int[] count = {0};
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
                            count[0]++;
                        }
                    } catch (Exception e) {
                        LOG.warn("Failed to re-analyze file: {}", changedFile, e);
                    }
                }
                batchQueue.flush();

                for (CodeFileAnalysisResult result : analysisResults) {
                    persistSingleFileInSession(indexId, result, session);
                }

                updateIndexStats(indexId);

                IFingerprintStore store = new OrmFingerprintStore(daoProvider, ormTemplate, pathMapper);
                store.saveFingerprints(indexId, newFingerprints);

                return analysisResults.size();
            } catch (IOException e) {
                throw new NopException(ERR_INCREMENTAL_FAILED).param(ARG_INDEX_ID, indexId).cause(e);
            }
        });
    }

    // 

====== File Page Query 

======

    @Override
    public PageBean<CodeFileAnalysisResult> findFilesPage(String indexId, String packageName, long offset, int limit) {
        ensureSubServices();
        return queryService.findFilesPage(indexId, packageName, offset, limit);
    }

    // 

====== ORM Persistence 

======

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
            indexEntity.setLanguage("Java");
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
                session.save(edgeEntity);
            }
            LOG.info("Persisted {} semantic edges for index {}", result.getSemanticEdges().size(), indexId);
        }

        resolveQualifiedNamesToIds(indexId, result.getGlobalSymbolTable(), session);
    }

    private void resolveQualifiedNamesToIds(String indexId, SymbolTable symbolTable, IOrmSession session) {
        IEntityDao<NopCodeInheritance> inhDao = daoProvider.daoFor(NopCodeInheritance.class);
        QueryBean inhQuery = new QueryBean();
        inhQuery.addFilter(FilterBeans.eq("indexId", indexId));
        for (NopCodeInheritance inh : inhDao.findAllByQuery(inhQuery)) {
            String superTypeId = inh.getSuperTypeId();
            if (superTypeId != null) {
                CodeSymbol resolved = symbolTable.getByQualifiedName(superTypeId);
                if (resolved != null) {
                    inh.setSuperTypeId(resolved.getId());
                }
            }
        }

        IEntityDao<NopCodeAnnotationUsage> annotDao = daoProvider.daoFor(NopCodeAnnotationUsage.class);
        QueryBean annotQuery = new QueryBean();
        annotQuery.addFilter(FilterBeans.eq("indexId", indexId));
        for (NopCodeAnnotationUsage annot : annotDao.findAllByQuery(annotQuery)) {
            String annotationTypeId = annot.getAnnotationTypeId();
            if (annotationTypeId != null) {
                CodeSymbol resolved = symbolTable.getByQualifiedName(annotationTypeId);
                if (resolved != null) {
                    annot.setAnnotationTypeId(resolved.getId());
                }
            }
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
            indexEntity.setLanguage("Java");
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
                session.save(edgeEntity);
            }
        }
        SymbolTable symbolTable = buildSymbolTableFromResult(result);
        resolveQualifiedNamesToIds(indexId, symbolTable, session);
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
                symEntity.setIsAbstract(sym.isAbstractFlag());
                symEntity.setIsFinal(sym.isFinalFlag());
                symEntity.setSignature(sym.getSignature());
                symEntity.setReturnType(sym.getReturnType());
                symEntity.setIsStatic(sym.isStaticFlag());
                symEntity.setFieldType(sym.getFieldType());
                symEntity.setRawReturnType(sym.getRawReturnType());
                symEntity.setRawFieldType(sym.getRawFieldType());
                symEntity.setExtData(sym.getExtData());
                symEntity.setAsyncFlag(sym.isAsyncFlag());
                symEntity.setReadonlyFlag(sym.isReadonlyFlag());
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
                saveReplacingExisting(session, annotEntity);
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
            boolean isTestFile = file.getFilePath().contains("Test.java")
                    || file.getFilePath().contains("/test/");
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
                    depEntity.setResolved(dep.isResolved());
                    session.save(depEntity);
                }
            }
        }
    }

    // 

====== Incremental Indexing Helpers 

======

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
                index.setFileCount(fileDao.findAllByQuery(fq).size());

                IEntityDao<NopCodeSymbol> symDao = daoProvider.daoFor(NopCodeSymbol.class);
                QueryBean sq = new QueryBean();
                sq.addFilter(FilterBeans.eq("indexId", indexId));
                index.setSymbolCount(symDao.findAllByQuery(sq).size());
                index.setLastIndexed(CoreMetrics.currentTimeMillis());
            }
        } catch (Exception e) {
            LOG.warn("Failed to update index stats for {}", indexId, e);
        }
    }

    // 

====== Flow Analysis 

======

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

    private void persistFlows(String indexId, List<ExecutionFlow> flows) {
        ormTemplate.runInSession(session -> {
            IEntityDao<NopCodeFlow> flowDao = daoProvider.daoFor(NopCodeFlow.class);
            QueryBean deleteQuery = new QueryBean();
            deleteQuery.addFilter(FilterBeans.eq("indexId", indexId));
            List<NopCodeFlow> existing = flowDao.findAllByQuery(deleteQuery);
            for (NopCodeFlow existingFlow : existing) {
                IEntityDao<NopCodeFlowMembership> membershipDao = daoProvider.daoFor(NopCodeFlowMembership.class);
                QueryBean mQuery = new QueryBean();
                mQuery.addFilter(FilterBeans.eq("flowId", existingFlow.getId()));
                membershipDao.batchDeleteEntities(membershipDao.findAllByQuery(mQuery));
            }
            flowDao.batchDeleteEntities(existing);

            for (ExecutionFlow flow : flows) {
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
                        membership.setCreatedTime(CoreMetrics.currentTimestamp());
                        session.save(membership);
                    }
                }
            }
            return null;
        });
    }

    private ExecutionFlow entityToExecutionFlow(NopCodeFlow entity) {
        ExecutionFlow flow = new ExecutionFlow();
        flow.setId(entity.getId());
        flow.setName(entity.getName());
        flow.setIndexId(entity.getIndexId());
        flow.setEntryPointSymbolId(entity.getEntryPointId());
        flow.setEntryPointQualifiedName(entity.getEntryPointQualifiedName());
        flow.setDepth(entity.getDepth() != null ? entity.getDepth() : 0);
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

    // 

====== Batch File Records 

======

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

    private String allowedLocalRoot;

    public void setAllowedLocalRoot(String allowedLocalRoot) {
        this.allowedLocalRoot = allowedLocalRoot;
    }

    private void validatePath(String path) {
        if (path == null || path.isEmpty())
            return;
        if (path.contains(".."))
            throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
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
}
}
    private void validateLocalPath(String path) {
        if (path == null || path.isEmpty())
            return;
        if (path.contains(".."))
            throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
        java.io.File localFile = new java.io.File(path);
        if (localFile.isDirectory() && allowedLocalRoot != null && !allowedLocalRoot.isEmpty()) {
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
