package io.nop.code.core.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // WP-5 AR-145: read methods must be synchronized to match the write methods.
    public synchronized Set<String> getAllNodeIds() {
        Set<String> all = new HashSet<>(forwardEdges.keySet());
        all.addAll(reverseEdges.keySet());
        return all;
    }

    // WP-5 AR-148: return a fully isolated snapshot — an unmodifiable map whose values are
    // unmodifiable copies, so callers cannot mutate the cached graph's internal lists and are
    // unaffected by later addEdge calls.
    public synchronized Map<String, List<String>> getForwardMap() {
        Map<String, List<String>> copy = new HashMap<>();
        for (Map.Entry<String, List<String>> e : forwardEdges.entrySet()) {
            copy.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }
}
