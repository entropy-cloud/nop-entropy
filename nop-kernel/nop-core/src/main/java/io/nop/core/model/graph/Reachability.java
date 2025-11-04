/*
The MIT License

Copyright (C) 2018 Yue Li, Tian Tan, Anders Møller, Yannis Smaragdakis

Permission is hereby granted, free of charge, to any person
obtaining a copy of this software and associated documentation
files (the "Software"), to deal in the Software without
restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the
Software is furnished to do so, subject to the following
conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.
 */
package io.nop.core.model.graph;

import com.google.common.collect.Sets;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Reachability<N> {

    private final IDirectedGraphVertexView<N> graph;
    private final Map<N, Set<N>> reachableNodes = new HashMap<>();
    private final Map<N, Set<N>> reachToNodes = new HashMap<>();

    public Reachability(IDirectedGraphVertexView<N> graph) {
        this.graph = graph;
    }

    /**
     * @param source
     * @return all nodes those can be reached from source.
     */
    public Set<N> reachableNodesFrom(N source) {
        if (!reachableNodes.containsKey(source)) {
            Set<N> visited = new HashSet<>();
            Deque<N> stack = new ArrayDeque<>();
            stack.push(source);
            while (!stack.isEmpty()) {
                N node = stack.pop();
                visited.add(node);
                graph.getTargetVertexes(node).stream().filter(n -> !visited.contains(n)).forEach(stack::push);
            }
            reachableNodes.put(source, visited);
        }
        return reachableNodes.get(source);
    }

    /**
     * @param target
     * @return all nodes those can reach target.
     */
    public Set<N> nodesReach(N target) {
        if (!reachToNodes.containsKey(target)) {
            Set<N> visited = new HashSet<>();
            Deque<N> stack = new ArrayDeque<>();
            stack.push(target);
            while (!stack.isEmpty()) {
                N node = stack.pop();
                visited.add(node);
                graph.getSourceVertexes(node).stream().filter(n -> !visited.contains(n)).forEach(stack::push);
            }
            reachToNodes.put(target, visited);
        }
        return reachToNodes.get(target);
    }

    /**
     * @param source
     * @param target
     * @return all nodes on the paths from source to target.
     */
    public Set<N> passedNodes(N source, N target) {
        Set<N> reachableFromSource = reachableNodesFrom(source);
        Set<N> reachToTarget = nodesReach(target);
        return Sets.intersection(reachableFromSource, reachToTarget);
    }
}
