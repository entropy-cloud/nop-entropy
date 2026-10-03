package io.nop.core.model.graph;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGraphAlgorithms {

    /**
     * 测试专用有向图：同时实现 Reachability 需要的 IDirectedGraphVertexView
     * 与 LowestCommonAncestorFinder 需要的 IBackwardGraphView。
     */
    static class MapGraph implements IDirectedGraphVertexView<String>, IBackwardGraphView<String, DefaultEdge<String>>,
                IOutwardEdgeVisitor<String, DefaultEdge<String>> {
        final Map<String, Set<String>> outs = new LinkedHashMap<>();
        final Map<String, Set<String>> ins = new LinkedHashMap<>();

        void addEdge(String from, String to) {
            outs.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
            ins.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);
            outs.computeIfAbsent(to, k -> new LinkedHashSet<>());
            ins.computeIfAbsent(from, k -> new LinkedHashSet<>());
        }

        @Override
        public Set<String> vertexSet() {
            return outs.keySet();
        }

        @Override
        public List<String> getSourceVertexes(String vertex) {
            return new ArrayList<>(ins.getOrDefault(vertex, Collections.emptySet()));
        }

        @Override
        public Collection<String> getTargetVertexes(String vertex) {
            return outs.getOrDefault(vertex, Collections.emptySet());
        }

        @Override
        public List<DefaultEdge<String>> getInwardEdges(String vertex) {
            return Collections.emptyList();
        }

        @Override
        public List<DefaultEdge<String>> getOutwardEdges(String source) {
            List<DefaultEdge<String>> list = new ArrayList<>();
            for (String target : outs.getOrDefault(source, Collections.emptySet())) {
                list.add(new DefaultEdge<>(source, target));
            }
            return list;
        }
    }

    /**
     * DAG: a->b->d, a->c->d, d->e
     */
    private static MapGraph newDiamond() {
        MapGraph g = new MapGraph();
        g.addEdge("a", "b");
        g.addEdge("a", "c");
        g.addEdge("b", "d");
        g.addEdge("c", "d");
        g.addEdge("d", "e");
        return g;
    }

    @Test
    public void testReachabilityForwardAndBackward() {
        Reachability<String> reach = new Reachability<>(newDiamond());

        Set<String> fromA = reach.reachableNodesFrom("a");
        assertEquals(Set.of("a", "b", "c", "d", "e"), fromA, "a should reach all nodes");

        Set<String> fromD = reach.reachableNodesFrom("d");
        assertEquals(Set.of("d", "e"), fromD);

        Set<String> reachD = reach.nodesReach("d");
        assertEquals(Set.of("a", "b", "c", "d"), reachD, "a/b/c can reach d");

        // passedNodes = 既是 a 可达又是能到达 d 的节点
        Set<String> passed = reach.passedNodes("a", "d");
        assertEquals(Set.of("a", "b", "c", "d"), passed);

        // 结果被缓存：重复调用返回同一集合
        assertTrue(fromA == reach.reachableNodesFrom("a"), "reachableNodesFrom should cache result");
    }

    @Test
    public void testReachabilitySingleVertex() {
        Reachability<String> reach = new Reachability<>(newDiamond());
        Set<String> reachE = reach.nodesReach("e");
        assertEquals(Set.of("a", "b", "c", "d", "e"), reachE, "everything reaches the sink e");
    }

    @Test
    public void testLowestCommonAncestorInDag() {
        MapGraph g = newDiamond();

        // b 和 c 的公共祖先: a（b/c 自身不互为祖先）
        Set<String> lca = new LowestCommonAncestorFinder<String, DefaultEdge<String>>(g).findAll(Set.of("b", "c"));
        assertEquals(Set.of("a"), lca, "LCA of b and c should be a");

        // a 和 e 的公共祖先: a 自身
        Set<String> lca2 = new LowestCommonAncestorFinder<String, DefaultEdge<String>>(g)
                .findAll(Set.of("a", "e"));
        assertEquals(Set.of("a"), lca2);

        // d 和 e 的最近公共祖先: d（d 是 e 的祖先）
        Set<String> lca3 = new LowestCommonAncestorFinder<String, DefaultEdge<String>>(g)
                .findAll(Set.of("d", "e"));
        assertEquals(Set.of("d"), lca3);
    }

    @Test
    public void testAStarFindPathWithDefaultCost() {
        AStarPathFinder<String, DefaultEdge<String>> finder = new AStarPathFinder<>(newDiamond());

        AStarPathFinder.FindResult<String, DefaultEdge<String>> result = finder.find("a", "e");
        assertEquals(3, result.getCost(), "unweighted path a->b->d->e costs 3");
        // scoreMap 修复后路径可重建（回归覆盖 wi1#5）
        List<DefaultEdge<String>> path = result.getPath();
        assertEquals(3, path.size(), "path should contain 3 edges a->b->d->e");
        assertEquals("a", path.get(0).getSource());
        assertEquals("b", path.get(0).getTarget());
        assertEquals("b", path.get(1).getSource());
        assertEquals("d", path.get(1).getTarget());
        assertEquals("d", path.get(2).getSource());
        assertEquals("e", path.get(2).getTarget());
    }

    @Test
    public void testAStarWeightedRelaxationChoosesCheaperPath() {
        // 回归覆盖 wi1#5：a->c 直连代价 10，a->b->c 代价 4，松弛后应选后者
        MapGraph g = new MapGraph();
        g.addEdge("a", "c");
        g.addEdge("a", "b");
        g.addEdge("b", "c");
        AStarPathFinder<String, DefaultEdge<String>> finder = new AStarPathFinder<>(g,
                edge -> "a->c".equals(edge.getSource() + "->" + edge.getTarget()) ? 10 : 1,
                (start, end) -> 0);

        AStarPathFinder.FindResult<String, DefaultEdge<String>> result = finder.find("a", "c");
        assertEquals(2, result.getCost(), "weighted relaxation should pick a->b->c with cost 2");
        assertEquals(2, result.getPath().size(), "path should be a->b->c");
        assertEquals("b", result.getPath().get(0).getTarget());
    }

    @Test
    public void testAStarFindSingleEdgeCost() {
        MapGraph g = new MapGraph();
        g.addEdge("a", "c");
        AStarPathFinder<String, DefaultEdge<String>> finder = new AStarPathFinder<>(g,
                edge -> 7, (start, end) -> 0);
        AStarPathFinder.FindResult<String, DefaultEdge<String>> result = finder.find("a", "c");
        assertEquals(7, result.getCost());
        assertEquals(1, result.getPath().size(), "single-edge path should be reconstructed");
        assertEquals("c", result.getPath().get(0).getTarget());
    }

    @Test
    public void testAStarNoPathReturnsMinusOne() {
        MapGraph g = new MapGraph();
        g.addEdge("a", "b");
        // c 不可达
        AStarPathFinder<String, DefaultEdge<String>> finder = new AStarPathFinder<>(g);
        AStarPathFinder.FindResult<String, DefaultEdge<String>> result = finder.find("a", "c");
        assertEquals(-1, result.getCost());
        assertTrue(result.getPath().isEmpty(), "unreachable target should produce empty path");
    }

    @Test
    public void testAStarFindStartReturnsZeroCost() {
        AStarPathFinder<String, DefaultEdge<String>> finder = new AStarPathFinder<>(newDiamond());
        // start == end 时代价为 0
        assertEquals(0, finder.find("a", "a").getCost());
        assertEquals(1, finder.find("a", "b").getCost());
        assertEquals(2, finder.find("a", "d").getCost());
    }

    @Test
    public void testDefaultEdgeReverse() {
        DefaultEdge<String> edge = new DefaultEdge<>("a", "b");
        assertEquals("a", edge.getSource());
        assertEquals("b", edge.getTarget());
        IEdge<String> rev = edge.reverse();
        assertEquals("b", rev.getSource());
        assertEquals("a", rev.getTarget());
    }

    @Test
    public void testLcaSingleRootContainsItself() {
        MapGraph g = newDiamond();
        LowestCommonAncestorFinder<String, DefaultEdge<String>> finder = new LowestCommonAncestorFinder<>(g);
        Set<String> lca = finder.findAll(Set.of("b"));
        assertTrue(lca.contains("b"), "single root should be contained in its ancestor set");
    }
}
