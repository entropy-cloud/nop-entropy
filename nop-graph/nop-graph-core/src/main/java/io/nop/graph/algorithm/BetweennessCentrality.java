package io.nop.graph.algorithm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;

/**
 * 介数中心性算法，委托 JGraphT BetweennessCentrality。
 */
public final class BetweennessCentrality {

    private BetweennessCentrality() {
    }

    /**
     * 计算图中各节点的介数中心性。
     *
     * @param graph 图
     * @param nodes 要计算的节点集
     * @return nodeId → betweenness score
     */
    public static Map<String, Double> compute(IGraph graph, Set<String> nodes) {
        if (graph == null || nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("graph and nodes must not be null or empty");
        }

        Graph<String, DefaultEdge> jgraph = new DefaultDirectedGraph<>(DefaultEdge.class);

        for (String node : nodes) {
            jgraph.addVertex(node);
        }

        for (String node : nodes) {
            for (Edge edge : graph.getOutEdges(node)) {
                String target = edge.getTargetId();
                if (nodes.contains(target)) {
                    try {
                        jgraph.addEdge(node, target);
                    } catch (IllegalArgumentException e) {
                        // duplicate edge, skip
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        org.jgrapht.alg.scoring.BetweennessCentrality<String, DefaultEdge> bc =
                new org.jgrapht.alg.scoring.BetweennessCentrality<>(jgraph, false);
        Map<String, Double> scores = bc.getScores();

        return new HashMap<>(scores);
    }
}
