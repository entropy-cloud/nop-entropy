package io.nop.javaparser.analyzer;

import nl.cwts.networkanalysis.LeidenAlgorithm;
import nl.cwts.networkanalysis.Network;
import nl.cwts.networkanalysis.Network;
import nl.cwts.networkanalysis.Clustering;
import nl.cwts.util.LargeIntArray;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.jgrapht.alg.clustering.LabelPropagationClustering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * 社区检测器
 * 
 * 支持两种算法:
 * 1. Leiden 算法 - 更高质量的社区检测，保证社区连通性
 *    参考: Traag, V. A., Waltman, L., & van Eck, N. J. (2019). 
 *    "From Louvain to Leiden: guaranteeing well-connected communities"
 * 
 * 2. LabelPropagation 算法 - 快速，近似线性时间
 *    参考: Raghavan, U. N., Albert, R., and Kumara, S. (2007). 
 *    "Near linear time algorithm to detect community structures in large-scale networks"
 * 
 * 大图优化策略:
 * 1. 自动检测大图模式 (符号数 > largeGraphThreshold)
 * 2. 过滤低度数节点 (噪音节点)
 * 3. 限制迭代次数
 * 4. 支持超时控制
 */
public class CommunityDetector {
    
    private static final Logger LOG = LoggerFactory.getLogger(CommunityDetector.class);
    
    /**
     * 算法类型
     */
    public enum AlgorithmType {
        LEIDEN,             // Leiden 算法 (高质量，保证连通性) - 推荐
        LABEL_PROPAGATION   // JGraphT LabelPropagation (快速)
    }
    
    /**
     * 社区检测结果
     */
    public static class CommunityDetectionResult {
        private List<Community> communities;
        private int totalSymbols;
        private int totalCommunities;
        private int clusteredSymbols;
        private double averageCohesion;
        
        // 大图模式统计
        private boolean largeGraphMode;
        private int filteredNodes;
        private int nodesProcessed;
        private long processingTimeMs;
        private AlgorithmType algorithmUsed;
        private double resolution;      // Leiden 算法分辨率
        private double modularity;      // 模块度
        
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
    
    /**
     * 社区
     */
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
            return symbolIds != null ? symbolIds : Collections.emptyList();
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
    
    /**
     * 社区检测配置
     */
    public static class CommunityConfig {
        private int minCommunitySize = 2;
        private int maxIterations = 10;
        
        // 大图模式配置
        private int largeGraphThreshold = 10_000;
        private int largeGraphMaxIterations = 3;
        private int minNodeDegree = 2;
        
        // 算法选择
        private AlgorithmType algorithm = AlgorithmType.LEIDEN;  // 默认使用 Leiden
        
        // Leiden 算法参数
        private double resolution = 0.1;           // 分辨率参数，越小社区越大（稀疏图用小值）
        private double randomness = 0.01;         // 随机性参数
        
        // 超时控制
        private boolean enableTimeout = false;
        private long timeoutMs = 60000;           // 默认60秒超时
        
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
            config.setTimeoutMs(120000);  // 2分钟超时
            return config;
        }
    }
    
    /**
     * 检测调用图中的社区
     */
    public static CommunityDetectionResult detectCommunities(
            Map<String, List<String>> callGraph,
            Map<String, SymbolInfo> symbolTable) {
        return detectCommunities(callGraph, symbolTable, CommunityConfig.defaultConfig());
    }
    
    /**
     * 检测调用图中的社区（带配置）
     */
    public static CommunityDetectionResult detectCommunities(
            Map<String, List<String>> callGraph,
            Map<String, SymbolInfo> symbolTable,
            CommunityConfig config) {
        
        long startTime = System.currentTimeMillis();
        
        // 1. 计算所有节点的度数
        Map<String, Integer> nodeDegree = calculateNodeDegrees(callGraph);
        
        int totalSymbols = nodeDegree.size();
        if (totalSymbols == 0) {
            return createEmptyResult(0);
        }
        
        // 2. 检测是否为大图模式
        boolean isLargeGraph = totalSymbols > config.getLargeGraphThreshold();
        int minDegree = isLargeGraph ? config.getMinNodeDegree() : 1;
        
        if (isLargeGraph) {
            LOG.info("Large graph detected ({} symbols), enabling optimizations: minDegree={}, algorithm={}", 
                    totalSymbols, minDegree, config.getAlgorithm());
        }
        
        // 3. 过滤低度数节点并建立节点索引映射
        Map<String, Integer> nodeIndexMap = new HashMap<>();
        List<String> indexNodeMap = new ArrayList<>();
        Set<String> filteredNodes = new HashSet<>();
        
        for (Map.Entry<String, List<String>> entry : callGraph.entrySet()) {
            String caller = entry.getKey();
            int callerDegree = nodeDegree.getOrDefault(caller, 0);
            
            if (callerDegree < minDegree) {
                filteredNodes.add(caller);
                continue;
            }
            
            if (!nodeIndexMap.containsKey(caller)) {
                nodeIndexMap.put(caller, indexNodeMap.size());
                indexNodeMap.add(caller);
            }
            
            for (String callee : entry.getValue()) {
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
        
        // 4. 收集边（去重）
        Set<Long> edgeSet = new HashSet<>();
        List<int[]> edgeList = new ArrayList<>();
        
        for (Map.Entry<String, List<String>> entry : callGraph.entrySet()) {
            String caller = entry.getKey();
            Integer callerIdx = nodeIndexMap.get(caller);
            if (callerIdx == null) continue;
            
            for (String callee : entry.getValue()) {
                Integer calleeIdx = nodeIndexMap.get(callee);
                if (calleeIdx == null || callerIdx.equals(calleeIdx)) continue;
                
                // 使用无向边（Leiden 要求）
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
        
        // 5. 根据算法类型选择执行方式
        CommunityDetectionResult result;
        
        if (config.getAlgorithm() == AlgorithmType.LEIDEN) {
            result = runLeidenAlgorithm(edgeList, nodesProcessed, indexNodeMap, 
                    callGraph, symbolTable, config, isLargeGraph);
        } else {
            result = runLabelPropagationAlgorithm(nodeIndexMap, indexNodeMap, edgeList,
                    callGraph, symbolTable, config, isLargeGraph);
        }
        
        // 6. 设置通用统计信息
        result.setTotalSymbols(totalSymbols);
        result.setFilteredNodes(filteredCount);
        result.setNodesProcessed(nodesProcessed);
        result.setLargeGraphMode(isLargeGraph);
        result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        
        LOG.info("Found {} communities with {} clustered symbols in {}ms (algorithm={}, largeGraph={})", 
                result.getTotalCommunities(), result.getClusteredSymbols(), 
                result.getProcessingTimeMs(), result.getAlgorithmUsed(), isLargeGraph);
        
        return result;
    }
    
    /**
     * 运行 Leiden 算法
     */
    private static CommunityDetectionResult runLeidenAlgorithm(
            List<int[]> edgeList,
            int nNodes,
            List<String> indexNodeMap,
            Map<String, List<String>> callGraph,
            Map<String, SymbolInfo> symbolTable,
            CommunityConfig config,
            boolean isLargeGraph) {
        
        CommunityDetectionResult result = new CommunityDetectionResult();
        result.setAlgorithmUsed(AlgorithmType.LEIDEN);
        result.setResolution(config.getResolution());
        
        try {
            // 构建 Leiden Network
            LargeIntArray[] edges = new LargeIntArray[2];
            edges[0] = new LargeIntArray(edgeList.size());
            edges[1] = new LargeIntArray(edgeList.size());
            
            for (int i = 0; i < edgeList.size(); i++) {
                int[] edge = edgeList.get(i);
                edges[0].set(i, edge[0]);
                edges[1].set(i, edge[1]);
            }
            
            // 创建网络（无向图，节点权重基于边权重）
            Network network = new Network(nNodes, true, edges, false, false);
            
            // 创建 Leiden 算法
            int nIterations = isLargeGraph ? config.getLargeGraphMaxIterations() : config.getMaxIterations();
            LeidenAlgorithm leidenAlgorithm = new LeidenAlgorithm(
                    config.getResolution(),
                    nIterations,
                    config.getRandomness(),
                    new Random()
            );
            
            // 执行聚类（支持超时）
            Clustering clustering;
            if (config.isEnableTimeout()) {
                clustering = runWithTimeout(() -> leidenAlgorithm.findClustering(network), 
                        config.getTimeoutMs());
            } else {
                clustering = leidenAlgorithm.findClustering(network);
            }
            
            // 计算模块度（使用算法对象计算）
            double modularity = leidenAlgorithm.calcQuality(network, clustering);
            result.setModularity(modularity);
            
            LOG.debug("Leiden modularity: {}", modularity);
            
            // 转换为社区结果
            List<Community> communities = convertClusteringToCommunities(
                    clustering, indexNodeMap, callGraph, symbolTable, config);
            
            // 如果 Leiden 返回空结果，回退到 LabelPropagation
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
            LOG.warn("Leiden algorithm failed, falling back to LabelPropagation: {}", e.getMessage());
            // 回退到 LabelPropagation
            return runLabelPropagationAlgorithm(null, indexNodeMap, edgeList, 
                    callGraph, symbolTable, config, isLargeGraph);
        }
        
        return result;
    }
    
    /**
     * 运行 LabelPropagation 算法（JGraphT）
     */
    private static CommunityDetectionResult runLabelPropagationAlgorithm(
            Map<String, Integer> nodeIndexMap,
            List<String> indexNodeMap,
            List<int[]> edgeList,
            Map<String, List<String>> callGraph,
            Map<String, SymbolInfo> symbolTable,
            CommunityConfig config,
            boolean isLargeGraph) {
        
        CommunityDetectionResult result = new CommunityDetectionResult();
        result.setAlgorithmUsed(AlgorithmType.LABEL_PROPAGATION);
        
        // 构建 JGraphT 图
        Graph<String, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        
        // 添加节点
        for (String nodeId : indexNodeMap) {
            graph.addVertex(nodeId);
        }
        
        // 添加边
        for (int[] edge : edgeList) {
            String source = indexNodeMap.get(edge[0]);
            String target = indexNodeMap.get(edge[1]);
            if (source != null && target != null) {
                try {
                    graph.addEdge(source, target);
                } catch (IllegalArgumentException e) {
                    // 边已存在，忽略
                }
            }
        }
        
        // 确定迭代次数
        int iterations = isLargeGraph ? config.getLargeGraphMaxIterations() : config.getMaxIterations();
        
        // 运行 LabelPropagation
        LabelPropagationClustering<String, DefaultEdge> algorithm = 
                new LabelPropagationClustering<>(graph, iterations);
        org.jgrapht.alg.interfaces.ClusteringAlgorithm.Clustering<String> clustering = 
                algorithm.getClustering();
        
        // 转换为社区结果
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
        
        // 按大小排序
        communities.sort((a, b) -> Integer.compare(b.getSymbolCount(), a.getSymbolCount()));
        
        result.setCommunities(communities);
        result.setTotalCommunities(communities.size());
        result.setClusteredSymbols(clusteredSymbols);
        result.setAverageCohesion(communities.isEmpty() ? 0 : totalCohesion / communities.size());
        
        return result;
    }
    
    /**
     * 将 Leiden Clustering 转换为社区列表
     */
    private static List<Community> convertClusteringToCommunities(
            Clustering clustering,
            List<String> indexNodeMap,
            Map<String, List<String>> callGraph,
            Map<String, SymbolInfo> symbolTable,
            CommunityConfig config) {
        
        // 获取每个簇的节点列表
        int[][] nodesPerCluster = clustering.getNodesPerCluster();
        int nClusters = nodesPerCluster.length;
        List<Community> communities = new ArrayList<>();
        
        for (int clusterIdx = 0; clusterIdx < nClusters; clusterIdx++) {
            int[] nodeIndices = nodesPerCluster[clusterIdx];
            
            // 获取该簇的所有节点
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
        
        // 按大小排序
        communities.sort((a, b) -> Integer.compare(b.getSymbolCount(), a.getSymbolCount()));
        
        return communities;
    }
    
    /**
     * 带超时执行
     */
    private static <T> T runWithTimeout(Callable<T> task, long timeoutMs) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<T> future = executor.submit(task);
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
    
    /**
     * 计算所有节点的度数
     */
    private static Map<String, Integer> calculateNodeDegrees(Map<String, List<String>> callGraph) {
        Map<String, Integer> degree = new HashMap<>();
        
        for (Map.Entry<String, List<String>> entry : callGraph.entrySet()) {
            String caller = entry.getKey();
            int outDegree = entry.getValue().size();
            
            degree.merge(caller, outDegree, Integer::sum);
            
            for (String callee : entry.getValue()) {
                degree.merge(callee, 1, Integer::sum);
            }
        }
        
        return degree;
    }
    
    /**
     * 计算社区内聚度
     */
    private static double calculateCohesion(Set<String> cluster, Map<String, List<String>> callGraph) {
        int internalEdges = 0;
        int totalPossibleEdges = cluster.size() * (cluster.size() - 1);
        
        if (totalPossibleEdges == 0) return 1;
        
        for (String node : cluster) {
            List<String> callees = callGraph.get(node);
            if (callees != null) {
                for (String callee : callees) {
                    if (cluster.contains(callee)) {
                        internalEdges++;
                    }
                }
            }
        }
        
        return (double) internalEdges / totalPossibleEdges;
    }
    
    /**
     * 找到社区中的主导包名
     */
    private static String findDominantPackage(Set<String> cluster, Map<String, SymbolInfo> symbolTable) {
        Map<String, Integer> packageCount = new HashMap<>();
        
        for (String symbolId : cluster) {
            SymbolInfo symbol = symbolTable.get(symbolId);
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
    
    /**
     * 生成社区标签
     */
    private static String generateLabel(Set<String> cluster, String dominantPackage, 
                                         Map<String, SymbolInfo> symbolTable) {
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
    
    /**
     * 从全限定名提取包名
     */
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
    
    /**
     * 创建空结果
     */
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
    
    /**
     * 获取符号所属的社区
     */
    public static Community getCommunityForSymbol(String symbolId, List<Community> communities) {
        for (Community community : communities) {
            if (community.getSymbolIds().contains(symbolId)) {
                return community;
            }
        }
        return null;
    }
    
    /**
     * 打印社区检测结果摘要
     */
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
