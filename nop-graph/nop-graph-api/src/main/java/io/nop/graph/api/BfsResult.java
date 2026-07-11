package io.nop.graph.api;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * BFS 遍历结果。
 */
public class BfsResult {
    private final Set<String> reachableNodes;
    private final Map<String, Integer> depthMap;

    public BfsResult(Set<String> reachableNodes, Map<String, Integer> depthMap) {
        this.reachableNodes = reachableNodes != null ? reachableNodes : Collections.emptySet();
        this.depthMap = depthMap != null ? depthMap : Collections.emptyMap();
    }

    public Set<String> getReachableNodes() {
        return reachableNodes;
    }

    public int getDepth(String nodeId) {
        return depthMap.getOrDefault(nodeId, -1);
    }

    public int size() {
        return reachableNodes.size();
    }
}
