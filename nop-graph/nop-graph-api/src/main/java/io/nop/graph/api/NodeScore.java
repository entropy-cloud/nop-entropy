package io.nop.graph.api;

/**
 * 关键节点评分。
 */
public class NodeScore {
    private final String nodeId;
    private final double score;
    private final int inDegree;
    private final int outDegree;

    public NodeScore(String nodeId, double score, int inDegree, int outDegree) {
        this.nodeId = nodeId;
        this.score = score;
        this.inDegree = inDegree;
        this.outDegree = outDegree;
    }

    public String getNodeId() {
        return nodeId;
    }

    public double getScore() {
        return score;
    }

    public int getInDegree() {
        return inDegree;
    }

    public int getOutDegree() {
        return outDegree;
    }

    public int getTotalDegree() {
        return inDegree + outDegree;
    }
}
