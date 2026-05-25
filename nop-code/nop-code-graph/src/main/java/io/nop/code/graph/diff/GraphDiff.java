package io.nop.code.graph.diff;

import java.util.List;

public class GraphDiff {
    private java.util.Set<String> addedNodes;
    private java.util.Set<String> removedNodes;
    private java.util.Set<GraphSnapshot.EdgeKey> addedEdges;
    private java.util.Set<GraphSnapshot.EdgeKey> removedEdges;
    private List<CommunityChange> communityChanges;

    public java.util.Set<String> getAddedNodes() {
        return addedNodes != null ? addedNodes : java.util.Collections.emptySet();
    }

    public void setAddedNodes(java.util.Set<String> addedNodes) {
        this.addedNodes = addedNodes;
    }

    public java.util.Set<String> getRemovedNodes() {
        return removedNodes != null ? removedNodes : java.util.Collections.emptySet();
    }

    public void setRemovedNodes(java.util.Set<String> removedNodes) {
        this.removedNodes = removedNodes;
    }

    public java.util.Set<GraphSnapshot.EdgeKey> getAddedEdges() {
        return addedEdges != null ? addedEdges : java.util.Collections.emptySet();
    }

    public void setAddedEdges(java.util.Set<GraphSnapshot.EdgeKey> addedEdges) {
        this.addedEdges = addedEdges;
    }

    public java.util.Set<GraphSnapshot.EdgeKey> getRemovedEdges() {
        return removedEdges != null ? removedEdges : java.util.Collections.emptySet();
    }

    public void setRemovedEdges(java.util.Set<GraphSnapshot.EdgeKey> removedEdges) {
        this.removedEdges = removedEdges;
    }

    public List<CommunityChange> getCommunityChanges() {
        return communityChanges != null ? communityChanges : List.of();
    }

    public void setCommunityChanges(List<CommunityChange> communityChanges) {
        this.communityChanges = communityChanges;
    }

    public static class CommunityChange {
        private String nodeId;
        private String oldCommunity;
        private String newCommunity;

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getOldCommunity() {
            return oldCommunity;
        }

        public void setOldCommunity(String oldCommunity) {
            this.oldCommunity = oldCommunity;
        }

        public String getNewCommunity() {
            return newCommunity;
        }

        public void setNewCommunity(String newCommunity) {
            this.newCommunity = newCommunity;
        }
    }
}
