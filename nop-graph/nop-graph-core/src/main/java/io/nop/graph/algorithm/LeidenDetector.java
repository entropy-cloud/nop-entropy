package io.nop.graph.algorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.graph.api.CommunityInfo;
import io.nop.graph.api.CommunityResult;
import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;
import io.nop.graph.api.LeidenConfig;
import nl.cwts.networkanalysis.Clustering;
import nl.cwts.networkanalysis.LeidenAlgorithm;
import nl.cwts.networkanalysis.Network;
import nl.cwts.util.LargeIntArray;

/**
 * Leiden 社区检测算法。
 *
 * 将 IGraph 转为 CWTS Network，运行 LeidenAlgorithm，
 * 返回 CommunityResult。不含 dominantPackage/label 等业务逻辑。
 */
public final class LeidenDetector {

    private static final Logger LOG = LoggerFactory.getLogger(LeidenDetector.class);

    private LeidenDetector() {
    }

    public static CommunityResult detect(IGraph graph, Set<String> nodes, LeidenConfig config) {
        if (graph == null || nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("graph and nodes must not be null or empty");
        }
        if (nodes.size() < 2) {
            throw new IllegalArgumentException("Leiden requires at least 2 nodes");
        }

        long startTime = System.currentTimeMillis();

        List<String> indexNodeMap = new ArrayList<>(nodes);
        Map<String, Integer> nodeIndexMap = new HashMap<>();
        for (int i = 0; i < indexNodeMap.size(); i++) {
            nodeIndexMap.put(indexNodeMap.get(i), i);
        }

        List<int[]> edgeList = new ArrayList<>();
        Set<String> seenEdges = new HashSet<>();
        for (String node : indexNodeMap) {
            for (Edge edge : graph.getOutEdges(node)) {
                String target = edge.getTargetId();
                if (nodeIndexMap.containsKey(target)) {
                    int src = nodeIndexMap.get(node);
                    int tgt = nodeIndexMap.get(target);
                    String key = src + "_" + tgt;
                    if (src != tgt && seenEdges.add(key)) {
                        edgeList.add(new int[]{src, tgt});
                    }
                }
            }
        }

        CommunityResult result = runLeiden(edgeList, indexNodeMap.size(), indexNodeMap,
                graph, config);

        return new CommunityResult(result.getCommunities(),
                nodes.size(),
                result.getTotalCommunities(),
                result.getAverageCohesion(),
                result.getModularity(),
                "LEIDEN",
                System.currentTimeMillis() - startTime);
    }

    private static CommunityResult runLeiden(List<int[]> edgeList, int nNodes,
                                              List<String> indexNodeMap,
                                              IGraph graph, LeidenConfig config) {
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

            LeidenAlgorithm leiden = new LeidenAlgorithm(
                    config.getResolution(),
                    config.getMaxIterations(),
                    0.01,
                    new Random()
            );

            Clustering clustering;
            if (config.getTimeoutMs() > 0) {
                clustering = runWithTimeout(() -> leiden.findClustering(network),
                        config.getTimeoutMs());
            } else {
                clustering = leiden.findClustering(network);
            }

            double modularity = leiden.calcQuality(network, clustering);

            List<CommunityInfo> communities = convertClustering(clustering, indexNodeMap,
                    graph, config);

            if (communities.isEmpty()) {
                LOG.debug("Leiden returned no communities, falling back to LabelPropagation");
                return LabelPropagation.detect(toIGraph(graph, indexNodeMap),
                        new LinkedHashSet<>(indexNodeMap), config.getMaxIterations());
            }

            double avgCohesion = communities.stream()
                    .mapToDouble(CommunityInfo::getCohesion).average().orElse(0);

            return new CommunityResult(communities, nNodes, communities.size(),
                    avgCohesion, modularity, "LEIDEN", 0);

        } catch (Exception e) {
            LOG.warn("Leiden algorithm failed, falling back to LabelPropagation", e);
            return LabelPropagation.detect(toIGraph(graph, indexNodeMap),
                    new LinkedHashSet<>(indexNodeMap), config.getMaxIterations());
        }
    }

    private static List<CommunityInfo> convertClustering(Clustering clustering,
                                                           List<String> indexNodeMap,
                                                           IGraph graph,
                                                           LeidenConfig config) {
        int[][] nodesPerCluster = clustering.getNodesPerCluster();
        List<CommunityInfo> communities = new ArrayList<>();

        for (int clusterIdx = 0; clusterIdx < nodesPerCluster.length; clusterIdx++) {
            int[] nodeIndices = nodesPerCluster[clusterIdx];
            Set<String> clusterNodes = new LinkedHashSet<>();

            for (int nodeIdx : nodeIndices) {
                if (nodeIdx >= 0 && nodeIdx < indexNodeMap.size()) {
                    clusterNodes.add(indexNodeMap.get(nodeIdx));
                }
            }

            if (clusterNodes.size() < config.getMinCommunitySize()) {
                continue;
            }

            double cohesion = calculateCohesion(clusterNodes, graph);
            communities.add(new CommunityInfo(clusterIdx, clusterNodes, cohesion));
        }

        communities.sort((a, b) -> Integer.compare(b.getNodeCount(), a.getNodeCount()));
        return communities;
    }

    static double calculateCohesion(Set<String> cluster, IGraph graph) {
        if (cluster.size() < 2) return 1.0;

        int internalEdges = 0;
        int externalEdges = 0;

        for (String node : cluster) {
            for (Edge edge : graph.getOutEdges(node)) {
                if (cluster.contains(edge.getTargetId())) {
                    internalEdges++;
                } else {
                    externalEdges++;
                }
            }
        }

        int total = internalEdges + externalEdges;
        return total == 0 ? 1.0 : (double) internalEdges / total;
    }

    private static IGraph toIGraph(IGraph graph, List<String> nodes) {
        return graph;
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
}
