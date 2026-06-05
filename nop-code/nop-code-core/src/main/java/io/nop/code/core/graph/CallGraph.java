package io.nop.code.core.graph;

import java.util.*;
/**
 * 方法调用有向图
 */
public class CallGraph {
    private final Map<String, List<String>> forwardEdges = new HashMap<>();
    private final Map<String, List<String>> reverseEdges = new HashMap<>();
    private final Set<String> edgeKeys = new HashSet<>();
    private boolean truncated;

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public synchronized void addEdge(String caller, String callee) {
        String edgeKey = caller + "->" + callee;
        if (!edgeKeys.add(edgeKey)) {
            return;
        }
        forwardEdges.computeIfAbsent(caller, k -> new ArrayList<>()).add(callee);
        reverseEdges.computeIfAbsent(callee, k -> new ArrayList<>()).add(caller);
    }

    public synchronized List<String> getCallees(String nodeId) {
        List<String> callees = forwardEdges.get(nodeId);
        return callees != null ? new ArrayList<>(callees) : Collections.emptyList();
    }

    public synchronized List<String> getCallers(String nodeId) {
        List<String> callers = reverseEdges.get(nodeId);
        return callers != null ? new ArrayList<>(callers) : Collections.emptyList();
    }

    public Set<String> getAllNodeIds() {
        Set<String> all = new HashSet<>(forwardEdges.keySet());
        all.addAll(reverseEdges.keySet());
        return all;
    }

    public Map<String, List<String>> getForwardMap() {
        return Collections.unmodifiableMap(forwardEdges);
    }
}
