package io.nop.graph.api;

import java.util.Collections;
import java.util.List;

/**
 * 影响分析结果。
 *
 * 包含 upstream（入边方向，即"谁会影响目标"）和
 * downstream（出边方向，即"目标会影响谁"）两个方向的受影响节点列表。
 */
public class ImpactResult {
    private final String targetNodeId;
    private final List<ImpactedNode> upstream;
    private final List<ImpactedNode> downstream;
    private final String riskLevel;
    private final int maxDepth;

    public ImpactResult(String targetNodeId, List<ImpactedNode> upstream,
                        List<ImpactedNode> downstream, String riskLevel, int maxDepth) {
        this.targetNodeId = targetNodeId;
        this.upstream = upstream != null ? upstream : Collections.emptyList();
        this.downstream = downstream != null ? downstream : Collections.emptyList();
        this.riskLevel = riskLevel;
        this.maxDepth = maxDepth;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public List<ImpactedNode> getUpstream() {
        return upstream;
    }

    public List<ImpactedNode> getDownstream() {
        return downstream;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public int getTotalImpacted() {
        return upstream.size() + downstream.size();
    }
}
