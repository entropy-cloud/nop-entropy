package io.nop.code.service.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.List;

@DataBean
public class CommunityDetectionResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<CommunityDTO> communities;
    private int totalSymbols;
    private int totalCommunities;
    private double averageCohesion;
    private String algorithmUsed;
    private double modularity;
    private long processingTimeMs;

    public List<CommunityDTO> getCommunities() {
        return communities;
    }

    public void setCommunities(List<CommunityDTO> communities) {
        this.communities = communities;
    }

    public int getTotalSymbols() {
        return totalSymbols;
    }

    public void setTotalSymbols(int totalSymbols) {
        this.totalSymbols = totalSymbols;
    }

    public int getTotalCommunities() {
        return totalCommunities;
    }

    public void setTotalCommunities(int totalCommunities) {
        this.totalCommunities = totalCommunities;
    }

    public double getAverageCohesion() {
        return averageCohesion;
    }

    public void setAverageCohesion(double averageCohesion) {
        this.averageCohesion = averageCohesion;
    }

    public String getAlgorithmUsed() {
        return algorithmUsed;
    }

    public void setAlgorithmUsed(String algorithmUsed) {
        this.algorithmUsed = algorithmUsed;
    }

    public double getModularity() {
        return modularity;
    }

    public void setModularity(double modularity) {
        this.modularity = modularity;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }
}
