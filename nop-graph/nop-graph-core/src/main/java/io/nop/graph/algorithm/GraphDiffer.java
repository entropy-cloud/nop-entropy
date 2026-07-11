package io.nop.graph.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.nop.graph.api.CommunityChange;
import io.nop.graph.api.Edge;
import io.nop.graph.api.GraphDiff;
import io.nop.graph.api.IGraph;

/**
 * 图差分算法。
 */
public final class GraphDiffer {

    private GraphDiffer() {
    }

    /**
     * 计算两个图之间的差分。
     *
     * @param baselineGraph    基线图
     * @param baselineNodes    基线图节点集
     * @param targetGraph      目标图
     * @param targetNodes      目标图节点集
     * @return 图差分结果（communityChanges 为空）
     */
    public static GraphDiff diff(IGraph baselineGraph, Set<String> baselineNodes,
                                  IGraph targetGraph, Set<String> targetNodes) {
        if (baselineGraph == null || targetGraph == null
                || baselineNodes == null || targetNodes == null) {
            throw new IllegalArgumentException("arguments must not be null");
        }

        Set<String> addedNodes = new LinkedHashSet<>(targetNodes);
        addedNodes.removeAll(baselineNodes);

        Set<String> removedNodes = new LinkedHashSet<>(baselineNodes);
        removedNodes.removeAll(targetNodes);

        Set<Edge> baselineEdges = collectEdges(baselineGraph, baselineNodes);
        Set<Edge> targetEdges = collectEdges(targetGraph, targetNodes);

        Set<Edge> addedEdges = new LinkedHashSet<>(targetEdges);
        addedEdges.removeAll(baselineEdges);

        Set<Edge> removedEdges = new LinkedHashSet<>(baselineEdges);
        removedEdges.removeAll(targetEdges);

        return new GraphDiff(addedNodes, removedNodes, addedEdges, removedEdges,
                Collections.emptyList());
    }

    /**
     * 计算两个图之间的差分，包含社区变化。
     *
     * @param baselineGraph        基线图
     * @param baselineNodes        基线图节点集
     * @param baselineCommunityMap 基线社区映射（nodeId → communityId）
     * @param targetGraph          目标图
     * @param targetNodes          目标图节点集
     * @param targetCommunityMap   目标社区映射（nodeId → communityId）
     * @return 图差分结果（含 communityChanges）
     */
    public static GraphDiff diffWithCommunities(IGraph baselineGraph, Set<String> baselineNodes,
                                                 Map<String, Integer> baselineCommunityMap,
                                                 IGraph targetGraph, Set<String> targetNodes,
                                                 Map<String, Integer> targetCommunityMap) {
        GraphDiff baseDiff = diff(baselineGraph, baselineNodes, targetGraph, targetNodes);

        List<CommunityChange> communityChanges = new ArrayList<>();

        Set<String> allNodes = new HashSet<>(baselineNodes);
        allNodes.retainAll(targetNodes);

        for (String node : allNodes) {
            Integer oldCommunity = baselineCommunityMap.get(node);
            Integer newCommunity = targetCommunityMap.get(node);

            if (oldCommunity != null && newCommunity != null && !oldCommunity.equals(newCommunity)) {
                communityChanges.add(new CommunityChange(node, oldCommunity, newCommunity));
            }
        }

        return new GraphDiff(baseDiff.getAddedNodes(), baseDiff.getRemovedNodes(),
                baseDiff.getAddedEdges(), baseDiff.getRemovedEdges(), communityChanges);
    }

    private static Set<Edge> collectEdges(IGraph graph, Set<String> nodes) {
        Set<Edge> edges = new LinkedHashSet<>();
        for (String node : nodes) {
            edges.addAll(graph.getOutEdges(node));
        }
        return edges;
    }
}
