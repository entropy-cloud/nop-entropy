package io.nop.graph.algorithm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;

/**
 * PageRank 中心性算法。
 */
public final class PageRank {

    private static final double DAMPING_FACTOR = 0.85;
    private static final double CONVERGENCE_THRESHOLD = 1e-6;

    private PageRank() {
    }

    /**
     * 计算图中节点的 PageRank 值。
     *
     * @param graph      图
     * @param nodes      要计算的节点集
     * @param iterations 迭代次数
     * @return nodeId → PageRank 值
     */
    public static Map<String, Double> compute(IGraph graph, Set<String> nodes, int iterations) {
        if (graph == null || nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("graph and nodes must not be null or empty");
        }
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be positive");
        }

        int n = nodes.size();
        Map<String, Double> ranks = new HashMap<>();
        for (String node : nodes) {
            ranks.put(node, 1.0 / n);
        }

        for (int i = 0; i < iterations; i++) {
            Map<String, Double> newRanks = new HashMap<>();
            double danglingSum = 0.0;

            for (String node : nodes) {
                int outDegree = graph.getOutEdges(node).size();
                if (outDegree == 0) {
                    danglingSum += ranks.get(node);
                }
            }

            for (String node : nodes) {
                double rank = (1.0 - DAMPING_FACTOR) / n;
                rank += DAMPING_FACTOR * danglingSum / n;

                for (Edge edge : graph.getInEdges(node)) {
                    String source = edge.getSourceId();
                    if (ranks.containsKey(source)) {
                        int sourceOutDegree = graph.getOutEdges(source).size();
                        if (sourceOutDegree > 0) {
                            double weight = edge.getWeight();
                            rank += DAMPING_FACTOR * ranks.get(source) * weight / sourceOutDegree;
                        }
                    }
                }

                newRanks.put(node, rank);
            }

            ranks = newRanks;
        }

        return ranks;
    }
}
