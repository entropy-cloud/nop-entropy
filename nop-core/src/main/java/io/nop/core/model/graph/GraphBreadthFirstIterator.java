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

import java.util.*;

/**
 * Iterates over the vertices in a directed graph in breadth-first order.
 *
 * @param <V> Vertex type
 */
public class GraphBreadthFirstIterator<V> implements Iterator<V> {
    private final ITargetVertexVisitor<V> graph;
    private final Deque<V> deque = new ArrayDeque<>();
    private final Set<V> set = new HashSet<>();

    public GraphBreadthFirstIterator(ITargetVertexVisitor<V> graph, V root) {
        this.graph = graph;
        this.deque.add(root);
    }

    // tell cpd to start ignoring code - CPD-OFF

    /**
     * Populates a set with the nodes reachable from a given node.
     */
    public static <V> void reachable(Set<V> set, final ITargetVertexVisitor<V> graph, final V root) {
        final Deque<V> deque = new ArrayDeque<>();
        deque.add(root);
        set.add(root);
        while (!deque.isEmpty()) {
            next(deque, graph, set);
        }
    }

    public boolean hasNext() {
        return !deque.isEmpty();
    }

    // resume CPD analysis - CPD-ON

    private static <V> V next(Deque<V> deque, ITargetVertexVisitor<V> graph, Set<V> set) {
        V v = deque.removeFirst();

        graph.forEachTarget(v, target -> {
            if (set.add(target)) {
                deque.addLast(target);
            }
        });
        return v;
    }

    public V next() {
        if (!hasNext())
            throw new NoSuchElementException();
        return next(deque, graph, set);
    }
}