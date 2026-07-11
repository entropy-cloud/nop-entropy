package io.nop.graph.algorithm;

import java.util.Set;
import java.util.function.Predicate;

import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;
import io.nop.graph.api.PathQuery;

/**
 * PathQuery 执行器。
 *
 * 根据 PathQuery 配置在图上做受限 BFS 遍历。
 */
public final class PathQueryExecutor {

    private PathQueryExecutor() {
    }

    /**
     * 执行路径查询。
     *
     * @param graph 图
     * @param start 起始节点 ID
     * @param query 查询配置
     * @return 匹配的节点 ID 集合
     */
    public static Set<String> execute(IGraph graph, String start, PathQuery query) {
        if (graph == null || start == null || query == null) {
            throw new IllegalArgumentException("graph, start, and query must not be null");
        }

        String edgeType = query.getEdgeType();
        Predicate<String> nodeFilter = query.getNodeFilter();
        int maxHops = query.getMaxHops();

        Predicate<String> effectiveFilter = nodeFilter != null ? nodeFilter : nodeId -> true;

        return Bfs.traverseFiltered(graph, start, maxHops,
                node -> effectiveFilter.test(node) && edgeMatches(graph, start, node, edgeType));
    }

    private static boolean edgeMatches(IGraph graph, String from, String to, String edgeType) {
        if (edgeType == null) {
            return true;
        }
        for (Edge edge : graph.getOutEdges(from)) {
            if (edge.getTargetId().equals(to) && edgeType.equals(edge.getType())) {
                return true;
            }
        }
        return false;
    }
}
