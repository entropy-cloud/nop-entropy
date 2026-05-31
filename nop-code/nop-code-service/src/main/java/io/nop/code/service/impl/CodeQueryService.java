package io.nop.code.service.impl;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.core.model.*;
import io.nop.code.core.util.DigestHelper;
import io.nop.code.dao.entity.NopCodeAnnotationUsage;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.code.dao.entity.NopCodeInheritance;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.dao.entity.NopCodeUsage;
import io.nop.code.service.api.dto.*;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

class CodeQueryService {

    private final IDaoProvider daoProvider;
    private final CodeCacheManager cacheManager;
    private final IOrmTemplate ormTemplate;

    CodeQueryService(IDaoProvider daoProvider, CodeCacheManager cacheManager, IOrmTemplate ormTemplate) {
        this.daoProvider = daoProvider;
        this.cacheManager = cacheManager;
        this.ormTemplate = ormTemplate;
    }

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

    private CodeFileAnalysisResult entityToFileResult(NopCodeFile entity) {
        CodeFileAnalysisResult result = new CodeFileAnalysisResult();
        result.setFilePath(entity.getFilePath());
        result.setPackageName(entity.getPackageName());
        result.setLanguage(entity.getLanguage() != null
                ? CodeLanguage.valueOf(entity.getLanguage()) : null);
        result.setLineCount(entity.getLineCount() != null ? entity.getLineCount() : 0);
        result.setSourceCode(entity.getSourceCode());
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
        return fileDao.findAllByQuery(query).stream()
                .map(this::entityToFileResult)
                .collect(Collectors.toList());
    }

    CodeFileAnalysisResult getFile(String indexId, String filePath) {
        if (daoProvider == null) return null;
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addFilter(FilterBeans.eq("filePath", filePath));
        List<NopCodeFile> files = fileDao.findAllByQuery(query);
        return files.isEmpty() ? null : entityToFileResult(files.get(0));
    }

    String getFileSourceCode(String indexId, String filePath) {
        if (daoProvider == null) return null;
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addFilter(FilterBeans.eq("filePath", filePath));
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
        return symbolDao.findAllByQuery(query).stream()
                .map(this::entityToCodeSymbol)
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

    List<ModuleDigestDTO> getModuleDigest(String indexId, String dirPath, boolean includePrivate) {
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

    List<PublicAPIDTO> getPublicSurface(String indexId, String dirPath) {
        if (daoProvider == null) return Collections.emptyList();

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean fileQuery = new QueryBean();
        fileQuery.addFilter(FilterBeans.eq("indexId", indexId));
        if (dirPath != null && !dirPath.isEmpty()) {
            fileQuery.addFilter(FilterBeans.startsWith("filePath", dirPath));
        }
        List<NopCodeFile> files = fileDao.findAllByQuery(fileQuery);

        Set<String> allowedKinds = new HashSet<>(Arrays.asList(
                "CLASS", "INTERFACE", "ENUM", "METHOD", "FIELD"));

        Map<String, String> fileIdToPath = new HashMap<>();
        Set<String> fileIds = new HashSet<>();
        for (NopCodeFile file : files) {
            fileIds.add(file.getId());
            fileIdToPath.put(file.getId(), file.getFilePath());
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
        NopCodeSymbol entity = symbolDao.getEntityById(symbolId);
        return entity != null ? entityToCodeSymbol(entity) : null;
    }

    CodeSymbol findSymbolByQualifiedName(String indexId, String qualifiedName) {
        if (daoProvider == null) return null;
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addFilter(FilterBeans.eq("qualifiedName", qualifiedName));
        List<NopCodeSymbol> results = symbolDao.findAllByQuery(query);
        return results.isEmpty() ? null : entityToCodeSymbol(results.get(0));
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
            qb.addFilter(FilterBeans.startsWith("qualifiedName", packageName));
        }
        if (limit > 0) qb.setLimit(limit);
        return symbolDao.findAllByQuery(qb).stream()
                .map(this::entityToCodeSymbol)
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
            countQb.addFilter(FilterBeans.startsWith("qualifiedName", packageName));
        }

        long total = symbolDao.countByQuery(countQb);
        pageBean.setTotal(total);

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
        outline.setKind(symbol.getKind().name());
        outline.setAccessModifier(symbol.getAccessModifier() != null ? symbol.getAccessModifier().name() : null);

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

    List<TypeOutlineDTO> batchGetTypeOutlines(String indexId, List<String> qualifiedNames) {
        return qualifiedNames.stream()
                .map(qn -> getTypeOutline(indexId, qn))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    List<ReferenceDTO> findReferencedBy(String indexId, String qualifiedName, String kind, int limit) {
        if (daoProvider == null) return Collections.emptyList();

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean symbolQuery = new QueryBean();
        symbolQuery.addFilter(FilterBeans.eq("indexId", indexId));
        symbolQuery.addFilter(FilterBeans.eq("qualifiedName", qualifiedName));
        List<NopCodeSymbol> symbols = symbolDao.findAllByQuery(symbolQuery);
        if (symbols.isEmpty()) return Collections.emptyList();

        String symbolId = symbols.get(0).getId();

        IEntityDao<NopCodeUsage> usageDao = daoProvider.daoFor(NopCodeUsage.class);
        QueryBean qb = new QueryBean();
        qb.addFilter(FilterBeans.eq("indexId", indexId));
        qb.addFilter(FilterBeans.eq("symbolId", symbolId));
        if (kind != null && !kind.isEmpty()) {
            qb.addFilter(FilterBeans.eq("kind", kind));
        }
        if (limit > 0) qb.setLimit(limit);
        List<NopCodeUsage> usages = usageDao.findAllByQuery(qb);

        return usages.stream().map(usage -> {
            ReferenceDTO dto = new ReferenceDTO();
            dto.setKind(usage.getKind());
            dto.setLine(usage.getLine() != null ? usage.getLine() : 0);
            dto.setColumn(usage.getColumn() != null ? usage.getColumn() : 0);
            dto.setContext(usage.getContext());

            if (usage.getFileId() != null) {
                IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
                NopCodeFile file = fileDao.getEntityById(usage.getFileId());
                if (file != null) {
                    dto.setFilePath(file.getFilePath());
                }
            }

            if (usage.getEnclosingSymbolId() != null) {
                NopCodeSymbol enclosing = symbolDao.getEntityById(usage.getEnclosingSymbolId());
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
        List<NopCodeAnnotationUsage> exactMatches = annotDao.findAllByQuery(annotQuery);

        if (exactMatches.isEmpty()) {
            QueryBean fuzzyQuery = new QueryBean();
            fuzzyQuery.addFilter(FilterBeans.eq("indexId", indexId));
            fuzzyQuery.addFilter(FilterBeans.contains("annotationTypeId", annotationName));
            exactMatches = annotDao.findAllByQuery(fuzzyQuery);
        }

        if (exactMatches.isEmpty()) return Collections.emptyList();

        Set<String> symbolIds = new LinkedHashSet<>();
        for (NopCodeAnnotationUsage usage : exactMatches) {
            if (usage.getAnnotatedSymbolId() != null) {
                symbolIds.add(usage.getAnnotatedSymbolId());
            }
        }
        if (symbolIds.isEmpty()) return Collections.emptyList();

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean symQuery = new QueryBean();
        symQuery.addFilter(FilterBeans.eq("indexId", indexId));
        symQuery.addFilter(FilterBeans.in("id", new ArrayList<>(symbolIds)));
        return symbolDao.findAllByQuery(symQuery).stream()
                .map(this::entityToCodeSymbol)
                .collect(Collectors.toList());
    }

    List<CodeSymbol> findImplementations(String indexId, String qualifiedName, boolean directOnly, int maxDepth) {
        if (daoProvider == null || qualifiedName == null) return Collections.emptyList();

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean symQuery = new QueryBean();
        symQuery.addFilter(FilterBeans.eq("indexId", indexId));
        symQuery.addFilter(FilterBeans.eq("qualifiedName", qualifiedName));
        List<NopCodeSymbol> targets = symbolDao.findAllByQuery(symQuery);
        if (targets.isEmpty()) return Collections.emptyList();

        IEntityDao<NopCodeInheritance> inhDao = daoProvider.daoFor(NopCodeInheritance.class);
        QueryBean inhQuery = new QueryBean();
        inhQuery.addFilter(FilterBeans.eq("indexId", indexId));
        inhQuery.addFilter(FilterBeans.eq("relationType", "IMPLEMENTS"));
        List<NopCodeInheritance> allInh = inhDao.findAllByQuery(inhQuery);

        IEntityDao<NopCodeSymbol> symDaoForInh = daoProvider.daoFor(NopCodeSymbol.class);
        Map<String, String> idToQn = new HashMap<>();
        for (NopCodeSymbol sym : symDaoForInh.findAllByQuery(new QueryBean() {{
            addFilter(FilterBeans.eq("indexId", indexId));
        }})) {
            if (sym.getQualifiedName() != null) {
                idToQn.put(sym.getId(), sym.getQualifiedName());
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
            Queue<String[]> queue = new LinkedList<>();
            queue.add(new String[]{qualifiedName, "0"});
            Set<String> visited = new HashSet<>();
            visited.add(qualifiedName);
            while (!queue.isEmpty()) {
                String[] current = queue.poll();
                String superQn = current[0];
                int d = Integer.parseInt(current[1]);
                if (d >= depth) continue;
                List<String> subs = superToSubs.get(superQn);
                if (subs == null) continue;
                for (String subId : subs) {
                    if (visited.add(subId)) {
                        resultIds.add(subId);
                        queue.add(new String[]{subId, String.valueOf(d + 1)});
                    }
                }
            }
        }

        if (resultIds.isEmpty()) return Collections.emptyList();

        QueryBean allSymQuery = new QueryBean();
        allSymQuery.addFilter(FilterBeans.eq("indexId", indexId));
        allSymQuery.addFilter(FilterBeans.in("id", new ArrayList<>(resultIds)));
        return symbolDao.findAllByQuery(allSymQuery).stream()
                .map(this::entityToCodeSymbol)
                .collect(Collectors.toList());
    }
}
