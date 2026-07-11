package io.nop.code.service.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.core.entrypoint.EntryPointScorer;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.CodeCallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.*;
import io.nop.code.core.util.BfsNode;
import io.nop.code.core.util.ExtDataHelper;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.dao.entity.NopCodeDependency;
import io.nop.code.dao.entity.NopCodeInheritance;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.api.dto.*;
import io.nop.code.service.graph.KnowledgeGapAnalyzer;
import io.nop.code.service.graph.KnowledgeGapResult;
import io.nop.code.service.util.CodeSymbolConverter;
import io.nop.graph.algorithm.BetweennessCentrality;
import io.nop.graph.algorithm.GraphExporter;
import io.nop.graph.algorithm.GraphDiffer;
import io.nop.graph.algorithm.ImpactPropagator;
import io.nop.graph.algorithm.LeidenDetector;
import io.nop.graph.api.CommunityInfo;
import io.nop.graph.api.CommunityResult;
import io.nop.graph.api.GraphDiff;
import io.nop.graph.api.ImpactConfig;
import io.nop.graph.api.ImpactResult;
import io.nop.graph.api.ImpactedNode;
import io.nop.graph.api.LeidenConfig;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
class CodeGraphService {

    private static final Logger LOG = LoggerFactory.getLogger(CodeGraphService.class);
    private static final int MAX_NODES_FOR_COMMUNITY_DETECTION = 10000;
    private static final int BATCH_QUERY_LIMIT = 10000;

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
        CommunityResult result = runCommunityDetection(callGraph);
        return convertCommunityResult(result, symbolTable);
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
        String nodeId;
        String qualifiedName;
        if (symbol != null) {
            nodeId = symbol.getId();
            qualifiedName = symbol.getQualifiedName();
        } else {
            CodeSymbol fuzzy = findSymbolByQualifiedName(symbolTable, symbolId);
            if (fuzzy != null) {
                nodeId = fuzzy.getId();
                qualifiedName = fuzzy.getQualifiedName();
            } else {
                ImpactResultDTO dto = new ImpactResultDTO();
                dto.setTargetSymbolId(symbolId);
                dto.setTargetQualifiedName(symbolId);
                dto.setRiskLevel("not-found");
                return dto;
            }
        }

        ImpactResult result = ImpactPropagator.propagate(
                new CodeCallGraph(callGraph), nodeId,
                ImpactConfig.create().setMaxDepth(maxDepth));
        return convertImpactResult(result, symbolTable, qualifiedName);
    }

    CriticalNodeResultDTO getCriticalNodes(String indexId, int topN) {
        if (daoProvider == null) return null;
        CallGraph callGraph = cacheManager.getOrRebuildCallGraph(indexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        if (symbolTable.size() > MAX_NODES_FOR_COMMUNITY_DETECTION) {
            LOG.warn("Graph too large for critical node analysis ({} > {}), skipping betweenness centrality",
                    symbolTable.size(), MAX_NODES_FOR_COMMUNITY_DETECTION);
            CriticalNodeResultDTO dto = new CriticalNodeResultDTO();
            dto.setTotalNodes(symbolTable.size());
            dto.setTopN(topN);
            dto.setHubNodes(Collections.emptyList());
            dto.setBridgeNodes(Collections.emptyList());
            return dto;
        }
        Set<String> nodeSet = callGraph.getAllNodeIds();
        int totalNodes = symbolTable.size();
        CriticalNodeResultDTO dto = new CriticalNodeResultDTO();
        dto.setTotalNodes(totalNodes);
        dto.setTopN(topN);
        dto.setHubNodes(computeHubNodeScores(callGraph, symbolTable, topN));
        if (nodeSet.isEmpty()) {
            dto.setBridgeNodes(Collections.emptyList());
        } else {
            dto.setBridgeNodes(computeBridgeNodeScores(callGraph, symbolTable, topN, nodeSet));
        }
        return dto;
    }

    KnowledgeGapResultDTO getKnowledgeGaps(String indexId) {
        if (daoProvider == null) return null;
        CallGraph callGraph = cacheManager.getOrRebuildCallGraph(indexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        CommunityResult communities = runCommunityDetection(callGraph);
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
        Set<String> nodeSet = callGraph.getAllNodeIds();
        CommunityResult communities = null;
        if (communityView) {
            communities = runCommunityDetection(callGraph);
        }
        return GraphExporter.export(new CodeCallGraph(callGraph), nodeSet, format, communities);
    }

    GraphDiffDTO diffGraph(String baselineIndexId, String targetIndexId) {
        if (daoProvider == null) return null;
        CallGraph baselineCallGraph = cacheManager.getOrRebuildCallGraph(baselineIndexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable baselineSymbolTable = cacheManager.getOrRebuildSymbolTable(baselineIndexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        if (baselineSymbolTable.size() > MAX_NODES_FOR_COMMUNITY_DETECTION) {
            LOG.warn("Baseline graph too large for community detection diff ({} > {}), returning empty diff",
                    baselineSymbolTable.size(), MAX_NODES_FOR_COMMUNITY_DETECTION);
            return new GraphDiffDTO();
        }

        Set<String> baselineNodes = baselineCallGraph.getAllNodeIds();
        CommunityResult baselineCommunities = runCommunityDetection(baselineCallGraph);
        Map<String, Integer> baselineCommunityMap = buildCommunityMap(baselineCommunities);

        CallGraph targetCallGraph = cacheManager.getOrRebuildCallGraph(targetIndexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable targetSymbolTable = cacheManager.getOrRebuildSymbolTable(targetIndexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        Set<String> targetNodes = targetCallGraph.getAllNodeIds();
        CommunityResult targetCommunities = runCommunityDetection(targetCallGraph);
        Map<String, Integer> targetCommunityMap = buildCommunityMap(targetCommunities);

        GraphDiff diff = GraphDiffer.diffWithCommunities(
                new CodeCallGraph(baselineCallGraph), baselineNodes, baselineCommunityMap,
                new CodeCallGraph(targetCallGraph), targetNodes, targetCommunityMap);
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
                int batchSize = 1000;
                List<String> idList = new ArrayList<>(batchIds);
                List<String> qnList = new ArrayList<>(batchQns);
                List<NopCodeInheritance> batch = new ArrayList<>();
                for (int from = 0; from < idList.size(); from += batchSize) {
                    int to = Math.min(from + batchSize, idList.size());
                    List<String> subList = idList.subList(from, to);
                    List<String> qnSubList = qnList.subList(from, Math.min(to, qnList.size()));
                    QueryBean q = new QueryBean();
                    q.addFilter(FilterBeans.eq("indexId", indexId));
                    q.addFilter(FilterBeans.or(
                            FilterBeans.in("subTypeId", subList),
                            FilterBeans.in("superTypeId", qnSubList)
                    ));
                    q.setLimit(BATCH_QUERY_LIMIT);
                    batch.addAll(inhDao.findAllByQuery(q));
                }
                for (NopCodeInheritance inh : batch) {
                    result.add(entityToInheritance(inh, table));
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

    private CodeInheritance entityToInheritance(NopCodeInheritance entity, SymbolTable table) {
        CodeInheritance inh = new CodeInheritance();
        inh.setId(entity.getId());
        inh.setSubTypeId(entity.getSubTypeId());
        String superTypeId = entity.getSuperTypeId();
        if (superTypeId != null) {
            CodeSymbol superSym = table.getById(superTypeId);
            if (superSym != null) {
                inh.setSuperTypeQualifiedName(superSym.getQualifiedName());
            } else {
                inh.setSuperTypeQualifiedName(superTypeId);
            }
        }
        inh.setRelationType(entity.getRelationType() != null
                ? CodeRelationType.valueOf(entity.getRelationType()) : null);
        return inh;
    }

    private CommunityResult runCommunityDetection(CallGraph callGraph) {
        Set<String> nodeSet = callGraph.getAllNodeIds();
        if (nodeSet.size() < 2) {
            return new CommunityResult(Collections.emptyList(), nodeSet.size(), 0, 0, 0, "LEIDEN", 0);
        }
        LeidenConfig config = LeidenConfig.create()
                .setResolution(0.1)
                .setMaxIterations(10)
                .setTimeoutMs(60000);
        return LeidenDetector.detect(new CodeCallGraph(callGraph), nodeSet, config);
    }

    private CommunityDetectionResultDTO convertCommunityResult(CommunityResult result, SymbolTable symbolTable) {
        CommunityDetectionResultDTO dto = new CommunityDetectionResultDTO();
        dto.setTotalSymbols(result.getTotalSymbols());
        dto.setTotalCommunities(result.getTotalCommunities());
        dto.setAverageCohesion(result.getAverageCohesion());
        dto.setAlgorithmUsed(result.getAlgorithmUsed());
        dto.setModularity(result.getModularity());
        dto.setProcessingTimeMs(result.getProcessingTimeMs());
        List<CommunityDTO> communities = new ArrayList<>();
        for (CommunityInfo comm : result.getCommunities()) {
            CommunityDTO c = new CommunityDTO();
            c.setId(String.valueOf(comm.getId()));
            c.setSymbolIds(new ArrayList<>(comm.getNodeIds()));
            c.setSymbolCount(comm.getNodeCount());
            c.setCohesion(comm.getCohesion());
            String dominantPackage = findDominantPackage(comm.getNodeIds(), symbolTable);
            c.setDominantPackage(dominantPackage);
            c.setLabel(generateCommunityLabel(dominantPackage, comm.getNodeCount()));
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

    private ImpactResultDTO convertImpactResult(ImpactResult result, SymbolTable symbolTable,
                                                  String qualifiedName) {
        ImpactResultDTO dto = new ImpactResultDTO();
        dto.setTargetSymbolId(result.getTargetNodeId());
        dto.setTargetQualifiedName(qualifiedName);
        dto.setRiskLevel(result.getRiskLevel());
        dto.setUpstream(result.getUpstream().stream()
                .map(node -> toImpactedSymbolDTO(node, symbolTable))
                .collect(Collectors.toList()));
        dto.setDownstream(result.getDownstream().stream()
                .map(node -> toImpactedSymbolDTO(node, symbolTable))
                .collect(Collectors.toList()));
        return dto;
    }

    private ImpactedSymbolDTO toImpactedSymbolDTO(ImpactedNode node, SymbolTable symbolTable) {
        ImpactedSymbolDTO dto = new ImpactedSymbolDTO();
        dto.setSymbolId(node.getNodeId());
        dto.setDepth(node.getDepth());
        CodeSymbol symbol = symbolTable.getById(node.getNodeId());
        if (symbol != null) {
            dto.setQualifiedName(symbol.getQualifiedName());
            dto.setName(symbol.getName());
            dto.setKind(symbol.getKind() != null ? symbol.getKind().name() : null);
            dto.setFilePath(ExtDataHelper.extractFilePath(symbol.getExtData()));
        } else {
            dto.setQualifiedName(node.getNodeId());
        }
        return dto;
    }

    private List<CriticalNodeScoreDTO> computeHubNodeScores(CallGraph callGraph, SymbolTable symbolTable, int topN) {
        Map<String, int[]> degrees = new HashMap<>();
        for (String caller : callGraph.getAllNodeIds()) {
            int[] deg = degrees.computeIfAbsent(caller, k -> new int[2]);
            List<String> callees = callGraph.getCallees(caller);
            deg[1] += callees.size();
            for (String callee : callees) {
                int[] calleeDeg = degrees.computeIfAbsent(callee, k -> new int[2]);
                calleeDeg[0]++;
            }
        }
        return degrees.entrySet().stream()
                .map(entry -> {
                    String nodeId = entry.getKey();
                    int inDeg = entry.getValue()[0];
                    int outDeg = entry.getValue()[1];
                    int totalDeg = inDeg + outDeg;
                    CriticalNodeScoreDTO dto = new CriticalNodeScoreDTO();
                    dto.setSymbolId(nodeId);
                    dto.setInDegree(inDeg);
                    dto.setOutDegree(outDeg);
                    dto.setTotalDegree(totalDeg);
                    dto.setScore(totalDeg);
                    CodeSymbol sym = symbolTable.getById(nodeId);
                    dto.setQualifiedName(sym != null ? sym.getQualifiedName() : nodeId);
                    return dto;
                })
                .sorted(Comparator.comparingDouble(CriticalNodeScoreDTO::getScore).reversed())
                .limit(topN)
                .collect(Collectors.toList());
    }

    private List<CriticalNodeScoreDTO> computeBridgeNodeScores(CallGraph callGraph, SymbolTable symbolTable,
                                                                int topN, Set<String> nodeSet) {
        Map<String, Double> betweennessScores = BetweennessCentrality.compute(new CodeCallGraph(callGraph), nodeSet);
        return betweennessScores.entrySet().stream()
                .map(entry -> {
                    String nodeId = entry.getKey();
                    double betweenness = entry.getValue();
                    CriticalNodeScoreDTO dto = new CriticalNodeScoreDTO();
                    dto.setSymbolId(nodeId);
                    dto.setScore(betweenness);
                    List<String> callees = callGraph.getCallees(nodeId);
                    List<String> callers = callGraph.getCallers(nodeId);
                    dto.setInDegree(callers.size());
                    dto.setOutDegree(callees.size());
                    dto.setTotalDegree(callers.size() + callees.size());
                    CodeSymbol sym = symbolTable.getById(nodeId);
                    dto.setQualifiedName(sym != null ? sym.getQualifiedName() : nodeId);
                    return dto;
                })
                .sorted(Comparator.comparingDouble(CriticalNodeScoreDTO::getScore).reversed())
                .limit(topN)
                .collect(Collectors.toList());
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

    private GraphDiffDTO convertGraphDiff(GraphDiff diff) {
        GraphDiffDTO dto = new GraphDiffDTO();
        dto.setAddedNodes(diff.getAddedNodes());
        dto.setRemovedNodes(diff.getRemovedNodes());
        dto.setAddedEdges(diff.getAddedEdges().stream()
                .map(e -> new EdgeKeyDTO(e.getSourceId(), e.getTargetId()))
                .collect(Collectors.toSet()));
        dto.setRemovedEdges(diff.getRemovedEdges().stream()
                .map(e -> new EdgeKeyDTO(e.getSourceId(), e.getTargetId()))
                .collect(Collectors.toSet()));
        dto.setCommunityChanges(diff.getCommunityChanges().stream()
                .map(cc -> {
                    CommunityChangeDTO c = new CommunityChangeDTO();
                    c.setNodeId(cc.getNodeId());
                    c.setOldCommunity(String.valueOf(cc.getOldCommunity()));
                    c.setNewCommunity(String.valueOf(cc.getNewCommunity()));
                    return c;
                }).collect(Collectors.toList()));
        return dto;
    }

    private String findDominantPackage(Set<String> nodeIds, SymbolTable symbolTable) {
        Map<String, Integer> packageCount = new HashMap<>();
        for (String nodeId : nodeIds) {
            CodeSymbol symbol = symbolTable.getById(nodeId);
            if (symbol != null && symbol.getQualifiedName() != null) {
                String pkg = extractPackage(symbol.getQualifiedName());
                packageCount.merge(pkg, 1, Integer::sum);
            }
        }
        return packageCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }

    private static String extractPackage(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot > 0) {
            String className = qualifiedName.substring(0, lastDot);
            int secondLastDot = className.lastIndexOf('.');
            if (secondLastDot > 0) {
                return className.substring(0, secondLastDot);
            }
            return className;
        }
        return "default";
    }

    private static String generateCommunityLabel(String dominantPackage, int nodeCount) {
        if (!"unknown".equals(dominantPackage) && !"default".equals(dominantPackage)) {
            String[] parts = dominantPackage.split("\\.");
            if (parts.length >= 2) {
                return parts[parts.length - 2] + "." + parts[parts.length - 1];
            } else if (parts.length == 1) {
                return parts[0];
            }
        }
        return "cluster_" + nodeCount;
    }

    private static CodeSymbol findSymbolByQualifiedName(SymbolTable symbolTable, String qualifiedName) {
        CodeSymbol exact = symbolTable.getByQualifiedName(qualifiedName);
        if (exact != null) {
            return exact;
        }
        int parenIndex = qualifiedName.indexOf('(');
        if (parenIndex > 0) {
            String withoutParams = qualifiedName.substring(0, parenIndex);
            CodeSymbol exactWithoutParams = symbolTable.getByQualifiedName(withoutParams);
            if (exactWithoutParams != null) {
                return exactWithoutParams;
            }
            CodeSymbol bestMatch = null;
            for (CodeSymbol symbol : symbolTable.getAll()) {
                if (symbol.getQualifiedName() != null &&
                    symbol.getQualifiedName().startsWith(withoutParams + ".")) {
                    if (bestMatch == null) {
                        bestMatch = symbol;
                    }
                }
            }
            return bestMatch;
        }
        return null;
    }

    private Map<String, Integer> buildCommunityMap(CommunityResult result) {
        Map<String, Integer> map = new HashMap<>();
        for (CommunityInfo comm : result.getCommunities()) {
            for (String nodeId : comm.getNodeIds()) {
                map.put(nodeId, comm.getId());
            }
        }
        return map;
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
        List<NopCodeDependency> deps = cacheManager.getOrRebuildDependencies(indexId, daoProvider);

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

        List<NopCodeDependency> allDeps = cacheManager.getOrRebuildDependencies(indexId, daoProvider);

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
        List<NopCodeDependency> deps = cacheManager.getOrRebuildDependencies(indexId, daoProvider);

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
        List<NopCodeDependency> deps = cacheManager.getOrRebuildDependencies(indexId, daoProvider);

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
        List<NopCodeDependency> deps = cacheManager.getOrRebuildDependencies(indexId, daoProvider);

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

        for (String startNode : allNodes) {
            if (nodeIndex.containsKey(startNode)) continue;

            Deque<Object[]> callStack = new ArrayDeque<>();
            callStack.push(new Object[]{startNode, 0, false});

            while (!callStack.isEmpty()) {
                Object[] frame = callStack.pop();
                String v = (String) frame[0];
                int edgeIdx = (Integer) frame[1];
                boolean returning = (Boolean) frame[2];

                if (!returning && !nodeIndex.containsKey(v)) {
                    nodeIndex.put(v, index[0]);
                    lowLink.put(v, index[0]);
                    index[0]++;
                    stack.push(v);
                    onStack.add(v);
                }

                List<String> neighbors = adj.getOrDefault(v, Collections.emptyList());
                boolean pushedChild = false;
                for (int i = edgeIdx; i < neighbors.size(); i++) {
                    String w = neighbors.get(i);
                    if (!nodeIndex.containsKey(w)) {
                        callStack.push(new Object[]{v, i + 1, true});
                        callStack.push(new Object[]{w, 0, false});
                        pushedChild = true;
                        break;
                    } else if (onStack.contains(w)) {
                        lowLink.put(v, Math.min(lowLink.get(v), nodeIndex.get(w)));
                    }
                }

                if (!pushedChild && returning) {
                    if (edgeIdx > 0) {
                        String w = neighbors.get(edgeIdx - 1);
                        lowLink.put(v, Math.min(lowLink.get(v), lowLink.get(w)));
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
        }
        return result;
    }
}
