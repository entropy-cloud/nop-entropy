package io.nop.graph.algorithm;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

import io.nop.graph.api.BfsResult;
import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;

/**
 * BFS 遍历算法。
 */
public final class Bfs {

    private Bfs() {
    }

    /**
     * 从指定节点出发，BFS 遍历到 maxDepth 层，返回所有可达节点（不含起点）。
     */
    public static Set<String> traverse(IGraph graph, String start, int maxDepth) {
        if (graph == null || start == null) {
            throw new IllegalArgumentException("graph and start must not be null");
        }
        if (maxDepth <= 0) {
            return Collections.emptySet();
        }

        Set<String> visited = new LinkedHashSet<>();
        Queue<String[]> queue = new LinkedList<>();
        queue.add(new String[]{start, "0"});
        visited.add(start);

        while (!queue.isEmpty()) {
            String[] current = queue.poll();
            String nodeId = current[0];
            int depth = Integer.parseInt(current[1]);

            if (depth >= maxDepth) {
                continue;
            }

            for (Edge edge : graph.getOutEdges(nodeId)) {
                String target = edge.getTargetId();
                if (visited.add(target)) {
                    queue.add(new String[]{target, String.valueOf(depth + 1)});
                }
            }
        }

        visited.remove(start);
        return visited;
    }

    /**
     * BFS 遍历，返回带深度的结果。
     */
    public static BfsResult traverseWithDepth(IGraph graph, String start, int maxDepth) {
        if (graph == null || start == null) {
            throw new IllegalArgumentException("graph and start must not be null");
        }

        Set<String> reachable = new LinkedHashSet<>();
        Map<String, Integer> depthMap = new HashMap<>();
        Queue<String[]> queue = new LinkedList<>();
        queue.add(new String[]{start, "0"});
        reachable.add(start);
        depthMap.put(start, 0);

        while (!queue.isEmpty()) {
            String[] current = queue.poll();
            String nodeId = current[0];
            int depth = Integer.parseInt(current[1]);

            if (depth >= maxDepth) {
                continue;
            }

            for (Edge edge : graph.getOutEdges(nodeId)) {
                String target = edge.getTargetId();
                if (!depthMap.containsKey(target)) {
                    depthMap.put(target, depth + 1);
                    reachable.add(target);
                    queue.add(new String[]{target, String.valueOf(depth + 1)});
                }
            }
        }

        return new BfsResult(reachable, depthMap);
    }

    /**
     * BFS 遍历，带边类型过滤和节点过滤。
     */
    public static Set<String> traverseFiltered(IGraph graph, String start, int maxDepth,
                                                Predicate<String> nodeFilter) {
        if (graph == null || start == null) {
            throw new IllegalArgumentException("graph and start must not be null");
        }
        if (maxDepth <= 0) {
            return Collections.emptySet();
        }

        Set<String> visited = new LinkedHashSet<>();
        Queue<String[]> queue = new LinkedList<>();
        queue.add(new String[]{start, "0"});
        visited.add(start);

        while (!queue.isEmpty()) {
            String[] current = queue.poll();
            String nodeId = current[0];
            int depth = Integer.parseInt(current[1]);

            if (depth >= maxDepth) {
                continue;
            }

            for (Edge edge : graph.getOutEdges(nodeId)) {
                String target = edge.getTargetId();
                if (!visited.contains(target)) {
                    visited.add(target);
                    if (nodeFilter == null || nodeFilter.test(target)) {
                        queue.add(new String[]{target, String.valueOf(depth + 1)});
                    }
                }
            }
        }

        visited.remove(start);
        return visited;
    }
}
