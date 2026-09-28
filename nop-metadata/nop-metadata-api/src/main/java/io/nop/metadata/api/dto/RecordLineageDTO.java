package io.nop.metadata.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

@DataBean
public class RecordLineageDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sourceEntityId;

    private String targetEntityId;

    private String sourceColumn;

    private String targetColumn;

    private String transformType;

    private String transformExpr;

    private String pipelineId;

    private Double confidence;

    private String lineageSource;

    public String getSourceEntityId() {
        return sourceEntityId;
    }

    public void setSourceEntityId(String sourceEntityId) {
        this.sourceEntityId = sourceEntityId;
    }

    public String getTargetEntityId() {
        return targetEntityId;
    }

    public void setTargetEntityId(String targetEntityId) {
        this.targetEntityId = targetEntityId;
    }

    public String getSourceColumn() {
        return sourceColumn;
    }

    public void setSourceColumn(String sourceColumn) {
        this.sourceColumn = sourceColumn;
    }

    public String getTargetColumn() {
        return targetColumn;
    }

    public void setTargetColumn(String targetColumn) {
        this.targetColumn = targetColumn;
    }

    public String getTransformType() {
        return transformType;
    }

    public void setTransformType(String transformType) {
        this.transformType = transformType;
    }

    public String getTransformExpr() {
        return transformExpr;
    }

    public void setTransformExpr(String transformExpr) {
        this.transformExpr = transformExpr;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getLineageSource() {
        return lineageSource;
    }

    public void setLineageSource(String lineageSource) {
        this.lineageSource = lineageSource;
    }
}
