package io.nop.code.service.impl;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.analyzer.CommunityDetector;
import io.nop.code.core.analyzer.EntryPointScorer;
import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.analyzer.ImpactAnalyzer;
import io.nop.code.core.analyzer.ILanguageAdapter;
import io.nop.code.core.analyzer.ProjectAnalyzer;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.incremental.ChangeSet;
import io.nop.code.core.incremental.FileFingerprint;
import io.nop.code.core.incremental.IFingerprintStore;
import io.nop.code.core.incremental.IncrementalDetector;
import io.nop.code.core.incremental.InMemoryFingerprintStore;
import io.nop.code.core.model.*;
import io.nop.code.dao.entity.NopCodeAnnotationUsage;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.code.dao.entity.NopCodeIndex;
import io.nop.code.dao.entity.NopCodeInheritance;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.lang.java.JavaLanguageAdapter;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.*;
import io.nop.commons.batch.BatchQueue;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceLoader;
import io.nop.core.resource.VirtualFileSystem;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

import static io.nop.code.service.NopCodeErrors.*;

public class CodeIndexService implements ICodeIndexService {

    private static final Logger LOG = LoggerFactory.getLogger(CodeIndexService.class);

    private static final int BATCH_SIZE = 1000;

    protected final LanguageAdapterRegistry registry;
    protected final ProjectAnalyzer analyzer;

    @Inject
    protected IDaoProvider daoProvider;

    @Inject
    protected IOrmTemplate ormTemplate;

    protected IFingerprintStore fingerprintStore = new InMemoryFingerprintStore();

    public void setFingerprintStore(IFingerprintStore fingerprintStore) {
        this.fingerprintStore = fingerprintStore;
    }

    public CodeIndexService() {
        this.registry = new LanguageAdapterRegistry();
        this.registry.registerAdapter(new JavaLanguageAdapter());
        this.analyzer = new ProjectAnalyzer(registry);
    }

    public CodeIndexService(LanguageAdapterRegistry registry, ProjectAnalyzer analyzer) {
        this.registry = registry;
        this.analyzer = analyzer;
    }

    // ==================== Entity-to-Model Conversion ====================

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
        symbol.setAsyncFlag(Boolean.TRUE.equals(entity.getAsyncFlag()));
        symbol.setReadonlyFlag(Boolean.TRUE.equals(entity.getReadonlyFlag()));
        return symbol;
    }

    private CodeFileAnalysisResult entityToFileResult(NopCodeFile entity) {
        CodeFileAnalysisResult result = new CodeFileAnalysisResult();
        result.setFilePath(entity.getFilePath());
        result.setPackageName(entity.getPackageName());
        result.setLanguage(entity.getLanguage() != null
                ? CodeLanguage.valueOf(entity.getLanguage()) : null);
        result.setLineCount(entity.getLineCount() != null ? entity.getLineCount() : 0);
        result.setSourceCode(null); // sourceCode not stored in DB
        return result;
    }

    private CodeAnnotationUsage entityToAnnotationUsage(NopCodeAnnotationUsage entity) {
        CodeAnnotationUsage usage = new CodeAnnotationUsage();
        usage.setId(entity.getId());
        usage.setAnnotationTypeQualifiedName(entity.getAnnotationTypeId());
        usage.setAnnotatedSymbolId(entity.getAnnotatedSymbolId());
        usage.setLine(entity.getLine() != null ? entity.getLine() : 0);
        usage.setColumn(entity.getColumn() != null ? entity.getColumn() : 0);
        usage.setAttributes(entity.getAttributes());
        return usage;
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

    // ==================== Rebuild-from-DB Helpers ====================

    private SymbolTable rebuildSymbolTable(String indexId) {
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));

        List<NopCodeSymbol> entities = symbolDao.findAllByQuery(query);

        SymbolTable table = new SymbolTable();
        for (NopCodeSymbol entity : entities) {
            CodeSymbol symbol = entityToCodeSymbol(entity);
            table.add(symbol);
        }
        return table;
    }

    private CallGraph rebuildCallGraph(String indexId) {
        IEntityDao<NopCodeCall> callDao = daoProvider.daoFor(NopCodeCall.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));

        List<NopCodeCall> callEntities = callDao.findAllByQuery(query);

        CallGraph callGraph = new CallGraph();
        for (NopCodeCall entity : callEntities) {
            callGraph.addEdge(entity.getCallerId(), entity.getCalleeId());
        }
        return callGraph;
    }

    // ==================== Indexing ====================

    @Override
    public int indexDirectory(String indexId, String vfsPath, String filePattern) {
        return ormTemplate.runInSession(session -> {
            ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(
                    VirtualFileSystem.instance(), vfsPath, filePattern);

            persistInSession(indexId, result, session);
            return result.getFileResults().size();
        });
    }

    @Override
    public CodeFileAnalysisResult indexFile(String indexId, String filePath, String sourceCode) {
        ICodeFileAnalyzer fileAnalyzer = registry.getAnalyzer(filePath);
        if (fileAnalyzer == null) {
            throw new NopException(ERR_NO_ANALYZER_FOR_FILE).param(ARG_FILE_PATH, filePath);
        }
        CodeFileAnalysisResult result = fileAnalyzer.analyze(filePath, sourceCode);

        ormTemplate.runInSession(session -> {
            persistSingleFileInSession(indexId, result, session);
            return null;
        });
        return result;
    }

    // ==================== File Queries ====================

    @Override
    public List<CodeFileAnalysisResult> getFiles(String indexId) {
        if (daoProvider == null) return Collections.emptyList();
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        return fileDao.findAllByQuery(query).stream()
                .map(this::entityToFileResult)
                .collect(Collectors.toList());
    }

    @Override
    public CodeFileAnalysisResult getFile(String indexId, String filePath) {
        if (daoProvider == null) return null;
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addFilter(FilterBeans.eq("filePath", filePath));
        List<NopCodeFile> files = fileDao.findAllByQuery(query);
        return files.isEmpty() ? null : entityToFileResult(files.get(0));
    }

    @Override
    public String getFileSourceCode(String indexId, String filePath) {
        return null; // sourceCode not stored in DB
    }

    @Override
    public List<CodeSymbol> getFileSymbols(String indexId, String filePath) {
        if (daoProvider == null) return Collections.emptyList();
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        String fileId = indexId + "_" + Math.abs(filePath.hashCode());
        query.addFilter(FilterBeans.eq("fileId", fileId));
        return symbolDao.findAllByQuery(query).stream()
                .map(this::entityToCodeSymbol)
                .collect(Collectors.toList());
    }

    @Override
    public List<CodeSymbol> getFileTypes(String indexId, String filePath) {
        List<CodeSymbol> symbols = getFileSymbols(indexId, filePath);
        return symbols.stream()
                .filter(s -> s.getKind() == CodeSymbolKind.CLASS
                        || s.getKind() == CodeSymbolKind.INTERFACE
                        || s.getKind() == CodeSymbolKind.ENUM
                        || s.getKind() == CodeSymbolKind.ANNOTATION_TYPE)
                .collect(Collectors.toList());
    }

    @Override
    public List<FileTreeNode> getFileTree(String indexId) {
        List<CodeFileAnalysisResult> files = getFiles(indexId);

        FileTreeNode root = new FileTreeNode();
        root.setName("root");
        root.setPath("");
        root.setType("package");

        Map<String, FileTreeNode> nodeMap = new LinkedHashMap<>();
        nodeMap.put("", root);

        for (CodeFileAnalysisResult file : files) {
            String packageName = file.getPackageName();
            if (packageName == null || packageName.isEmpty()) {
                packageName = "(default)";
            }

            String[] parts = packageName.split("\\.");
            StringBuilder currentPath = new StringBuilder();
            for (String part : parts) {
                String parentPath = currentPath.toString();
                currentPath.append(currentPath.length() > 0 ? "." : "").append(part);
                String packagePath = currentPath.toString();

                if (!nodeMap.containsKey(packagePath)) {
                    FileTreeNode packageNode = new FileTreeNode();
                    packageNode.setName(part);
                    packageNode.setPath(packagePath);
                    packageNode.setType("package");
                    nodeMap.put(packagePath, packageNode);

                    FileTreeNode parentNode = nodeMap.get(parentPath);
                    if (parentNode != null) {
                        parentNode.getChildren().add(packageNode);
                    }
                }
            }

            FileTreeNode fileNode = new FileTreeNode();
            fileNode.setName(file.getFilePath() != null
                    ? file.getFilePath().substring(file.getFilePath().lastIndexOf('/') + 1)
                    : "unknown");
            fileNode.setPath(file.getFilePath());
            fileNode.setType("file");

            FileTreeNode packageParent = nodeMap.get(packageName);
            if (packageParent != null) {
                packageParent.getChildren().add(fileNode);
            }
        }

        return root.getChildren();
    }

    // ==================== Symbol Queries ====================

    @Override
    public CodeSymbol getSymbolById(String indexId, String symbolId) {
        if (daoProvider == null) return null;
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        NopCodeSymbol entity = symbolDao.getEntityById(symbolId);
        return entity != null ? entityToCodeSymbol(entity) : null;
    }

    @Override
    public CodeSymbol findSymbolByQualifiedName(String indexId, String qualifiedName) {
        if (daoProvider == null) return null;
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addFilter(FilterBeans.eq("qualifiedName", qualifiedName));
        List<NopCodeSymbol> results = symbolDao.findAllByQuery(query);
        return results.isEmpty() ? null : entityToCodeSymbol(results.get(0));
    }

    @Override
    public List<CodeSymbol> findSymbols(String indexId, String query, List<CodeSymbolKind> kinds,
                                        String packageName, int limit) {
        if (daoProvider == null) return Collections.emptyList();
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean qb = new QueryBean();
        qb.addFilter(FilterBeans.eq("indexId", indexId));
        if (query != null && !query.isEmpty()) {
            TreeBean nameFilter = FilterBeans.contains("name", query);
            TreeBean qnFilter = FilterBeans.contains("qualifiedName", query);
            qb.addFilter(FilterBeans.or(nameFilter, qnFilter));
        }
        if (kinds != null && !kinds.isEmpty()) {
            List<String> kindNames = kinds.stream().map(Enum::name).collect(Collectors.toList());
            qb.addFilter(FilterBeans.in("kind", kindNames));
        }
        if (packageName != null && !packageName.isEmpty()) {
            qb.addFilter(FilterBeans.startsWith("qualifiedName", packageName));
        }
        if (limit > 0) qb.setLimit(limit);
        return symbolDao.findAllByQuery(qb).stream()
                .map(this::entityToCodeSymbol)
                .collect(Collectors.toList());
    }

    @Override
    public PageBean<CodeSymbol> findSymbolsPage(String indexId, String query, List<CodeSymbolKind> kinds,
                                                 String packageName, long offset, int limit) {
        PageBean<CodeSymbol> pageBean = new PageBean<>();
        pageBean.setOffset(offset);
        pageBean.setLimit(limit);

        if (daoProvider == null) {
            pageBean.setTotal(0);
            pageBean.setItems(Collections.emptyList());
            return pageBean;
        }

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);

        // Build base filter for count query
        QueryBean countQb = new QueryBean();
        countQb.addFilter(FilterBeans.eq("indexId", indexId));
        if (query != null && !query.isEmpty()) {
            TreeBean nameFilter = FilterBeans.contains("name", query);
            TreeBean qnFilter = FilterBeans.contains("qualifiedName", query);
            countQb.addFilter(FilterBeans.or(nameFilter, qnFilter));
        }
        if (kinds != null && !kinds.isEmpty()) {
            List<String> kindNames = kinds.stream().map(Enum::name).collect(Collectors.toList());
            countQb.addFilter(FilterBeans.in("kind", kindNames));
        }
        if (packageName != null && !packageName.isEmpty()) {
            countQb.addFilter(FilterBeans.startsWith("qualifiedName", packageName));
        }

        long total = symbolDao.countByQuery(countQb);
        pageBean.setTotal(total);

        // Page query
        QueryBean pageQb = new QueryBean();
        pageQb.setOffset(offset);
        pageQb.setLimit(limit > 0 ? limit : 20);
        pageQb.setFilter(countQb.getFilter());

        List<NopCodeSymbol> entities = symbolDao.findPageByQuery(pageQb);
        pageBean.setItems(entities.stream()
                .map(this::entityToCodeSymbol)
                .collect(Collectors.toList()));
        return pageBean;
    }

    @Override
    public List<CodeAnnotationUsage> getSymbolUsages(String indexId, String symbolId, int limit) {
        if (daoProvider == null) return Collections.emptyList();
        IEntityDao<NopCodeAnnotationUsage> annotDao = daoProvider.daoFor(NopCodeAnnotationUsage.class);
        QueryBean qb = new QueryBean();
        qb.addFilter(FilterBeans.eq("indexId", indexId));
        qb.addFilter(FilterBeans.eq("annotatedSymbolId", symbolId));
        if (limit > 0) qb.setLimit(limit);
        return annotDao.findAllByQuery(qb).stream()
                .map(this::entityToAnnotationUsage)
                .collect(Collectors.toList());
    }

    @Override
    public String getSymbolSourceCode(String indexId, String symbolId, int linesBefore, int linesAfter) {
        return null; // sourceCode not stored in DB — TODO: future disk-based loading
    }

    // ==================== Type Queries ====================

    @Override
    public TypeOutlineDTO getTypeOutline(String indexId, String qualifiedName) {
        if (daoProvider == null) return null;
        CodeSymbol symbol = findSymbolByQualifiedName(indexId, qualifiedName);
        if (symbol == null) return null;

        TypeOutlineDTO outline = new TypeOutlineDTO();
        outline.setName(symbol.getName());
        outline.setQualifiedName(symbol.getQualifiedName());
        outline.setKind(symbol.getKind().name());
        outline.setAccessModifier(symbol.getAccessModifier() != null ? symbol.getAccessModifier().name() : null);

        // Find children by parentId or declaringSymbolId
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean childQuery = new QueryBean();
        childQuery.addFilter(FilterBeans.eq("indexId", indexId));
        childQuery.addFilter(FilterBeans.or(
                FilterBeans.eq("parentId", symbol.getId()),
                FilterBeans.eq("declaringSymbolId", symbol.getId())
        ));

        List<SymbolInfoDTO> methods = new ArrayList<>();
        List<SymbolInfoDTO> fields = new ArrayList<>();
        for (NopCodeSymbol child : symbolDao.findAllByQuery(childQuery)) {
            SymbolInfoDTO info = new SymbolInfoDTO();
            info.setName(child.getName());
            info.setKind(child.getKind());
            info.setQualifiedName(child.getQualifiedName());
            info.setAccessModifier(child.getAccessModifier());

            String kind = child.getKind();
            if ("METHOD".equals(kind) || "CONSTRUCTOR".equals(kind)) {
                methods.add(info);
            } else if ("FIELD".equals(kind)) {
                fields.add(info);
            }
        }
        outline.setMethods(methods);
        outline.setFields(fields);
        return outline;
    }

    @Override
    public List<TypeOutlineDTO> batchGetTypeOutlines(String indexId, List<String> qualifiedNames) {
        return qualifiedNames.stream()
                .map(qn -> getTypeOutline(indexId, qn))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ==================== Hierarchy Queries ====================

    @Override
    public TypeHierarchyDTO getTypeHierarchy(String indexId, String qualifiedName,
                                             String direction, int maxDepth) {
        if (daoProvider == null) return null;

        SymbolTable table = rebuildSymbolTable(indexId);
        if (table == null) return null;

        CodeSymbol symbol = table.getByQualifiedName(qualifiedName);
        if (symbol == null) return null;

        IEntityDao<NopCodeInheritance> inhDao = daoProvider.daoFor(NopCodeInheritance.class);
        QueryBean inhQuery = new QueryBean();
        inhQuery.addFilter(FilterBeans.eq("indexId", indexId));
        List<CodeInheritance> allInheritances = inhDao.findAllByQuery(inhQuery).stream()
                .map(this::entityToInheritance)
                .collect(Collectors.toList());

        return buildTypeHierarchy(qualifiedName, direction, maxDepth, table, allInheritances);
    }

    private TypeHierarchyDTO buildTypeHierarchy(String qualifiedName, String direction, int maxDepth,
                                                SymbolTable table, List<CodeInheritance> allInheritances) {
        CodeSymbol symbol = table.getByQualifiedName(qualifiedName);
        TypeHierarchyDTO node = new TypeHierarchyDTO();

        SymbolInfoDTO symbolInfo = new SymbolInfoDTO();
        if (symbol != null) {
            symbolInfo.setName(symbol.getName());
            symbolInfo.setQualifiedName(symbol.getQualifiedName());
            symbolInfo.setKind(symbol.getKind().name());
        } else {
            symbolInfo.setQualifiedName(qualifiedName);
            symbolInfo.setName(qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1));
        }
        node.setSymbol(symbolInfo);

        if (maxDepth <= 0)
            return node;

        if ("super".equals(direction) || "both".equals(direction)) {
            List<TypeHierarchyDTO> superTypes = allInheritances.stream()
                    .filter(i -> symbol != null && symbol.getId().equals(i.getSubTypeId()))
                    .map(i -> buildTypeHierarchy(i.getSuperTypeQualifiedName(), direction, maxDepth - 1, table, allInheritances))
                    .collect(Collectors.toList());
            node.setSuperTypes(superTypes);
        }

        if ("sub".equals(direction) || "both".equals(direction)) {
            List<TypeHierarchyDTO> subTypes = allInheritances.stream()
                    .filter(i -> qualifiedName.equals(i.getSuperTypeQualifiedName()))
                    .map(i -> {
                        CodeSymbol subSymbol = table.getById(i.getSubTypeId());
                        if (subSymbol != null) {
                            return buildTypeHierarchy(subSymbol.getQualifiedName(), direction, maxDepth - 1, table, allInheritances);
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            node.setSubTypes(subTypes);
        }

        return node;
    }

    @Override
    public CallHierarchyDTO getCallHierarchy(String indexId, String qualifiedName,
                                             String direction, int maxDepth) {
        if (daoProvider == null) return null;

        CallGraph callGraph = rebuildCallGraph(indexId);
        SymbolTable table = rebuildSymbolTable(indexId);

        return buildCallHierarchy(qualifiedName, direction, maxDepth, callGraph, table);
    }

    private CallHierarchyDTO buildCallHierarchy(String qualifiedName, String direction, int maxDepth,
                                                CallGraph callGraph, SymbolTable table) {
        CodeSymbol symbol = table.getByQualifiedName(qualifiedName);
        CallHierarchyDTO node = new CallHierarchyDTO();

        SymbolInfoDTO symbolInfo = new SymbolInfoDTO();
        if (symbol != null) {
            symbolInfo.setName(symbol.getName());
            symbolInfo.setQualifiedName(symbol.getQualifiedName());
            symbolInfo.setKind(symbol.getKind().name());
        } else {
            symbolInfo.setQualifiedName(qualifiedName);
            symbolInfo.setName(qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1));
        }
        node.setSymbol(symbolInfo);

        if (maxDepth <= 0)
            return node;

        if ("outgoing".equals(direction) || "both".equals(direction)) {
            List<String> calleeIds = callGraph.getCallees(qualifiedName);
            if (calleeIds != null) {
                List<CallHierarchyDTO> callees = calleeIds.stream()
                        .map(callee -> buildCallHierarchy(callee, direction, maxDepth - 1, callGraph, table))
                        .collect(Collectors.toList());
                node.setCallees(callees);
            }
        }

        if ("incoming".equals(direction) || "both".equals(direction)) {
            List<String> callerIds = callGraph.getCallers(qualifiedName);
            if (callerIds != null) {
                List<CallHierarchyDTO> callers = callerIds.stream()
                        .map(caller -> buildCallHierarchy(caller, direction, maxDepth - 1, callGraph, table))
                        .collect(Collectors.toList());
                node.setCallers(callers);
            }
        }

        return node;
    }

    // ==================== Index Management ====================

    @Override
    public IndexStatsDTO getIndexStats(String indexId) {
        if (daoProvider == null) {
            IndexStatsDTO stats = new IndexStatsDTO();
            stats.setIndexId(indexId);
            return stats;
        }

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean symbolQuery = new QueryBean();
        symbolQuery.addFilter(FilterBeans.eq("indexId", indexId));
        List<NopCodeSymbol> allSymbols = symbolDao.findAllByQuery(symbolQuery);

        IndexStatsDTO stats = new IndexStatsDTO();
        stats.setIndexId(indexId);
        stats.setSymbolCount(allSymbols.size());

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean fileQuery = new QueryBean();
        fileQuery.addFilter(FilterBeans.eq("indexId", indexId));
        stats.setFileCount(fileDao.findAllByQuery(fileQuery).size());

        if (!allSymbols.isEmpty()) {
            Map<String, Integer> kindCounts = new LinkedHashMap<>();
            for (NopCodeSymbol s : allSymbols) {
                String kind = s.getKind();
                kindCounts.merge(kind != null ? kind : "UNKNOWN", 1, Integer::sum);
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

    @Override
    public void deleteIndex(String indexId) {
        ormTemplate.runInSession(session -> {
            try {
                IEntityDao<NopCodeAnnotationUsage> annotDao = daoProvider.daoFor(NopCodeAnnotationUsage.class);
                QueryBean annotQuery = new QueryBean();
                annotQuery.addFilter(FilterBeans.eq("indexId", indexId));
                annotDao.batchDeleteEntities(annotDao.findAllByQuery(annotQuery));

                IEntityDao<NopCodeInheritance> inhDao = daoProvider.daoFor(NopCodeInheritance.class);
                QueryBean inhQuery = new QueryBean();
                inhQuery.addFilter(FilterBeans.eq("indexId", indexId));
                inhDao.batchDeleteEntities(inhDao.findAllByQuery(inhQuery));

                IEntityDao<NopCodeCall> callDao = daoProvider.daoFor(NopCodeCall.class);
                QueryBean callQuery = new QueryBean();
                callQuery.addFilter(FilterBeans.eq("indexId", indexId));
                callDao.batchDeleteEntities(callDao.findAllByQuery(callQuery));

                IEntityDao<NopCodeSymbol> symDao = daoProvider.daoFor(NopCodeSymbol.class);
                QueryBean symQuery = new QueryBean();
                symQuery.addFilter(FilterBeans.eq("indexId", indexId));
                symDao.batchDeleteEntities(symDao.findAllByQuery(symQuery));

                IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
                QueryBean fileQuery = new QueryBean();
                fileQuery.addFilter(FilterBeans.eq("indexId", indexId));
                fileDao.batchDeleteEntities(fileDao.findAllByQuery(fileQuery));

                daoProvider.daoFor(NopCodeIndex.class).deleteEntityById(indexId);
            } catch (Exception e) {
                LOG.warn("Failed to cleanup DB records for index {}", indexId, e);
            }
            return null;
        });
    }

    // ==================== Graph Analysis ====================

    @Override
    public CommunityDetectionResultDTO detectCommunities(String indexId) {
        if (daoProvider == null) return null;

        CallGraph callGraph = rebuildCallGraph(indexId);
        SymbolTable symbolTable = rebuildSymbolTable(indexId);
        if (symbolTable.size() == 0)
            return null;

        CommunityDetector.CommunityDetectionResult result =
                CommunityDetector.detectCommunities(callGraph, symbolTable);

        return convertCommunityResult(result);
    }

    @Override
    public GraphAnalysisResultDTO getGraphAnalysis(String indexId, int topN) {
        if (daoProvider == null) return null;

        CallGraph callGraph = rebuildCallGraph(indexId);
        SymbolTable symbolTable = rebuildSymbolTable(indexId);

        int limit = topN > 0 ? topN : 20;

        List<EntryPointScorer.EntryPointScore> scores =
                EntryPointScorer.scoreEntryPoints(callGraph, symbolTable);

        List<GodNodeDTO> godNodes = scores.stream()
                .limit(limit)
                .map(this::toGodNode)
                .collect(Collectors.toList());

        List<String> isolatedSymbols = scores.stream()
                .filter(s -> s.getEntryPointType() == EntryPointScorer.EntryPointType.ISOLATED)
                .map(EntryPointScorer.EntryPointScore::getQualifiedName)
                .limit(limit)
                .collect(Collectors.toList());

        int extractedCount = 0;
        int inferredCount = 0;
        for (CodeSymbol symbol : symbolTable.getAll()) {
            String id = symbol.getId();
            if (!callGraph.getCallees(id).isEmpty() || !callGraph.getCallers(id).isEmpty()) {
                extractedCount++;
            } else {
                inferredCount++;
            }
        }

        int total = extractedCount + inferredCount;
        CohesionBreakdownDTO breakdown = new CohesionBreakdownDTO();
        breakdown.setExtractedCount(extractedCount);
        breakdown.setInferredCount(inferredCount);
        breakdown.setExtractedPercent(total > 0 ? (double) extractedCount / total * 100 : 0);
        breakdown.setInferredPercent(total > 0 ? (double) inferredCount / total * 100 : 0);

        GraphAnalysisResultDTO dto = new GraphAnalysisResultDTO();
        dto.setGodNodes(godNodes);
        dto.setCohesionBreakdown(breakdown);
        dto.setIsolatedSymbols(isolatedSymbols);
        return dto;
    }

    @Override
    public ImpactResultDTO getImpactAnalysis(String indexId, String symbolId, int depth) {
        if (daoProvider == null) return null;

        CallGraph callGraph = rebuildCallGraph(indexId);
        SymbolTable symbolTable = rebuildSymbolTable(indexId);

        int maxDepth = depth > 0 ? depth : 3;
        CodeSymbol symbol = symbolTable.getById(symbolId);
        String qualifiedName = symbol != null ? symbol.getQualifiedName() : symbolId;

        ImpactAnalyzer.ImpactResult result =
                ImpactAnalyzer.analyzeImpact(qualifiedName, callGraph, symbolTable, maxDepth);

        return convertImpactResult(result);
    }

    // ==================== Incremental Indexing ====================

    @Override
    public int triggerIncrementalIndex(String indexId, String vfsPath, String manifestPath) {
        return ormTemplate.runInSession(session -> {
            try {
                IncrementalDetector detector = new IncrementalDetector();

                // Load previous fingerprints from store
                List<FileFingerprint> previousFingerprints = fingerprintStore.loadFingerprints(indexId);

                IResourceLoader vfs = VirtualFileSystem.instance();
                List<IResource> currentResources = collectSourceResourcesFromVfs(vfs, vfsPath);

                List<Path> allFiles = currentResources.stream()
                        .map(res -> Path.of(res.getStdPath()))
                        .collect(Collectors.toList());

                ChangeSet changes = detector.detectChanges(previousFingerprints, allFiles);

                List<Path> changedFiles = changes.getAddedAndModified();
                List<Path> deletedFiles = changes.getDeletedFiles();

                LOG.info("Incremental index for {}: {} changed, {} deleted, {} unchanged",
                        indexId, changedFiles.size(), deletedFiles.size(),
                        changes.getUnchangedFiles().size());

                if (changedFiles.isEmpty() && deletedFiles.isEmpty()) {
                    return 0;
                }

                deleteFileRecords(indexId, deletedFiles);
                deleteFileRecords(indexId, changedFiles.stream()
                        .map(Path::toString).collect(Collectors.toList()));

                List<CodeFileAnalysisResult> changedResults = new ArrayList<>();
                for (Path file : changedFiles) {
                    try {
                        String relativePath = file.toString();
                        ICodeFileAnalyzer fileAnalyzer = registry.getAnalyzer(relativePath);
                        if (fileAnalyzer == null) continue;

                        IResource resource = vfs.getResource(file.toString());
                        if (resource == null) continue;
                        String sourceCode = resource.readText();
                        CodeFileAnalysisResult fileResult = fileAnalyzer.analyze(relativePath, sourceCode);
                        if (fileResult != null) changedResults.add(fileResult);
                    } catch (Exception e) {
                        LOG.warn("Failed to re-analyze file: {} - {}", file, e.getMessage());
                    }
                }

                for (CodeFileAnalysisResult fileResult : changedResults) {
                    persistSingleFileInSession(indexId, fileResult, session);
                }

                updateIndexStats(indexId);

                // Save new fingerprints to store
                List<FileFingerprint> newFingerprints = detector.computeFingerprints(allFiles);
                fingerprintStore.saveFingerprints(indexId, newFingerprints);

                return changedResults.size();
            } catch (IOException e) {
                throw new NopException(ERR_INCREMENTAL_FAILED).cause(e);
            }
        });
    }

    // ==================== File Page Query ====================

    @Override
    public PageBean<CodeFileAnalysisResult> findFilesPage(String indexId, String packageName, long offset, int limit) {
        PageBean<CodeFileAnalysisResult> pageBean = new PageBean<>();
        pageBean.setOffset(offset);
        pageBean.setLimit(limit);

        if (daoProvider == null) {
            pageBean.setTotal(0);
            pageBean.setItems(Collections.emptyList());
            return pageBean;
        }

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);

        QueryBean countQb = new QueryBean();
        countQb.addFilter(FilterBeans.eq("indexId", indexId));
        if (packageName != null && !packageName.isEmpty()) {
            countQb.addFilter(FilterBeans.eq("packageName", packageName));
        }

        long total = fileDao.countByQuery(countQb);
        pageBean.setTotal(total);

        QueryBean pageQb = new QueryBean();
        pageQb.setOffset(offset);
        pageQb.setLimit(limit > 0 ? limit : 20);
        pageQb.setFilter(countQb.getFilter());

        List<NopCodeFile> entities = fileDao.findPageByQuery(pageQb);
        pageBean.setItems(entities.stream()
                .map(this::entityToFileResult)
                .collect(Collectors.toList()));
        return pageBean;
    }

    // ==================== ORM Persistence ====================

    private void persistInSession(String indexId, ProjectAnalyzer.ProjectAnalysisResult result,
                                  IOrmSession session) {
        NopCodeIndex indexEntity = (NopCodeIndex) session.get(
                NopCodeIndex.class.getName(), indexId);
        if (indexEntity != null) {
            indexEntity.setName(indexId);
            indexEntity.setFileCount(result.getFileResults().size());
            indexEntity.setSymbolCount(result.getGlobalSymbolTable().size());
            indexEntity.setStatus("COMPLETED");
            indexEntity.setLastIndexed(System.currentTimeMillis());
        } else {
            indexEntity = (NopCodeIndex) ormTemplate.newEntity(NopCodeIndex.class.getName());
            indexEntity.setId(indexId);
            indexEntity.setName(indexId);
            indexEntity.setRootPath("");
            indexEntity.setLanguage("Java");
            indexEntity.setFileCount(result.getFileResults().size());
            indexEntity.setSymbolCount(result.getGlobalSymbolTable().size());
            indexEntity.setStatus("COMPLETED");
            indexEntity.setLastIndexed(System.currentTimeMillis());
            session.save(indexEntity);
        }

        BatchQueue<CodeFileAnalysisResult> queue = new BatchQueue<>(BATCH_SIZE, batch -> {
            LOG.debug("Flushed batch of {} file results for index {}", batch.size(), indexId);
        });

        for (CodeFileAnalysisResult file : result.getFileResults()) {
            saveFileResultInSession(indexId, file, session);
            queue.add(file);
        }
        queue.flush();
    }

    private void persistSingleFileInSession(String indexId, CodeFileAnalysisResult result,
                                            IOrmSession session) {
        saveFileResultInSession(indexId, result, session);
    }

    private void saveFileResultInSession(String indexId, CodeFileAnalysisResult file,
                                         IOrmSession session) {
        String fileEntityId = indexId + "_" + Math.abs(file.getFilePath().hashCode());

        NopCodeFile fileEntity = (NopCodeFile) ormTemplate.newEntity(NopCodeFile.class.getName());
        fileEntity.setId(fileEntityId);
        fileEntity.setIndexId(indexId);
        fileEntity.setFilePath(file.getFilePath());
        fileEntity.setPackageName(file.getPackageName());
        fileEntity.setLanguage(file.getLanguage() != null ? file.getLanguage().name() : null);
        fileEntity.setLineCount(file.getLineCount());
        String sourceCode = file.getSourceCode();
        if (sourceCode != null) {
            fileEntity.setFileHash(sha256Hex(sourceCode.getBytes(StandardCharsets.UTF_8)));
            fileEntity.setFileSize((long) sourceCode.length());
        }
        fileEntity.setLastModified(System.currentTimeMillis());
        session.save(fileEntity);

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
                symEntity.setExtData(sym.getExtData());
                symEntity.setAsyncFlag(sym.isAsyncFlag());
                symEntity.setReadonlyFlag(sym.isReadonlyFlag());
                session.save(symEntity);
            }
        }

        if (file.getCalls() != null) {
            for (CodeMethodCall call : file.getCalls()) {
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
                session.save(callEntity);
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
                session.save(inhEntity);
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
                session.save(annotEntity);
            }
        }
    }

    // ==================== Incremental Indexing Helpers ====================

    private List<IResource> collectSourceResourcesFromVfs(IResourceLoader resourceLoader, String vfsPath) {
        List<String> allExtensions = new ArrayList<>();
        for (CodeLanguage lang : registry.getSupportedLanguages()) {
            ILanguageAdapter adapter = registry.getAdapter(lang);
            allExtensions.addAll(adapter.getFileExtensions());
        }

        List<IResource> result = new ArrayList<>();
        for (String ext : allExtensions) {
            Collection<? extends IResource> resources = resourceLoader.getAllResources(vfsPath, ext);
            result.addAll(resources);
        }
        return result;
    }

    private void deleteFileRecords(String indexId, List<?> filePaths) {
        if (daoProvider == null || filePaths.isEmpty()) return;

        for (Object pathObj : filePaths) {
            String filePath = pathObj instanceof Path ? ((Path) pathObj).toString() : pathObj.toString();
            String fileId = indexId + "_" + Math.abs(filePath.hashCode());

            deleteEntitiesByFilter(NopCodeAnnotationUsage.class, "fileId", fileId);
            deleteEntitiesByFilter(NopCodeInheritance.class, "fileId", fileId);
            deleteEntitiesByFilter(NopCodeCall.class, "fileId", fileId);
            deleteEntitiesByFilter(NopCodeSymbol.class, "fileId", fileId);

            IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
            QueryBean q = new QueryBean();
            q.addFilter(FilterBeans.eq("indexId", indexId));
            q.addFilter(FilterBeans.eq("id", fileId));
            fileDao.batchDeleteEntities(fileDao.findAllByQuery(q));
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
                index.setLastIndexed(System.currentTimeMillis());
            }
        } catch (Exception e) {
            LOG.warn("Failed to update index stats for {}", indexId, e);
        }
    }

    // ==================== Private Conversion Helpers ====================

    private CommunityDetectionResultDTO convertCommunityResult(
            CommunityDetector.CommunityDetectionResult result) {
        CommunityDetectionResultDTO dto = new CommunityDetectionResultDTO();
        dto.setTotalSymbols(result.getTotalSymbols());
        dto.setTotalCommunities(result.getTotalCommunities());
        dto.setAverageCohesion(result.getAverageCohesion());
        dto.setAlgorithmUsed(result.getAlgorithmUsed() != null
                ? result.getAlgorithmUsed().name() : null);
        dto.setModularity(result.getModularity());
        dto.setProcessingTimeMs(result.getProcessingTimeMs());

        List<CommunityDTO> communities = new ArrayList<>();
        for (CommunityDetector.Community community : result.getCommunities()) {
            CommunityDTO c = new CommunityDTO();
            c.setId(community.getId());
            c.setLabel(community.getLabel());
            c.setSymbolIds(community.getSymbolIds());
            c.setSymbolCount(community.getSymbolCount());
            c.setCohesion(community.getCohesion());
            c.setDominantPackage(community.getDominantPackage());
            communities.add(c);
        }
        dto.setCommunities(communities);
        return dto;
    }

    private GodNodeDTO toGodNode(EntryPointScorer.EntryPointScore score) {
        GodNodeDTO node = new GodNodeDTO();
        node.setSymbolId(score.getSymbolId());
        node.setQualifiedName(score.getQualifiedName());
        node.setKind(score.getKind() != null ? score.getKind().name() : null);
        node.setDegree(score.getCallerCount() + score.getCalleeCount());
        node.setCallerCount(score.getCallerCount());
        node.setCalleeCount(score.getCalleeCount());
        return node;
    }

    private ImpactResultDTO convertImpactResult(ImpactAnalyzer.ImpactResult result) {
        ImpactResultDTO dto = new ImpactResultDTO();
        dto.setTargetSymbolId(result.getTargetSymbolId());
        dto.setTargetQualifiedName(result.getTargetQualifiedName());
        dto.setRiskLevel(result.getRiskLevel());

        dto.setUpstream(result.getUpstream().stream()
                .map(this::toImpactedSymbol)
                .collect(Collectors.toList()));

        dto.setDownstream(result.getDownstream().stream()
                .map(this::toImpactedSymbol)
                .collect(Collectors.toList()));
        return dto;
    }

    private ImpactedSymbolDTO toImpactedSymbol(ImpactAnalyzer.ImpactedSymbol symbol) {
        ImpactedSymbolDTO dto = new ImpactedSymbolDTO();
        dto.setSymbolId(symbol.getSymbolId());
        dto.setQualifiedName(symbol.getQualifiedName());
        dto.setName(symbol.getName());
        dto.setKind(symbol.getKind() != null ? symbol.getKind().name() : null);
        dto.setDepth(symbol.getDepth());
        dto.setFilePath(symbol.getFilePath());
        return dto;
    }

    // ==================== Batch File Records ====================

    @Override
    public void batchSaveFileRecords(String indexId, List<FileFingerprint> fingerprints) {
        if (daoProvider == null || fingerprints == null || fingerprints.isEmpty()) return;

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);

        for (FileFingerprint fp : fingerprints) {
            String fileId = indexId + "_" + Math.abs(fp.getFilePath().hashCode());

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

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            char[] hexChars = "0123456789abcdef".toCharArray();
            for (byte b : hash) {
                sb.append(hexChars[(b >> 4) & 0x0f]);
                sb.append(hexChars[b & 0x0f]);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
