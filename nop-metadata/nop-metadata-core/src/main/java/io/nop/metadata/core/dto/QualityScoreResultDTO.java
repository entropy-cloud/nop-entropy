package io.nop.metadata.core.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@DataBean
public class QualityScoreResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String scoreId;
    private String qualityScoreId;
    private double overallScore;
    private Map<String, Object> dimensionScores = new LinkedHashMap<>();
    private Map<String, Object> ruleSummary = new LinkedHashMap<>();
    private Map<String, Object> trend = new LinkedHashMap<>();

    public String getScoreId() {
        return scoreId;
    }

    public void setScoreId(String scoreId) {
        this.scoreId = scoreId;
    }

    public String getQualityScoreId() {
        return qualityScoreId;
    }

    public void setQualityScoreId(String qualityScoreId) {
        this.qualityScoreId = qualityScoreId;
    }

    public double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(double overallScore) {
        this.overallScore = overallScore;
    }

    public Map<String, Object> getDimensionScores() {
        return dimensionScores;
    }

    public void setDimensionScores(Map<String, Object> dimensionScores) {
        this.dimensionScores = dimensionScores;
    }

    public Map<String, Object> getRuleSummary() {
        return ruleSummary;
    }

    public void setRuleSummary(Map<String, Object> ruleSummary) {
        this.ruleSummary = ruleSummary;
    }

    public Map<String, Object> getTrend() {
        return trend;
    }

    public void setTrend(Map<String, Object> trend) {
        this.trend = trend;
    }
}
