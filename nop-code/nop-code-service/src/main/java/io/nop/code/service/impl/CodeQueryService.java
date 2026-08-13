package io.nop.code.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.query.QueryFieldBean;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.*;
import io.nop.code.core.util.BfsNode;
import io.nop.code.core.util.DigestHelper;
import io.nop.code.dao.entity.NopCodeAnnotationUsage;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.code.dao.entity.NopCodeInheritance;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.dao.entity.NopCodeUsage;
import io.nop.code.api.dto.*;
import io.nop.code.service.util.CodeSymbolConverter;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.core.lang.json.JsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
class CodeQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(CodeQueryService.class);

    // WP-6 AR-136/168/177: surface silent truncation of single-shot capped queries so downstream
    // consumers know the result may be incomplete (size == cap ⇒ possibly more rows exist).
    static boolean isCapped(int resultSize, int cap) {
        return cap > 0 && resultSize >= cap;
    }

    private static void warnIfCapped(int resultSize, int cap, String context) {
        if (isCapped(resultSize, cap)) {
            LOG.warn("Query result capped at {} for {}; result may be incomplete (more rows may exist)",
                    cap, context);
        }
    }

    // WP-8 AR-160/162/165: package filter must respect package boundaries — "com.example" must
    // match "com.example.Foo" and "com.example.sub.Bar" but NOT the sibling "com.exampleFoo.Bar".
    // startsWith(packageName) is a plain prefix test that leaks across the boundary, so append a
    // dot separator.
    private static String packagePrefix(String packageName) {
        return packageName.endsWith(".") ? packageName : packageName + ".";
    }

    private final IDaoProvider daoProvider;
    private final CodeCacheManager cacheManager;
    private final IOrmTemplate ormTemplate;

    CodeQueryService(IDaoProvider daoProvider, CodeCacheManager cacheManager, IOrmTemplate ormTemplate) {
        this.daoProvider = daoProvider;
        this.cacheManager = cacheManager;
        this.ormTemplate = ormTemplate;
    }

    private CodeFileAnalysisResult entityToFileResult(NopCodeFile entity) {
        CodeFileAnalysisResult result = new CodeFileAnalysisResult();
        result.setFilePath(entity.getFilePath());
        result.setPackageName(entity.getPackageName());
        result.setLanguage(entity.getLanguage() != null
                ? CodeLanguage.valueOf(entity.getLanguage()) : null);
        result.setLineCount(entity.getLineCount() != null ? entity.getLineCount() : 0);
        result.setSourceCode(entity.getSourceCode());
        String importsJson = entity.getImports();
        if (importsJson != null && !importsJson.isEmpty()) {
            Object parsed = JsonTool.parse(importsJson);
            if (parsed instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> importsList = (List<String>) parsed;
                result.setImports(importsList);
            }
        }
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

    private SymbolInfoDTO toSymbolInfoDTO(CodeSymbol symbol) {
        SymbolInfoDTO dto = new SymbolInfoDTO();
        dto.setName(symbol.getName());
        dto.setQualifiedName(symbol.getQualifiedName());
        dto.setKind(symbol.getKind() != null ? symbol.getKind().name() : null);
        dto.setAccessModifier(symbol.getAccessModifier() != null ? symbol.getAccessModifier().name() : null);
        return dto;
    }

    private String extractLines(String source, int startLine, int endLine) {
        if (source == null || startLine < 1 || endLine < startLine) return null;
        String[] lines = source.split("\n", -1);
        int start = Math.max(1, startLine) - 1;
        int end = Math.min(lines.length, endLine);
        if (start >= end) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) sb.append("\n");
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private String generateFileId(String indexId, String filePath) {
        return DigestHelper.sha256Hex((indexId + ":" + filePath).getBytes(StandardCharsets.UTF_8)).substring(0, 36);
    }

    List<CodeFileAnalysisResult> getFiles(String indexId) {
        if (daoProvider == null) return Collections.emptyList();
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
        List<NopCodeFile> files = fileDao.findAllByQuery(query);
        warnIfCapped(files.size(), CodeIndexService.MAX_QUERY_RESULTS, "getFiles:" + indexId);
        return files.stream()
                .map(this::entityToFileResult)
                .collect(Collectors.toList());
    }

    CodeFileAnalysisResult getFile(String indexId, String filePath) {
        if (daoProvider == null) return null;
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addFilter(FilterBeans.eq("filePath", filePath));
        query.setLimit(1);
        List<NopCodeFile> files = fileDao.findAllByQuery(query);
        return files.isEmpty() ? null : entityToFileResult(files.get(0));
    }

    String getFileSourceCode(String indexId, String filePath) {
        if (daoProvider == null) return null;
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addFilter(FilterBeans.eq("filePath", filePath));
        query.setLimit(1);
        List<NopCodeFile> files = fileDao.findAllByQuery(query);
        return files.isEmpty() ? null : files.get(0).getSourceCode();
    }

    List<CodeSymbol> getFileSymbols(String indexId, String filePath) {
        if (daoProvider == null) return Collections.emptyList();
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        String fileId = generateFileId(indexId, filePath);
        query.addFilter(FilterBeans.eq("fileId", fileId));
        query.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
        List<NopCodeSymbol> symbols = symbolDao.findAllByQuery(query);
        warnIfCapped(symbols.size(), CodeIndexService.MAX_QUERY_RESULTS, "getFileSymbols:" + filePath);
        return symbols.stream()
                .map(CodeSymbolConverter::toCodeSymbol)
                .collect(Collectors.toList());
    }

    List<CodeSymbol> getFileTypes(String indexId, String filePath) {
        List<CodeSymbol> symbols = getFileSymbols(indexId, filePath);
        return symbols.stream()
                .filter(s -> s.getKind() == CodeSymbolKind.CLASS
                        || s.getKind() == CodeSymbolKind.INTERFACE
                        || s.getKind() == CodeSymbolKind.ENUM
                        || s.getKind() == CodeSymbolKind.ANNOTATION_TYPE)
                .collect(Collectors.toList());
    }

    FileOutlineDTO getFileOutline(String indexId, String filePath) {
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

    List<FileTreeNode> getFileTree(String indexId) {
        List<CodeFileAnalysisResult> files = getFiles(indexId);

        FileTreeNode root = new FileTreeNode();
        root.setName("root");
        root.setPath("");
        root.setType(FileTreeNodeType.PACKAGE);

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
                    packageNode.setType(FileTreeNodeType.PACKAGE);
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
            fileNode.setType(FileTreeNodeType.FILE);

            FileTreeNode packageParent = nodeMap.get(packageName);
            if (packageParent != null) {
                packageParent.getChildren().add(fileNode);
            }
        }

        return root.getChildren();
    }

    List<ModuleDigestDTO> getModuleDigest(String indexId, String dirPath, boolean includePrivate) {
        if (daoProvider == null) return Collections.emptyList();

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean fileQuery = new QueryBean();
        fileQuery.addFilter(FilterBeans.eq("indexId", indexId));
        if (dirPath != null && !dirPath.isEmpty()) {
            fileQuery.addFilter(FilterBeans.startsWith("filePath", dirPath));
        }
        fileQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
        fileQuery.addField(QueryFieldBean.forField("id"));
        fileQuery.addField(QueryFieldBean.forField("filePath"));
        fileQuery.addField(QueryFieldBean.forField("packageName"));
        // Projection: avoid loading CLOB sourceCode for a digest that only needs scalars
        List<Map<String, Object>> fileRows = fileDao.selectFieldsByQuery(fileQuery);
        warnIfCapped(fileRows.size(), CodeIndexService.MAX_QUERY_RESULTS, "getModuleDigest:files:" + indexId);

        Set<String> allowedKinds = new HashSet<>(Arrays.asList(
                "CLASS", "INTERFACE", "ENUM", "ANNOTATION_TYPE", "METHOD", "FUNCTION"));

        Set<String> fileIds = new HashSet<>();
        Map<String, Map<String, Object>> fileById = new LinkedHashMap<>();
        for (Map<String, Object> row : fileRows) {
            Object id = row.get("id");
            if (id != null) {
                fileIds.add(id.toString());
                fileById.put(id.toString(), row);
            }
        }

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean symQuery = new QueryBean();
        symQuery.addFilter(FilterBeans.eq("indexId", indexId));
        symQuery.addFilter(FilterBeans.in("fileId", new ArrayList<>(fileIds)));
        if (!includePrivate) {
            symQuery.addFilter(FilterBeans.ne("accessModifier", "PRIVATE"));
        }
        symQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
        List<NopCodeSymbol> allSymbols = symbolDao.findAllByQuery(symQuery);
        warnIfCapped(allSymbols.size(), CodeIndexService.MAX_QUERY_RESULTS, "getModuleDigest:symbols:" + indexId);

        Map<String, List<NopCodeSymbol>> symbolsByFileId = new LinkedHashMap<>();
        for (NopCodeSymbol sym : allSymbols) {
            symbolsByFileId.computeIfAbsent(sym.getFileId(), k -> new ArrayList<>()).add(sym);
        }

        List<ModuleDigestDTO> result = new ArrayList<>();
        for (Map<String, Object> file : fileRows) {
            Object fid = file.get("id");
            String fileId = fid != null ? fid.toString() : "";

            List<SymbolInfoDTO> symbols = new ArrayList<>();
            List<NopCodeSymbol> fileSymbols = symbolsByFileId.getOrDefault(fileId, Collections.emptyList());
            for (NopCodeSymbol sym : fileSymbols) {
                if (!allowedKinds.contains(sym.getKind())) continue;
                SymbolInfoDTO info = new SymbolInfoDTO();
                info.setName(sym.getName());
                info.setQualifiedName(sym.getQualifiedName());
                info.setKind(sym.getKind());
                info.setAccessModifier(sym.getAccessModifier());
                symbols.add(info);
            }

            ModuleDigestDTO dto = new ModuleDigestDTO();
            Object fp = file.get("filePath");
            dto.setFilePath(fp != null ? fp.toString() : null);
            Object pn = file.get("packageName");
            dto.setPackageName(pn != null ? pn.toString() : null);
            dto.setSymbols(symbols);
            result.add(dto);
        }

        result.sort(Comparator.comparing(ModuleDigestDTO::getFilePath));
        return result;
    }

    List<PublicAPIDTO> getPublicSurface(String indexId, String dirPath) {
        if (daoProvider == null) return Collections.emptyList();

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean fileQuery = new QueryBean();
        fileQuery.addFilter(FilterBeans.eq("indexId", indexId));
        if (dirPath != null && !dirPath.isEmpty()) {
            fileQuery.addFilter(FilterBeans.startsWith("filePath", dirPath));
        }
        fileQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
        fileQuery.addField(QueryFieldBean.forField("id"));
        fileQuery.addField(QueryFieldBean.forField("filePath"));
        // Projection: avoid loading CLOB sourceCode for a surface scan that only needs id+path
        List<Map<String, Object>> fileRows = fileDao.selectFieldsByQuery(fileQuery);
        warnIfCapped(fileRows.size(), CodeIndexService.MAX_QUERY_RESULTS, "getPublicSurface:files:" + indexId);

        Set<String> allowedKinds = new HashSet<>(Arrays.asList(
                "CLASS", "INTERFACE", "ENUM", "METHOD", "FIELD"));

        Map<String, String> fileIdToPath = new HashMap<>();
        Set<String> fileIds = new HashSet<>();
        for (Map<String, Object> row : fileRows) {
            Object id = row.get("id");
            Object path = row.get("filePath");
            if (id != null) {
                fileIds.add(id.toString());
                fileIdToPath.put(id.toString(), path != null ? path.toString() : null);
            }
        }

        if (fileIds.isEmpty()) return Collections.emptyList();

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean symQuery = new QueryBean();
        symQuery.addFilter(FilterBeans.eq("indexId", indexId));
        symQuery.addFilter(FilterBeans.in("fileId", fileIds));
        symQuery.addFilter(FilterBeans.eq("accessModifier", "PUBLIC"));

        List<PublicAPIDTO> result = new ArrayList<>();
        for (NopCodeSymbol sym : symbolDao.findAllByQuery(symQuery)) {
            if (!allowedKinds.contains(sym.getKind())) continue;

            PublicAPIDTO dto = new PublicAPIDTO();
            dto.setFilePath(fileIdToPath.get(sym.getFileId()));
            dto.setSymbolName(sym.getName());
            dto.setQualifiedName(sym.getQualifiedName());
            dto.setKind(sym.getKind());
            dto.setSignature(sym.getSignature());
            dto.setDocumentation(sym.getDocumentation());
            dto.setReturnType(sym.getReturnType());
            result.add(dto);
        }

        result.sort(Comparator.comparing(PublicAPIDTO::getFilePath)
                .thenComparing(PublicAPIDTO::getSymbolName));
        return result;
    }

    PageBean<CodeFileAnalysisResult> findFilesPage(String indexId, String packageName, long offset, int limit) {
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

    CodeSymbol getSymbolById(String indexId, String symbolId) {
        if (daoProvider == null) return null;
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        // WP-7 AR-41: scope by indexId so a symbol from another index is never returned
        // (getEntityById alone ignores indexId → cross-index leak).
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addFilter(FilterBeans.eq("id", symbolId));
        query.setLimit(1);
        List<NopCodeSymbol> results = symbolDao.findAllByQuery(query);
        return results.isEmpty() ? null : CodeSymbolConverter.toCodeSymbol(results.get(0));
    }

    CodeSymbol findSymbolByQualifiedName(String indexId, String qualifiedName) {
        if (daoProvider == null) return null;
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addFilter(FilterBeans.eq("qualifiedName", qualifiedName));
        query.setLimit(1);
        List<NopCodeSymbol> results = symbolDao.findAllByQuery(query);
        return results.isEmpty() ? null : CodeSymbolConverter.toCodeSymbol(results.get(0));
    }

    List<CodeSymbol> findSymbols(String indexId, String query, List<CodeSymbolKind> kinds,
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
            qb.addFilter(FilterBeans.startsWith("qualifiedName", packagePrefix(packageName)));
        }
        if (limit > 0) qb.setLimit(limit);
        return symbolDao.findAllByQuery(qb).stream()
                .map(CodeSymbolConverter::toCodeSymbol)
                .collect(Collectors.toList());
    }

    PageBean<CodeSymbol> findSymbolsPage(String indexId, String query, List<CodeSymbolKind> kinds,
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
            countQb.addFilter(FilterBeans.startsWith("qualifiedName", packagePrefix(packageName)));
        }

        long total = symbolDao.countByQuery(countQb);
        pageBean.setTotal(total);

        QueryBean pageQb = new QueryBean();
        pageQb.setOffset(offset);
        pageQb.setLimit(limit > 0 ? limit : 20);
        pageQb.setFilter(countQb.getFilter());

        List<NopCodeSymbol> entities = symbolDao.findPageByQuery(pageQb);
        pageBean.setItems(entities.stream()
                .map(CodeSymbolConverter::toCodeSymbol)
                .collect(Collectors.toList()));
        return pageBean;
    }

    List<CodeAnnotationUsage> getSymbolUsages(String indexId, String symbolId, int limit) {
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

    String getSymbolSourceCode(String indexId, String symbolId, int linesBefore, int linesAfter) {
        if (daoProvider == null) return null;
        NopCodeSymbol entity = daoProvider.daoFor(NopCodeSymbol.class).getEntityById(symbolId);
        if (entity == null || entity.getFileId() == null) return null;
        NopCodeFile file = daoProvider.daoFor(NopCodeFile.class).getEntityById(entity.getFileId());
        if (file == null || file.getSourceCode() == null) return null;
        int startLine = (entity.getLine() != null ? entity.getLine() : 1) - linesBefore;
        int endLine = (entity.getEndLine() != null ? entity.getEndLine() : entity.getLine() != null ? entity.getLine() : 1) + linesAfter;
        return extractLines(file.getSourceCode(), Math.max(1, startLine), endLine);
    }

    SymbolSourceDTO showSymbolSource(String indexId, String qualifiedName, boolean includeBody) {
        if (daoProvider == null) return null;

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addFilter(FilterBeans.eq("qualifiedName", qualifiedName));
        query.setLimit(1);
        List<NopCodeSymbol> results = symbolDao.findAllByQuery(query);
        if (results.isEmpty()) return null;

        NopCodeSymbol entity = results.get(0);

        SymbolSourceDTO dto = new SymbolSourceDTO();
        dto.setQualifiedName(entity.getQualifiedName());
        dto.setStartLine(entity.getLine() != null ? entity.getLine() : 0);
        dto.setEndLine(entity.getEndLine() != null ? entity.getEndLine() : 0);
        dto.setSignature(entity.getSignature());

        String fileId = entity.getFileId();
        NopCodeFile file = null;
        if (fileId != null) {
            IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
            file = fileDao.getEntityById(fileId);
            if (file != null) {
                dto.setFilePath(file.getFilePath());
            }
        }

        if (file != null && file.getSourceCode() != null && includeBody) {
            int start = dto.getStartLine();
            int end = dto.getEndLine();
            if (start > 0 && end >= start) {
                dto.setSourceCode(extractLines(file.getSourceCode(), start, end));
            }
        }

        return dto;
    }

    TypeOutlineDTO getTypeOutline(String indexId, String qualifiedName) {
        if (daoProvider == null) return null;
        CodeSymbol symbol = findSymbolByQualifiedName(indexId, qualifiedName);
        if (symbol == null) return null;

        TypeOutlineDTO outline = new TypeOutlineDTO();
        outline.setName(symbol.getName());
        outline.setQualifiedName(symbol.getQualifiedName());
        outline.setKind(symbol.getKind() != null ? symbol.getKind().name() : null);
        outline.setAccessModifier(symbol.getAccessModifier() != null ? symbol.getAccessModifier().name() : null);

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean childQuery = new QueryBean();
        childQuery.addFilter(FilterBeans.eq("indexId", indexId));
        childQuery.addFilter(FilterBeans.or(
                FilterBeans.eq("parentId", symbol.getId()),
                FilterBeans.eq("declaringSymbolId", symbol.getId())
        ));
        childQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);

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

    List<TypeOutlineDTO> batchGetTypeOutlines(String indexId, List<String> qualifiedNames) {
        if (daoProvider == null || qualifiedNames == null || qualifiedNames.isEmpty()) {
            return Collections.emptyList();
        }

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean qnQuery = new QueryBean();
        qnQuery.addFilter(FilterBeans.eq("indexId", indexId));
        qnQuery.addFilter(FilterBeans.in("qualifiedName", qualifiedNames));
        qnQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
        List<NopCodeSymbol> typeSymbols = symbolDao.findAllByQuery(qnQuery);

        Map<String, NopCodeSymbol> symbolByQN = new LinkedHashMap<>();
        Set<String> typeIds = new LinkedHashSet<>();
        for (NopCodeSymbol s : typeSymbols) {
            if (s.getQualifiedName() != null) {
                symbolByQN.put(s.getQualifiedName(), s);
                typeIds.add(s.getId());
            }
        }

        if (typeIds.isEmpty()) return Collections.emptyList();

        QueryBean childQuery = new QueryBean();
        childQuery.addFilter(FilterBeans.eq("indexId", indexId));
        childQuery.addFilter(FilterBeans.in("parentId", new ArrayList<>(typeIds)));
        childQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
        List<NopCodeSymbol> childrenByParent = symbolDao.findAllByQuery(childQuery);

        QueryBean memberQuery = new QueryBean();
        memberQuery.addFilter(FilterBeans.eq("indexId", indexId));
        memberQuery.addFilter(FilterBeans.in("declaringSymbolId", new ArrayList<>(typeIds)));
        memberQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
        List<NopCodeSymbol> childrenByDecl = symbolDao.findAllByQuery(memberQuery);

        Map<String, List<NopCodeSymbol>> childrenMap = new LinkedHashMap<>();
        for (NopCodeSymbol child : childrenByParent) {
            String parentId = child.getParentId();
            if (parentId != null) {
                childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(child);
            }
        }
        for (NopCodeSymbol child : childrenByDecl) {
            String declId = child.getDeclaringSymbolId();
            if (declId != null) {
                childrenMap.computeIfAbsent(declId, k -> new ArrayList<>()).add(child);
            }
        }

        Set<String> allowedMethodKinds = new HashSet<>(Arrays.asList(
                "METHOD", "FUNCTION", "CONSTRUCTOR"));
        Set<String> allowedFieldKinds = new HashSet<>(Arrays.asList(
                "FIELD", "CONSTANT"));

        List<TypeOutlineDTO> result = new ArrayList<>();
        for (String qn : qualifiedNames) {
            NopCodeSymbol entity = symbolByQN.get(qn);
            if (entity == null) continue;

            TypeOutlineDTO outline = new TypeOutlineDTO();
            outline.setName(entity.getName());
            outline.setQualifiedName(entity.getQualifiedName());
            outline.setKind(entity.getKind());
            outline.setAccessModifier(entity.getAccessModifier());

            List<SymbolInfoDTO> methods = new ArrayList<>();
            List<SymbolInfoDTO> fields = new ArrayList<>();
            List<NopCodeSymbol> children = childrenMap.getOrDefault(entity.getId(), Collections.emptyList());
            for (NopCodeSymbol child : children) {
                SymbolInfoDTO info = new SymbolInfoDTO();
                info.setName(child.getName());
                info.setQualifiedName(child.getQualifiedName());
                info.setKind(child.getKind());
                info.setAccessModifier(child.getAccessModifier());

                if (allowedMethodKinds.contains(child.getKind())) {
                    methods.add(info);
                } else if (allowedFieldKinds.contains(child.getKind())) {
                    fields.add(info);
                }
            }
            outline.setMethods(methods);
            outline.setFields(fields);
            result.add(outline);
        }
        return result;
    }

    List<ReferenceDTO> findReferencedBy(String indexId, String qualifiedName, String kind, int limit) {
        if (daoProvider == null) return Collections.emptyList();

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean symbolQuery = new QueryBean();
        symbolQuery.addFilter(FilterBeans.eq("indexId", indexId));
        symbolQuery.addFilter(FilterBeans.eq("qualifiedName", qualifiedName));
        symbolQuery.setLimit(1);
        symbolQuery.addField(QueryFieldBean.forField("id"));
        List<Map<String, Object>> symbolRows = symbolDao.selectFieldsByQuery(symbolQuery);
        if (symbolRows.isEmpty()) return Collections.emptyList();

        Set<String> symbolIds = new LinkedHashSet<>();
        for (Map<String, Object> row : symbolRows) {
            Object id = row.get("id");
            if (id != null) symbolIds.add(id.toString());
        }

        IEntityDao<NopCodeUsage> usageDao = daoProvider.daoFor(NopCodeUsage.class);
        QueryBean qb = new QueryBean();
        qb.addFilter(FilterBeans.eq("indexId", indexId));
        qb.addFilter(FilterBeans.in("symbolId", new ArrayList<>(symbolIds)));
        if (kind != null && !kind.isEmpty()) {
            qb.addFilter(FilterBeans.eq("kind", kind));
        }
        int usageCap = limit > 0 ? limit : CodeIndexService.MAX_QUERY_RESULTS;
        qb.setLimit(usageCap);
        List<NopCodeUsage> usages = usageDao.findAllByQuery(qb);
        warnIfCapped(usages.size(), usageCap, "findReferences:usages:" + indexId);

        Set<String> fileIds = new LinkedHashSet<>();
        Set<String> enclosingSymbolIds = new LinkedHashSet<>();
        for (NopCodeUsage usage : usages) {
            if (usage.getFileId() != null) fileIds.add(usage.getFileId());
            if (usage.getEnclosingSymbolId() != null) enclosingSymbolIds.add(usage.getEnclosingSymbolId());
        }

        Map<String, NopCodeFile> fileMap = Collections.emptyMap();
        if (!fileIds.isEmpty()) {
            IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
            QueryBean fileQuery = new QueryBean();
            fileQuery.addFilter(FilterBeans.in("id", new ArrayList<>(fileIds)));
            fileQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
            fileQuery.addField(QueryFieldBean.forField("id"));
            fileQuery.addField(QueryFieldBean.forField("filePath"));
            fileMap = new HashMap<>();
            for (Map<String, Object> row : fileDao.selectFieldsByQuery(fileQuery)) {
                NopCodeFile stub = new NopCodeFile();
                Object id = row.get("id");
                Object path = row.get("filePath");
                if (id != null) stub.setId(id.toString());
                if (path != null) stub.setFilePath(path.toString());
                fileMap.put(stub.getId(), stub);
            }
        }

        Map<String, NopCodeSymbol> enclosingMap = Collections.emptyMap();
        if (!enclosingSymbolIds.isEmpty()) {
            QueryBean encQuery = new QueryBean();
            encQuery.addFilter(FilterBeans.in("id", new ArrayList<>(enclosingSymbolIds)));
            encQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
            enclosingMap = new HashMap<>();
            for (NopCodeSymbol enc : symbolDao.findAllByQuery(encQuery)) {
                enclosingMap.put(enc.getId(), enc);
            }
        }

        Map<String, NopCodeFile> finalFileMap = fileMap;
        Map<String, NopCodeSymbol> finalEnclosingMap = enclosingMap;
        return usages.stream().map(usage -> {
            ReferenceDTO dto = new ReferenceDTO();
            dto.setKind(usage.getKind());
            dto.setLine(usage.getLine() != null ? usage.getLine() : 0);
            dto.setColumn(usage.getColumn() != null ? usage.getColumn() : 0);
            dto.setContext(usage.getContext());

            if (usage.getFileId() != null) {
                NopCodeFile file = finalFileMap.get(usage.getFileId());
                if (file != null) {
                    dto.setFilePath(file.getFilePath());
                }
            }

            if (usage.getEnclosingSymbolId() != null) {
                NopCodeSymbol enclosing = finalEnclosingMap.get(usage.getEnclosingSymbolId());
                if (enclosing != null) {
                    dto.setEnclosingSymbolName(enclosing.getName());
                    dto.setEnclosingQualifiedName(enclosing.getQualifiedName());
                }
            }

            return dto;
        }).collect(Collectors.toList());
    }

    List<CodeSymbol> findByAnnotation(String indexId, String annotationName) {
        if (daoProvider == null || annotationName == null || annotationName.isEmpty())
            return Collections.emptyList();

        IEntityDao<NopCodeAnnotationUsage> annotDao = daoProvider.daoFor(NopCodeAnnotationUsage.class);
        QueryBean annotQuery = new QueryBean();
        annotQuery.addFilter(FilterBeans.eq("indexId", indexId));
        annotQuery.addFilter(FilterBeans.eq("annotationTypeId", annotationName));
        annotQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
        annotQuery.addField(QueryFieldBean.forField("annotatedSymbolId"));
        List<Map<String, Object>> exactRows = annotDao.selectFieldsByQuery(annotQuery);
        warnIfCapped(exactRows.size(), CodeIndexService.MAX_QUERY_RESULTS, "findByAnnotation:exact:" + annotationName);

        if (exactRows.isEmpty()) {
            QueryBean fuzzyQuery = new QueryBean();
            fuzzyQuery.addFilter(FilterBeans.eq("indexId", indexId));
            fuzzyQuery.addFilter(FilterBeans.contains("annotationTypeId", annotationName));
            fuzzyQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
            fuzzyQuery.addField(QueryFieldBean.forField("annotatedSymbolId"));
            exactRows = annotDao.selectFieldsByQuery(fuzzyQuery);
            warnIfCapped(exactRows.size(), CodeIndexService.MAX_QUERY_RESULTS, "findByAnnotation:fuzzy:" + annotationName);
        }

        if (exactRows.isEmpty()) return Collections.emptyList();

        Set<String> symbolIds = new LinkedHashSet<>();
        for (Map<String, Object> row : exactRows) {
            Object sid = row.get("annotatedSymbolId");
            if (sid != null) {
                symbolIds.add(sid.toString());
            }
        }
        if (symbolIds.isEmpty()) return Collections.emptyList();

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean symQuery = new QueryBean();
        symQuery.addFilter(FilterBeans.eq("indexId", indexId));
        symQuery.addFilter(FilterBeans.in("id", new ArrayList<>(symbolIds)));
        symQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
        List<NopCodeSymbol> annotSymbols = symbolDao.findAllByQuery(symQuery);
        warnIfCapped(annotSymbols.size(), CodeIndexService.MAX_QUERY_RESULTS, "findByAnnotation:symbols:" + annotationName);
        return annotSymbols.stream()
                .map(CodeSymbolConverter::toCodeSymbol)
                .collect(Collectors.toList());
    }

    List<CodeSymbol> findImplementations(String indexId, String qualifiedName, boolean directOnly, int maxDepth) {
        if (daoProvider == null || qualifiedName == null) return Collections.emptyList();

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean symQuery = new QueryBean();
        symQuery.addFilter(FilterBeans.eq("indexId", indexId));
        symQuery.addFilter(FilterBeans.eq("qualifiedName", qualifiedName));
        symQuery.setLimit(1);
        List<NopCodeSymbol> targets = symbolDao.findAllByQuery(symQuery);
        if (targets.isEmpty()) return Collections.emptyList();

        IEntityDao<NopCodeInheritance> inhDao = daoProvider.daoFor(NopCodeInheritance.class);
        QueryBean inhQuery = new QueryBean();
        inhQuery.addFilter(FilterBeans.eq("indexId", indexId));
        inhQuery.addFilter(FilterBeans.eq("relationType", "IMPLEMENTS"));
        inhQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
        List<NopCodeInheritance> allInh = inhDao.findAllByQuery(inhQuery);

        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        Map<String, String> idToQn = new HashMap<>();
        if (symbolTable != null) {
            for (CodeSymbol sym : symbolTable.getAll()) {
                if (sym.getQualifiedName() != null) {
                    idToQn.put(sym.getId(), sym.getQualifiedName());
                }
            }
        }

        Map<String, List<String>> superToSubs = new HashMap<>();
        for (NopCodeInheritance inh : allInh) {
            String superQn = idToQn.getOrDefault(inh.getSuperTypeId(), inh.getSuperTypeId());
            String subQn = idToQn.getOrDefault(inh.getSubTypeId(), inh.getSubTypeId());
            superToSubs.computeIfAbsent(superQn, k -> new ArrayList<>())
                    .add(subQn);
        }

        Set<String> resultIds = new LinkedHashSet<>();
        int depth = maxDepth > 0 ? maxDepth : Integer.MAX_VALUE;

        if (directOnly) {
            List<String> directSubs = superToSubs.get(qualifiedName);
            if (directSubs != null) {
                resultIds.addAll(directSubs);
            }
        } else {
            Queue<BfsNode> queue = new LinkedList<>();
            queue.add(new BfsNode(qualifiedName, 0));
            Set<String> visited = new HashSet<>();
            visited.add(qualifiedName);
            while (!queue.isEmpty()) {
                BfsNode current = queue.poll();
                if (current.depth() >= depth) continue;
                List<String> subs = superToSubs.get(current.nodeId());
                if (subs == null) continue;
                for (String subId : subs) {
                    if (visited.add(subId)) {
                        resultIds.add(subId);
                        queue.add(new BfsNode(subId, current.depth() + 1));
                    }
                }
            }
        }

        if (resultIds.isEmpty()) return Collections.emptyList();

        QueryBean allSymQuery = new QueryBean();
        allSymQuery.addFilter(FilterBeans.eq("indexId", indexId));
        allSymQuery.addFilter(FilterBeans.in("id", new ArrayList<>(resultIds)));
        allSymQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
        return symbolDao.findAllByQuery(allSymQuery).stream()
                .map(CodeSymbolConverter::toCodeSymbol)
                .collect(Collectors.toList());
    }
}
