/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.graph;

import java.util.ArrayList;
import java.util.List;

public class SpanningTreeFinder {

    public static <V, E extends IEdge<V>> SpanningTree<V, E> find(IDirectedGraphView<V, E> graph) {
        return findForEdges(graph.edgeSet());
    }

    public static <V, E extends IEdge<V>> SpanningTree<V, E> findForEdges(Iterable<E> edges) {
        UnionFind<V> uf = new UnionFind<>();

        List<E> treeEdges = new ArrayList<>();

        for (E edge : edges) {

            V source = edge.getSource();
            V target = edge.getTarget();

            if (uf.addVertex(source)) {
                uf.addVertex(target);
                uf.union(source, target);
                treeEdges.add(edge);
                continue;
            }

            if (uf.addVertex(target)) {
                uf.union(source, target);
                treeEdges.add(edge);
                continue;
            }

            V parent1 = uf.find(source);
            V parent2 = uf.find(target);
            if (parent1.equals(parent2))
                continue;

            uf.union(source, target);
            treeEdges.add(edge);
        }
        return new SpanningTree<>(treeEdges);
    }
}