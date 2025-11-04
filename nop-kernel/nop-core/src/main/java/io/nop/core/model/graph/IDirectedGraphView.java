/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.graph;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface IDirectedGraphView<V, E extends IEdge<V>> extends IForwardGraphView<V, E>,
        IBackwardGraphView<V, E>, IGraphViewBase<V, E> {

    IDirectedGraphView<V, E> cloneInstance();

    /**
     * 反转图中边的方向，source->target, target->source
     *
     * @return
     */
    IDirectedGraphView<V, E> reverse();

    Set<V> vertexSet();

    Set<E> edgeSet();

    default int getVertexCount() {
        return vertexSet().size();
    }

    default int getEdgeCount() {
        return edgeSet().size();
    }

    List<E> getOutwardEdges(V source);

    List<E> getInwardEdges(V vertex);

    // void forEachTarget(V source, Consumer<? super V> action);
    //
    // void forEachSource(V vertex, Consumer<? super V> action);

    List<V> getSourceVertexes(V vertex);

    List<V> getTargetVertexes(V vertex);

    E getEdge(V source, V target);

    default boolean containsEdge(V source, V target) {
        return getEdge(source, target) != null;
    }

    default boolean containsVertex(V vertex) {
        return vertexSet().contains(vertex);
    }

    default int getInwardDegree(V vertex) {
        Collection<E> edges = getInwardEdges(vertex);
        return edges == null ? 0 : edges.size();
    }

    default int getOutwardDegree(V source) {
        Collection<E> edges = getOutwardEdges(source);
        return edges == null ? 0 : edges.size();
    }

    default boolean isEmpty() {
        return vertexSet().isEmpty();
    }

    default void forEachVertex(Consumer<? super V> action) {
        vertexSet().forEach(action);
    }

    default void forEachEdge(Consumer<? super E> action) {
        edgeSet().forEach(action);
    }
    //
    // default Iterator<V> depthFirstIterator(V start) {
    // return new DepthFirstIterator<>(this, start);
    // }
    //
    // default Iterator<V> breadthFirstIterator(V start) {
    // return new BreadthFirstIterator<>(this, start);
    // }
    //
    // default Set<V> findCycles() {
    // return new CycleDetector<>(this).findCycles();
    // }

    // default TopologicalOrderIterator<V> topologicalOrderIterator(boolean allowLoop) {
    // return new TopologicalOrderIterator(vertexSet(), this, allowLoop);
    // }

    // default Set<V> reachableVertexes(V vertex) {
    // Set<V> set = new LinkedHashSet<>();
    // BreadthFirstIterator.reachable(set, this, vertex);
    // return set;
    // }

    default void copyTo(IDirectedGraph<V, E> graph) {
        forEachVertex(vertex -> {
            graph.addVertex(vertex);
        });

        forEachEdge(edge -> {
            graph.addEdge(edge);
        });
    }
}