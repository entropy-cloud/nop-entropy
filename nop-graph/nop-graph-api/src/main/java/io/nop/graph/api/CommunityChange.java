package io.nop.graph.api;

/**
 * 图差分中节点社区归属变化。
 */
public class CommunityChange {
    private final String nodeId;
    private final int oldCommunity;
    private final int newCommunity;

    public CommunityChange(String nodeId, int oldCommunity, int newCommunity) {
        this.nodeId = nodeId;
        this.oldCommunity = oldCommunity;
        this.newCommunity = newCommunity;
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getOldCommunity() {
        return oldCommunity;
    }

    public int getNewCommunity() {
        return newCommunity;
    }
}
