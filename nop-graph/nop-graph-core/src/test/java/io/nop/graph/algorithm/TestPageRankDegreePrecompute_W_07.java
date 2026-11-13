package io.nop.graph.algorithm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;
import io.nop.graph.impl.InMemoryGraph;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 5.6-W-07（plan 2529 Phase 2 组 C / 设计裁定⑤）PageRank 邻接分配放大回归钉。
 *
 * <p>缺陷形态（CS-004 族主 + CS-R2-003 生产载体吸收）：迭代体内每条入边调用
 * {@code graph.getOutEdges(source).size()} 仅为读出度——{@code InMemoryGraph.getOutEdges}
 * 每次锁内全表防御拷贝，单迭代总拷贝元素数 E·d̄；悬挂节点判定段 ：49 每节点每迭代一次为
 * 同族轻症。修复形态：compute 开头一次性预计算 outDegree 映射（恰每节点一次
 * {@code getOutEdges} 调用），迭代期邻接出边查询归零。
 *
 * <p>红⑤ = 计数代理 IGraph 结构性断言（无墙钟断言——CI 抖动面）：V=20/E=60/iterations=3，
 * pre-fix 调用数 (V+E)·iterations = 240，post-fix 恰 V = 20。正确性回归双路径：
 * {@code TestAlgorithms} PageRank 正确性锚 + 生产路径 {@code TestGraphMetricMaterialization}
 * （nop-code-service）零回退；数值语义零变更（预计算值 ≡ 逐次调用值——图在 compute 期间
 * 不可变属 IGraph 消费既有假设）。CL-204（IGraph 缺 degree 只读 API）为结构性根因附注，
 * API 级治理预设禁选（条件申报②不触发）。
 */
class TestPageRankDegreePrecompute_W_07 {

    /**
     * 计数代理：分别统计出边/入边查询次数（红⑤ 判据 = 出边查询数）。
     */
    static final class CountingGraph implements IGraph {
        private final IGraph delegate;
        final AtomicInteger outQueries = new AtomicInteger();
        final AtomicInteger inQueries = new AtomicInteger();

        CountingGraph(IGraph delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<Edge> getOutEdges(String nodeId) {
            outQueries.incrementAndGet();
            return delegate.getOutEdges(nodeId);
        }

        @Override
        public List<Edge> getInEdges(String nodeId) {
            inQueries.incrementAndGet();
            return delegate.getInEdges(nodeId);
        }
    }

    private static InMemoryGraph buildGraph(int nodeCount) {
        InMemoryGraph g = new InMemoryGraph();
        for (int i = 0; i < nodeCount; i++) {
            // 3 outgoing edges per node over a wrapped ring: V=20 nodes, E=60 edges
            g.addEdge(node(i, nodeCount), node(i + 1, nodeCount));
            g.addEdge(node(i, nodeCount), node(i + 2, nodeCount));
            g.addEdge(node(i, nodeCount), node(i + 3, nodeCount));
        }
        return g;
    }

    private static String node(int i, int nodeCount) {
        return "n" + (i % nodeCount);
    }

    /**
     * 计数钉（主判据）：预计算恰每节点一次出边查询——迭代期零出边查询。
     */
    @Test
    void testW_07_outEdgesQueriedExactlyOncePerNode() {
        InMemoryGraph g = buildGraph(20);
        assertEquals(60, g.getEdgeCount(), "fixture graph must have V=20/E=60");
        Set<String> nodes = new HashSet<>(g.nodeSet());
        assertEquals(20, nodes.size());

        CountingGraph proxy = new CountingGraph(g);
        Map<String, Double> ranks = PageRank.compute(proxy, nodes, 3);

        assertEquals(20, proxy.outQueries.get(),
                "getOutEdges must be queried exactly once per node (degree precompute), was "
                        + proxy.outQueries.get() + " (pre-fix shape: (V+E)*iterations = 240)");
        // in-edge enumeration stays per iteration (algorithm-inherent, ruling 5.3)
        assertEquals(60, proxy.inQueries.get(),
                "getInEdges stays one query per node per iteration (3 * 20)");
    }

    /**
     * 数值语义零变更：预计算路径与既有算法在小图上的 rank 质量守恒 + 排序正确性。
     */
    @Test
    void testW_07_rankSemanticsUnchanged() {
        InMemoryGraph g = buildGraph(20);
        Set<String> nodes = new HashSet<>(g.nodeSet());

        Map<String, Double> ranks = PageRank.compute(g, nodes, 20);

        double sum = ranks.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 1e-6, "PageRank mass must be conserved");
        // the wrapped ring gives every node identical in/out structure: ranks must be uniform
        double first = ranks.get("n0");
        for (String n : nodes) {
            assertEquals(first, ranks.get(n), 1e-9,
                    "symmetric ring graph must yield uniform ranks");
        }
    }
}
