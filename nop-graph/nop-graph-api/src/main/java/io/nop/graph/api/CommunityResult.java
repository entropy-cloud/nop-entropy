package io.nop.graph.api;

import java.util.Collections;
import java.util.List;

/**
 * 社区检测结果。
 */
public class CommunityResult {
    private final List<CommunityInfo> communities;
    private final int totalSymbols;
    private final int totalCommunities;
    private final double averageCohesion;
    private final double modularity;
    private final String algorithmUsed;
    private final long processingTimeMs;

    public CommunityResult(List<CommunityInfo> communities, int totalSymbols,
                           int totalCommunities, double averageCohesion,
                           double modularity, String algorithmUsed,
                           long processingTimeMs) {
        this.communities = communities != null ? communities : Collections.emptyList();
        this.totalSymbols = totalSymbols;
        this.totalCommunities = totalCommunities;
        this.averageCohesion = averageCohesion;
        this.modularity = modularity;
        this.algorithmUsed = algorithmUsed;
        this.processingTimeMs = processingTimeMs;
    }

    public List<CommunityInfo> getCommunities() {
        return communities;
    }

    public int getTotalSymbols() {
        return totalSymbols;
    }

    public int getTotalCommunities() {
        return totalCommunities;
    }

    public double getAverageCohesion() {
        return averageCohesion;
    }

    public double getModularity() {
        return modularity;
    }

    public String getAlgorithmUsed() {
        return algorithmUsed;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }
}
