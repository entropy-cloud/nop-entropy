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
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.core.entrypoint.EntryPointScorer;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.CodeCallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.*;
import io.nop.code.core.util.BfsNode;
import io.nop.code.core.util.ExtDataHelper;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.code.dao.entity.NopCodeDependency;
import io.nop.code.dao.entity.NopCodeInheritance;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.api.dto.ExplorationQuestionDTO;
import io.nop.code.api.dto.SurprisingConnectionDTO;
import io.nop.code.api.dto.*;
import io.nop.code.core.graph.CodeRelationGraph;
import io.nop.code.service.graph.CodeRelationGraphLoader;
import io.nop.code.service.graph.GraphMetricStore;
import io.nop.code.api.dto.GraphWikiDTO;
import io.nop.code.service.graph.GraphQuestionGenerator;
import io.nop.code.service.graph.GraphWikiExporter;
import io.nop.code.service.graph.SurprisingConnectionAnalyzer;
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

    private static final int SYMBOL_LOOKUP_CHUNK = 500;

    private final IDaoProvider daoProvider;
    private final CodeCacheManager cacheManager;
    private final GraphMetricStore metricStore;
    private final GraphMetricMaterializer metricMaterializer;

    CodeGraphService(IDaoProvider daoProvider, CodeCacheManager cacheManager,
                     GraphMetricMaterializer metricMaterializer) {
        this.daoProvider = daoProvider;
        this.cacheManager = cacheManager;
        this.metricStore = daoProvider != null ? new GraphMetricStore(daoProvider) : null;
        this.metricMaterializer = metricMaterializer;
    }

    // WP-6 AR-76/61: when the cached symbol/call-graph was built from partial data (exceeded
    // MAX_CACHE_SYMBOLS/MAX_CACHE_EDGES), graph results are derived from incomplete input — warn so
    // consumers know the output may be partial. Best-effort: proceed with available data.
    private static void warnIfCacheTruncated(SymbolTable symbolTable, CallGraph callGraph, String indexId) {
        if (symbolTable != null && symbolTable.isTruncated()) {
            LOG.warn("Symbol table cache for index {} is truncated; graph analysis may be incomplete", indexId);
        }
        if (callGraph != null && callGraph.isTruncated()) {
            LOG.warn("Call graph cache for index {} is truncated; graph analysis may be incomplete", indexId);
        }
    }

    CommunityDetectionResultDTO detectCommunities(String indexId) {
        if (daoProvider == null) return null;
        if (metricStore.hasRequiredFamilies(indexId, GraphMetricStore.METRIC_COMMUNITY,
                GraphMetricStore.METRIC_COMMUNITY_INFO, GraphMetricStore.METRIC_GRAPH_SUMMARY)) {
            CommunityDetectionResultDTO assembled = assembleCommunitiesFromMetrics(indexId);
            if (assembled != null) return assembled;
        }
        // self-healing fallback: one computation, persisted, returned through the same read path
        CallGraph callGraph = cacheManager.getOrRebuildCallGraph(indexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        if (symbolTable.size() == 0) return null;
        warnIfCacheTruncated(symbolTable, callGraph, indexId);
        if (metricMaterializer != null) {
            try {
                metricMaterializer.materialize(indexId);
                CommunityDetectionResultDTO assembled = assembleCommunitiesFromMetrics(indexId);
                if (assembled != null) return assembled;
            } catch (Exception e) {
                LOG.warn("Materialized community detection failed for {}; using in-memory computation", indexId, e);
            }
        }
        CommunityResult result = runCommunityDetection(callGraph);
        return convertCommunityResult(result, symbolTable);
    }

    /**
     * Assembles the communities DTO from materialized COMMUNITY / COMMUNITY_INFO /
     * GRAPH_SUMMARY rows. Returns null when required rows are missing or inconsistent —
     * callers fall back to computation. totalSymbols uses callGraphNodeCount (the lazy
     * path's totalSymbols equals the call-graph node set); processingTimeMs is 0 for
     * materialized reads by contract.
     */
    private CommunityDetectionResultDTO assembleCommunitiesFromMetrics(String indexId) {
        try {
            Map<String, Object> summary = metricStore.loadSummary(indexId);
            Map<String, Integer> membership = metricStore.loadCommunities(indexId);
            Map<Integer, Double> cohesionByCommunity = metricStore.loadCommunityInfo(indexId);
            if (summary == null || membership.isEmpty()) {
                return null;
            }
            int callGraphNodeCount = asInt(summary.get("callGraphNodeCount"));
            if (membership.size() != callGraphNodeCount) {
                return null;
            }
            Map<String, NopCodeSymbol> symbolsById = loadSymbolsByIds(membership.keySet());

            Map<Integer, List<String>> byCommunity = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : membership.entrySet()) {
                byCommunity.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
            }

            List<CommunityDTO> communities = new ArrayList<>();
            for (Map.Entry<Integer, List<String>> entry : byCommunity.entrySet()) {
                List<String> nodeIds = entry.getValue();
                CommunityDTO c = new CommunityDTO();
                c.setId(String.valueOf(entry.getKey()));
                c.setSymbolIds(nodeIds);
                c.setSymbolCount(nodeIds.size());
                Double cohesion = cohesionByCommunity.get(entry.getKey());
                c.setCohesion(cohesion != null ? cohesion : 0.0);
                String dominantPackage = findDominantPackageByNames(nodeIds, symbolsById);
                c.setDominantPackage(dominantPackage);
                c.setLabel(generateCommunityLabel(dominantPackage, nodeIds.size()));
                communities.add(c);
            }

            CommunityDetectionResultDTO dto = new CommunityDetectionResultDTO();
            dto.setCommunities(communities);
            dto.setTotalSymbols(callGraphNodeCount);
            dto.setTotalCommunities(communities.size());
            Object avg = summary.get("averageCohesion");
            dto.setAverageCohesion(avg instanceof Number ? ((Number) avg).doubleValue() : 0.0);
            Object modularity = summary.get("modularity");
            dto.setModularity(modularity instanceof Number ? ((Number) modularity).doubleValue() : 0.0);
            Object algo = summary.get("algorithmUsed");
            dto.setAlgorithmUsed(algo != null ? algo.toString() : null);
            dto.setProcessingTimeMs(0);
            return dto;
        } catch (Exception e) {
            LOG.warn("Failed to assemble communities from materialized metrics for {}", indexId, e);
            return null;
        }
    }

    GraphAnalysisResultDTO getGraphAnalysis(String indexId, int topN) {
        if (daoProvider == null) return null;
        if (metricStore.hasRequiredFamilies(indexId, GraphMetricStore.METRIC_ENTRY_POINT,
                GraphMetricStore.METRIC_HUB, GraphMetricStore.METRIC_GRAPH_SUMMARY)) {
            GraphAnalysisResultDTO assembled = assembleGraphAnalysisFromMetrics(indexId, topN);
            if (assembled != null) return assembled;
        }
        // self-healing fallback: one computation, persisted, returned through the same read path
        CallGraph callGraph = cacheManager.getOrRebuildCallGraph(indexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        warnIfCacheTruncated(symbolTable, callGraph, indexId);
        if (metricMaterializer != null) {
            try {
                metricMaterializer.materialize(indexId);
                GraphAnalysisResultDTO assembled = assembleGraphAnalysisFromMetrics(indexId, topN);
                if (assembled != null) return assembled;
            } catch (Exception e) {
                LOG.warn("Materialized graph analysis failed for {}; using in-memory computation", indexId, e);
            }
        }
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

    /**
     * Assembles the graph-analysis DTO from materialized ENTRY_POINT / HUB / GRAPH_SUMMARY
     * rows. Read ordering follows rankNo (materialized generation order). extractedCount =
     * HUB row count (symbols with call edges); inferredCount = symbolCount - extracted.
     */
    private GraphAnalysisResultDTO assembleGraphAnalysisFromMetrics(String indexId, int topN) {
        try {
            Map<String, Object> summary = metricStore.loadSummary(indexId);
            if (summary == null) {
                return null;
            }
            int symbolCount = asInt(summary.get("symbolCount"));
            List<NopCodeGraphMetric> entryPoints = metricStore.loadEntryPoints(indexId);
            if (entryPoints.isEmpty()) {
                return null;
            }
            Map<String, int[]> hubs = metricStore.loadHubs(indexId);
            int limit = topN > 0 ? topN : 20;
            Map<String, NopCodeSymbol> symbolsById = loadSymbolsByIds(
                    entryPoints.stream().map(NopCodeGraphMetric::getSymbolId).collect(Collectors.toSet()));

            List<GodNodeDTO> godNodes = new ArrayList<>();
            List<String> isolatedSymbols = new ArrayList<>();
            for (NopCodeGraphMetric row : entryPoints) {
                int[] degree = hubs.get(row.getSymbolId());
                int inDeg = degree != null ? degree[1] : 0;
                int outDeg = degree != null ? degree[2] : 0;
                if (godNodes.size() < limit) {
                    GodNodeDTO node = new GodNodeDTO();
                    node.setSymbolId(row.getSymbolId());
                    node.setQualifiedName(qualifiedNameOf(symbolsById, row.getSymbolId()));
                    NopCodeSymbol symbolEntity = symbolsById.get(row.getSymbolId());
                    node.setKind(symbolEntity != null && symbolEntity.getKind() != null
                            ? symbolEntity.getKind() : null);
                    node.setDegree(inDeg + outDeg);
                    node.setCallerCount(inDeg);
                    node.setCalleeCount(outDeg);
                    godNodes.add(node);
                }
                if (EntryPointScorer.EntryPointType.ISOLATED.name().equals(row.getEntryPointType())
                        && isolatedSymbols.size() < limit) {
                    isolatedSymbols.add(qualifiedNameOf(symbolsById, row.getSymbolId()));
                }
            }

            int extractedCount = hubs.size();
            int inferredCount = Math.max(0, symbolCount - extractedCount);
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
        } catch (Exception e) {
            LOG.warn("Failed to assemble graph analysis from materialized metrics for {}", indexId, e);
            return null;
        }
    }

    List<SurprisingConnectionDTO> getSurprisingConnections(String indexId, int topN, Integer minScore) {
        if (daoProvider == null) return null;
        ensureMaterializedForGraphMetrics(indexId);
        // names and filePaths come from the cached symbol table projection (N1.4 read-cache
        // semantics: structure-adjacent queries keep the lazy view until the N6.2 DB backend)
        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        Function<String, String> nameResolver = symbolId -> {
            CodeSymbol sym = symbolTable.getById(symbolId);
            return sym != null && sym.getQualifiedName() != null ? sym.getQualifiedName() : symbolId;
        };
        Function<String, String> filePathResolver = symbolId -> {
            CodeSymbol sym = symbolTable.getById(symbolId);
            return sym != null && sym.getFilePath() != null ? sym.getFilePath() : null;
        };
        CodeRelationGraph graph = CodeRelationGraphLoader.load(indexId, daoProvider, filePathResolver);
        Map<String, Integer> communities = metricStore.loadCommunities(indexId);
        return new SurprisingConnectionAnalyzer().analyze(graph, communities, nameResolver, topN, minScore);
    }

    /**
     * Communities for surprise scoring, materialization-first with the N1.3 gating: a missing
     * GRAPH_SUMMARY row means never materialized -> self-heal once; GRAPH_SUMMARY present but
     * COMMUNITY rows absent means the graph was too small -> degrade to an empty map without
     * re-materializing.
     */
    private void ensureMaterializedForGraphMetrics(String indexId) {
        if (metricStore.loadSummary(indexId) == null && metricMaterializer != null) {
            try {
                metricMaterializer.materialize(indexId);
            } catch (Exception e) {
                LOG.warn("Self-heal materialization failed for {}; surprise scoring degrades", indexId, e);
            }
        }
    }

    List<ExplorationQuestionDTO> getExplorationQuestions(String indexId, int topN) {
        if (daoProvider == null) return null;
        ensureMaterializedForGraphMetrics(indexId);
        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        Function<String, String> nameResolver = symbolId -> {
            CodeSymbol sym = symbolTable.getById(symbolId);
            return sym != null && sym.getQualifiedName() != null ? sym.getQualifiedName() : symbolId;
        };
        CodeRelationGraph graph = CodeRelationGraphLoader.load(indexId, daoProvider, null);
        Map<String, Integer> communities = metricStore.loadCommunities(indexId);

        // community sizes: count COMMUNITY rows per communityId; cohesion: COMMUNITY_INFO rows
        Map<Integer, Integer> communitySizes = new HashMap<>();
        Map<Integer, Double> communityCohesion = metricStore.loadCommunityInfo(indexId);
        for (Map.Entry<String, Integer> entry : communities.entrySet()) {
            communitySizes.merge(entry.getValue(), 1, Integer::sum);
        }
        List<Map.Entry<String, Double>> betweenness = new ArrayList<>();
        for (Map.Entry<String, Double> entry : metricStore.loadScores(indexId,
                GraphMetricStore.METRIC_BETWEENNESS).entrySet()) {
            betweenness.add(new HashMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
        }

        return new GraphQuestionGenerator().generate(graph, symbolTable.getAll(), communities,
                communitySizes, communityCohesion, betweenness, indexId, topN);
    }

    GraphWikiDTO exportGraphWiki(String indexId, Integer maxCommunities, Integer maxHubNodes) {
        if (daoProvider == null) return null;
        ensureMaterializedForGraphMetrics(indexId);
        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        Function<String, String> nameResolver = symbolId -> {
            CodeSymbol sym = symbolTable.getById(symbolId);
            return sym != null && sym.getQualifiedName() != null ? sym.getQualifiedName() : symbolId;
        };
        Function<String, String> filePathResolver = symbolId -> {
            CodeSymbol sym = symbolTable.getById(symbolId);
            return sym != null && sym.getFilePath() != null ? sym.getFilePath() : null;
        };
        CodeRelationGraph graph = CodeRelationGraphLoader.load(indexId, daoProvider, filePathResolver);
        Map<String, Integer> communities = metricStore.loadCommunities(indexId);
        Map<Integer, Double> cohesion = metricStore.loadCommunityInfo(indexId);
        return new GraphWikiExporter().exportWiki(graph, communities, cohesion, nameResolver,
                filePathResolver, maxCommunities != null && maxCommunities > 0 ? maxCommunities : 20,
                maxHubNodes != null && maxHubNodes > 0 ? maxHubNodes : 20);
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
        if (metricStore.hasRequiredFamilies(indexId, GraphMetricStore.METRIC_HUB,
                GraphMetricStore.METRIC_GRAPH_SUMMARY)) {
            CriticalNodeResultDTO assembled = assembleCriticalNodesFromMetrics(indexId, topN);
            if (assembled != null) return assembled;
        }
        // self-healing fallback: one computation, persisted, returned through the same read path
        CallGraph callGraph = cacheManager.getOrRebuildCallGraph(indexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);
        if (metricMaterializer != null) {
            try {
                metricMaterializer.materialize(indexId);
                CriticalNodeResultDTO assembled = assembleCriticalNodesFromMetrics(indexId, topN);
                if (assembled != null) return assembled;
            } catch (Exception e) {
                LOG.warn("Materialized critical node analysis failed for {}; using in-memory computation", indexId, e);
            }
        }
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
                    // superTypeId 落库形态可能是全限定名（未解析的遗留/增量行）也可能是符号 ID
                    // （全量索引写入即解析），sub 方向遍历两种都要匹配
                    List<String> superIdSubList = new ArrayList<>(qnSubList.size());
                    for (String qn : qnSubList) {
                        CodeSymbol superSym = qn != null ? table.getByQualifiedName(qn) : null;
                        if (superSym != null)
                            superIdSubList.add(superSym.getId());
                    }
                    List<TreeBean> orFilters = new ArrayList<>(3);
                    orFilters.add(FilterBeans.in("subTypeId", subList));
                    if (!qnSubList.isEmpty())
                        orFilters.add(FilterBeans.in("superTypeId", qnSubList));
                    if (!superIdSubList.isEmpty())
                        orFilters.add(FilterBeans.in("superTypeId", superIdSubList));
                    QueryBean q = new QueryBean();
                    q.addFilter(FilterBeans.eq("indexId", indexId));
                    q.addFilter(orFilters.size() == 1 ? orFilters.get(0)
                            : FilterBeans.or(orFilters));
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

    /**
     * Assembles the critical-nodes DTO from materialized HUB / GRAPH_SUMMARY rows (+
     * BETWEENNESS rows when present). Reproduces the legacy >10000-symbol guard: hub and
     * bridge lists are both empty. bridgeNodes degrade to empty when BETWEENNESS rows are
     * absent (large-graph skip), which matches the legacy skip-and-continue semantics.
     */
    private CriticalNodeResultDTO assembleCriticalNodesFromMetrics(String indexId, int topN) {
        try {
            Map<String, Object> summary = metricStore.loadSummary(indexId);
            if (summary == null) {
                return null;
            }
            int symbolCount = asInt(summary.get("symbolCount"));
            Map<String, int[]> hubs = metricStore.loadHubs(indexId);
            Map<String, NopCodeSymbol> symbolsById = loadSymbolsByIds(hubs.keySet());

            CriticalNodeResultDTO dto = new CriticalNodeResultDTO();
            dto.setTotalNodes(symbolCount);
            dto.setTopN(topN);
            if (symbolCount > MAX_NODES_FOR_COMMUNITY_DETECTION) {
                LOG.warn("Graph too large for critical node analysis ({} > {}), hub/bridge lists empty",
                        symbolCount, MAX_NODES_FOR_COMMUNITY_DETECTION);
                dto.setHubNodes(Collections.emptyList());
                dto.setBridgeNodes(Collections.emptyList());
                return dto;
            }

            int limit = topN > 0 ? topN : 20;
            List<CriticalNodeScoreDTO> hubNodes = hubs.entrySet().stream()
                    .map(entry -> {
                        String symbolId = entry.getKey();
                        int[] deg = entry.getValue();
                        CriticalNodeScoreDTO node = new CriticalNodeScoreDTO();
                        node.setSymbolId(symbolId);
                        node.setInDegree(deg[1]);
                        node.setOutDegree(deg[2]);
                        node.setTotalDegree(deg[0]);
                        node.setScore(deg[0]);
                        node.setQualifiedName(qualifiedNameOf(symbolsById, symbolId));
                        return node;
                    })
                    .sorted(java.util.Comparator.comparingDouble(CriticalNodeScoreDTO::getScore).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
            dto.setHubNodes(hubNodes);

            List<NopCodeGraphMetric> bridges = metricStore.loadBetweenness(indexId);
            List<CriticalNodeScoreDTO> bridgeNodes = new ArrayList<>();
            for (NopCodeGraphMetric row : bridges) {
                if (bridgeNodes.size() >= limit) break;
                CriticalNodeScoreDTO node = new CriticalNodeScoreDTO();
                node.setSymbolId(row.getSymbolId());
                node.setScore(row.getScore() != null ? row.getScore() : 0.0);
                int[] deg = hubs.get(row.getSymbolId());
                node.setInDegree(deg != null ? deg[1] : 0);
                node.setOutDegree(deg != null ? deg[2] : 0);
                node.setTotalDegree(deg != null ? deg[0] : 0);
                node.setQualifiedName(qualifiedNameOf(symbolsById, row.getSymbolId()));
                bridgeNodes.add(node);
            }
            dto.setBridgeNodes(bridgeNodes);
            return dto;
        } catch (Exception e) {
            LOG.warn("Failed to assemble critical nodes from materialized metrics for {}", indexId, e);
            return null;
        }
    }

    /**
     * Chunked id->entity lookup on NopCodeSymbol (qualifiedName + kind) — never a full
     * symbol-table load. Preserves the GraphQL contract: GodNodeDTO.kind is populated.
     */
    private Map<String, NopCodeSymbol> loadSymbolsByIds(java.util.Collection<String> symbolIds) {
        Map<String, NopCodeSymbol> result = new HashMap<>();
        if (symbolIds.isEmpty()) {
            return result;
        }
        List<String> ids = new ArrayList<>(symbolIds);
        IEntityDao<NopCodeSymbol> dao = daoProvider.daoFor(NopCodeSymbol.class);
        for (int from = 0; from < ids.size(); from += SYMBOL_LOOKUP_CHUNK) {
            List<String> chunk = ids.subList(from, Math.min(from + SYMBOL_LOOKUP_CHUNK, ids.size()));
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.in("id", chunk));
            query.setLimit(SYMBOL_LOOKUP_CHUNK);
            for (NopCodeSymbol entity : dao.findAllByQuery(query)) {
                result.put(entity.getId(), entity);
            }
        }
        return result;
    }

    private static String qualifiedNameOf(Map<String, NopCodeSymbol> symbols, String symbolId) {
        NopCodeSymbol entity = symbols.get(symbolId);
        return entity != null ? entity.getQualifiedName() : symbolId;
    }

    /** Mirrors findDominantPackage but over preloaded symbol entities. */
    private String findDominantPackageByNames(List<String> nodeIds, Map<String, NopCodeSymbol> symbolsById) {
        Map<String, Integer> packageCounts = new HashMap<>();
        for (String nodeId : nodeIds) {
            NopCodeSymbol entity = symbolsById.get(nodeId);
            if (entity == null || entity.getQualifiedName() == null) continue;
            String pkg = extractPackage(entity.getQualifiedName());
            packageCounts.merge(pkg, 1, Integer::sum);
        }
        return packageCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static int asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
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

                // propagate lowLink from the child subtree that just completed before scanning
                // further neighbors; otherwise only the last child's lowLink would be merged
                if (returning && edgeIdx > 0) {
                    String child = neighbors.get(edgeIdx - 1);
                    lowLink.put(v, Math.min(lowLink.get(v), lowLink.get(child)));
                }

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

                // SCC root check runs for every finished frame, including frames that never
                // pushed a child (leaves / all neighbors visited): otherwise such nodes stay on
                // the Tarjan stack and are swallowed into an ancestor's SCC
                if (!pushedChild) {
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
