package io.nop.graph.algorithm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.alg.clustering.LabelPropagationClustering;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import io.nop.graph.api.CommunityInfo;
import io.nop.graph.api.CommunityResult;
import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;

/**
 * Label Propagation 社区检测算法。
 */
public final class LabelPropagation {

    private LabelPropagation() {
    }

    public static CommunityResult detect(IGraph graph, Set<String> nodes, int iterations) {
        if (graph == null || nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("graph and nodes must not be null or empty");
        }
        if (nodes.size() < 2) {
            throw new IllegalArgumentException("LabelPropagation requires at least 2 nodes");
        }

        long startTime = System.currentTimeMillis();

        Graph<String, DefaultEdge> jgraph = new SimpleGraph<>(DefaultEdge.class);

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

        LabelPropagationClustering<String, DefaultEdge> algorithm =
                new LabelPropagationClustering<>(jgraph, iterations);
        org.jgrapht.alg.interfaces.ClusteringAlgorithm.Clustering<String> clustering =
                algorithm.getClustering();

        List<CommunityInfo> communities = new ArrayList<>();
        int communityIndex = 0;

        for (Set<String> cluster : clustering.getClusters()) {
            if (cluster.size() < 1) {
                continue;
            }
            double cohesion = LeidenDetector.calculateCohesion(
                    new LinkedHashSet<>(cluster), graph);
            communities.add(new CommunityInfo(communityIndex++,
                    new LinkedHashSet<>(cluster), cohesion));
        }

        communities.sort((a, b) -> Integer.compare(b.getNodeCount(), a.getNodeCount()));

        double avgCohesion = communities.stream()
                .mapToDouble(CommunityInfo::getCohesion).average().orElse(0);

        return new CommunityResult(communities, nodes.size(), communities.size(),
                avgCohesion, 0.0, "LABEL_PROPAGATION",
                System.currentTimeMillis() - startTime);
    }
}
