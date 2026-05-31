package io.nop.code.core.util;

public final class BfsNode {
    private final String nodeId;
    private final int depth;

    public BfsNode(String nodeId, int depth) {
        this.nodeId = nodeId;
        this.depth = depth;
    }

    public String nodeId() {
        return nodeId;
    }

    public int depth() {
        return depth;
    }
}
