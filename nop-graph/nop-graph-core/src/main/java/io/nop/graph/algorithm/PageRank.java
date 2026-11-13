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
     * <p>出度预计算（5.6-W-07，plan 2529 裁定⑤）：迭代开始前对入参 nodes 恰每节点一次
     * {@code getOutEdges(node).size()}（Θ(V) 次调用、Θ(V+E) 次元素拷贝，单次全量）；
     * 迭代体内悬挂节点判定与入边源的出度读取全部改读映射——迭代期出边查询归零。否则
     * 每条入边每次迭代都触发一次出边列表查询，实现侧（如 InMemoryGraph）为锁内全表
     * 防御拷贝，单迭代产生 E·d̄ 次元素拷贝与等量临时 List 分配。预计算值 ≡ 逐次调用值
     * （图在 compute 期间不可变属 IGraph 消费既有假设）；nodes 之外的 source 经入边循环
     * 的 containsKey 天然跳过，无需出度。
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
        Map<String, Integer> outDegree = new HashMap<>();
        for (String node : nodes) {
            outDegree.put(node, graph.getOutEdges(node).size());
        }

        Map<String, Double> ranks = new HashMap<>();
        for (String node : nodes) {
            ranks.put(node, 1.0 / n);
        }

        for (int i = 0; i < iterations; i++) {
            Map<String, Double> newRanks = new HashMap<>();
            double danglingSum = 0.0;

            for (String node : nodes) {
                if (outDegree.get(node) == 0) {
                    danglingSum += ranks.get(node);
                }
            }

            for (String node : nodes) {
                double rank = (1.0 - DAMPING_FACTOR) / n;
                rank += DAMPING_FACTOR * danglingSum / n;

                for (Edge edge : graph.getInEdges(node)) {
                    String source = edge.getSourceId();
                    if (ranks.containsKey(source)) {
                        int sourceOutDegree = outDegree.get(source);
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
