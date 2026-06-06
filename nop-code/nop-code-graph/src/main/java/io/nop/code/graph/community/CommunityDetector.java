package io.nop.code.graph.community;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import nl.cwts.networkanalysis.Clustering;
import nl.cwts.networkanalysis.LeidenAlgorithm;
import nl.cwts.networkanalysis.Network;
import nl.cwts.util.LargeIntArray;
import org.jgrapht.Graph;
import org.jgrapht.alg.clustering.LabelPropagationClustering;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;

public class CommunityDetector {
    
    private static final Logger LOG = LoggerFactory.getLogger(CommunityDetector.class);
    
    public enum AlgorithmType {
        LEIDEN,
        LABEL_PROPAGATION
    }
    
    public static class CommunityDetectionResult {
        private List<Community> communities;
        private int totalSymbols;
        private int totalCommunities;
        private int clusteredSymbols;
        private double averageCohesion;
        
        private boolean largeGraphMode;
        private int filteredNodes;
        private int nodesProcessed;
        private long processingTimeMs;
        private AlgorithmType algorithmUsed;
        private double resolution;
        private double modularity;
        
        public List<Community> getCommunities() {
            return communities != null ? communities : Collections.emptyList();
        }
        
        public void setCommunities(List<Community> communities) {
            this.communities = communities;
        }
        
        public int getTotalSymbols() {
            return totalSymbols;
        }
        
        public void setTotalSymbols(int totalSymbols) {
            this.totalSymbols = totalSymbols;
        }
        
        public int getTotalCommunities() {
            return totalCommunities;
        }
        
        public void setTotalCommunities(int totalCommunities) {
            this.totalCommunities = totalCommunities;
        }
        
        public int getClusteredSymbols() {
            return clusteredSymbols;
        }
        
        public void setClusteredSymbols(int clusteredSymbols) {
            this.clusteredSymbols = clusteredSymbols;
        }
        
        public double getAverageCohesion() {
            return averageCohesion;
        }
        
        public void setAverageCohesion(double averageCohesion) {
            this.averageCohesion = averageCohesion;
        }
        
        public boolean isLargeGraphMode() {
            return largeGraphMode;
        }
        
        public void setLargeGraphMode(boolean largeGraphMode) {
            this.largeGraphMode = largeGraphMode;
        }
        
        public int getFilteredNodes() {
            return filteredNodes;
        }
        
        public void setFilteredNodes(int filteredNodes) {
            this.filteredNodes = filteredNodes;
        }
        
        public int getNodesProcessed() {
            return nodesProcessed;
        }
        
        public void setNodesProcessed(int nodesProcessed) {
            this.nodesProcessed = nodesProcessed;
        }
        
        public long getProcessingTimeMs() {
            return processingTimeMs;
        }
        
        public void setProcessingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
        }
        
        public AlgorithmType getAlgorithmUsed() {
            return algorithmUsed;
        }
        
        public void setAlgorithmUsed(AlgorithmType algorithmUsed) {
            this.algorithmUsed = algorithmUsed;
        }
        
        public double getResolution() {
            return resolution;
        }
        
        public void setResolution(double resolution) {
            this.resolution = resolution;
        }
        
        public double getModularity() {
            return modularity;
        }
        
        public void setModularity(double modularity) {
            this.modularity = modularity;
        }
    }
    
    public static class Community {
        private String id;
        private String label;
        private List<String> symbolIds;
        private int symbolCount;
        private double cohesion;
        private String dominantPackage;
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getLabel() {
            return label;
        }
        
        public void setLabel(String label) {
            this.label = label;
        }
        
        public List<String> getSymbolIds() {
            return symbolIds != null ? symbolIds : new ArrayList<>();
        }
        
        public void setSymbolIds(List<String> symbolIds) {
            this.symbolIds = symbolIds;
            this.symbolCount = symbolIds != null ? symbolIds.size() : 0;
        }
        
        public int getSymbolCount() {
            return symbolCount;
        }
        
        public double getCohesion() {
            return cohesion;
        }
        
        public void setCohesion(double cohesion) {
            this.cohesion = cohesion;
        }
        
        public String getDominantPackage() {
            return dominantPackage;
        }
        
        public void setDominantPackage(String dominantPackage) {
            this.dominantPackage = dominantPackage;
        }
        
        @Override
        public String toString() {
            return String.format("Community{id=%s, label=%s, count=%d, cohesion=%.2f}",
                    id, label, symbolCount, cohesion);
        }
    }
    
    public static class CommunityConfig {
        private int minCommunitySize = 2;
        private int maxIterations = 10;
        
        private int largeGraphThreshold = 10_000;
        private int largeGraphMaxIterations = 3;
        private int minNodeDegree = 2;
        
        private AlgorithmType algorithm = AlgorithmType.LEIDEN;
        
        private double resolution = 0.1;
        private double randomness = 0.01;
        
        private boolean enableTimeout = false;
        private long timeoutMs = 60000;
        
        public int getMinCommunitySize() {
            return minCommunitySize;
        }
        
        public void setMinCommunitySize(int minCommunitySize) {
            this.minCommunitySize = minCommunitySize;
        }
        
        public int getMaxIterations() {
            return maxIterations;
        }
        
        public void setMaxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
        }
        
        public int getLargeGraphThreshold() {
            return largeGraphThreshold;
        }
        
        public void setLargeGraphThreshold(int largeGraphThreshold) {
            this.largeGraphThreshold = largeGraphThreshold;
        }
        
        public int getLargeGraphMaxIterations() {
            return largeGraphMaxIterations;
        }
        
        public void setLargeGraphMaxIterations(int largeGraphMaxIterations) {
            this.largeGraphMaxIterations = largeGraphMaxIterations;
        }
        
        public int getMinNodeDegree() {
            return minNodeDegree;
        }
        
        public void setMinNodeDegree(int minNodeDegree) {
            this.minNodeDegree = minNodeDegree;
        }
        
        public AlgorithmType getAlgorithm() {
            return algorithm;
        }
        
        public void setAlgorithm(AlgorithmType algorithm) {
            this.algorithm = algorithm;
        }
        
        public double getResolution() {
            return resolution;
        }
        
        public void setResolution(double resolution) {
            this.resolution = resolution;
        }
        
        public double getRandomness() {
            return randomness;
        }
        
        public void setRandomness(double randomness) {
            this.randomness = randomness;
        }
        
        public boolean isEnableTimeout() {
            return enableTimeout;
        }
        
        public void setEnableTimeout(boolean enableTimeout) {
            this.enableTimeout = enableTimeout;
        }
        
        public long getTimeoutMs() {
            return timeoutMs;
        }
        
        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
        
        public static CommunityConfig defaultConfig() {
            return new CommunityConfig();
        }
        
        public static CommunityConfig leidenConfig() {
            CommunityConfig config = new CommunityConfig();
            config.setAlgorithm(AlgorithmType.LEIDEN);
            return config;
        }
        
        public static CommunityConfig largeGraphConfig() {
            CommunityConfig config = new CommunityConfig();
            config.setLargeGraphThreshold(5_000);
            config.setMinNodeDegree(2);
            config.setLargeGraphMaxIterations(3);
            config.setEnableTimeout(true);
            config.setTimeoutMs(120000);
            return config;
        }
    }
    
    public CommunityDetectionResult detectCommunities(
            CallGraph callGraph,
            SymbolTable symbolTable) {
        return detectCommunities(callGraph, symbolTable, CommunityConfig.defaultConfig());
    }
    
    public CommunityDetectionResult detectCommunities(
            CallGraph callGraph,
            SymbolTable symbolTable,
            CommunityConfig config) {
        
        long startTime = System.currentTimeMillis();
        
        Map<String, Integer> nodeDegree = calculateNodeDegrees(callGraph);
        
        int totalSymbols = nodeDegree.size();
        if (totalSymbols == 0) {
            return createEmptyResult(0);
        }
        
        boolean isLargeGraph = totalSymbols > config.getLargeGraphThreshold();
        int minDegree = isLargeGraph ? config.getMinNodeDegree() : 1;
        
        if (isLargeGraph) {
            LOG.info("Large graph detected ({} symbols), enabling optimizations: minDegree={}, algorithm={}", 
                    totalSymbols, minDegree, config.getAlgorithm());
        }
        
        Map<String, Integer> nodeIndexMap = new HashMap<>();
        List<String> indexNodeMap = new ArrayList<>();
        Set<String> filteredNodes = new HashSet<>();
        
        for (String caller : callGraph.getAllNodeIds()) {
            int callerDegree = nodeDegree.getOrDefault(caller, 0);
            
            if (callerDegree < minDegree) {
                filteredNodes.add(caller);
                continue;
            }
            
            if (!nodeIndexMap.containsKey(caller)) {
                nodeIndexMap.put(caller, indexNodeMap.size());
                indexNodeMap.add(caller);
            }
            
            for (String callee : callGraph.getCallees(caller)) {
                int calleeDegree = nodeDegree.getOrDefault(callee, 0);
                if (calleeDegree >= minDegree && !nodeIndexMap.containsKey(callee)) {
                    nodeIndexMap.put(callee, indexNodeMap.size());
                    indexNodeMap.add(callee);
                }
            }
        }
        
        int nodesProcessed = nodeIndexMap.size();
        int filteredCount = filteredNodes.size();
        
        if (nodesProcessed == 0) {
            CommunityDetectionResult result = createEmptyResult(totalSymbols);
            result.setLargeGraphMode(isLargeGraph);
            result.setFilteredNodes(filteredCount);
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return result;
        }
        
        Set<Long> edgeSet = new HashSet<>();
        List<int[]> edgeList = new ArrayList<>();
        
        for (String caller : callGraph.getAllNodeIds()) {
            Integer callerIdx = nodeIndexMap.get(caller);
            if (callerIdx == null) continue;
            
            for (String callee : callGraph.getCallees(caller)) {
                Integer calleeIdx = nodeIndexMap.get(callee);
                if (calleeIdx == null || callerIdx.equals(calleeIdx)) continue;
                
                int minIdx = Math.min(callerIdx, calleeIdx);
                int maxIdx = Math.max(callerIdx, calleeIdx);
                long edgeKey = (long) minIdx * nodesProcessed + maxIdx;
                
                if (!edgeSet.contains(edgeKey)) {
                    edgeSet.add(edgeKey);
                    edgeList.add(new int[]{minIdx, maxIdx});
                }
            }
        }
        
        LOG.info("Running {} algorithm on {} nodes, {} edges (filtered {} low-degree nodes)...", 
                config.getAlgorithm(), nodesProcessed, edgeList.size(), filteredCount);
        
        CommunityDetectionResult result;
        
        if (config.getAlgorithm() == AlgorithmType.LEIDEN) {
            result = runLeidenAlgorithm(edgeList, nodesProcessed, indexNodeMap, 
                    callGraph, symbolTable, config, isLargeGraph);
        } else {
            result = runLabelPropagationAlgorithm(nodeIndexMap, indexNodeMap, edgeList,
                    callGraph, symbolTable, config, isLargeGraph);
        }
        
        result.setTotalSymbols(totalSymbols);
        result.setFilteredNodes(filteredCount);
        result.setNodesProcessed(nodesProcessed);
        result.setLargeGraphMode(isLargeGraph);
        result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        
        LOG.info("Found {} communities with {} clustered symbols in {}ms (algorithm={}, largeGraph={})", 
                result.getTotalCommunities(), result.getClusteredSymbols(), 
                result.getProcessingTimeMs(), result.getAlgorithmUsed(), isLargeGraph);
        
        result = splitSuperCommunities(result, callGraph, symbolTable, config);
        
        return result;
    }
    
    private CommunityDetectionResult splitSuperCommunities(
            CommunityDetectionResult result,
            CallGraph callGraph,
            SymbolTable symbolTable,
            CommunityConfig config) {
        
        int totalNodes = result.getTotalSymbols();
        if (totalNodes == 0) return result;
        
        double superThreshold = 0.25;
        int maxSize = (int) Math.ceil(totalNodes * superThreshold);
        
        List<Community> finalCommunities = new ArrayList<>();
        boolean changed = false;
        
        for (Community community : result.getCommunities()) {
            if (community.getSymbolCount() > maxSize && community.getSymbolCount() >= 4) {
                List<Community> subCommunities = recursiveSplit(
                        community, callGraph, symbolTable, config, maxSize, 0, 3);
                if (subCommunities != null && subCommunities.size() > 1) {
                    finalCommunities.addAll(subCommunities);
                    changed = true;
                    continue;
                }
            }
            finalCommunities.add(community);
        }
        
        if (changed) {
            result.setCommunities(finalCommunities);
            result.setTotalCommunities(finalCommunities.size());
            result.setClusteredSymbols(finalCommunities.stream()
                    .mapToInt(Community::getSymbolCount).sum());
            result.setAverageCohesion(finalCommunities.isEmpty() ? 0 :
                    finalCommunities.stream().mapToDouble(Community::getCohesion).average().orElse(0));
        }
        
        return result;
    }
    
    private List<Community> recursiveSplit(
            Community community,
            CallGraph callGraph,
            SymbolTable symbolTable,
            CommunityConfig config,
            int maxSize,
            int depth,
            int maxDepth) {
        
        if (depth >= maxDepth) return null;
        
        Set<String> memberSet = new HashSet<>(community.getSymbolIds());
        
        Graph<String, DefaultEdge> subGraph = new SimpleGraph<>(DefaultEdge.class);
        for (String node : memberSet) {
            subGraph.addVertex(node);
        }
        for (String node : memberSet) {
            for (String callee : callGraph.getCallees(node)) {
                if (memberSet.contains(callee)) {
                    try {
                        subGraph.addEdge(node, callee);
                    } catch (IllegalArgumentException e) {
                        // JGraphT addEdge throws on duplicate edges, safe to ignore
                    }
                }
            }
        }
        
        if (subGraph.edgeSet().isEmpty()) return null;
        
        int nNodes = memberSet.size();
        double resolution = Math.max(0.05, 1.0 / Math.log10(Math.max(nNodes, 10)));
        
        LabelPropagationClustering<String, DefaultEdge> lpc =
                new LabelPropagationClustering<>(subGraph, config.getMaxIterations());
        org.jgrapht.alg.interfaces.ClusteringAlgorithm.Clustering<String> clustering =
                lpc.getClustering();
        
        List<Community> subCommunities = new ArrayList<>();
        List<String> unclusteredNodes = new ArrayList<>();
        int idx = 0;
        for (Set<String> cluster : clustering.getClusters()) {
            if (cluster.size() < 2) {
                unclusteredNodes.addAll(cluster);
                continue;
            }
            
            Community sub = new Community();
            sub.setId(community.getId() + "_" + idx++);
            sub.setSymbolIds(new ArrayList<>(cluster));
            
            double cohesion = calculateCohesion(cluster, callGraph);
            sub.setCohesion(cohesion);
            
            String dominantPackage = findDominantPackage(cluster, symbolTable);
            sub.setDominantPackage(dominantPackage);
            sub.setLabel(generateLabel(cluster, dominantPackage, symbolTable));
            
            subCommunities.add(sub);
        }
        
        if (!unclusteredNodes.isEmpty() && !subCommunities.isEmpty()) {
            for (String node : unclusteredNodes) {
                Community bestMatch = null;
                int bestConnections = 0;
                for (Community sub : subCommunities) {
                    int connections = 0;
                    for (String memberId : sub.getSymbolIds()) {
                        List<String> callees = callGraph.getCallees(node);
                        if (callees != null) {
                            for (String callee : callees) {
                                if (memberId.equals(callee)) connections++;
                            }
                        }
                    }
                    if (connections > bestConnections) {
                        bestConnections = connections;
                        bestMatch = sub;
                    }
                }
                if (bestMatch != null) {
                    bestMatch.getSymbolIds().add(node);
                } else {
                    Community singleton = new Community();
                    singleton.setId(community.getId() + "_singleton_" + idx++);
                    singleton.setSymbolIds(new ArrayList<>(List.of(node)));
                    singleton.setCohesion(0);
                    subCommunities.add(singleton);
                }
            }
        }

        if (subCommunities.isEmpty()) return null;
        
        List<Community> result = new ArrayList<>();
        for (Community sub : subCommunities) {
            if (sub.getSymbolCount() > maxSize && depth + 1 < maxDepth) {
                List<Community> further = recursiveSplit(
                        sub, callGraph, symbolTable, config, maxSize, depth + 1, maxDepth);
                if (further != null && further.size() > 1) {
                    result.addAll(further);
                } else {
                    result.add(sub);
                }
            } else {
                result.add(sub);
            }
        }
        
        return result;
    }
    
    private static CommunityDetectionResult runLeidenAlgorithm(
            List<int[]> edgeList,
            int nNodes,
            List<String> indexNodeMap,
            CallGraph callGraph,
            SymbolTable symbolTable,
            CommunityConfig config,
            boolean isLargeGraph) {
        
        CommunityDetectionResult result = new CommunityDetectionResult();
        result.setAlgorithmUsed(AlgorithmType.LEIDEN);
        result.setResolution(config.getResolution());
        
        try {
            LargeIntArray[] edges = new LargeIntArray[2];
            edges[0] = new LargeIntArray(edgeList.size());
            edges[1] = new LargeIntArray(edgeList.size());
            
            for (int i = 0; i < edgeList.size(); i++) {
                int[] edge = edgeList.get(i);
                edges[0].set(i, edge[0]);
                edges[1].set(i, edge[1]);
            }
            
            Network network = new Network(nNodes, false, edges, false, false);
            
            int nIterations = isLargeGraph ? config.getLargeGraphMaxIterations() : config.getMaxIterations();
            LeidenAlgorithm leidenAlgorithm = new LeidenAlgorithm(
                    config.getResolution(),
                    nIterations,
                    config.getRandomness(),
                    new Random()
            );
            
            Clustering clustering;
            if (config.isEnableTimeout()) {
                clustering = runWithTimeout(() -> leidenAlgorithm.findClustering(network), 
                        config.getTimeoutMs());
            } else {
                clustering = leidenAlgorithm.findClustering(network);
            }
            
            double modularity = leidenAlgorithm.calcQuality(network, clustering);
            result.setModularity(modularity);
            
            LOG.debug("Leiden modularity: {}", modularity);
            
            List<Community> communities = convertClusteringToCommunities(
                    clustering, indexNodeMap, callGraph, symbolTable, config);
            
            if (communities.isEmpty() && indexNodeMap.size() >= 2) {
                LOG.debug("Leiden returned no communities, falling back to LabelPropagation");
                return runLabelPropagationAlgorithm(null, indexNodeMap, edgeList, 
                        callGraph, symbolTable, config, isLargeGraph);
            }
            
            result.setCommunities(communities);
            result.setTotalCommunities(communities.size());
            result.setClusteredSymbols(communities.stream()
                    .mapToInt(Community::getSymbolCount).sum());
            result.setAverageCohesion(communities.isEmpty() ? 0 : 
                    communities.stream().mapToDouble(Community::getCohesion).average().orElse(0));
            
        } catch (Exception e) {
            LOG.warn("Leiden algorithm failed, falling back to LabelPropagation", e);
            return runLabelPropagationAlgorithm(null, indexNodeMap, edgeList, 
                    callGraph, symbolTable, config, isLargeGraph);
        }
        
        return result;
    }
    
    private static CommunityDetectionResult runLabelPropagationAlgorithm(
            Map<String, Integer> nodeIndexMap,
            List<String> indexNodeMap,
            List<int[]> edgeList,
            CallGraph callGraph,
            SymbolTable symbolTable,
            CommunityConfig config,
            boolean isLargeGraph) {
        
        CommunityDetectionResult result = new CommunityDetectionResult();
        result.setAlgorithmUsed(AlgorithmType.LABEL_PROPAGATION);
        
        Graph<String, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        
        for (String nodeId : indexNodeMap) {
            graph.addVertex(nodeId);
        }
        
        for (int[] edge : edgeList) {
            String source = indexNodeMap.get(edge[0]);
            String target = indexNodeMap.get(edge[1]);
            if (source != null && target != null) {
                try {
                    graph.addEdge(source, target);
                } catch (IllegalArgumentException e) {
                    // JGraphT addEdge throws on duplicate edges, safe to ignore
                }
            }
        }
        
        int iterations = isLargeGraph ? config.getLargeGraphMaxIterations() : config.getMaxIterations();
        
        LabelPropagationClustering<String, DefaultEdge> algorithm = 
                new LabelPropagationClustering<>(graph, iterations);
        org.jgrapht.alg.interfaces.ClusteringAlgorithm.Clustering<String> clustering = 
                algorithm.getClustering();
        
        List<Community> communities = new ArrayList<>();
        int communityIndex = 0;
        int clusteredSymbols = 0;
        double totalCohesion = 0;
        
        for (Set<String> cluster : clustering.getClusters()) {
            if (cluster.size() < config.getMinCommunitySize()) {
                continue;
            }
            
            Community community = new Community();
            community.setId("comm_" + communityIndex++);
            community.setSymbolIds(new ArrayList<>(cluster));
            
            double cohesion = calculateCohesion(cluster, callGraph);
            community.setCohesion(cohesion);
            
            String dominantPackage = findDominantPackage(cluster, symbolTable);
            community.setDominantPackage(dominantPackage);
            community.setLabel(generateLabel(cluster, dominantPackage, symbolTable));
            
            communities.add(community);
            clusteredSymbols += cluster.size();
            totalCohesion += cohesion;
        }
        
        communities.sort((a, b) -> Integer.compare(b.getSymbolCount(), a.getSymbolCount()));
        
        result.setCommunities(communities);
        result.setTotalCommunities(communities.size());
        result.setClusteredSymbols(clusteredSymbols);
        result.setAverageCohesion(communities.isEmpty() ? 0 : totalCohesion / communities.size());
        
        return result;
    }
    
    private static List<Community> convertClusteringToCommunities(
            Clustering clustering,
            List<String> indexNodeMap,
            CallGraph callGraph,
            SymbolTable symbolTable,
            CommunityConfig config) {
        
        int[][] nodesPerCluster = clustering.getNodesPerCluster();
        int nClusters = nodesPerCluster.length;
        List<Community> communities = new ArrayList<>();
        
        for (int clusterIdx = 0; clusterIdx < nClusters; clusterIdx++) {
            int[] nodeIndices = nodesPerCluster[clusterIdx];
            
            List<String> clusterNodes = new ArrayList<>();
            for (int nodeIdx : nodeIndices) {
                if (nodeIdx >= 0 && nodeIdx < indexNodeMap.size()) {
                    clusterNodes.add(indexNodeMap.get(nodeIdx));
                }
            }
            
            if (clusterNodes.size() < config.getMinCommunitySize()) {
                continue;
            }
            
            Community community = new Community();
            community.setId("comm_" + communities.size());
            community.setSymbolIds(clusterNodes);
            
            double cohesion = calculateCohesion(new HashSet<>(clusterNodes), callGraph);
            community.setCohesion(cohesion);
            
            String dominantPackage = findDominantPackage(new HashSet<>(clusterNodes), symbolTable);
            community.setDominantPackage(dominantPackage);
            community.setLabel(generateLabel(new HashSet<>(clusterNodes), dominantPackage, symbolTable));
            
            communities.add(community);
        }
        
        communities.sort((a, b) -> Integer.compare(b.getSymbolCount(), a.getSymbolCount()));
        
        return communities;
    }
    
    private static <T> T runWithTimeout(Callable<T> task, long timeoutMs) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<T> future = executor.submit(task);
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
    
    private static Map<String, Integer> calculateNodeDegrees(CallGraph callGraph) {
        Map<String, Integer> degree = new HashMap<>();
        
        for (String caller : callGraph.getAllNodeIds()) {
            List<String> callees = callGraph.getCallees(caller);
            int outDegree = callees.size();
            
            degree.merge(caller, outDegree, Integer::sum);
            
            for (String callee : callees) {
                degree.merge(callee, 1, Integer::sum);
            }
        }
        
        return degree;
    }
    
    private static double calculateCohesion(Set<String> cluster, CallGraph callGraph) {
        if (cluster.size() < 2) return 1.0;

        int internalEdges = 0;
        int externalEdges = 0;

        for (String node : cluster) {
            List<String> callees = callGraph.getCallees(node);
            if (callees != null) {
                for (String callee : callees) {
                    if (cluster.contains(callee)) {
                        internalEdges++;
                    } else {
                        externalEdges++;
                    }
                }
            }
        }

        int total = internalEdges + externalEdges;
        return total == 0 ? 1.0 : (double) internalEdges / total;
    }
    
    private static String findDominantPackage(Set<String> cluster, SymbolTable symbolTable) {
        Map<String, Integer> packageCount = new HashMap<>();
        
        for (String symbolId : cluster) {
            CodeSymbol symbol = symbolTable.getById(symbolId);
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
    
    private static String generateLabel(Set<String> cluster, String dominantPackage, 
                                         SymbolTable symbolTable) {
        if (!"unknown".equals(dominantPackage)) {
            String[] parts = dominantPackage.split("\\.");
            if (parts.length >= 2) {
                return parts[parts.length - 2] + "." + parts[parts.length - 1];
            } else if (parts.length == 1) {
                return parts[0];
            }
        }
        
        return "cluster_" + cluster.size();
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
    
    private static CommunityDetectionResult createEmptyResult(int totalSymbols) {
        CommunityDetectionResult result = new CommunityDetectionResult();
        result.setCommunities(Collections.emptyList());
        result.setTotalSymbols(totalSymbols);
        result.setTotalCommunities(0);
        result.setClusteredSymbols(0);
        result.setAverageCohesion(0);
        result.setAlgorithmUsed(AlgorithmType.LEIDEN);
        return result;
    }
    
    public static Community getCommunityForSymbol(String symbolId, List<Community> communities) {
        for (Community community : communities) {
            if (community.getSymbolIds().contains(symbolId)) {
                return community;
            }
        }
        return null;
    }
    
    public static String printSummary(CommunityDetectionResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Community Detection Summary ===\n");
        sb.append(String.format("Algorithm: %s\n", result.getAlgorithmUsed()));
        sb.append(String.format("Total symbols: %d\n", result.getTotalSymbols()));
        sb.append(String.format("Nodes processed: %d\n", result.getNodesProcessed()));
        sb.append(String.format("Filtered nodes: %d\n", result.getFilteredNodes()));
        sb.append(String.format("Communities found: %d\n", result.getTotalCommunities()));
        sb.append(String.format("Clustered symbols: %d (%.1f%%)\n", 
                result.getClusteredSymbols(),
                result.getTotalSymbols() > 0 ? 
                    (double) result.getClusteredSymbols() / result.getTotalSymbols() * 100 : 0));
        sb.append(String.format("Average cohesion: %.2f\n", result.getAverageCohesion()));
        
        if (result.getModularity() > 0) {
            sb.append(String.format("Modularity: %.4f\n", result.getModularity()));
        }
        if (result.getResolution() > 0) {
            sb.append(String.format("Resolution: %.2f\n", result.getResolution()));
        }
        
        sb.append(String.format("Processing time: %dms\n", result.getProcessingTimeMs()));
        
        if (result.isLargeGraphMode()) {
            sb.append("[Large graph mode enabled]\n");
        }
        
        sb.append("\nTop 10 communities:\n");
        result.getCommunities().stream()
                .limit(10)
                .forEach(c -> {
                    sb.append(String.format("  %s: %d symbols, cohesion=%.2f\n",
                            c.getLabel(), c.getSymbolCount(), c.getCohesion()));
                });
        
        return sb.toString();
    }
}
