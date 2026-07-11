package io.nop.graph.api;

import java.util.Collections;
import java.util.List;

/**
 * 关键节点分析结果。
 *
 * hubNodes 基于 degree 排序，bridgeNodes 基于 betweenness centrality。
 */
public class CriticalNodeResult {
    private final int totalNodes;
    private final int topN;
    private final List<NodeScore> hubNodes;
    private final List<NodeScore> bridgeNodes;

    public CriticalNodeResult(int totalNodes, int topN,
                              List<NodeScore> hubNodes, List<NodeScore> bridgeNodes) {
        this.totalNodes = totalNodes;
        this.topN = topN;
        this.hubNodes = hubNodes != null ? hubNodes : Collections.emptyList();
        this.bridgeNodes = bridgeNodes != null ? bridgeNodes : Collections.emptyList();
    }

    public int getTotalNodes() {
        return totalNodes;
    }

    public int getTopN() {
        return topN;
    }

    public List<NodeScore> getHubNodes() {
        return hubNodes;
    }

    public List<NodeScore> getBridgeNodes() {
        return bridgeNodes;
    }
}
