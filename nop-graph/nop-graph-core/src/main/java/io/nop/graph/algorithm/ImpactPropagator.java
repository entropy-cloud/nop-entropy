package io.nop.graph.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import io.nop.graph.api.Edge;
import io.nop.graph.api.ImpactConfig;
import io.nop.graph.api.ImpactedNode;
import io.nop.graph.api.ImpactResult;
import io.nop.graph.api.IGraph;

/**
 * 影响传播算法。
 *
 * 从指定节点出发，做双向 BFS：
 * - downstream：沿出边方向（目标会影响谁）
 * - upstream：沿入边方向（谁会影响目标）
 *
 * 支持权重和深度衰减，以及 maxNodes 限制。
 */
public final class ImpactPropagator {

    private ImpactPropagator() {
    }

    /**
     * 计算指定节点的影响范围。
     *
     * @param graph  图
     * @param start  起始节点 ID
     * @param config 配置（maxDepth, maxNodes）
     * @return 影响分析结果（含 upstream + downstream）
     */
    public static ImpactResult propagate(IGraph graph, String start, ImpactConfig config) {
        if (graph == null || start == null || config == null) {
            throw new IllegalArgumentException("graph, start, and config must not be null");
        }

        List<ImpactedNode> downstream = traceDirection(graph, start, config, true);
        List<ImpactedNode> upstream = traceDirection(graph, start, config, false);

        String riskLevel = evaluateRisk(upstream.size(), downstream.size(),
                getMaxDepth(upstream), getMaxDepth(downstream));

        return new ImpactResult(start, upstream, downstream, riskLevel,
                Math.max(getMaxDepth(upstream), getMaxDepth(downstream)));
    }

    /**
     * 沿指定方向做 BFS。
     *
     * @param forward true = 出边方向（downstream），false = 入边方向（upstream）
     */
    private static List<ImpactedNode> traceDirection(IGraph graph, String start,
                                                      ImpactConfig config, boolean forward) {
        List<ImpactedNode> impacted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String[]> queue = new LinkedList<>();

        queue.add(new String[]{start, "0"});
        visited.add(start);

        int maxDepth = config.getMaxDepth();
        int maxNodes = config.getMaxNodes();

        while (!queue.isEmpty() && impacted.size() < maxNodes) {
            String[] current = queue.poll();
            String nodeId = current[0];
            int depth = Integer.parseInt(current[1]);

            if (depth >= maxDepth) {
                continue;
            }

            List<Edge> edges = forward
                    ? graph.getOutEdges(nodeId)
                    : graph.getInEdges(nodeId);

            for (Edge edge : edges) {
                String neighbor = forward ? edge.getTargetId() : edge.getSourceId();

                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    impacted.add(new ImpactedNode(neighbor, depth + 1));

                    if (impacted.size() >= maxNodes) {
                        break;
                    }

                    queue.add(new String[]{neighbor, String.valueOf(depth + 1)});
                }
            }
        }

        return impacted;
    }

    private static String evaluateRisk(int upstreamCount, int downstreamCount,
                                        int maxUpstreamDepth, int maxDownstreamDepth) {
        int total = upstreamCount + downstreamCount;
        int maxDepth = Math.max(maxUpstreamDepth, maxDownstreamDepth);

        if (total > 50 || maxDepth > 5) {
            return "critical";
        } else if (total > 20 || maxDepth > 3) {
            return "high";
        } else if (total > 5) {
            return "medium";
        } else {
            return "low";
        }
    }

    private static int getMaxDepth(List<ImpactedNode> nodes) {
        return nodes.stream()
                .mapToInt(ImpactedNode::getDepth)
                .max()
                .orElse(0);
    }
}
