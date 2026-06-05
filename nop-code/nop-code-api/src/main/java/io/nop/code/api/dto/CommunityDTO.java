package io.nop.code.api.dto;

import java.io.Serializable;
import java.util.List;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class CommunityDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String label;
    private List<String> symbolIds;
    private int symbolCount;
    private double cohesion;
    private String dominantPackage;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<String> getSymbolIds() {
        return symbolIds;
    }

    public void setSymbolIds(List<String> symbolIds) {
        this.symbolIds = symbolIds;
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

    public String getDominantPackage() {
        return dominantPackage;
    }

    public void setDominantPackage(String dominantPackage) {
        this.dominantPackage = dominantPackage;
    }
}
