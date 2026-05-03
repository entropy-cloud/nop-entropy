package io.nop.code.core.graph;

import java.util.*;

/**
 * 方法调用有向图
 */
public class CallGraph {
    private final Map<String, List<String>> forwardEdges = new HashMap<>();
    private final Map<String, List<String>> reverseEdges = new HashMap<>();

    public void addEdge(String caller, String callee) {
        forwardEdges.computeIfAbsent(caller, k -> new ArrayList<>()).add(callee);
        reverseEdges.computeIfAbsent(callee, k -> new ArrayList<>()).add(caller);
    }

    public List<String> getCallees(String nodeId) {
        return forwardEdges.getOrDefault(nodeId, Collections.emptyList());
    }

    public List<String> getCallers(String nodeId) {
        return reverseEdges.getOrDefault(nodeId, Collections.emptyList());
    }

    public Set<String> getAllNodeIds() {
        Set<String> all = new HashSet<>(forwardEdges.keySet());
        all.addAll(reverseEdges.keySet());
        return all;
    }

    public Map<String, List<String>> getForwardMap() {
        return forwardEdges;
    }
}
