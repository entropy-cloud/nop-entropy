package io.nop.graph.api;

/**
 * 影响分析中受影响的节点。
 */
public class ImpactedNode {
    private final String nodeId;
    private final int depth;

    public ImpactedNode(String nodeId, int depth) {
        this.nodeId = nodeId;
        this.depth = depth;
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public String toString() {
        return "ImpactedNode{nodeId='" + nodeId + "', depth=" + depth + "}";
    }
}
