/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.graph;

import io.nop.commons.util.CollectionHelper;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import static io.nop.core.model.graph.GraphTestHelper.newGraph;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    public void testTopologicalOrder() {
        StringGraph g = newGraph(new String[]{"a", "b", "c", "d", "e", "f"}, new String[]{"b", "d", "x"},
                new String[]{"y", "z", "e"});

        List<String> list = CollectionHelper.iteratorToList(g.topologicalOrderIterator(true));
        assertEquals(Arrays.asList("a", "y", "b", "z", "c", "d", "e", "x", "f"), list);
    }

    @Test
    public void testTopologicalOrder2() {
        GraphDTO dto = attachmentBean("beans-graph.json", GraphDTO.class);
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
}
