package io.nop.graph.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;

/**
 * 内存图实现。内部使用 HashMap 存储出边和入边。
 *
 * 这是同步线程安全的实现。
 */
public class InMemoryGraph implements IGraph {

    private final Map<String, List<Edge>> outEdges = new HashMap<>();
    private final Map<String, List<Edge>> inEdges = new HashMap<>();
    private final Set<String> nodes = new LinkedHashSet<>();
    private final Set<Edge> allEdges = new LinkedHashSet<>();

    public synchronized void addNode(String nodeId) {
        nodes.add(nodeId);
    }

    public synchronized void addEdge(String sourceId, String targetId) {
        addEdge(sourceId, targetId, 1.0, null);
    }

    public synchronized void addEdge(String sourceId, String targetId, double weight, String type) {
        Edge edge = new Edge(sourceId, targetId, weight, type);
        nodes.add(sourceId);
        nodes.add(targetId);
        outEdges.computeIfAbsent(sourceId, k -> new ArrayList<>()).add(edge);
        inEdges.computeIfAbsent(targetId, k -> new ArrayList<>()).add(edge);
        allEdges.add(edge);
    }

    @Override
    public synchronized List<Edge> getOutEdges(String nodeId) {
        List<Edge> edges = outEdges.get(nodeId);
        return edges != null ? new ArrayList<>(edges) : Collections.emptyList();
    }

    @Override
    public synchronized List<Edge> getInEdges(String nodeId) {
        List<Edge> edges = inEdges.get(nodeId);
        return edges != null ? new ArrayList<>(edges) : Collections.emptyList();
    }

    public synchronized Set<String> nodeSet() {
        return new LinkedHashSet<>(nodes);
    }

    public synchronized Set<Edge> edgeSet() {
        return new LinkedHashSet<>(allEdges);
    }

    public synchronized int getNodeCount() {
        return nodes.size();
    }

    public synchronized int getEdgeCount() {
        return allEdges.size();
    }
}
