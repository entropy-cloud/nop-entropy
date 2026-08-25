/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.graph;

import io.nop.api.core.beans.GraphBean;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import static io.nop.core.model.graph.GraphTestHelper.newGraph;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDirectedGraph extends BaseTestCase {
    @Test
    public void testCycleDetector() {
        StringGraph g = newGraph(new String[]{"a", "b", "c"}, new String[]{"e", "f"});
        Set<String> cycles = g.findCycles();
        assertTrue(cycles.isEmpty());

        g = newGraph(new String[]{"a", "b", "c"}, new String[]{"b", "a"});
        cycles = g.findCycles();
        assertEquals(3, cycles.size());
        assertTrue(!cycles.isEmpty());

        g = newGraph(new String[]{"a", "b", "c", "d", "e", "f"}, new String[]{"d", "b"});
        cycles = g.findCycles();
        assertTrue(!cycles.isEmpty());
        assertEquals(5, cycles.size());
    }

    @Test
    public void testLoop() {
        StringGraph g = new StringGraph();
        g.addEdge("b", "a");
        g.addEdge("a", "a");
        assertEquals(1, g.findCycles().size());
    }

    @Test
    public void testTopologicalOrder() {
        StringGraph g = newGraph(new String[]{"a", "b", "c", "d", "e", "f"}, new String[]{"b", "d", "x"},
                new String[]{"y", "z", "e"});

        List<String> list = CollectionHelper.iteratorToList(g.topologicalOrderIterator(true));
        assertEquals(Arrays.asList("a", "y", "b", "z", "c", "d", "e", "x", "f"), list);
    }

    @Test
    public void testTopologicalOrder2() {
        GraphBean dto = attachmentBean("beans-graph.json", GraphBean.class);
        DefaultDirectedGraph<String, DefaultEdge<String>> graph = DefaultDirectedGraph.createFromDTO(dto);
        List<String> list = CollectionHelper.iteratorToList(graph.topologicalOrderIterator(true));
        assertEquals(list.size(), graph.vertexSet().size());
        assertEquals(
                "[myParentNested, myInitBean, myDestroyBean, myLazyInitBean, myFactoryBean, myLazyInitBean2, myChild, "
                        + "myPrototypeBean, myInjectBean, testInitProperty, myParent, myGrandparent, $GEN$5, a, b, $GEN$4, c]",
                list.toString());
    }

    @Test
    public void testDepthFirstIterator() {
        StringGraph g = newGraph(new String[]{"a", "b", "c", "d", "e", "f"}, new String[]{"b", "d", "x"},
                new String[]{"y", "z", "e"});

        List<String> list = CollectionHelper.iteratorToList(g.depthFirstIterator("a"));
        assertEquals(Arrays.asList("a", "b", "c", "d", "e", "f", "x"), list);
    }

    @Test
    public void testBreadthFirstIterator() {
        StringGraph g = newGraph(new String[]{"a", "b", "c", "d", "e", "f"}, new String[]{"b", "e", "x"},
                new String[]{"y", "z", "e"});

        List<String> list = CollectionHelper.iteratorToList(g.breadthFirstIterator("a"));
        assertEquals(Arrays.asList("a", "b", "c", "e", "d", "f", "x"), list);

        String dot = GraphvizHelper.toDot(new IGraphvizAdapter<String>() {
            @Override
            public String getNodeId(String node) {
                return node;
            }

            @Override
            public String getNodeLabel(String node) {
                return node;
            }

            @Override
            public String getNodeColor(String node) {
                return null;
            }
        }, g, true, "test");
        System.out.println(dot);
    }

    @Test
    public void testSpanningTree() {
        StringGraph g = newGraph(new String[]{"a", "b", "c", "d"}, new String[]{"b", "b1", "b2", "c", "a"},
                new String[]{"b1", "b11", "b12"});

        SpanningTree<String, DefaultEdge<String>> tree = SpanningTreeFinder.find(g);
        Set<String> roots = tree.getRoots();
        assertEquals("[a]", roots.toString());
        assertEquals("{b=a, b1=b, b11=b1, b12=b11, b2=b1, c=b, d=c}", new TreeMap<>(tree.getParentMap()).toString());
        assertEquals("{a=[b], b=[c, b1], b1=[b2, b11], b11=[b12], c=[d]}",
                new TreeMap<>(tree.getChildrenMap()).toString());
    }

    /**
     * removeVertex必须同步清理全局edges集合，否则edgeSet/toGraphBean会返回端点已不存在的悬空边
     */
    @Test
    public void testRemoveVertexCleansGlobalEdgeSet() {
        StringGraph g = newGraph(new String[]{"a", "b", "c"}, new String[]{"a", "b"}, new String[]{"b", "c"});
        assertEquals(2, g.edgeSet().size());

        assertTrue(g.removeVertex("b"));

        assertTrue(g.edgeSet().isEmpty(), "edges of removed vertex must be removed from global edge set");
        GraphBean bean = g.toGraphBean(Object::toString);
        assertTrue(bean.getEdges().isEmpty());
    }

    @Test
    public void testRemoveMinorityVerticesCleansGlobalEdgeSet() {
        // 6个顶点、删除2个(2 <= 6*0.35)，走removeMinorityVertices路径
        StringGraph g = newGraph(new String[]{"a", "b", "c", "d", "e", "f"});
        g.removeAllVertices(Arrays.asList("b", "e"));

        // a->b,b->c,d->e,e->f因端点被删除而移除，仅剩c->d
        assertEquals(1, g.edgeSet().size());
        assertNotNull(g.getEdge("c", "d"));
    }

    @Test
    public void testRemoveMajorityVerticesCleansGlobalEdgeSet() {
        // 4个顶点、删除3个(3 > 4*0.35)，走removeMajorityVertices路径
        StringGraph g = newGraph(new String[]{"a", "b", "c", "d"});
        g.removeAllVertices(new java.util.HashSet<>(Arrays.asList("a", "b", "c")));
        assertTrue(g.edgeSet().isEmpty(), "edges of removed vertices must be removed from global edge set");
    }

    /**
     * allowLoop=true的迭代器上containsCycle/findCycles也应正确检出环，
     * 不能因为hasNext()内部的breakLoop清空countMap而恒返回"无环"
     */
    @Test
    public void testContainsCycleWithAllowLoopIterator() {
        StringGraph g = new StringGraph();
        g.addVertex("a");
        g.addVertex("b");
        g.addEdge("a", "b");
        g.addEdge("b", "a");

        TopologicalOrderIterator<String> it = new TopologicalOrderIterator<>(g);
        assertTrue(it.containsCycle(), "allowLoop=true iterator should still detect cycle");

        TopologicalOrderIterator<String> it2 = new TopologicalOrderIterator<>(g);
        assertEquals(2, it2.findCycles().size());
    }
}
