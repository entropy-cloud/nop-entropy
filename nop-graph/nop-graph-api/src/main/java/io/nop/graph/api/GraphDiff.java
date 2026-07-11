package io.nop.graph.api;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 两个图快照之间的差分结果。
 */
public class GraphDiff {
    private final Set<String> addedNodes;
    private final Set<String> removedNodes;
    private final Set<Edge> addedEdges;
    private final Set<Edge> removedEdges;
    private final List<CommunityChange> communityChanges;

    public GraphDiff(Set<String> addedNodes, Set<String> removedNodes,
                     Set<Edge> addedEdges, Set<Edge> removedEdges,
                     List<CommunityChange> communityChanges) {
        this.addedNodes = addedNodes != null ? addedNodes : Collections.emptySet();
        this.removedNodes = removedNodes != null ? removedNodes : Collections.emptySet();
        this.addedEdges = addedEdges != null ? addedEdges : Collections.emptySet();
        this.removedEdges = removedEdges != null ? removedEdges : Collections.emptySet();
        this.communityChanges = communityChanges != null ? communityChanges : Collections.emptyList();
    }

    public Set<String> getAddedNodes() {
        return addedNodes;
    }

    public Set<String> getRemovedNodes() {
        return removedNodes;
    }

    public Set<Edge> getAddedEdges() {
        return addedEdges;
    }

    public Set<Edge> getRemovedEdges() {
        return removedEdges;
    }

    public List<CommunityChange> getCommunityChanges() {
        return communityChanges;
    }

    public boolean isEmpty() {
        return addedNodes.isEmpty() && removedNodes.isEmpty()
                && addedEdges.isEmpty() && removedEdges.isEmpty()
                && communityChanges.isEmpty();
    }
}
