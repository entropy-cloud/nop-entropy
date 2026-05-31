package io.nop.code.service.api.dto;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class WeakCommunityDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String communityId;
    private String label;
    private int symbolCount;
    private double cohesion;
    private double threshold;

    public String getCommunityId() {
        return communityId;
    }

    public void setCommunityId(String communityId) {
        this.communityId = communityId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getSymbolCount() {
        return symbolCount;
    }

    public void setSymbolCount(int symbolCount) {
        this.symbolCount = symbolCount;
    }

    public double getCohesion() {
        return cohesion;
    }

    public void setCohesion(double cohesion) {
        this.cohesion = cohesion;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }
}
