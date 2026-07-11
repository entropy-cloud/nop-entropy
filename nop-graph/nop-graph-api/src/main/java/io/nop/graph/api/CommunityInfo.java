package io.nop.graph.api;

import java.util.Collections;
import java.util.Set;

/**
 * 单个社区的信息。
 */
public class CommunityInfo {
    private final int id;
    private final Set<String> nodeIds;
    private final double cohesion;

    public CommunityInfo(int id, Set<String> nodeIds, double cohesion) {
        this.id = id;
        this.nodeIds = nodeIds != null ? nodeIds : Collections.emptySet();
        this.cohesion = cohesion;
    }

    public int getId() {
        return id;
    }

    public Set<String> getNodeIds() {
        return nodeIds;
    }

    public int getNodeCount() {
        return nodeIds.size();
    }

    public double getCohesion() {
        return cohesion;
    }
}
