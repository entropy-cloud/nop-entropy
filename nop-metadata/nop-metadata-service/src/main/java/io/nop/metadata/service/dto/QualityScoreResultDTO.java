package io.nop.metadata.service.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

/**
 * 质量评分计算结果 DTO（来源：{@code NopMetaQualityScoreBizModel.computeQualityScore}）。
 */
@DataBean
public class QualityScoreResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String metaTableId;
    private String qualityScoreId;
    private double score;
    private int totalRules;
    private int passedRules;
    private int failedRules;
    private int skippedRules;

    public String getMetaTableId() {
        return metaTableId;
    }

    public void setMetaTableId(String metaTableId) {
        this.metaTableId = metaTableId;
    }

    public String getQualityScoreId() {
        return qualityScoreId;
    }

    public void setQualityScoreId(String qualityScoreId) {
        this.qualityScoreId = qualityScoreId;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public int getTotalRules() {
        return totalRules;
    }

    public void setTotalRules(int totalRules) {
        this.totalRules = totalRules;
    }

    public int getPassedRules() {
        return passedRules;
    }

    public void setPassedRules(int passedRules) {
        this.passedRules = passedRules;
    }

    public int getFailedRules() {
        return failedRules;
    }

    public void setFailedRules(int failedRules) {
        this.failedRules = failedRules;
    }

    public int getSkippedRules() {
        return skippedRules;
    }

    public void setSkippedRules(int skippedRules) {
        this.skippedRules = skippedRules;
    }
}
