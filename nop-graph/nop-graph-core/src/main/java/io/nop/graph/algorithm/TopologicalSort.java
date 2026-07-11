package io.nop.graph.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;

/**
 * 拓扑排序算法（Kahn 算法）。
 *
 * 如果图中存在环，抛出 IllegalArgumentException。
 */
public final class TopologicalSort {

    private TopologicalSort() {
    }

    /**
     * 对图中的节点集做拓扑排序。
     *
     * @param graph 图
     * @param nodes 要排序的节点集
     * @return 拓扑排序后的节点列表
     * @throws IllegalArgumentException 如果图中存在环
     */
    public static List<String> sort(IGraph graph, Set<String> nodes) {
        if (graph == null || nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("graph and nodes must not be null or empty");
        }

        Map<String, Integer> inDegree = new HashMap<>();
        for (String node : nodes) {
            inDegree.put(node, 0);
        }

        for (String node : nodes) {
            for (Edge edge : graph.getOutEdges(node)) {
                String target = edge.getTargetId();
                if (inDegree.containsKey(target)) {
                    inDegree.merge(target, 1, Integer::sum);
                }
            }
        }

        List<String> queue = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.remove(0);
            result.add(node);

            for (Edge edge : graph.getOutEdges(node)) {
                String target = edge.getTargetId();
                if (inDegree.containsKey(target)) {
                    int newDeg = inDegree.merge(target, -1, Integer::sum);
                    if (newDeg == 0) {
                        queue.add(target);
                    }
                }
            }
        }

        if (result.size() != nodes.size()) {
            throw new IllegalArgumentException("Graph contains a cycle, cannot topologically sort");
        }

        return result;
    }
}
