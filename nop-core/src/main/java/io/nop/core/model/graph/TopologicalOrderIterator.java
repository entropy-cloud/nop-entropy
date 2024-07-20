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

import io.nop.commons.mutable.MutableInt;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Iterates over the edges of a graph in topological order.
 *
 * @param <V> Vertex type
 */
public class TopologicalOrderIterator<V> implements Iterator<V> {
    private final Map<V, MutableInt> countMap = new LinkedHashMap<>();
    private final ArrayDeque<V> empties = new ArrayDeque<>();
    private final ITargetVertexVisitor<V> graph;
    private boolean allowLoop;

    public TopologicalOrderIterator(Collection<V> vertexSet, ITargetVertexVisitor<V> graph, boolean allowLoop) {
        this.graph = graph;
        populate(vertexSet, countMap, empties);
        this.allowLoop = allowLoop;
    }

    public TopologicalOrderIterator(ITargetVertexView<V> graph, boolean allowLoop) {
        this(graph.vertexSet(), graph, allowLoop);
    }

    public TopologicalOrderIterator(ITargetVertexView<V> graph) {
        this(graph, true);
    }

    private void populate(Collection<V> vertexSet, Map<V, MutableInt> countMap, ArrayDeque<V> empties) {
        for (V v : vertexSet) {
            countMap.put(v, new MutableInt());
        }

        for (V v : vertexSet) {
            graph.forEachTarget(v, target -> {
                final MutableInt ints = countMap.get(target);
                if (ints != null) {
                    ints.incrementAndGet();
                }
            });
        }

        for (Map.Entry<V, MutableInt> entry : countMap.entrySet()) {
            if (entry.getValue().get() == 0) {
                empties.add(entry.getKey());
            }
        }
        countMap.keySet().removeAll(empties);
    }

    public boolean hasNext() {
        return !empties.isEmpty() || (allowLoop && breakLoop());
    }

    public V next() {
        do {
            V v = nextNoCycle();
            if (v != null)
                return v;

            if (!allowLoop)
                return null;

            if (!breakLoop())
                return null;
        } while (true);
    }

    boolean breakLoop() {
        if (countMap.isEmpty())
            return false;

        Iterator<V> it = countMap.keySet().iterator();
        V target = it.next();
        it.remove();
        empties.addLast(target);
        return true;
    }

    V nextNoCycle() {
        V v = empties.removeFirst();
        graph.forEachTarget(v, target -> {
            MutableInt ints = countMap.get(target);
            if (ints != null) {
                if (ints.decrementAndGet() == 0) {
                    countMap.remove(target);
                    empties.addLast(target);
                }
            }
        });
        return v;
    }

    public Set<V> getRemaining() {
        return countMap.keySet();
    }

    public boolean containsCycle() {
        while (hasNext()) {
            nextNoCycle();
        }
        return !countMap.isEmpty();
    }

    public Set<V> findCycles() {
        while (hasNext()) {
            nextNoCycle();
        }

        return countMap.keySet();
    }

    public List<V> findOneCycle() {
        List<V> visited = new ArrayList<>();
        for (V node : countMap.keySet()) {
            visited.clear();
            if (containsCycle(node, visited))
                break;
        }
        return visited;
    }

    public String displayOneCycle() {
        List<V> cycle = findOneCycle();
        cycle = CollectionHelper.reverseList(cycle);
        return StringHelper.join(cycle, "->");
    }

    private boolean containsCycle(V node, List<V> visited) {
        int index = visited.indexOf(node);
        if (index >= 0) {
            // 仅保留最小化的部分
            for (int i = 0; i < index; i++) {
                visited.remove(0);
            }
            visited.add(node);
            return true;
        }
        visited.add(node);
        for (V target : graph.getTargetVertexes(node)) {
            if (containsCycle(target, visited))
                return true;
        }
        visited.remove(visited.size() - 1);
        return false;
    }
}