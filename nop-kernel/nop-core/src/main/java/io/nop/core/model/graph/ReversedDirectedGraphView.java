/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.graph;

import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ReversedDirectedGraphView<V, E extends IEdge<V>> implements IDirectedGraphView<V, E> {
    private final IDirectedGraphView<V, E> graph;

    public ReversedDirectedGraphView(IDirectedGraphView<V, E> graph) {
        this.graph = graph;
    }

    @Override
    public IDirectedGraphView<V, E> cloneInstance() {
        return new ReversedDirectedGraphView<>(graph.cloneInstance());
    }

    @Override
    public IDirectedGraphView<V, E> reverse() {
        return graph;
    }

    @Override
    public Set<V> vertexSet() {
        return graph.vertexSet();
    }

    @Override
    public Set<E> edgeSet() {
        Set<E> edges = graph.edgeSet();

        return new AbstractSet<E>() {
            @Override
            public Iterator<E> iterator() {
                return new ReverseEdgeIterator<>(edges.iterator());
            }

            @Override
            public int size() {
                return edges.size();
            }
        };
    }

    static class ReverseEdgeIterator<V, E extends IEdge<V>> implements Iterator<E> {
        private final Iterator<E> it;

        public ReverseEdgeIterator(Iterator<E> it) {
            this.it = it;
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public E next() {
            return (E) it.next().reverse();
        }

        @Override
        public void remove() {
            it.remove();
        }
    }

    @Override
    public List<E> getOutwardEdges(V source) {
        List<E> list = graph.getInwardEdges(source);
        if (list == null || list.isEmpty())
            return Collections.emptyList();

        return new AbstractList<E>() {
            @Override
            public E get(int index) {
                return (E) list.get(index).reverse();
            }

            @Override
            public int size() {
                return list.size();
            }
        };
    }

    @Override
    public List<E> getInwardEdges(V vertex) {
        List<E> list = graph.getOutwardEdges(vertex);
        if (list == null || list.isEmpty())
            return Collections.emptyList();

        return new AbstractList<E>() {
            @Override
            public E get(int index) {
                return (E) list.get(index).reverse();
            }

            @Override
            public int size() {
                return list.size();
            }
        };
    }

    @Override
    public void forEachTarget(V source, Consumer<? super V> action) {
        graph.forEachSource(source, action);
    }

    @Override
    public void forEachSource(V vertex, Consumer<? super V> action) {
        graph.forEachTarget(vertex, action);
    }

    @Override
    public E getEdge(V source, V target) {
        E edge = graph.getEdge(target, source);
        return edge == null ? null : (E) edge.reverse();
    }

    @Override
    public List<V> getSourceVertexes(V vertex) {
        return graph.getTargetVertexes(vertex);
    }

    @Override
    public List<V> getTargetVertexes(V vertex) {
        return graph.getSourceVertexes(vertex);
    }

    @Override
    public boolean containsEdge(V source, V target) {
        return graph.containsEdge(target, source);
    }

    @Override
    public boolean containsVertex(V vertex) {
        return graph.containsVertex(vertex);
    }

    @Override
    public int getInwardDegree(V vertex) {
        return graph.getOutwardDegree(vertex);
    }

    @Override
    public int getOutwardDegree(V source) {
        return graph.getInwardDegree(source);
    }

    @Override
    public boolean isEmpty() {
        return graph.isEmpty();
    }

    @Override
    public void forEachVertex(Consumer<? super V> action) {
        graph.forEachVertex(action);
    }

    @Override
    public void forEachEdge(Consumer<? super E> action) {
        graph.forEachEdge(edge -> {
            action.accept((E) edge.reverse());
        });
    }
}