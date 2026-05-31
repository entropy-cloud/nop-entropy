package io.nop.code.service.api.dto;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class CriticalNodeScoreDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String symbolId;
    private String qualifiedName;
    private double score;
    private int inDegree;
    private int outDegree;
    private int totalDegree;

    public String getSymbolId() {
        return symbolId;
    }

    public void setSymbolId(String symbolId) {
        this.symbolId = symbolId;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public int getInDegree() {
        return inDegree;
    }

    public void setInDegree(int inDegree) {
        this.inDegree = inDegree;
    }

    public int getOutDegree() {
        return outDegree;
    }

    public void setOutDegree(int outDegree) {
        this.outDegree = outDegree;
    }

    public int getTotalDegree() {
        return totalDegree;
    }

    public void setTotalDegree(int totalDegree) {
        this.totalDegree = totalDegree;
    }
}
