package io.nop.graph.api;

/**
 * 影响分析配置。
 */
public class ImpactConfig {
    private int maxDepth = 3;
    private int maxNodes = 100;

    public int getMaxDepth() {
        return maxDepth;
    }

    public ImpactConfig setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
        return this;
    }

    public int getMaxNodes() {
        return maxNodes;
    }

    public ImpactConfig setMaxNodes(int maxNodes) {
        this.maxNodes = maxNodes;
        return this;
    }

    public static ImpactConfig create() {
        return new ImpactConfig();
    }
}
