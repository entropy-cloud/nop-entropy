package io.nop.code.service.impl;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.code.core.NopCodeCoreErrors;
import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.analyzer.CommunityDetector;
import io.nop.code.core.analyzer.EntryPointScorer;
import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.analyzer.ImpactAnalyzer;
import io.nop.code.core.analyzer.ILanguageAdapter;
import io.nop.code.core.analyzer.ProjectAnalysisResult;
import io.nop.code.core.analyzer.ProjectAnalyzer;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.util.DigestHelper;
import io.nop.code.core.incremental.ChangeSet;
import io.nop.code.core.incremental.FileFingerprint;
import io.nop.code.core.incremental.IFingerprintStore;
import io.nop.code.core.incremental.IncrementalDetector;
import io.nop.code.service.incremental.OrmFingerprintStore;
import io.nop.code.core.model.*;
import io.nop.code.core.resolver.IImportResolver;
import io.nop.code.core.resolver.JavaImportResolver;
import io.nop.code.core.resolver.PythonImportResolver;
import io.nop.code.core.resolver.TypeScriptImportResolver;
import io.nop.code.dao.entity.NopCodeAnnotationUsage;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.dao.entity.NopCodeDependency;
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
import java.util.*;
import java.util.stream.Collectors;

import static io.nop.code.service.NopCodeErrors.*;

public class CodeIndexService implements ICodeIndexService {

    private static final Logger LOG = LoggerFactory.getLogger(CodeIndexService.class);

    private static final int BATCH_SIZE = 1000;

    protected final LanguageAdapterRegistry registry;
    protected final ProjectAnalyzer analyzer;
    protected final Map<String, IImportResolver> importResolvers = new HashMap<>();

    @Inject
    protected IDaoProvider daoProvider;

    @Inject
    protected IOrmTemplate ormTemplate;

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
        this.analyzer = new ProjectAnalyzer(registry);
        registerImportResolvers();
    }

    public CodeIndexService(LanguageAdapterRegistry registry, ProjectAnalyzer analyzer) {
        this.registry = registry;
        this.analyzer = analyzer;
        registerImportResolvers();
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
        symbol.setRawReturnType(entity.getRawReturnType());
        symbol.setRawFieldType(entity.getRawFieldType());
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
            ensureIndexEntity(indexId, vfsPath, session);

            java.io.File localFile = new java.io.File(vfsPath);
            if (localFile.isDirectory()) {
                ProjectAnalysisResult result = analyzer.analyzeProject(localFile.toPath());
                persistInSession(indexId, vfsPath, result, session);
                return result.getFileResults().size();
            } else {
                int[] count = {0};
                ProjectAnalysisResult result = analyzer.analyzeProject(
                        VirtualFileSystem.instance(), vfsPath, filePattern,
                        batch -> {
                            for (CodeFileAnalysisResult fileResult : batch) {
                                saveFileResultInSession(indexId, fileResult, session);
                                count[0]++;
                            }
                        });
                updateIndexStats(indexId, result);
                return result.getFileResults().size();
            }
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
    public FileOutlineDTO getFileOutline(String indexId, String filePath) {
        CodeFileAnalysisResult file = getFile(indexId, filePath);
        if (file == null) return null;

        List<CodeSymbol> symbols = getFileSymbols(indexId, filePath);

        List<CodeSymbol> typeSymbols = symbols.stream()
                .filter(s -> s.getKind() == CodeSymbolKind.CLASS
                        || s.getKind() == CodeSymbolKind.INTERFACE
                        || s.getKind() == CodeSymbolKind.ENUM
                        || s.getKind() == CodeSymbolKind.ANNOTATION_TYPE)
                .collect(Collectors.toList());

        List<SymbolInfoDTO> types = new ArrayList<>();
        for (CodeSymbol type : typeSymbols) {
            types.add(toSymbolInfoDTO(type));
        }

        FileOutlineDTO outline = new FileOutlineDTO();
        outline.setFilePath(file.getFilePath());
        outline.setPackageName(file.getPackageName());
        outline.setImports(file.getImports());
        outline.setLineCount(file.getLineCount());
        outline.setTypes(types);
        return outline;
    }

    private SymbolInfoDTO toSymbolInfoDTO(CodeSymbol symbol) {
        SymbolInfoDTO dto = new SymbolInfoDTO();
        dto.setName(symbol.getName());
        dto.setQualifiedName(symbol.getQualifiedName());
        dto.setKind(symbol.getKind() != null ? symbol.getKind().name() : null);
        dto.setAccessModifier(symbol.getAccessModifier() != null ? symbol.getAccessModifier().name() : null);
        return dto;
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

    @Override
    public List<ModuleDigestDTO> getModuleDigest(String indexId, String dirPath, boolean includePrivate) {
        if (daoProvider == null) return Collections.emptyList();

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean fileQuery = new QueryBean();
        fileQuery.addFilter(FilterBeans.eq("indexId", indexId));
        if (dirPath != null && !dirPath.isEmpty()) {
            fileQuery.addFilter(FilterBeans.startsWith("filePath", dirPath));
        }
        List<NopCodeFile> files = fileDao.findAllByQuery(fileQuery);

        Set<String> allowedKinds = new HashSet<>(Arrays.asList(
                "CLASS", "INTERFACE", "ENUM", "ANNOTATION_TYPE", "METHOD", "FUNCTION"));

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);

        List<ModuleDigestDTO> result = new ArrayList<>();
        for (NopCodeFile file : files) {
            String fileId = file.getId();

            QueryBean symQuery = new QueryBean();
            symQuery.addFilter(FilterBeans.eq("indexId", indexId));
            symQuery.addFilter(FilterBeans.eq("fileId", fileId));
            if (!includePrivate) {
                symQuery.addFilter(FilterBeans.ne("accessModifier", "PRIVATE"));
            }

            List<SymbolInfoDTO> symbols = new ArrayList<>();
            for (NopCodeSymbol sym : symbolDao.findAllByQuery(symQuery)) {
                if (!allowedKinds.contains(sym.getKind())) continue;
                SymbolInfoDTO info = new SymbolInfoDTO();
                info.setName(sym.getName());
                info.setQualifiedName(sym.getQualifiedName());
                info.setKind(sym.getKind());
                info.setAccessModifier(sym.getAccessModifier());
                symbols.add(info);
            }

            ModuleDigestDTO dto = new ModuleDigestDTO();
            dto.setFilePath(file.getFilePath());
            dto.setPackageName(file.getPackageName());
            dto.setSymbols(symbols);
            result.add(dto);
        }

        result.sort(Comparator.comparing(ModuleDigestDTO::getFilePath));
        return result;
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
    public List<CodeSearchResultDTO> searchCode(String indexId, String query, String searchType,
                                                 String language, String filePattern, int limit) {
        if (daoProvider == null || query == null || query.isEmpty()) return Collections.emptyList();

        String type = searchType != null ? searchType : "COMBINED";
        int lim = limit > 0 ? limit : 50;

        switch (type) {
            case "SYMBOL_NAME":
                return searchBySymbolName(indexId, query, language, filePattern, lim);
            case "FULL_TEXT":
                return searchFullText(indexId, query, language, filePattern, lim);
            case "COMBINED":
            default:
                return searchCombined(indexId, query, language, filePattern, lim);
        }
    }

    private List<CodeSearchResultDTO> searchBySymbolName(String indexId, String query,
                                                          String language, String filePattern, int limit) {
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean qb = new QueryBean();
        qb.addFilter(FilterBeans.eq("indexId", indexId));
        TreeBean nameFilter = FilterBeans.contains("name", query);
        TreeBean qnFilter = FilterBeans.contains("qualifiedName", query);
        qb.addFilter(FilterBeans.or(nameFilter, qnFilter));
        if (language != null && !language.isEmpty()) {
            qb.addFilter(FilterBeans.eq("kind", language));
        }
        qb.setLimit(limit * 2);

        List<NopCodeSymbol> symbols = symbolDao.findAllByQuery(qb);
        Map<String, String> filePathCache = buildFilePathCache(indexId);

        List<CodeSearchResultDTO> results = new ArrayList<>();
        for (NopCodeSymbol sym : symbols) {
            CodeSearchResultDTO dto = toSearchResult(sym, filePathCache, "SYMBOL_NAME");
            dto.setScore(scoreSymbolNameMatch(query, sym.getName(), sym.getQualifiedName()));
            results.add(dto);
        }

        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (results.size() > limit) results = results.subList(0, limit);

        return filterByFilePattern(results, filePattern);
    }

    private List<CodeSearchResultDTO> searchFullText(String indexId, String query,
                                                      String language, String filePattern, int limit) {
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean qb = new QueryBean();
        qb.addFilter(FilterBeans.eq("indexId", indexId));
        TreeBean sigFilter = FilterBeans.contains("signature", query);
        TreeBean docFilter = FilterBeans.contains("documentation", query);
        qb.addFilter(FilterBeans.or(sigFilter, docFilter));
        qb.setLimit(limit * 2);

        List<NopCodeSymbol> symbols = symbolDao.findAllByQuery(qb);
        Map<String, String> filePathCache = buildFilePathCache(indexId);

        List<CodeSearchResultDTO> results = new ArrayList<>();
        for (NopCodeSymbol sym : symbols) {
            CodeSearchResultDTO dto = toSearchResult(sym, filePathCache, "FULL_TEXT");
            dto.setScore(scoreFullTextMatch(query, sym.getSignature(), sym.getDocumentation()));
            results.add(dto);
        }

        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (results.size() > limit) results = results.subList(0, limit);

        return filterByFilePattern(results, filePattern);
    }

    private List<CodeSearchResultDTO> searchCombined(String indexId, String query,
                                                      String language, String filePattern, int limit) {
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean qb = new QueryBean();
        qb.addFilter(FilterBeans.eq("indexId", indexId));

        TreeBean nameFilter = FilterBeans.contains("name", query);
        TreeBean qnFilter = FilterBeans.contains("qualifiedName", query);
        TreeBean sigFilter = FilterBeans.contains("signature", query);
        TreeBean docFilter = FilterBeans.contains("documentation", query);
        qb.addFilter(FilterBeans.or(nameFilter, qnFilter, sigFilter, docFilter));
        qb.setLimit(limit * 3);

        List<NopCodeSymbol> symbols = symbolDao.findAllByQuery(qb);
        Map<String, String> filePathCache = buildFilePathCache(indexId);

        Set<String> seen = new HashSet<>();
        List<CodeSearchResultDTO> results = new ArrayList<>();
        for (NopCodeSymbol sym : symbols) {
            String dedupeKey = sym.getId();
            if (seen.contains(dedupeKey)) continue;
            seen.add(dedupeKey);

            CodeSearchResultDTO dto = toSearchResult(sym, filePathCache, "COMBINED");
            dto.setScore(scoreCombined(query, sym.getName(), sym.getQualifiedName(),
                    sym.getSignature(), sym.getDocumentation()));
            results.add(dto);
        }

        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (results.size() > limit) results = results.subList(0, limit);

        return filterByFilePattern(results, filePattern);
    }

    private Map<String, String> buildFilePathCache(String indexId) {
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean fq = new QueryBean();
        fq.addFilter(FilterBeans.eq("indexId", indexId));
        List<NopCodeFile> files = fileDao.findAllByQuery(fq);
        Map<String, String> cache = new HashMap<>();
        for (NopCodeFile f : files) {
            cache.put(f.getId(), f.getFilePath());
        }
        return cache;
    }

    private CodeSearchResultDTO toSearchResult(NopCodeSymbol sym, Map<String, String> filePathCache,
                                                String matchType) {
        CodeSearchResultDTO dto = new CodeSearchResultDTO();
        dto.setMatchedSymbolName(sym.getName());
        dto.setMatchedQualifiedName(sym.getQualifiedName());
        dto.setMatchType(matchType);
        dto.setLine(sym.getLine() != null ? sym.getLine() : 0);
        dto.setFilePath(filePathCache.getOrDefault(sym.getFileId(), ""));
        dto.setContext(sym.getSignature());
        return dto;
    }

    private double scoreSymbolNameMatch(String query, String name, String qualifiedName) {
        if (name != null && name.equals(query)) return 1.0;
        if (name != null && name.startsWith(query)) return 0.8;
        if (name != null && name.contains(query)) return 0.6;
        if (qualifiedName != null && qualifiedName.contains(query)) return 0.5;
        return 0.1;
    }

    private double scoreFullTextMatch(String query, String signature, String documentation) {
        boolean sigMatch = signature != null && signature.contains(query);
        boolean docMatch = documentation != null && documentation.contains(query);
        if (sigMatch && docMatch) return 0.5;
        if (sigMatch) return 0.4;
        if (docMatch) return 0.3;
        return 0.1;
    }

    private double scoreCombined(String query, String name, String qualifiedName,
                                  String signature, String documentation) {
        if (name != null && name.equals(query)) return 1.0;
        if (name != null && name.startsWith(query)) return 0.8;
        if (name != null && name.contains(query)) return 0.6;
        if (qualifiedName != null && qualifiedName.contains(query)) return 0.5;
        boolean sigMatch = signature != null && signature.contains(query);
        boolean docMatch = documentation != null && documentation.contains(query);
        if (sigMatch) return 0.3;
        if (docMatch) return 0.3;
        return 0.1;
    }

    private List<CodeSearchResultDTO> filterByFilePattern(List<CodeSearchResultDTO> results, String filePattern) {
        if (filePattern == null || filePattern.isEmpty()) return results;
        String pattern = filePattern.replace("*", ".*").replace("?", ".");
        return results.stream()
                .filter(r -> r.getFilePath() != null && r.getFilePath().matches(pattern))
                .collect(Collectors.toList());
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

    @Override
    public SymbolSourceDTO showSymbolSource(String indexId, String qualifiedName, boolean includeBody) {
        if (daoProvider == null) return null;

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addFilter(FilterBeans.eq("qualifiedName", qualifiedName));
        List<NopCodeSymbol> results = symbolDao.findAllByQuery(query);
        if (results.isEmpty()) return null;

        NopCodeSymbol entity = results.get(0);

        SymbolSourceDTO dto = new SymbolSourceDTO();
        dto.setQualifiedName(entity.getQualifiedName());
        dto.setStartLine(entity.getLine() != null ? entity.getLine() : 0);
        dto.setEndLine(entity.getEndLine() != null ? entity.getEndLine() : 0);
        dto.setSignature(entity.getSignature());

        String fileId = entity.getFileId();
        if (fileId != null) {
            IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
            NopCodeFile file = fileDao.getEntityById(fileId);
            if (file != null) {
                dto.setFilePath(file.getFilePath());
            }
        }

        // Source code not stored in DB — future SourceCodeProvider will populate this
        dto.setSourceCode(null);

        return dto;
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
                new CommunityDetector().detectCommunities(callGraph, symbolTable);

        return convertCommunityResult(result);
    }

    @Override
    public GraphAnalysisResultDTO getGraphAnalysis(String indexId, int topN) {
        if (daoProvider == null) return null;

        CallGraph callGraph = rebuildCallGraph(indexId);
        SymbolTable symbolTable = rebuildSymbolTable(indexId);

        int limit = topN > 0 ? topN : 20;

        List<EntryPointScorer.EntryPointScore> scores =
                new EntryPointScorer().scoreEntryPoints(callGraph, symbolTable);

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
                new ImpactAnalyzer().analyzeImpact(qualifiedName, callGraph, symbolTable, maxDepth);

        return convertImpactResult(result);
    }

    // ==================== Dependency Graph ====================

    @Override
    public DepGraphDTO getDeps(String indexId, String filePath, int depth) {
        if (daoProvider == null) return new DepGraphDTO();
        Map<String, List<DepEdgeDTO>> adj = buildForwardAdjacency(indexId);
        Set<String> visited = new HashSet<>();
        List<DepEdgeDTO> resultEdges = new ArrayList<>();
        bfsCollect(filePath, adj, depth, visited, resultEdges);
        return buildGraphFromEdges(resultEdges);
    }

    @Override
    public DepGraphDTO getReverseDeps(String indexId, String filePath, int depth, int limit) {
        if (daoProvider == null) return new DepGraphDTO();
        Map<String, List<DepEdgeDTO>> adj = buildReverseAdjacency(indexId);
        Set<String> visited = new HashSet<>();
        List<DepEdgeDTO> resultEdges = new ArrayList<>();
        bfsCollect(filePath, adj, depth, visited, resultEdges);
        if (limit > 0 && resultEdges.size() > limit) {
            resultEdges = resultEdges.subList(0, limit);
        }
        return buildGraphFromEdges(resultEdges);
    }

    @Override
    public List<List<String>> findCycles(String indexId, int minSize) {
        if (daoProvider == null) return Collections.emptyList();
        Map<String, List<String>> adj = buildForwardStringAdjacency(indexId);
        List<List<String>> sccs = tarjanSCC(adj);
        int min = minSize > 0 ? minSize : 2;
        sccs.removeIf(scc -> scc.size() < min);
        return sccs;
    }

    @Override
    public DepGraphDTO getDepGraph(String indexId, boolean includeExternal) {
        if (daoProvider == null) return new DepGraphDTO();
        IEntityDao<NopCodeDependency> dao = daoProvider.daoFor(NopCodeDependency.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq("indexId", indexId));
        List<NopCodeDependency> deps = dao.findAllByQuery(q);

        List<DepEdgeDTO> edges = new ArrayList<>();
        Map<String, int[]> degreeMap = new LinkedHashMap<>();
        for (NopCodeDependency dep : deps) {
            if (!includeExternal && !Boolean.TRUE.equals(dep.getResolved() != null && dep.getResolved() == 1)) {
                continue;
            }
            String src = dep.getSourceFilePath();
            String tgt = dep.getTargetFilePath();
            if (src == null || tgt == null) continue;

            DepEdgeDTO edge = new DepEdgeDTO();
            edge.setSource(src);
            edge.setTarget(tgt);
            edge.setImportStatement(dep.getImportStatement());
            edge.setResolved(dep.getResolved() != null && dep.getResolved() == 1);
            edges.add(edge);

            int[] srcDeg = degreeMap.computeIfAbsent(src, k -> new int[2]);
            srcDeg[1]++;
            int[] tgtDeg = degreeMap.computeIfAbsent(tgt, k -> new int[2]);
            tgtDeg[0]++;
        }

        List<DepNodeDTO> nodes = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : degreeMap.entrySet()) {
            DepNodeDTO node = new DepNodeDTO();
            node.setFilePath(entry.getKey());
            node.setInDegree(entry.getValue()[0]);
            node.setOutDegree(entry.getValue()[1]);
            nodes.add(node);
        }

        DepGraphDTO graph = new DepGraphDTO();
        graph.setNodes(nodes);
        graph.setEdges(edges);
        return graph;
    }

    private Map<String, List<DepEdgeDTO>> buildForwardAdjacency(String indexId) {
        IEntityDao<NopCodeDependency> dao = daoProvider.daoFor(NopCodeDependency.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq("indexId", indexId));
        List<NopCodeDependency> deps = dao.findAllByQuery(q);

        Map<String, List<DepEdgeDTO>> adj = new HashMap<>();
        for (NopCodeDependency dep : deps) {
            if (dep.getSourceFilePath() == null || dep.getTargetFilePath() == null) continue;
            DepEdgeDTO edge = new DepEdgeDTO();
            edge.setSource(dep.getSourceFilePath());
            edge.setTarget(dep.getTargetFilePath());
            edge.setImportStatement(dep.getImportStatement());
            edge.setResolved(dep.getResolved() != null && dep.getResolved() == 1);
            adj.computeIfAbsent(dep.getSourceFilePath(), k -> new ArrayList<>()).add(edge);
        }
        return adj;
    }

    private Map<String, List<DepEdgeDTO>> buildReverseAdjacency(String indexId) {
        IEntityDao<NopCodeDependency> dao = daoProvider.daoFor(NopCodeDependency.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq("indexId", indexId));
        List<NopCodeDependency> deps = dao.findAllByQuery(q);

        Map<String, List<DepEdgeDTO>> adj = new HashMap<>();
        for (NopCodeDependency dep : deps) {
            if (dep.getSourceFilePath() == null || dep.getTargetFilePath() == null) continue;
            DepEdgeDTO edge = new DepEdgeDTO();
            edge.setSource(dep.getSourceFilePath());
            edge.setTarget(dep.getTargetFilePath());
            edge.setImportStatement(dep.getImportStatement());
            edge.setResolved(dep.getResolved() != null && dep.getResolved() == 1);
            adj.computeIfAbsent(dep.getTargetFilePath(), k -> new ArrayList<>()).add(edge);
        }
        return adj;
    }

    private Map<String, List<String>> buildForwardStringAdjacency(String indexId) {
        IEntityDao<NopCodeDependency> dao = daoProvider.daoFor(NopCodeDependency.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq("indexId", indexId));
        List<NopCodeDependency> deps = dao.findAllByQuery(q);

        Map<String, List<String>> adj = new HashMap<>();
        for (NopCodeDependency dep : deps) {
            if (dep.getSourceFilePath() == null || dep.getTargetFilePath() == null) continue;
            adj.computeIfAbsent(dep.getSourceFilePath(), k -> new ArrayList<>())
                    .add(dep.getTargetFilePath());
        }
        return adj;
    }

    private void bfsCollect(String start, Map<String, List<DepEdgeDTO>> adj, int maxDepth,
                            Set<String> visited, List<DepEdgeDTO> result) {
        Queue<String[]> queue = new LinkedList<>();
        queue.add(new String[]{start, "0"});
        visited.add(start);
        while (!queue.isEmpty()) {
            String[] current = queue.poll();
            String node = current[0];
            int d = Integer.parseInt(current[1]);
            if (d >= maxDepth) continue;
            List<DepEdgeDTO> edges = adj.getOrDefault(node, Collections.emptyList());
            for (DepEdgeDTO edge : edges) {
                result.add(edge);
                if (!visited.contains(edge.getTarget())) {
                    visited.add(edge.getTarget());
                    queue.add(new String[]{edge.getTarget(), String.valueOf(d + 1)});
                }
            }
        }
    }

    private DepGraphDTO buildGraphFromEdges(List<DepEdgeDTO> edges) {
        Map<String, int[]> degreeMap = new LinkedHashMap<>();
        for (DepEdgeDTO edge : edges) {
            int[] srcDeg = degreeMap.computeIfAbsent(edge.getSource(), k -> new int[2]);
            srcDeg[1]++;
            int[] tgtDeg = degreeMap.computeIfAbsent(edge.getTarget(), k -> new int[2]);
            tgtDeg[0]++;
        }

        List<DepNodeDTO> nodes = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : degreeMap.entrySet()) {
            DepNodeDTO node = new DepNodeDTO();
            node.setFilePath(entry.getKey());
            node.setInDegree(entry.getValue()[0]);
            node.setOutDegree(entry.getValue()[1]);
            nodes.add(node);
        }

        DepGraphDTO graph = new DepGraphDTO();
        graph.setNodes(nodes);
        graph.setEdges(edges);
        return graph;
    }

    private List<List<String>> tarjanSCC(Map<String, List<String>> adj) {
        List<List<String>> result = new ArrayList<>();
        int[] index = {0};
        Map<String, Integer> nodeIndex = new HashMap<>();
        Map<String, Integer> lowLink = new HashMap<>();
        Map<String, Boolean> onStack = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();

        Set<String> allNodes = new LinkedHashSet<>(adj.keySet());
        for (List<String> targets : adj.values()) {
            allNodes.addAll(targets);
        }

        for (String node : allNodes) {
            if (!nodeIndex.containsKey(node)) {
                tarjanDFS(node, adj, index, nodeIndex, lowLink, onStack, stack, result);
            }
        }
        return result;
    }

    private void tarjanDFS(String v, Map<String, List<String>> adj, int[] index,
                           Map<String, Integer> nodeIndex, Map<String, Integer> lowLink,
                           Map<String, Boolean> onStack, Deque<String> stack,
                           List<List<String>> result) {
        nodeIndex.put(v, index[0]);
        lowLink.put(v, index[0]);
        index[0]++;
        stack.push(v);
        onStack.put(v, true);

        for (String w : adj.getOrDefault(v, Collections.emptyList())) {
            if (!nodeIndex.containsKey(w)) {
                tarjanDFS(w, adj, index, nodeIndex, lowLink, onStack, stack, result);
                lowLink.put(v, Math.min(lowLink.get(v), lowLink.get(w)));
            } else if (Boolean.TRUE.equals(onStack.get(w))) {
                lowLink.put(v, Math.min(lowLink.get(v), nodeIndex.get(w)));
            }
        }

        if (lowLink.get(v).equals(nodeIndex.get(v))) {
            List<String> scc = new ArrayList<>();
            String w;
            do {
                w = stack.pop();
                onStack.put(w, false);
                scc.add(w);
            } while (!w.equals(v));
            result.add(scc);
        }
    }

    // ==================== Incremental Indexing ====================

    @Override
    public int triggerIncrementalIndex(String indexId, String vfsPath, String manifestPath) {
        return ormTemplate.runInSession(session -> {
            try {
                IncrementalDetector detector = new IncrementalDetector();

                // Load previous fingerprints from store
                List<FileFingerprint> previousFingerprints = getFingerprintStore().loadFingerprints(indexId);

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
                getFingerprintStore().saveFingerprints(indexId, newFingerprints);

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
            indexEntity.setLastIndexed(System.currentTimeMillis());
        } else {
            indexEntity = (NopCodeIndex) ormTemplate.newEntity(NopCodeIndex.class.getName());
            indexEntity.setId(indexId);
            indexEntity.setName(indexId);
            indexEntity.setRootPath(rootPath != null ? rootPath : "/");
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
            indexEntity.setLastIndexed(System.currentTimeMillis());
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
            indexEntity.setLastIndexed(System.currentTimeMillis());
        }
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
            fileEntity.setFileHash(DigestHelper.sha256Hex(sourceCode.getBytes(StandardCharsets.UTF_8)));
            fileEntity.setFileSize((long) sourceCode.length());
        }
        if (sourceCode != null) {
            fileEntity.setSourceCode(sourceCode);
        }
        if (file.getImports() != null && !file.getImports().isEmpty()) {
            fileEntity.setImports(String.join("\n", file.getImports()));
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
                symEntity.setRawReturnType(sym.getRawReturnType());
                symEntity.setRawFieldType(sym.getRawFieldType());
                symEntity.setExtData(sym.getExtData());
                symEntity.setAsyncFlag(sym.isAsyncFlag());
                symEntity.setReadonlyFlag(sym.isReadonlyFlag());
                session.save(symEntity);
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

        if (file.getImports() != null && !file.getImports().isEmpty() && file.getLanguage() != null) {
            IImportResolver resolver = importResolvers.get(file.getLanguage().name());
            if (resolver != null) {
                Set<String> projectFiles = getProjectFilePaths(indexId);
                List<CodeFileDependency> deps = resolver.resolveImports(
                        file.getFilePath(), file.getImports(), projectFiles);
                for (CodeFileDependency dep : deps) {
                    NopCodeDependency depEntity = (NopCodeDependency) ormTemplate.newEntity(
                            NopCodeDependency.class.getName());
                    String depId = indexId + "_" + Math.abs(
                            (dep.getSourceFilePath() + "|" + dep.getImportStatement()).hashCode());
                    depEntity.setDepId(depId);
                    depEntity.setIndexId(indexId);
                    depEntity.setSourceFilePath(dep.getSourceFilePath());
                    depEntity.setTargetFilePath(dep.getTargetFilePath());
                    depEntity.setImportStatement(dep.getImportStatement());
                    depEntity.setResolved(dep.isResolved() ? 1 : 0);
                    session.save(depEntity);
                }
            }
        }
    }

    // ==================== Incremental Indexing Helpers ====================

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
            deleteEntitiesByFilter(NopCodeDependency.class, "sourceFilePath", filePath);

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
}
