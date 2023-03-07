/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;

public class AStarPathFinder<V, E extends IEdge<V>> {
    static final ToIntFunction DEFAULT_COST_FUNCTION = edge -> 1;
    static final ToIntBiFunction DEFAULT_HEURISTIC_FUNCTION = (start, end) -> 0;

    private final IOutwardEdgeVisitor<V, E> graph;
    private final ToIntFunction<E> costFn;
    private final ToIntBiFunction<V, V> heuristicFn;

    public AStarPathFinder(IOutwardEdgeVisitor<V, E> graph, ToIntFunction<E> costFn,
                           ToIntBiFunction<V, V> heuristicFn) {
        this.graph = graph;
        this.costFn = costFn;
        this.heuristicFn = heuristicFn;
    }

    public AStarPathFinder(IOutwardEdgeVisitor<V, E> graph) {
        this(graph, DEFAULT_COST_FUNCTION, DEFAULT_HEURISTIC_FUNCTION);
    }

    public static class FindResult<V, E extends IEdge<V>> {
        private final int cost;
        private final List<E> path;

        public FindResult(int cost, List<E> path) {
            this.cost = cost;
            this.path = path;
        }

        public int getCost() {
            return cost;
        }

        public List<E> getPath() {
            return path;
        }
    }

    public FindResult<V, E> find(V start, V end) {
        Map<V, DNode<V, E>> scoreMap = new HashMap<>();
        int cost = find(start, end, scoreMap);
        List<E> path = reconstructPath(scoreMap, end);
        return new FindResult<>(cost, path);
    }

    public Map<V, FindResult<V, E>> findAll(V start) {
        Map<V, DNode<V, E>> scoreMap = new HashMap<>();
        find(start, null, scoreMap);

        Map<V, FindResult<V, E>> ret = new HashMap<>();
        for (DNode<V, E> node : scoreMap.values()) {
            int cost = node.g;
            List<E> path = reconstructPath(scoreMap, node.vertex);
            ret.put(node.vertex, new FindResult<>(cost, path));
        }
        return ret;
    }

    private List<E> reconstructPath(Map<V, DNode<V, E>> scoreMap, V current) {
        final List<E> totalPath = new ArrayList<>();

        while (current != null) {
            DNode<V, E> node = scoreMap.get(current);
            if (node != null) {
                final E edge = node.from;
                totalPath.add(edge);
                current = edge.getSource();
            } else {
                break;
            }
        }
        Collections.reverse(totalPath);
        return totalPath;
    }

    int heuristicCost(V v, V end) {
        if (end == null)
            return 0;
        return heuristicFn.applyAsInt(v, end);
    }

    private int find(V start, V end, Map<V, DNode<V, E>> scoreMap) {
        final Set<V> closedSet = new HashSet<>(); // The set of nodes already evaluated.
        final Set<V> openSet = new HashSet<>(); // The set of tentative nodes to be evaluated, initially containing the
        // start node
        openSet.add(start);

        PriorityQueue<DNode<V, E>> pq = new PriorityQueue<>();
        pq.offer(new DNode<>(start, 0, heuristicCost(start, end)));
        openSet.add(start);

        while (!pq.isEmpty()) {
            DNode<V, E> node = pq.poll();
            V v = node.vertex;
            openSet.remove(v);
            closedSet.add(v);

            if (v.equals(end))
                return node.g;

            graph.forEachOutwardEdge(v, edge -> {
                V to = edge.getTarget();
                if (closedSet.contains(to))
                    return;

                int g = node.g + costFn.applyAsInt(edge);

                DNode<V, E> toNode = scoreMap.get(to); // !openSet.contains(to) 时 toNode == null
                if (toNode == null || g < toNode.g) {
                    if (toNode == null) {
                        toNode = new DNode<>(to, g, heuristicCost(to, end));
                    } else {
                        toNode.g = g;
                    }
                    toNode.from = edge;

                    if (!openSet.contains(to)) {
                        pq.offer(toNode);
                        openSet.add(to);
                    }
                }
            });
        }

        return -1;
    }

    static class DNode<V, E extends IEdge<V>> implements Comparable<DNode<V, E>> {
        final V vertex;
        int g;
        final int h;
        E from;

        public DNode(V vertex, int g, int h) {
            this.vertex = vertex;
            this.g = g;
            this.h = h;
        }

        @Override
        public int compareTo(DNode<V, E> o) {
            return Integer.compare(g + h, o.g + o.h);
        }
    }
}