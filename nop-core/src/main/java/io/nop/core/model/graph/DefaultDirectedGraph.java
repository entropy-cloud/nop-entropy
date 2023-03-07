/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.core.model.graph;

import com.google.common.collect.Ordering;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Default implementation of {@link IDirectedGraph}.
 *
 * @param <V> Vertex type
 * @param <E> Edge type
 */
public class DefaultDirectedGraph<V, E extends IEdge<V>> implements IDirectedGraph<V, E> {
    private final Set<E> edges = new LinkedHashSet<>();
    private final Map<V, VertexInfo<V, E>> vertexMap = new LinkedHashMap<>();
    private final IEdgeFactory<V, E> edgeFactory;

    /**
     * Creates a graph.
     */
    public DefaultDirectedGraph(IEdgeFactory<V, E> edgeFactory) {
        this.edgeFactory = edgeFactory;
    }

    public static <V> DefaultDirectedGraph<V, DefaultEdge<V>> create() {
        return create(DefaultEdge.factory());
    }

    public static <V, E extends IEdge<V>> DefaultDirectedGraph<V, E> create(IEdgeFactory<V, E> edgeFactory) {
        return new DefaultDirectedGraph<>(edgeFactory);
    }

    public static DefaultDirectedGraph<String, DefaultEdge<String>> createFromDTO(GraphDTO dto) {
        DefaultDirectedGraph<String, DefaultEdge<String>> graph = create();
        if (dto.getVertices() != null) {
            for (String vertex : dto.getVertices()) {
                graph.addVertex(vertex);
            }
        }

        if (dto.getEdges() != null) {
            for (GraphDTO.EdgeDTO edgeDTO : dto.getEdges()) {
                graph.addEdge(edgeDTO.getSource(), edgeDTO.getTarget());
            }
        }
        return graph;
    }

    public String toStringUnordered() {
        return "graph(" + "vertices: " + vertexMap.keySet() + ", edges: " + edges + ")";
    }

    public GraphDTO toGraphDTO() {
        GraphDTO dto = new GraphDTO();
        List<String> vertices = new ArrayList<>(vertexMap.size());
        for (V v : vertexMap.keySet()) {
            vertices.add(v.toString());
        }
        List<GraphDTO.EdgeDTO> edges = new ArrayList<>(this.edges.size());
        for (E edge : this.edges) {
            GraphDTO.EdgeDTO edgeDTO = new GraphDTO.EdgeDTO();
            edgeDTO.setSource(edge.getSource().toString());
            edgeDTO.setTarget(edge.getTarget().toString());
            edges.add(edgeDTO);
        }
        dto.setVertices(vertices);
        dto.setEdges(edges);
        return dto;
    }

    @Override
    public String toString() {
        @SuppressWarnings("unchecked") final Ordering<V> vertexOrdering = (Ordering) Ordering.usingToString();
        @SuppressWarnings("unchecked") final Ordering<E> edgeOrdering = (Ordering) Ordering.usingToString();
        return toString(vertexOrdering, edgeOrdering);
    }

    /**
     * Returns the string representation of this graph, using the given orderings to ensure that the output order of
     * vertices and edges is deterministic.
     */
    private String toString(Ordering<V> vertexOrdering, Ordering<E> edgeOrdering) {
        return "graph(" + "vertices: " + vertexOrdering.sortedCopy(vertexMap.keySet()) + ", edges: "
                + edgeOrdering.sortedCopy(edges) + ")";
    }

    public boolean addVertex(V vertex) {
        if (vertexMap.containsKey(vertex)) {
            return false;
        } else {
            vertexMap.put(vertex, new VertexInfo<>());
            return true;
        }
    }

    public Set<E> edgeSet() {
        return Collections.unmodifiableSet(edges);
    }

    @Override
    public IDirectedGraph<V, E> cloneInstance() {
        DefaultDirectedGraph<V, E> graph = new DefaultDirectedGraph<>(edgeFactory);
        copyTo(graph);
        return graph;
    }

    @Override
    public IDirectedGraphView<V, E> reverse() {
        return new ReversedDirectedGraphView<>(this);
    }

    public E createEdge(V source, V target) {
        return edgeFactory.createEdge(source, target);
    }

    VertexInfo<V, E> makeVertexInfo(V vertex) {
        VertexInfo<V, E> info = vertexMap.get(vertex);
        if (info == null) {
            info = new VertexInfo<>();
            vertexMap.put(vertex, info);
        }
        return info;
    }

    public E addEdge(V vertex, V targetVertex) {
        final VertexInfo<V, E> info = makeVertexInfo(vertex);
        final VertexInfo<V, E> targetInfo = makeVertexInfo(targetVertex);

        final E edge = edgeFactory.createEdge(vertex, targetVertex);
        if (edges.add(edge)) {
            info.outEdges.add(edge);
            targetInfo.inEdges.add(edge);
            return edge;
        } else {
            return null;
        }
    }

    public boolean addEdge(E edge) {
        V vertex = edge.getSource();
        V targetVertex = edge.getTarget();

        if (edges.add(edge)) {
            final VertexInfo<V, E> info = makeVertexInfo(vertex);
            final VertexInfo<V, E> targetInfo = makeVertexInfo(targetVertex);
            info.outEdges.add(edge);
            targetInfo.inEdges.add(edge);
            return true;
        } else {
            return false;
        }
    }

    public E getEdge(V source, V target) {
        // REVIEW: could instead use edges.get(new DefaultEdge(source, target))
        final VertexInfo<V, E> info = vertexMap.get(source);
        for (E outEdge : info.outEdges) {
            if (outEdge.getTarget().equals(target)) {
                return outEdge;
            }
        }
        return null;
    }

    public boolean removeEdge(V source, V target) {
        // remove out edges
        final List<E> outEdges = vertexMap.get(source).outEdges;
        boolean outRemoved = false;
        for (int i = 0, size = outEdges.size(); i < size; i++) {
            E edge = outEdges.get(i);
            if (edge.getTarget().equals(target)) {
                outEdges.remove(i);
                edges.remove(edge);
                outRemoved = true;
                break;
            }
        }

        // remove in edges
        final List<E> inEdges = vertexMap.get(target).inEdges;
        boolean inRemoved = false;
        for (int i = 0, size = inEdges.size(); i < size; i++) {
            E edge = inEdges.get(i);
            if (edge.getSource().equals(source)) {
                inEdges.remove(i);
                inRemoved = true;
                break;
            }
        }
        assert outRemoved == inRemoved;
        return outRemoved;
    }

    public Set<V> vertexSet() {
        return vertexMap.keySet();
    }

    @Override
    public boolean removeVertex(V v) {
        final VertexInfo<V, E> info = vertexMap.remove(v);
        if (info == null) {
            return false;
        }

        _removeEdges(info,v);
        return true;
    }

    private void _removeEdges(VertexInfo<V, E> info, V v) {
        // remove all edges pointing to v
        for (E edge : info.inEdges) {
            final V source = edge.getSource();
            final VertexInfo<V, E> sourceInfo = vertexMap.get(source);
            sourceInfo.outEdges.removeIf(e -> e.getTarget().equals(v));
        }

        // remove all edges starting from v
        for (E edge : info.outEdges) {
            final V target = edge.getTarget();
            final VertexInfo<V, E> targetInfo = vertexMap.get(target);
            targetInfo.inEdges.removeIf(e -> e.getSource().equals(v));
        }
    }

    public void removeAllVertices(Collection<V> collection) {
        // The point at which collection is large enough to make the 'majority'
        // algorithm more efficient.
        final float threshold = 0.35f;
        final int thresholdSize = (int) (vertexMap.size() * threshold);
        if (collection.size() > thresholdSize && !(collection instanceof Set)) {
            // Convert collection to a set, so that collection.contains() is
            // faster. If there are duplicates, collection.size() will get smaller.
            collection = new HashSet<>(collection);
        }
        if (collection.size() > thresholdSize) {
            removeMajorityVertices((Set<V>) collection);
        } else {
            removeMinorityVertices(collection);
        }
    }

    /**
     * Implementation of {@link #removeAllVertices(Collection)} that is efficient if {@code collection} is a small
     * fraction of the set of vertices.
     */
    private void removeMinorityVertices(Collection<V> collection) {
        for (V v : collection) {
            final VertexInfo<V, E> info = vertexMap.get(v);
            if (info == null) {
                continue;
            }

            _removeEdges(info,v);
        }
        vertexMap.keySet().removeAll(collection);
    }

    /**
     * Implementation of {@link #removeAllVertices(Collection)} that is efficient if {@code vertexSet} is a large
     * fraction of the set of vertices in the graph.
     */
    private void removeMajorityVertices(Set<V> vertexSet) {
        vertexMap.keySet().removeAll(vertexSet);
        for (VertexInfo<V, E> info : vertexMap.values()) {
            info.outEdges.removeIf(e -> vertexSet.contains(e.getTarget()));
            info.inEdges.removeIf(e -> vertexSet.contains(e.getSource()));
        }
    }

    public List<E> getOutwardEdges(V source) {
        VertexInfo<V, E> info = vertexMap.get(source);
        return info == null ? Collections.emptyList() : info.outEdges;
    }

    public List<E> getInwardEdges(V target) {
        VertexInfo<V, E> info = vertexMap.get(target);
        return info == null ? Collections.emptyList() : info.inEdges;
    }

    @Override
    public void forEachTarget(V source, Consumer<? super V> action) {
        for (E edge : getOutwardEdges(source)) {
            V target = edge.getTarget();
            action.accept(target);
        }
    }

    @Override
    public void forEachSource(V vertex, Consumer<? super V> action) {
        for (E edge : getInwardEdges(vertex)) {
            V target = edge.getSource();
            action.accept(target);
        }
    }

    @Override
    public List<V> getSourceVertexes(V vertex) {
        final List<E> edges = getInwardEdges(vertex);
        if (edges.isEmpty())
            return Collections.emptyList();

        return new AbstractList<V>() {
            public V get(int index) {
                return edges.get(index).getSource();
            }

            public int size() {
                return edges.size();
            }
        };
    }

    public boolean hasOutwardEdge(V vertex) {
        VertexInfo<V, E> info = vertexMap.get(vertex);
        return info != null && !info.outEdges.isEmpty();
    }

    @Override
    public List<V> getTargetVertexes(V source) {
        final List<E> edges = getOutwardEdges(source);
        if (edges.isEmpty())
            return Collections.emptyList();

        return new AbstractList<V>() {
            public V get(int index) {
                return edges.get(index).getTarget();
            }

            public int size() {
                return edges.size();
            }
        };
    }

    /**
     * Information about a vertex.
     *
     * @param <V> Vertex type
     * @param <E> Edge type
     */
    private static class VertexInfo<V, E> {
        private final List<E> outEdges = new ArrayList<>();
        private final List<E> inEdges = new ArrayList<>();
    }
}