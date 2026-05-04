package io.nop.code.service.impl;

import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.analyzer.ProjectAnalyzer;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.*;
import io.nop.code.lang.java.JavaLanguageAdapter;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CodeIndexService implements ICodeIndexService {

    private final Map<String, List<CodeFileAnalysisResult>> fileResultsMap = new ConcurrentHashMap<>();
    private final Map<String, SymbolTable> symbolTableMap = new ConcurrentHashMap<>();
    private final Map<String, CallGraph> callGraphMap = new ConcurrentHashMap<>();
    private final Map<String, ProjectAnalyzer.ProjectAnalysisResult> analysisResultsMap = new ConcurrentHashMap<>();

    protected final LanguageAdapterRegistry registry;
    protected final ProjectAnalyzer analyzer;

    public CodeIndexService() {
        this.registry = new LanguageAdapterRegistry();
        this.registry.registerAdapter(new JavaLanguageAdapter());
        this.analyzer = new ProjectAnalyzer(registry);
    }

    public void updateAnalysisResult(String indexId, ProjectAnalyzer.ProjectAnalysisResult result) {
        fileResultsMap.put(indexId, result.getFileResults());
        symbolTableMap.put(indexId, result.getGlobalSymbolTable());
        callGraphMap.put(indexId, result.buildCallGraph());
        analysisResultsMap.put(indexId, result);
    }

    public ProjectAnalyzer getAnalyzer() {
        return analyzer;
    }

    @Override
    public int indexDirectory(String indexId, Path directoryPath, String filePattern) {
        try {
            ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(directoryPath);
            fileResultsMap.put(indexId, result.getFileResults());
            symbolTableMap.put(indexId, result.getGlobalSymbolTable());
            callGraphMap.put(indexId, result.buildCallGraph());
            analysisResultsMap.put(indexId, result);
            return result.getFileResults().size();
        } catch (IOException e) {
            throw new RuntimeException("Failed to index directory: " + directoryPath, e);
        }
    }

    @Override
    public CodeFileAnalysisResult indexFile(String indexId, String filePath, String sourceCode) {
        var fileAnalyzer = registry.getAnalyzer(filePath);
        if (fileAnalyzer == null) {
            throw new RuntimeException("No analyzer registered for file: " + filePath);
        }
        CodeFileAnalysisResult result = fileAnalyzer.analyze(filePath, sourceCode);

        List<CodeFileAnalysisResult> files = fileResultsMap.computeIfAbsent(indexId, k -> new ArrayList<>());
        files.add(result);

        SymbolTable symbolTable = symbolTableMap.computeIfAbsent(indexId, k -> new SymbolTable());
        if (result.getSymbols() != null) {
            for (CodeSymbol symbol : result.getSymbols()) {
                symbolTable.add(symbol);
            }
        }

        analysisResultsMap.remove(indexId);
        return result;
    }

    // ==================== File Queries ====================

    @Override
    public List<CodeFileAnalysisResult> getFiles(String indexId) {
        return fileResultsMap.getOrDefault(indexId, Collections.emptyList());
    }

    @Override
    public CodeFileAnalysisResult getFile(String indexId, String filePath) {
        List<CodeFileAnalysisResult> files = getFiles(indexId);
        return files.stream()
                .filter(f -> filePath.equals(f.getFilePath()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String getFileSourceCode(String indexId, String filePath) {
        CodeFileAnalysisResult file = getFile(indexId, filePath);
        return file != null ? file.getSourceCode() : null;
    }

    @Override
    public List<CodeSymbol> getFileSymbols(String indexId, String filePath) {
        CodeFileAnalysisResult file = getFile(indexId, filePath);
        return file != null ? file.getSymbols() : Collections.emptyList();
    }

    @Override
    public List<CodeSymbol> getFileTypes(String indexId, String filePath) {
        return getFileSymbols(indexId, filePath).stream()
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
            fileNode.setSymbolCount(file.getSymbols() != null ? file.getSymbols().size() : 0);

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
        SymbolTable table = symbolTableMap.get(indexId);
        return table != null ? table.getById(symbolId) : null;
    }

    @Override
    public CodeSymbol findSymbolByQualifiedName(String indexId, String qualifiedName) {
        SymbolTable table = symbolTableMap.get(indexId);
        return table != null ? table.getByQualifiedName(qualifiedName) : null;
    }

    @Override
    public List<CodeSymbol> findSymbols(String indexId, String query, List<CodeSymbolKind> kinds,
                                        String packageName, int limit) {
        SymbolTable table = symbolTableMap.get(indexId);
        if (table == null)
            return Collections.emptyList();

        return table.getAll().stream()
                .filter(s -> {
                    if (query != null && !query.isEmpty()) {
                        if (!s.getName().contains(query)
                                && (s.getQualifiedName() == null || !s.getQualifiedName().contains(query))) {
                            return false;
                        }
                    }
                    if (kinds != null && !kinds.isEmpty()) {
                        if (!kinds.contains(s.getKind()))
                            return false;
                    }
                    if (packageName != null && !packageName.isEmpty()) {
                        if (s.getQualifiedName() == null || !s.getQualifiedName().startsWith(packageName))
                            return false;
                    }
                    return true;
                })
                .limit(limit > 0 ? limit : Integer.MAX_VALUE)
                .collect(Collectors.toList());
    }

    @Override
    public List<CodeAnnotationUsage> getSymbolUsages(String indexId, String symbolId, int limit) {
        List<CodeFileAnalysisResult> files = getFiles(indexId);
        return files.stream()
                .flatMap(f -> f.getAnnotationUsages().stream())
                .filter(u -> symbolId.equals(u.getAnnotatedSymbolId()))
                .limit(limit > 0 ? limit : Integer.MAX_VALUE)
                .collect(Collectors.toList());
    }

    @Override
    public String getSymbolSourceCode(String indexId, String symbolId, int linesBefore, int linesAfter) {
        CodeSymbol symbol = getSymbolById(indexId, symbolId);
        if (symbol == null)
            return null;

        List<CodeFileAnalysisResult> files = getFiles(indexId);
        CodeFileAnalysisResult containingFile = files.stream()
                .filter(f -> f.getSymbols() != null && f.getSymbols().stream().anyMatch(s -> symbolId.equals(s.getId())))
                .findFirst().orElse(null);

        if (containingFile == null || containingFile.getSourceCode() == null)
            return null;

        String[] lines = containingFile.getSourceCode().split("\n");
        int start = Math.max(0, symbol.getLine() - 1 - linesBefore);
        int end = Math.min(lines.length, symbol.getEndLine() + linesAfter);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString();
    }

    // ==================== Type Queries ====================

    @Override
    public TypeOutlineDTO getTypeOutline(String indexId, String qualifiedName) {
        CodeSymbol symbol = findSymbolByQualifiedName(indexId, qualifiedName);
        if (symbol == null)
            return null;

        TypeOutlineDTO outline = new TypeOutlineDTO();
        outline.setName(symbol.getName());
        outline.setQualifiedName(symbol.getQualifiedName());
        outline.setKind(symbol.getKind().name());
        outline.setAccessModifier(symbol.getAccessModifier() != null ? symbol.getAccessModifier().name() : null);

        SymbolTable table = symbolTableMap.get(indexId);
        if (table != null) {
            List<SymbolInfoDTO> methods = new ArrayList<>();
            List<SymbolInfoDTO> fields = new ArrayList<>();
            for (CodeSymbol child : table.getAll()) {
                if (symbol.getId().equals(child.getParentId())
                        || symbol.getId().equals(child.getDeclaringSymbolId())) {
                    SymbolInfoDTO info = new SymbolInfoDTO();
                    info.setName(child.getName());
                    info.setKind(child.getKind().name());
                    info.setQualifiedName(child.getQualifiedName());
                    info.setAccessModifier(child.getAccessModifier() != null ? child.getAccessModifier().name() : null);

                    if (child.getKind() == CodeSymbolKind.METHOD || child.getKind() == CodeSymbolKind.CONSTRUCTOR) {
                        methods.add(info);
                    } else if (child.getKind() == CodeSymbolKind.FIELD) {
                        fields.add(info);
                    }
                }
            }
            outline.setMethods(methods);
            outline.setFields(fields);
        }
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
        SymbolTable table = symbolTableMap.get(indexId);
        List<CodeFileAnalysisResult> files = getFiles(indexId);
        if (table == null)
            return null;

        CodeSymbol symbol = table.getByQualifiedName(qualifiedName);
        if (symbol == null)
            return null;

        List<CodeInheritance> allInheritances = files.stream()
                .flatMap(f -> f.getInheritances() != null ? f.getInheritances().stream() : Stream.empty())
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
        CallGraph callGraph = callGraphMap.get(indexId);
        SymbolTable table = symbolTableMap.get(indexId);
        if (callGraph == null || table == null)
            return null;

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
        List<CodeFileAnalysisResult> files = getFiles(indexId);
        SymbolTable table = symbolTableMap.get(indexId);

        IndexStatsDTO stats = new IndexStatsDTO();
        stats.setIndexId(indexId);
        stats.setFileCount(files.size());
        stats.setSymbolCount(table != null ? table.size() : 0);

        if (table != null) {
            Map<String, Integer> kindCounts = new LinkedHashMap<>();
            for (CodeSymbol s : table.getAll()) {
                String kind = s.getKind().name();
                kindCounts.merge(kind, 1, Integer::sum);
            }
            stats.setSymbolCounts(kindCounts);
        }
        return stats;
    }

    @Override
    public List<String> getIndexIds() {
        return new ArrayList<>(fileResultsMap.keySet());
    }

    @Override
    public void deleteIndex(String indexId) {
        fileResultsMap.remove(indexId);
        symbolTableMap.remove(indexId);
        callGraphMap.remove(indexId);
        analysisResultsMap.remove(indexId);
    }

    // ==================== Graph Accessors ====================

    public CallGraph getCallGraph(String indexId) {
        return callGraphMap.get(indexId);
    }

    public SymbolTable getSymbolTable(String indexId) {
        return symbolTableMap.get(indexId);
    }

    public ProjectAnalyzer.ProjectAnalysisResult getAnalysisResult(String indexId) {
        return analysisResultsMap.get(indexId);
    }
}
