package io.nop.code.service.impl;

import java.util.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.*;
import io.nop.code.core.util.BfsNode;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.dao.entity.NopCodeDependency;
import io.nop.code.dao.entity.NopCodeInheritance;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.graph.community.CommunityDetector;
import io.nop.code.graph.critical.CriticalNodeAnalyzer;
import io.nop.code.graph.critical.CriticalNodeResult;
import io.nop.code.graph.diff.GraphDiffer;
import io.nop.code.graph.diff.GraphSnapshot;
import io.nop.code.graph.entrypoint.EntryPointScorer;
import io.nop.code.graph.export.GraphExporter;
import io.nop.code.graph.impact.ImpactAnalyzer;
import io.nop.code.graph.knowledge.KnowledgeGapAnalyzer;
import io.nop.code.graph.knowledge.KnowledgeGapResult;
import io.nop.code.api.dto.*;
import io.nop.code.service.util.CodeSymbolConverter;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
class CodeGraphService {

    private final IDaoProvider daoProvider;
    private final CodeCacheManager cacheManager;

    CodeGraphService(IDaoProvider daoProvider, CodeCacheManager cacheManager) {
        this.daoProvider = daoProvider;
        this.cacheManager = cacheManager;
    }

    CommunityDetectionResultDTO detectCommunities(String indexId) {
        if (daoProvider == null) return null;
        CallGraph callGraph = cacheManager.getOrRebuildCallGraph(indexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        if (symbolTable.size() == 0) return null;
        CommunityDetector.CommunityDetectionResult result =
                new CommunityDetector().detectCommunities(callGraph, symbolTable);
        return convertCommunityResult(result);
    }

    GraphAnalysisResultDTO getGraphAnalysis(String indexId, int topN) {
        if (daoProvider == null) return null;
        CallGraph callGraph = cacheManager.getOrRebuildCallGraph(indexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
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

    ImpactResultDTO getImpactAnalysis(String indexId, String symbolId, int depth) {
        if (daoProvider == null) return null;
        CallGraph callGraph = cacheManager.getOrRebuildCallGraph(indexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        int maxDepth = depth > 0 ? depth : 3;
        CodeSymbol symbol = symbolTable.getById(symbolId);
        String qualifiedName = symbol != null ? symbol.getQualifiedName() : symbolId;
        ImpactAnalyzer.ImpactResult result =
                new ImpactAnalyzer().analyzeImpact(qualifiedName, callGraph, symbolTable, maxDepth);
        return convertImpactResult(result);
    }

    CriticalNodeResultDTO getCriticalNodes(String indexId, int topN) {
        if (daoProvider == null) return null;
        CallGraph callGraph = cacheManager.getOrRebuildCallGraph(indexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        CriticalNodeResult result = new CriticalNodeAnalyzer().analyze(callGraph, symbolTable, topN);
        CriticalNodeResultDTO dto = new CriticalNodeResultDTO();
        dto.setTotalNodes(result.getTotalNodes());
        dto.setTopN(result.getTopN());
        dto.setHubNodes(convertNodeScores(result.getHubNodes()));
        dto.setBridgeNodes(convertNodeScores(result.getBridgeNodes()));
        return dto;
    }

    KnowledgeGapResultDTO getKnowledgeGaps(String indexId) {
        if (daoProvider == null) return null;
        CallGraph callGraph = cacheManager.getOrRebuildCallGraph(indexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        CommunityDetector.CommunityDetectionResult communities =
                new CommunityDetector().detectCommunities(callGraph, symbolTable);
        KnowledgeGapResult result = new KnowledgeGapAnalyzer().analyze(callGraph, symbolTable, communities);
        KnowledgeGapResultDTO dto = new KnowledgeGapResultDTO();
        dto.setIsolatedSymbols(result.getIsolatedSymbols().stream()
                .map(this::toIsolatedSymbolDTO).collect(Collectors.toList()));
        dto.setWeakCommunities(result.getWeakCommunities().stream()
                .map(this::toWeakCommunityDTO).collect(Collectors.toList()));
        return dto;
    }

    String exportGraph(String indexId, String format, boolean communityView) {
        if (daoProvider == null) return null;
        CallGraph callGraph = cacheManager.getOrRebuildCallGraph(indexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        CommunityDetector.CommunityDetectionResult communities = null;
        if (communityView) {
            communities = new CommunityDetector().detectCommunities(callGraph, symbolTable);
        }
        return new GraphExporter().export(callGraph, symbolTable, format, communityView, communities);
    }

    GraphDiffDTO diffGraph(String baselineIndexId, String targetIndexId) {
        if (daoProvider == null) return null;
        CallGraph baselineCallGraph = cacheManager.getOrRebuildCallGraph(baselineIndexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable baselineSymbolTable = cacheManager.getOrRebuildSymbolTable(baselineIndexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        CommunityDetector.CommunityDetectionResult baselineCommunities =
                new CommunityDetector().detectCommunities(baselineCallGraph, baselineSymbolTable);
        GraphSnapshot baseline = GraphDiffer.buildSnapshot(baselineCallGraph, baselineCommunities);

        CallGraph targetCallGraph = cacheManager.getOrRebuildCallGraph(targetIndexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable targetSymbolTable = cacheManager.getOrRebuildSymbolTable(targetIndexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        CommunityDetector.CommunityDetectionResult targetCommunities =
                new CommunityDetector().detectCommunities(targetCallGraph, targetSymbolTable);
        GraphSnapshot target = GraphDiffer.buildSnapshot(targetCallGraph, targetCommunities);

        io.nop.code.graph.diff.GraphDiff diff = new GraphDiffer().diff(baseline, target);
        return convertGraphDiff(diff);
    }

    TypeHierarchyDTO getTypeHierarchy(String indexId, String qualifiedName,
                                       String direction, int maxDepth) {
        if (daoProvider == null) return null;
        SymbolTable table = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        if (table == null) return null;
        CodeSymbol symbol = table.getByQualifiedName(qualifiedName);
        if (symbol == null) return null;

        List<CodeInheritance> relevantInheritances = collectRelevantInheritances(
                indexId, symbol.getId(), qualifiedName, direction, Math.min(maxDepth, 50), table);
        return buildTypeHierarchy(qualifiedName, direction, Math.min(maxDepth, 50), table, relevantInheritances, new HashSet<>());
    }

    private List<CodeInheritance> collectRelevantInheritances(String indexId, String startId,
                                                               String startQn, String direction,
                                                               int maxDepth, SymbolTable table) {
        IEntityDao<NopCodeInheritance> inhDao = daoProvider.daoFor(NopCodeInheritance.class);
        Set<String> visitedIds = new HashSet<>();
        Set<String> visitedQns = new HashSet<>();
        Queue<String> idQueue = new ArrayDeque<>();
        Queue<String> qnQueue = new ArrayDeque<>();
        idQueue.add(startId);
        qnQueue.add(startQn);
        visitedIds.add(startId);
        visitedQns.add(startQn);

        List<CodeInheritance> result = new ArrayList<>();
        int depth = 0;

        while (!idQueue.isEmpty() && depth <= maxDepth) {
            Set<String> batchIds = new HashSet<>();
            Set<String> batchQns = new HashSet<>();
            int size = idQueue.size();
            for (int i = 0; i < size; i++) {
                batchIds.add(idQueue.poll());
                batchQns.add(qnQueue.poll());
            }

            if (!batchIds.isEmpty()) {
                QueryBean q = new QueryBean();
                q.addFilter(FilterBeans.eq("indexId", indexId));
                q.addFilter(FilterBeans.or(
                        FilterBeans.in("subTypeId", new ArrayList<>(batchIds)),
                        FilterBeans.in("superTypeId", new ArrayList<>(batchQns))
                ));
                q.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
                for (NopCodeInheritance inh : inhDao.findAllByQuery(q)) {
                    result.add(entityToInheritance(inh));
                    String subId = inh.getSubTypeId();
                    String superQn = inh.getSuperTypeId();
                    CodeSymbol subSym = table.getById(subId);
                    String subQn = subSym != null ? subSym.getQualifiedName() : subId;
                    if (visitedIds.add(subId)) idQueue.add(subId);
                    if (subQn != null && visitedQns.add(subQn)) qnQueue.add(subQn);
                    if (superQn != null && visitedQns.add(superQn)) qnQueue.add(superQn);
                    CodeSymbol superSym = table.getByQualifiedName(superQn);
                    if (superSym != null && visitedIds.add(superSym.getId())) idQueue.add(superSym.getId());
                }
            }
            depth++;
        }
        return result;
    }

    CallHierarchyDTO getCallHierarchy(String indexId, String qualifiedName,
                                       String direction, int maxDepth) {
        if (daoProvider == null) return null;
        CallGraph callGraph = cacheManager.getOrRebuildCallGraph(indexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable table = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        return buildCallHierarchy(qualifiedName, direction, Math.min(maxDepth, 50), callGraph, table, new HashSet<>());
    }

    private TypeHierarchyDTO buildTypeHierarchy(String qualifiedName, String direction, int maxDepth,
                                                 SymbolTable table, List<CodeInheritance> allInheritances,
                                                 Set<String> visited) {
        if (visited.contains(qualifiedName)) return null;
        visited.add(qualifiedName);
        CodeSymbol symbol = table.getByQualifiedName(qualifiedName);
        TypeHierarchyDTO node = new TypeHierarchyDTO();
        SymbolInfoDTO symbolInfo = new SymbolInfoDTO();
        if (symbol != null) {
            symbolInfo.setName(symbol.getName());
            symbolInfo.setQualifiedName(symbol.getQualifiedName());
            symbolInfo.setKind(symbol.getKind() != null ? symbol.getKind().name() : null);
        } else {
            symbolInfo.setQualifiedName(qualifiedName);
            symbolInfo.setName(qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1));
        }
        node.setSymbol(symbolInfo);
        if (maxDepth <= 0) return node;
        if ("super".equals(direction) || "both".equals(direction)) {
            List<TypeHierarchyDTO> superTypes = allInheritances.stream()
                    .filter(i -> symbol != null && symbol.getId().equals(i.getSubTypeId()))
                    .map(i -> {
                        String superRef = i.getSuperTypeQualifiedName();
                        CodeSymbol superSymbol = table.getById(superRef);
                        String superQn = superSymbol != null ? superSymbol.getQualifiedName() : superRef;
                        return buildTypeHierarchy(superQn, direction, maxDepth - 1, table, allInheritances, visited);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            node.setSuperTypes(superTypes);
        }
        if ("sub".equals(direction) || "both".equals(direction)) {
            String currentId = symbol != null ? symbol.getId() : null;
            List<TypeHierarchyDTO> subTypes = allInheritances.stream()
                    .filter(i -> qualifiedName.equals(i.getSuperTypeQualifiedName())
                            || (currentId != null && currentId.equals(i.getSuperTypeQualifiedName())))
                    .map(i -> {
                        CodeSymbol subSymbol = table.getById(i.getSubTypeId());
                        if (subSymbol != null) {
                            return buildTypeHierarchy(subSymbol.getQualifiedName(), direction, maxDepth - 1, table, allInheritances, visited);
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            node.setSubTypes(subTypes);
        }
        return node;
    }

    private CallHierarchyDTO buildCallHierarchy(String qualifiedName, String direction, int maxDepth,
                                                 CallGraph callGraph, SymbolTable table,
                                                 Set<String> visited) {
        if (visited.contains(qualifiedName)) return null;
        visited.add(qualifiedName);
        CodeSymbol symbol = table.getByQualifiedName(qualifiedName);
        CallHierarchyDTO node = new CallHierarchyDTO();
        SymbolInfoDTO symbolInfo = new SymbolInfoDTO();
        if (symbol != null) {
            symbolInfo.setName(symbol.getName());
            symbolInfo.setQualifiedName(symbol.getQualifiedName());
            symbolInfo.setKind(symbol.getKind() != null ? symbol.getKind().name() : null);
        } else {
            symbolInfo.setQualifiedName(qualifiedName);
            symbolInfo.setName(qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1));
        }
        node.setSymbol(symbolInfo);
        if (maxDepth <= 0) return node;
        if ("outgoing".equals(direction) || "both".equals(direction)) {
            List<String> calleeIds = callGraph.getCallees(symbol != null ? symbol.getId() : qualifiedName);
            if (calleeIds != null) {
                List<CallHierarchyDTO> callees = calleeIds.stream()
                        .map(calleeId -> {
                            CodeSymbol calleeSymbol = table.getById(calleeId);
                            String calleeQn = calleeSymbol != null ? calleeSymbol.getQualifiedName() : calleeId;
                            return buildCallHierarchy(calleeQn, direction, maxDepth - 1, callGraph, table, visited);
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                node.setCallees(callees);
            }
        }
        if ("incoming".equals(direction) || "both".equals(direction)) {
            List<String> callerIds = callGraph.getCallers(symbol != null ? symbol.getId() : qualifiedName);
            if (callerIds != null) {
                List<CallHierarchyDTO> callers = callerIds.stream()
                        .map(callerId -> {
                            CodeSymbol callerSymbol = table.getById(callerId);
                            String callerQn = callerSymbol != null ? callerSymbol.getQualifiedName() : callerId;
                            return buildCallHierarchy(callerQn, direction, maxDepth - 1, callGraph, table, visited);
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                node.setCallers(callers);
            }
        }
        return node;
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
                .map(this::toImpactedSymbol).collect(Collectors.toList()));
        dto.setDownstream(result.getDownstream().stream()
                .map(this::toImpactedSymbol).collect(Collectors.toList()));
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

    private List<CriticalNodeScoreDTO> convertNodeScores(List<CriticalNodeResult.NodeScore> scores) {
        return scores.stream().map(ns -> {
            CriticalNodeScoreDTO dto = new CriticalNodeScoreDTO();
            dto.setSymbolId(ns.getSymbolId());
            dto.setQualifiedName(ns.getQualifiedName());
            dto.setScore(ns.getScore());
            dto.setInDegree(ns.getInDegree());
            dto.setOutDegree(ns.getOutDegree());
            dto.setTotalDegree(ns.getTotalDegree());
            return dto;
        }).collect(Collectors.toList());
    }

    private IsolatedSymbolDTO toIsolatedSymbolDTO(KnowledgeGapResult.IsolatedSymbol iso) {
        IsolatedSymbolDTO dto = new IsolatedSymbolDTO();
        dto.setSymbolId(iso.getSymbolId());
        dto.setQualifiedName(iso.getQualifiedName());
        dto.setName(iso.getName());
        dto.setKind(iso.getKind());
        return dto;
    }

    private WeakCommunityDTO toWeakCommunityDTO(KnowledgeGapResult.WeakCommunity wc) {
        WeakCommunityDTO dto = new WeakCommunityDTO();
        dto.setCommunityId(wc.getCommunityId());
        dto.setLabel(wc.getLabel());
        dto.setSymbolCount(wc.getSymbolCount());
        dto.setCohesion(wc.getCohesion());
        dto.setThreshold(wc.getThreshold());
        return dto;
    }

    private GraphDiffDTO convertGraphDiff(io.nop.code.graph.diff.GraphDiff diff) {
        GraphDiffDTO dto = new GraphDiffDTO();
        dto.setAddedNodes(diff.getAddedNodes());
        dto.setRemovedNodes(diff.getRemovedNodes());
        dto.setAddedEdges(diff.getAddedEdges().stream()
                .map(e -> new EdgeKeyDTO(e.getSource(), e.getTarget()))
                .collect(Collectors.toSet()));
        dto.setRemovedEdges(diff.getRemovedEdges().stream()
                .map(e -> new EdgeKeyDTO(e.getSource(), e.getTarget()))
                .collect(Collectors.toSet()));
        dto.setCommunityChanges(diff.getCommunityChanges().stream()
                .map(cc -> {
                    CommunityChangeDTO c = new CommunityChangeDTO();
                    c.setNodeId(cc.getNodeId());
                    c.setOldCommunity(cc.getOldCommunity());
                    c.setNewCommunity(cc.getNewCommunity());
                    return c;
                }).collect(Collectors.toList()));
        return dto;
    }

    DepGraphDTO getDeps(String indexId, String filePath, int depth) {
        if (daoProvider == null) return new DepGraphDTO();
        Map<String, List<DepEdgeDTO>> adj = buildForwardAdjacency(indexId);
        Set<String> visited = new HashSet<>();
        List<DepEdgeDTO> resultEdges = new ArrayList<>();
        bfsCollect(filePath, adj, depth, visited, resultEdges, DepEdgeDTO::getTarget);
        return buildGraphFromEdges(resultEdges);
    }

    DepGraphDTO getReverseDeps(String indexId, String filePath, int depth, int limit) {
        if (daoProvider == null) return new DepGraphDTO();
        Map<String, List<DepEdgeDTO>> adj = buildReverseAdjacency(indexId);
        Set<String> visited = new HashSet<>();
        List<DepEdgeDTO> resultEdges = new ArrayList<>();
        bfsCollect(filePath, adj, depth, visited, resultEdges, DepEdgeDTO::getSource);
        if (limit > 0 && resultEdges.size() > limit) {
            resultEdges = resultEdges.subList(0, limit);
        }
        return buildGraphFromEdges(resultEdges);
    }

    List<List<String>> findCycles(String indexId, int minSize) {
        if (daoProvider == null) return Collections.emptyList();
        Map<String, List<String>> adj = buildForwardStringAdjacency(indexId);
        List<List<String>> sccs = tarjanSCC(adj);
        int min = minSize > 0 ? minSize : 2;
        sccs.removeIf(scc -> scc.size() < min);
        return sccs;
    }

    DepGraphDTO getDepGraph(String indexId, boolean includeExternal) {
        if (daoProvider == null) return new DepGraphDTO();
        IEntityDao<NopCodeDependency> dao = daoProvider.daoFor(NopCodeDependency.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq("indexId", indexId));
        List<NopCodeDependency> deps = dao.findAllByQuery(q);
        // Full dependency list needed for graph building

        List<DepEdgeDTO> edges = new ArrayList<>();
        Map<String, int[]> degreeMap = new LinkedHashMap<>();
        for (NopCodeDependency dep : deps) {
            if (!includeExternal && !Boolean.TRUE.equals(dep.getResolved())) {
                continue;
            }
            String src = dep.getSourceFilePath();
            String tgt = dep.getTargetFilePath();
            if (src == null || tgt == null) continue;

            DepEdgeDTO edge = new DepEdgeDTO();
            edge.setSource(src);
            edge.setTarget(tgt);
            edge.setImportStatement(dep.getImportStatement());
            edge.setResolved(Boolean.TRUE.equals(dep.getResolved()));
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

    List<String> findDependentFiles(String indexId, String filePath) {
        if (daoProvider == null || filePath == null) return Collections.emptyList();

        IEntityDao<NopCodeDependency> depDao = daoProvider.daoFor(NopCodeDependency.class);
        QueryBean depQuery = new QueryBean();
        depQuery.addFilter(FilterBeans.eq("indexId", indexId));
        List<NopCodeDependency> allDeps = depDao.findAllByQuery(depQuery);

        Map<String, List<String>> targetToSources = new HashMap<>();
        for (NopCodeDependency dep : allDeps) {
            if (dep.getTargetFilePath() != null && dep.getSourceFilePath() != null) {
                targetToSources.computeIfAbsent(dep.getTargetFilePath(), k -> new ArrayList<>())
                        .add(dep.getSourceFilePath());
            }
        }

        Set<String> result = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(filePath);
        visited.add(filePath);

        int hops = 0;
        while (!queue.isEmpty() && hops < 2) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                String current = queue.poll();
                List<String> sources = targetToSources.get(current);
                if (sources == null) continue;
                for (String source : sources) {
                    if (visited.add(source)) {
                        result.add(source);
                        queue.add(source);
                    }
                }
            }
            hops++;
        }

        return new ArrayList<>(result);
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
            edge.setResolved(Boolean.TRUE.equals(dep.getResolved()));
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
            edge.setResolved(Boolean.TRUE.equals(dep.getResolved()));
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
                            Set<String> visited, List<DepEdgeDTO> result,
                            Function<DepEdgeDTO, String> nextNodeFn) {
        Queue<BfsNode> queue = new LinkedList<>();
        queue.add(new BfsNode(start, 0));
        visited.add(start);
        while (!queue.isEmpty()) {
            BfsNode current = queue.poll();
            if (current.depth() >= maxDepth) continue;
            List<DepEdgeDTO> edges = adj.getOrDefault(current.nodeId(), Collections.emptyList());
            for (DepEdgeDTO edge : edges) {
                result.add(edge);
                String nextNode = nextNodeFn.apply(edge);
                if (!visited.contains(nextNode)) {
                    visited.add(nextNode);
                    queue.add(new BfsNode(nextNode, current.depth() + 1));
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
        Set<String> onStack = new HashSet<>();
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
                           Set<String> onStack, Deque<String> stack,
                           List<List<String>> result) {
        nodeIndex.put(v, index[0]);
        lowLink.put(v, index[0]);
        index[0]++;
        stack.push(v);
        onStack.add(v);

        for (String w : adj.getOrDefault(v, Collections.emptyList())) {
            if (!nodeIndex.containsKey(w)) {
                tarjanDFS(w, adj, index, nodeIndex, lowLink, onStack, stack, result);
                lowLink.put(v, Math.min(lowLink.get(v), lowLink.get(w)));
            } else if (onStack.contains(w)) {
                lowLink.put(v, Math.min(lowLink.get(v), nodeIndex.get(w)));
            }
        }

        if (lowLink.get(v).equals(nodeIndex.get(v))) {
            List<String> scc = new ArrayList<>();
            String w;
            do {
                w = stack.pop();
                onStack.remove(w);
                scc.add(w);
            } while (!w.equals(v));
            result.add(scc);
        }
    }
}
